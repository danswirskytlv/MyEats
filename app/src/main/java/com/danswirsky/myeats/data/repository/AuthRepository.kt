package com.danswirsky.myeats.data.repository

import com.danswirsky.myeats.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

/**
 * Wraps Firebase Auth. The UI never touches FirebaseAuth directly.
 * Callbacks deliver null on success or a readable error message on failure.
 */
class AuthRepository {

    companion object {
        /** Seed accounts use fake addresses and can't receive verification links. */
        private const val DEMO_DOMAIN = "@myeats.com"
    }

    private val auth = FirebaseAuth.getInstance()
    private val userRepository = UserRepository()

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    /** True when signed in AND the email was verified (demo accounts bypass). */
    val isSignedInAndVerified: Boolean
        get() = currentUser?.let { user ->
            user.isEmailVerified || (user.email ?: "").endsWith(DEMO_DOMAIN)
        } ?: false

    /** Sends the Firebase verification link to the signed-in user's email. */
    fun sendVerificationEmail(onResult: (String?) -> Unit) {
        val user = currentUser ?: return onResult("Not signed in")
        user.sendEmailVerification()
            .addOnSuccessListener { onResult(null) }
            .addOnFailureListener { e -> onResult(e.localizedMessage ?: "Could not send email") }
    }

    /** Sends a password-reset link to [email] (no sign-in required). */
    fun sendPasswordReset(email: String, onResult: (String?) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { onResult(null) }
            .addOnFailureListener { e -> onResult(e.localizedMessage ?: "Could not send email") }
    }

    /** Refreshes the user from the server and reports the verification state. */
    fun reloadAndCheckVerified(onResult: (Boolean) -> Unit) {
        val user = currentUser ?: return onResult(false)
        user.reload()
            .addOnCompleteListener { onResult(isSignedInAndVerified) }
    }

    fun register(name: String, email: String, password: String, onResult: (String?) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: return@addOnSuccessListener onResult("Unknown error")
                // Save the profile in the Realtime Database as well
                userRepository.saveUser(User(uid = uid, name = name, email = email)) { error ->
                    onResult(error)
                }
            }
            .addOnFailureListener { e -> onResult(e.localizedMessage ?: "Registration failed") }
    }

    fun login(email: String, password: String, onResult: (String?) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { onResult(null) }
            .addOnFailureListener { e -> onResult(e.localizedMessage ?: "Login failed") }
    }

    fun logout() = auth.signOut()
}
