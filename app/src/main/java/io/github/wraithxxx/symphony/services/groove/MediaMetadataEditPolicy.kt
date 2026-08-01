package io.github.wraithxxx.symphony.services.groove

object MediaMetadataEditPolicy {
    fun splitMultiValue(rawValue: String): Array<String> = rawValue
        .split(';', '\n')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toTypedArray()

    fun isOptionalNonNegativeInteger(rawValue: String): Boolean =
        rawValue.isBlank() || (rawValue.toIntOrNull()?.let { it >= 0 } == true)

    fun propertyValuesDiffer(original: Array<String>?, updated: Array<String>?): Boolean =
        original?.toList().orEmpty() != updated?.toList().orEmpty()
}
