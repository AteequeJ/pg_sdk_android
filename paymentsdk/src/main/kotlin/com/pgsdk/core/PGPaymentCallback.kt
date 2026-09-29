package com.pgsdk.core

import com.pgsdk.model.PGPaymentResult

/**
 * Receives the terminal outcome of a checkout flow. Prefer [PGPaymentLauncher], which
 * wires this up for you against the Activity Result API; implement this directly only
 * if you need to plug into a custom flow.
 */
fun interface PGPaymentCallback {
    fun onPaymentResult(result: PGPaymentResult)
}
