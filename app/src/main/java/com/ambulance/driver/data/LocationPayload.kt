package com.ambulance.driver.data

import org.json.JSONObject

data class LocationPayload(
    val ambulanceId: String,
    val lat: Double,
    val longitude: Double,
    val severity: String,
    val timestamp: Long
) {
    fun toJson(): String {
        return JSONObject()
            .put("ambulance_id", ambulanceId)
            .put("lat", lat)
            .put("long", longitude)
            .put("severity", severity)
            .put("timestamp", timestamp)
            .toString()
    }

    fun topic(): String = "ambulance/$ambulanceId/location"
}
