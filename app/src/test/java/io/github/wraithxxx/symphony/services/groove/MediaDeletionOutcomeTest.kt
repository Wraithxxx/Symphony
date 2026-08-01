package io.github.wraithxxx.symphony.services.groove

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.FileNotFoundException

class MediaDeletionOutcomeTest {
    @Test
    fun `recognizes a direct missing-file failure`() {
        assertTrue(
            MediaDeletionOutcome.isMissingDocument(FileNotFoundException("Missing file")),
        )
    }

    @Test
    fun `recognizes a missing-file failure in the cause chain`() {
        assertTrue(
            MediaDeletionOutcome.isMissingDocument(
                IllegalArgumentException("Provider query failed", FileNotFoundException()),
            ),
        )
    }

    @Test
    fun `recognizes the wrapped DocumentsProvider failure seen on device`() {
        assertTrue(
            MediaDeletionOutcome.isMissingDocument(
                IllegalArgumentException(
                    "Failed to determine child relationship: " +
                        "java.io.FileNotFoundException: Missing file for content://media/track",
                ),
            ),
        )
    }

    @Test
    fun `does not classify an unrelated provider error as a missing file`() {
        assertFalse(
            MediaDeletionOutcome.isMissingDocument(
                IllegalArgumentException("Unsupported document URI"),
            ),
        )
    }
}
