package org.graphiks.kanvas.image

import org.graphiks.kanvas.types.ColorSpace
import org.graphiks.math.SkIRect
import org.graphiks.math.SkISize

data class ImageInfo(
    val width: Int,
    val height: Int,
    val colorType: ColorType = ColorType.RGBA_8888,
    val alphaType: AlphaType = defaultAlphaTypeFor(colorType),
    val colorSpace: ColorSpace = ColorSpace.SRGB,
) {
    init {
        require(width >= 0 && height >= 0) { "negative dimensions: ${width}x$height" }
    }

    fun dimensions(): SkISize = SkISize.Make(width, height)

    fun bounds(): SkIRect = SkIRect.MakeWH(width, height)

    fun isEmpty(): Boolean = width <= 0 || height <= 0

    fun isOpaque(): Boolean = alphaType.isOpaque()

    fun bytesPerPixel(): Int = colorType.bytesPerPixel

    fun minRowBytes(): Int = width * bytesPerPixel()

    fun makeWH(newWidth: Int, newHeight: Int): ImageInfo =
        copy(width = newWidth, height = newHeight)

    fun makeColorType(newColorType: ColorType): ImageInfo =
        copy(colorType = newColorType)

    fun makeAlphaType(newAlphaType: AlphaType): ImageInfo =
        copy(alphaType = newAlphaType)

    fun makeColorSpace(newColorSpace: ColorSpace): ImageInfo =
        copy(colorSpace = newColorSpace)

    companion object {
        fun make(
            width: Int,
            height: Int,
            colorType: ColorType = ColorType.RGBA_8888,
            alphaType: AlphaType = defaultAlphaTypeFor(colorType),
            colorSpace: ColorSpace = ColorSpace.SRGB,
        ): ImageInfo = ImageInfo(width, height, colorType, alphaType, colorSpace)

        fun makeN32(
            width: Int,
            height: Int,
            alphaType: AlphaType = AlphaType.UNPREMUL,
            colorSpace: ColorSpace = ColorSpace.SRGB,
        ): ImageInfo = make(width, height, ColorType.RGBA_8888, alphaType, colorSpace)

        fun makeN32Premul(
            width: Int,
            height: Int,
            colorSpace: ColorSpace = ColorSpace.SRGB,
        ): ImageInfo = make(width, height, ColorType.RGBA_8888, AlphaType.PREMUL, colorSpace)

        fun makeA8(
            width: Int,
            height: Int,
            colorSpace: ColorSpace = ColorSpace.SRGB,
        ): ImageInfo = make(width, height, ColorType.ALPHA_8, AlphaType.PREMUL, colorSpace)

        fun make4444(
            width: Int,
            height: Int,
            colorSpace: ColorSpace = ColorSpace.SRGB,
        ): ImageInfo = make(width, height, ColorType.ARGB_4444, AlphaType.PREMUL, colorSpace)

        fun makeRgb565(
            width: Int,
            height: Int,
            colorSpace: ColorSpace = ColorSpace.SRGB,
        ): ImageInfo = make(width, height, ColorType.RGB_565, AlphaType.OPAQUE, colorSpace)

        fun makeGray8(
            width: Int,
            height: Int,
            colorSpace: ColorSpace = ColorSpace.SRGB,
        ): ImageInfo = make(width, height, ColorType.GRAY_8, AlphaType.OPAQUE, colorSpace)
    }
}

private fun defaultAlphaTypeFor(colorType: ColorType): AlphaType = when (colorType) {
    ColorType.RGBA_8888,
    ColorType.BGRA_8888,
        -> AlphaType.UNPREMUL
    ColorType.RGBA_F16,
    ColorType.ALPHA_8,
    ColorType.ARGB_4444,
        -> AlphaType.PREMUL
    ColorType.RGB_565,
    ColorType.GRAY_8,
        -> AlphaType.OPAQUE
}
