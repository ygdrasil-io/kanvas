package com.kanvas.core

import com.kanvas.core.AlphaType.*
import com.kanvas.core.ColorType.*

/**
 * SkImage is the base class for images in Kanvas.
 * It represents a two-dimensional array of pixels and provides methods for accessing and manipulating image data.
 */
abstract class SkImage(protected val imageInfo: SkImageInfo, uniqueID: UInt = 0u) {
    protected val uniqueID: UInt = if (uniqueID == 0u) generateUniqueID() else uniqueID

    init {
        require(imageInfo.width() > 0) { "Image width must be > 0" }
        require(imageInfo.height() > 0) { "Image height must be > 0" }
    }

    /**
     * Returns the image info describing the color and dimensions of the image.
     */
    fun imageInfo(): SkImageInfo = imageInfo

    /**
     * Returns the width of the image.
     */
    fun width(): Int = imageInfo.width()

    /**
     * Returns the height of the image.
     */
    fun height(): Int = imageInfo.height()

    /**
     * Returns the color type of the image.
     */
    fun colorType(): ColorType = imageInfo.colorType()

    /**
     * Returns the alpha type of the image.
     */
    fun alphaType(): AlphaType = imageInfo.alphaType()

    /**
     * Returns the color space of the image.
     */
    fun colorSpace(): SkColorSpace? = imageInfo.colorSpace()

    /**
     * Returns the unique ID of this image.
     */
    fun uniqueID(): UInt = uniqueID

    /**
     * Returns true if the image is valid.
     */
    fun isValid(): Boolean = imageInfo.isValid()

    /**
     * Attempts to peek at the pixels without forcing a decode or copy.
     *
     * @param pixmap The pixmap to fill with pixel data
     * @return true if the pixels were successfully peeked, false otherwise
     */
    fun peekPixels(pixmap: SkPixmap): Boolean {
        return onPeekPixels(pixmap)
    }

    /**
     * Reads pixels from the image into the provided buffer.
     *
     * @param dstInfo The desired image info for the output
     * @param dstPixels The buffer to write pixels to
     * @param dstRowBytes The number of bytes per row in the buffer
     * @param srcX The x-coordinate of the source rectangle
     * @param srcY The y-coordinate of the source rectangle
     * @param cachingHint Hint for caching behavior
     * @return true if the pixels were successfully read, false otherwise
     */
    fun readPixels(dstInfo: SkImageInfo, dstPixels: ByteArray, dstRowBytes: Long, srcX: Int, srcY: Int, cachingHint: CachingHint): Boolean {
        return onReadPixels(dstInfo, dstPixels, dstRowBytes, srcX, srcY, cachingHint)
    }

    /**
     * Creates a new image that is a scaled version of this image.
     *
     * @param newInfo The desired dimensions and color info for the new image
     * @param sampling The sampling options for scaling
     * @return A new scaled image or null if scaling failed
     */
    fun makeScaled(newInfo: SkImageInfo, sampling: SkSamplingOptions): SkImage? {
        return makeScaled(null, newInfo, sampling, SkSurfaceProps())
    }

    /**
     * Creates a new image that is a scaled version of this image.
     *
     * @param recorder The recorder for recording drawing commands (optional)
     * @param newInfo The desired dimensions and color info for the new image
     * @param sampling The sampling options for scaling
     * @param props The surface properties for the new image
     * @return A new scaled image or null if scaling failed
     */
    fun makeScaled(recorder: SkRecorder?, newInfo: SkImageInfo, sampling: SkSamplingOptions, props: SkSurfaceProps): SkImage? {
        if (!SkImageInfo.isValid(newInfo)) {
            return null
        }
        if (newInfo == imageInfo) {
            return this
        }

        val surface = onMakeSurface(recorder, newInfo)
        if (surface == null) {
            return null
        }

        val paint = SkPaint().apply {
            blendMode = SkBlendMode.kSrc
        }
        surface.canvas.drawImageRect(
            this,
            SkRect.makeWH(newInfo.width().toFloat(), newInfo.height().toFloat()),
            sampling,
            paint
        )
        return surface.makeImageSnapshot()
    }

