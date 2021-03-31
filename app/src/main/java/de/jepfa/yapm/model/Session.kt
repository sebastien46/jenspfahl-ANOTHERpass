package de.jepfa.yapm.model

import java.util.concurrent.TimeUnit
import javax.crypto.SecretKey

object Session {

    const val DEFAULT_LOCK_TIMEOUT = 2
    const val DEFAULT_LOGOUT_TIMEOUT = 10
    /**
     * After this period of time of inactivity the secret is outdated.
     */
    private var lock_timeout: Long = minutesToMillis(DEFAULT_LOCK_TIMEOUT)
    private var logout_timeout: Long = minutesToMillis(DEFAULT_LOGOUT_TIMEOUT)

    private var masterSecretKey: SecretKey? = null
    private var encMasterPassword: Encrypted? = null
    private var lastUpdated: Long = 0

    fun getMasterKeySK() : SecretKey? {
        return masterSecretKey
    }

    fun getEncMasterPasswd() :Encrypted? {
        return encMasterPassword
    }

    fun setTimeouts(lockTimeoutMinutes: Int?, logoutTimeoutMinutes: Int?) {
        if (lockTimeoutMinutes != null) lock_timeout = minutesToMillis(lockTimeoutMinutes)
        if (logoutTimeoutMinutes != null)logout_timeout = minutesToMillis(logoutTimeoutMinutes)
    }

    fun login(secretKey: SecretKey, encMasterPasswd: Encrypted) {
        masterSecretKey = secretKey
        encMasterPassword = encMasterPasswd
        touch()
    }

    fun touch() {
        lastUpdated = System.currentTimeMillis()
    }

    fun safeTouch() {
        if (!isLoggedOut() && !isLocked()) {
            touch()
        }
    }

    fun isOutdated(): Boolean {
        return age() > lock_timeout || shouldBeLoggedOut()
    }

    fun shouldBeLoggedOut(): Boolean {
        return age() > logout_timeout
    }

    fun isLocked() : Boolean {
        return masterSecretKey == null
    }

    fun isLoggedOut() : Boolean {
        return encMasterPassword == null
    }

    fun isDenied() : Boolean {
        return isLoggedOut() || isLocked() || isOutdated()
    }

    fun lock() {
        masterSecretKey = null
        touch()
    }

    fun logout() {
        encMasterPassword = null
        lock()
    }

    private fun age() = System.currentTimeMillis() - lastUpdated

    private fun minutesToMillis(value: Int) = TimeUnit.MINUTES.toMillis(value.toLong())

}
