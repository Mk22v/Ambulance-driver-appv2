package com.ambulance.driver.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MqttConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

object JourneyState {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _severity = MutableStateFlow("")
    val severity: StateFlow<String> = _severity.asStateFlow()

    private val _destination = MutableStateFlow("")
    val destination: StateFlow<String> = _destination.asStateFlow()

    private val _lat = MutableStateFlow<Double?>(null)
    val lat: StateFlow<Double?> = _lat.asStateFlow()

    private val _lng = MutableStateFlow<Double?>(null)
    val lng: StateFlow<Double?> = _lng.asStateFlow()

    private val _lastUpdatedMillis = MutableStateFlow<Long?>(null)
    val lastUpdatedMillis: StateFlow<Long?> = _lastUpdatedMillis.asStateFlow()

    private val _mqttStatus = MutableStateFlow(MqttConnectionStatus.DISCONNECTED)
    val mqttStatus: StateFlow<MqttConnectionStatus> = _mqttStatus.asStateFlow()

    private val _queuedCount = MutableStateFlow(0)
    val queuedCount: StateFlow<Int> = _queuedCount.asStateFlow()

    fun start(severity: String, destination: String) {
        _severity.value = severity
        _destination.value = destination
        _lat.value = null
        _lng.value = null
        _lastUpdatedMillis.value = null
        _mqttStatus.value = MqttConnectionStatus.CONNECTING
        _active.value = true
    }

    fun updateLocation(lat: Double, lng: Double, timestamp: Long = System.currentTimeMillis()) {
        _lat.value = lat
        _lng.value = lng
        _lastUpdatedMillis.value = timestamp
    }

    fun setMqttStatus(status: MqttConnectionStatus) {
        _mqttStatus.value = status
    }

    fun setQueuedCount(count: Int) {
        _queuedCount.value = count
    }

    fun stop() {
        _active.value = false
        _lat.value = null
        _lng.value = null
        _lastUpdatedMillis.value = null
        _mqttStatus.value = MqttConnectionStatus.DISCONNECTED
    }
}
