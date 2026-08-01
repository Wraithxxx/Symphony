package io.github.wraithxxx.symphony.services.groove

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MediaArtworkMutationPolicyTest {
    @Test
    fun defersArtworkOnlyMutationWhileFileIsOpenByPlayer() {
        assertTrue(
            MediaArtworkMutationPolicy.shouldDefer(
                artworkMutationRequested = true,
                propertyMutationRequested = false,
                hasOpenPlayerForFile = true,
            )
        )
    }

    @Test
    fun doesNotDeferWhenNoArtworkChanged() {
        assertFalse(
            MediaArtworkMutationPolicy.shouldDefer(
                artworkMutationRequested = false,
                propertyMutationRequested = false,
                hasOpenPlayerForFile = true,
            )
        )
    }

    @Test
    fun doesNotDeferCombinedPropertyMutation() {
        assertFalse(
            MediaArtworkMutationPolicy.shouldDefer(
                artworkMutationRequested = true,
                propertyMutationRequested = true,
                hasOpenPlayerForFile = true,
            )
        )
    }

    @Test
    fun commitsSynchronouslyWhenPlayerHasNoOpenFile() {
        assertFalse(
            MediaArtworkMutationPolicy.shouldDefer(
                artworkMutationRequested = true,
                propertyMutationRequested = false,
                hasOpenPlayerForFile = false,
            )
        )
    }
}
