package com.ambulance.driver

import android.app.Application

class AmbulanceApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: AmbulanceApp
            private set
    }
}
