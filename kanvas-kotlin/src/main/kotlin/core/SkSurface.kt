package com.kanvas.core

import com.kanvas.core.AlphaType.*
import com.kanvas.core.ColorType.*

/**
 * SkSurface represents a drawing surface that can receive drawing commands.
 * It provides a canvas and manages the underlying pixel storage or GPU resources.
 */
abstract class SkSurface(protected val width: Int, protected val height: Int, protected val props: SkSurfaceProps) {

    init {
        require(width > 0) { "Width must be > 0" }
        require(height > 0) { "Height must be > 0" }
    }

    /**
     * Returns the width of the surface.
     *
     * @return The width in pixels
     */
    fun width(): Int = width

    /**
     * Returns the height of the surface.
     *
     * @return The height in pixels
     */
    fun height(): Int = height

    /**
     * Returns the surface properties.
     *
     * @return The surface properties
     */
    fun props(): SkSurfaceProps = props

    /**
     * Returns the image info for this surface.
     *
     * @return The image info describing color type and dimensions
     */
    abstract fun imageInfo(): SkImageInfo

    /**
     * Returns the canvas associated with this surface.
     *
     * @return The canvas for drawing on this surface
     */
    abstract fun canvas(): SkCanvas

    /**
     * Returns the generation ID of this surface.
     * The generation ID changes when the surface content is modified.
     *
     * @return The generation ID
     */
    fun generationID(): UInt {
        if (fGenerationID == 0u) {
            fGenerationID = onNewGenerationID()
        }
        return fGenerationID
    }

    /**
     * Notifies that the content of this surface is about to change.
     *
     * @param mode The content change mode
     */
    fun notifyContentWillChange(mode: ContentChangeMode) {
        onAboutToDraw(mode)
    }

    /**
     * Creates an image snapshot of this surface.
     *
     * @return A new image representing the current contents
     */
    fun makeImageSnapshot(): SkImage? {
        return onRefCachedImage()
    }

    /**
     * Creates an image snapshot of a subset of this surface.
     *
     * @param srcBounds The source bounds to capture
     * @return A new image representing the specified subset
     */
    fun makeImageSnapshot(srcBounds: SkIRect): SkImage? {
        val surfBounds = SkIRect.makeWH(width, height)
        val bounds = srcBounds.intersect(surfBounds)
        
        if (!bounds.isValid() || bounds.isEmpty()) {
            return null
        }
        
        if (bounds == surfBounds) {
            return makeImageSnapshot()
        } else {
            return onNewImageSnapshot(bounds)
        }
    }

    /**
     * Creates a temporary image from this surface.
     *
     * @return A new temporary image
     */
    fun makeTemporaryImage(): SkImage? {
        return onMakeTemporaryImage()
    }

    /**
     * Creates a new surface with the specified dimensions.
     *
     * @param info The desired image info for the new surface
     * @return A new surface or null if creation failed
     */
    fun makeSurface(info: SkImageInfo): SkSurface? {
        return onNewSurface(info)
    }

    /**
     * Creates a new surface with the specified dimensions.
     *
     * @param width The desired width
     * @param height The desired height
     * @return A new surface or null if creation failed
     */
    fun makeSurface(width: Int, height: Int): SkSurface? {
        return makeSurface(imageInfo().makeWH(width, height))
    }

    /**
     * Draws this surface onto another canvas.
     *
     * @param canvas The destination canvas
     * @param x The x-coordinate to draw at
     * @param y The y-coordinate to draw at
     * @param sampling The sampling options
     * @param paint The paint to use (optional)
     */
    fun draw(canvas: SkCanvas, x: SkScalar, y: SkScalar, sampling: SkSamplingOptions, paint: SkPaint? = null) {
        onDraw(canvas, x, y, sampling, paint)
    }

    /**
     * Peeks at the pixels of this surface.
     *
     * @param pixmap The pixmap to fill with pixel data
     * @return true if successful, false otherwise
     */
    fun peekPixels(pixmap: SkPixmap): Boolean {
        return canvas().peekPixels(pixmap)
    }

    /**
     * Reads pixels from this surface.
     *
     * @param pm The pixmap to read into
     * @param srcX The source x-coordinate
     * @param srcY The source y-coordinate
     * @return true if successful, false otherwise
     */
    fun readPixels(pm: SkPixmap, srcX: Int, srcY: Int): Boolean {
        return canvas().readPixels(pm, srcX, srcY)
    }

