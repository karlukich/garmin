package com.carlos.garmin

import android.util.Base64
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GarminException(message: String, val code: Int = 0) : Exception(message)

/**
 * Cliente mínimo de Garmin Connect (API no oficial, la misma que usa la app móvil).
 * Usa el token Bearer "DI" y lo renueva con el token de refresco cuando va a caducar.
 */
class GarminClient(
    initial: Tokens,
    private val onTokensChanged: (Tokens) -> Unit,
) {
    @Volatile
    private var tokens: Tokens = initial
    private val lock = Any()
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** GET a connectapi.garmin.com. Devuelve JSONObject, JSONArray o null si la respuesta está vacía. */
    fun getJson(path: String, params: List<Pair<String, String>> = emptyList()): Any? {
        if (expiresSoon(tokens.diToken)) refresh()
        var res = execute(path, params)
        if (res.first == 401) {
            refresh()
            res = execute(path, params)
        }
        val (code, body) = res
        when {
            code == 429 -> throw GarminException("Garmin pide esperar unos minutos (demasiadas peticiones).", code)
            code == 401 || code == 403 -> throw GarminException("La sesión de Garmin ha caducado: vuelve a importar los tokens.", code)
            code !in 200..299 -> throw GarminException("Garmin respondió con el código $code.", code)
        }
        val text = body.trim()
        if (text.isEmpty()) return null
        return if (text.startsWith("[")) JSONArray(text) else JSONObject(text)
    }

    private fun execute(path: String, params: List<Pair<String, String>>): Pair<Int, String> {
        val builder = "https://connectapi.garmin.com".toHttpUrl().newBuilder()
            .addPathSegments(path.trimStart('/'))
        for ((k, v) in params) builder.addQueryParameter(k, v)
        val req = Request.Builder()
            .url(builder.build())
            .header("Authorization", "Bearer ${tokens.diToken}")
            .header("Accept", "application/json")
            .also { addNativeHeaders(it) }
            .get()
            .build()
        return http.newCall(req).execute().use { r -> Pair(r.code, r.body?.string() ?: "") }
    }

    private fun refresh() {
        synchronized(lock) {
            val t = tokens
            val rt = t.refreshToken
                ?: throw GarminException("No hay token de refresco: vuelve a importar los tokens.", 401)
            val cid = t.clientId ?: jwtClaim(t.diToken, "client_id")
                ?: throw GarminException("Faltan datos de sesión: vuelve a importar los tokens.", 401)
            val basic = "Basic " + Base64.encodeToString("$cid:".toByteArray(), Base64.NO_WRAP)
            val body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", cid)
                .add("refresh_token", rt)
                .build()
            val req = Request.Builder()
                .url("https://diauth.garmin.com/di-oauth2-service/oauth/token")
                .header("Authorization", basic)
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache")
                .also { addNativeHeaders(it) }
                .post(body)
                .build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) {
                    throw GarminException("No se pudo renovar la sesión (código ${r.code}): vuelve a importar los tokens.", r.code)
                }
                val o = JSONObject(r.body?.string() ?: "{}")
                val access = o.strOrNull("access_token")
                    ?: throw GarminException("Garmin no devolvió un token nuevo.", 401)
                val updated = Tokens(
                    diToken = access,
                    refreshToken = o.strOrNull("refresh_token") ?: rt,
                    clientId = jwtClaim(access, "client_id") ?: cid,
                )
                tokens = updated
                onTokensChanged(updated)
            }
        }
    }

    private fun addNativeHeaders(b: Request.Builder) {
        b.header("User-Agent", "GCM-Android-5.23")
        b.header(
            "X-Garmin-User-Agent",
            "com.garmin.android.apps.connectmobile/5.23; ; Google/sdk_gphone64_arm64/google; Android/33; Dalvik/2.1.0",
        )
        b.header("X-Garmin-Paired-App-Version", "10861")
        b.header("X-Garmin-Client-Platform", "Android")
        b.header("X-App-Ver", "10861")
        b.header("X-Lang", "en")
        b.header("X-GCExperience", "GC5")
        b.header("Accept-Language", "en-US,en;q=0.9")
    }

    private fun jwtPayload(token: String): JSONObject? {
        return try {
            val part = token.split(".")[1]
            val bytes = Base64.decode(part, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            JSONObject(String(bytes))
        } catch (e: Exception) {
            null
        }
    }

    private fun jwtClaim(token: String, claim: String): String? = jwtPayload(token)?.strOrNull(claim)

    private fun expiresSoon(token: String): Boolean {
        val exp = jwtPayload(token)?.dblOrNull("exp") ?: return false
        return System.currentTimeMillis() / 1000.0 > exp - 900
    }
}
