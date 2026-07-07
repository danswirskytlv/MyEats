package com.danswirsky.myeats.ui.feed

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.danswirsky.myeats.R
import com.danswirsky.myeats.data.model.Recipe
import com.danswirsky.myeats.databinding.ItemRecipeBinding
import java.util.Locale

class RecipeAdapter(
    private val onClick: (Recipe) -> Unit,
    private val onSelectionChanged: (() -> Unit)? = null,
) : ListAdapter<Recipe, RecipeAdapter.RecipeViewHolder>(Diff) {

    /** When true, items show a checkbox and taps toggle selection instead of opening. */
    var selectionMode = false
        private set

    /** Owned by the caller (e.g. the ViewModel) so selection survives rotation. */
    var selectedIds: MutableSet<String> = mutableSetOf()

    fun setSelectionMode(enabled: Boolean) {
        selectionMode = enabled
        if (!enabled) selectedIds.clear()
        notifyDataSetChanged()
        onSelectionChanged?.invoke()
    }

    object Diff : DiffUtil.ItemCallback<Recipe>() {
        override fun areItemsTheSame(oldItem: Recipe, newItem: Recipe) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Recipe, newItem: Recipe) = oldItem == newItem
    }

    inner class RecipeViewHolder(private val binding: ItemRecipeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(recipe: Recipe) {
            binding.recipeTitle.text = recipe.title
            binding.recipeSubtitle.text = if (recipe.ratingCount > 0) {
                binding.root.context.getString(
                    R.string.recipe_subtitle_format,
                    String.format(Locale.US, "%.1f", recipe.avgRating),
                    recipe.categoriesText,
                )
            } else {
                recipe.categoriesText
            }
            binding.recipeOwner.text = recipe.ownerName

            Glide.with(binding.root)
                .load(recipe.imageUrl.ifEmpty { R.drawable.bg_image_placeholder })
                .placeholder(R.drawable.bg_image_placeholder)
                .centerCrop()
                .into(binding.recipeImage)

            binding.recipeCheckbox.visibility = if (selectionMode) android.view.View.VISIBLE else android.view.View.GONE
            binding.recipeCheckbox.setOnCheckedChangeListener(null)
            binding.recipeCheckbox.isChecked = recipe.id in selectedIds
            binding.recipeCheckbox.setOnCheckedChangeListener { _, checked ->
                if (checked) selectedIds.add(recipe.id) else selectedIds.remove(recipe.id)
                onSelectionChanged?.invoke()
            }

            binding.root.setOnClickListener {
                if (selectionMode) {
                    binding.recipeCheckbox.isChecked = !binding.recipeCheckbox.isChecked
                } else {
                    onClick(recipe)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val binding = ItemRecipeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RecipeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) =
        holder.bind(getItem(position))
}
