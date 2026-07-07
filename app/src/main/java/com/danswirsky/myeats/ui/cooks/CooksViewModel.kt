package com.danswirsky.myeats.ui.cooks

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import com.danswirsky.myeats.data.model.User
import com.danswirsky.myeats.data.repository.AuthRepository
import com.danswirsky.myeats.data.repository.UserRepository

/**
 * Cooks screen, built to scale: browsing shows a capped live page of users,
 * and typing runs an indexed server-side prefix search — the full users node
 * is never downloaded. Recipe counts come from a denormalized field.
 */
class CooksViewModel : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 100
        private const val SEARCH_DEBOUNCE_MS = 300L
    }

    private val userRepository = UserRepository()
    private val authRepository = AuthRepository()
    private val handler = Handler(Looper.getMainLooper())

    private val myUid: String = authRepository.currentUser?.uid ?: ""

    private val liveUsers: LiveData<List<User>> = userRepository.observeUsers(PAGE_SIZE)
    private var query = ""
    private var searchResults: List<User>? = null

    val cooks = MediatorLiveData<List<User>>().apply {
        addSource(liveUsers) { rebuild() }
    }

    private fun rebuild() {
        val base = if (query.isNotEmpty()) searchResults ?: emptyList()
        else liveUsers.value ?: emptyList()
        cooks.value = base.filter { it.uid != myUid } // your own page is the Profile tab
    }

    private val searchRunnable = Runnable {
        val q = query
        userRepository.searchByName(q, PAGE_SIZE) { results ->
            if (q == query) { // ignore stale responses
                searchResults = results ?: emptyList()
                rebuild()
            }
        }
    }

    fun setQuery(text: String) {
        query = text.trim().lowercase()
        handler.removeCallbacks(searchRunnable)
        if (query.isEmpty()) {
            searchResults = null
            rebuild()
        } else {
            handler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS)
        }
    }

    override fun onCleared() {
        handler.removeCallbacks(searchRunnable)
        super.onCleared()
    }
}
