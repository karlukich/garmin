package com.carlos.garmin

import org.json.JSONArray
import org.json.JSONObject

fun JSONObject.dblOrNull(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    val v = optDouble(key)
    return if (v.isNaN()) null else v
}

fun JSONObject.intOrNull(key: String): Int? = dblOrNull(key)?.toInt()

fun JSONObject.strOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key)
}

fun JSONObject.objOrNull(key: String): JSONObject? = if (!has(key) || isNull(key)) null else optJSONObject(key)

fun JSONObject.arrOrNull(key: String): JSONArray? = if (!has(key) || isNull(key)) null else optJSONArray(key)

fun JSONArray.objects(): List<JSONObject> {
    val out = ArrayList<JSONObject>(length())
    for (i in 0 until length()) {
        val o = optJSONObject(i)
        if (o != null) out.add(o)
    }
    return out
}
