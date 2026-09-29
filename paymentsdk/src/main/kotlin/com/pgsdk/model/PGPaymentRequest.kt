package com.pgsdk.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Customer details prefilled into the checkout UI. All fields are optional and are only
 * used to prefill form fields / pass through to the backend — the SDK never uses these
 * for identity verification.
 */
@Parcelize
data class PGCustomerInfo(
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null
) : Parcelable

/**
 * Describes a payment the merchant app wants the user to complete.
 *
 * IMPORTANT: [orderId] and [orderToken] MUST originate from a call your app makes to
 * *your own backend*, which in turn creates the order with your payment gateway using
 * your secret/server-side credentials. The SDK never creates orders and never handles
 * secret keys — see the integration guide in docs/INTEGRATION.md.
 *
 * @param orderId Order identifier created by your backend.
 * @param orderToken Opaque, short-lived token your backend issued authorizing this
 *   device/session to complete [orderId]. Treat it like a bearer credential: it is sent
 *   over HTTPS to your backend only and is never logged.
 * @param amountMinor Amount in the currency's smallest unit (e.g. paise for INR, cents
 *   for USD) to avoid floating point rounding errors.
 * @param currency ISO 4217 currency code, e.g. "INR", "USD".
 */
@Parcelize
data class PGPaymentRequest(
    val orderId: String,
    val orderToken: String,
    val amountMinor: Long,
    val currency: String = "INR",
    val description: String? = null,
    val customer: PGCustomerInfo? = null,
    val notes: Map<String, String> = emptyMap()
) : Parcelable {

    init {
        require(orderId.isNotBlank()) { "orderId must not be blank" }
        require(orderToken.isNotBlank()) { "orderToken must not be blank" }
        require(amountMinor > 0) { "amountMinor must be greater than zero" }
        require(currency.length == 3) { "currency must be a 3-letter ISO 4217 code" }
    }
}
