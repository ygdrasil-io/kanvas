
package com.kanvas.core

import com.kanvas.core.AlphaType.*
import com.kanvas.core.ColorType.*

/**
 * SkImageInfo describes the color and transparency of an image.
 * It contains the dimensions of the image (width and height) and the color information.
 */
data class SkImageInfo(
    val width: Int,
    val height: Int,
    val colorInfo: SkColorInfo,
    val uniqueID: UInt = 0u
) {
    constructor(width: Int, height: Int, colorType: ColorType, alphaType: AlphaType = PREMUL, colorSpace: ColorSpace? = null) : this(
        width, height, SkColorInfo(colorType, alphaType, colorSpace)
    )

    init {
        require(width > 0) { "width must be > 0" }
        require(height > 0) { "height must be > 0" }
    }

    /**
     * Returns the width of the image.
     */
    fun width(): Int = width

    /**
     * Returns the height of the image.
     */
    fun height(): Int = height

    /**
     * Returns the color type of the image.
     */
    fun colorType(): ColorType = colorInfo.colorType()

    /**
     * Returns the alpha type of the image.
     */
    fun alphaType(): AlphaType = colorInfo.alphaType()

    /**
     * Returns the color space of the image.
     */
    fun colorSpace(): ColorSpace? = colorInfo.colorSpace()

    /**
     * Returns the color info of the image.
     */
    fun colorInfo(): SkColorInfo = colorInfo

    /**
     * Returns the number of bytes per pixel.
     */
    fun bytesPerPixel(): Int = colorInfo.bytesPerPixel()

    /**
     * Returns the minimum row bytes for this image info.
     */
    fun minRowBytes(): Long = width.toLong() * bytesPerPixel().toLong()

    /**
     * Returns the shift per pixel for this color type.
     */
    fun shiftPerPixel(): Int = colorInfo.shiftPerPixel()

    /**
     * Returns the size in bytes of the image data.
     */
    fun computeByteSize(rowBytes: Long = minRowBytes()): Long {
        return height.toLong() * rowBytes
    }

    /**
     * Returns the offset in bytes for the pixel at (x, y).
     */
    fun computeOffset(x: Int, y: Int, rowBytes: Long = minRowBytes()): Long {
        require(x >= 0 && x < width) { "x must be in [0, width)" }
        require(y >= 0 && y < height) { "y must be in [0, height)" }
        return y.toLong() * rowBytes + (x.toLong() shl shiftPerPixel())
    }

    /**
     * Returns true if the image info is valid.
     */
    fun isValid(): Boolean {
        return width > 0 && height > 0 && colorInfo.isValid()
    }

    /**
     * Returns a new SkImageInfo with the same dimensions but a different color type.
     */
    fun makeColorType(newColorType: ColorType): SkImageInfo {
        return SkImageInfo(width, height, newColorType, alphaType(), colorSpace())
    }

    /**
     * Returns a new SkImageInfo with the same dimensions but a different alpha type.
     */
    fun makeAlphaType(newAlphaType: AlphaType): SkImageInfo {
        return SkImageInfo(width, height, colorType(), newAlphaType, colorSpace())
    }

    /**
     * Returns a new SkImageInfo with the same dimensions but a different color space.
     */
    fun makeColorSpace(newColorSpace: ColorSpace?): SkImageInfo {
        return SkImageInfo(width, height, colorType(), alphaType(), newColorSpace)
    }

    /**
     * Returns a new SkImageInfo with new dimensions.
     */
    fun makeWH(newWidth: Int, newHeight: Int): SkImageInfo {
        return SkImageInfo(newWidth, newHeight, colorType(), alphaType(), colorSpace())
    }

    /**
     * Returns a new SkImageInfo with new dimensions and color info.
     */
    fun makeDimensions(newWidth: Int, newHeight: Int, newColorInfo: SkColorInfo): SkImageInfo {
        return SkImageInfo(newWidth, newHeight, newColorInfo)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SkImageInfo) return false
        return width == other.width && height == other.height && colorInfo == other.colorInfo
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + colorInfo.hashCode()
        return result
    }

    override fun toString(): String {
        return "SkImageInfo(width=$width, height=$height, colorInfo=$colorInfo)"
    }

    companion object {
        /**
         * Returns true if the image info is valid.
         */
        fun isValid(info: SkImageInfo): Boolean {
            return info.isValid()
        }

        /**
         * Returns true if the conversion from src to dst is valid.
         */
        fun validConversion(dst: SkImageInfo, src: SkImageInfo): Boolean {
            return isValid(dst) && isValid(src)
        }
    }
}

/**
 * SkColorInfo describes the color and transparency of pixels.
 */
