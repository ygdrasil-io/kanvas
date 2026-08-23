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

    /**
     * Returns the minimum row stride without narrowing to an allocation-sized
     * [Int]. Callers that own an [Int]-sized buffer must check its range before
     * allocation.
     */
    fun minRowBytesLong(): Long = Math.multiplyExact(width.toLong(), bytesPerPixel().toLong())

    /**
     * Returns the minimum row stride for APIs whose stride is an [Int].
     *
     * @throws IllegalArgumentException when the stride cannot be represented
     * by those APIs.
     */
    fun minRowBytes(): Int {
        val rowBytes = minRowBytesLong()
        require(rowBytes <= Int.MAX_VALUE.toLong()) {
            "minimum row bytes exceed Int range: $rowBytes"
        }
        return rowBytes.toInt()
    }

    /** Computes the checked byte size of a strided image. */
    fun computeByteSize(rowBytes: Long): Long {
        if (isEmpty()) return 0L
        val minRowBytes = minRowBytesLong()
        require(rowBytes >= minRowBytes) {
            "rowBytes=$rowBytes < minRowBytes=$minRowBytes"
        }
        return Math.addExact(
            Math.multiplyExact((height - 1).toLong(), rowBytes),
            minRowBytes,
        )
    }

    /** Returns null rather than throwing when [rowBytes] or the total overflows. */
    fun computeByteSizeOrNull(rowBytes: Long): Long? = try {
        computeByteSize(rowBytes)
    } catch (_: ArithmeticException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

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
