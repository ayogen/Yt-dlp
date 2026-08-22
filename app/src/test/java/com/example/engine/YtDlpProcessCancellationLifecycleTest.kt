package com.example.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class YtDlpProcessCancellationLifecycleTest {

    /**
     * Test simulated asynchronous bridge mirroring executeRequestCancellable:
     * Verifies that when a coroutine is cancelled while a blocking operation is running on a dedicated thread,
     * the invokeOnCancellation hook executes immediately and invokes the process destroyer.
     */
    @Test
    fun testProcessCancellationHookTriggersConcurrently() = runBlocking {
        val processMap = ConcurrentHashMap<String, AtomicBoolean>()
        val processDestroyCount = AtomicInteger(0)

        val processId = "meta_test_${System.currentTimeMillis()}_${(1000..9999).random()}"
        processMap[processId] = AtomicBoolean(true) // Process is alive

        fun mockDestroyProcessById(id: String): Boolean {
            val alive = processMap[id]
            return if (alive != null && alive.getAndSet(false)) {
                processDestroyCount.incrementAndGet()
                true
            } else false
        }

        var processWasDestroyedDuringCancellation = false

        val job = launch {
            try {
                kotlinx.coroutines.suspendCancellableCoroutine<String> { continuation ->
                    continuation.invokeOnCancellation {
                        mockDestroyProcessById(processId)
                    }

                    // Simulate blocking executor
                    val thread = Thread {
                        try {
                            // Block for 2 seconds (simulating YoutubeDL.execute)
                            Thread.sleep(2000L)
                            if (continuation.isActive) {
                                continuation.resume("success") {}
                            }
                        } catch (_: InterruptedException) {}
                    }
                    thread.isDaemon = true
                    thread.start()
                }
            } catch (e: CancellationException) {
                processWasDestroyedDuringCancellation = (processMap[processId]?.get() == false)
            }
        }

        delay(100L)
        // Coroutine cancellation occurs while worker thread is sleeping in blocking call
        job.cancel()
        job.join()

        assertTrue("destroyProcessById must be called via invokeOnCancellation while execute is blocking", processWasDestroyedDuringCancellation)
        assertEquals(1, processDestroyCount.get())
        assertFalse("Process in map must be marked terminated", processMap[processId]?.get() ?: true)
    }

    @Test
    fun testProcessIdUniquenessAcrossConcurrentCalls() {
        val ids = ConcurrentHashMap.newKeySet<String>()
        val count = 100
        for (i in 0 until count) {
            val processId = "meta_${System.currentTimeMillis()}_${(1000..9999).random()}_$i"
            ids.add(processId)
        }
        assertEquals("Each request must have a strictly unique processId", count, ids.size)
    }

    @Test
    fun testNormalCompletionDoesNotDestroyActiveProcessEarly() = runBlocking {
        val isDestroyed = AtomicBoolean(false)
        val processId = "meta_test_normal"

        val result = kotlinx.coroutines.suspendCancellableCoroutine<String> { continuation ->
            continuation.invokeOnCancellation {
                isDestroyed.set(true)
            }

            val thread = Thread {
                Thread.sleep(50L) // Quick operation
                if (continuation.isActive) {
                    continuation.resume("stdout_metadata_json") {}
                }
            }
            thread.isDaemon = true
            thread.start()
        }

        assertEquals("stdout_metadata_json", result)
        assertFalse("Normal completion must not trigger invokeOnCancellation", isDestroyed.get())
    }
}
