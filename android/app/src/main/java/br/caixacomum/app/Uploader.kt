package br.caixacomum.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manda as notificações para o Supabase. Sem internet, guarda numa fila
 * e tenta de novo na próxima notificação ou quando o app é aberto.
 */
object Uploader {
    private val running = AtomicBoolean(false)
    private const val MAX_QUEUE = 300

    fun enqueue(c: Context, texto: String, banco: String, app: String) {
        val ctx = c.applicationContext
        synchronized(Store) {
            val q = Store.queue(ctx)
            q.put(JSONObject().put("texto", texto).put("banco", banco).put("app", app))
            while (q.length() > MAX_QUEUE) q.remove(0)
            Store.saveQueue(ctx, q)
        }
        flushAsync(ctx)
    }

    fun flushAsync(c: Context) {
        val ctx = c.applicationContext
        if (!running.compareAndSet(false, true)) return
        Thread {
            try { flush(ctx) } finally { running.set(false) }
        }.start()
    }

    private fun flush(ctx: Context) {
        val dev = Store.device(ctx) ?: return
        while (true) {
            val item = synchronized(Store) { Store.queue(ctx).optJSONObject(0) } ?: return
            when (send(dev, item)) {
                Result.OK, Result.DROP -> synchronized(Store) {
                    val q = Store.queue(ctx)
                    val rest = JSONArray()
                    for (i in 1 until q.length()) rest.put(q.get(i))
                    Store.saveQueue(ctx, rest)
                }
                Result.INVALID_DEVICE -> { Store.setDevice(ctx, null); return }
                Result.RETRY -> return
            }
        }
    }

    private enum class Result { OK, DROP, INVALID_DEVICE, RETRY }

    private fun send(dev: JSONObject, item: JSONObject): Result {
        var conn: HttpURLConnection? = null
        return try {
            val base = dev.getString("url").trimEnd('/')
            val key = dev.getString("key")
            conn = (URL("$base/rest/v1/rpc/registrar_notificacao").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("apikey", key)
                setRequestProperty("Authorization", "Bearer $key")
            }
            val body = JSONObject()
                .put("p_token", dev.getString("token"))
                .put("p_texto", item.optString("texto"))
                .put("p_banco", item.optString("banco"))
                .put("p_app", item.optString("app"))
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            when {
                code in 200..299 -> {
                    val resp = conn.inputStream.bufferedReader().use { it.readText() }.trim()
                    if (resp == "false") Result.INVALID_DEVICE else Result.OK
                }
                code in 400..499 -> Result.DROP
                else -> Result.RETRY
            }
        } catch (_: Exception) {
            Result.RETRY
        } finally {
            conn?.disconnect()
        }
    }
}
