package io.github.zyrouge.symphony.services.groove

object MediaMetadataEditPolicy {
    fun splitMultiValue(rawValue: String): Array<String> = rawValue
        .split(';', '\n')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toTypedArray()

    fun isOptionalNonNegativeInteger(rawValue: String): Boolean =
        rawValue.isBlank() || (rawValue.toIntOrNull()?.let { it >= 0 } == true)
}
