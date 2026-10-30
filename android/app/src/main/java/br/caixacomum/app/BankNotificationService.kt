package br.caixacomum.app

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Recebe as notificações do celular e repassa só as dos bancos
 * que tenham valor em reais (R$) para a caixa de entrada do Caixa Comum.
 */
class BankNotificationService : NotificationListenerService() {

    companion object {
        /** Pacote do app no Android → código do banco usado no Caixa Comum. */
        val BANKS = mapOf(
            "br.com.bb.android" to "bb",
            "com.picpay" to "picpay",
            "com.c6bank.app" to "c6",
            "br.com.intermedium" to "inter",
            "com.nu.production" to "nubank"
        )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Uploader.flushAsync(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val banco = BANKS[sbn.packageName] ?: return
        val n = sbn.notification ?: return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val ex = n.extras ?: return
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val lines = ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString(" ") { it.toString() }.orEmpty()
        val body = when {
            !big.isNullOrBlank() -> big
            text.isNotBlank() -> text
            else -> lines
        }

        val full = listOf(title, body)
            .filter { it.isNotBlank() }
            .joinToString(" - ")
            .replace(' ', ' ')
            .trim()

        if (!full.contains("R$")) return
        if (Store.seenRecently(this, full)) return

        Uploader.enqueue(this, full, banco, sbn.packageName)
    }
}
