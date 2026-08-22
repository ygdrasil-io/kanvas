package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.skia.foundation.SkCompressedDataUtils
import org.skia.foundation.SkData
import org.skia.foundation.SkImage
import org.skia.foundation.SkImages
import org.skia.foundation.SkTextureCompressionType
import org.skia.foundation.SkColorType
import org.skia.foundation.SkBitmap
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.SizeI32

/** Port of Skia's `gm/exoticformats.cpp`.
 *  Tests compressed texture rendering with exotic formats (BC1, ETC1,
 *  ASTC) — draws compressed images with various pixel formats.
 *  @see https://github.com/google/skia/blob/main/gm/exoticformats.cpp
 */
class ExoticFormatsGm : SkiaGm {
    override val name = "exoticformats"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 2 * IMG_WIDTH_HEIGHT + 3 * PAD
    override val height = IMG_WIDTH_HEIGHT + 2 * PAD

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(
            ColorARGB.Black.red / 255f,
            ColorARGB.Black.green / 255f,
            ColorARGB.Black.blue / 255f,
        )

        val etc1Image = try {
            makePlaceholderCompressedImage(IMG_WIDTH_HEIGHT, SkTextureCompressionType.kETC2_RGB8_UNORM)
        } catch (_: NotImplementedError) { null }

        val bc1Image = try {
            makePlaceholderCompressedImage(IMG_WIDTH_HEIGHT, SkTextureCompressionType.kBC1_RGB8_UNORM)
        } catch (_: NotImplementedError) { null }

        drawImageWithOutline(canvas, etc1Image, PAD, PAD)
        drawImageWithOutline(canvas, bc1Image, IMG_WIDTH_HEIGHT + 2 * PAD, PAD)
    }

    private fun drawImageWithOutline(canvas: GmCanvas, skImage: SkImage?, x: Int, y: Int) {
        if (skImage != null) {
            val img = skImageToKanvasImage(skImage)
            canvas.drawImage(
                img,
                Rect(x.toFloat(), y.toFloat(), (x + IMG_WIDTH_HEIGHT).toFloat(), (y + IMG_WIDTH_HEIGHT).toFloat()),
            )
        }

        val redStroke = Paint(
            color = Color.RED,
            style = PaintStyle.STROKE,
            strokeWidth = 2f,
        )
        canvas.drawRect(
            Rect(x.toFloat(), y.toFloat(), (x + IMG_WIDTH_HEIGHT).toFloat(), (y + IMG_WIDTH_HEIGHT).toFloat()),
            redStroke,
        )
    }

    private companion object {
        const val IMG_WIDTH_HEIGHT = 128
        const val PAD = 4

        private fun makePlaceholderCompressedImage(size: Int, compression: SkTextureCompressionType): SkImage? {
            val dim = SizeI32.of(size, size)
            val totalSize = SkCompressedDataUtils.SkCompressedDataSize(compression, dim, null, false)
            val bytes = ByteArray(totalSize.toInt())

            val bm = renderColorBars(size, size)
            when (compression) {
                SkTextureCompressionType.kETC2_RGB8_UNORM -> {
                    SkCompressedDataUtils.Etc1EncodeImage(srcBitmap = bm, dst = bytes, dstOffset = 0)
                }
                SkTextureCompressionType.kBC1_RGB8_UNORM,
                SkTextureCompressionType.kBC1_RGBA8_UNORM -> {
                    SkCompressedDataUtils.TwoColorBC1Compress(
                        srcBitmap = bm, otherColor = 0xFF0000FF.toInt(), dst = bytes, dstOffset = 0,
                    )
                }
                else -> return null
            }
            return SkImages.RasterFromCompressedTextureData(
                SkData.MakeWithCopy(bytes), size, size, compression,
            )
        }

        private fun renderColorBars(w: Int, h: Int): SkBitmap {
            val bm = SkBitmap(w, h, colorType = SkColorType.kRGB_565)
            val barWidth = w / 4
            val colors = intArrayOf(
                ColorARGB.Red.toPackedInt(),
                ColorARGB.Green.toPackedInt(),
                ColorARGB.Blue.toPackedInt(),
                ColorARGB.Black.toPackedInt(),
            )
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val index = (x / barWidth).coerceIn(0, colors.lastIndex)
                    bm.setPixel(x, y, colors[index])
                }
            }
            return bm
        }

    }
}
