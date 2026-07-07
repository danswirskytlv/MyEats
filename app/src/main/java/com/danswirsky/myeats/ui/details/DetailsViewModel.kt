package com.danswirsky.myeats.ui.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.danswirsky.myeats.data.model.Comment
import com.danswirsky.myeats.data.model.Recipe
import com.danswirsky.myeats.data.repository.AuthRepository
import com.danswirsky.myeats.data.repository.CommentRepository
import com.danswirsky.myeats.data.repository.RecipeRepository
import com.danswirsky.myeats.data.repository.UserRepository

class DetailsViewModel : ViewModel() {

    private val recipeRepository = RecipeRepository()
    private val userRepository = UserRepository()
    private val authRepository = AuthRepository()
    private val commentRepository = CommentRepository()

    val uid: String?
        get() = authRepository.currentUser?.uid

    private var recipeId: String? = null

    lateinit var recipe: LiveData<Recipe?>
        private set
    lateinit var favorites: LiveData<Set<String>>
        private set
    lateinit var comments: LiveData<List<Comment>>
        private set

    private val _postingComment = MutableLiveData(false)
    val postingComment: LiveData<Boolean> = _postingComment

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    /** Idempotent — safe to call again after rotation. */
    fun start(id: String) {
        if (recipeId != null) return
        recipeId = id
        recipe = recipeRepository.observeRecipe(id)
        favorites = userRepository.favorites(uid ?: "")
        comments = commentRepository.observeComments(id)
    }

    /** Like/unlike a comment — one like per user. */
    fun toggleCommentLike(comment: Comment) {
        val user = uid ?: return
        commentRepository.setLike(
            comment.recipeId, comment.id, user, !comment.isLikedBy(user),
        ) { error -> if (error != null) _error.value = error }
    }

    /** Author-only edit. */
    fun editComment(comment: Comment, newText: String) {
        if (comment.authorUid != uid) return
        commentRepository.updateComment(comment.recipeId, comment.id, newText) { error ->
            if (error != null) _error.value = error
        }
    }

    /** Author-only delete. */
    fun deleteComment(comment: Comment) {
        if (comment.authorUid != uid) return
        commentRepository.deleteComment(comment.recipeId, comment.id) { error ->
            if (error != null) _error.value = error
        }
    }

    /** Posts a comment as the signed-in user (with their name and avatar). */
    fun postComment(text: String) {
        val id = recipeId ?: return
        val user = authRepository.currentUser ?: return
        _postingComment.value = true
        userRepository.getUser(user.uid) { profile ->
            val comment = Comment(
                recipeId = id,
                authorUid = user.uid,
                authorName = profile?.name ?: (user.email ?: "Unknown"),
                authorPhotoUrl = profile?.photoUrl ?: "",
                text = text,
            )
            commentRepository.addComment(comment) { error ->
                _postingComment.value = false
                if (error != null) _error.value = error
            }
        }
    }

    fun rate(stars: Long) {
        val id = recipeId ?: return
        val user = uid ?: return
        recipeRepository.rate(id, user, stars) { error -> _error.value = error }
    }

    fun setFavorite(favorite: Boolean) {
        val id = recipeId ?: return
        val user = uid ?: return
        userRepository.setFavorite(user, id, favorite) { error -> _error.value = error }
    }

    /** Owner-only. Calls [onDeleted] on success so the UI can navigate back. */
    fun delete(onDeleted: () -> Unit) {
        val current = recipe.value ?: return
        if (current.ownerUid != uid) return
        recipeRepository.deleteRecipe(current) { error ->
            if (error == null) onDeleted() else _error.value = error
        }
    }

    fun clearError() {
        _error.value = null
    }
}
