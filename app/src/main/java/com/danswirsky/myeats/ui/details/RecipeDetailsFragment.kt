package com.danswirsky.myeats.ui.details

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.danswirsky.myeats.R
import com.danswirsky.myeats.data.model.Comment
import com.danswirsky.myeats.data.model.Recipe
import com.danswirsky.myeats.databinding.FragmentDetailsBinding
import com.danswirsky.myeats.util.SignalManager
import com.danswirsky.myeats.util.dismissKeyboardOnTapOutside
import java.util.Locale

class RecipeDetailsFragment : Fragment() {

    private var _binding: FragmentDetailsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DetailsViewModel by viewModels()
    private val commentAdapter = CommentAdapter(
        onLike = { comment ->
            SignalManager.getInstance().vibrate()
            viewModel.toggleCommentLike(comment)
        },
        onEdit = { comment -> showEditCommentDialog(comment) },
        onDelete = { comment -> confirmDeleteComment(comment) },
    )

    private var isFavorite = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recipeId = arguments?.getString("recipeId") ?: return
        viewModel.start(recipeId)

        binding.detailsBack.setOnClickListener { findNavController().popBackStack() }

        // Tapping outside the comment field closes the keyboard
        dismissKeyboardOnTapOutside(binding.root)

        viewModel.recipe.observe(viewLifecycleOwner) { recipe ->
            if (recipe != null) show(recipe)
        }
        viewModel.favorites.observe(viewLifecycleOwner) { favorites ->
            isFavorite = recipeId in favorites
            binding.favoriteButton.setIconResource(
                if (isFavorite) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
            )
        }
        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }

        binding.favoriteButton.setOnClickListener {
            SignalManager.getInstance().vibrate()
            viewModel.setFavorite(!isFavorite)
        }
        binding.userRatingBar.setOnRatingBarChangeListener { _, rating, fromUser ->
            if (fromUser && rating > 0) {
                SignalManager.getInstance().vibrate()
                viewModel.rate(rating.toLong())
            }
        }

        binding.shareButton.setOnClickListener { shareRecipe() }

        // Comments
        commentAdapter.currentUid = viewModel.uid ?: ""
        binding.commentsList.layoutManager = LinearLayoutManager(requireContext())
        binding.commentsList.adapter = commentAdapter
        viewModel.comments.observe(viewLifecycleOwner) { comments ->
            commentAdapter.submitList(comments)
            binding.commentsHeader.text =
                getString(R.string.comments_title_format, comments.size)
            binding.commentsEmpty.visibility =
                if (comments.isEmpty()) View.VISIBLE else View.GONE
        }
        viewModel.postingComment.observe(viewLifecycleOwner) { posting ->
            binding.sendCommentButton.isEnabled = !posting
        }
        binding.sendCommentButton.setOnClickListener {
            val text = binding.commentInput.text.toString().trim()
            if (text.isNotEmpty()) {
                viewModel.postComment(text)
                binding.commentInput.text?.clear()
            }
        }

        binding.editButton.setOnClickListener {
            findNavController().navigate(
                R.id.action_details_to_edit,
                bundleOf("recipeId" to recipeId),
            )
        }
        binding.deleteButton.setOnClickListener { confirmDelete() }
    }

    /** Opens the system share sheet (implicit ACTION_SEND intent). */
    private fun shareRecipe() {
        val recipe = viewModel.recipe.value ?: return
        val text = buildString {
            appendLine("${recipe.title} — ${getString(R.string.details_by_format, recipe.ownerName)} (MyEats)")
            appendLine()
            appendLine(getString(R.string.details_ingredients) + ":")
            appendLine(recipe.ingredients)
            appendLine()
            appendLine(getString(R.string.details_steps) + ":")
            appendLine(recipe.steps)
        }
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, recipe.title)
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        startActivity(
            android.content.Intent.createChooser(intent, getString(R.string.share_chooser_title))
        )
    }

    /** Small dialog with the current text, prefilled, for editing your comment. */
    private fun showEditCommentDialog(comment: Comment) {
        val layout = android.widget.FrameLayout(requireContext())
        val input = android.widget.EditText(requireContext()).apply {
            setText(comment.text)
            setSelection(comment.text.length)
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        layout.setPadding(pad, 0, pad, 0)
        layout.addView(input)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.edit_comment_title)
            .setView(layout)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save_changes) { _, _ ->
                val newText = input.text.toString().trim()
                if (newText.isNotEmpty() && newText != comment.text) {
                    viewModel.editComment(comment, newText)
                }
            }
            .show()
    }

    private fun confirmDeleteComment(comment: Comment) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_comment_title)
            .setMessage(R.string.delete_comment_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.deleteComment(comment)
            }
            .show()
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_confirm_title)
            .setMessage(R.string.delete_confirm_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.delete {
                    if (_binding != null) {
                        Toast.makeText(requireContext(), R.string.recipe_deleted, Toast.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                    }
                }
            }
            .show()
    }

    private fun show(recipe: Recipe) {
        binding.detailsTitle.text = recipe.title
        binding.detailsOwner.text = getString(R.string.details_by_format, recipe.ownerName)
        // Tapping the author opens their cook page with all their recipes
        binding.detailsOwner.setOnClickListener {
            findNavController().navigate(
                R.id.action_details_to_user,
                bundleOf("ownerUid" to recipe.ownerUid, "ownerName" to recipe.ownerName),
            )
        }
        binding.detailsCategory.text = recipe.categoriesText
        binding.detailsIngredients.text = recipe.ingredients
        binding.detailsSteps.text = recipe.steps
        binding.detailsRating.text = if (recipe.ratingCount > 0) {
            getString(
                R.string.details_rating_format,
                String.format(Locale.US, "%.1f", recipe.avgRating),
                recipe.ratingCount,
            )
        } else {
            getString(R.string.details_no_ratings)
        }

        // Show the user's own rating without re-triggering the listener (fromUser=false)
        val myRating = viewModel.uid?.let { recipe.ratings[it] } ?: 0L
        binding.userRatingBar.rating = myRating.toFloat()

        // Edit/Delete are visible only to the recipe's owner
        val isOwner = recipe.ownerUid == viewModel.uid
        binding.ownerActions.visibility = if (isOwner) View.VISIBLE else View.GONE

        // The owner's comments get the "Chef" badge
        commentAdapter.recipeOwnerUid = recipe.ownerUid

        Glide.with(this)
            .load(recipe.imageUrl.ifEmpty { R.drawable.bg_image_placeholder })
            .placeholder(R.drawable.bg_image_placeholder)
            .centerCrop()
            .into(binding.detailsImage)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
