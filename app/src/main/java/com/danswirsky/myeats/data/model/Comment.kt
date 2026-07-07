package com.danswirsky.myeats.data.model

import com.google.firebase.database.Exclude

/**
 * A comment on a recipe, stored under comments/{recipeId}/{commentId}.
 * Kept in a separate top-level node so recipe reads stay small.
 */
data class Comment(
    var id: String = "",
    var recipeId: String = "",
    var authorUid: String = "",
    var authorName: String = "",
    var authorPhotoUrl: String = "",
    var text: String = "",
    var createdAt: Long = 0L,
    /** True once the author edited the text ("· edited" label). */
    var edited: Boolean = false,
    /** uid -> true. One like per user; liking again removes it. */
    var likes: Map<String, Boolean> = emptyMap(),
) {
    @get:Exclude
    val likeCount: Int
        get() = likes.size

    @Exclude
    fun isLikedBy(uid: String?): Boolean = uid != null && likes.containsKey(uid)
}
