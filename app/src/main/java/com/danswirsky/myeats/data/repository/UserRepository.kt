package com.danswirsky.myeats.data.repository

import androidx.lifecycle.LiveData
import com.danswirsky.myeats.data.model.User
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage

/** Reads and writes user profiles and favorites under users/{uid}. */
class UserRepository {

    private val usersRef = FirebaseDatabase.getInstance().reference.child("users")
    private val avatarsRef = FirebaseStorage.getInstance().reference.child("profile_images")

    fun saveUser(user: User, onResult: (String?) -> Unit) {
        user.nameLower = user.name.lowercase()
        usersRef.child(user.uid).setValue(user)
            .addOnSuccessListener { onResult(null) }
            .addOnFailureListener { e -> onResult(e.localizedMessage ?: "Failed to save user") }
    }

    fun getUser(uid: String, onResult: (User?) -> Unit) {
        usersRef.child(uid).get()
            .addOnSuccessListener { snapshot -> onResult(snapshot.getValue(User::class.java)) }
            .addOnFailureListener { onResult(null) }
    }

    /**
     * Updates name, bio and (optionally) the profile photo. Uses updateChildren
     * so email and favorites are untouched.
     */
    fun updateProfile(uid: String, name: String, bio: String, photoBytes: ByteArray?, onResult: (String?) -> Unit) {
        fun writeFields(photoUrl: String?) {
            val fields = mutableMapOf<String, Any>(
                "name" to name,
                "nameLower" to name.lowercase(),
                "bio" to bio,
            )
            if (photoUrl != null) fields["photoUrl"] = photoUrl
            usersRef.child(uid).updateChildren(fields)
                .addOnSuccessListener { onResult(null) }
                .addOnFailureListener { e -> onResult(e.localizedMessage ?: "Failed to update profile") }
        }

        if (photoBytes == null) {
            writeFields(null)
            return
        }
        val photoRef = avatarsRef.child("$uid.jpg")
        photoRef.putBytes(photoBytes)
            .continueWithTask { task ->
                if (!task.isSuccessful) task.exception?.let { throw it }
                photoRef.downloadUrl
            }
            .addOnSuccessListener { url -> writeFields(url.toString()) }
            .addOnFailureListener { e -> onResult(e.localizedMessage ?: "Photo upload failed") }
    }

    /**
     * Live first page of cooks, ordered by name. Only [limit] profiles are
     * downloaded — finding anyone beyond the page is done with [searchByName].
     */
    fun observeUsers(limit: Int): LiveData<List<User>> = object : LiveData<List<User>>() {
        private val query = usersRef.orderByChild("nameLower").limitToFirst(limit)
        private val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                value = snapshot.children.mapNotNull { it.getValue(User::class.java) }
                    .sortedBy { it.nameLower }
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

    /** Server-side prefix search on the lowercased name — works at any DB size. */
    fun searchByName(prefix: String, limit: Int, onResult: (List<User>?) -> Unit) {
        val p = prefix.trim().lowercase()
        usersRef.orderByChild("nameLower")
            .startAt(p)
            .endAt(p + "")
            .limitToFirst(limit)
            .get()
            .addOnSuccessListener { snapshot ->
                onResult(snapshot.children.mapNotNull { it.getValue(User::class.java) })
            }
            .addOnFailureListener { onResult(null) }
    }

    /** Live set of recipe ids the user marked as favorite (users/{uid}/favorites). */
    fun favorites(uid: String): LiveData<Set<String>> = object : LiveData<Set<String>>() {
        private val ref = usersRef.child(uid).child("favorites")
        private val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                value = snapshot.children.mapNotNull { it.key }.toSet()
            }

            override fun onCancelled(error: DatabaseError) {
                value = emptySet()
            }
        }

        override fun onActive() {
            ref.addValueEventListener(listener)
        }

        override fun onInactive() {
            ref.removeEventListener(listener)
        }
    }

    fun setFavorite(uid: String, recipeId: String, favorite: Boolean, onResult: (String?) -> Unit) {
        val ref = usersRef.child(uid).child("favorites").child(recipeId)
        val task = if (favorite) ref.setValue(true) else ref.removeValue()
        task.addOnSuccessListener { onResult(null) }
            .addOnFailureListener { e -> onResult(e.localizedMessage ?: "Failed to update favorite") }
    }
}
