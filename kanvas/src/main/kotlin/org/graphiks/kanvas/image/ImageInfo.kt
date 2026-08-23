package org.graphiks.kanvas.image

import org.graphiks.kanvas.color.ImageColorSpace
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32

data class ImageInfo(
    val width: Int,
    val height: Int,
    val colorType: ColorType = ColorType.RGBA_8888,
    val alphaType: AlphaType = colorType.defaultAlphaType(),
    val colorSpace: ImageColorSpace = ImageColorSpace.sRGB(),
) {
    init {
        require(width >= 0 && height >= 0) { "negative dimensions: ${width}x$height" }
    }

    fun dimensions(): SizeI32 = SizeI32.of(width, height)

    fun bounds(): RectI32 = RectI32.ofSize(width, height)

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

    fun makeColorSpace(newColorSpace: ImageColorSpace): ImageInfo =
        copy(colorSpace = newColorSpace)

    companion object {
        fun make(
            width: Int,
            height: Int,
            colorType: ColorType = ColorType.RGBA_8888,
            alphaType: AlphaType = colorType.defaultAlphaType(),
            colorSpace: ImageColorSpace = ImageColorSpace.sRGB(),
        ): ImageInfo = ImageInfo(width, height, colorType, alphaType, colorSpace)

        fun makeN32(
            width: Int,
            height: Int,
            alphaType: AlphaType = AlphaType.UNPREMUL,
            colorSpace: ImageColorSpace = ImageColorSpace.sRGB(),
        ): ImageInfo = make(width, height, ColorType.RGBA_8888, alphaType, colorSpace)

        fun makeN32Premul(
            width: Int,
            height: Int,
            colorSpace: ImageColorSpace = ImageColorSpace.sRGB(),
        ): ImageInfo = make(width, height, ColorType.RGBA_8888, AlphaType.PREMUL, colorSpace)

        fun makeA8(
            width: Int,
            height: Int,
            colorSpace: ImageColorSpace = ImageColorSpace.sRGB(),
        ): ImageInfo = make(width, height, ColorType.ALPHA_8, AlphaType.PREMUL, colorSpace)

        fun make4444(
            width: Int,
            height: Int,
            colorSpace: ImageColorSpace = ImageColorSpace.sRGB(),
        ): ImageInfo = make(width, height, ColorType.ARGB_4444, AlphaType.PREMUL, colorSpace)

        fun makeRgb565(
            width: Int,
            height: Int,
            colorSpace: ImageColorSpace = ImageColorSpace.sRGB(),
        ): ImageInfo = make(width, height, ColorType.RGB_565, AlphaType.OPAQUE, colorSpace)

        fun makeGray8(
            width: Int,
            height: Int,
            colorSpace: ImageColorSpace = ImageColorSpace.sRGB(),
        ): ImageInfo = make(width, height, ColorType.GRAY_8, AlphaType.OPAQUE, colorSpace)
    }
}
