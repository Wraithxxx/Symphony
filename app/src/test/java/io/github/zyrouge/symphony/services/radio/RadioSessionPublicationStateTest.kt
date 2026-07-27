package io.github.zyrouge.symphony.services.radio

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RadioSessionPublicationStateTest {
    @Test
    fun `first restored song publishes basic metadata immediately`() {
        val state = RadioSessionPublicationState()

        assertTrue(state.shouldPublishBasicMetadata("song-a"))
    }

    @Test
    fun `state-only updates do not remove already loaded artwork`() {
        val state = RadioSessionPublicationState()
        state.shouldPublishBasicMetadata("song-a")

        assertFalse(state.shouldPublishBasicMetadata("song-a"))
    }

    @Test
    fun `track change publishes new basic metadata`() {
        val state = RadioSessionPublicationState()
        state.shouldPublishBasicMetadata("song-a")

        assertTrue(state.shouldPublishBasicMetadata("song-b"))
    }

    @Test
    fun `session recreation republishes current metadata`() {
        val state = RadioSessionPublicationState()
        state.shouldPublishBasicMetadata("song-a")

        state.clear()

        assertTrue(state.shouldPublishBasicMetadata("song-a"))
    }

    @Test
    fun `empty snapshot never publishes metadata`() {
        val state = RadioSessionPublicationState()

        assertFalse(state.shouldPublishBasicMetadata(null))
    }
}
