package com.pgsdk.core

import com.pgsdk.model.PGPaymentMethod

/**
 * One-time SDK configuration supplied by the merchant app, typically from
 * `Application.onCreate()`.
 *
 * Security notes:
 *  - [publishableKey] must be a *publishable* (public) key. It identifies your merchant
 *    account to your own backend / gateway but must not, by itself, authorize money
 *    movement. Never put a secret/server key here -- [PGPaymentSDK.initialize] rejects
 *    keys that look like common secret-key naming conventions as a defense-in-depth
 *    guard, but the app developer is ultimately responsible for using the right key.
 *  - [backendBaseUrl] is the payment gateway's own API base URL (see docs/API.md), e.g.
 *    `https://api.gateway.example.com/`. The SDK talks to the gateway directly using
 *    [publishableKey] -- your own backend is only involved in issuing the [orderToken]
 *    that authorizes each payment, per [com.pgsdk.model.PGPaymentRequest].
 *  - All traffic is required to be HTTPS in [PGEnvironment.PRODUCTION].
 *
 * @param merchantId Your merchant/account identifier, used for display and analytics.
 * @param publishableKey Public, non-secret key identifying your account to the gateway.
 * @param backendBaseUrl Base URL of the payment gateway's API, e.g.
 *   `https://api.gateway.example.com/`.
 * @param environment Affects SDK-side UX/validation only (see [PGEnvironment]).
 * @param merchantDisplayName Shown in the checkout UI's header (e.g. "Paying to Acme
 *   Store"). Defaults to [merchantId] if not set.
 * @param enableLogging Verbose internal SDK logs. Defaults to `false`; keep it that way
 *   in production builds. See [com.pgsdk.util.PGLogger].
 * @param connectTimeoutSeconds Network connect timeout.
 * @param readTimeoutSeconds Network read timeout.
 * @param theme Optional checkout UI theming.
 * @param allowedPaymentMethods Methods the checkout UI may offer. The backend's
 *   checkout-config response can further narrow this per-order.
 * @param debugInterceptors Intercepts gateway requests in-process, before any network
 *   I/O -- for a sample/demo app to exercise the full checkout flow against an in-memory
 *   mock instead of a real gateway (mirrors the iOS SDK's `debugURLProtocolClasses`).
 *   Must be empty in [PGEnvironment.PRODUCTION]; rejected by [init] otherwise so this
 *   can't accidentally ship enabled.
 */
data class PGConfig(
    val merchantId: String,
    val publishableKey: String,
    val backendBaseUrl: String,
    val environment: PGEnvironment = PGEnvironment.PRODUCTION,
    val merchantDisplayName: String = merchantId,
    val enableLogging: Boolean = false,
    val connectTimeoutSeconds: Long = 15L,
    val readTimeoutSeconds: Long = 30L,
    val theme: PGCheckoutTheme = PGCheckoutTheme.Default,
    val allowedPaymentMethods: Set<PGPaymentMethod> = PGPaymentMethod.entries.toSet(),
    val debugInterceptors: List<PGDebugInterceptor> = emptyList()
) {

    init {
        require(merchantId.isNotBlank()) { "merchantId must not be blank" }
        require(publishableKey.isNotBlank()) { "publishableKey must not be blank" }
        require(merchantDisplayName.isNotBlank()) { "merchantDisplayName must not be blank" }
        require(!looksLikeSecretKey(publishableKey)) {
            "publishableKey looks like a secret/server-side key. The SDK must only ever " +
                "receive a publishable/public key -- secret keys belong on your backend only."
        }
        require(backendBaseUrl.isNotBlank()) { "backendBaseUrl must not be blank" }
        require(isValidBackendUrl(backendBaseUrl, environment)) {
            "backendBaseUrl must be an HTTPS URL" +
                (if (environment == PGEnvironment.SANDBOX) " (http allowed only for localhost/10.0.2.2 in SANDBOX)" else "")
        }
        require(connectTimeoutSeconds in 1..120) { "connectTimeoutSeconds must be between 1 and 120" }
        require(readTimeoutSeconds in 1..120) { "readTimeoutSeconds must be between 1 and 120" }
        require(allowedPaymentMethods.isNotEmpty()) { "allowedPaymentMethods must not be empty" }
        require(environment != PGEnvironment.PRODUCTION || debugInterceptors.isEmpty()) {
            "debugInterceptors must not be set when environment is PRODUCTION."
        }
    }

    internal companion object {
        private val SECRET_KEY_PREFIXES = listOf("sk_", "secret_", "sk-", "server_")

        fun looksLikeSecretKey(key: String): Boolean {
            val normalized = key.lowercase()
            return SECRET_KEY_PREFIXES.any { normalized.startsWith(it) }
        }

        fun isValidBackendUrl(url: String, environment: PGEnvironment): Boolean {
            val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
            val scheme = uri.scheme?.lowercase()
            if (scheme == "https") return true
            if (scheme == "http" && environment == PGEnvironment.SANDBOX) {
                val host = uri.host?.lowercase()
                return host == "10.0.2.2" || host == "localhost" || host == "127.0.0.1"
            }
            return false
        }
    }
}
