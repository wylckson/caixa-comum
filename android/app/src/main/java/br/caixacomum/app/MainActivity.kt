package br.caixacomum.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Tela principal: abre o Caixa Comum (o mesmo site da versão web) e oferece
 * ao site uma ponte ("CaixaAndroid") para ligar a captura de notificações.
 */
class MainActivity : Activity() {

    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = true
        }
        web.addJavascriptInterface(Bridge(), "CaixaAndroid")
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                val inside = url.toString().startsWith(BuildConfig.WEB_URL) ||
                    (url.host ?: "").endsWith(".supabase.co")
                if (inside) return false
                try { startActivity(Intent(Intent.ACTION_VIEW, url)) } catch (_: Exception) { }
                return true
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    view.loadData(OFFLINE_HTML, "text/html; charset=utf-8", "utf-8")
                }
            }
        }

        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        else web.loadUrl(BuildConfig.WEB_URL)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        web.evaluateJavascript("window.caixaAndroidChanged && window.caixaAndroidChanged()", null)
        Uploader.flushAsync(this)
    }

    @Deprecated("Mantido para Android antigo")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    fun reload() = runOnUiThread { web.loadUrl(BuildConfig.WEB_URL) }

    /** Funções que o site chama pelo JavaScript (window.CaixaAndroid). */
    inner class Bridge {
        @JavascriptInterface
        fun isNotifEnabled(): Boolean {
            val list = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
            return list.contains(packageName)
        }

        @JavascriptInterface
        fun openNotifSettings() {
            runOnUiThread {
                try {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }
            }
        }

        @JavascriptInterface
        fun setDevice(json: String) {
            Store.setDevice(this@MainActivity, json)
            Uploader.flushAsync(this@MainActivity)
        }

        @JavascriptInterface
        fun deviceCaixa(): String = Store.device(this@MainActivity)?.optString("caixa", "") ?: ""

        @JavascriptInterface
        fun clearDevice() = Store.setDevice(this@MainActivity, null)

        @JavascriptInterface
        fun pendingCount(): Int = Store.queue(this@MainActivity).length()

        @JavascriptInterface
        fun flush() = Uploader.flushAsync(this@MainActivity)

        @JavascriptInterface
        fun retry() = reload()
    }

    companion object {
        private const val OFFLINE_HTML = """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>body{font-family:sans-serif;background:#F2F5F4;color:#13201D;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;padding:24px;text-align:center}
button{margin-top:16px;padding:12px 20px;border:0;border-radius:8px;background:#0E6E68;color:#fff;font-size:16px}</style></head>
<body><div><h2>Sem conexão</h2><p>O Caixa Comum precisa de internet para abrir. As notificações dos bancos continuam sendo guardadas e são enviadas quando a internet voltar.</p>
<button onclick="CaixaAndroid.retry()">Tentar de novo</button></div></body></html>"""
    }
}
