package com.danswirsky.myeats.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.danswirsky.myeats.R
import com.danswirsky.myeats.databinding.FragmentEditProfileBinding
import com.danswirsky.myeats.util.ImageUtils
import com.danswirsky.myeats.util.dismissKeyboardOnTapOutside
import com.danswirsky.myeats.util.hideKeyboard

class EditProfileFragment : Fragment() {

    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: EditProfileViewModel by viewModels()

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                binding.changePhotoButton.isEnabled = false
                ImageUtils.compressToJpeg(requireContext(), uri) { bytes ->
                    if (_binding == null) return@compressToJpeg
                    binding.changePhotoButton.isEnabled = true
                    if (bytes != null) {
                        viewModel.pickedImageBytes = bytes
                        showPickedAvatar()
                    } else {
                        Toast.makeText(requireContext(), R.string.error_image_load, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEditProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Tapping outside the bio field closes the keyboard
        dismissKeyboardOnTapOutside(binding.root)

        binding.editProfileBack.setOnClickListener { findNavController().popBackStack() }
        binding.changePhotoButton.setOnClickListener {
            hideKeyboard()
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.saveProfileButton.setOnClickListener {
            hideKeyboard()
            val name = binding.editNameInput.text.toString().trim()
            binding.editNameLayout.error = null
            if (name.isEmpty()) {
                binding.editNameLayout.error = getString(R.string.error_empty_name)
                return@setOnClickListener
            }
            viewModel.save(name, binding.bioInput.text.toString().trim())
        }

        if (viewModel.pickedImageBytes != null) showPickedAvatar()

        viewModel.user.observe(viewLifecycleOwner) { profile ->
            if (profile == null) return@observe
            if (!viewModel.formPrefilled) {
                viewModel.formPrefilled = true
                binding.editNameInput.setText(profile.name)
                binding.bioInput.setText(profile.bio)
            }
            if (viewModel.pickedImageBytes == null && profile.photoUrl.isNotEmpty()) {
                Glide.with(this).load(profile.photoUrl).circleCrop().into(binding.avatarPreview)
            }
        }
        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.editProfileProgress.visibility = if (loading) View.VISIBLE else View.GONE
            binding.saveProfileButton.isEnabled = !loading
        }
        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
        viewModel.done.observe(viewLifecycleOwner) { done ->
            if (done) {
                Toast.makeText(requireContext(), R.string.profile_updated, Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
        }
    }

    private fun showPickedAvatar() {
        Glide.with(this).load(viewModel.pickedImageBytes).circleCrop().into(binding.avatarPreview)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
