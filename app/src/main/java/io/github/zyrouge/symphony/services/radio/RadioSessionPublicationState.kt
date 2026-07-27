package io.github.zyrouge.symphony.services.radio

internal class RadioSessionPublicationState {
    private var publishedSongId: String? = null

    fun shouldPublishBasicMetadata(songId: String?): Boolean {
        if (songId == null || songId == publishedSongId) {
            return false
        }
        publishedSongId = songId
        return true
    }

    fun clear() {
        publishedSongId = null
    }
}
