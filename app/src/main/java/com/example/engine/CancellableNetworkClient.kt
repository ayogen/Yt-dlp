package com.example.engine

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reusable coroutine-aware OkHttp executor.
 * Guarantees that coroutine cancellation or withTimeout triggers Call.cancel() immediately.
 */
object CancellableNetworkClient {

    /**
     * Executes an OkHttp request asynchronously within a cancellable coroutine.
     * When the calling coroutine is cancelled (e.g. user cancelled or withTimeout exceeded),
     * [Call.cancel] is invoked immediately, releasing sockets and worker threads.
     */
    suspend fun executeCancellable(client: OkHttpClient, request: Request): Response {
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)

            continuation.invokeOnCancellation {
                try {
                    call.cancel()
                } catch (_: Throwable) {}
            }

            call.enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isActive) {
                        continuation.resume(response)
                    } else {
                        response.close()
                    }
                }

                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }
            })
        }
    }
}
