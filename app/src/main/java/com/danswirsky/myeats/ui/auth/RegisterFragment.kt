package com.danswirsky.myeats.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.danswirsky.myeats.R
import com.danswirsky.myeats.databinding.FragmentRegisterBinding
import com.danswirsky.myeats.util.dismissKeyboardOnTapOutside

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dismissKeyboardOnTapOutside(binding.root)

        binding.registerButton.setOnClickListener { attemptRegister() }
        binding.goToLoginButton.setOnClickListener { findNavController().popBackStack() }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.registerProgress.visibility = if (loading) View.VISIBLE else View.GONE
            binding.registerButton.isEnabled = !loading
        }
        viewModel.errorRes.observe(viewLifecycleOwner) { errorRes ->
            if (errorRes != null) {
                binding.registerError.setText(errorRes)
                binding.registerError.visibility = View.VISIBLE
                viewModel.clearError()
            }
        }
        viewModel.event.observe(viewLifecycleOwner) { event ->
            when (event) {
                AuthViewModel.AuthEvent.VERIFIED -> {
                    viewModel.clearEvent()
                    (requireActivity() as AuthActivity).goToMain()
                }
                AuthViewModel.AuthEvent.NEEDS_VERIFICATION -> {
                    viewModel.clearEvent()
                    findNavController().navigate(R.id.action_register_to_verify)
                }
                null -> Unit
            }
        }
    }

    private fun attemptRegister() {
        binding.registerError.visibility = View.GONE
        val name = binding.nameInput.text.toString().trim()
        val email = binding.emailInput.text.toString().trim()
        val password = binding.passwordInput.text.toString()
        val confirm = binding.confirmInput.text.toString()

        binding.nameLayout.error = null
        binding.emailLayout.error = null
        binding.passwordLayout.error = null
        binding.confirmLayout.error = null

        // Form-format validation (the rules are public — nothing to hide here);
        // SERVER errors are shown as one generic message in the ViewModel.
        var valid = true
        if (name.isEmpty()) {
            binding.nameLayout.error = getString(R.string.error_empty_name)
            valid = false
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailLayout.error = getString(R.string.error_invalid_email)
            valid = false
        }
        if (password.length < 6) {
            binding.passwordLayout.error = getString(R.string.error_short_password)
            valid = false
        }
        if (confirm != password) {
            binding.confirmLayout.error = getString(R.string.error_password_mismatch)
            valid = false
        }
        if (valid) viewModel.register(name, email, password)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