    /**
     * Reads pixels from this surface.
     *
     * @param dstInfo The desired image info
     * @param dstPixels The destination pixel buffer
     * @param dstRowBytes The destination row bytes
     * @param srcX The source x-coordinate
     * @param srcY The source y-coordinate
     * @return true if successful, false otherwise
     */
    fun readPixels(dstInfo: SkImageInfo, dstPixels: ByteArray, dstRowBytes: Long, srcX: Int, srcY: Int): Boolean {
        val pm = SkPixmap(dstInfo, dstPixels, dstRowBytes)
        return readPixels(pm, srcX, srcY)
    }

    /**
     * Reads pixels from this surface into a bitmap.
     *
     * @param bitmap The destination bitmap
     * @param srcX The source x-coordinate
     * @param srcY The source y-coordinate
     * @return true if successful, false otherwise
     */
    fun readPixels(bitmap: SkBitmap, srcX: Int, srcY: Int): Boolean {
        val pm = SkPixmap()
        return bitmap.peekPixels(pm) && readPixels(pm, srcX, srcY)
    }

    /**
     * Asynchronously rescales and reads pixels from this surface.
     *
     * @param info The desired image info
     * @param srcRect The source rectangle
     * @param rescaleGamma The gamma correction mode
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
        if (!SkIRect.makeWH(width, height).contains(srcRect) || !SkImageInfo.isValid(info)) {
            callback.onResult(context, null)
            return
        }
        onAsyncRescaleAndReadPixels(info, srcRect, rescaleGamma, rescaleMode, callback, context)
    }

    /**
     * Asynchronously rescales and reads YUV420 pixels from this surface.
     *
     * @param yuvColorSpace The YUV color space
     * @param dstColorSpace The destination color space
     * @param srcRect The source rectangle
     * @param dstSize The desired output size
     * @param rescaleGamma The gamma correction mode
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
        if (!SkIRect.makeWH(width, height).contains(srcRect) || dstSize.isZero() ||
            (dstSize.width() and 0b1) != 0 || (dstSize.height() and 0b1) != 0) {
            callback.onResult(context, null)
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
     * Asynchronously rescales and reads YUVA420 pixels from this surface.
     *
     * @param yuvColorSpace The YUV color space
     * @param dstColorSpace The destination color space
     * @param srcRect The source rectangle
     * @param dstSize The desired output size
     * @param rescaleGamma The gamma correction mode
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
        if (!SkIRect.makeWH(width, height).contains(srcRect) || dstSize.isZero() ||
            (dstSize.width() and 0b1) != 0 || (dstSize.height() and 0b1) != 0) {
            callback.onResult(context, null)
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
     * Discards the content of this surface to free up resources.
     * The surface becomes undefined after this call.
     */
    fun discard() {
        onDiscard()
    }

    /**
     * Returns true if this surface is raster-backed (CPU-backed).
     *
     * @return true if raster-backed, false otherwise
     */
    fun isRasterBacked(): Boolean {
        return type() == Type.kRaster
    }

    /**
     * Returns true if this surface is GPU-backed.
     *
     * @return true if GPU-backed, false otherwise
     */
    fun isGPUBacked(): Boolean {
        return type() == Type.kGanesh || type() == Type.kGraphite
    }

    /**
     * Returns the type of this surface.
     *
     * @return The surface type
     */
    abstract fun type(): Type

    // Abstract methods to be implemented by subclasses

    /**
     * Creates a new canvas for this surface.
     */
    protected abstract fun onNewCanvas(): SkCanvas

    /**
     * Creates a new surface with the specified image info.
     */
    protected abstract fun onNewSurface(info: SkImageInfo): SkSurface?

    /**
     * Creates a new image snapshot.
     */
    protected abstract fun onNewImageSnapshot(subset: SkIRect?): SkImage?

    /**
     * Creates a temporary image.
     */
    protected abstract fun onMakeTemporaryImage(): SkImage?

    /**
     * Draws this surface onto another canvas.
     */
    protected abstract fun onDraw(canvas: SkCanvas, x: SkScalar, y: SkScalar, sampling: SkSamplingOptions, paint: SkPaint?)

    /**
     * Discards the content of this surface.
     */
    protected abstract fun onDiscard()

    /**
     * Called when content is about to change.
     */
    protected abstract fun onAboutToDraw(mode: ContentChangeMode): Boolean

    /**
     * Called to copy-on-write if the surface is shared.
     */
    protected abstract fun onCopyOnWrite(mode: ContentChangeMode): Boolean

    /**
     * Generates a new generation ID.
     */
    protected abstract fun onNewGenerationID(): UInt

    /**
     * Returns the cached image for this surface.
     */
    protected abstract fun onRefCachedImage(): SkImage?

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

