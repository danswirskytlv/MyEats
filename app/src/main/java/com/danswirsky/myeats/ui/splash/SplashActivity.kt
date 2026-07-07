package com.danswirsky.myeats.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.danswirsky.myeats.data.repository.AuthRepository
import com.danswirsky.myeats.databinding.ActivitySplashBinding
import com.danswirsky.myeats.ui.MainActivity
import com.danswirsky.myeats.ui.auth.AuthActivity

/**
 * Branded entry screen: fades the logo in, then routes to the app
 * (signed in) or to the login flow (signed out).
 */
class SplashActivity : AppCompatActivity() {

    companion object {
        private const val FADE_MS = 600L
        private const val HOLD_MS = 500L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.splashLogo.alpha = 0f
        binding.splashTitle.alpha = 0f
        binding.splashTagline.alpha = 0f

        binding.splashLogo.animate().alpha(1f).setDuration(FADE_MS)
        binding.splashTitle.animate().alpha(1f).setDuration(FADE_MS).setStartDelay(200)
        binding.splashTagline.animate().alpha(1f).setDuration(FADE_MS).setStartDelay(400)
            .withEndAction {
                binding.root.postDelayed({ route() }, HOLD_MS)
            }
    }

    private fun route() {
        if (isFinishing) return
        val target = if (AuthRepository().isSignedInAndVerified) {
            MainActivity::class.java
        } else {
            AuthActivity::class.java
        }
        startActivity(Intent(this, target))
        finish()
    }
}
