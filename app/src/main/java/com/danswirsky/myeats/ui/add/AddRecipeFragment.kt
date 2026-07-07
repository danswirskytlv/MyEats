package com.danswirsky.myeats.ui.add

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
import com.danswirsky.myeats.databinding.FragmentAddRecipeBinding
import com.danswirsky.myeats.util.ImageUtils
import com.danswirsky.myeats.util.dismissKeyboardOnTapOutside
import com.danswirsky.myeats.util.hideKeyboard
import com.google.android.material.chip.Chip

class AddRecipeFragment : Fragment() {

    private var _binding: FragmentAddRecipeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AddRecipeViewModel by viewModels()

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                // Compress immediately so uploads are small and fast
                binding.pickImageButton.isEnabled = false
                ImageUtils.compressToJpeg(requireContext(), uri) { bytes ->
                    if (_binding == null) return@compressToJpeg
                    binding.pickImageButton.isEnabled = true
                    if (bytes != null) {
                        viewModel.pickedImageBytes = bytes
                        showPickedImage()
                    } else {
                        Toast.makeText(requireContext(), R.string.error_image_load, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAddRecipeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val editRecipeId = arguments?.getString("recipeId")
        if (editRecipeId != null) viewModel.startEdit(editRecipeId)

        // Tapping outside a text field closes the keyboard
        dismissKeyboardOnTapOutside(binding.root)

        buildCategoryChips()

        if (viewModel.isEditing) {
            binding.addHeader.text = getString(R.string.edit_title)
            binding.uploadButton.text = getString(R.string.action_save_changes)
            binding.addBack.visibility = View.VISIBLE
            binding.addBack.setOnClickListener { findNavController().popBackStack() }
            viewModel.editRecipe.observe(viewLifecycleOwner) { recipe ->
                if (recipe != null && !viewModel.formPrefilled) {
                    viewModel.formPrefilled = true
                    binding.titleInput.setText(recipe.title)
                    binding.ingredientsInput.setText(recipe.ingredients)
                    binding.stepsInput.setText(recipe.steps)
                    syncChipsFromViewModel()
                    if (recipe.imageUrl.isNotEmpty() && viewModel.pickedImageBytes == null) {
                        binding.imagePreview.visibility = View.VISIBLE
                        Glide.with(this).load(recipe.imageUrl).centerCrop().into(binding.imagePreview)
                    }
                }
            }
        }

        // Restore picked image after rotation
        if (viewModel.pickedImageBytes != null) showPickedImage()

        binding.pickImageButton.setOnClickListener {
            hideKeyboard()
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.uploadButton.setOnClickListener {
            hideKeyboard()
            attemptSave()
        }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.uploadProgress.visibility = if (loading) View.VISIBLE else View.GONE
            binding.uploadButton.isEnabled = !loading
        }
        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
        viewModel.done.observe(viewLifecycleOwner) { done ->
            if (done) {
                if (viewModel.isEditing) {
                    Toast.makeText(requireContext(), R.string.recipe_updated, Toast.LENGTH_SHORT).show()
                    viewModel.resetDone()
                    findNavController().popBackStack()
                } else {
                    Toast.makeText(requireContext(), R.string.recipe_uploaded, Toast.LENGTH_SHORT).show()
                    clearForm()
                    viewModel.resetDone()
                }
            }
        }
    }

    private fun buildCategoryChips() {
        binding.categoryChips.removeAllViews()
        resources.getStringArray(R.array.recipe_categories).forEach { name ->
            val chip = Chip(requireContext()).apply {
                setTextAppearanceResource(R.style.TextAppearance_MyEats_Chip)
                text = name
                isCheckable = true
                isChecked = name in viewModel.selectedCategories
                setOnCheckedChangeListener { _, checked ->
                    if (checked) viewModel.selectedCategories.add(name)
                    else viewModel.selectedCategories.remove(name)
                }
            }
            binding.categoryChips.addView(chip)
        }
    }

    private fun syncChipsFromViewModel() {
        for (i in 0 until binding.categoryChips.childCount) {
            val chip = binding.categoryChips.getChildAt(i) as Chip
            chip.isChecked = chip.text.toString() in viewModel.selectedCategories
        }
    }

    private fun attemptSave() {
        val title = binding.titleInput.text.toString().trim()
        val ingredients = binding.ingredientsInput.text.toString().trim()
        val steps = binding.stepsInput.text.toString().trim()

        binding.titleLayout.error = null
        binding.ingredientsLayout.error = null
        binding.stepsLayout.error = null
        binding.categoryError.visibility = View.GONE

        var valid = true
        if (title.isEmpty()) {
            binding.titleLayout.error = getString(R.string.error_required)
            valid = false
        }
        if (viewModel.selectedCategories.isEmpty()) {
            binding.categoryError.visibility = View.VISIBLE
            valid = false
        }
        if (ingredients.isEmpty()) {
            binding.ingredientsLayout.error = getString(R.string.error_required)
            valid = false
        }
        if (steps.isEmpty()) {
            binding.stepsLayout.error = getString(R.string.error_required)
            valid = false
        }
        if (valid) viewModel.save(title, ingredients, steps)
    }

    private fun showPickedImage() {
        binding.imagePreview.visibility = View.VISIBLE
        Glide.with(this).load(viewModel.pickedImageBytes).centerCrop().into(binding.imagePreview)
    }

    private fun clearForm() {
        binding.titleInput.text?.clear()
        binding.ingredientsInput.text?.clear()
        binding.stepsInput.text?.clear()
        viewModel.selectedCategories.clear()
        syncChipsFromViewModel()
        binding.imagePreview.setImageDrawable(null)
        binding.imagePreview.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
