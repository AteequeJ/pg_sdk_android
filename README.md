# PG Android Payment SDK

Native Android SDK for UPI (intent + collect), card and net-banking checkout. It
presents its own checkout screens and returns a typed success / pending / failure /
cancelled result, while every secret and every payment verification stays on your
backend.

| | |
|---|---|
| Maven coordinates | `io.github.ateequej:paymentsdk:1.0.0` |
| Min SDK | 24 (Android 7.0) |
| Language | Kotlin (usable from Java) |
| Step-by-step guide | [`docs/INTEGRATION.md`](docs/INTEGRATION.md) |
| REST contract | [`docs/API.md`](docs/API.md) |

Using Flutter? Use [`pg_flutter_sdk`](https://pub.dev/packages/pg_flutter_sdk), which
wraps this SDK. Building for iOS? See [`pg_sdk_ios`](https://github.com/AteequeJ/pg_sdk_ios).

## Before you start

The SDK is the **client half** of a payment. Your integration also needs a backend that:

1. Creates each order with the gateway using your **secret** key, and returns its
   `orderId` + `orderToken` to the app. The app never sees the secret key.
2. Verifies a payment server-to-server before fulfilling the order. A `Success` in the
   app is a claim, not proof.

Until you have a backend, you can run the whole checkout against a mock, as described in
[Testing without a backend](#testing-without-a-backend).

## Installation

### Option A: from Maven Central (recommended)

The SDK is published to Maven Central, which Android projects already use, so you only
add the dependency.

`app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.ateequej:paymentsdk:1.0.0")
}
```

<details><summary>Groovy (<code>app/build.gradle</code>)</summary>

```groovy
dependencies {
    implementation 'io.github.ateequej:paymentsdk:1.0.0'
}
```
</details>

Make sure `mavenCentral()` is in your repositories. New projects have it in
`settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

### Option B: from a local checkout (SDK development)

Use this to test changes to the SDK itself in an app before they're released.

1. Clone this repo and publish it to your local Maven repository (`~/.m2`):

   ```sh
   git clone https://github.com/AteequeJ/pg_sdk_android.git
   cd pg_sdk_android
   ./gradlew :paymentsdk:publishToMavenLocal
   ```

   To publish under a different version, add `-PsdkVersionName=1.0.1-SNAPSHOT`.

2. In your app's `settings.gradle.kts`, add `mavenLocal()` **first**, limited to this
   SDK's group so nothing else gets resolved from `~/.m2`:

   ```kotlin
   dependencyResolutionManagement {
       repositories {
           mavenLocal { content { includeGroup("io.github.ateequej") } }
           google()
           mavenCentral()
       }
   }
   ```

3. Keep the same `implementation("io.github.ateequej:paymentsdk:<version>")` line. Re-run
   `publishToMavenLocal` after each SDK change, then rebuild the app.

Remove the `mavenLocal` block before you ship.

### What the SDK adds to your app

You don't need to set anything up for these; the SDK's manifest is merged into your
app's automatically:

- `INTERNET` and `ACCESS_NETWORK_STATE` permissions.
- A `<queries>` entry for the `upi` scheme, so installed UPI apps can be found on
  Android 11+.
- The checkout activity (`PGCheckoutActivity`).

## Quick start

### 1. Initialize once, in `Application.onCreate()`

```kotlin
import com.pgsdk.core.PGConfig
import com.pgsdk.core.PGEnvironment
import com.pgsdk.core.PGPaymentSDK

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PGPaymentSDK.initialize(
            this,
            PGConfig(
                merchantId = "acme",
                publishableKey = "pk_live_...",           // publishable key ONLY
                backendBaseUrl = "https://api.gateway.example.com/",
                environment = PGEnvironment.PRODUCTION,
                merchantDisplayName = "Acme Store",
            )
        )
    }
}
```

`PGConfig` throws `IllegalArgumentException` on bad config. For example, it rejects a key
that looks like a secret key (`sk_…`), or an `http://` URL in `PRODUCTION`.

### 2. Register a launcher in your Activity or Fragment

Create it in `onCreate()` or as a field. This is required by the AndroidX Activity
Result API, so the host must be a `ComponentActivity` (e.g. `AppCompatActivity`) or a
`Fragment`.

```kotlin
import com.pgsdk.core.PGPaymentLauncher
import com.pgsdk.model.PGPaymentResult

class CheckoutActivity : AppCompatActivity() {

    private val paymentLauncher = PGPaymentLauncher.create(this) { result ->
        when (result) {
            is PGPaymentResult.Success -> {
                // result.paymentId: send it to YOUR backend to verify before fulfilling.
            }
            is PGPaymentResult.Pending -> {
                // Don't fulfil yet; your backend's webhook decides.
            }
            is PGPaymentResult.Failure -> {
                // result.error.code (PGErrorCode.*), result.error.isRetryable
            }
            is PGPaymentResult.Cancelled -> Unit
        }
    }
}
```

### 3. Start checkout for an order your backend created

```kotlin
import com.pgsdk.model.PGCustomerInfo
import com.pgsdk.model.PGPaymentRequest

val order = myBackend.createOrder(cart)          // your API, not the SDK's

paymentLauncher.launch(
    PGPaymentRequest(
        orderId = order.id,
        orderToken = order.token,
        amountMinor = 49_900,                    // ₹499.00, in paise
        currency = "INR",
        customer = PGCustomerInfo(name = "Jane", email = "jane@example.com"),
    )
)
```

### 4. Verify on your backend

Before shipping anything, your backend confirms the payment with the gateway using its
secret key, and treats the gateway's webhook as the source of truth for `Pending`
payments.

### Error codes

`PGPaymentResult.Failure.error.code` is one of `PGErrorCode`:
`sdk_not_initialized`, `invalid_config`, `invalid_request`, `network_error`, `timeout`,
`server_error`, `payment_declined`, `no_upi_app_found`, `unknown_error`. A user who
backs out arrives as `Cancelled`, not `Failure`.

### Optional configuration

| `PGConfig` field | Default | Purpose |
|---|---|---|
| `theme` | `PGCheckoutTheme.Default` | `primaryColor`, `onPrimaryColor`, or a full `styleRes` extending `Theme.PGSdk` |
| `allowedPaymentMethods` | all | Limit to e.g. `setOf(PGPaymentMethod.UPI, PGPaymentMethod.CARD)` |
| `enableLogging` | `false` | Verbose SDK logs. Keep it off in release builds |
| `connectTimeoutSeconds` / `readTimeoutSeconds` | 15 / 30 | Network timeouts (1–120) |

## Testing without a backend

**Sample app.** This repo's [`sample`](sample) module is a complete integration that
runs against an in-process mock, so it needs no server:

```sh
./gradlew :sample:installDebug
```

**Mock server.** [`tools/mock_gateway_server.py`](tools/mock_gateway_server.py)
implements every endpoint in [`docs/API.md`](docs/API.md) with scripted outcomes: for
example, card `4242 4242 4242 4242` succeeds, and UPI ID `fail@okhdfcbank` fails. The
script's header lists every test value. Unlike the in-process mock, it also serves the
net-banking and 3DS pages.

```sh
python3 tools/mock_gateway_server.py          # http://localhost:8080
```

Point your own app at it:

```kotlin
PGConfig(
    merchantId = "demo",
    publishableKey = "pk_test_demo",
    backendBaseUrl = "http://10.0.2.2:8080/",    // the host machine, from the emulator
    environment = PGEnvironment.SANDBOX,         // http is only allowed in SANDBOX
)
```

Any non-empty `orderId` / `orderToken` works with the mock.

**In-process mock.** Pass `debugInterceptors = listOf(PGDebugInterceptor { req -> ... })`
to answer gateway calls in memory, without a server. The SDK rejects this in
`PRODUCTION`.

## Security

- Only a **publishable** key belongs in the app. The SDK rejects keys that look secret.
- Card numbers, CVVs and UPI IDs go straight to the gateway. They're never sent to your
  backend, logged or stored. The only thing stored on the device (encrypted) is an
  in-flight attempt id.
- HTTPS is required in `PRODUCTION`.

## Developing this SDK

```
paymentsdk/   the SDK (the only module that gets published)
sample/       demo app, depends on :paymentsdk directly
tools/        mock gateway server
docs/         INTEGRATION.md (guide), API.md (REST contract)
```

```sh
./gradlew :paymentsdk:testDebugUnitTest       # unit tests
./gradlew :sample:installDebug                # run the demo against your changes
./gradlew :paymentsdk:publishToMavenLocal     # try it in another app (see Option B)
```

### Releasing a new version (maintainers)

1. You need Maven Central credentials and a GPG key in `~/.gradle/gradle.properties`
   (`mavenCentralUsername`, `mavenCentralPassword`, `signing.keyId`, `signing.password`,
   `signing.secretKeyRingFile`). Never put these in the repo.
2. Upload and release:

   ```sh
   ./gradlew :paymentsdk:publishAndReleaseToMavenCentral -PsdkVersionName=1.0.1
   ```

   Or use `publishToMavenCentral` to upload only, and click **Publish** at
   central.sonatype.com → Deployments. A released version can't be changed or deleted.
3. Bump the version in `pg_flutter_sdk/android/build.gradle.kts` so Flutter users get it.
