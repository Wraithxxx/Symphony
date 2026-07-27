package io.github.zyrouge.symphony.services.radio

internal object RadioQueueNavigation {
    fun previousIndex(currentIndex: Int, queueSize: Int): Int? {
        if (queueSize <= 1 || currentIndex !in 0 until queueSize) {
            return null
        }
        return if (currentIndex == 0) queueSize - 1 else currentIndex - 1
    }

    fun nextIndex(currentIndex: Int, queueSize: Int): Int? {
        if (queueSize <= 1 || currentIndex !in 0 until queueSize) {
            return null
        }
        return if (currentIndex == queueSize - 1) 0 else currentIndex + 1
    }
}
