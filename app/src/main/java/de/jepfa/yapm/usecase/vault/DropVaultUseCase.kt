package de.jepfa.yapm.usecase.vault

import androidx.appcompat.app.AlertDialog
import de.jepfa.yapm.R
import de.jepfa.yapm.model.session.Session
import de.jepfa.yapm.service.PreferenceService
import de.jepfa.yapm.service.label.LabelService
import de.jepfa.yapm.service.label.LabelsHolder
import de.jepfa.yapm.service.secret.MasterPasswordService
import de.jepfa.yapm.ui.BaseActivity
import de.jepfa.yapm.ui.SecureActivity
import de.jepfa.yapm.usecase.BasicUseCase
import de.jepfa.yapm.util.observeOnce
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object DropVaultUseCase: BasicUseCase<SecureActivity>() {

    override fun execute(activity: SecureActivity): Boolean {
        Session.logout()
        activity.closeOverlayDialogs()
        dropVaultData(activity)
        activity.finishAffinity()
        SecureActivity.SecretChecker.getOrAskForSecret(activity) // restart app
        return true
    }

    fun doubleCheckDropVault(activity: BaseActivity, doDrop: () -> Unit) {
        activity.credentialViewModel.allCredentials.observeOnce(activity) { credentials ->
            if (credentials.isEmpty()) {
                doDrop()
            }
            else {
                AlertDialog.Builder(activity)
                    .setTitle(activity.getString(R.string.title_reset_all))
                    .setMessage(activity.getString(R.string.message_reset_all_double_check, credentials.size))
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(R.string.reset_vault_and_delete) { dialog, whichButton ->
                        doDrop()
                    }
                    .setNegativeButton(android.R.string.no, null)
                    .show()
            }
        }
    }

    fun dropVaultData(activity: BaseActivity) {

        MasterPasswordService.deleteStoredMasterPassword(activity)
        PreferenceService.deleteAllData(activity)
        PreferenceService.deleteAllTempData(activity)
        PreferenceService.delete(PreferenceService.STATE_LOGIN_ATTEMPTS, activity)
        PreferenceService.delete(PreferenceService.STATE_LOGIN_DENIED_AT, activity)
        PreferenceService.delete(PreferenceService.STATE_LOGIN_SUCCEEDED_AT, activity)
        PreferenceService.delete(PreferenceService.STATE_PREVIOUS_LOGIN_ATTEMPTS, activity)
        PreferenceService.delete(PreferenceService.STATE_PREVIOUS_LOGIN_SUCCEEDED_AT, activity)

        LabelService.defaultHolder.clearAll()
        CoroutineScope(Dispatchers.IO).launch {
            activity.getApp().database?.clearAllTables()
        }
    }
}