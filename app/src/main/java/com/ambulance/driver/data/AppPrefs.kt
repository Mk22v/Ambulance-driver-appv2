package com.ambulance.driver.data

import android.content.Context

class AppPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var ambulanceId: String
        get() = prefs.getString(KEY_AMBULANCE_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_AMBULANCE_ID, value.trim()).apply()

    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USERNAME, value.trim()).apply()

    var loggedIn: Boolean
        get() = prefs.getBoolean(KEY_LOGGED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_LOGGED_IN, value).apply()

    fun saveLogin(ambulanceId: String, username: String) {
        prefs.edit()
            .putString(KEY_AMBULANCE_ID, ambulanceId.trim())
            .putString(KEY_USERNAME, username.trim())
            .putBoolean(KEY_LOGGED_IN, true)
            .apply()
    }

    fun logout() {
        prefs.edit().putBoolean(KEY_LOGGED_IN, false).apply()
    }

    companion object {
        private const val PREFS_NAME = "ambulance_driver_prefs"
        private const val KEY_AMBULANCE_ID = "ambulance_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_LOGGED_IN = "logged_in"
    }
}
