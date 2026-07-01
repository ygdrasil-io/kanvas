package org.graphiks.kanvas.surface

import org.graphiks.kanvas.image.Image

/**
 * Encodes raw pixel data into a compressed image format such as PNG.
 *
 * Implementations are registered via [ImageEncoderRegistry] and looked up by
 * format name at the call site.
 */
interface ImageEncoder {
    /**
     * Encode raw pixels into the target format.
     * @param pixels  flat row-major pixel data (see [PixelLayout] for channel order)
     * @param width   image width in pixels
     * @param height  image height in pixels
     * @param metadata decoder hints such as pixel layout
     * @return the encoded image as a byte array
     */
    fun encode(pixels: ByteArray, width: Int, height: Int, metadata: Metadata): ByteArray

    /**
     * Metadata passed to [encode] describing the pixel layout.
     * @property format the channel order of the supplied pixel data
     */
    data class Metadata(val format: PixelLayout)

    /**
     * Channel order of the raw pixel buffer.
     * - [RGBA8] — red, green, blue, alpha, 8 bits per channel.
     * - [BGRA8] — blue, green, red, alpha, 8 bits per channel.
     */
    enum class PixelLayout { RGBA8, BGRA8 }
}

/**
 * Global registry of [ImageEncoder] instances keyed by format name (e.g. "png").
 *
 * Encoders must be registered before calling [RenderResult.toPng].
 */
object ImageEncoderRegistry {
    private val encoders = mutableMapOf<String, ImageEncoder>()

    /**
     * Look up a registered encoder by format name.
     * @return the encoder, or null if no encoder has been registered for [format]
     */
    fun find(format: String): ImageEncoder? = encoders[format]

    /**
     * Register an encoder for the given format name.
     * Subsequent calls with the same [format] overwrite the previous registration.
     */
    fun register(format: String, encoder: ImageEncoder) { encoders[format] = encoder }
}

/**
 * Encode this [RenderResult] as a PNG byte array.
 * @throws IllegalStateException if no PNG encoder is registered
 */
fun RenderResult.toPng(): ByteArray {
    val encoder = ImageEncoderRegistry.find("png")
        ?: error("No PNG encoder registered. Add :codec:png to your dependencies to enable PNG export.")
    val layout = when (format) {
        PixelFormat.RGBA8 -> ImageEncoder.PixelLayout.RGBA8
        PixelFormat.BGRA8 -> ImageEncoder.PixelLayout.BGRA8
    }
    return encoder.encode(pixels.toByteArray(), width, height, ImageEncoder.Metadata(layout))
}

/**
 * Convert this [RenderResult] into an [Image] with RGBA_8888 color type.
 */
fun RenderResult.toImage(): Image = Image(width, height, org.graphiks.kanvas.image.ColorType.RGBA_8888, "render-result")
