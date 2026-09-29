package com.pgsdk.util

import android.util.Log

/**
 * Internal logging facade. Disabled by default; enabled only when the merchant app opts
 * in via `PGConfig(enableLogging = true)`. Never logs raw request/response bodies --
 * only high-level lifecycle events -- and callers should route anything containing PII
 * or tokens through [redact] first.
 *
 * Merchants should leave logging disabled in production. Even when enabled, no
 * card/UPI/bank credentials are ever logged by this SDK.
 */
object PGLogger {

    private const val TAG = "PGPaymentSDK"

    @Volatile
    var isEnabled: Boolean = false
        internal set

    internal fun configure(enabled: Boolean) {
        isEnabled = enabled
    }

    fun d(message: String, subTag: String? = null) {
        if (isEnabled) Log.d(tag(subTag), message)
    }

    fun i(message: String, subTag: String? = null) {
        if (isEnabled) Log.i(tag(subTag), message)
    }

    fun w(message: String, throwable: Throwable? = null, subTag: String? = null) {
        if (isEnabled) Log.w(tag(subTag), message, throwable)
    }

    fun e(message: String, throwable: Throwable? = null, subTag: String? = null) {
        if (isEnabled) Log.e(tag(subTag), message, throwable)
    }

    private fun tag(subTag: String?): String = if (subTag.isNullOrBlank()) TAG else "$TAG:$subTag"

    /**
     * Masks a sensitive value for logging, keeping only a short suffix for correlation
     * (e.g. "order token ...ab12"). Never pass raw PAN/CVV/UPI PIN through this or any
     * other logging path -- those must never be logged at all.
     */
    fun redact(value: String, keepLast: Int = 4): String {
        if (value.length <= keepLast) return "*".repeat(value.length)
        return "*".repeat(value.length - keepLast) + value.takeLast(keepLast)
    }
}
