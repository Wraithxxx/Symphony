package io.github.wraithxxx.symphony.services.groove

import kotlinx.coroutines.delay

internal object StorageMutationRetry {
    private val delaysMs = longArrayOf(120L, 350L)

    suspend fun <T> run(
        retryDelaysMs: LongArray = delaysMs,
        shouldRetry: (T) -> Boolean,
        operation: suspend (attempt: Int) -> T,
    ): T {
        var result = operation(1)
        retryDelaysMs.forEachIndexed { index, delayMs ->
            if (!shouldRetry(result)) {
                return result
            }
            delay(delayMs)
            result = operation(index + 2)
        }
        return result
    }
}
