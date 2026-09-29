package com.pgsdk.network

import com.pgsdk.model.PGError
import com.pgsdk.model.PGErrorCode
import com.pgsdk.network.dto.PGAttemptStatus
import com.pgsdk.network.dto.PGStatusResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * Polls `GET /v1/payment_attempts/{id}` with capped exponential backoff until a terminal
 * status (succeeded/failed/cancelled) or [maxAttempts] is exhausted. Mirrors the iOS SDK's
 * PGStatusPoller (starts at 1.5s, caps at 8s, up to 20 attempts). Cancellation-safe:
 * cancelling the calling coroutine aborts the poll immediately via [delay].
 */
internal class PGStatusPoller(private val api: PGApiService) {

    suspend fun pollUntilTerminal(
        attemptId: String,
        maxAttempts: Int = 20,
        initialDelayMillis: Long = 1_500L,
        maxDelayMillis: Long = 8_000L
    ): PGApiResult<PGStatusResponse> {
        var attempt = 0
        var delayMillis = initialDelayMillis
        var lastResult: PGApiResult<PGStatusResponse> = PGApiResult.Err(
            PGError(PGErrorCode.TIMEOUT, "Status check did not complete in time.", isRetryable = true)
        )

        while (attempt < maxAttempts) {
            val result = safeCall { api.getPaymentAttempt(attemptId) }
            lastResult = result
            val status = (result as? PGApiResult.Ok)?.value?.status
            if (status != null && status != PGAttemptStatus.CREATED && status != PGAttemptStatus.PENDING) {
                return result
            }
            attempt++
            if (attempt < maxAttempts) {
                delay(delayMillis)
                delayMillis = min((delayMillis * 1.6).toLong(), maxDelayMillis)
            }
        }
        return lastResult
    }

    private suspend fun <T> safeCall(block: suspend () -> T): PGApiResult<T> = try {
        PGApiResult.Ok(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        PGApiResult.Err(PGErrorMapper.from(throwable))
    }
}
