package com.pgsdk.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Terminal outcome of a checkout flow, delivered exactly once per [PGPaymentRequest].
 *
 * [Success] and [Pending] carry a [paymentId] issued by the gateway. This is a
 * *reference*, not proof of payment -- always confirm the payment server-to-server
 * (webhook + signature verification, or a status poll) on your backend before releasing
 * goods/services. Never trust a client-side [Success] alone.
 */
sealed class PGPaymentResult : Parcelable {

    abstract val orderId: String?

    @Parcelize
    data class Success(
        override val orderId: String,
        val paymentId: String,
        val method: PGPaymentMethod? = null,
        val rawReference: String? = null
    ) : PGPaymentResult()

    @Parcelize
    data class Pending(
        override val orderId: String,
        val paymentId: String? = null,
        val message: String? = null
    ) : PGPaymentResult()

    @Parcelize
    data class Failure(
        override val orderId: String?,
        val error: PGError
    ) : PGPaymentResult()

    @Parcelize
    data class Cancelled(
        override val orderId: String?
    ) : PGPaymentResult()
}
