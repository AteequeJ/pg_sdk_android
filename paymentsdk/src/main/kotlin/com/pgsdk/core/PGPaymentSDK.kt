package com.pgsdk.core

import android.content.Context
import com.pgsdk.BuildConfig
import com.pgsdk.di.PGServiceLocator
import com.pgsdk.util.PGLogger
import java.util.concurrent.atomic.AtomicReference

/**
 * Public entry point of the Payment Gateway SDK.
 *
 * Typical usage:
 * ```
 * // Application.onCreate()
 * PGPaymentSDK.initialize(
 *     this,
 *     PGConfig(
 *         merchantId = "merchant_123",
 *         publishableKey = "pk_live_xxx",       // publishable key ONLY, never a secret key
 *         backendBaseUrl = "https://api.example.com/",
 *         environment = PGEnvironment.PRODUCTION
 *     )
 * )
 *
 * // In an Activity/Fragment (onCreate, before STARTED):
 * val paymentLauncher = PGPaymentLauncher.create(this) { result ->
 *     when (result) {
 *         is PGPaymentResult.Success -> ...
 *         is PGPaymentResult.Pending -> ...
 *         is PGPaymentResult.Failure -> ...
 *         is PGPaymentResult.Cancelled -> ...
 *     }
 * }
 *
 * // Later, e.g. on a "Pay" button click, using an order created by YOUR backend:
 * paymentLauncher.launch(
 *     PGPaymentRequest(orderId = order.id, orderToken = order.token, amountMinor = order.amountMinor)
 * )
 * ```
 *
 * This object holds no payment credentials of its own -- it only forwards the
 * publishable key supplied via [PGConfig] to your backend, which is responsible for all
 * gateway integration and payment verification using server-side secret credentials.
 */
object PGPaymentSDK {

    private val configRef = AtomicReference<PGConfig?>(null)

    /** True once [initialize] has been called successfully. */
    val isInitialized: Boolean get() = configRef.get() != null

    /** The SDK's own version, e.g. for display in support tooling or bug reports. */
    val version: String get() = BuildConfig.SDK_VERSION

    /**
     * Configures the SDK. Safe to call more than once (e.g. across process restarts in
     * tests) -- a later call simply reconfigures the SDK; in-flight checkouts started
     * under a previous configuration are not affected.
     *
     * Must be called from the application (not activity) context, ideally from
     * [android.app.Application.onCreate], before [PGPaymentLauncher.create] /
     * [PGPaymentLauncher.launch] are used.
     */
    fun initialize(context: Context, config: PGConfig) {
        PGLogger.configure(config.enableLogging)
        PGServiceLocator.initialize(context.applicationContext, config)
        configRef.set(config)
        PGLogger.i("Initialized (merchantId=${config.merchantId}, env=${config.environment})")
    }

    internal fun requireConfig(): PGConfig =
        configRef.get() ?: throw PGSdkException.NotInitialized()
}
