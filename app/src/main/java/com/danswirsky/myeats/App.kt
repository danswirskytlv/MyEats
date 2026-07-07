package com.danswirsky.myeats

import android.app.Application
import com.danswirsky.myeats.util.SharedPreferencesManager
import com.danswirsky.myeats.util.SignalManager

/** Initializes app-wide singletons once, before any Activity starts. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        SharedPreferencesManager.init(this)
        SignalManager.init(this)
    }
}
