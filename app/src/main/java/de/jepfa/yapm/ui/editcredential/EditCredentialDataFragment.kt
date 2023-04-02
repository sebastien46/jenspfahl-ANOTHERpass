@file:Suppress("KotlinDeprecation")

package de.jepfa.yapm.ui.editcredential

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.widget.addTextChangedListener
import androidx.navigation.fragment.findNavController
import de.jepfa.yapm.R
import de.jepfa.yapm.model.encrypted.EncCredential
import de.jepfa.yapm.model.secret.Password
import de.jepfa.yapm.model.secret.SecretKeyHolder
import de.jepfa.yapm.model.session.Session
import de.jepfa.yapm.service.label.LabelService
import de.jepfa.yapm.service.secret.SecretService
import de.jepfa.yapm.service.secret.SecretService.decryptCommonString
import de.jepfa.yapm.service.secret.SecretService.decryptLong
import de.jepfa.yapm.service.secret.SecretService.encryptCommonString
import de.jepfa.yapm.service.secret.SecretService.encryptLong
import de.jepfa.yapm.ui.DropDownList
import de.jepfa.yapm.ui.SecureFragment
import de.jepfa.yapm.ui.label.LabelEditViewExtender
import de.jepfa.yapm.usecase.vault.LockVaultUseCase
import de.jepfa.yapm.util.*
import java.util.*


class EditCredentialDataFragment : SecureFragment() {

    private enum class ExpiryOptions(val representationId: Int) {
        NO_EXPIRATION(R.string.no_expiration),
        EXPIRES_IN_A_MONTH(R.string.expires_in_1_month),
        EXPIRES_IN_3_MONTHS(R.string.expires_in_3_months),
        EXPIRES_IN_6_MONTHS(R.string.expires_in_6_months),
        EXPIRES_IN_12_MONTHS(R.string.expires_in_12_months),
        EXPIRES_ON_CUSTOM(R.string.expires_on),
    }

    private lateinit var editCredentialActivity: EditCredentialActivity
    private lateinit var labelEditViewExtender: LabelEditViewExtender

    private lateinit var editCredentialNameView: EditText
    private lateinit var editCredentialUserView: EditText
    private lateinit var editCredentialWebsiteView: EditText
    private lateinit var editCredentialExpiredAtImageView: ImageView
    private lateinit var editCredentialExpiredAtSpinner: DropDownList
    private lateinit var editCredentialExpiredAtAdapter: ArrayAdapter<String>
    private var openSelectExpiryDateDialog = false
    private var selectedExpiryDate: Date? = null

    private lateinit var editCredentialAdditionalInfoView: EditText
    private lateinit var expandAdditionalInfoImageView: ImageView

