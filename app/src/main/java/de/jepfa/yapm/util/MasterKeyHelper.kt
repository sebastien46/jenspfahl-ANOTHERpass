package de.jepfa.yapm.util

import de.jepfa.yapm.model.Encrypted
import de.jepfa.yapm.model.Key
import de.jepfa.yapm.model.Password
import de.jepfa.yapm.model.Session
import de.jepfa.yapm.service.encrypt.SecretService
import de.jepfa.yapm.ui.BaseActivity
import java.util.*
import javax.crypto.SecretKey

object MasterKeyHelper {

    /**
     * Returns the Master passphrase which is calculated of the users Master Pin and his Master password
     */
    fun getMasterPassPhraseSK(masterPin: Password, masterPassword: Password, salt: Key): SecretKey {
        val masterPassPhrase = SecretService.conjunctPasswords(masterPin, masterPassword, salt)

        val masterPassPhraseSK = SecretService.generateSecretKey(masterPassPhrase, salt)
        masterPassPhrase.clear()

        return masterPassPhraseSK
    }

    /**
     * Returns the Master Secret Key which is encrypted twice, first with the Android key
     * and second with the PassPhrase key.
     */
    fun getMasterSK(masterPassPhraseSK: SecretKey, salt: Key, storedEncMasterKey: Encrypted): SecretKey? {
        val masterKey = getMasterKey(masterPassPhraseSK, storedEncMasterKey) ?: return null
        val masterSK = SecretService.generateSecretKey(masterKey, salt)
        masterKey.clear()

        return masterSK
    }

    fun getMasterKey(masterPassPhraseSK: SecretKey, storedEncMasterKey: Encrypted): Key? {
        val androidSK = SecretService.getAndroidSecretKey(SecretService.ALIAS_KEY_MK)

        val encMasterKey = SecretService.decryptEncrypted(androidSK, storedEncMasterKey)
        val masterKey = SecretService.decryptKey(masterPassPhraseSK, encMasterKey)
        if (Arrays.equals(masterKey.data, SecretService.FAILED_BYTE_ARRAY)) {
            return null
        }
        return masterKey
    }

    fun encryptAndStoreMasterKey(masterKey: Key, pin: Password, masterPasswd: Password, salt: Key, activity: BaseActivity) {

        val mkSK = SecretService.getAndroidSecretKey(SecretService.ALIAS_KEY_MK)
        val masterPassphrase = SecretService.conjunctPasswords(pin, masterPasswd, salt)
        val masterSK = SecretService.generateSecretKey(masterPassphrase, salt)
        masterPassphrase.clear()

        val encryptedMasterKey = SecretService.encryptKey(Encrypted.TYPE_ENC_MASTER_KEY, masterSK, masterKey)

        val encEncryptedMasterKey = SecretService.encryptEncrypted(mkSK, encryptedMasterKey)

        PreferenceUtil.putEncrypted(PreferenceUtil.PREF_ENCRYPTED_MASTER_KEY, encEncryptedMasterKey, activity)
    }
}