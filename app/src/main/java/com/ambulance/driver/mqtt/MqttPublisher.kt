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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class MqttPublisher(
    private val prefs: AppPrefs,
    private val queue: OfflineMessageQueue
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycleMutex = Mutex()
    private val publishMutex = Mutex()
    private val flushMutex = Mutex()
    private val connected = AtomicBoolean(false)
    private val stayConnected = AtomicBoolean(false)
    private val attemptSeq = AtomicInteger(0)
    private val connectInFlight = AtomicBoolean(false)

    @Volatile
    private var client: Mqtt5AsyncClient? = null

    @Volatile
    private var activeAttempt: Int = 0

    private var reconnectJob: Job? = null

    fun connect() {
        stayConnected.set(true)
        scope.launch { connectExclusive(reason = "start") }
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
        stayConnected.set(false)
        attemptSeq.incrementAndGet()
        reconnectJob?.cancel()
        reconnectJob = null
        scope.launch { disconnectExclusive() }
    }

    private suspend fun connectExclusive(reason: String) {
        if (!stayConnected.get()) {
            Log.i(PUBLISH_TAG, "Skipping MQTT connect ($reason): stayConnected=false")
            return
        }
        if (connected.get() && client != null) {
            Log.i(PUBLISH_TAG, "Skipping MQTT connect ($reason): already connected")
            return
        }
        if (!connectInFlight.compareAndSet(false, true)) {
            Log.i(PUBLISH_TAG, "Skipping overlapping MQTT connect ($reason): another attempt is in flight")
            return
        }

        val attempt = attemptSeq.incrementAndGet()
        activeAttempt = attempt
        reconnectJob?.cancel()
        reconnectJob = null

        try {
            lifecycleMutex.withLock {
                if (!stayConnected.get() || attempt != attemptSeq.get()) {
                    Log.i(PUBLISH_TAG, "Aborting MQTT connect attempt=$attempt reason=$reason (superseded)")
                    return@withLock
                }
                if (!MqttConfig.isConfigured()) {
                    Log.e(PUBLISH_TAG, "MQTT secrets are not configured; updates will be queued")
                    JourneyState.setMqttStatus(MqttConnectionStatus.DISCONNECTED)
                    return@withLock
                }

                disposeClientLocked("before attempt=$attempt reason=$reason")
                JourneyState.setMqttStatus(MqttConnectionStatus.CONNECTING)

                val host = MqttConfig.host
                val port = MqttConfig.port
                val clientId = "ambulance-${prefs.ambulanceId}-${UUID.randomUUID().toString().take(8)}"
                Log.i(
                    PUBLISH_TAG,
                    "MQTT connect attempt=$attempt clientId=$clientId reason=$reason host=$host:$port tls=${MqttConfig.useTls}"
                )

                val built = MqttClient.builder()
                    .useMqttVersion5()
                    .identifier(clientId)
                    .serverHost(host)
                    .serverPort(port)
                    .addConnectedListener {
                        if (attempt != attemptSeq.get()) {
                            Log.w(
                                PUBLISH_TAG,
                                "Ignoring connected callback for stale clientId=$clientId attempt=$attempt"
                            )
                            return@addConnectedListener
                        }
                        connected.set(true)
                        JourneyState.setMqttStatus(MqttConnectionStatus.CONNECTED)
                        Log.i(PUBLISH_TAG, "MQTT connected clientId=$clientId attempt=$attempt to $host:$port")
                        scope.launch { flushQueue() }
                    }
                    .addDisconnectedListener { context ->
                        connected.set(false)
                        if (context.source == MqttDisconnectSource.USER) {
                            Log.i(PUBLISH_TAG, "MQTT disconnected by app clientId=$clientId attempt=$attempt")
                            if (attempt == activeAttempt) {
                                JourneyState.setMqttStatus(MqttConnectionStatus.DISCONNECTED)
                            }
                            return@addDisconnectedListener
                        }
                        Log.w(
                            PUBLISH_TAG,
                            "MQTT disconnected clientId=$clientId attempt=$attempt " +
                                "source=${context.source}: ${context.cause?.message}"
                        )
                        if (!stayConnected.get() || attempt != attemptSeq.get()) {
                            Log.i(
                                PUBLISH_TAG,
                                "Ignoring reconnect for stale/stopped clientId=$clientId attempt=$attempt"
                            )
                            return@addDisconnectedListener
                        }
                        JourneyState.setMqttStatus(MqttConnectionStatus.RECONNECTING)
                        scheduleReconnect(attempt, clientId)
                    }
                    .let { builder ->
                        if (MqttConfig.useTls) builder.sslWithDefaultConfig().buildAsync()
                        else builder.buildAsync()
                    }

                client = built
                try {
                    built.connectWith()
                        .cleanStart(true)
                        .sessionExpiryInterval(0)
                        .simpleAuth()
                        .username(MqttConfig.username)
                        .password(MqttConfig.password.toByteArray(StandardCharsets.UTF_8))
                        .applySimpleAuth()
                        .keepAlive(30)
                        .send()
                        .await()
                    if (attempt != attemptSeq.get()) {
                        Log.w(PUBLISH_TAG, "Connect completed for superseded clientId=$clientId; disposing")
                        runCatching {
                            built.disconnectWith().sessionExpiryInterval(0).send().await()
                        }
                    }
                } catch (e: Exception) {
                    if (attempt != attemptSeq.get()) {
                        Log.i(PUBLISH_TAG, "Connect error ignored for superseded attempt=$attempt clientId=$clientId")
                        return@withLock
                    }
                    connected.set(false)
                    JourneyState.setMqttStatus(MqttConnectionStatus.RECONNECTING)
                    Log.e(PUBLISH_TAG, "MQTT connect failed attempt=$attempt clientId=$clientId", e)
                    scheduleReconnect(attempt, clientId)
                }
            }
        } finally {
            connectInFlight.set(false)
        }
    }

    private fun scheduleReconnect(fromAttempt: Int, previousClientId: String) {
        if (!stayConnected.get()) return
        if (fromAttempt != attemptSeq.get()) {
            Log.i(PUBLISH_TAG, "Not scheduling reconnect; attempt=$fromAttempt was superseded")
            return
        }
        if (reconnectJob?.isActive == true) {
            Log.i(
                PUBLISH_TAG,
                "Reconnect already scheduled; ignoring extra trigger from clientId=$previousClientId"
            )
            return
        }
        reconnectJob = scope.launch {
            Log.i(
                PUBLISH_TAG,
                "Scheduling MQTT reconnect in ${RECONNECT_DELAY_MS}ms after clientId=$previousClientId attempt=$fromAttempt"
            )
            delay(RECONNECT_DELAY_MS)
            if (!stayConnected.get() || fromAttempt != attemptSeq.get()) {
                Log.i(PUBLISH_TAG, "Reconnect cancelled after clientId=$previousClientId attempt=$fromAttempt")
                return@launch
            }
            connectExclusive(reason = "reconnect-after-$previousClientId")
        }
    }

    private suspend fun disconnectExclusive() {
        lifecycleMutex.withLock {
            disposeClientLocked("user disconnect")
            JourneyState.setMqttStatus(MqttConnectionStatus.DISCONNECTED)
        }
    }

    private suspend fun disposeClientLocked(reason: String) {
        connected.set(false)
        val existing = client
        client = null
        if (existing == null) return
        Log.i(PUBLISH_TAG, "Disposing MQTT client ($reason)")
        try {
            existing.disconnectWith()
                .sessionExpiryInterval(0)
                .send()
                .await()
        } catch (e: Exception) {
            Log.w(PUBLISH_TAG, "MQTT dispose disconnect: ${e.message}")
        }
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

    companion object {
        const val PUBLISH_TAG = "AmbulancePublish"
        private const val RECONNECT_DELAY_MS = 2_500L
    }
}