    init {
        enableBack = true
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        if (Session.isDenied()) {
            getSecureActivity()?.let { LockVaultUseCase.execute(it) }
            return null
        }
        return inflater.inflate(R.layout.fragment_edit_credential_data, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, null)

        editCredentialActivity = getBaseActivity() as EditCredentialActivity

        editCredentialNameView = view.findViewById(R.id.edit_credential_name)
        editCredentialUserView = view.findViewById(R.id.edit_credential_user)
        editCredentialWebsiteView = view.findViewById(R.id.edit_credential_website)
        editCredentialExpiredAtImageView = view.findViewById(R.id.expired_at_imageview)
        editCredentialExpiredAtSpinner = view.findViewById(R.id.expired_at_spinner)
        editCredentialAdditionalInfoView = view.findViewById(R.id.edit_credential_additional_info)
        expandAdditionalInfoImageView = view.findViewById(R.id.imageview_expand_additional_info)


        editCredentialExpiredAtAdapter = ArrayAdapter(editCredentialActivity, android.R.layout.simple_spinner_dropdown_item, mutableListOf<String>())
        editCredentialExpiredAtSpinner.adapter = editCredentialExpiredAtAdapter
        updateExpiredAtAdapter(updateSelection = false, null, editCredentialActivity)

        editCredentialExpiredAtImageView.setOnClickListener {
            editCredentialExpiredAtSpinner.setSelection(ExpiryOptions.EXPIRES_ON_CUSTOM.ordinal)
        }
        editCredentialExpiredAtSpinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (position == ExpiryOptions.NO_EXPIRATION.ordinal) {
                    updateExpiredAtAdapter(updateSelection = false, null, editCredentialActivity)
                }
                else if (position == ExpiryOptions.EXPIRES_IN_A_MONTH.ordinal) {
                    openSelectExpiryDateDialog = false
                    updateExpiredAtAdapter(updateSelection = true, Date().addMonths(1), editCredentialActivity)
                }
                else if (position == ExpiryOptions.EXPIRES_IN_3_MONTHS.ordinal) {
                    openSelectExpiryDateDialog = false
                    updateExpiredAtAdapter(updateSelection = true, Date().addMonths(3), editCredentialActivity)
                }
                else if (position == ExpiryOptions.EXPIRES_IN_6_MONTHS.ordinal) {
                    openSelectExpiryDateDialog = false
                    updateExpiredAtAdapter(updateSelection = true, Date().addMonths(6), editCredentialActivity)
                }
                else if (position == ExpiryOptions.EXPIRES_IN_12_MONTHS.ordinal) {
                    openSelectExpiryDateDialog = false
                    updateExpiredAtAdapter(updateSelection = true, Date().addMonths(12), editCredentialActivity)
                }
                else if (position == ExpiryOptions.EXPIRES_ON_CUSTOM.ordinal) {
                    // This flag is needed to prevent open the dialog for existing credentials with a expiryDate != null,
                    // because the setSelection - call from fillUi() would trigger it.
                    if (openSelectExpiryDateDialog) {
                        selectExpiryDate()
                    }
                    else {
                        openSelectExpiryDateDialog = true
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

        }

        editCredentialAdditionalInfoView.addTextChangedListener {
            if (it == null) return@addTextChangedListener
            updateExpandAddInfoVisibility(expandAdditionalInfoImageView, it)
        }
        expandAdditionalInfoImageView.setOnClickListener {
            if (editCredentialAdditionalInfoView.maxLines == R.integer.max_credential_additional_info_length) {
                editCredentialAdditionalInfoView.maxLines = 3
                expandAdditionalInfoImageView.setImageDrawable(editCredentialActivity.getDrawable(R.drawable.ic_baseline_expand_more_24))
            }
            else {
                editCredentialAdditionalInfoView.maxLines = R.integer.max_credential_additional_info_length
                expandAdditionalInfoImageView.setImageDrawable(editCredentialActivity.getDrawable(R.drawable.ic_baseline_expand_less_24))
            }
        }

        val explanationView: TextView = view.findViewById(R.id.edit_credential_explanation)
        explanationView.setOnLongClickListener {
            DebugInfo.toggleDebug()
            toastText(
                getBaseActivity(),
                "Debug mode " + if (DebugInfo.isDebug) "ON" else "OFF"
            )
            true
        }

        labelEditViewExtender = LabelEditViewExtender(editCredentialActivity, view)

        //fill UI
        val current = editCredentialActivity.current

        if (current != null) {
            editCredentialActivity.hideKeyboard(editCredentialNameView)

            masterSecretKey?.let{ key ->
                fillUi(key, current)
            }
        }
        else if (editCredentialActivity.isUpdate()) {
            editCredentialActivity.hideKeyboard(editCredentialNameView)

            editCredentialActivity.load().observe(editCredentialActivity) { orig ->
                editCredentialActivity.original = orig
                masterSecretKey?.let { key ->
                    editCredentialActivity.updateTitle(orig)
                    fillUi(key, orig)
                }
            }
        }
        else {
            editCredentialNameView.requestFocus()
            editCredentialActivity.suggestedCredentialName?.let {
                editCredentialNameView.setText(it.capitalize())
            }
            editCredentialActivity.suggestedWebSite?.let {
                editCredentialWebsiteView.setText(it)
            }
            openSelectExpiryDateDialog = true
        }

        val buttonNext: Button = view.findViewById(R.id.button_next)
        buttonNext.setOnClickListener {

            val now = Date()
            if (selectedExpiryDate?.after(now) == false) {
                toastText(editCredentialActivity, R.string.error_expired_in_the_past)
                editCredentialExpiredAtSpinner.requestFocus()
            }
            else if (TextUtils.isEmpty(editCredentialNameView.text)) {
                editCredentialNameView.error = getString(R.string.error_field_required)
                editCredentialNameView.requestFocus()
            }
            else {

                masterSecretKey?.let{ key ->
                    saveCurrentUiData(key)

                    openSelectExpiryDateDialog = false
                    findNavController().navigate(R.id.action_EditCredential_DataFragment_to_PasswordFragment)

                }
            }
        }
    }

    private fun updateExpiredAtAdapter(updateSelection: Boolean, expiryDate: Date?, context: Context) {
        editCredentialExpiredAtAdapter.clear()
        editCredentialExpiredAtAdapter.addAll(
            context.getString(ExpiryOptions.NO_EXPIRATION.representationId),
            context.getString(ExpiryOptions.EXPIRES_IN_A_MONTH.representationId),
            context.getString(ExpiryOptions.EXPIRES_IN_3_MONTHS.representationId),
            context.getString(ExpiryOptions.EXPIRES_IN_6_MONTHS.representationId),
            context.getString(ExpiryOptions.EXPIRES_IN_12_MONTHS.representationId),
        )
        if (expiryDate == null) {
            editCredentialExpiredAtAdapter.add(context.getString(ExpiryOptions.EXPIRES_ON_CUSTOM.representationId))
            editCredentialExpiredAtImageView.colorFilter = null

            editCredentialExpiredAtAdapter.notifyDataSetChanged()

            if (updateSelection) {
                editCredentialExpiredAtSpinner.setSelection(0)
            }
        }
        else {
            val now = Date()
            if (expiryDate.after(now)) {
                // good
                editCredentialExpiredAtAdapter.add("${getString(R.string.expires)}: ${dateToNiceString(expiryDate, editCredentialActivity)} ")
                editCredentialExpiredAtImageView.colorFilter = null
            }
            else {
                // expired
                editCredentialExpiredAtAdapter.add("${getString(R.string.expired_since)}: ${dateToNiceString(expiryDate, editCredentialActivity, withPreposition = false)} ") // 5
                editCredentialExpiredAtImageView.setColorFilter(editCredentialActivity.getColor(R.color.Red))

            }
            editCredentialExpiredAtAdapter.notifyDataSetChanged()

            if (updateSelection) {
               editCredentialExpiredAtSpinner.setSelection(ExpiryOptions.EXPIRES_ON_CUSTOM.ordinal)
            }
        }
        selectedExpiryDate = expiryDate?.removeTime()
    }

    private fun selectExpiryDate() {

        val c = Calendar.getInstance()
        selectedExpiryDate?.let { c.time = it }
        val mYear = c.get(Calendar.YEAR)
        val mMonth = c.get(Calendar.MONTH)
        val mDay = c.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            editCredentialActivity,
            { _, year, monthOfYear, dayOfMonth ->
                val c = Calendar.getInstance()
                c.set(year, monthOfYear, dayOfMonth)
                updateExpiredAtAdapter(updateSelection = false, c.time, editCredentialActivity)

            }, mYear, mMonth, mDay
        )
        datePickerDialog.show()
    }

