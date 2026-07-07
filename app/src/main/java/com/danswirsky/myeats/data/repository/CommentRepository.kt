package com.danswirsky.myeats.data.repository

import androidx.lifecycle.LiveData
import com.danswirsky.myeats.data.model.Comment
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/** Comments live under comments/{recipeId} — one small, targeted node per recipe. */
class CommentRepository {

    private val commentsRef = FirebaseDatabase.getInstance().reference.child("comments")

    /** Live list of a recipe's comments, oldest first (a conversation reads top-down). */
    fun observeComments(recipeId: String): LiveData<List<Comment>> =
        object : LiveData<List<Comment>>() {
            private val ref = commentsRef.child(recipeId)
            private val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    value = snapshot.children
                        .mapNotNull { it.getValue(Comment::class.java) }
                        .sortedBy { it.createdAt }
                }

                override fun onCancelled(error: DatabaseError) {
                    value = emptyList()
                }
            }

            override fun onActive() {
                ref.addValueEventListener(listener)
            }

            override fun onInactive() {
                ref.removeEventListener(listener)
            }
        }

    fun addComment(comment: Comment, onResult: (String?) -> Unit) {
        val ref = commentsRef.child(comment.recipeId)
        val id = ref.push().key ?: return onResult("Could not create comment id")
        comment.id = id
        comment.createdAt = System.currentTimeMillis()
        ref.child(id).setValue(comment)
            .addOnSuccessListener { onResult(null) }
            .addOnFailureListener { e -> onResult(e.localizedMessage ?: "Failed to post comment") }
    }

    /** Author-only (enforced by the security rules). Marks the comment as edited. */
    fun updateComment(recipeId: String, commentId: String, newText: String, onResult: (String?) -> Unit) {
        commentsRef.child(recipeId).child(commentId)
            .updateChildren(mapOf("text" to newText, "edited" to true))
            .addOnSuccessListener { onResult(null) }
            .addOnFailureListener { e -> onResult(e.localizedMessage ?: "Failed to edit comment") }
    }

    /** Author-only (enforced by the security rules). */
    fun deleteComment(recipeId: String, commentId: String, onResult: (String?) -> Unit) {
        commentsRef.child(recipeId).child(commentId).removeValue()
            .addOnSuccessListener { onResult(null) }
            .addOnFailureListener { e -> onResult(e.localizedMessage ?: "Failed to delete comment") }
    }

    /** Adds or removes the user's like — one like per user under likes/{uid}. */
    fun setLike(recipeId: String, commentId: String, uid: String, liked: Boolean, onResult: (String?) -> Unit) {
        val likeRef = commentsRef.child(recipeId).child(commentId).child("likes").child(uid)
        val task = if (liked) likeRef.setValue(true) else likeRef.removeValue()
        task.addOnSuccessListener { onResult(null) }
            .addOnFailureListener { e -> onResult(e.localizedMessage ?: "Failed to update like") }
    }
}
