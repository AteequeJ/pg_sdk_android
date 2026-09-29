# Integrating the PG Payment SDK (Android)

A step-by-step guide to adding `paymentsdk` to your own Android app, from nothing to a
working checkout. For the full type-by-type reference (every field, every error code,
the gateway's REST contract) see [`API.md`](API.md) — this doc is the narrative
walkthrough; that one is what you'll keep open while coding.

This guide assumes you're consuming the SDK as a published dependency in your own app.
You do **not** need this repository checked out, its sample app running, or any local
build step to integrate it.

## Before you start

This SDK is the **client half** of a payment integration. It cannot do anything on its
own — it needs a backend on the other end that:

1. Creates orders and mints an `orderToken` per payment attempt, using your payment
   gateway's **secret** key (never shipped in your app).
2. Independently verifies a completed payment server-to-server before you fulfill an
   order — a `Success` result on the client is a claim, not proof.

The SDK itself talks directly to the payment gateway's own REST API (see
[the contract in API.md](API.md)) using only a publishable (public) key — your backend's
job is minting `orderToken`s and verifying outcomes, not proxying every gateway call.

If you don't have that backend yet, skip to
[Testing before you have a real backend](#testing-before-you-have-a-real-backend) to get
the UI running against an in-process mock first — it's a faster way to build the rest of
your integration in parallel.

**Requirements:** minSdk 24+, Kotlin 2.0+, AGP 8.5+.

## Step 1 — Add the dependency

The SDK is on Maven Central, which new projects already list in `settings.gradle.kts`:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("io.github.ateequej:paymentsdk:1.0.0")
}
```

To try unreleased changes from a local checkout of this repo instead, see
"Option B" in the [README](../README.md#option-b-from-a-local-checkout-sdk-development).

**Nothing else to configure.** Unlike some SDKs, there's no manifest entry, deep-link
scheme, or ProGuard rule to add by hand — the checkout Activity, the `INTERNET`
permission, the package-visibility `<queries>` block needed to detect installed UPI
apps, and the consumer R8/ProGuard keep rules all ship inside the AAR and merge into
your app automatically.

## Step 2 — Configure the SDK at launch

Call `initialize(...)` once, from `Application.onCreate()`, before any screen might
launch a checkout:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PGPaymentSDK.initialize(
            this,
            PGConfig(
                merchantId = "your_merchant_id",
                publishableKey = "pk_live_...",      // client-safe key from your gateway dashboard
                backendBaseUrl = "https://api.yourgateway.com/",
                environment = PGEnvironment.PRODUCTION, // .SANDBOX while testing against staging
                merchantDisplayName = "Acme Store"      // shown in the checkout UI
            )
        )
    }
}
```

Don't forget to point your `AndroidManifest.xml`'s `<application android:name>` at this
class if you haven't already.

A few things `PGConfig` enforces for you at construction time (it throws
`IllegalArgumentException` synchronously — treat this as a startup-time bug to fix, not
a runtime condition to recover from), so you find out at launch rather than in front of
a customer:
- `publishableKey` is rejected if it looks like a secret key (`sk_`, `secret_`, ... ) —
  the SDK has no code path that accepts one.
