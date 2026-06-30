package org.graphiks.kanvas.surface

import org.graphiks.kanvas.image.Image

interface ImageEncoder {
    fun encode(pixels: ByteArray, width: Int, height: Int, metadata: Metadata): ByteArray
    data class Metadata(val format: PixelLayout)
    enum class PixelLayout { RGBA8, BGRA8 }
}

object ImageEncoderRegistry {
    private val encoders = mutableMapOf<String, ImageEncoder>()
    fun find(format: String): ImageEncoder? = encoders[format]
    fun register(format: String, encoder: ImageEncoder) { encoders[format] = encoder }
}

fun RenderResult.toPng(): ByteArray {
    val encoder = ImageEncoderRegistry.find("png")
        ?: error("No PNG encoder registered. Add :codec:png to your dependencies to enable PNG export.")
    return encoder.encode(pixels.toByteArray(), width, height, ImageEncoder.Metadata(ImageEncoder.PixelLayout.RGBA8))
}

fun RenderResult.toImage(): Image = Image(width, height, org.graphiks.kanvas.image.ColorType.RGBA_8888, "render-result")