    /**
     * Asynchronously rescales and reads pixels from the image.
     *
     * @param info The desired image info for the output
     * @param srcRect The source rectangle to read from
     * @param rescaleGamma The gamma correction for rescaling
     * @param rescaleMode The rescaling mode
     * @param callback The callback to receive the result
     * @param context The context for the callback
     */
    fun asyncRescaleAndReadPixels(
        info: SkImageInfo,
        srcRect: SkIRect,
        rescaleGamma: RescaleGamma,
        rescaleMode: RescaleMode,
        callback: ReadPixelsCallback,
        context: ReadPixelsContext
    ) {
        if (!SkIRect.makeWH(width(), height()).contains(srcRect) || !SkImageInfo.isValid(info)) {
            callback(context, null)
            return
        }
        onAsyncRescaleAndReadPixels(info, srcRect, rescaleGamma, rescaleMode, callback, context)
    }

    /**
     * Asynchronously rescales and reads YUV420 pixels from the image.
     *
     * @param yuvColorSpace The YUV color space
     * @param dstColorSpace The destination color space
     * @param srcRect The source rectangle to read from
     * @param dstSize The desired output size
     * @param rescaleGamma The gamma correction for rescaling
     * @param rescaleMode The rescaling mode
     * @param callback The callback to receive the result
     * @param context The context for the callback
     */
    fun asyncRescaleAndReadPixelsYUV420(
        yuvColorSpace: SkYUVColorSpace,
        dstColorSpace: SkColorSpace,
        srcRect: SkIRect,
        dstSize: SkISize,
        rescaleGamma: RescaleGamma,
        rescaleMode: RescaleMode,
        callback: ReadPixelsCallback,
        context: ReadPixelsContext
    ) {
        if (!SkIRect.makeWH(width(), height()).contains(srcRect) || dstSize.isZero() ||
            (dstSize.width() and 0b1) != 0 || (dstSize.height() and 0b1) != 0) {
            callback(context, null)
            return
        }
        onAsyncRescaleAndReadPixelsYUV420(
            yuvColorSpace,
            false, // readAlpha
            dstColorSpace,
            srcRect,
            dstSize,
            rescaleGamma,
            rescaleMode,
            callback,
            context
        )
    }

    /**
     * Asynchronously rescales and reads YUVA420 pixels from the image.
     *
     * @param yuvColorSpace The YUV color space
     * @param dstColorSpace The destination color space
     * @param srcRect The source rectangle to read from
     * @param dstSize The desired output size
     * @param rescaleGamma The gamma correction for rescaling
     * @param rescaleMode The rescaling mode
     * @param callback The callback to receive the result
     * @param context The context for the callback
     */
    fun asyncRescaleAndReadPixelsYUVA420(
        yuvColorSpace: SkYUVColorSpace,
        dstColorSpace: SkColorSpace,
        srcRect: SkIRect,
        dstSize: SkISize,
        rescaleGamma: RescaleGamma,
        rescaleMode: RescaleMode,
        callback: ReadPixelsCallback,
        context: ReadPixelsContext
    ) {
        if (!SkIRect.makeWH(width(), height()).contains(srcRect) || dstSize.isZero() ||
            (dstSize.width() and 0b1) != 0 || (dstSize.height() and 0b1) != 0) {
            callback(context, null)
            return
        }
        onAsyncRescaleAndReadPixelsYUV420(
            yuvColorSpace,
            true, // readAlpha
            dstColorSpace,
            srcRect,
            dstSize,
            rescaleGamma,
            rescaleMode,
            callback,
            context
        )
    }

    /**
     * Scales pixels and writes them to the provided pixmap.
     *
     * @param dst The destination pixmap
     * @param sampling The sampling options for scaling
     * @param cachingHint Hint for caching behavior
     * @return true if scaling was successful, false otherwise
     */
    fun scalePixels(dst: SkPixmap, sampling: SkSamplingOptions, cachingHint: CachingHint): Boolean {
        if (width() == dst.width() && height() == dst.height()) {
            return readPixels(dst.info(), dst.pixels(), dst.rowBytes(), 0, 0, cachingHint)
        }

        // For now, use a simple approach - this would be optimized in a real implementation
        val bitmap = SkBitmap()
        if (onGetROPixels(bitmap, cachingHint)) {
            val pixmap = SkPixmap()
            if (bitmap.peekPixels(pixmap)) {
                return pixmap.scalePixels(dst, sampling)
            }
        }
        return false
    }

    /**
     * Creates a shader from this image.
     *
     * @param sampling The sampling options for the shader
     * @param localMatrix The local matrix for the shader (optional)
     * @return A new shader using this image
     */
    fun makeShader(sampling: SkSamplingOptions, localMatrix: SkMatrix? = null): SkShader {
        return SkImageShader.make(this, SkTileMode.kClamp, SkTileMode.kClamp, sampling, localMatrix)
    }

