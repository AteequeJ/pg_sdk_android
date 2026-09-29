package com.pgsdk.handler

import android.content.Intent
import android.net.Uri

/**
 * Wraps the `upi://pay?...` URL returned by `POST /v1/payment_attempts/{id}/upi/intent`
 * into a launchable [Intent]. The gateway -- not the client -- constructs the URL (payee
 * VPA, amount, transaction reference), so this SDK never handles those fields directly.
 */
internal object PGUpiIntentBuilder {

    /**
     * @param targetPackage If set, restricts the intent to a specific installed UPI app
     *   (bypassing the system chooser); otherwise the system disambiguation UI is shown.
     */
    fun build(intentUrl: String, targetPackage: String? = null): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(intentUrl)).apply {
            if (targetPackage != null) setPackage(targetPackage)
        }
}
