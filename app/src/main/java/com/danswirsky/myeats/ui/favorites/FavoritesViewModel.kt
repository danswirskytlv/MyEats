package com.danswirsky.myeats.ui.favorites

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import com.danswirsky.myeats.data.model.Recipe
import com.danswirsky.myeats.data.repository.AuthRepository
import com.danswirsky.myeats.data.repository.RecipeRepository
import com.danswirsky.myeats.data.repository.UserRepository

/**
 * Favorites, built to scale: the live favorites id-set drives targeted
 * per-id fetches — only the user's own favorites are ever downloaded,
 * regardless of how many recipes exist in the database.
 */
class FavoritesViewModel : ViewModel() {

    private val recipeRepository = RecipeRepository()
    private val userRepository = UserRepository()
    private val authRepository = AuthRepository()

    private val uid: String = authRepository.currentUser?.uid ?: ""

    private val favoriteIds: LiveData<Set<String>> = userRepository.favorites(uid)

    /** Selection state lives here so it survives rotation. */
    var selectionMode = false
    val selectedIds = mutableSetOf<String>()

    val favorites = MediatorLiveData<List<Recipe>>().apply {
        addSource(favoriteIds) { ids -> fetch(ids ?: emptySet()) }
    }

    private fun fetch(ids: Set<String>) {
        recipeRepository.fetchByIds(ids) { recipes ->
            favorites.value = recipes
        }
    }

    /** Removes all selected recipes from the user's favorites. */
    fun removeSelected(onDone: (Int) -> Unit) {
        val ids = selectedIds.toList()
        ids.forEach { recipeId ->
            userRepository.setFavorite(uid, recipeId, false) { /* id-set updates live */ }
        }
        selectedIds.clear()
        onDone(ids.size)
    }
}
