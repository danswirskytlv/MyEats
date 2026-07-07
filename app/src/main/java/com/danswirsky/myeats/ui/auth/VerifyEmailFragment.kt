package com.danswirsky.myeats.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.danswirsky.myeats.R
import com.danswirsky.myeats.databinding.FragmentVerifyEmailBinding

/**
 * Shown after registration (or login with an unverified account).
 * The user must open the verification link we emailed before entering the app.
 */
class VerifyEmailFragment : Fragment() {

    private var _binding: FragmentVerifyEmailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVerifyEmailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.verifyMessage.text =
            getString(R.string.verify_message_format, viewModel.userEmail ?: "")

        binding.verifiedButton.setOnClickListener { viewModel.checkVerified() }
        binding.resendButton.setOnClickListener { viewModel.resendVerification() }
        binding.backToLoginButton.setOnClickListener {
            viewModel.logout()
            findNavController().popBackStack(R.id.loginFragment, false)
        }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.verifyProgress.visibility = if (loading) View.VISIBLE else View.GONE
            binding.verifiedButton.isEnabled = !loading
            binding.resendButton.isEnabled = !loading
        }
        viewModel.errorRes.observe(viewLifecycleOwner) { errorRes ->
            if (errorRes != null) {
                Toast.makeText(requireContext(), errorRes, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
        viewModel.infoRes.observe(viewLifecycleOwner) { infoRes ->
            if (infoRes != null) {
                Toast.makeText(requireContext(), infoRes, Toast.LENGTH_SHORT).show()
                viewModel.clearInfo()
            }
        }
        viewModel.event.observe(viewLifecycleOwner) { event ->
            if (event == AuthViewModel.AuthEvent.VERIFIED) {
                viewModel.clearEvent()
                (requireActivity() as AuthActivity).goToMain()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
