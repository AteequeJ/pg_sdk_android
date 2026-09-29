package com.pgsdk.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.pgsdk.model.PGError
import com.pgsdk.model.PGErrorCode
import com.pgsdk.model.PGPaymentRequest
import com.pgsdk.model.PGPaymentResult
import com.pgsdk.ui.checkout.PGCheckoutActivity

/**
 * [ActivityResultContract] that launches the checkout UI for a [PGPaymentRequest] and
 * returns a [PGPaymentResult]. Prefer [PGPaymentLauncher] for a simpler call site --
 * this is exposed directly for apps that already manage their own
 * [androidx.activity.result.ActivityResultLauncher] registration.
 */
class PGPaymentContract : ActivityResultContract<PGPaymentRequest, PGPaymentResult>() {

    override fun createIntent(context: Context, input: PGPaymentRequest): Intent {
        check(PGPaymentSDK.isInitialized) { PGSdkException.NotInitialized().message!! }
        return Intent(context, PGCheckoutActivity::class.java).apply {
            putExtra(EXTRA_REQUEST, input)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): PGPaymentResult {
        if (resultCode != Activity.RESULT_OK || intent == null) {
            return PGPaymentResult.Cancelled(orderId = intent?.getStringExtra(EXTRA_CANCELLED_ORDER_ID))
        }
        return intent.getParcelableExtraCompat(EXTRA_RESULT) ?: PGPaymentResult.Failure(
            orderId = null,
            error = PGError(
                code = PGErrorCode.UNKNOWN,
                message = "Checkout finished without a result.",
                isRetryable = true
            )
        )
    }

    companion object {
        const val EXTRA_REQUEST = "com.pgsdk.extra.REQUEST"
        const val EXTRA_RESULT = "com.pgsdk.extra.RESULT"
        const val EXTRA_CANCELLED_ORDER_ID = "com.pgsdk.extra.CANCELLED_ORDER_ID"
    }
}

private inline fun <reified T> Intent.getParcelableExtraCompat(key: String): T? =
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key) as? T
    }