    /**
     * Creates a subset of this image.
     *
     * @param subset The rectangle defining the subset
     * @return A new image containing the subset or null if the subset is invalid
     */
    fun makeSubset(subset: SkIRect): SkImage? {
        return onMakeSubset(subset)
    }

    /**
     * Returns the texture size of this image in bytes.
     */
    fun textureSize(): Long {
        return onTextureSize()
    }

    /**
     * Returns true if this image has mipmaps.
     */
    fun hasMipmaps(): Boolean {
        return onHasMipmaps()
    }

    /**
     * Returns true if this image is protected (e.g., DRM-protected).
     */
    fun isProtected(): Boolean {
        return onIsProtected()
    }

    /**
     * Returns true if this image is texture-backed (GPU-backed).
     */
    fun isTextureBacked(): Boolean {
        return onIsTextureBacked()
    }

    /**
     * Returns true if this image is lazy generated (e.g., from a codec or picture).
     */
    fun isLazyGenerated(): Boolean {
        return onIsLazyGenerated()
    }

    /**
     * Returns true if this image is raster-backed (CPU-backed).
     */
    fun isRasterBacked(): Boolean {
        return onIsRasterBacked()
    }

    /**
     * Returns the type of this image.
     */
    fun type(): Type {
        return onType()
    }

    /**
     * Called when this image is part of the key to a resource cache entry.
     */
    fun notifyAddedToRasterCache() {
        onNotifyAddedToRasterCache()
    }

    /**
     * Creates a new image with a different color space.
     *
     * @param newColorSpace The new color space
     * @return A new image with the specified color space or null if conversion failed
     */
    fun reinterpretColorSpace(newColorSpace: SkColorSpace): SkImage? {
        return onReinterpretColorSpace(newColorSpace)
    }

    /**
     * Creates a new image with mipmaps.
     *
     * @param mipmap The mipmap to use
     * @return A new image with mipmaps or null if mipmap creation failed
     */
    fun makeWithMipmaps(mipmap: SkMipmap): SkImage? {
        return onMakeWithMipmaps(mipmap)
    }

    /**
     * Encodes this image and returns the encoded data.
     *
     * @return The encoded data or null if encoding failed
     */
    fun refEncoded(): ByteArray? {
        return onRefEncoded()
    }

    /**
     * Converts this image to a legacy bitmap.
     *
     * @param bitmap The bitmap to fill
     * @return true if conversion was successful, false otherwise
     */
    fun asLegacyBitmap(bitmap: SkBitmap): Boolean {
        return onAsLegacyBitmap(bitmap)
    }

    // Abstract methods to be implemented by subclasses

    /**
     * Attempts to peek at the pixels without forcing a decode or copy.
     */
    protected abstract fun onPeekPixels(pixmap: SkPixmap): Boolean

    /**
     * Reads pixels from the image.
     */
    protected abstract fun onReadPixels(dstInfo: SkImageInfo, dstPixels: ByteArray, dstRowBytes: Long, srcX: Int, srcY: Int, cachingHint: CachingHint): Boolean

    /**
     * Creates a surface for this image.
     */
    protected abstract fun onMakeSurface(recorder: SkRecorder?, newInfo: SkImageInfo): SkSurface?

    /**
     * Asynchronously rescales and reads pixels.
     */
    protected abstract fun onAsyncRescaleAndReadPixels(
        info: SkImageInfo,
        srcRect: SkIRect,
        rescaleGamma: RescaleGamma,
        rescaleMode: RescaleMode,
        callback: ReadPixelsCallback,
        context: ReadPixelsContext
    )

    /**
     * Asynchronously rescales and reads YUV pixels.
     */
    protected abstract fun onAsyncRescaleAndReadPixelsYUV420(
        yuvColorSpace: SkYUVColorSpace,
        readAlpha: Boolean,
        dstColorSpace: SkColorSpace,
        srcRect: SkIRect,
        dstSize: SkISize,
        rescaleGamma: RescaleGamma,
        rescaleMode: RescaleMode,
        callback: ReadPixelsCallback,
        context: ReadPixelsContext
    )

    /**
     * Gets read-only pixels for this image.
     */
    protected abstract fun onGetROPixels(bitmap: SkBitmap, cachingHint: CachingHint): Boolean

