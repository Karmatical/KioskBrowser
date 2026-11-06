package com.arthursacomori.kioskbrowser.data

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "kiosk_settings"
        private const val KEY_TARGET_URL = "target_url"
        private const val KEY_ADMIN_PASSWORD = "admin_password"

        const val DEFAULT_URL = "https://www.google.com"
        const val DEFAULT_PASSWORD = "KIOSKADMIN26"
    }

    var targetUrl: String
        get() = prefs.getString(KEY_TARGET_URL, DEFAULT_URL) ?: DEFAULT_URL
        set(value) = prefs.edit().putString(KEY_TARGET_URL, value).apply()

    var adminPassword: String
        get() = prefs.getString(KEY_ADMIN_PASSWORD, DEFAULT_PASSWORD) ?: DEFAULT_PASSWORD
        set(value) = prefs.edit().putString(KEY_ADMIN_PASSWORD, value).apply()
}
