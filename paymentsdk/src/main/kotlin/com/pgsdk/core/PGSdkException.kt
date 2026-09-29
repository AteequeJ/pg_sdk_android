package com.pgsdk.core

/** Thrown for programmer errors (misuse of the public API), never for payment failures. */
sealed class PGSdkException(message: String) : IllegalStateException(message) {

    class NotInitialized : PGSdkException(
        "PGPaymentSDK.initialize(context, config) must be called before starting a payment."
    )

    class InvalidRequest(reason: String) : PGSdkException("Invalid payment request: $reason")
}
