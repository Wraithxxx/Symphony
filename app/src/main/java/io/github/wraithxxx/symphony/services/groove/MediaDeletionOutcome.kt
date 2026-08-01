package io.github.wraithxxx.symphony.services.groove

import java.io.FileNotFoundException

/**
 * Interprets storage-provider failures without depending on a provider's exception wrapping.
 *
 * DocumentsProvider calls may transport a FileNotFoundException as the message of an
 * IllegalArgumentException instead of preserving it in the throwable cause chain.
 */
internal object MediaDeletionOutcome {
    fun isMissingDocument(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is FileNotFoundException) {
                return true
            }
            val message = current.message.orEmpty()
            if (
                message.contains("java.io.FileNotFoundException:") &&
                message.contains("Missing file for")
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