    companion object {
        /**
         * Creates a raster surface.
         *
         * @param imageInfo The image info for the surface
         * @param props The surface properties (optional)
         * @return A new raster surface or null if creation failed
         */
        fun makeRaster(imageInfo: SkImageInfo, props: SkSurfaceProps? = null): SkSurface? {
            return RasterSurface(imageInfo, props ?: SkSurfaceProps())
        }

        /**
         * Creates a raster surface with the specified dimensions.
         *
         * @param width The width of the surface
         * @param height The height of the surface
         * @param colorType The color type
         * @param alphaType The alpha type
         * @param props The surface properties (optional)
         * @return A new raster surface or null if creation failed
         */
        fun makeRaster(width: Int, height: Int, colorType: ColorType, alphaType: AlphaType = kPremul_SkAlphaType, props: SkSurfaceProps? = null): SkSurface? {
            val imageInfo = SkImageInfo(width, height, colorType, alphaType)
            return makeRaster(imageInfo, props)
        }

        /**
         * Creates a raster surface with the specified dimensions and color space.
         *
         * @param width The width of the surface
         * @param height The height of the surface
         * @param colorType The color type
         * @param alphaType The alpha type
         * @param colorSpace The color space
         * @param props The surface properties (optional)
         * @return A new raster surface or null if creation failed
         */
        fun makeRaster(width: Int, height: Int, colorType: ColorType, alphaType: AlphaType, colorSpace: SkColorSpace, props: SkSurfaceProps? = null): SkSurface? {
            val imageInfo = SkImageInfo(width, height, colorType, alphaType, colorSpace)
            return makeRaster(imageInfo, props)
        }

        /**
         * Creates a null surface (for testing and performance measurements).
         *
         * @param width The width of the surface
         * @param height The height of the surface
         * @param colorType The color type
         * @param alphaType The alpha type
         * @param props The surface properties (optional)
         * @return A new null surface
         */
        fun makeNull(width: Int, height: Int, colorType: ColorType, alphaType: AlphaType = kPremul_SkAlphaType, props: SkSurfaceProps? = null): SkSurface {
            val imageInfo = SkImageInfo(width, height, colorType, alphaType)
            return NullSurface(imageInfo, props ?: SkSurfaceProps())
        }
    }

    /**
     * Surface types.
     */
    enum class Type {
        kNull,
        kRaster,
        kGanesh,
        kGraphite
    }

    /**
     * Content change modes.
     */
    enum class ContentChangeMode {
        kDiscard,
        kRetain
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

    /**
     * Generation ID for tracking surface content changes.
     */
    protected var fGenerationID: UInt = 0u
}

/**
 * Concrete implementation of SkSurface for raster surfaces (CPU-backed).
 */
class RasterSurface(imageInfo: SkImageInfo, props: SkSurfaceProps) : SkSurface(imageInfo.width(), imageInfo.height(), props) {

    private val imageInfo: SkImageInfo = imageInfo
    private val canvas: SkCanvas by lazy { onNewCanvas() }

    override fun imageInfo(): SkImageInfo = imageInfo

    override fun canvas(): SkCanvas = canvas

    override fun type(): SkSurface.Type = SkSurface.Type.kRaster

    override fun onNewCanvas(): SkCanvas {
        // Create a raster canvas that draws into this surface
        return SkCanvas.makeRasterDirect(imageInfo, props)
    }

    override fun onNewSurface(info: SkImageInfo): SkSurface? {
        return RasterSurface(info, props)
    }

    override fun onNewImageSnapshot(subset: SkIRect?): SkImage? {
        // Create a raster image from the current surface contents
        val bitmap = SkBitmap()
        if (canvas.peekPixels(bitmap)) {
            return if (subset == null) {
                SkImage.makeFromBitmap(bitmap)
            } else {
                // For subset, we would need to create a subset bitmap
                // This is simplified for now
                SkImage.makeFromBitmap(bitmap)
            }
        }
        return null
    }

    override fun onMakeTemporaryImage(): SkImage? {
        return makeImageSnapshot()
    }

    override fun onDraw(canvas: SkCanvas, x: SkScalar, y: SkScalar, sampling: SkSamplingOptions, paint: SkPaint?) {
        val image = makeImageSnapshot()
        if (image != null) {
            canvas.drawImage(image, x.toFloat(), y.toFloat(), paint)
        }
    }

    override fun onDiscard() {
        // For raster surfaces, we can just clear the canvas
        canvas.clear(SkColor.TRANSPARENT)
    }

