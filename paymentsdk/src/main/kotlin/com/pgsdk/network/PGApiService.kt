package com.pgsdk.network

import com.pgsdk.network.dto.CreatePaymentAttemptRequest
import com.pgsdk.network.dto.PGBankListResponse
import com.pgsdk.network.dto.PGCardTokenizeRequest
import com.pgsdk.network.dto.PGCardTokenizeResponse
import com.pgsdk.network.dto.PGNetBankingInitiateRequest
import com.pgsdk.network.dto.PGNetBankingInitiateResponse
import com.pgsdk.network.dto.PGPaymentAttempt
import com.pgsdk.network.dto.PGStatusResponse
import com.pgsdk.network.dto.PGUpiCollectRequest
import com.pgsdk.network.dto.PGUpiCollectResponse
import com.pgsdk.network.dto.PGUpiIntentRequest
import com.pgsdk.network.dto.PGUpiIntentResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit contract for the payment gateway's REST API (docs/API.md), kept in exact
 * parity with the iOS SDK's `PGAPIService.swift`. Every call is authenticated via
 * [PGAuthInterceptor] (`Authorization: Bearer <publishableKey>`); the base URL is
 * HTTPS-only by construction ([com.pgsdk.core.PGConfig] validates this).
 *
 * Card number/CVV/VPA values passed here go straight to the gateway and are never sent
 * to, or expected back from, any other host.
 */
internal interface PGApiService {

    @POST("v1/payment_attempts")
    suspend fun createPaymentAttempt(
        @Body request: CreatePaymentAttemptRequest,
        @Header("Idempotency-Key") idempotencyKey: String
    ): PGPaymentAttempt

    @GET("v1/payment_attempts/{id}")
    suspend fun getPaymentAttempt(@Path("id") attemptId: String): PGStatusResponse

    @POST("v1/payment_attempts/{id}/upi/intent")
    suspend fun createUpiIntent(
        @Path("id") attemptId: String,
        @Body request: PGUpiIntentRequest
    ): PGUpiIntentResponse

    @POST("v1/payment_attempts/{id}/upi/collect")
    suspend fun collectUpi(
        @Path("id") attemptId: String,
        @Body request: PGUpiCollectRequest
    ): PGUpiCollectResponse

    @POST("v1/payment_attempts/{id}/card/tokenize")
    suspend fun tokenizeCard(
        @Path("id") attemptId: String,
        @Body request: PGCardTokenizeRequest
    ): PGCardTokenizeResponse

    @GET("v1/net_banking/banks")
    suspend fun listBanks(): PGBankListResponse

    @POST("v1/payment_attempts/{id}/net_banking/initiate")
    suspend fun initiateNetBanking(
        @Path("id") attemptId: String,
        @Body request: PGNetBankingInitiateRequest
    ): PGNetBankingInitiateResponse
}
