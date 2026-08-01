package io.github.wraithxxx.symphony.services.groove

internal object MediaArtworkMutationPolicy {
    fun shouldDefer(
        artworkMutationRequested: Boolean,
        propertyMutationRequested: Boolean,
        hasOpenPlayerForFile: Boolean,
    ): Boolean = artworkMutationRequested &&
        !propertyMutationRequested &&
        hasOpenPlayerForFile
}
