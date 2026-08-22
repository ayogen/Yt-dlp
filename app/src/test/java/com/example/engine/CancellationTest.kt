package com.example.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class CancellationTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun testCancellableNetworkClientCancelsOnTimeout() = runBlocking {
        // Enqueue delayed response
        server.enqueue(
            MockResponse()
                .setBody("Delayed Payload")
                .setBodyDelay(3, TimeUnit.SECONDS)
        )

        val request = Request.Builder()
            .url(server.url("/delayed"))
            .build()

        val start = System.currentTimeMillis()
        val result = withTimeoutOrNull(300L) {
            CancellableNetworkClient.executeCancellable(client, request)
        }
        val elapsed = System.currentTimeMillis() - start

        assertNull("Request should have returned null due to timeout cancellation", result)
        assertTrue("Timeout cancellation should happen promptly (< 1500ms), took $elapsed ms", elapsed < 1500L)
    }

    @Test
    fun testCancellableNetworkClientCancelsOnJobCancellation() = runBlocking {
        server.enqueue(
            MockResponse()
                .setBody("Delayed Payload")
                .setBodyDelay(3, TimeUnit.SECONDS)
        )

        val request = Request.Builder()
            .url(server.url("/delayed2"))
            .build()

        var caughtCancellation = false
        val job = launch {
            try {
                CancellableNetworkClient.executeCancellable(client, request)
            } catch (e: CancellationException) {
                caughtCancellation = true
            }
        }

        delay(100L)
        job.cancel()
        job.join()

        assertTrue("Coroutine job cancellation should raise CancellationException and cancel OkHttp call", caughtCancellation)
    }
}
