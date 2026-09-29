package com.pgsdk.model

/**
 * Payment methods supported by the checkout UI. The set a merchant allows for a given
 * payment is controlled via [com.pgsdk.core.PGConfig.allowedPaymentMethods] and can be
 * further narrowed per-request by the backend's checkout-methods response.
 */
enum class PGPaymentMethod {
    UPI,
    CARD,
    NET_BANKING
}

/** Wire value expected by the gateway's `method` field (see docs/API.md). */
internal val PGPaymentMethod.wireValue: String
    get() = when (this) {
        PGPaymentMethod.UPI -> "upi"
        PGPaymentMethod.CARD -> "card"
        PGPaymentMethod.NET_BANKING -> "net_banking"
    }

internal fun pgPaymentMethodFromWireValue(value: String?): PGPaymentMethod? =
    PGPaymentMethod.entries.find { it.wireValue == value }
