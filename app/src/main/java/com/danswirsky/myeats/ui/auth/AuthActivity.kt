package com.danswirsky.myeats.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.danswirsky.myeats.R
import com.danswirsky.myeats.data.repository.AuthRepository
import com.danswirsky.myeats.ui.MainActivity

/** Entry point. Hosts the login/register navigation graph. */
class AuthActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Already signed in AND verified from a previous session — skip to the app
        if (AuthRepository().isSignedInAndVerified) {
            goToMain()
            return
        }
        setContentView(R.layout.activity_auth)
    }

    fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
