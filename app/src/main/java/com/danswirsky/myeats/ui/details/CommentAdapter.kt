package com.danswirsky.myeats.ui.details

import android.content.res.ColorStateList
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.danswirsky.myeats.R
import com.danswirsky.myeats.data.model.Comment
import com.danswirsky.myeats.databinding.ItemCommentBinding

class CommentAdapter(
    private val onLike: (Comment) -> Unit,
    private val onEdit: (Comment) -> Unit,
    private val onDelete: (Comment) -> Unit,
) : ListAdapter<Comment, CommentAdapter.CommentViewHolder>(Diff) {

    /** Uid of the recipe owner — their comments get the "Chef" badge. */
    var recipeOwnerUid: String = ""
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    /** The signed-in user — controls like state and edit/delete visibility. */
    var currentUid: String = ""

    object Diff : DiffUtil.ItemCallback<Comment>() {
        override fun areItemsTheSame(oldItem: Comment, newItem: Comment) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Comment, newItem: Comment) = oldItem == newItem
    }

    inner class CommentViewHolder(private val binding: ItemCommentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(comment: Comment) {
            val context = binding.root.context
            binding.commentAuthor.text = comment.authorName
            binding.commentText.text = comment.text

            val time = DateUtils.getRelativeTimeSpanString(
                comment.createdAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
            )
            binding.commentTime.text = if (comment.edited) {
                context.getString(R.string.comment_time_edited_format, time)
            } else {
                time
            }

            val isOwner = comment.authorUid == recipeOwnerUid && recipeOwnerUid.isNotEmpty()
            binding.chefBadge.visibility = if (isOwner) View.VISIBLE else View.GONE

            // Likes
            val liked = comment.isLikedBy(currentUid)
            binding.likeButton.setImageResource(
                if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
            )
            binding.likeButton.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, if (liked) R.color.brick else R.color.text_secondary)
            )
            binding.likeCount.text = if (comment.likeCount > 0) comment.likeCount.toString() else ""
            binding.likeButton.setOnClickListener { onLike(comment) }

            // Edit/delete only on your own comments
            val isMine = comment.authorUid == currentUid && currentUid.isNotEmpty()
            binding.editCommentButton.visibility = if (isMine) View.VISIBLE else View.GONE
            binding.deleteCommentButton.visibility = if (isMine) View.VISIBLE else View.GONE
            binding.editCommentButton.setOnClickListener { onEdit(comment) }
            binding.deleteCommentButton.setOnClickListener { onDelete(comment) }

            if (comment.authorPhotoUrl.isNotEmpty()) {
                binding.commentAvatar.setPadding(0, 0, 0, 0)
                Glide.with(binding.root).load(comment.authorPhotoUrl).circleCrop()
                    .into(binding.commentAvatar)
            } else {
                val pad = (7 * binding.root.resources.displayMetrics.density).toInt()
                binding.commentAvatar.setPadding(pad, pad, pad, pad)
                binding.commentAvatar.setImageResource(R.drawable.ic_utensils)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) = holder.bind(getItem(position))
}
