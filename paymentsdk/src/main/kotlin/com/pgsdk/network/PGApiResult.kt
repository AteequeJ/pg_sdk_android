package com.pgsdk.network

import com.pgsdk.model.PGError

/** Internal result wrapper distinguishing network/server failures from success. */
internal sealed class PGApiResult<out T> {
    data class Ok<T>(val value: T) : PGApiResult<T>()
    data class Err(val error: PGError) : PGApiResult<Nothing>()
}

internal inline fun <T, R> PGApiResult<T>.map(transform: (T) -> R): PGApiResult<R> = when (this) {
    is PGApiResult.Ok -> PGApiResult.Ok(transform(value))
    is PGApiResult.Err -> this
}
