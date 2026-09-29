package com.pgsdk.network

import com.pgsdk.model.PGCustomerInfo
import com.pgsdk.model.PGPaymentMethod
import com.pgsdk.model.wireValue
import com.pgsdk.network.dto.BankDto
import com.pgsdk.network.dto.CreatePaymentAttemptRequest
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
import kotlinx.coroutines.CancellationException

/**
 * Mediates between the checkout UI and the payment gateway's REST API (docs/API.md). All
 * suspend functions return [PGApiResult] instead of throwing, so ViewModels never need
 * try/catch around business logic. Mirrors the iOS SDK's `PGAPIService` + `PGPaymentSession`.
 */
internal class PGPaymentRepository(private val api: PGApiService) {

    private val poller = PGStatusPoller(api)

    /**
     * Creates (or, per `Idempotency-Key: {orderToken}_{method}`, idempotently re-fetches)
     * the payment attempt for this method. Called once per user action (launching a UPI
     * app, submitting a VPA, submitting a card, or picking a bank) rather than cached
     * client-side -- the gateway's idempotency key makes repeat calls safe.
     */
    suspend fun createAttempt(
        orderToken: String,
        method: PGPaymentMethod,
        amountMinor: Long,
        currency: String,
        customer: PGCustomerInfo?,
        metadata: Map<String, String>
    ): PGApiResult<PGPaymentAttempt> {
        val wireMethod = method.wireValue
        val request = CreatePaymentAttemptRequest(
            orderToken = orderToken,
            method = wireMethod,
            amountMinorUnits = amountMinor,
            currency = currency,
            customerName = customer?.name,
            customerEmail = customer?.email,
            customerPhone = customer?.phone,
            metadata = metadata
        )
        return safeCall { api.createPaymentAttempt(request, idempotencyKey = "${orderToken}_$wireMethod") }
    }

    suspend fun getAttempt(attemptId: String): PGApiResult<PGStatusResponse> =
        safeCall { api.getPaymentAttempt(attemptId) }

    suspend fun createUpiIntent(attemptId: String): PGApiResult<PGUpiIntentResponse> =
        safeCall { api.createUpiIntent(attemptId, PGUpiIntentRequest(attemptId)) }

    suspend fun collectUpi(attemptId: String, vpa: String): PGApiResult<PGUpiCollectResponse> =
        safeCall { api.collectUpi(attemptId, PGUpiCollectRequest(attemptId, vpa)) }

    suspend fun tokenizeCard(
        attemptId: String,
        cardNumber: String,
        expiryMonth: String,
        expiryYear: String,
        cvv: String,
        cardholderName: String
    ): PGApiResult<PGCardTokenizeResponse> = safeCall {
        api.tokenizeCard(
            attemptId,
            PGCardTokenizeRequest(attemptId, cardNumber, expiryMonth, expiryYear, cvv, cardholderName)
        )
    }

    suspend fun listBanks(): PGApiResult<List<BankDto>> = safeCall { api.listBanks().banks }

    suspend fun initiateNetBanking(
        attemptId: String,
        bankId: String
    ): PGApiResult<PGNetBankingInitiateResponse> =
        safeCall { api.initiateNetBanking(attemptId, PGNetBankingInitiateRequest(attemptId, bankId)) }

    /**
     * Polls until the attempt reaches a terminal status or gives up -- see
     * [PGStatusPoller]. A caller that times out here should surface a
     * [com.pgsdk.model.PGPaymentResult.Pending] rather than a hard failure: the payment
     * may still complete asynchronously and should be confirmed via the merchant's own
     * backend webhook, not further client polling.
     */
    suspend fun pollUntilTerminal(attemptId: String): PGApiResult<PGStatusResponse> =
        poller.pollUntilTerminal(attemptId)

    private suspend fun <T> safeCall(block: suspend () -> T): PGApiResult<T> = try {
        PGApiResult.Ok(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        PGApiResult.Err(PGErrorMapper.from(throwable))
    }
}
