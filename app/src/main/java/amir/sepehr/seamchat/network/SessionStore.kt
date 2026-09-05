package amir.sepehr.seamchat.network

import android.content.Context

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("seam_session", Context.MODE_PRIVATE)

    fun save(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun token(): String? = prefs.getString(KEY_TOKEN, null)

    fun clear() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    companion object {
        private const val KEY_TOKEN = "session_token"
    }
}
