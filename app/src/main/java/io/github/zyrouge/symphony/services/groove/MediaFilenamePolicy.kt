package io.github.zyrouge.symphony.services.groove

import io.github.zyrouge.symphony.utils.SimplePath

object MediaFilenamePolicy {
    sealed class Result {
        data class Valid(val displayName: String) : Result()
        object Blank : Result()
        object InvalidCharacters : Result()
        object Reserved : Result()
        object Unchanged : Result()
    }

    fun buildDisplayName(currentFilename: String, requestedBaseName: String): Result {
        val baseName = requestedBaseName.trim()
        if (baseName.isEmpty()) {
            return Result.Blank
        }
        if (baseName == "." || baseName == "..") {
            return Result.Reserved
        }
        if (baseName.any { it == '/' || it == '\\' || it.isISOControl() }) {
            return Result.InvalidCharacters
        }
        val extension = SimplePath(currentFilename).extension
        val displayName = when {
            extension.isEmpty() -> baseName
            else -> "$baseName.$extension"
        }
        return when {
            displayName == currentFilename -> Result.Unchanged
            else -> Result.Valid(displayName)
        }
    }
}
