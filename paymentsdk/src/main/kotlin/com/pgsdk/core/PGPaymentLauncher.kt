package com.pgsdk.core

import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import com.pgsdk.model.PGPaymentRequest

/**
 * Lifecycle-safe wrapper around [PGPaymentContract] / the AndroidX Activity Result API.
 *
 * Create exactly one instance per Activity/Fragment, in `onCreate()` (or as a field
 * initializer), before the host reaches `STARTED` -- this is an AndroidX Activity Result
 * API requirement, not an SDK-specific one, and matches how e.g.
 * `registerForActivityResult` is normally used.
 *
 * ```
 * private lateinit var paymentLauncher: PGPaymentLauncher
 *
 * override fun onCreate(savedInstanceState: Bundle?) {
 *     super.onCreate(savedInstanceState)
 *     paymentLauncher = PGPaymentLauncher.create(this) { result -> handle(result) }
 * }
 * ```
 */
class PGPaymentLauncher private constructor(
    private val launcher: ActivityResultLauncher<PGPaymentRequest>
) {

    fun launch(request: PGPaymentRequest) {
        check(PGPaymentSDK.isInitialized) { PGSdkException.NotInitialized().message!! }
        launcher.launch(request)
    }

    companion object {
        fun create(caller: ActivityResultCaller, callback: PGPaymentCallback): PGPaymentLauncher {
            val launcher = caller.registerForActivityResult(PGPaymentContract()) { result ->
                callback.onPaymentResult(result)
            }
            return PGPaymentLauncher(launcher)
        }
    }
}
