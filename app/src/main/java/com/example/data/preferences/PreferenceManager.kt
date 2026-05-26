package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("digibook_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_SESSION_TOKEN = "session_token"
        private const val KEY_CURRENT_BOOK_ID = "current_book_id"
        private const val KEY_CF_ACCESS_TOKEN = "cf_access_token"
        private const val KEY_CF_ACCESS_EMAIL = "cf_access_email"
    }

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var sessionToken: String
        get() = prefs.getString(KEY_SESSION_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SESSION_TOKEN, value).apply()

    var currentBookId: String
        get() = prefs.getString(KEY_CURRENT_BOOK_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CURRENT_BOOK_ID, value).apply()

    var cfAccessToken: String
        get() = prefs.getString(KEY_CF_ACCESS_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CF_ACCESS_TOKEN, value).apply()

    var cfAccessEmail: String
        get() = prefs.getString(KEY_CF_ACCESS_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CF_ACCESS_EMAIL, value).apply()

    val isConfigured: Boolean
        get() = serverUrl.isNotEmpty() && sessionToken.isNotEmpty()

    fun clear() {
        prefs.edit().clear().apply()
    }
}
