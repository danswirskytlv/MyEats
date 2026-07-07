package com.danswirsky.myeats.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.danswirsky.myeats.data.model.User
import com.danswirsky.myeats.data.repository.AuthRepository
import com.danswirsky.myeats.data.repository.RecipeRepository
import com.danswirsky.myeats.data.repository.UserRepository

class EditProfileViewModel : ViewModel() {

    private val userRepository = UserRepository()
    private val authRepository = AuthRepository()
    private val recipeRepository = RecipeRepository()

    private val uid: String = authRepository.currentUser?.uid ?: ""

    /** The picked avatar, already compressed — survives rotation. */
    var pickedImageBytes: ByteArray? = null

    /** True once the bio field was filled from the loaded profile. */
    var formPrefilled = false

    private val _user = MutableLiveData<User?>()
    val user: LiveData<User?> = _user

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _done = MutableLiveData(false)
    val done: LiveData<Boolean> = _done

    init {
        if (uid.isNotEmpty()) {
            userRepository.getUser(uid) { profile -> _user.postValue(profile) }
        }
    }

    fun save(name: String, bio: String) {
        if (uid.isEmpty()) return
        _loading.value = true
        val nameChanged = name != (_user.value?.name ?: name)
        userRepository.updateProfile(uid, name, bio, pickedImageBytes) { error ->
            if (error != null) {
                _loading.value = false
                _error.value = error
                return@updateProfile
            }
            if (nameChanged) {
                // Keep the denormalized ownerName on this user's recipes in sync
                recipeRepository.renameOwner(uid, name) { renameError ->
                    _loading.value = false
                    if (renameError == null) {
                        pickedImageBytes = null
                        _done.value = true
                    } else {
                        _error.value = renameError
                    }
                }
            } else {
                _loading.value = false
                pickedImageBytes = null
                _done.value = true
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
