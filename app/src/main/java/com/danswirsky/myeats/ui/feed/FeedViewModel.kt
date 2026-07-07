package com.danswirsky.myeats.ui.feed

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.danswirsky.myeats.data.model.Recipe
import com.danswirsky.myeats.data.repository.FeedSort
import com.danswirsky.myeats.data.repository.RecipeRepository
import com.danswirsky.myeats.util.SharedPreferencesManager

/**
 * Scalable feed:
 * - Browse mode: a live first page (PAGE_SIZE items) + older pages fetched
 *   on demand as the user scrolls (cursor pagination). The full recipes
 *   node is never downloaded.
 * - Search mode: server-side prefix search on the indexed title field.
 * - Category filter and sort are applied to whatever is loaded.
 */
class FeedViewModel : ViewModel() {

    companion object {
        const val CATEGORY_ALL = "All"
        const val PAGE_SIZE = 30
        private const val SEARCH_DEBOUNCE_MS = 300L
    }

    private val repository = RecipeRepository()
    private val handler = Handler(Looper.getMainLooper())

    private val category = MutableLiveData(CATEGORY_ALL)
    private var query = ""
    private var searchResults: List<Recipe>? = null

    /** Sort preference is device-local, restored from SharedPreferences. */
    private var sort: FeedSort = runCatching {
        FeedSort.valueOf(
            SharedPreferencesManager.getInstance()
                .getString(SharedPreferencesManager.KEY_FEED_SORT, FeedSort.NEWEST.name)
        )
    }.getOrDefault(FeedSort.NEWEST)

    private var livePage: LiveData<List<Recipe>>? = null
    private val olderPages = mutableListOf<Recipe>()
    private var endReached = false

    private val _loadingMore = MutableLiveData(false)
    val loadingMore: LiveData<Boolean> = _loadingMore

    val recipes = MediatorLiveData<List<Recipe>>()

    init {
        recipes.addSource(category) { rebuild() }
        attachLivePage()
    }

    /** Swaps the live first-page listener (called on init and on sort change). */
    private fun attachLivePage() {
        livePage?.let { recipes.removeSource(it) }
        olderPages.clear()
        endReached = false
        val page = repository.observeLatest(sort, PAGE_SIZE)
        livePage = page
        recipes.addSource(page) { rebuild() }
    }

    private fun browseList(): List<Recipe> =
        ((livePage?.value ?: emptyList()) + olderPages)
            .distinctBy { it.id }
            .sortedWith(repository.comparatorFor(sort))

    private fun rebuild() {
        val base = if (query.isNotEmpty()) searchResults ?: emptyList() else browseList()
        val cat = category.value ?: CATEGORY_ALL
        recipes.value = base
            .filter { cat == CATEGORY_ALL || cat in it.categories }
            .sortedWith(repository.comparatorFor(sort))
    }

    /** Infinite scroll: fetch the next page below the last loaded item. */
    fun loadMore() {
        if (query.isNotEmpty() || endReached || _loadingMore.value == true) return
        val last = browseList().lastOrNull() ?: return
        _loadingMore.value = true
        repository.loadPageAfter(sort, last, PAGE_SIZE) { page ->
            _loadingMore.value = false
            if (page != null) {
                if (page.size < PAGE_SIZE) endReached = true
                olderPages += page
                rebuild()
            }
        }
    }

    /**
     * Runs the title-prefix search and an ingredient-keyword search in
     * parallel, then merges the results — so typing "sugar" finds every
     * recipe that uses sugar, not just recipes named "Sugar…".
     */
    private val searchRunnable = Runnable {
        val q = query
        val merged = mutableListOf<Recipe>()
        var remaining = 2

        fun deliver(results: List<Recipe>?) {
            merged += results ?: emptyList()
            if (--remaining == 0 && q == query) { // ignore stale responses
                // Safety net: also match already-loaded recipes locally, so
                // older entries without the keyword index are still found.
                val localMatches = browseList().filter {
                    it.titleLower.contains(q) || it.ingredients.lowercase().contains(q)
                }
                searchResults = (merged + localMatches).distinctBy { it.id }
                rebuild()
            }
        }

        repository.searchByTitle(q, PAGE_SIZE) { deliver(it) }
        repository.searchByIngredient(q, PAGE_SIZE) { deliver(it) }
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

    fun setCategory(name: String) {
        category.value = name
    }

    fun setSort(newSort: FeedSort) {
        if (newSort == sort) return
        sort = newSort
        SharedPreferencesManager.getInstance()
            .putString(SharedPreferencesManager.KEY_FEED_SORT, newSort.name)
        attachLivePage()
        rebuild()
    }

    /** For restoring chip state after rotation. */
    fun currentCategory(): String = category.value ?: CATEGORY_ALL

    fun currentSort(): FeedSort = sort

    override fun onCleared() {
        handler.removeCallbacks(searchRunnable)
        super.onCleared()
    }
}
