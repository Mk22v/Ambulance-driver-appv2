package com.ambulance.driver.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import com.ambulance.driver.R
import com.ambulance.driver.data.AppPrefs
import com.ambulance.driver.data.JourneyState
import com.ambulance.driver.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            restoreNavigation(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        restoreNavigation(intent)
    }

    private fun restoreNavigation(intent: Intent) {
        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
            ?: return
        val navController = navHost.navController
        val dest = navController.currentDestination?.id
        val openJourney = intent.getBooleanExtra(EXTRA_OPEN_JOURNEY, false) || JourneyState.active.value
        val prefs = AppPrefs(this)

        if (openJourney) {
            if (dest == R.id.journeyFragment) return
            if (dest == R.id.loginFragment) {
                navController.navigate(R.id.action_login_to_trip)
            }
            if (navController.currentDestination?.id == R.id.tripSetupFragment) {
                navController.navigate(R.id.action_trip_to_journey)
            }
            return
        }

        if (prefs.loggedIn && dest == R.id.loginFragment) {
            navController.navigate(R.id.action_login_to_trip)
        }
    }

    companion object {
        const val EXTRA_OPEN_JOURNEY = "open_journey"
    }
}
