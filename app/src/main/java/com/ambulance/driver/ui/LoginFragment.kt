package com.ambulance.driver.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.ambulance.driver.R
import com.ambulance.driver.data.AppPrefs
import com.ambulance.driver.databinding.FragmentLoginBinding

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: AppPrefs

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = AppPrefs(requireContext())
        binding.ambulanceId.setText(prefs.ambulanceId)
        binding.username.setText(prefs.username)

        binding.loginButton.setOnClickListener { onLogin() }
    }

    private fun onLogin() {
        val ambulanceId = binding.ambulanceId.text?.toString()?.trim().orEmpty()
        val username = binding.username.text?.toString()?.trim().orEmpty()
        val password = binding.password.text?.toString().orEmpty()

        if (ambulanceId.isBlank() || username.isBlank() || password.isBlank()) {
            Toast.makeText(requireContext(), R.string.fill_login, Toast.LENGTH_SHORT).show()
            return
        }

        prefs.saveLogin(ambulanceId, username)
        findNavController().navigate(R.id.action_login_to_trip)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
