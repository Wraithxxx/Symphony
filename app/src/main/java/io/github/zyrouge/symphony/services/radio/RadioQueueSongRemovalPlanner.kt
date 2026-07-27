package io.github.zyrouge.symphony.services.radio

internal object RadioQueueSongRemovalPlanner {
    data class Result(
        val originalQueue: List<String>,
        val currentQueue: List<String>,
        val currentSongIndex: Int,
        val removedCurrentSong: Boolean,
        val replacementIndex: Int,
    )

    fun remove(
        songId: String,
        originalQueue: List<String>,
        currentQueue: List<String>,
        currentSongIndex: Int,
    ) = removeAll(
        songIds = setOf(songId),
        originalQueue = originalQueue,
        currentQueue = currentQueue,
        currentSongIndex = currentSongIndex,
    )

    fun removeAll(
        songIds: Set<String>,
        originalQueue: List<String>,
        currentQueue: List<String>,
        currentSongIndex: Int,
    ): Result {
        val currentId = currentQueue.getOrNull(currentSongIndex)
        val removedCurrentSong = currentId in songIds
        val nextOriginalQueue = originalQueue.filterNot { it in songIds }
        val nextCurrentQueue = currentQueue.filterNot { it in songIds }
        val nextIndex = when {
            removedCurrentSong -> currentSongIndex.coerceAtMost(nextCurrentQueue.lastIndex)
            currentId == null -> -1
            else -> nextCurrentQueue.indexOf(currentId)
        }
        return Result(
            originalQueue = nextOriginalQueue,
            currentQueue = nextCurrentQueue,
            currentSongIndex = nextIndex,
            removedCurrentSong = removedCurrentSong,
            replacementIndex = if (removedCurrentSong) nextIndex else -1,
        )
    }
}
