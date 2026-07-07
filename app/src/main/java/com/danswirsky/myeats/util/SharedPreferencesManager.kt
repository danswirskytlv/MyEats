package com.danswirsky.myeats.util

import android.content.Context

/**
 * Singleton wrapper around SharedPreferences for small device-local
 * preferences (e.g. the feed sort order). User data lives in Firebase;
 * this is only for UI preferences that belong to the device.
 */
class SharedPreferencesManager private constructor(context: Context) {

    private val sharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_FILE = "myeats_prefs"
        const val KEY_FEED_SORT = "feed_sort"

        @Volatile
        private var instance: SharedPreferencesManager? = null

        fun init(context: Context): SharedPreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: SharedPreferencesManager(context.applicationContext)
                    .also { instance = it }
            }
        }

        fun getInstance(): SharedPreferencesManager {
            return instance ?: throw IllegalStateException(
                "SharedPreferencesManager must be initialized in App.onCreate() before use."
            )
        }
    }

    fun getString(key: String, defaultValue: String): String {
        return sharedPreferences.getString(key, defaultValue) ?: defaultValue
    }

    fun putString(key: String, value: String) {
        sharedPreferences.edit().putString(key, value).apply()
    }
}
