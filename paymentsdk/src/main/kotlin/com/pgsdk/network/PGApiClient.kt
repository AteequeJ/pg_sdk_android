package com.pgsdk.network

import com.pgsdk.core.PGConfig
import com.pgsdk.core.PGDebugRequest
import com.pgsdk.util.PGLogger
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.Buffer
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/** Builds the Retrofit/OkHttp stack used to talk to the merchant backend. */
internal object PGApiClient {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun create(config: PGConfig): PGApiService {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            PGLogger.d(message, subTag = "Http")
        }.apply {
            // BASIC only: never log request/response bodies, which may contain PII or
            // gateway session data, even when the merchant opts into logging.
            level = if (config.enableLogging) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val builder = OkHttpClient.Builder()
            .connectTimeout(config.connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(config.readTimeoutSeconds, TimeUnit.SECONDS)

        // Placed first so a matching debug interceptor short-circuits the request before
        // it's ever touched by auth headers, logging, or the real network -- mirrors the
        // iOS SDK's debugURLProtocolClasses, which intercepts at the URLSession layer.
        if (config.debugInterceptors.isNotEmpty()) {
            builder.addInterceptor(debugInterceptorBridge(config))
        }

        val okHttpClient = builder
            .addInterceptor(PGAuthInterceptor(config.publishableKey))
            .addInterceptor(loggingInterceptor)
            .build()

        val baseUrl = if (config.backendBaseUrl.endsWith("/")) {
            config.backendBaseUrl
        } else {
            "${config.backendBaseUrl}/"
        }

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        return retrofit.create(PGApiService::class.java)
    }

    /** Bridges the SDK's OkHttp-free [com.pgsdk.core.PGDebugInterceptor] into a real interceptor. */
    private fun debugInterceptorBridge(config: PGConfig) = Interceptor { chain ->
        val request = chain.request()
        val bodyJson = request.body?.let { body ->
            Buffer().also { body.writeTo(it) }.readUtf8()
        }
        val debugRequest = PGDebugRequest(
            method = request.method,
            path = request.url.encodedPath,
            bodyJson = bodyJson
        )

        val debugResponse = config.debugInterceptors.firstNotNullOfOrNull { it.intercept(debugRequest) }
        if (debugResponse == null) {
            chain.proceed(request)
        } else {
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(debugResponse.statusCode)
                .message("Mocked")
                .apply { debugResponse.headers.forEach { (name, value) -> header(name, value) } }
                .body(debugResponse.bodyJson.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }
}
