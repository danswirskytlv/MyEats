package com.danswirsky.myeats.data.model

/** A user profile stored under users/{uid} in the Realtime Database. */
data class User(
    var uid: String = "",
    var name: String = "",
    /** Lowercased copy of the name — enables indexed server-side prefix search. */
    var nameLower: String = "",
    var email: String = "",
    /** Denormalized count, maintained on upload/delete — avoids downloading all recipes. */
    var recipeCount: Long = 0L,
    /** Profile photo in Storage (profile_images/{uid}.jpg), empty if none. */
    var photoUrl: String = "",
    /** Short free-text bio ("Home cook from Tel Aviv, obsessed with pastry"). */
    var bio: String = "",
)
