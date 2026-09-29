# Payment Gateway REST API contract

This is the contract `paymentsdk` implements against (see `com.pgsdk.network.PGApiService`
and `com.pgsdk.network.dto.PaymentAttemptDtos`). It is cross-checked field-for-field
against the sibling iOS SDK (`pg_ios_sdk`'s `PGAPIModels.swift` / `PGAPIService.swift`),
which is the authoritative reference for this contract -- keep both in sync if either
changes.

## Transport & auth

- All calls are HTTPS. `PGConfig.backendBaseUrl` must be an `https://` URL, except in
  `PGEnvironment.SANDBOX` where `http://10.0.2.2`, `http://localhost`, and
  `http://127.0.0.1` are allowed (emulator-to-host local testing only).
- Every request carries:
  ```
  Authorization: Bearer <publishableKey>
  Accept: application/json
  User-Agent: pg-android-sdk/<version>
  ```
  `publishableKey` is a public, non-secret key (see `PGConfig`). The SDK never holds or
  transmits a secret/server key.
- `POST /v1/payment_attempts` additionally carries:
  ```
  Idempotency-Key: {orderToken}_{method}
  ```
  A retried call with the same key against the same order+method returns the existing
  attempt rather than creating a duplicate. The client relies on this: every user action
  (launching a UPI app, submitting a VPA, submitting a card, picking a bank) calls create
  fresh rather than caching an attempt id locally.
- Every sub-endpoint scoped to an attempt (`upi/intent`, `upi/collect`, `card/tokenize`,
  `net_banking/initiate`) carries the attempt id **both** in the URL path and redundantly
  in the JSON body (`"attempt_id": "..."`) -- matches the real gateway's expected body
  shape, confirmed against the iOS client.
- All JSON keys are `snake_case`.

## Error format

Any non-2xx response body:
```json
{ "code": "card_declined", "message": "The card was declined by the issuer." }
```
`code` is a stable, machine-readable string surfaced to the merchant app via
`PGError.code`; `message` is shown to the end user as-is. 5xx is treated as retryable,
4xx as not (see `PGErrorMapper`).

## Payment attempt lifecycle

```
created ──▶ pending ──▶ succeeded
                    └──▶ failed
                    └──▶ cancelled
```
`GET /v1/payment_attempts/{id}` is polled (`PGStatusPoller`: 1.5s initial delay, ×1.6
backoff, 8s cap, 20 attempts max -- matches the iOS poller exactly) until `status` leaves
`created`/`pending`. A poller timeout surfaces `PGPaymentResult.Pending` to the merchant
app, not a hard failure -- resolve `.pending` outcomes via your backend's own webhook
from the gateway, never by polling further client-side.

## Endpoints

### Create a payment attempt

```
POST /v1/payment_attempts
Idempotency-Key: {orderToken}_{method}
```
Request:
```json
{
  "order_token": "tok_abc123",
  "method": "upi",
  "amount_minor_units": 49900,
  "currency": "INR",
  "customer_name": "Jane Doe",
  "customer_email": "jane@example.com",
  "customer_phone": "+919876543210",
  "metadata": { "order_id": "order_789" }
}
```
`method` is one of `upi` | `card` | `net_banking`. `amount_minor_units` is in minor units
(paise). `customer_*` fields and `metadata` are optional.

Response (`200`) -- deliberately narrower than the status response below:
```json
{ "attempt_id": "att_xyz789", "order_token": "tok_abc123", "status": "created" }
```

### Poll attempt status

```
GET /v1/payment_attempts/{id}
```
Response:
```json
{
  "attempt_id": "att_xyz789",
  "order_token": "tok_abc123",
  "status": "succeeded",
  "method": "upi",
  "verification_reference": "gw_ref_456",
  "failure_reason": null
}
```
`failure_reason` is a single human-readable string populated when `status: "failed"`
(there is no separate machine-readable failure code from this endpoint -- the client
surfaces it as `PGError.code = PAYMENT_DECLINED` with this string as the message).
`verification_reference` (when present on `succeeded`) is surfaced as
`PGPaymentResult.Success.rawReference` -- a hint only. **The merchant backend must
independently verify the payment against the gateway using its secret key before
fulfilling the order; a client-side `succeeded` is never proof of payment.**

### Create a UPI intent

```
POST /v1/payment_attempts/{id}/upi/intent
```
Request:
```json
{ "attempt_id": "att_xyz789" }
```
Response:
```json
{ "intent_url": "upi://pay?pa=merchant@bank&pn=Acme&am=499.00&cu=INR&tr=att_xyz789" }
```
The gateway constructs the full `upi://pay` URL (payee VPA, amount, transaction
reference) -- the client only wraps it in an `Intent` (`PGUpiIntentBuilder`) and never
constructs UPI parameters itself.

### UPI collect (VPA push)

```
POST /v1/payment_attempts/{id}/upi/collect
```
Request:
```json
{ "attempt_id": "att_xyz789", "vpa": "jane@okhdfcbank" }
```
Response:
```json
{ "status": "pending" }
```
Just the status -- not the full attempt. The client polls `GET .../{id}` separately for
the terminal outcome.

### Card tokenize

```
POST /v1/payment_attempts/{id}/card/tokenize
```
Request:
```json
{
  "attempt_id": "att_xyz789",
  "card_number": "4111111111111111",
  "expiry_month": "12",
  "expiry_year": "2030",
  "cvv": "123",
  "cardholder_name": "Jane Doe"
}
```
`cardholder_name` is **required** by the gateway (not optional). Response:
```json
{ "card_token": "tok_card_abc", "three_ds_redirect_url": null, "status": "pending" }
```
When `three_ds_redirect_url` is present, the client shows it in a WebView
(`PGWebRedirectFragment`) and polls the attempt status in the background rather than
inspecting the redirect's own URL. **This request body goes straight to the gateway and
is never sent to, or persisted by, anything else** -- not the merchant backend, not
`PGSecureStorage`, not HTTP logs (logging is BASIC-level: headers/status only, no body).

### List supported banks

```
GET /v1/net_banking/banks
```
Response (note: wrapped in a `banks` object, not a bare array):
```json
{
  "banks": [
    { "id": "HDFC", "name": "HDFC Bank", "icon_url": null },
    { "id": "ICIC", "name": "ICICI Bank", "icon_url": null }
  ]
}
```
Independent of any attempt -- fetched as soon as the user picks Net Banking.

### Net banking initiate

```
POST /v1/payment_attempts/{id}/net_banking/initiate
```
Request:
```json
{ "attempt_id": "att_xyz789", "bank_id": "HDFC" }
```
Response:
```json
{ "redirect_url": "https://bank.example.com/auth?attempt=att_xyz789" }
```
Shown in a WebView the same way as the card 3DS redirect; same "never trust the URL,
only the poll" rule applies.

## Key rules (client-side)

1. The SDK never mints orders -- `orderToken` must come from the merchant's own backend
   before calling the SDK (`PGPaymentRequest.orderToken`).
2. A client-side terminal result is not proof of payment -- the merchant's backend must
   independently verify `paymentId`/`verification_reference` against the gateway using
   its secret key before fulfilling an order.
3. Card number/CVV/VPA go straight to the gateway API -- never to the merchant's own
   backend, never persisted by the SDK (`PGSecureStorage` only ever holds an attempt id).
4. `.pending` outcomes (e.g. UPI collect awaiting approval) should be resolved via the
   merchant's backend webhook in the merchant's own app logic -- the SDK's own polling
   exists only to drive the checkout UI to a terminal screen, not as the source of truth.

## Testing locally

`tools/mock_gateway_server.py` is a stdlib-only Python mock implementing every endpoint
above with scriptable outcomes (see its header comment for the full cheatsheet of test
card numbers / VPAs / bank flows). Run it, then point `PGConfig.backendBaseUrl` at it
(the sample app already does, via `http://10.0.2.2:8080/` in `PGSampleApp.kt`):

```bash
python3 tools/mock_gateway_server.py
```

For fast, no-process-required testing of the SDK's own logic (as opposed to the sample
app's UI), prefer an in-memory fake `PGApiService` in unit tests -- mirrors the iOS SDK's
`MockHTTPClient` pattern (`Tests/PGPaymentSDKTests/Mocks/MockHTTPClient.swift` in
`pg_ios_sdk`). `paymentsdk`'s own test suite should do the same rather than spinning up
the mock server.
