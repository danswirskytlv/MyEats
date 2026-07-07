package com.danswirsky.myeats.data.model

import com.google.firebase.database.Exclude

/**
 * A single recipe stored under recipes/{id} in the Realtime Database.
 * All fields have defaults so Firebase can deserialize with the empty constructor.
 */
data class Recipe(
    var id: String = "",
    var title: String = "",
    /** Lowercased copy of the title — enables indexed server-side prefix search. */
    var titleLower: String = "",
    /** word -> true, extracted from the ingredients — enables server-side ingredient search. */
    var ingredientKeywords: Map<String, Boolean> = emptyMap(),
    var ownerUid: String = "",
    var ownerName: String = "",
    /** A recipe can belong to several categories, e.g. Lunch + Dinner. */
    var categories: List<String> = emptyList(),
    var ingredients: String = "",
    var steps: String = "",
    var imageUrl: String = "",
    var createdAt: Long = 0L,
    var ratingSum: Long = 0L,
    var ratingCount: Long = 0L,
    /** Denormalized average — stored so the server can sort "Top rated" pages. */
    var ratingAvg: Double = 0.0,
    /** uid -> stars (1-5). One rating per user; re-rating overwrites. */
    var ratings: Map<String, Long> = emptyMap(),
) {
    @get:Exclude
    val avgRating: Double
        get() = if (ratingCount > 0) ratingSum.toDouble() / ratingCount else 0.0

    @get:Exclude
    val categoriesText: String
        get() = categories.joinToString(" · ")
}
