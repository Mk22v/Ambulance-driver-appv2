package com.ambulance.driver.mqtt

import android.util.Log
import com.ambulance.driver.data.AppPrefs
import com.ambulance.driver.data.JourneyState
import com.ambulance.driver.data.LocationPayload
import com.ambulance.driver.data.MqttConnectionStatus
import com.ambulance.driver.data.OfflineMessageQueue
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.lifecycle.MqttDisconnectSource
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class MqttPublisher(
    private val prefs: AppPrefs,
    private val queue: OfflineMessageQueue
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val publishMutex = Mutex()
    private val flushMutex = Mutex()
    private val connected = AtomicBoolean(false)

    @Volatile
    private var client: Mqtt5AsyncClient? = null

    fun connect() {
        scope.launch {
            try {
                disconnectInternal(userInitiated = true)
                if (!MqttConfig.isConfigured()) {
                    Log.e(PUBLISH_TAG, "MQTT secrets are not configured; updates will be queued")
                    JourneyState.setMqttStatus(MqttConnectionStatus.DISCONNECTED)
                    return@launch
                }

                JourneyState.setMqttStatus(MqttConnectionStatus.CONNECTING)
                val host = MqttConfig.host
                val port = MqttConfig.port
                val clientId = "ambulance-${prefs.ambulanceId}-${UUID.randomUUID().toString().take(8)}"

                val builder = MqttClient.builder()
                    .useMqttVersion5()
                    .identifier(clientId)
                    .serverHost(host)
                    .serverPort(port)
                    .automaticReconnectWithDefaultConfig()
                    .addConnectedListener {
                        connected.set(true)
                        JourneyState.setMqttStatus(MqttConnectionStatus.CONNECTED)
                        Log.i(PUBLISH_TAG, "MQTT connected to $host:$port")
                        scope.launch { flushQueue() }
                    }
                    .addDisconnectedListener { context ->
                        connected.set(false)
                        if (context.source == MqttDisconnectSource.USER) {
                            JourneyState.setMqttStatus(MqttConnectionStatus.DISCONNECTED)
                            context.reconnector.reconnect(false)
                            Log.i(PUBLISH_TAG, "MQTT disconnected by app")
                        } else {
                            JourneyState.setMqttStatus(MqttConnectionStatus.RECONNECTING)
                            Log.w(
                                PUBLISH_TAG,
                                "MQTT disconnected (${context.source}): ${context.cause?.message}; reconnecting"
                            )
                        }
                    }

                val built = if (MqttConfig.useTls) {
                    builder.sslWithDefaultConfig().buildAsync()
                } else {
                    builder.buildAsync()
                }

                client = built
                built.connectWith()
                    .simpleAuth()
                    .username(MqttConfig.username)
                    .password(MqttConfig.password.toByteArray(StandardCharsets.UTF_8))
                    .applySimpleAuth()
                    .keepAlive(30)
                    .send()
                    .await()
            } catch (e: Exception) {
                connected.set(false)
                JourneyState.setMqttStatus(MqttConnectionStatus.RECONNECTING)
                Log.e(PUBLISH_TAG, "MQTT connect failed; will retry / queue locally", e)
            }
        }
    }

    fun publish(payload: LocationPayload) {
        scope.launch {
            val topic = payload.topic()
            val json = payload.toJson()
            Log.i(PUBLISH_TAG, "Publish attempt topic=$topic qos=1 payload=$json")
            val sent = tryPublish(topic, json)
            if (!sent) {
                queue.enqueue(topic, json)
                JourneyState.setQueuedCount(queue.size())
                Log.w(PUBLISH_TAG, "Publish failed or offline; queued topic=$topic queueSize=${queue.size()}")
            }
        }
    }

    fun disconnect() {
        scope.launch { disconnectInternal(userInitiated = true) }
    }

    private suspend fun tryPublish(topic: String, json: String): Boolean {
        val mqtt = client ?: return false
        if (!connected.get()) return false
        return publishMutex.withLock {
            try {
                mqtt.publishWith()
                    .topic(topic)
                    .qos(MqttQos.AT_LEAST_ONCE)
                    .payload(json.toByteArray(StandardCharsets.UTF_8))
                    .send()
                    .await()
                Log.i(PUBLISH_TAG, "Publish succeeded topic=$topic")
                true
            } catch (e: Exception) {
                Log.e(PUBLISH_TAG, "Publish failed topic=$topic", e)
                false
            }
        }
    }

    private suspend fun flushQueue() {
        flushMutex.withLock {
            val pending = queue.snapshot()
            if (pending.isEmpty()) {
                JourneyState.setQueuedCount(0)
                return
            }
            Log.i(PUBLISH_TAG, "Flushing ${pending.size} queued MQTT messages")
            val remaining = pending.toMutableList()
            val iterator = remaining.iterator()
            while (iterator.hasNext()) {
                if (!connected.get()) break
                val item = iterator.next()
                Log.i(PUBLISH_TAG, "Flush publish attempt topic=${item.topic} qos=1")
                val ok = tryPublish(item.topic, item.payload)
                if (ok) {
                    iterator.remove()
                } else {
                    Log.e(PUBLISH_TAG, "Flush publish failed topic=${item.topic}; stopping flush")
                    break
                }
            }
            queue.replaceAll(remaining)
            JourneyState.setQueuedCount(queue.size())
        }
    }

    private suspend fun disconnectInternal(userInitiated: Boolean) {
        connected.set(false)
        if (userInitiated) {
            JourneyState.setMqttStatus(MqttConnectionStatus.DISCONNECTED)
        }
        val existing = client
        client = null
        if (existing != null) {
            try {
                existing.disconnect().await()
            } catch (e: Exception) {
                Log.w(PUBLISH_TAG, "Disconnect: ${e.message}")
            }
        }
    }

    companion object {
        const val PUBLISH_TAG = "AmbulancePublish"
    }
}
