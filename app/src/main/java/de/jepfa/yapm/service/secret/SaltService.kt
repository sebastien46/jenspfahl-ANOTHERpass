package de.jepfa.yapm.service.secret

import android.content.Context
import android.util.Base64
import de.jepfa.yapm.model.encrypted.EncryptedType
import de.jepfa.yapm.model.encrypted.EncryptedType.Types.ENC_SALT
import de.jepfa.yapm.model.secret.Key
import de.jepfa.yapm.service.PreferenceService
import de.jepfa.yapm.service.secret.SecretService.ALIAS_KEY_SALT
import de.jepfa.yapm.service.secret.SecretService.decryptKey
import de.jepfa.yapm.service.secret.SecretService.encryptKey
import de.jepfa.yapm.service.secret.SecretService.generateRandomKey
import de.jepfa.yapm.service.secret.SecretService.getAndroidSecretKey
import de.jepfa.yapm.util.Constants
import java.util.*

object SaltService {

    @Synchronized
    fun getSalt(context: Context): Key {
        val encSalt = PreferenceService.getEncrypted(PreferenceService.DATA_SALT, context)
        if (encSalt != null) {
            val saltKey = getAndroidSecretKey(ALIAS_KEY_SALT)
            val salt = decryptKey(saltKey, encSalt)
            return salt
        }
        else {
            return createAndStoreSalt(context)
        }
    }

    fun getSaltAsBase64String(context: Context): String {
        val salt = getSalt(context)
        return Base64.encodeToString(salt.data, Base64.DEFAULT)
    }

    fun storeSaltFromBase64String(saltAsBase64String: String, context: Context) {
        val salt = Key(Base64.decode(saltAsBase64String, 0))
        storeSalt(salt, context)
    }

    private fun storeSalt(salt: Key, context: Context) {
        val saltKey = getAndroidSecretKey(ALIAS_KEY_SALT)
        val encSalt = encryptKey(EncryptedType(ENC_SALT), saltKey, salt)
        PreferenceService.putEncrypted(PreferenceService.DATA_SALT, encSalt, context)
    }

    fun saltToVaultId(saltAsBase64String: String): String {
        return saltAsBase64String
            .toLowerCase(Locale.ROOT)
            .replace(Regex("[^0-9a-z]"),"")
            .take(8)
    }

    private fun createAndStoreSalt(context: Context): Key {
        val salt = generateRandomKey(Constants.MASTER_KEY_BYTE_SIZE)
        storeSalt(salt, context)

        return salt
    }

}