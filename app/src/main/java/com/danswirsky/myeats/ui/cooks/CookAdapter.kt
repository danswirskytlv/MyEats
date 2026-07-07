package com.danswirsky.myeats.ui.cooks

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.danswirsky.myeats.R
import com.danswirsky.myeats.data.model.User
import com.danswirsky.myeats.databinding.ItemCookBinding

class CookAdapter(
    private val onClick: (User) -> Unit,
) : ListAdapter<User, CookAdapter.CookViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User) = oldItem.uid == newItem.uid
        override fun areContentsTheSame(oldItem: User, newItem: User) = oldItem == newItem
    }

    inner class CookViewHolder(private val binding: ItemCookBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(user: User) {
            binding.cookName.text = user.name
            binding.cookItemBio.text = user.bio
            binding.cookRecipeCount.text = binding.root.context.getString(
                R.string.cook_recipe_count_format, user.recipeCount.toInt(),
            )

            if (user.photoUrl.isNotEmpty()) {
                binding.cookItemAvatar.setPadding(0, 0, 0, 0)
                Glide.with(binding.root).load(user.photoUrl).circleCrop()
                    .into(binding.cookItemAvatar)
            } else {
                val pad = (10 * binding.root.resources.displayMetrics.density).toInt()
                binding.cookItemAvatar.setPadding(pad, pad, pad, pad)
                binding.cookItemAvatar.setImageResource(R.drawable.ic_utensils)
            }

            binding.root.setOnClickListener { onClick(user) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CookViewHolder {
        val binding = ItemCookBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CookViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CookViewHolder, position: Int) = holder.bind(getItem(position))
}
