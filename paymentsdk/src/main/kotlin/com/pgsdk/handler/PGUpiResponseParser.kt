package com.pgsdk.handler

import android.content.Intent

/**
 * Parses the `response` extra a UPI app returns via `setResult()`, e.g.
 * `"txnId=T123&responseCode=00&Status=SUCCESS&approvalRefNo=A1"`.
 *
 * This is treated only as a *hint* to drive UI (e.g. skip straight to a success
 * animation) -- the SDK always confirms the final outcome via [com.pgsdk.network.PGPaymentRepository.pollPaymentStatus]
 * against the backend before returning a terminal [com.pgsdk.model.PGPaymentResult],
 * since a malicious or misbehaving UPI app could otherwise spoof a success response.
 */
internal object PGUpiResponseParser {

    data class UpiAppResponse(
        val status: String?,
        val transactionId: String?,
        val approvalRefNo: String?,
        val responseCode: String?
    )

    fun parse(intent: Intent?): UpiAppResponse? {
        val raw = intent?.getStringExtra("response") ?: return null
        val fields = raw.split("&")
            .mapNotNull { pair ->
                val parts = pair.split("=", limit = 2)
                if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
            }
            .toMap()

        return UpiAppResponse(
            status = fields["Status"] ?: fields["status"],
            transactionId = fields["txnId"],
            approvalRefNo = fields["approvalRefNo"],
            responseCode = fields["responseCode"]
        )
    }

    fun isLikelySuccess(response: UpiAppResponse?): Boolean =
        response?.status?.equals("SUCCESS", ignoreCase = true) == true
}
