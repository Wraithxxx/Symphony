package io.github.zyrouge.metaphony

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AudioArtworkTest {
    @Test
    fun `maps Android 9 compatible still image formats`() {
        val cases = mapOf(
            "image/jpeg" to AudioArtwork.Format.Jpeg,
            "image/png" to AudioArtwork.Format.Png,
            "image/gif" to AudioArtwork.Format.Gif,
            "image/bmp" to AudioArtwork.Format.Bmp,
            "image/webp" to AudioArtwork.Format.Webp,
            "image/heif" to AudioArtwork.Format.Heif,
            "image/heic" to AudioArtwork.Format.Heic,
        )

        cases.forEach { (mimeType, expected) ->
            assertEquals(expected, AudioArtwork.Format.fromMimeType(mimeType))
        }
    }

    @Test
    fun `normalizes aliases case and MIME parameters`() {
        assertEquals(
            AudioArtwork.Format.Jpeg,
            AudioArtwork.Format.fromMimeType(" IMAGE/JPG; charset=binary "),
        )
        assertEquals(
            AudioArtwork.Format.Heif,
            AudioArtwork.Format.fromMimeType("image/heif-sequence"),
        )
        assertEquals(
            AudioArtwork.Format.Heic,
            AudioArtwork.Format.fromMimeType("HEIC"),
        )
    }

    @Test
    fun `does not treat WebM as still artwork`() {
        assertEquals(
            AudioArtwork.Format.Unknown,
            AudioArtwork.Format.fromMimeType("video/webm"),
        )
    }
}
