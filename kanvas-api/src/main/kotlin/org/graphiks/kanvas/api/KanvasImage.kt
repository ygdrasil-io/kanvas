package org.graphiks.kanvas.api

import org.graphiks.kanvas.gpu.renderer.images.GPUImageSourceDescriptor

class KanvasImage(
    val width: Int,
    val height: Int,
    val colorType: KanvasColorType,
    internal val sourceId: String,
) {
    companion object {
        fun decode(bytes: ByteArray, mimeType: String? = null): KanvasImage {
            val hint = mimeType?.let { codecHintFromMime(it) }
            return if (hint != null) {
                KanvasImage(
                    width = 0, height = 0,
                    colorType = KanvasColorType.RGBA_8888,
                    sourceId = "decoded:${hint}:${bytes.size}bytes",
                )
            } else {
                KanvasImage(
                    width = 0, height = 0,
                    colorType = KanvasColorType.RGBA_8888,
                    sourceId = "decoded:unknown:${bytes.size}bytes",
                )
            }
        }

        private fun codecHintFromMime(mime: String): String? = when (mime.lowercase()) {
            "image/png", "png" -> "png"
            "image/jpeg", "jpeg", "jpg" -> "jpeg"
            "image/webp", "webp" -> "webp"
            else -> null
        }
    }

    fun lower(): GPUImageSourceDescriptor = GPUImageSourceDescriptor(
        sourceId = sourceId,
        sourceKind = "decoded-image",
        sizeLabel = "${width}x${height}",
        colorProfileLabel = "srgb",
        provenance = "kanvas-image-decode",
    )
}

enum class KanvasColorType(val label: String) {
    RGBA_8888("rgba_8888"),
    BGRA_8888("bgra_8888"),
    ALPHA_8("alpha_8"),
    GRAY_8("gray_8"),
}
