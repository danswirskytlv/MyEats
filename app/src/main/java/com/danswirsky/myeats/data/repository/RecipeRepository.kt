package com.danswirsky.myeats.data.repository

import androidx.lifecycle.LiveData
import com.danswirsky.myeats.data.model.Recipe
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage

/** Feed sort orders — each maps to an indexed field so the SERVER sorts pages. */
enum class FeedSort { NEWEST, TOP_RATED }

/**
 * Single source of truth for recipes.
 *
 * Built to scale: no call here ever downloads the whole recipes node.
 * The feed is paginated with cursor queries, search runs server-side on an
 * indexed field, and per-user lists use an indexed ownerUid query.
 */
class RecipeRepository {

    companion object {
        private const val FIELD_CREATED = "createdAt"
        private const val FIELD_RATING_AVG = "ratingAvg"
        private const val FIELD_TITLE_LOWER = "titleLower"
        private const val FIELD_OWNER = "ownerUid"

        /** Measurement/descriptor words that aren't real ingredients. */
        private val STOP_WORDS = setOf(
            "and", "the", "for", "with", "cup", "cups", "tbsp", "tsp", "pinch",
            "large", "small", "medium", "fresh", "chopped", "sliced", "diced",
            "ground", "handful", "juice", "zest", "optional", "plus", "serve",
            "taste", "into", "some", "ripe", "whole", "half",
        )

        /**
         * Extracts searchable ingredient words ("2 tbsp brown sugar" ->
         * brown, sugar). Stored as a map so the server can answer
         * "which recipes contain sugar?" without scanning text.
         */
        fun extractIngredientKeywords(ingredients: String): Map<String, Boolean> =
            Regex("[a-z]+")
                .findAll(ingredients.lowercase())
                .map { it.value }
                .filter { it.length >= 3 && it !in STOP_WORDS }
                .associateWith { true }
    }

    private val rootRef = FirebaseDatabase.getInstance().reference
    private val recipesRef = rootRef.child("recipes")
    private val usersRef = rootRef.child("users")
    private val imagesRef = FirebaseStorage.getInstance().reference.child("recipe_images")

    fun comparatorFor(sort: FeedSort): Comparator<Recipe> = when (sort) {
        FeedSort.NEWEST -> compareByDescending { it.createdAt }
        FeedSort.TOP_RATED -> compareByDescending<Recipe> { it.ratingAvg }
            .thenByDescending { it.ratingCount }
    }

    private fun sortQuery(sort: FeedSort): Query = when (sort) {
        FeedSort.NEWEST -> recipesRef.orderByChild(FIELD_CREATED)
        FeedSort.TOP_RATED -> recipesRef.orderByChild(FIELD_RATING_AVG)
    }

    private fun toSortedList(snapshot: DataSnapshot, sort: FeedSort): List<Recipe> =
        snapshot.children.mapNotNull { it.getValue(Recipe::class.java) }
            .sortedWith(comparatorFor(sort))

    /**
     * Live first page of the feed. Only [limit] items are downloaded, and the
     * listener re-delivers only this window when something changes.
     */
    fun observeLatest(sort: FeedSort, limit: Int): LiveData<List<Recipe>> =
        object : LiveData<List<Recipe>>() {
            private val query = sortQuery(sort).limitToLast(limit)
            private val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    value = toSortedList(snapshot, sort)
                }