data class SkColorInfo(
    val colorType: ColorType,
    val alphaType: AlphaType,
    val colorSpace: ColorSpace?
) {
    constructor(colorType: ColorType, alphaType: AlphaType = PREMUL) : this(colorType, alphaType, null)

    /**
     * Returns the number of bytes per pixel for this color type.
     */
    fun bytesPerPixel(): Int {
        return when (colorType) {
            kUnknown_SkColorType -> 0
            kAlpha_8_SkColorType -> 1
            kRGB_565_SkColorType -> 2
            kARGB_4444_SkColorType -> 2
            kRGBA_8888_SkColorType -> 4
            kRGB_888x_SkColorType -> 4
            kBGRA_8888_SkColorType -> 4
            kRGBA_1010102_SkColorType -> 4
            kRGB_101010x_SkColorType -> 4
            kBGRA_1010102_SkColorType -> 4
            kBGR_101010x_SkColorType -> 4
            kRGBA_10x6_SkColorType -> 8
            kGray_8_SkColorType -> 1
            kRGBA_F16Norm_SkColorType -> 8
            kRGBA_F16_SkColorType -> 8
            kRGB_F16F16F16x_SkColorType -> 8
            kRGBA_F32_SkColorType -> 16
            kR8G8_unorm_SkColorType -> 2
            kA16_unorm_SkColorType -> 2
            kR16_unorm_SkColorType -> 2
            kR16G16_unorm_SkColorType -> 4
            kA16_float_SkColorType -> 2
            kR16G16_float_SkColorType -> 4
            kR16G16B16A16_unorm_SkColorType -> 8
            kSRGBA_8888_SkColorType -> 4
            kR8_unorm_SkColorType -> 1
        }
    }

    /**
     * Returns the shift per pixel for this color type.
     */
    fun shiftPerPixel(): Int {
        return when (colorType) {
            kUnknown_SkColorType -> 0
            kAlpha_8_SkColorType -> 0
            kRGB_565_SkColorType -> 1
            kARGB_4444_SkColorType -> 1
            kRGBA_8888_SkColorType -> 2
            kRGB_888x_SkColorType -> 2
            kBGRA_8888_SkColorType -> 2
            kRGBA_1010102_SkColorType -> 2
            kRGB_101010x_SkColorType -> 2
            kBGRA_1010102_SkColorType -> 2
            kBGR_101010x_SkColorType -> 2
            kRGBA_10x6_SkColorType -> 3
            kGray_8_SkColorType -> 0
            kRGBA_F16Norm_SkColorType -> 3
            kRGBA_F16_SkColorType -> 3
            kRGB_F16F16F16x_SkColorType -> 3
            kRGBA_F32_SkColorType -> 4
            kR8G8_unorm_SkColorType -> 1
            kA16_unorm_SkColorType -> 1
            kR16_unorm_SkColorType -> 1
            kR16G16_unorm_SkColorType -> 2
            kA16_float_SkColorType -> 1
            kR16G16_float_SkColorType -> 2
            kR16G16B16A16_unorm_SkColorType -> 3
            kSRGBA_8888_SkColorType -> 2
            kR8_unorm_SkColorType -> 0
        }
    }

    /**
     * Returns true if the color type is always opaque.
     */
    fun isOpaque(): Boolean {
        return !(colorType.channelFlags() and kAlpha_SkColorChannelFlag)
    }

    /**
     * Returns true if the color info is valid.
     */
    fun isValid(): Boolean {
        return colorType != kUnknown_SkColorType && alphaType != kUnknown_SkAlphaType
    }

    /**
     * Returns a new SkColorInfo with the same color type and color space but a different alpha type.
     */
    fun makeAlphaType(newAlphaType: AlphaType): SkColorInfo {
        return SkColorInfo(colorType, newAlphaType, colorSpace)
    }

    /**
     * Returns a new SkColorInfo with the same alpha type and color space but a different color type.
     */
    fun makeColorType(newColorType: ColorType): SkColorInfo {
        return SkColorInfo(newColorType, alphaType, colorSpace)
    }

    /**
     * Returns a new SkColorInfo with the same color type and alpha type but a different color space.
     */
    fun makeColorSpace(newColorSpace: ColorSpace?): SkColorInfo {
        return SkColorInfo(colorType, alphaType, newColorSpace)
    }

    /**
     * Returns true if the gamma is close to sRGB.
     */
    fun gammaCloseToSRGB(): Boolean {
        return colorSpace?.gammaCloseToSRGB() ?: false
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SkColorInfo) return false
        return colorType == other.colorType && alphaType == other.alphaType && colorSpace == other.colorSpace
    }

    override fun hashCode(): Int {
        var result = colorType.hashCode()
        result = 31 * result + alphaType.hashCode()
        result = 31 * result + (colorSpace?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "SkColorInfo(colorType=$colorType, alphaType=$alphaType, colorSpace=$colorSpace)"
    }
}