package com.ambulance.driver.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.ambulance.driver.R
import com.ambulance.driver.data.AppPrefs
import com.ambulance.driver.data.JourneyState
import com.ambulance.driver.data.LocationPayload
import com.ambulance.driver.data.OfflineMessageQueue
import com.ambulance.driver.mqtt.MqttPublisher
import com.ambulance.driver.ui.MainActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class LocationForegroundService : LifecycleService() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var mqtt: MqttPublisher
    private lateinit var prefs: AppPrefs
    private var severity: String = "Medium"

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            onLocation(location)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = AppPrefs(this)
        mqtt = MqttPublisher(prefs, OfflineMessageQueue(this))
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopJourney()
            return START_NOT_STICKY
        }

        severity = intent?.getStringExtra(EXTRA_SEVERITY) ?: JourneyState.severity.value
        val destination = intent?.getStringExtra(EXTRA_DESTINATION) ?: JourneyState.destination.value
        JourneyState.start(severity, destination)

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        mqtt.connect()
        startLocationUpdates()
        return START_STICKY
    }

    private fun startLocationUpdates() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            Log.e(TAG, "Location permission missing; stopping service")
            stopJourney()
            return
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
            .setMaxUpdateDelayMillis(UPDATE_INTERVAL_MS)
            .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            .setWaitForAccurateLocation(false)
            .build()

        fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        fusedClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) onLocation(loc)
        }
    }

    private fun onLocation(location: Location) {
        val timestamp = System.currentTimeMillis()
        JourneyState.updateLocation(location.latitude, location.longitude, timestamp)
        val payload = LocationPayload(
            ambulanceId = prefs.ambulanceId,
            lat = location.latitude,
            longitude = location.longitude,
            severity = severity,
            timestamp = timestamp
        )
        mqtt.publish(payload)
    }

    private fun stopJourney() {
        runCatching { fusedClient.removeLocationUpdates(locationCallback) }
        mqtt.disconnect()
        JourneyState.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        runCatching { fusedClient.removeLocationUpdates(locationCallback) }
        mqtt.disconnect()
        if (JourneyState.active.value) {
            JourneyState.stop()
        }
        super.onDestroy()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_JOURNEY, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_stat_ambulance)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.ambulance.driver.STOP_JOURNEY"
        const val EXTRA_SEVERITY = "severity"
        const val EXTRA_DESTINATION = "destination"
        private const val CHANNEL_ID = "journey_tracking"
        private const val NOTIFICATION_ID = 42
        private const val UPDATE_INTERVAL_MS = 4_000L
        private const val MIN_UPDATE_INTERVAL_MS = 3_000L
        private const val TAG = "LocationService"
    }
}
