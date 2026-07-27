package io.github.zyrouge.symphony.services.radio

internal object PlaybackProgressPolicy {
    sealed class Decision {
        object Ignore : Decision()
        object Clear : Decision()
        data class Save(val positionMs: Long) : Decision()
    }

    fun decide(
        enabled: Boolean,
        minimumDurationMs: Long,
        durationMs: Long,
        positionMs: Long,
    ): Decision {
        if (!enabled || durationMs < minimumDurationMs || durationMs <= 0L) {
            return Decision.Ignore
        }
        val clampedPosition = positionMs.coerceIn(0L, durationMs)
        if (clampedPosition < MINIMUM_POSITION_MS) {
            return Decision.Clear
        }
        if (durationMs - clampedPosition <= completionWindow(durationMs)) {
            return Decision.Clear
        }
        return Decision.Save(clampedPosition)
    }

    fun restorablePosition(
        enabled: Boolean,
        minimumDurationMs: Long,
        durationMs: Long,
        positionMs: Long,
    ): Long? = when (
        val decision = decide(
            enabled = enabled,
            minimumDurationMs = minimumDurationMs,
            durationMs = durationMs,
            positionMs = positionMs,
        )
    ) {
        is Decision.Save -> decision.positionMs
        Decision.Clear,
        Decision.Ignore,
        -> null
    }

    fun matchesFile(
        storedPath: String,
        storedDurationMs: Long,
        storedDateModified: Long,
        storedSize: Long,
        currentPath: String,
        currentDurationMs: Long,
        currentDateModified: Long,
        currentSize: Long,
    ) = storedPath == currentPath &&
            storedDurationMs == currentDurationMs &&
            storedDateModified == currentDateModified &&
            storedSize == currentSize

    private fun completionWindow(durationMs: Long) =
        maxOf(MINIMUM_COMPLETION_WINDOW_MS, durationMs / 100L)

    internal const val MINIMUM_POSITION_MS = 10_000L
    internal const val MINIMUM_COMPLETION_WINDOW_MS = 30_000L
}
