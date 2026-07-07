package com.danswirsky.myeats.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.danswirsky.myeats.R
import com.danswirsky.myeats.data.repository.AuthRepository

/** Shared by the Login, Register and VerifyEmail fragments. Survives rotation. */
class AuthViewModel : ViewModel() {

    /** What the UI should do after an auth attempt. */
    enum class AuthEvent { VERIFIED, NEEDS_VERIFICATION }

    private val repository = AuthRepository()

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    /**
     * Error to show, as a string resource. Login failures are deliberately
     * generic — the app never reveals whether the email or the password
     * was wrong (prevents account enumeration).
     */
    private val _errorRes = MutableLiveData<Int?>()
    val errorRes: LiveData<Int?> = _errorRes

    /** Info message (e.g. "verification email sent"). */
    private val _infoRes = MutableLiveData<Int?>()
    val infoRes: LiveData<Int?> = _infoRes

    private val _event = MutableLiveData<AuthEvent?>()
    val event: LiveData<AuthEvent?> = _event

    val userEmail: String?
        get() = repository.currentUser?.email

    fun login(email: String, password: String) {
        _loading.value = true
        repository.login(email, password) { error ->
            _loading.value = false
            if (error != null) {
                _errorRes.value = R.string.error_invalid_credentials
            } else {
                _event.value = if (repository.isSignedInAndVerified) {
                    AuthEvent.VERIFIED
                } else {
                    AuthEvent.NEEDS_VERIFICATION
                }
            }
        }
    }

    fun register(name: String, email: String, password: String) {
        _loading.value = true
        repository.register(name, email, password) { error ->
            _loading.value = false
            if (error != null) {
                _errorRes.value = R.string.error_registration_failed
            } else {
                // New accounts must prove they own the email address
                repository.sendVerificationEmail { /* best-effort; resend available */ }
                _event.value = if (repository.isSignedInAndVerified) {
                    AuthEvent.VERIFIED // demo-domain accounts skip verification
                } else {
                    AuthEvent.NEEDS_VERIFICATION
                }
            }
        }
    }

    /**
     * Password reset. Security: the confirmation message is identical whether
     * or not the email exists, so this can't be used to probe for accounts.
     */
    fun sendPasswordReset(email: String) {
        _loading.value = true
        repository.sendPasswordReset(email) { _ ->
            _loading.value = false
            _infoRes.value = R.string.reset_email_sent
        }
    }

    fun resendVerification() {
        _loading.value = true
        repository.sendVerificationEmail { error ->
            _loading.value = false
            if (error != null) {
                _errorRes.value = R.string.error_send_verification
            } else {
                _infoRes.value = R.string.verification_sent
            }
        }
    }

    /** Called from "I verified" — re-checks with the server. */
    fun checkVerified() {
        _loading.value = true
        repository.reloadAndCheckVerified { verified ->
            _loading.value = false
            if (verified) {
                _event.value = AuthEvent.VERIFIED
            } else {
                _errorRes.value = R.string.error_still_not_verified
            }
        }
    }

    fun logout() = repository.logout()

    fun clearError() {
        _errorRes.value = null
    }

    fun clearInfo() {
        _infoRes.value = null
    }

    fun clearEvent() {
        _event.value = null
    }
}
