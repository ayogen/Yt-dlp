package com.example.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object HttpCoroutineUtils {

    /**
     * Executes an OkHttp request asynchronously and cancellably in a Kotlin coroutine.
     * If the calling coroutine or its withTimeoutOrNull scope is cancelled, call.cancel()
     * is instantly invoked, aborting the active socket, TCP handshake, and TLS negotiation.
     */
    suspend fun OkHttpClient.executeAsync(request: Request): Response =
        suspendCancellableCoroutine { cont ->
            val call = newCall(request)
            cont.invokeOnCancellation {
                try {
                    call.cancel()
                } catch (_: Throwable) {}
            }
            call.enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    if (cont.isActive) {
                        cont.resume(response)
                    } else {
                        response.close()
                    }
                }

                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) {
                        cont.resumeWithException(e)
                    }
                }
            })
        }

    /**
     * Fetches the response body as a String asynchronously, safely closing the response.
     */
    suspend fun OkHttpClient.fetchStringAsync(request: Request): String? {
        return try {
            val response = executeAsync(request)
            response.use { resp ->
                if (resp.isSuccessful) {
                    resp.body?.string()
                } else {
                    null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.d("HttpCoroutineUtils", "fetchStringAsync failed for ${request.url}: ${e.message}")
            null
        }
    }
}
