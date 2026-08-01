package io.github.wraithxxx.symphony.services.groove

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MediaMetadataEditPolicyTest {
    @Test
    fun splitsSemicolonSeparatedValues() {
        assertArrayEquals(
            arrayOf("Artist one", "Artist two"),
            MediaMetadataEditPolicy.splitMultiValue("Artist one; Artist two"),
        )
    }

    @Test
    fun splitsLinesAndDropsBlankValues() {
        assertArrayEquals(
            arrayOf("Rock", "Spoken word"),
            MediaMetadataEditPolicy.splitMultiValue(" Rock\n\nSpoken word; "),
        )
    }

    @Test
    fun preservesCommasInsideOneValue() {
        assertArrayEquals(
            arrayOf("Earth, Wind & Fire"),
            MediaMetadataEditPolicy.splitMultiValue("Earth, Wind & Fire"),
        )
    }

    @Test
    fun acceptsBlankAndNonNegativeNumbers() {
        assertTrue(MediaMetadataEditPolicy.isOptionalNonNegativeInteger(""))
        assertTrue(MediaMetadataEditPolicy.isOptionalNonNegativeInteger("0"))
        assertTrue(MediaMetadataEditPolicy.isOptionalNonNegativeInteger("12"))
    }

    @Test
    fun rejectsNegativeAndNonNumericNumbers() {
        assertFalse(MediaMetadataEditPolicy.isOptionalNonNegativeInteger("-1"))
        assertFalse(MediaMetadataEditPolicy.isOptionalNonNegativeInteger("1/12"))
        assertFalse(MediaMetadataEditPolicy.isOptionalNonNegativeInteger("first"))
    }

    @Test
    fun detectsActualPropertyChangesWithoutTreatingMissingAsDifferentFromEmpty() {
        assertFalse(MediaMetadataEditPolicy.propertyValuesDiffer(null, emptyArray()))
        assertFalse(
            MediaMetadataEditPolicy.propertyValuesDiffer(
                arrayOf("Artist one", "Artist two"),
                arrayOf("Artist one", "Artist two"),
            )
        )
        assertTrue(
            MediaMetadataEditPolicy.propertyValuesDiffer(
                arrayOf("Artist one"),
                arrayOf("Artist two"),
            )
        )
    }
}
