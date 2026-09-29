package com.pgsdk.core

/** A gateway request, as seen by a [PGDebugInterceptor]. */
data class PGDebugRequest(
    val method: String,
    val path: String,
    val bodyJson: String?
)

/** A canned response a [PGDebugInterceptor] returns to short-circuit a gateway request. */
data class PGDebugResponse(
    val statusCode: Int,
    val bodyJson: String,
    val headers: Map<String, String> = emptyMap()
)

/**
 * Intercepts gateway requests in-process during development, before any network I/O --
 * e.g. a sample/demo app's in-memory mock backend, so it can exercise the full checkout
 * flow (docs/API.md) without a real gateway. Return `null` to let the request fall
 * through to the next interceptor, or to the real network if none match.
 *
 * Configured via [PGConfig.debugInterceptors]; the SDK does not expose OkHttp types
 * here so host apps never need OkHttp on their compile classpath just to depend on this
 * SDK -- see [PGConfig] for how this is bridged internally.
 */
fun interface PGDebugInterceptor {
    fun intercept(request: PGDebugRequest): PGDebugResponse?
}
