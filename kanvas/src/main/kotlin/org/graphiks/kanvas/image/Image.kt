package org.graphiks.kanvas.image

enum class ColorType { RGBA_8888, BGRA_8888, ALPHA_8, GRAY_8 }

data class Image(
    val width: Int,
    val height: Int,
    val colorType: ColorType = ColorType.RGBA_8888,
    val sourceId: String,
) {
    companion object {
        fun decode(bytes: ByteArray, mimeType: String? = null): Image =
            Image(0, 0, ColorType.RGBA_8888, "decode-placeholder:${bytes.size}")
    }
}
