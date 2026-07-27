package io.github.zyrouge.symphony.ui.components

internal object SongSelection {
    fun toggle(selected: Set<String>, songId: String): Set<String> = when {
        songId in selected -> selected - songId
        else -> selected + songId
    }

    fun selectAll(availableSongIds: Collection<String>): Set<String> =
        availableSongIds.toSet()

    fun retainAvailable(
        selected: Set<String>,
        availableSongIds: Collection<String>,
    ): Set<String> = selected intersect availableSongIds.toSet()
}