- `PRODUCTION` requires an HTTPS `backendBaseUrl`.
- `debugInterceptors` (see [Testing](#testing-before-you-have-a-real-backend)) is
  rejected outright under `PRODUCTION`, so a mock can't accidentally ship enabled.

## Step 3 — Create an order on your backend

Before starting a payment, your app calls **your own backend** — not the SDK — to
create an order. Something like:

```kotlin
data class Order(val orderId: String, val orderToken: String, val amountMinor: Long, val currency: String)

suspend fun createOrder(amountMinor: Long, currency: String): Order {
    // Your own API call, however you make network calls elsewhere in your app.
    return myApiService.createOrder(CreateOrderRequest(amountMinor, currency))
}
```

Your backend, in turn, calls your payment gateway's server-side API (using the
gateway's **secret** key) to actually create the order there, and returns the resulting
token to your app. The SDK never sees your secret key and never creates orders itself —
that asymmetry is the whole point: a client-only credential can't be trusted to mint a
payable order.

## Step 4 — Start a payment

Register a `PGPaymentLauncher` as a field, before your Activity/Fragment reaches
`STARTED` (the same rule as `registerForActivityResult` generally — a field initializer
in `onCreate()`, or a property initializer, both work):

```kotlin
class CheckoutActivity : AppCompatActivity() {

    private val paymentLauncher = PGPaymentLauncher.create(this) { result -> handle(result) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        payButton.setOnClickListener { startPayment() }
    }

    private fun startPayment() {
        lifecycleScope.launch {
            val order = createOrder(amountMinor = 49_900, currency = "INR")
            val request = PGPaymentRequest(
                orderId = order.orderId,
                orderToken = order.orderToken,
                amountMinor = order.amountMinor,
                currency = order.currency,
                customer = PGCustomerInfo(name = "Jane Doe", email = "jane@example.com")
            )
            paymentLauncher.launch(request)
        }
    }
}
```

`launch()` starts the checkout Activity; your result callback fires once the user
reaches a terminal outcome.

## Step 5 — Handle the result

```kotlin
private fun handle(result: PGPaymentResult) {
    when (result) {
        is PGPaymentResult.Success -> {
            // Ask YOUR backend to verify this before treating the order as paid.
            verifyOnBackend(paymentId = result.paymentId, reference = result.rawReference)
        }

        is PGPaymentResult.Failure -> {
            // result.error.code / .message / .isRetryable — a real gateway decline.
            // paymentId isn't on Failure directly, but result.error carries enough
            // to show the user what happened and whether Retry makes sense.
            showError(result.error.message)
        }

        is PGPaymentResult.Cancelled -> {
            // The user backed out and explicitly confirmed the "Cancel payment?"
            // prompt -- you'll only ever see this after that confirmation, never
            // from a stray back-press or swipe.
        }

        is PGPaymentResult.Pending -> {
            // Accepted but not yet confirmed (common for UPI collect requests).
            // Wait for your backend's webhook, or poll your own order status --
            // don't fulfill yet.
        }
    }
}
```

Two behaviors worth knowing about, since they affect what your users see before this
`when` ever runs:

- **The SDK shows its own confirmation screen first.** Before the checkout Activity
  finishes, the user briefly sees a checkmark/cross/clock screen ("Payment successful" /
  "Payment failed" / "Payment cancelled") for about a second and a half, matching the
  pattern used by Razorpay/PayU/Cashfree-style checkouts. Your result callback doesn't
  fire until that's done — you don't need (and shouldn't add) a redundant "Payment
  successful!" screen of your own immediately after.
- **Cancelling asks first.** Pressing back or the toolbar's up arrow mid-flow prompts
  "Cancel payment?" before actually cancelling. You'll only ever see `Cancelled` for a
  cancellation the user explicitly confirmed.

## Step 6 — Verify the payment server-side

This is the one step that happens entirely outside the SDK, but it's the step that makes
the rest of it safe. On `Success`, call your backend with `paymentId` and
`rawReference`; your backend calls your gateway's server-side verification API with its
secret key and only *then* fulfills the order. Never fulfill an order on the strength of
the client-side `Success` alone — treat it as "the client thinks this worked," not proof.

## Testing before you have a real backend

You don't need a live gateway to build and test your integration. Implement
`PGDebugInterceptor` and pass it via `PGConfig.debugInterceptors` — every gateway
request the SDK makes gets routed to your implementation in-process, before any network
I/O, instead of hitting the network:

```kotlin
fun interface PGDebugInterceptor {
    fun intercept(request: PGDebugRequest): PGDebugResponse?  // null falls through
}
```

This repository's `sample` module has a complete, working example:
[`sample/src/main/kotlin/com/pgsdk/sample/MockGatewayInterceptor.kt`](../sample/src/main/kotlin/com/pgsdk/sample/MockGatewayInterceptor.kt)
implements the whole contract (create attempt, UPI intent/collect, card tokenize, net
banking) with well-known test values (`4242 4242 4242 4242` succeeds, a card ending
`0002` declines; VPA `success@upi` / `failure@upi` for UPI — see the file's header
comment for the full list). Copy it as a starting point, or run
[`sample`](../sample) itself to see the full flow working end to end with nothing else
to set up:

```bash
git clone <this-repo>
cd pg_android_sdk
./gradlew :sample:installDebug
```

Remember `debugInterceptors` is rejected under `environment = PRODUCTION` — there's no
way to accidentally ship it turned on.

If you'd rather exercise the real Retrofit/OkHttp networking stack end-to-end (as
opposed to short-circuiting it), `tools/mock_gateway_server.py` in this repo is a
standalone local HTTP server implementing the same contract — see its header comment.

## Optional configuration

- **Theming.** `PGConfig.theme` (`PGCheckoutTheme`) takes either a full custom
  `styleRes` (extending `Theme.PGSdk`) or individual `primaryColor`/`onPrimaryColor`
  overrides for the common case.
- **Allowed methods.** `PGConfig.allowedPaymentMethods` restricts which of
  UPI/Card/Net Banking the checkout UI offers; defaults to all three (UPI is
  automatically hidden for non-INR orders regardless of this setting).
- **Logging.** `PGConfig.enableLogging` defaults to `false`. When enabled, HTTP logging
  is header/status-line only (`BASIC` level) — request/response bodies, which may
  contain PII, are never logged regardless of this setting.
- **Timeouts.** `PGConfig.connectTimeoutSeconds` / `readTimeoutSeconds`, both default to
  a reasonable value; override if your gateway is known to be slow.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| "No UPI apps found on this device" even with GPay/PhonePe installed | Rare if you didn't touch the manifest (the SDK's `<queries>` block merges in automatically) — check you haven't overridden `<queries>` in your own manifest and accidentally dropped it |
| `IllegalArgumentException` constructing `PGConfig` | Read the message — usually a blank field, a secret key passed as `publishableKey`, a non-HTTPS `backendBaseUrl` under `PRODUCTION`, or `debugInterceptors` set under `PRODUCTION` |
| `PGSdkException.NotInitialized` when launching checkout | `PGPaymentSDK.initialize(...)` wasn't called (or hasn't run yet) before `PGPaymentLauncher.launch()` |
| A gateway response fails to decode | Your gateway's JSON keys don't match the contract in [API.md](API.md) — required fields must be present; unknown extra fields are fine |
| Checkout screens fail with a network error in *your own* app | Expected until you wire up a real gateway or a mock — see [Testing](#testing-before-you-have-a-real-backend) |
| Net banking / 3DS WebView never resolves | The redirect page itself can't be mocked without a real HTTP server (it's a real web page, not a gateway API call) — use `tools/mock_gateway_server.py`, which serves an interactive mock page, rather than `debugInterceptors` for that specific case |

## Known differences from the iOS SDK

If you're integrating both platforms from the same team, two things intentionally (for
now) work differently on Android:

- **No certificate pinning yet.** The iOS SDK supports SPKI pinning
  (`PGConfiguration.pinning`); there's no Android equivalent yet.
- **No built-in double-launch guard.** iOS's `PGCheckout` rejects a second
  `startPayment` call while one is in flight. `PGPaymentLauncher.launch()` on Android
  does not currently guard against this — debounce your Pay button (e.g. disable it on
  tap) until this is added.

Two things are genuinely simpler on Android and need no equivalent setup at all:
no manifest/callback-URL-scheme configuration (UPI app return is handled via the
standard Activity Result API, and 3DS/net-banking redirects happen in an in-app WebView
rather than an external browser), and no consumer ProGuard rules to write by hand.

## Production checklist

- [ ] `publishableKey` is your live key, not a test one
- [ ] `environment = PGEnvironment.PRODUCTION`, and `debugInterceptors` is not set
- [ ] Orders are created server-side; no secret key anywhere in the app
- [ ] `Success` and `Pending` are both verified server-side before fulfillment — never
      trust the client alone
- [ ] `enableLogging` is `false` in your release build configuration
- [ ] You've run a minified (R8) release build at least once before shipping
