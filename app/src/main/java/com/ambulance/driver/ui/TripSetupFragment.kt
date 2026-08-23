package com.ambulance.driver.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.ambulance.driver.R
import com.ambulance.driver.data.Hospital
import com.ambulance.driver.data.JourneyState
import com.ambulance.driver.data.SampleHospitals
import com.ambulance.driver.databinding.FragmentTripSetupBinding
import com.ambulance.driver.location.LocationForegroundService

class TripSetupFragment : Fragment() {

    private var _binding: FragmentTripSetupBinding? = null
    private val binding get() = _binding!!

    private var pendingStart = false
    private var pendingSeverity: String? = null
    private var pendingHospital: Hospital? = null
    private var selectedHospital: Hospital? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            continuePermissionSequence()
        } else {
            pendingStart = false
            showPermissionDenied(lastRequestedMessage())
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTripSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val hospitals = SampleHospitals.all
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            hospitals.map { it.name }
        )
        binding.destination.setAdapter(adapter)
        binding.destination.setOnClickListener { binding.destination.showDropDown() }
        binding.destination.setOnItemClickListener { parent, _, position, _ ->
            val name = parent.getItemAtPosition(position) as String
            val hospital = hospitals.first { it.name == name }
            selectedHospital = hospital
            binding.hospitalCoords.text = getString(
                R.string.hospital_coords,
                hospital.latitude,
                hospital.longitude
            )
        }

        binding.startButton.setOnClickListener { onStartClicked() }
    }

    private fun onStartClicked() {
        val severity = selectedSeverity()
        if (severity == null) {
            Toast.makeText(requireContext(), R.string.select_severity, Toast.LENGTH_SHORT).show()
            return
        }
        val selectedName = binding.destination.text?.toString()?.trim().orEmpty()
        val hospital = selectedHospital
            ?: SampleHospitals.all.firstOrNull { it.name == selectedName }
        selectedHospital = hospital
        if (hospital == null) {
            Toast.makeText(requireContext(), R.string.select_hospital, Toast.LENGTH_SHORT).show()
            return
        }
        pendingStart = true
        pendingSeverity = severity
        pendingHospital = hospital
        continuePermissionSequence()
    }

    private fun selectedSeverity(): String? {
        return when (binding.severityGroup.checkedRadioButtonId) {
            R.id.severityLow -> getString(R.string.severity_low)
            R.id.severityMedium -> getString(R.string.severity_medium)
            R.id.severityHigh -> getString(R.string.severity_high)
            R.id.severityCritical -> getString(R.string.severity_critical)
            else -> null
        }
    }

    /**
     * System dialogs only, in the required order:
     * fine location → coarse location → notifications (API 33+) → background location.
     */
    private fun continuePermissionSequence() {
        if (!pendingStart) return

        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        if (!hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        ) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.perm_required_title)
                .setMessage(R.string.perm_bg_rationale)
                .setPositiveButton(R.string.continue_label) { _, _ ->
                    permissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    pendingStart = false
                    showPermissionDenied(getString(R.string.perm_bg_denied))
                }
                .show()
            return
        }

        startJourneyService()
    }

    private fun lastRequestedMessage(): String {
        return when {
            !hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ->
                getString(R.string.perm_fine_denied)
            !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ->
                getString(R.string.perm_coarse_denied)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !hasPermission(Manifest.permission.POST_NOTIFICATIONS) ->
                getString(R.string.perm_notifications_denied)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                !hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) ->
                getString(R.string.perm_bg_denied)
            else -> getString(R.string.perm_location_required)
        }
    }

    private fun showPermissionDenied(message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.perm_required_title)
            .setMessage(message)
            .setPositiveButton(R.string.retry) { _, _ ->
                pendingStart = true
                continuePermissionSequence()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startJourneyService() {
        pendingStart = false
        val severity = pendingSeverity ?: return
        val hospital = pendingHospital ?: return
        val destination = "${hospital.name} (${hospital.latitude}, ${hospital.longitude})"
        JourneyState.start(severity, destination)
        val intent = Intent(requireContext(), LocationForegroundService::class.java)
            .putExtra(LocationForegroundService.EXTRA_SEVERITY, severity)
            .putExtra(LocationForegroundService.EXTRA_DESTINATION, destination)
        ContextCompat.startForegroundService(requireContext(), intent)
        findNavController().navigate(R.id.action_trip_to_journey)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
