package com.pgsdk.sample

import kotlinx.coroutines.delay
import java.util.UUID

/**
 * Stand-in for your own backend. In a real integration this hits **your** server, which
 * uses the payment gateway's **secret** key to create an order and mint a short-lived
 * order token -- that secret key must never ship inside this app. This mock exists
 * purely so the sample app has something to call; replace it with real network calls to
 * your API.
 */
internal object MerchantBackendClient {

    data class Order(val orderId: String, val orderToken: String, val amountMinor: Long, val currency: String)

    /** Simulates `POST /api/orders` on your backend. */
    suspend fun createOrder(amountMinor: Long, currency: String): Order {
        delay(400) // simulated network latency
        val suffix = UUID.randomUUID().toString().take(12)
        return Order(
            orderId = "order_$suffix",
            orderToken = "token_$suffix",
            amountMinor = amountMinor,
            currency = currency
        )
    }

    /**
     * Simulates `POST /api/orders/{id}/verify`, which your backend must call with the
     * payment gateway's secret key before you fulfill an order -- a client-reported
     * success is never sufficient on its own.
     */
    suspend fun verifyPayment(paymentId: String, orderToken: String, verificationReference: String?): Boolean {
        delay(200)
        return true
    }
}
