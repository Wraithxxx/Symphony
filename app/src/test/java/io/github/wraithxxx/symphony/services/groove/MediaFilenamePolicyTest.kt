package io.github.wraithxxx.symphony.services.groove

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MediaFilenamePolicyTest {
    @Test
    fun preservesOriginalExtension() {
        assertEquals(
            MediaFilenamePolicy.Result.Valid("Renamed episode.opus"),
            MediaFilenamePolicy.buildDisplayName("Old episode.opus", "Renamed episode"),
        )
    }

    @Test
    fun doesNotTreatDotsInRequestedBaseAsFormatConversion() {
        assertEquals(
            MediaFilenamePolicy.Result.Valid("Episode.final.mp3"),
            MediaFilenamePolicy.buildDisplayName("Episode.mp3", "Episode.final"),
        )
    }

    @Test
    fun supportsExtensionlessFiles() {
        assertEquals(
            MediaFilenamePolicy.Result.Valid("Renamed"),
            MediaFilenamePolicy.buildDisplayName("Original", "Renamed"),
        )
    }

    @Test
    fun rejectsPathSeparatorsAndReservedNames() {
        assertTrue(
            MediaFilenamePolicy.buildDisplayName("Track.flac", "../Track") is
                    MediaFilenamePolicy.Result.InvalidCharacters,
        )
        assertTrue(
            MediaFilenamePolicy.buildDisplayName("Track.flac", "..") is
                    MediaFilenamePolicy.Result.Reserved,
        )
    }

    @Test
    fun trimsAndDetectsAnUnchangedFilename() {
        assertTrue(
            MediaFilenamePolicy.buildDisplayName("Track.m4a", " Track ") is
                    MediaFilenamePolicy.Result.Unchanged,
        )
    }

    @Test
    fun rejectsControlCharacters() {
        assertTrue(
            MediaFilenamePolicy.buildDisplayName("Track.mp3", "Track\nrenamed") is
                    MediaFilenamePolicy.Result.InvalidCharacters,
        )
    }
}
