package de.jepfa.yapm.ui.settings

import android.os.Bundle
import androidx.preference.*
import de.jepfa.yapm.R
import de.jepfa.yapm.model.Session
import de.jepfa.yapm.ui.SecureActivity
import de.jepfa.yapm.usecase.LockVaultUseCase
import de.jepfa.yapm.util.ClipboardUtil
import de.jepfa.yapm.util.PreferenceUtil

private const val TITLE_TAG = "settingsActivityTitle"

class SettingsActivity : SecureActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Session.isDenied()) {
            LockVaultUseCase.execute(this)
            return
        }

        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, HeaderFragment())
                .commit()
        } else {
            title = savedInstanceState.getCharSequence(TITLE_TAG)
        }
        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                setTitle(R.string.title_activity_settings)
            }
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun lock() {
        recreate()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save current activity title so we can set it again after a configuration change
        outState.putCharSequence(TITLE_TAG, title)
    }

    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.popBackStackImmediate()) {
            return true
        }
        navigateUpTo(intent)
        return super.onSupportNavigateUp()
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        // Instantiate the new Fragment
        val args = pref.extras
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            pref.fragment
        ).apply {
            arguments = args
            setTargetFragment(caller, 0)
        }
        // Replace the existing Fragment with the new Fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings, fragment)
            .addToBackStack(null)
            .commit()
        title = pref.title
        return true
    }

    class HeaderFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.header_preferences, rootKey)
        }
    }

    class GeneralSettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.general_preferences, rootKey)
        }
    }

    class LoginSettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.login_preferences, rootKey)

            val qrcPref = findPreference<SwitchPreferenceCompat>(
                PreferenceUtil.PREF_FAST_MASTERPASSWD_LOGIN_WITH_QRC)
            val nfcPref = findPreference<SwitchPreferenceCompat>(
                PreferenceUtil.PREF_FAST_MASTERPASSWD_LOGIN_WITH_NFC)

            qrcPref?.let {
                it.setOnPreferenceChangeListener { preference, newValue ->
                    if (newValue == true) {
                        nfcPref?.isChecked = false
                    }
                    true
                }
            }

            nfcPref?.let {
                it.setOnPreferenceChangeListener { preference, newValue ->
                    if (newValue == true) {
                        qrcPref?.isChecked = false
                    }
                    true
                }
            }
        }
    }

    class SecuritySettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.security_preferences, rootKey)

            findPreference<ListPreference>(PreferenceUtil.PREF_LOCK_TIMEOUT)?.let {
                it.setOnPreferenceChangeListener { preference, newValue ->
                    Session.setLockTimeout(newValue.toString().toInt())
                    true
                }
            }

            findPreference<ListPreference>(PreferenceUtil.PREF_LOGOUT_TIMEOUT)?.let {
                it.setOnPreferenceChangeListener { preference, newValue ->
                    Session.setLogoutTimeout(newValue.toString().toInt())
                    true
                }
            }
        }
    }

    class PasswordGeneratorSettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.password_generator_preferences, rootKey)
        }
    }

    class ClipboardSettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.clipboard_preferences, rootKey)

            findPreference<Preference>(PreferenceUtil.ACTION_TEST_COPY_PASSWORD)?.let {
                it.setOnPreferenceClickListener { preference ->
                    activity?.let { activity -> ClipboardUtil.copyTestPasteConsumer(activity.applicationContext) }
                    true
                }
            }
        }
    }

    class AutofillSettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.autofill_preferences, rootKey)
        }
    }
}