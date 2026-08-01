package io.github.wraithxxx.symphony.services.radio

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

    @Test
    fun `new core publication invalidates older asynchronous work`() {
        val state = RadioSessionPublicationState()

        assertTrue(state.beginCorePublication(1L))
        assertTrue(state.allowsAsyncPublication(1L))
        assertTrue(state.beginCorePublication(2L))

        assertFalse(state.allowsAsyncPublication(1L))
        assertTrue(state.allowsAsyncPublication(2L))
    }

    @Test
    fun `older core publication cannot overwrite a newer state`() {
        val state = RadioSessionPublicationState()

        assertTrue(state.beginCorePublication(2L))
        assertFalse(state.beginCorePublication(1L))
        assertTrue(state.allowsAsyncPublication(2L))
    }
}
