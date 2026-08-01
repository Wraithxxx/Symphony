package io.github.wraithxxx.symphony.services.radio

internal class RadioSessionPublicationState {
    private var publishedSongId: String? = null
    private var latestGeneration = 0L

    fun beginCorePublication(generation: Long): Boolean {
        if (generation < latestGeneration) {
            return false
        }
        latestGeneration = generation
        return true
    }

    fun allowsAsyncPublication(generation: Long): Boolean {
        return generation == latestGeneration
    }

    fun shouldPublishBasicMetadata(songId: String?): Boolean {
        if (songId == null || songId == publishedSongId) {
            return false
        }
        publishedSongId = songId
        return true
    }

    fun clear() {
        publishedSongId = null
        latestGeneration = 0L
    }
}
