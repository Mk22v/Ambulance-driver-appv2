package com.ambulance.driver.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.ambulance.driver.R
import com.ambulance.driver.data.JourneyState
import com.ambulance.driver.data.MqttConnectionStatus
import com.ambulance.driver.databinding.FragmentJourneyBinding
import com.ambulance.driver.location.LocationForegroundService
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class JourneyFragment : Fragment() {

    private var _binding: FragmentJourneyBinding? = null
    private val binding get() = _binding!!
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentJourneyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                confirmEndJourney()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        binding.endButton.setOnClickListener { stopJourney() }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    JourneyState.lat.collect { updateLocationText() }
                }
                launch {
                    JourneyState.lng.collect { updateLocationText() }
                }
                launch {
                    JourneyState.lastUpdatedMillis.collect { millis ->
                        binding.lastUpdated.text = if (millis == null) {
                            getString(R.string.waiting_gps)
                        } else {
                            getString(R.string.last_updated, timeFormat.format(Date(millis)))
                        }
                    }
                }
                launch {
                    JourneyState.mqttStatus.collect { status ->
                        binding.mqttStatus.text = when (status) {
                            MqttConnectionStatus.CONNECTED -> getString(R.string.mqtt_connected)
                            MqttConnectionStatus.DISCONNECTED -> getString(R.string.mqtt_disconnected)
                            MqttConnectionStatus.CONNECTING,
                            MqttConnectionStatus.RECONNECTING -> getString(R.string.mqtt_reconnecting)
                        }
                    }
                }
                launch {
                    JourneyState.queuedCount.collect { count ->
                        binding.queueCount.text = getString(R.string.queued_count, count)
                    }
                }
                launch {
                    JourneyState.active.collect { active ->
                        if (!active && isAdded &&
                            findNavController().currentDestination?.id == R.id.journeyFragment
                        ) {
                            findNavController().popBackStack()
                        }
                    }
                }
            }
        }
    }

    private fun updateLocationText() {
        val lat = JourneyState.lat.value
        val lng = JourneyState.lng.value
        binding.locationValue.text = if (lat != null && lng != null) {
            getString(R.string.gps_coords, lat, lng)
        } else {
            getString(R.string.waiting_gps)
        }
    }

    private fun confirmEndJourney() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.end_journey_title)
            .setMessage(R.string.end_journey_confirm)
            .setPositiveButton(R.string.end_journey) { _, _ -> stopJourney() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun stopJourney() {
        val stop = Intent(requireContext(), LocationForegroundService::class.java)
            .setAction(LocationForegroundService.ACTION_STOP)
        requireContext().startService(stop)
        if (!JourneyState.active.value &&
            findNavController().currentDestination?.id == R.id.journeyFragment
        ) {
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
