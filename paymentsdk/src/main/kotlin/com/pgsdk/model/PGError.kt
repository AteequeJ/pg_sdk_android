package com.pgsdk.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Machine-readable, stable error codes returned to the merchant app. Never contains
 * sensitive details (no stack traces, no raw gateway payloads) — only what an app needs
 * to branch its UX and what a support agent needs to triage.
 */
object PGErrorCode {
    const val NOT_INITIALIZED = "sdk_not_initialized"
    const val INVALID_CONFIG = "invalid_config"
    const val INVALID_REQUEST = "invalid_request"
    const val NETWORK_ERROR = "network_error"
    const val TIMEOUT = "timeout"
    const val SERVER_ERROR = "server_error"
    const val PAYMENT_DECLINED = "payment_declined"
    const val NO_UPI_APP_FOUND = "no_upi_app_found"
    const val USER_CANCELLED = "user_cancelled"
    const val UNKNOWN = "unknown_error"
}

@Parcelize
data class PGError(
    val code: String,
    val message: String,
    val isRetryable: Boolean = false
) : Parcelable
