package com.danswirsky.myeats.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.danswirsky.myeats.R
import com.danswirsky.myeats.databinding.FragmentLoginBinding
import com.danswirsky.myeats.util.dismissKeyboardOnTapOutside

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dismissKeyboardOnTapOutside(binding.root)

        binding.loginButton.setOnClickListener { attemptLogin() }
        binding.forgotPasswordButton.setOnClickListener { attemptPasswordReset() }
        binding.goToRegisterButton.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_register)
        }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.loginProgress.visibility = if (loading) View.VISIBLE else View.GONE
            binding.loginButton.isEnabled = !loading
        }
        viewModel.errorRes.observe(viewLifecycleOwner) { errorRes ->
            if (errorRes != null) {
                showMessage(errorRes, isError = true)
                viewModel.clearError()
            }
        }
        viewModel.infoRes.observe(viewLifecycleOwner) { infoRes ->
            if (infoRes != null) {
                showMessage(infoRes, isError = false)
                viewModel.clearInfo()
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
                    findNavController().navigate(R.id.action_login_to_verify)
                }
                null -> Unit
            }
        }
    }

    /**
     * Security: any problem — bad format, unknown email, wrong password —
     * produces the same generic message, so the app never reveals whether
     * an account exists.
     */
    private fun attemptLogin() {
        binding.loginError.visibility = View.GONE
        val email = binding.emailInput.text.toString().trim()
        val password = binding.passwordInput.text.toString()

        val locallyValid =
            android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() && password.length >= 6
        if (!locallyValid) {
            showMessage(R.string.error_invalid_credentials, isError = true)
            return
        }
        viewModel.login(email, password)
    }

    /** Sends a reset link to the address typed in the email field. */
    private fun attemptPasswordReset() {
        binding.loginError.visibility = View.GONE
        val email = binding.emailInput.text.toString().trim()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showMessage(R.string.error_reset_needs_email, isError = true)
            return
        }
        viewModel.sendPasswordReset(email)
    }

    /** Inline message inside the form — always visible, even with the keyboard open. */
    private fun showMessage(messageRes: Int, isError: Boolean) {
        binding.loginError.setText(messageRes)
        binding.loginError.setTextColor(
            androidx.core.content.ContextCompat.getColor(
                requireContext(),
                if (isError) R.color.brick else R.color.primary_green,
            )
        )
        binding.loginError.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
