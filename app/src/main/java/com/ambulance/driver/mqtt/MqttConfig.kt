package com.ambulance.driver.mqtt

import com.ambulance.driver.BuildConfig

object MqttConfig {
    val host: String = BuildConfig.MQTT_HOST
    val port: Int = if (BuildConfig.MQTT_PORT > 0) BuildConfig.MQTT_PORT else 8883
    val username: String = BuildConfig.MQTT_USERNAME
    val password: String = BuildConfig.MQTT_PASSWORD
    val useTls: Boolean = port == 8883 || port == 443

    fun isConfigured(): Boolean {
        return host.isNotBlank() &&
            !host.startsWith("YOUR_") &&
            username.isNotBlank() &&
            !username.startsWith("YOUR_") &&
            password.isNotBlank() &&
            !password.startsWith("YOUR_")
    }
}
