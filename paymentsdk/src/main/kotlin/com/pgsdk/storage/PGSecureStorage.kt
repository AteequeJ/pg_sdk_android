package com.pgsdk.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.pgsdk.util.PGLogger

/**
 * Encrypted, app-private key-value storage backed by Jetpack Security
 * (AES256-GCM-encrypted [SharedPreferences] with an Android Keystore master key).
 *
 * Scope and limits -- read before storing anything new here:
 *  - Used only for short-lived, non-secret operational state that benefits from being
 *    protected at rest on a rooted/compromised device, e.g. the in-flight order id /
 *    payment attempt id for resuming a checkout flow interrupted by process death, or the
 *    user's last-used payment method as a UX convenience.
 *  - NEVER store card numbers, CVV, UPI PIN, OTPs, or gateway secret keys here (or
 *    anywhere on-device) -- those must never leave the gateway's own hosted UI /
 *    device-native input flows in the first place.
 *  - Values are cleared via [clearSession] once a checkout flow reaches a terminal
 *    state.
 */
internal class PGSecureStorage private constructor(private val prefs: SharedPreferences) {

    fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getString(key: String): String? = prefs.getString(key, null)

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_ACTIVE_ORDER_ID)
            .remove(KEY_ACTIVE_ATTEMPT_ID)
            .apply()
    }

    companion object {
        internal const val KEY_ACTIVE_ORDER_ID = "active_order_id"
        internal const val KEY_ACTIVE_ATTEMPT_ID = "active_attempt_id"
        internal const val KEY_LAST_PAYMENT_METHOD = "last_payment_method"

        private const val PREFS_FILE_NAME = "com.pgsdk.secure_prefs"

        fun create(context: Context): PGSecureStorage {
            val prefs = runCatching {
                val masterKey = MasterKey.Builder(context.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                EncryptedSharedPreferences.create(
                    context.applicationContext,
                    PREFS_FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            }.getOrElse { error ->
                // Extremely rare (e.g. Keystore corruption after an OS restore). Fall back
                // to a plain, app-private, non-backed-up prefs file rather than crashing the
                // host app -- nothing sensitive is ever stored here regardless.
                PGLogger.e("Falling back to unencrypted local storage", error)
                context.applicationContext.getSharedPreferences(
                    "${PREFS_FILE_NAME}_fallback",
                    Context.MODE_PRIVATE
                )
            }
            return PGSecureStorage(prefs)
        }
    }
}
