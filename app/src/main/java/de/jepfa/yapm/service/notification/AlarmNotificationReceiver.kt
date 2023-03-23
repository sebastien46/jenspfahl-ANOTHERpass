package de.jepfa.yapm.service.notification

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import de.jepfa.yapm.R
import de.jepfa.yapm.service.PreferenceService
import de.jepfa.yapm.service.PreferenceService.DATA_EXPIRY_DATES
import de.jepfa.yapm.service.PreferenceService.PREF_EXPIRED_CREDENTIALS_NOTIFICATION_ENABLED
import de.jepfa.yapm.service.notification.NotificationService.SCHEDULED_NOTIFICATION_KEY_SEPARATOR
import de.jepfa.yapm.ui.SecureActivity
import de.jepfa.yapm.ui.credential.ListCredentialsActivity
import de.jepfa.yapm.util.Constants
import de.jepfa.yapm.util.removeTime
import de.jepfa.yapm.util.toSimpleDateFormat
import java.util.*

class AlarmNotificationReceiver : BroadcastReceiver() {


    override fun onReceive(context: Context, intent: Intent) {

        val id = intent.getIntExtra("ID", 0)
        Log.d("NOTIF", "scheduled notification with id=$id alarm received")

        PreferenceService.initStorage(context)

        val enabled = PreferenceService.getAsBool(PREF_EXPIRED_CREDENTIALS_NOTIFICATION_ENABLED, context)
        if (!enabled) {
            Log.d("NOTIF", "scheduled notifications disabled")
            return
        }

        val today = Date().removeTime()
        val expiresAtValues = PreferenceService.getAllStartingWith(DATA_EXPIRY_DATES, context)
        val key = DATA_EXPIRY_DATES + SCHEDULED_NOTIFICATION_KEY_SEPARATOR + id.toString()
        val expiryDateForId =
            expiresAtValues.filterKeys { it == key }
                .map { it.key }
                .mapNotNull { PreferenceService.getAsString(it, context) }
                .mapNotNull { it.toLongOrNull() }
                .map { Date(it) }
                .map { it.removeTime() }
                .firstOrNull { it == today || it.before(today) }

        Log.d("NOTIF", "scheduled notification with id=$id alarm received having expiryDate=$expiryDateForId")

        if (expiryDateForId != null) {
            val contentIntent = createPendingExpiryIntent(context, id,
                action = "${Constants.ACTION_OPEN_VAULT_FOR_FILTERING}${Constants.ACTION_DELIMITER}${Constants.SEARCH_COMMAND_SEARCH_ID}$id${Constants.SEARCH_COMMAND_END}" )
            val actionIntent = createPendingExpiryIntent(context, id,
                action = "${Constants.ACTION_OPEN_VAULT_FOR_FILTERING}${Constants.ACTION_DELIMITER}${Constants.SEARCH_COMMAND_SHOW_EXPIRED} " ) //tailing whitespace to not open autocomplete

            NotificationService.pushNotification(
                context,
                NotificationService.CHANNEL_ID_SCHEDULED,
                context.getString(R.string.credential_expired_notifiction_title),
                context.getString(R.string.credential_expired_notifiction_message, expiryDateForId.toSimpleDateFormat()),
                id,
                contentIntent,
                context.getString(R.string.credential_expired_notifiction_show_all_expired),
                actionIntent
            )
        }
    }

    private fun createPendingExpiryIntent(context: Context, credentialId: Int, action: String): PendingIntent {
        val authIntent = Intent(context, ListCredentialsActivity::class.java)
        authIntent.putExtra(SecureActivity.SecretChecker.fromAutofillOrNotification, true)
        authIntent.action = action
        authIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)

        return PendingIntent.getActivity(
            context,
            credentialId,
            authIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
    }
}