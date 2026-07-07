package com.danswirsky.myeats.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.danswirsky.myeats.data.model.Recipe
import com.danswirsky.myeats.data.model.User
import com.danswirsky.myeats.data.repository.AuthRepository
import com.danswirsky.myeats.data.repository.RecipeRepository
import com.danswirsky.myeats.data.repository.UserRepository

class ProfileViewModel : ViewModel() {

    /** count of my recipes + weighted average rating across them (0.0 if unrated) */
    data class Stats(val recipeCount: Int, val avgRating: Double)

    private val recipeRepository = RecipeRepository()
    private val userRepository = UserRepository()
    private val authRepository = AuthRepository()

    private val uid: String = authRepository.currentUser?.uid ?: ""

    val email: String? = authRepository.currentUser?.email

    private val _user = MutableLiveData<User?>()
    val user: LiveData<User?> = _user

    /** Indexed server-side query — only this user's recipes are downloaded. */
    val myRecipes: LiveData<List<Recipe>> = recipeRepository.observeByOwner(uid)

    val stats = MediatorLiveData<Stats>().apply {
        addSource(myRecipes) { mine ->
            val ratingSum = mine.sumOf { it.ratingSum }
            val ratingCount = mine.sumOf { it.ratingCount }
            value = Stats(
                recipeCount = mine.size,
                avgRating = if (ratingCount > 0) ratingSum.toDouble() / ratingCount else 0.0,
            )
        }
    }

    init {
        refresh()
    }

    /** Called again from onResume so edits made in Edit Profile show up. */
    fun refresh() {
        if (uid.isNotEmpty()) {
            userRepository.getUser(uid) { profile -> _user.postValue(profile) }
        }
    }

    fun logout() = authRepository.logout()
}
