package de.jepfa.yapm.util

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import de.jepfa.yapm.R
import de.jepfa.yapm.model.encrypted.Encrypted
import de.jepfa.yapm.model.secret.Key
import de.jepfa.yapm.model.secret.Password
import de.jepfa.yapm.provider.PasteContentProvider
import de.jepfa.yapm.service.PreferenceService
import de.jepfa.yapm.service.secret.SecretService
import de.jepfa.yapm.ui.SecureActivity
import de.jepfa.yapm.service.PreferenceService.PREF_WARN_BEFORE_COPY_TO_CB

object ClipboardUtil {

    fun copyEncPasswordWithCheck(encPassword: Encrypted, obfuscationKey : Key?, activity: SecureActivity) {
        val warn = PreferenceService.getAsBool(PREF_WARN_BEFORE_COPY_TO_CB, activity)
        if (warn) {
            AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.title_copy_password))
                .setMessage(activity.getString(R.string.message_copy_password))
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.copy) { dialog, whichButton ->
                    copyEncPassword(encPassword, obfuscationKey, activity)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        else {
            copyEncPassword(encPassword, obfuscationKey, activity)
        }
    }

    private fun copyEncPassword(encPassword: Encrypted, obfuscationKey : Key?, activity: SecureActivity) {
        activity.masterSecretKey?.let{ key ->
            val passwd = SecretService.decryptPassword(key, encPassword)
            obfuscationKey?.let {
                passwd.deobfuscate(it)
            }
            copyPassword(passwd, activity)
            passwd.clear()
        }
    }

    fun copy(label: String, text: String, context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

    private fun copyPassword(password: Password, context: Context) {
        copy("Password", password.toString(), context)
        Toast.makeText(context, R.string.toast_copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    fun copyTestPasteConsumer(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newUri(
            context.contentResolver,
            "Test copy password",
            PasteContentProvider.contentUri
        )
        PasteContentProvider.enablePushNotification = true
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, R.string.toast_test_copypaste_password, Toast.LENGTH_LONG).show()

    }

    fun clearClips(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("", "")
        clipboard.setPrimaryClip(clip)
    }
}