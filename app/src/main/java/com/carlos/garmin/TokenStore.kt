package com.carlos.garmin

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

/** Tokens de sesión de Garmin (los mismos que guarda login.py en garmin_tokens.json). */
data class Tokens(
    val diToken: String,
    val refreshToken: String?,
    val clientId: String?,
)

class TokenStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "garmin_tokens",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun load(): Tokens? = prefs.getString("json", null)?.let { parse(it) }

    fun save(tokens: Tokens) {
        prefs.edit().putString("json", toJson(tokens)).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        /** Acepta el contenido de garmin_tokens.json. Devuelve null si no tiene el formato esperado. */
        fun parse(text: String): Tokens? {
            return try {
                val o = JSONObject(text.trim())
                val di = o.strOrNull("di_token") ?: return null
                if (di.isBlank()) return null
                Tokens(di, o.strOrNull("di_refresh_token"), o.strOrNull("di_client_id"))
            } catch (e: Exception) {
                null
            }
        }

        fun toJson(t: Tokens): String {
            val o = JSONObject()
            o.put("di_token", t.diToken)
            o.put("di_refresh_token", t.refreshToken ?: JSONObject.NULL)
            o.put("di_client_id", t.clientId ?: JSONObject.NULL)
            return o.toString()
        }
    }
}
