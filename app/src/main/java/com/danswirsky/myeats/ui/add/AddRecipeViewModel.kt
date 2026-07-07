package com.danswirsky.myeats.ui.add

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.danswirsky.myeats.data.model.Recipe
import com.danswirsky.myeats.data.repository.AuthRepository
import com.danswirsky.myeats.data.repository.RecipeRepository
import com.danswirsky.myeats.data.repository.UserRepository

/** Handles both creating a new recipe and editing an existing one. */
class AddRecipeViewModel : ViewModel() {

    private val recipeRepository = RecipeRepository()
    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()

    /** The picked image, already compressed to JPEG — survives rotation. */
    var pickedImageBytes: ByteArray? = null

    /** Categories the user checked — survives rotation. */
    val selectedCategories = mutableSetOf<String>()

    /** Set when the screen was opened to edit an existing recipe. */
    private var editId: String? = null
    val isEditing: Boolean get() = editId != null

    private val _editRecipe = MutableLiveData<Recipe?>()
    val editRecipe: LiveData<Recipe?> = _editRecipe

    /** True once the form fields were filled from the loaded recipe. */
    var formPrefilled = false

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _done = MutableLiveData(false)
    val done: LiveData<Boolean> = _done

    /** Idempotent — safe to call again after rotation. */
    fun startEdit(recipeId: String) {
        if (editId != null) return
        editId = recipeId
        recipeRepository.getRecipe(recipeId) { recipe ->
            if (recipe != null && !formPrefilled) {
                selectedCategories.clear()
                selectedCategories.addAll(recipe.categories)
            }
            _editRecipe.postValue(recipe)
        }
    }

    fun save(title: String, ingredients: String, steps: String) {
        val user = authRepository.currentUser ?: run {
            _error.value = "Not signed in"
            return
        }
        _loading.value = true

        val existing = _editRecipe.value
        if (isEditing && existing != null) {
            // Keep identity, timestamps and ratings; replace the edited fields
            val updated = existing.copy(
                title = title,
                categories = selectedCategories.toList(),
                ingredients = ingredients,
                steps = steps,
            )
            recipeRepository.updateRecipe(updated, pickedImageBytes) { error -> finish(error) }
        } else {
            userRepository.getUser(user.uid) { profile ->
                val recipe = Recipe(
                    title = title,
                    ownerUid = user.uid,
                    ownerName = profile?.name ?: (user.email ?: "Unknown"),
                    categories = selectedCategories.toList(),
                    ingredients = ingredients,
                    steps = steps,
                )
                recipeRepository.uploadRecipe(recipe, pickedImageBytes) { error -> finish(error) }
            }
        }
    }

    private fun finish(error: String?) {
        _loading.value = false
        if (error == null) {
            pickedImageBytes = null
            _done.value = true
        } else {
            _error.value = error
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun resetDone() {
        _done.value = false
    }
}