                override fun onCancelled(error: DatabaseError) {
                    value = emptyList()
                }
            }

            override fun onActive() {
                query.addValueEventListener(listener)
            }

            override fun onInactive() {
                query.removeEventListener(listener)
            }
        }

    /**
     * One-shot cursor pagination: fetches the [pageSize] items that come after
     * [last] in the given sort order (infinite scroll).
     */
    fun loadPageAfter(sort: FeedSort, last: Recipe, pageSize: Int, onResult: (List<Recipe>?) -> Unit) {
        val query = when (sort) {
            FeedSort.NEWEST ->
                sortQuery(sort).endBefore(last.createdAt.toDouble(), last.id)
            FeedSort.TOP_RATED ->
                sortQuery(sort).endBefore(last.ratingAvg, last.id)
        }.limitToLast(pageSize)

        query.get()
            .addOnSuccessListener { snapshot -> onResult(toSortedList(snapshot, sort)) }
            .addOnFailureListener { onResult(null) }
    }

    /** Server-side prefix search on the lowercased title — works at any DB size. */
    fun searchByTitle(prefix: String, limit: Int, onResult: (List<Recipe>?) -> Unit) {
        val p = prefix.trim().lowercase()
        recipesRef.orderByChild(FIELD_TITLE_LOWER)
            .startAt(p)
            .endAt(p + "")
            .limitToFirst(limit)
            .get()
            .addOnSuccessListener { snapshot ->
                onResult(snapshot.children.mapNotNull { it.getValue(Recipe::class.java) })
            }
            .addOnFailureListener { onResult(null) }
    }

    /** Server-side ingredient search: recipes whose keyword map contains [word]. */
    fun searchByIngredient(word: String, limit: Int, onResult: (List<Recipe>?) -> Unit) {
        val key = word.trim().lowercase().replace(Regex("[^a-z]"), "")
        if (key.length < 3) {
            onResult(emptyList())
            return
        }
        recipesRef.orderByChild("ingredientKeywords/$key").equalTo(true)
            .limitToFirst(limit)
            .get()
            .addOnSuccessListener { snapshot ->
                onResult(snapshot.children.mapNotNull { it.getValue(Recipe::class.java) })
            }
            .addOnFailureListener { onResult(null) }
    }

    /** Live list of one user's recipes — an indexed equalTo query, not a full scan. */
    fun observeByOwner(uid: String): LiveData<List<Recipe>> =
        object : LiveData<List<Recipe>>() {
            private val query = recipesRef.orderByChild(FIELD_OWNER).equalTo(uid)
            private val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    value = toSortedList(snapshot, FeedSort.NEWEST)
                }

                override fun onCancelled(error: DatabaseError) {
                    value = emptyList()
                }
            }

            override fun onActive() {
                query.addValueEventListener(listener)
            }

            override fun onInactive() {
                query.removeEventListener(listener)
            }
        }

    /**
     * After a profile rename: updates the denormalized ownerName on all of the
     * user's recipes (one indexed query + one multi-path update).
     */
    fun renameOwner(uid: String, newName: String, onResult: (String?) -> Unit) {
        recipesRef.orderByChild(FIELD_OWNER).equalTo(uid).get()
            .addOnSuccessListener { snapshot ->
                val updates = mutableMapOf<String, Any>()
                snapshot.children.forEach { child ->
                    child.key?.let { key -> updates["$key/ownerName"] = newName }
                }
                if (updates.isEmpty()) {
                    onResult(null)
                } else {
                    recipesRef.updateChildren(updates)
                        .addOnSuccessListener { onResult(null) }
                        .addOnFailureListener { e -> onResult(e.localizedMessage) }
                }
            }
            .addOnFailureListener { e -> onResult(e.localizedMessage) }
    }

    /** Fetches specific recipes by id (Favorites) — downloads only what's needed. */
    fun fetchByIds(ids: Collection<String>, onResult: (List<Recipe>) -> Unit) {
        if (ids.isEmpty()) {
            onResult(emptyList())
            return
        }
        val results = mutableListOf<Recipe>()
        var remaining = ids.size
        ids.forEach { id ->
            recipesRef.child(id).get().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    task.result?.getValue(Recipe::class.java)?.let { results.add(it) }
                }
                if (--remaining == 0) {
                    onResult(results.sortedByDescending { it.createdAt })
                }
            }
        }
    }

    fun getRecipe(id: String, onResult: (Recipe?) -> Unit) {
        recipesRef.child(id).get()
            .addOnSuccessListener { snapshot -> onResult(snapshot.getValue(Recipe::class.java)) }
            .addOnFailureListener { onResult(null) }
    }

    /** Live view of a single recipe — the details screen updates when anyone rates it. */
    fun observeRecipe(id: String): LiveData<Recipe?> = object : LiveData<Recipe?>() {
        private val ref = recipesRef.child(id)
        private val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                value = snapshot.getValue(Recipe::class.java)
            }

            override fun onCancelled(error: DatabaseError) {
                value = null
            }
        }

        override fun onActive() {
            ref.addValueEventListener(listener)
        }

        override fun onInactive() {
            ref.removeEventListener(listener)
        }
    }

    /**
     * Stores/updates the user's star rating inside a transaction, so two users
     * rating at the same moment can never corrupt the aggregates.
     */
    fun rate(recipeId: String, uid: String, stars: Long, onResult: (String?) -> Unit) {
        recipesRef.child(recipeId).runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val recipe = currentData.getValue(Recipe::class.java)
                    ?: return Transaction.success(currentData)
                val ratings = recipe.ratings.toMutableMap()
                ratings[uid] = stars
                recipe.ratings = ratings
                recipe.ratingSum = ratings.values.sum()
                recipe.ratingCount = ratings.size.toLong()
                recipe.ratingAvg =
                    if (recipe.ratingCount > 0) recipe.ratingSum.toDouble() / recipe.ratingCount else 0.0
                currentData.value = recipe
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                onResult(error?.message)
            }
        })
    }

    /**
     * Uploads the compressed photo to Storage (if one was picked), then writes
     * the recipe and bumps the owner's denormalized recipe counter.
     */
    fun uploadRecipe(recipe: Recipe, imageBytes: ByteArray?, onResult: (String?) -> Unit) {
        val id = recipesRef.push().key ?: return onResult("Could not create recipe id")
        recipe.id = id
        recipe.createdAt = System.currentTimeMillis()
        recipe.titleLower = recipe.title.lowercase()
        recipe.ingredientKeywords = extractIngredientKeywords(recipe.ingredients)

        val save: () -> Unit = {
            saveRecipe(recipe) { error ->
                if (error == null) {
                    usersRef.child(recipe.ownerUid).child("recipeCount")
                        .setValue(ServerValue.increment(1))
                }
                onResult(error)
            }
        }

        if (imageBytes == null) {
            save()
            return
        }
        val imageRef = imagesRef.child("$id.jpg")
        imageRef.putBytes(imageBytes)
            .continueWithTask { task ->
                if (!task.isSuccessful) task.exception?.let { throw it }
                imageRef.downloadUrl
            }
            .addOnSuccessListener { url ->
                recipe.imageUrl = url.toString()
                save()
            }
            .addOnFailureListener { e -> onResult(e.localizedMessage ?: "Image upload failed") }
    }

    /**
     * Saves changes to an existing recipe. If a new photo was picked it
     * overwrites the old one in Storage (same path) first.
     */
    fun updateRecipe(recipe: Recipe, newImageBytes: ByteArray?, onResult: (String?) -> Unit) {
        recipe.titleLower = recipe.title.lowercase()
        recipe.ingredientKeywords = extractIngredientKeywords(recipe.ingredients)
        if (newImageBytes == null) {
            saveRecipe(recipe, onResult)
            return
        }
        val imageRef = imagesRef.child("${recipe.id}.jpg")
        imageRef.putBytes(newImageBytes)
            .continueWithTask { task ->
                if (!task.isSuccessful) task.exception?.let { throw it }
                imageRef.downloadUrl
            }
            .addOnSuccessListener { url ->
                recipe.imageUrl = url.toString()
                saveRecipe(recipe, onResult)
            }
            .addOnFailureListener { e -> onResult(e.localizedMessage ?: "Image upload failed") }
    }

    /** Removes the recipe, its comments, its photo and one from the owner's counter. */
    fun deleteRecipe(recipe: Recipe, onResult: (String?) -> Unit) {
        recipesRef.child(recipe.id).removeValue()
            .addOnSuccessListener {
                usersRef.child(recipe.ownerUid).child("recipeCount")
                    .setValue(ServerValue.increment(-1))
                rootRef.child("comments").child(recipe.id).removeValue() // best-effort
                if (recipe.imageUrl.isNotEmpty()) {
                    imagesRef.child("${recipe.id}.jpg").delete() // ignore result
                }
                onResult(null)
            }
            .addOnFailureListener { e -> onResult(e.localizedMessage ?: "Failed to delete recipe") }
    }

    private fun saveRecipe(recipe: Recipe, onResult: (String?) -> Unit) {
        recipesRef.child(recipe.id).setValue(recipe)
            .addOnSuccessListener { onResult(null) }
            .addOnFailureListener { e -> onResult(e.localizedMessage ?: "Failed to save recipe") }
    }
}
