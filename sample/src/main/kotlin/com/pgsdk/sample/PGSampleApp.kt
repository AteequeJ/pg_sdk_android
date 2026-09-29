package com.pgsdk.sample

import android.app.Application
import com.pgsdk.core.PGConfig
import com.pgsdk.core.PGEnvironment
import com.pgsdk.core.PGPaymentSDK

class PGSampleApp : Application() {

    override fun onCreate() {
        super.onCreate()
        PGPaymentSDK.initialize(
            this,
            PGConfig(
                merchantId = "sample_merchant",
                publishableKey = "pk_test_sample_12345",
                // Never actually resolved -- MockGatewayInterceptor below intercepts every
                // request in-process before any network I/O happens, so this just needs to
                // be a well-formed HTTPS URL. Delete debugInterceptors and point this at
                // your real gateway (see docs/API.md) for a real integration.
                backendBaseUrl = "https://sandbox-api.example-pg.com/",
                environment = PGEnvironment.SANDBOX,
                enableLogging = true,
                debugInterceptors = listOf(MockGatewayInterceptor())
            )
        )
    }
}
