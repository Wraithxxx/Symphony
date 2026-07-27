package io.github.zyrouge.symphony.services.radio

internal object RadioQueueLibraryReconciler {
    data class Result(
        val originalQueue: List<String>,
        val currentQueue: List<String>,
        val currentSongIndex: Int,
    )

    fun reconcile(
        originalQueue: List<String>,
        currentQueue: List<String>,
        currentSongIndex: Int,
        librarySongIds: Set<String>,
    ): Result {
        val currentId = currentQueue.getOrNull(currentSongIndex)
        val retainedIds = when (currentId) {
            null -> librarySongIds
            else -> librarySongIds + currentId
        }
        val nextOriginalQueue = originalQueue.filter { it in retainedIds }
        val nextCurrentQueue = currentQueue.filter { it in retainedIds }
        return Result(
            originalQueue = nextOriginalQueue,
            currentQueue = nextCurrentQueue,
            currentSongIndex = currentId?.let(nextCurrentQueue::indexOf) ?: -1,
        )
    }
}
