package com.pgsdk.network

import com.pgsdk.model.PGError
import com.pgsdk.model.PGErrorCode
import com.pgsdk.network.dto.ApiErrorBody
import com.pgsdk.util.PGLogger
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/** Maps low-level exceptions from the network layer into stable, public [PGError]s. */
internal object PGErrorMapper {

    private val json = Json { ignoreUnknownKeys = true }

    fun from(throwable: Throwable): PGError = when (throwable) {
        is SocketTimeoutException -> PGError(
            code = PGErrorCode.TIMEOUT,
            message = "The request timed out. Please check your connection and try again.",
            isRetryable = true
        )

        is HttpException -> fromHttpException(throwable)

        is IOException -> PGError(
            code = PGErrorCode.NETWORK_ERROR,
            message = "Could not reach the payment server. Please check your connection.",
            isRetryable = true
        )

        else -> {
            PGLogger.e("Unexpected error", throwable)
            PGError(
                code = PGErrorCode.UNKNOWN,
                message = "Something went wrong. Please try again.",
                isRetryable = true
            )
        }
    }

    private fun fromHttpException(exception: HttpException): PGError {
        val body = runCatching {
            exception.response()?.errorBody()?.string()
        }.getOrNull()

        val parsed = body?.let { raw ->
            runCatching { json.decodeFromString(ApiErrorBody.serializer(), raw) }.getOrNull()
        }

        val isServerError = exception.code() >= 500
        return PGError(
            code = parsed?.code ?: PGErrorCode.SERVER_ERROR,
            message = parsed?.message ?: "The payment server returned an error (${exception.code()}).",
            isRetryable = isServerError
        )
    }
}
