package com.kanvas.core

import com.kanvas.core.AlphaType.*
import com.kanvas.core.ColorType.*

/**
 * SkImageGenerator is the base class for generating pixel data for images.
 * It provides an interface for decoding and accessing image data from various sources.
 */
abstract class SkImageGenerator(protected val imageInfo: SkImageInfo, uniqueID: UInt = 0u) {
    protected val uniqueID: UInt = if (uniqueID == 0u) generateUniqueID() else uniqueID

    /**
     * Returns the image info describing the color and dimensions of the image.
     */
    fun getInfo(): SkImageInfo = imageInfo

    /**
     * Returns the unique ID of this image generator.
     */
    fun getUniqueID(): UInt = uniqueID

    /**
     * Gets the pixels from the image generator and writes them to the provided buffer.
     *
     * @param info The desired image info for the output pixels
     * @param pixels The buffer to write the pixels to
     * @param rowBytes The number of bytes per row in the buffer
     * @return true if the pixels were successfully retrieved, false otherwise
     */
    fun getPixels(info: SkImageInfo, pixels: ByteArray, rowBytes: Long): Boolean {
        if (info.colorType() == kUnknown_SkColorType) {
            return false
        }
        if (pixels.isEmpty()) {
            return false
        }
        if (rowBytes < info.minRowBytes()) {
            return false
        }

        return onGetPixels(info, pixels, rowBytes, Options())
    }

    /**
     * Gets the pixels from the image generator and writes them to the provided buffer.
     *
     * @param dstInfo The desired image info for the output pixels
     * @param dstPixels The buffer to write the pixels to
     * @param dstRowBytes The number of bytes per row in the buffer
     * @param srcX The x-coordinate of the source rectangle
     * @param srcY The y-coordinate of the source rectangle
     * @param options The options for pixel retrieval
     * @return true if the pixels were successfully retrieved, false otherwise
     */
    fun getPixels(dstInfo: SkImageInfo, dstPixels: ByteArray, dstRowBytes: Long, srcX: Int, srcY: Int, options: Options): Boolean {
        // Placeholder implementation - to be implemented by subclasses
        return false
    }

    /**
     * Queries YUVA information for YUV images.
     *
     * @param supportedDataTypes The supported YUVA data types
     * @param yuvaPixmapInfo The output YUVA pixmap information
     * @return true if YUVA information is available and supported, false otherwise
     */
    fun queryYUVAInfo(supportedDataTypes: YUVAPixmapInfo.SupportedDataTypes, yuvaPixmapInfo: YUVAPixmapInfo): Boolean {
        return onQueryYUVAInfo(supportedDataTypes, yuvaPixmapInfo) &&
               yuvaPixmapInfo.isSupported(supportedDataTypes)
    }

    /**
     * Gets the YUVA planes for YUV images.
     *
     * @param yuvaPixmaps The YUVA pixmaps to fill with plane data
     * @return true if the YUVA planes were successfully retrieved, false otherwise
     */
    fun getYUVAPlanes(yuvaPixmaps: YUVAPixmaps): Boolean {
        return onGetYUVAPlanes(yuvaPixmaps)
    }

    /**
     * Called when the generating surface is deleted to indicate no further writes may happen.
     */
    open fun generatingSurfaceIsDeleted() {
        // Placeholder - to be overridden by subclasses if needed
    }

    /**
     * Options for pixel retrieval.
     */
    data class Options(
        val zeroInitialized: Boolean = true,
        val subsample: Int = 0,
        val mipmapLevel: Int = 0
    )

    /**
     * YUVA pixmap information.
     */
    data class YUVAPixmapInfo(
        val dataTypes: SupportedDataTypes,
        val dimensions: List<SkISize>,
        val colorSpaces: List<SkColorSpace>,
        val channelFlags: List<Int>
    ) {
        enum class SupportedDataTypes {
            YUV_420,
            YUV_422,
            YUV_444,
            YUVA_420,
            YUVA_422,
            YUVA_444
        }

        fun isSupported(supportedDataTypes: SupportedDataTypes): Boolean {
            // Placeholder implementation
            return true
        }
    }

