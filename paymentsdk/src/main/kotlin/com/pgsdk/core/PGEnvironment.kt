package com.pgsdk.core

/**
 * Controls SDK-side behavior that should differ between a merchant's test and live
 * setups (e.g. showing a "TEST MODE" banner in checkout UI, relaxed HTTPS-only checks
 * for `10.0.2.2`/localhost during local backend development).
 *
 * This does NOT select which payment gateway credentials are used -- that decision is
 * made entirely by your backend, which is the only place that ever sees secret keys.
 */
enum class PGEnvironment {
    SANDBOX,
    PRODUCTION
}
