package com.kanvas.core

/**
 * SkImageInterface defines the complete image interface compatible with Skia's SkImage
 * This interface ensures that Kanvas images have all the methods available in Skia
 */
interface SkImageInterface {
    
    /**
     * Image Properties
     */
    fun getWidth(): Int
    fun getHeight(): Int
    fun getRowBytes(): Int
    fun getConfig(): BitmapConfig
    fun getColorInfo(): ColorInfo
    
    /**
     * Pixel Access
     */
    fun getPixel(x: Int, y: Int): Color
    fun setPixel(x: Int, y: Int, color: Color)
    fun eraseColor(color: Color)
    
    /**
     * Image Manipulation
     */
    fun copy(): Bitmap
    fun extractSubset(src: Rect): Bitmap
    fun scale(newWidth: Int, newHeight: Int, sampling: SamplingOptions): Bitmap
    fun scaleNearest(newWidth: Int, newHeight: Int): Bitmap
    fun scaleLinear(newWidth: Int, newHeight: Int): Bitmap
    fun scaleCubic(newWidth: Int, newHeight: Int, resampler: CubicResampler): Bitmap
    fun applyColorFilter(filter: ColorFilter): Bitmap
    
    /**
     * Image Analysis
     */
    fun hasNonTransparentPixels(): Boolean
    fun isOpaque(): Boolean
    
    /**
     * Image Conversion
     */
    fun toByteBuffer(): ByteBuffer
    fun toByteArray(): ByteArray
    
    /**
     * Skia-compatible Methods (to be implemented)
     */
    fun makeSubset(left: Int, top: Int, right: Int, bottom: Int): Bitmap
    fun makeTextureImage(): TextureImage
    fun makeShader(tileModeX: TileMode, tileModeY: TileMode, localMatrix: Matrix?): Shader
    fun makeNonTextureImage(): Bitmap
    fun makeRasterImage(): Bitmap
    fun makeColorSpace(colorSpace: ColorSpace): Bitmap
    fun makeWithFilter(filter: ImageFilter, subset: Rect?, clipBounds: Rect?, outSubset: Rect?): Bitmap
    fun makeColorTypeAndColorSpace(colorType: ColorType, colorSpace: ColorSpace): Bitmap
    fun makeSubsetWithFilter(filter: ImageFilter, subset: Rect): Bitmap
    fun makeWithFilterAndColorSpace(filter: ImageFilter, colorSpace: ColorSpace): Bitmap
    
    /**
     * Advanced Image Operations
     */
    fun readPixels(dstInfo: ImageInfo, dstPixels: ByteBuffer, dstRowBytes: Int, srcX: Int, srcY: Int): Boolean
    fun readPixels(dstInfo: ImageInfo, dstPixels: ByteArray, dstRowBytes: Int, srcX: Int, srcY: Int): Boolean
    fun readPixels(dstInfo: ImageInfo, dstPixels: IntArray, dstRowBytes: Int, srcX: Int, srcY: Int): Boolean
    
    /**
     * Image Metadata
     */
    fun getAlphaType(): AlphaType
    fun getColorType(): ColorType
    fun getColorSpace(): ColorSpace
    fun isAlphaOnly(): Boolean
    fun isVolatile(): Boolean
    fun isLazyGenerated(): Boolean
    fun uniqueID(): Long
}