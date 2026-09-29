package com.pgsdk.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire-format DTOs for the payment gateway's REST API -- kept in exact parity with the
 * iOS SDK's `PGAPIModels.swift`/`PGAPIService.swift`, the authoritative reference for this
 * contract (see docs/API.md). Internal and deliberately kept separate from the public
 * `com.pgsdk.model` types so the network schema can evolve without breaking the public API.
 */

/** Canonical status strings returned by the gateway (`PGRemoteStatus` on iOS). */
internal object PGAttemptStatus {
    const val CREATED = "created"
    const val PENDING = "pending"
    const val SUCCEEDED = "succeeded"
    const val FAILED = "failed"
    const val CANCELLED = "cancelled"
}

/** Body for `POST /v1/payment_attempts`. */
@Serializable
internal data class CreatePaymentAttemptRequest(
    @SerialName("order_token") val orderToken: String,
    val method: String,
    @SerialName("amount_minor_units") val amountMinorUnits: Long,
    val currency: String,
    @SerialName("customer_name") val customerName: String? = null,
    @SerialName("customer_email") val customerEmail: String? = null,
    @SerialName("customer_phone") val customerPhone: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

/** Response for `POST /v1/payment_attempts` -- deliberately narrower than [PGStatusResponse]. */
@Serializable
internal data class PGPaymentAttempt(
    @SerialName("attempt_id") val attemptId: String,
    @SerialName("order_token") val orderToken: String,
    val status: String
)

/** Response for `GET /v1/payment_attempts/{id}`. */
@Serializable
internal data class PGStatusResponse(
    @SerialName("attempt_id") val attemptId: String,
    @SerialName("order_token") val orderToken: String,
    val status: String,
    val method: String,
    @SerialName("verification_reference") val verificationReference: String? = null,
    @SerialName("failure_reason") val failureReason: String? = null
)

// -- UPI ----------------------------------------------------------------------------

/** Body for `POST /v1/payment_attempts/{id}/upi/intent` -- `attemptId` is redundant with
 *  the URL path but matches the real gateway's expected body shape. */
@Serializable
internal data class PGUpiIntentRequest(
    @SerialName("attempt_id") val attemptId: String
)

@Serializable
internal data class PGUpiIntentResponse(
    @SerialName("intent_url") val intentUrl: String
)

@Serializable
internal data class PGUpiCollectRequest(
    @SerialName("attempt_id") val attemptId: String,
    val vpa: String
)

@Serializable
internal data class PGUpiCollectResponse(
    val status: String
)

// -- Card -----------------------------------------------------------------------------

/**
 * Body for `POST /v1/payment_attempts/{id}/card/tokenize`. Sent directly to the gateway
 * and never logged (HTTP logging is BASIC-level only, see [com.pgsdk.network.PGApiClient])
 * or persisted anywhere by the SDK. `cardholderName` is required by the gateway.
 */
@Serializable
internal data class PGCardTokenizeRequest(
    @SerialName("attempt_id") val attemptId: String,
    @SerialName("card_number") val cardNumber: String,
    @SerialName("expiry_month") val expiryMonth: String,
    @SerialName("expiry_year") val expiryYear: String,
    val cvv: String,
    @SerialName("cardholder_name") val cardholderName: String
)

@Serializable
internal data class PGCardTokenizeResponse(
    @SerialName("card_token") val cardToken: String,
    @SerialName("three_ds_redirect_url") val threeDsRedirectUrl: String? = null,
    val status: String
)

// -- Net Banking ------------------------------------------------------------------------

@Serializable
internal data class BankDto(
    val id: String,
    val name: String,
    @SerialName("icon_url") val iconUrl: String? = null
)

@Serializable
internal data class PGBankListResponse(
    val banks: List<BankDto>
)

@Serializable
internal data class PGNetBankingInitiateRequest(
    @SerialName("attempt_id") val attemptId: String,
    @SerialName("bank_id") val bankId: String
)

@Serializable
internal data class PGNetBankingInitiateResponse(
    @SerialName("redirect_url") val redirectUrl: String
)

/** Shape of every non-2xx error body: `{"code": "...", "message": "..."}`. */
@Serializable
internal data class ApiErrorBody(
    val code: String? = null,
    val message: String? = null
)
