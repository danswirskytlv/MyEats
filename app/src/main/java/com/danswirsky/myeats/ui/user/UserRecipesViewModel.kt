package com.danswirsky.myeats.ui.user

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.danswirsky.myeats.data.model.Recipe
import com.danswirsky.myeats.data.model.User
import com.danswirsky.myeats.data.repository.RecipeRepository
import com.danswirsky.myeats.data.repository.UserRepository

/** All recipes uploaded by one specific user (the "cook page"). */
class UserRecipesViewModel : ViewModel() {

    private val repository = RecipeRepository()
    private val userRepository = UserRepository()

    private var ownerUid: String? = null

    lateinit var recipes: LiveData<List<Recipe>>
        private set

    private val _cook = MutableLiveData<User?>()
    val cook: LiveData<User?> = _cook

    /** Idempotent — safe to call again after rotation. */
    fun start(uid: String) {
        if (ownerUid != null) return
        ownerUid = uid
        // Indexed server-side query — only this cook's recipes are downloaded
        recipes = repository.observeByOwner(uid)
        userRepository.getUser(uid) { profile -> _cook.postValue(profile) }
    }
}