    /**
     * YUVA pixmaps container.
     */
    data class YUVAPixmaps(
        val pixmaps: List<SkPixmap>,
        val colorSpaces: List<SkColorSpace>
    )

    /**
     * Abstract method to be implemented by subclasses for pixel retrieval.
     */
    protected abstract fun onGetPixels(dstInfo: SkImageInfo, dstPixels: ByteArray, dstRowBytes: Long, options: Options): Boolean

    /**
     * Abstract method to be implemented by subclasses for YUVA info query.
     */
    protected abstract fun onQueryYUVAInfo(supportedDataTypes: YUVAPixmapInfo.SupportedDataTypes, yuvaPixmapInfo: YUVAPixmapInfo): Boolean

    /**
     * Abstract method to be implemented by subclasses for YUVA plane retrieval.
     */
    protected abstract fun onGetYUVAPlanes(yuvaPixmaps: YUVAPixmaps): Boolean

    companion object {
        private var nextUniqueID: UInt = 1u

        /**
         * Generates a unique ID for the image generator.
         */
        private fun generateUniqueID(): UInt {
            return nextUniqueID++
        }

        /**
         * Creates an image generator from encoded data.
         *
         * @param data The encoded image data
         * @param alphaType The desired alpha type (optional)
         * @return A new SkImageGenerator instance or null if the data cannot be decoded
         */
        fun makeFromEncoded(data: ByteArray, alphaType: AlphaType? = null): SkImageGenerator? {
            // Placeholder implementation - to be implemented
            // This would typically use SkCodec to decode the image data
            return null
        }

        /**
         * Creates an image generator from a picture.
         *
         * @param size The desired size of the image
         * @param picture The picture to render
         * @param matrix The transformation matrix (optional)
         * @param paint The paint to use for rendering (optional)
         * @param bitDepth The bit depth for rendering
         * @param colorSpace The color space for rendering (optional)
         * @param surfaceProps The surface properties for rendering
         * @return A new SkImageGenerator instance or null if the picture cannot be used
         */
        fun makeFromPicture(
            size: SkISize,
            picture: SkPicture,
            matrix: SkMatrix? = null,
            paint: SkPaint? = null,
            bitDepth: BitDepth = BitDepth.kU8,
            colorSpace: SkColorSpace? = null,
            surfaceProps: SkSurfaceProps = SkSurfaceProps()
        ): SkImageGenerator? {
            // Placeholder implementation - to be implemented
            return null
        }
    }

    /**
     * Bit depth enum for image generation.
     */
    enum class BitDepth {
        kU8,
        kU16,
        kF16,
        kF32
    }
}

/**
 * Concrete implementation of SkImageGenerator for testing and basic usage.
 */
class SimpleImageGenerator(imageInfo: SkImageInfo, private val pixelData: ByteArray) : SkImageGenerator(imageInfo) {

    override fun onGetPixels(dstInfo: SkImageInfo, dstPixels: ByteArray, dstRowBytes: Long, options: SkImageGenerator.Options): Boolean {
        // Simple implementation that copies pixel data
        if (dstInfo != imageInfo) {
            return false
        }
        if (dstPixels.size < pixelData.size) {
            return false
        }
        System.arraycopy(pixelData, 0, dstPixels, 0, pixelData.size)
        return true
    }

    override fun onQueryYUVAInfo(supportedDataTypes: SkImageGenerator.YUVAPixmapInfo.SupportedDataTypes, yuvaPixmapInfo: SkImageGenerator.YUVAPixmapInfo): Boolean {
        return false // Not a YUV image
    }

    override fun onGetYUVAPlanes(yuvaPixmaps: SkImageGenerator.YUVAPixmaps): Boolean {
        return false // Not a YUV image
    }
}