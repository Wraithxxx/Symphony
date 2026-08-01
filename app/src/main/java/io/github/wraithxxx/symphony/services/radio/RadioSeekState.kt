package io.github.wraithxxx.symphony.services.radio

internal class RadioSeekState {
    data class Command(val position: Long)

    private var activeTarget: Long? = null
    private var pendingTarget: Long? = null

    @Synchronized
    fun request(position: Long, duration: Long): Command? {
        val target = position.coerceIn(0L, duration.coerceAtLeast(0L))
        return when {
            activeTarget == null -> {
                activeTarget = target
                Command(target)
            }

            else -> {
                pendingTarget = target
                null
            }
        }
    }

    @Synchronized
    fun onSeekComplete(): Command? {
        if (activeTarget == null) {
            return null
        }
        val completedTarget = activeTarget
        val nextTarget = pendingTarget
        pendingTarget = null
        return when {
            nextTarget != null && nextTarget != completedTarget -> {
                activeTarget = nextTarget
                Command(nextTarget)
            }

            else -> {
                activeTarget = null
                null
            }
        }
    }

    @Synchronized
    fun positionForReporting(actualPosition: Long): Long {
        return pendingTarget ?: activeTarget ?: actualPosition
    }

    @Synchronized
    fun isSeeking(): Boolean = activeTarget != null

    @Synchronized
    fun reset() {
        activeTarget = null
        pendingTarget = null
    }
}