    /**
     * Creates a subset of this image.
     */
    protected abstract fun onMakeSubset(subset: SkIRect): SkImage?

    /**
     * Returns the texture size of this image.
     */
    protected abstract fun onTextureSize(): Long

    /**
     * Returns true if this image has mipmaps.
     */
    protected abstract fun onHasMipmaps(): Boolean

    /**
     * Returns true if this image is protected.
     */
    protected abstract fun onIsProtected(): Boolean

    /**
     * Returns true if this image is texture-backed.
     */
    protected abstract fun onIsTextureBacked(): Boolean

    /**
     * Returns true if this image is lazy generated.
     */
    protected abstract fun onIsLazyGenerated(): Boolean

    /**
     * Returns true if this image is raster-backed.
     */
    protected abstract fun onIsRasterBacked(): Boolean

    /**
     * Returns the type of this image.
     */
    protected abstract fun onType(): Type

    /**
     * Called when this image is added to a raster cache.
     */
    protected abstract fun onNotifyAddedToRasterCache()

    /**
     * Creates a new image with a different color space.
     */
    protected abstract fun onReinterpretColorSpace(newColorSpace: SkColorSpace): SkImage?

    /**
     * Creates a new image with mipmaps.
     */
    protected abstract fun onMakeWithMipmaps(mipmap: SkMipmap): SkImage?

    /**
     * Encodes this image.
     */
    protected abstract fun onRefEncoded(): ByteArray?

    /**
     * Converts this image to a legacy bitmap.
     */
    protected abstract fun onAsLegacyBitmap(bitmap: SkBitmap): Boolean

    companion object {
        private var nextUniqueID: UInt = 1u

        /**
         * Generates a unique ID for the image.
         */
        private fun generateUniqueID(): UInt {
            return nextUniqueID++
        }

        /**
         * Creates an image from an image generator.
         *
         * @param generator The image generator to use
         * @return A new image or null if creation failed
         */
        fun makeFromGenerator(generator: SkImageGenerator): SkImage? {
            // Placeholder implementation - to be implemented by specific factories
            return null
        }

        /**
         * Creates an image from a bitmap.
         *
         * @param bitmap The bitmap to use
         * @return A new image or null if creation failed
         */
        fun makeFromBitmap(bitmap: SkBitmap): SkImage? {
            // Placeholder implementation - to be implemented by specific factories
            return null
        }

        /**
         * Creates an image from a pixmap.
         *
         * @param pixmap The pixmap to use
         * @return A new image or null if creation failed
         */
        fun makeFromPixmap(pixmap: SkPixmap): SkImage? {
            // Placeholder implementation - to be implemented by specific factories
            return null
        }

        /**
         * Creates an image from encoded data.
         *
         * @param data The encoded image data
         * @return A new image or null if decoding failed
         */
        fun makeFromEncoded(data: ByteArray): SkImage? {
            // Placeholder implementation - to be implemented by specific factories
            return null
        }

        /**
         * Creates an image from a picture.
         *
         * @param picture The picture to render
         * @param dimensions The desired dimensions
         * @return A new image or null if rendering failed
         */
        fun makeFromPicture(picture: SkPicture, dimensions: SkISize): SkImage? {
            // Placeholder implementation - to be implemented by specific factories
            return null
        }
    }

    /**
     * Image types.
     */
    enum class Type {
        kRaster,
        kRasterPinnable,
        kLazy,
        kLazyPicture,
        kLazyTexture,
        kGanesh,
        kGaneshYUVA,
        kGraphite,
        kGraphiteYUVA
    }

    /**
     * Caching hints for pixel operations.
     */
    enum class CachingHint {
        kAllow,
        kDisallow
    }

    /**
     * Rescale gamma modes.
     */
    enum class RescaleGamma {
        kSrc,
        kLinear
    }

    /**
     * Rescale modes.
     */
    enum class RescaleMode {
        kNearest,
        kLinear
    }

    /**
     * Callback for asynchronous pixel operations.
     */
    fun interface ReadPixelsCallback {
        fun onResult(context: ReadPixelsContext, result: ByteArray?)
    }

    /**
     * Context for read pixels callbacks.
     */
    interface ReadPixelsContext
}

/**
 * Concrete implementation of SkImage for raster images.
 */
class RasterImage(imageInfo: SkImageInfo, private val pixelData: ByteArray) : SkImage(imageInfo) {

