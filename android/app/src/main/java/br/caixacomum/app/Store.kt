package br.caixacomum.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Dados guardados no celular: conexão com o caixa, fila sem internet e notificações já vistas. */
object Store {
    private const val PREFS = "caixa"
    private const val TWO_HOURS = 2 * 60 * 60 * 1000L

    private fun p(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun device(c: Context): JSONObject? {
        val raw = p(c).getString("device", null) ?: return null
        return try {
            val o = JSONObject(raw)
            if (o.optString("url").isBlank() || o.optString("token").isBlank()) null else o
        } catch (_: Exception) { null }
    }

    fun setDevice(c: Context, json: String?) {
        p(c).edit().apply { if (json == null) remove("device") else putString("device", json) }.apply()
    }

    @Synchronized
    fun queue(c: Context): JSONArray = try {
        JSONArray(p(c).getString("queue", "[]"))
    } catch (_: Exception) { JSONArray() }

    @Synchronized
    fun saveQueue(c: Context, a: JSONArray) {
        p(c).edit().putString("queue", a.toString()).apply()
    }

    /** Evita mandar a mesma notificação duas vezes (o Android às vezes repete). */
    @Synchronized
    fun seenRecently(c: Context, text: String): Boolean {
        val now = System.currentTimeMillis()
        val key = text.hashCode().toString()
        val old = try { JSONArray(p(c).getString("seen", "[]")) } catch (_: Exception) { JSONArray() }
        val keep = JSONArray()
        var found = false
        for (i in 0 until old.length()) {
            val o = old.optJSONObject(i) ?: continue
            if (now - o.optLong("t") > TWO_HOURS) continue
            if (o.optString("h") == key) found = true
            keep.put(o)
        }
        if (!found) keep.put(JSONObject().put("h", key).put("t", now))
        while (keep.length() > 150) keep.remove(0)
        p(c).edit().putString("seen", keep.toString()).apply()
        return found
    }
}
