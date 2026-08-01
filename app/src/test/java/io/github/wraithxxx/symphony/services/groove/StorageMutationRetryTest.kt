package io.github.wraithxxx.symphony.services.groove

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StorageMutationRetryTest {
    @Test
    fun `successful operation runs once`() = runBlocking {
        var calls = 0

        val result = StorageMutationRetry.run(
            retryDelaysMs = longArrayOf(0L, 0L),
            shouldRetry = { value -> !value },
        ) {
            calls++
            true
        }

        assertEquals(true, result)
        assertEquals(1, calls)
    }

    @Test
    fun `transient failure recovers within bounded attempts`() = runBlocking {
        var calls = 0

        val result = StorageMutationRetry.run(
            retryDelaysMs = longArrayOf(0L, 0L),
            shouldRetry = { value -> !value },
        ) {
            calls++
            calls == 3
        }

        assertEquals(true, result)
        assertEquals(3, calls)
    }

    @Test
    fun `permanent result is not retried`() = runBlocking {
        var calls = 0

        val result = StorageMutationRetry.run(
            retryDelaysMs = longArrayOf(0L, 0L),
            shouldRetry = { value -> value == "transient" },
        ) {
            calls++
            "permission-denied"
        }

        assertEquals("permission-denied", result)
        assertEquals(1, calls)
    }
}
