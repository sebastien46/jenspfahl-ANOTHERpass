package de.jepfa.yapm.ui.login

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.autofill.AutofillManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.navigation.fragment.findNavController
import de.jepfa.yapm.R
import de.jepfa.yapm.model.secret.Password
import de.jepfa.yapm.model.session.LoginData
import de.jepfa.yapm.model.session.Session
import de.jepfa.yapm.service.PreferenceService
import de.jepfa.yapm.service.autofill.ResponseFiller
import de.jepfa.yapm.service.secret.AndroidKey
import de.jepfa.yapm.service.secret.MasterPasswordService
import de.jepfa.yapm.service.secret.SecretService
import de.jepfa.yapm.ui.BaseFragment
import de.jepfa.yapm.ui.UseCaseBackgroundLauncher
import de.jepfa.yapm.ui.createvault.CreateVaultActivity
import de.jepfa.yapm.usecase.secret.RemoveStoredMasterPasswordUseCase
import de.jepfa.yapm.usecase.session.LoginUseCase
import de.jepfa.yapm.util.putEncrypted
import de.jepfa.yapm.util.toastText


class LoginEnterPinFragment : BaseFragment() {

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_login_enter_pin, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, null)

        val loginActivity = getBaseActivity() as LoginActivity
        loginActivity.showTagDetectedMessage = true

        val pinTextView: EditText = view.findViewById(R.id.edittext_enter_pin)
        val nextButton = view.findViewById<Button>(R.id.button_login_next)
        val noAutofillButton = view.findViewById<Button>(R.id.button_no_autofill)

        // this is to perform next step out of the keyboard
        pinTextView.imeOptions = EditorInfo.IME_ACTION_DONE
        pinTextView.setOnEditorActionListener{ _, _, _ ->
            nextButton.performClick()
            true
        }

        pinTextView.requestFocus()


        val pauseDurationInSec = PreferenceService.getAsString(PreferenceService.PREF_AUTOFILL_DEACTIVATION_DURATION, context)

        if (pauseDurationInSec != "0" && loginActivity.isFromAutofill && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            noAutofillButton.setOnClickListener {
                val replyIntent = Intent().apply {
                    val pauseResponse = ResponseFiller.createAutofillPauseResponse(loginActivity)
                    putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, pauseResponse)
                }
                loginActivity.setResult(Activity.RESULT_OK, replyIntent)
                Log.i("CFS", "disable clicked")
                val entries = resources.getStringArray(R.array.autofill_deactivation_duration_entries)
                val values = resources.getStringArray(R.array.autofill_deactivation_duration_values)
                values.indexOf(pauseDurationInSec).let {
                    entries[it]?.let { entry ->
                        toastText(context, getString(R.string.temp_deact_autofill_on, entry))
                    }
                }
                loginActivity.finish()
            }
        }
        else {
            noAutofillButton.visibility = View.GONE
        }

        nextButton.setOnLongClickListener{
            AlertDialog.Builder(loginActivity)
                .setTitle(getString(R.string.delete_stored_masterpasswd))
                .setMessage(getString(R.string.delete_stored_masterpasswd_confirmation))
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.yes) { dialog, whichButton ->
                    RemoveStoredMasterPasswordUseCase.execute(loginActivity)
                    toastText(loginActivity, R.string.masterpassword_removed)
                }
                .setNegativeButton(android.R.string.no, null)
                .show()
            true
        }

        nextButton.setOnClickListener {

            val keyForTemp = SecretService.getAndroidSecretKey(AndroidKey.ALIAS_KEY_TRANSPORT, view.context)

            val userPin = Password(pinTextView.text)
            if (userPin.isEmpty()) {
                pinTextView.error = getString(R.string.pin_required)
                pinTextView.requestFocus()

                return@setOnClickListener
            }

            pinTextView.text = null
            val scannedNdefTag = loginActivity.ndefTag?.data

            if (!Session.isLoggedOut()) {
                val keyForTemp = SecretService.getAndroidSecretKey(AndroidKey.ALIAS_KEY_TRANSPORT, view.context)
                val encMasterPasswd = Session.getEncMasterPasswd()
                if (encMasterPasswd == null) {
                    toastText(context, R.string.something_went_wrong)
                    return@setOnClickListener
                }
                val masterPasswd = SecretService.decryptPassword(keyForTemp, encMasterPasswd)

                login(pinTextView, userPin, masterPasswd, loginActivity)
            }
            else if (scannedNdefTag != null) {
                loginActivity.readMasterPassword(scannedNdefTag)
                { masterPasswd ->
                    masterPasswd?.let {
                        login(pinTextView, userPin, masterPasswd, loginActivity)
                    }
                }
            }
            else {
                MasterPasswordService.getMasterPasswordFromStore(
                    loginActivity, { masterPasswd ->
                        login(pinTextView, userPin, masterPasswd, loginActivity)
                    }
                    , {
                        val encUserPin = SecretService.encryptPassword(keyForTemp, userPin)
                        val args = Bundle()
                        args.putEncrypted(CreateVaultActivity.ARG_ENC_PIN, encUserPin)

                        findNavController().navigate(R.id.action_Login_PinFragment_to_MasterPasswordFragment, args)
                    }
                )
            }
        }
    }

    private fun login(
        pinTextView: TextView,
        userPin: Password,
        masterPasswd: Password,
        loginActivity: LoginActivity
    ) {

        loginActivity.getProgressBar()?.let {

            UseCaseBackgroundLauncher(LoginUseCase)
                .launch(loginActivity, LoginData(userPin, masterPasswd))
                { output ->
                    if (!output.success) {
                        loginActivity.handleFailedLoginAttempt()
                        pinTextView.error = "${getString(R.string.password_wrong)} ${loginActivity.getLoginAttemptMessage()}"
                        pinTextView.requestFocus()
                    } else {
                        userPin.clear()
                        masterPasswd.clear()
                        pinTextView.text = ""

                        loginActivity.loginSuccessful()
                    }
                }
        }
    }
}