    override fun onPeekPixels(pixmap: SkPixmap): Boolean {
        // Simple implementation that copies pixel data to the pixmap
        if (pixmap.info() != imageInfo) {
            return false
        }
        if (pixmap.pixels().size < pixelData.size) {
            return false
        }
        System.arraycopy(pixelData, 0, pixmap.pixels(), 0, pixelData.size)
        return true
    }

    override fun onReadPixels(dstInfo: SkImageInfo, dstPixels: ByteArray, dstRowBytes: Long, srcX: Int, srcY: Int, cachingHint: SkImage.image.CachingHint): Boolean {
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

    override fun onMakeSurface(recorder: SkRecorder?, newInfo: SkImageInfo): SkSurface? {
        // Create a raster surface
        return SkSurface.makeRaster(newInfo)
    }

    override fun onAsyncRescaleAndReadPixels(info: SkImageInfo, srcRect: SkIRect, rescaleGamma: SkImage.RescaleGamma, rescaleMode: SkImage.RescaleMode, callback: SkImage.ReadPixelsCallback, context: SkImage.ReadPixelsContext) {
        // Simple synchronous implementation for now
        val result = if (info == imageInfo && srcRect == SkIRect.makeWH(width(), height())) {
            pixelData.copyOf()
        } else {
            null
        }
        callback.onResult(context, result)
    }

    override fun onAsyncRescaleAndReadPixelsYUV420(yuvColorSpace: SkYUVColorSpace, readAlpha: Boolean, dstColorSpace: SkColorSpace, srcRect: SkIRect, dstSize: SkISize, rescaleGamma: SkImage.RescaleGamma, rescaleMode: SkImage.RescaleMode, callback: SkImage.ReadPixelsCallback, context: SkImage.ReadPixelsContext) {
        // Not supported for raster images
        callback.onResult(context, null)
    }

    override fun onGetROPixels(bitmap: SkBitmap, cachingHint: SkImage.CachingHint): Boolean {
        // Simple implementation
        return bitmap.installPixels(imageInfo, pixelData, pixelData.size.toLong())
    }

    override fun onMakeSubset(subset: SkIRect): SkImage? {
        // Check if subset is valid
        if (!SkIRect.makeWH(width(), height()).contains(subset)) {
            return null
        }

        // Create new pixel data for the subset
        val subsetWidth = subset.width()
        val subsetHeight = subset.height()
        val bytesPerPixel = imageInfo.bytesPerPixel()
        val subsetData = ByteArray(subsetWidth * subsetHeight * bytesPerPixel)

        // Copy pixels from the subset region
        val sourceRowBytes = width() * bytesPerPixel
        val destRowBytes = subsetWidth * bytesPerPixel

        for (y in 0 until subsetHeight) {
            val srcPos = ((subset.y() + y) * sourceRowBytes + subset.x() * bytesPerPixel)
            val destPos = y * destRowBytes
            System.arraycopy(pixelData, srcPos, subsetData, destPos, destRowBytes)
        }

        val subsetInfo = SkImageInfo.makeWH(subsetWidth, subsetHeight, imageInfo.colorInfo())
        return RasterImage(subsetInfo, subsetData)
    }

    override fun onTextureSize(): Long = 0

    override fun onHasMipmaps(): Boolean = false

    override fun onIsProtected(): Boolean = false

    override fun onIsTextureBacked(): Boolean = false

    override fun onIsLazyGenerated(): Boolean = false

    override fun onIsRasterBacked(): Boolean = true

    override fun onType(): SkImage.Type = SkImage.Type.kRaster

    override fun onNotifyAddedToRasterCache() {
        // No special handling needed
    }

    override fun onReinterpretColorSpace(newColorSpace: SkColorSpace): SkImage? {
        // Create a new image with the same pixel data but different color space
        val newInfo = imageInfo.makeColorSpace(newColorSpace)
        return RasterImage(newInfo, pixelData.copyOf())
    }

    override fun onMakeWithMipmaps(mipmap: SkMipmap): SkImage? {
        // Mipmaps not supported in this simple implementation
        return null
    }

    override fun onRefEncoded(): ByteArray? {
        // Encoding not supported in this simple implementation
        return null
    }

    override fun onAsLegacyBitmap(bitmap: SkBitmap): Boolean {
        return bitmap.installPixels(imageInfo, pixelData, pixelData.size.toLong())
    }
}