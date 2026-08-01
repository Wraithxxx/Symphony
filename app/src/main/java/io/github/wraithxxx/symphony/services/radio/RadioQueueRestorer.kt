package io.github.wraithxxx.symphony.services.radio

internal object RadioQueueRestorer {
    fun filter(
        previous: RadioQueue.Serialized,
        availableSongIds: Set<String>,
    ): RadioQueue.Serialized? {
        val originalQueue = previous.originalQueue.filter(availableSongIds::contains)
        val currentQueue = previous.currentQueue.filter(availableSongIds::contains)
        if (originalQueue.isEmpty() || currentQueue.isEmpty()) {
            return null
        }

        val previousCurrentId = previous.currentQueue.getOrNull(previous.currentSongIndex)
        val restoredIndex = previousCurrentId?.let(currentQueue::indexOf) ?: -1
        val currentSongSurvived = restoredIndex >= 0

        return RadioQueue.Serialized(
            currentSongIndex = when {
                currentSongSurvived -> restoredIndex
                else -> 0
            },
            playedDuration = when {
                currentSongSurvived -> previous.playedDuration.coerceAtLeast(0L)
                else -> 0L
            },
            originalQueue = originalQueue,
            currentQueue = currentQueue,
            shuffled = previous.shuffled,
        )
    }
}