    private fun updateExpandAddInfoVisibility(
        expandAdditionalInfoImageView: ImageView,
        charSequence: CharSequence
    ) {
        expandAdditionalInfoImageView.visibility =
            if (charSequence.lines().count() > 3) View.VISIBLE else View.INVISIBLE
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (isVisible) {
            masterSecretKey?.let { key ->
                saveCurrentUiData(key)
            }
        }
    }

    private fun fillUi(
        key: SecretKeyHolder,
        current: EncCredential
    ) {
        val name = decryptCommonString(key, current.name)
        val user = decryptCommonString(key, current.user)
        val website = decryptCommonString(key, current.website)
        val expiresAtAsLong = decryptLong(key, current.expiresAt)
        val additionalInfo = decryptCommonString(
            key,
            current.additionalInfo
        )


        editCredentialNameView.setText(name)
        editCredentialUserView.setText(user)
        editCredentialWebsiteView.setText(website)

        val expiresAt = if (expiresAtAsLong != null && expiresAtAsLong > 0) Date(expiresAtAsLong) else null
        updateExpiredAtAdapter(updateSelection = true, expiresAt, editCredentialActivity)
        if (expiresAt == null) {
            // allow open selection dialog since there is currently no value so no progrmmatic selection will be triggered
            openSelectExpiryDateDialog = true
        }

        editCredentialAdditionalInfoView.setText(additionalInfo)
        updateExpandAddInfoVisibility(expandAdditionalInfoImageView, additionalInfo)

        val allLabelsForCredential = LabelService.defaultHolder.decryptLabelsForCredential(key, current)

        labelEditViewExtender.addPersistedLabels(allLabelsForCredential)


    }

    private fun saveCurrentUiData(
        key: SecretKeyHolder
    ) {
        val name = editCredentialNameView.text.toString().trim()
        val additionalInfo = editCredentialAdditionalInfoView.text.toString()
        val user = editCredentialUserView.text.toString().trim()
        val website = editCredentialWebsiteView.text.toString().trim()
        val expiresAt = selectedExpiryDate?.time

        val encName = encryptCommonString(key, name)
        val encAdditionalInfo = encryptCommonString(key, additionalInfo)
        val encUser = encryptCommonString(key, user)
        val encPassword = editCredentialActivity.current?.password
            ?: editCredentialActivity.original?.password
            ?: SecretService.encryptPassword(key, Password.empty()
        )
        val encWebsite = encryptCommonString(key, website)
        val encExpiresAt = encryptLong(key, expiresAt ?: 0L)
        val encLabels = LabelService.defaultHolder.encryptLabelIds(
            key,
            labelEditViewExtender.getCommitedLabelNames()
        )

        // we create the new credential out of a former current if present or else out of the original if present
        val credentialToSave = EncCredential(
            editCredentialActivity.currentId,
            editCredentialActivity.original?.uid,
            encName,
            encAdditionalInfo,
            encUser,
            encPassword,
            editCredentialActivity.original?.lastPassword,
            encWebsite,
            encLabels,
            encExpiresAt,
            editCredentialActivity.current?.isObfuscated
                ?: editCredentialActivity.original?.isObfuscated
                ?: false,
            editCredentialActivity.original?.isLastPasswordObfuscated
                ?: false,
            null
        )
        editCredentialActivity.current = credentialToSave
    }

}