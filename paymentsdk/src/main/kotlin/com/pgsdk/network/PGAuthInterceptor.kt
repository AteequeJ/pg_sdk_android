package com.pgsdk.network

import com.pgsdk.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the merchant's publishable (public) key and SDK identification headers to
 * every request. Never attaches a secret key -- the SDK never holds one.
 */
internal class PGAuthInterceptor(private val publishableKey: String) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Authorization", "Bearer $publishableKey")
            .header("Accept", "application/json")
            .header("User-Agent", "pg-android-sdk/${BuildConfig.SDK_VERSION}")
            .header("X-SDK-Platform", "android")
            .header("X-SDK-Version", BuildConfig.SDK_VERSION)
            .build()
        return chain.proceed(request)
    }
}