    override fun onAboutToDraw(mode: SkSurface.ContentChangeMode): Boolean {
        return true // Always allow drawing for raster surfaces
    }

    override fun onCopyOnWrite(mode: SkSurface.ContentChangeMode): Boolean {
        return true // Raster surfaces don't need copy-on-write
    }

    override fun onNewGenerationID(): UInt {
        fGenerationID++
        if (fGenerationID == 0u) fGenerationID = 1u // Skip 0
        return fGenerationID
    }

    override fun onRefCachedImage(): SkImage? {
        // Create and cache an image from the current surface contents
        val bitmap = SkBitmap()
        if (canvas.peekPixels(bitmap)) {
            return SkImage.makeFromBitmap(bitmap)
        }
        return null
    }

    override fun onAsyncRescaleAndReadPixels(
        info: SkImageInfo,
        srcRect: SkIRect,
        rescaleGamma: SkSurface.RescaleGamma,
        rescaleMode: SkSurface.RescaleMode,
        callback: SkSurface.ReadPixelsCallback,
        context: SkSurface.ReadPixelsContext
    ) {
        // Simple synchronous implementation for now
        val result = if (info == imageInfo && srcRect == SkIRect.makeWH(width, height)) {
            val bitmap = SkBitmap()
            if (canvas.peekPixels(bitmap)) {
                bitmap.pixels()
            } else {
                null
            }
        } else {
            null
        }
        callback.onResult(context, result)
    }

    override fun onAsyncRescaleAndReadPixelsYUV420(
        yuvColorSpace: SkYUVColorSpace,
        readAlpha: Boolean,
        dstColorSpace: SkColorSpace,
        srcRect: SkIRect,
        dstSize: SkISize,
        rescaleGamma: SkSurface.RescaleGamma,
        rescaleMode: SkSurface.RescaleMode,
        callback: SkSurface.ReadPixelsCallback,
        context: SkSurface.ReadPixelsContext
    ) {
        // YUV conversion not supported in this simple implementation
        callback.onResult(context, null)
    }
}

/**
 * Concrete implementation of SkSurface for null surfaces (for testing).
 */
class NullSurface(imageInfo: SkImageInfo, props: SkSurfaceProps) : SkSurface(imageInfo.width(), imageInfo.height(), props) {

    private val imageInfo: SkImageInfo = imageInfo
    private val canvas: SkCanvas by lazy { onNewCanvas() }

    override fun imageInfo(): SkImageInfo = imageInfo

    override fun canvas(): SkCanvas = canvas

    override fun type(): SkSurface.Type = SkSurface.Type.kNull

    override fun onNewCanvas(): SkCanvas {
        // Create a null canvas that doesn't actually draw
        return SkCanvas.makeNull(width, height)
    }

    override fun onNewSurface(info: SkImageInfo): SkSurface? {
        return NullSurface(info, props)
    }

    override fun onNewImageSnapshot(subset: SkIRect?): SkImage? {
        return null // Null surfaces don't have actual content
    }

    override fun onMakeTemporaryImage(): SkImage? {
        return null
    }

    override fun onDraw(canvas: SkCanvas, x: SkScalar, y: SkScalar, sampling: SkSamplingOptions, paint: SkPaint?) {
        // Null surfaces don't draw anything
    }

    override fun onDiscard() {
        // Nothing to discard
    }

    override fun onAboutToDraw(mode: SkSurface.ContentChangeMode): Boolean {
        return true
    }

    override fun onCopyOnWrite(mode: SkSurface.ContentChangeMode): Boolean {
        return true
    }

    override fun onNewGenerationID(): UInt {
        return 0u // Null surfaces don't track generations
    }

    override fun onRefCachedImage(): SkImage? {
        return null
    }

    override fun onAsyncRescaleAndReadPixels(
        info: SkImageInfo,
        srcRect: SkIRect,
        rescaleGamma: SkSurface.RescaleGamma,
        rescaleMode: SkSurface.RescaleMode,
        callback: SkSurface.ReadPixelsCallback,
        context: SkSurface.ReadPixelsContext
    ) {
        callback.onResult(context, null)
    }

    override fun onAsyncRescaleAndReadPixelsYUV420(
        yuvColorSpace: SkYUVColorSpace,
        readAlpha: Boolean,
        dstColorSpace: SkColorSpace,
        srcRect: SkIRect,
        dstSize: SkISize,
        rescaleGamma: SkSurface.RescaleGamma,
        rescaleMode: SkSurface.RescaleMode,
        callback: SkSurface.ReadPixelsCallback,
        context: SkSurface.ReadPixelsContext
    ) {
        callback.onResult(context, null)
    }
}