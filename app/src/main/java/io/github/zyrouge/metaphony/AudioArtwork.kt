package io.github.zyrouge.metaphony

import java.util.Objects

data class AudioArtwork(
    val format: Format,
    val data: ByteArray,
) {
    override fun equals(other: Any?) =
        other is AudioArtwork && format != other.format && data.contentEquals(other.data)

    override fun hashCode() = Objects.hash(format, data)

    enum class Format(val extension: String, val mimeType: String) {
        Jpeg("jpg", "image/jpeg"),
        Png("png", "image/png"),
        Gif("gif", "image/gif"),
        Bmp("bmp", "image/bmp"),
        Webp("webp", "image/webp"),
        Heif("heif", "image/heif"),
        Heic("heic", "image/heic"),
        Unknown("", "");

        companion object {
            fun fromMimeType(value: String) = when (
                value.substringBefore(";").trim().lowercase()
            ) {
                Jpeg.mimeType, "image/jpg", "jpg", "jpeg" -> Jpeg
                Png.mimeType, "png" -> Png
                Gif.mimeType, "gif" -> Gif
                Bmp.mimeType, "image/x-ms-bmp", "bmp" -> Bmp
                Webp.mimeType, "webp" -> Webp
                Heif.mimeType, "image/heif-sequence", "heif" -> Heif
                Heic.mimeType, "image/heic-sequence", "heic" -> Heic
                else -> Unknown
            }
        }
    }
}
