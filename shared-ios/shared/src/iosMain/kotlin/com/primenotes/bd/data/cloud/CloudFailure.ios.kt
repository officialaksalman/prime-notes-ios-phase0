package com.primenotes.bd.data.cloud

import io.ktor.client.engine.darwin.DarwinHttpRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.ServerResponseException
import platform.Foundation.NSURLErrorCannotConnectToHost
import platform.Foundation.NSURLErrorCannotFindHost
import platform.Foundation.NSURLErrorDNSLookupFailed
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorNetworkConnectionLost
import platform.Foundation.NSURLErrorNotConnectedToInternet
import platform.Foundation.NSURLErrorTimedOut

/**
 * The Apple classification, for the iOS target.
 *
 * Everything below is read off the exception Ktor's Darwin engine actually throws, which is
 * `DarwinHttpRequestException` — a `kotlinx.io.IOException` carrying the original `NSError` as a
 * public `origin` property. The `NSError` is therefore reachable directly; there is no need to
 * dig through a `userInfo` dictionary, and `code` is a plain property rather than a boxed
 * `NSNumber` under a string key.
 *
 * Three shapes are recognised, in this order:
 *
 *  * **a response that arrived** — `ServerResponseException` and friends. A request that got an
 *    answer, however unwelcome, is not a network failure, and reporting it as one would tell a
 *    person their network is down when the server simply disagreed with them. Checked first
 *    because a refused request is the common case and must not be mislabelled.
 *  * **Ktor's own timeout** — a plain Kotlin exception with no `NSError` at all.
 *  * **the `NSURLError` codes**, which are the same situations the JVM names with
 *    `UnknownHostException` and `SocketTimeoutException`.
 *
 * An `NSURLError` that is none of the listed codes still counts as unreachable rather than as
 * "not a network problem": the request demonstrably did not complete, and saying so is the safer
 * of the two mistakes.
 */
actual fun Throwable.networkFailureKind(): FailureKind {
    if (this is ServerResponseException || this is ResponseException) return FailureKind.None

    if (this is HttpRequestTimeoutException) return FailureKind.TimedOut

    val error = (this as? DarwinHttpRequestException)?.origin ?: return FailureKind.None

    return when (error.code.toLong()) {
        NSURLErrorCannotFindHost.toLong(), NSURLErrorDNSLookupFailed.toLong() ->
            FailureKind.HostNotResolved

        NSURLErrorTimedOut.toLong() -> FailureKind.TimedOut

        NSURLErrorNotConnectedToInternet.toLong(),
        NSURLErrorNetworkConnectionLost.toLong(),
        NSURLErrorCannotConnectToHost.toLong() -> FailureKind.Unreachable

        else -> FailureKind.Unreachable
    }
}
