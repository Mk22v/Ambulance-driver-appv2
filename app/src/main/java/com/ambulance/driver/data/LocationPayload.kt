package com.ambulance.driver.data

import android.location.Location
import org.json.JSONObject

data class LocationPayload(
    val ambulanceId: String,
    val lat: Double,
    val longitude: Double,
    val severity: String,
    val speed: Double,
    val altitude: Double?,
    val accuracy: Double?,
    val bearing: Double?,
    val timestamp: Long
) {
    fun toJson(): String {
        return JSONObject()
            .put("ambulance_id", ambulanceId)
            .put("severity", severity)
            .put("lat", lat)
            .put("long", longitude)
            .put("speed", speed)
            .putNullable("altitude", altitude)
            .putNullable("accuracy", accuracy)
            .putNullable("bearing", bearing)
            .put("timestamp", timestamp)
            .toString()
    }

    fun topic(): String = "ambulance/$ambulanceId/location"

    companion object {
        fun from(
            location: Location,
            ambulanceId: String,
            severity: String,
            timestamp: Long = System.currentTimeMillis()
        ): LocationPayload {
            return LocationPayload(
                ambulanceId = ambulanceId,
                lat = location.latitude,
                longitude = location.longitude,
                severity = severity,
                speed = if (location.hasSpeed()) location.speed.toDouble() else 0.0,
                altitude = if (location.hasAltitude()) location.altitude else null,
                accuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
                bearing = if (location.hasBearing()) location.bearing.toDouble() else null,
                timestamp = timestamp
            )
        }
    }
}

private fun JSONObject.putNullable(key: String, value: Double?): JSONObject {
    return if (value == null) put(key, JSONObject.NULL) else put(key, value)
}
