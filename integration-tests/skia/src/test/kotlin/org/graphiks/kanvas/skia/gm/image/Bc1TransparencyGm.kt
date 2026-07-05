package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
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
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkISize
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE

class Bc1TransparencyGm : SkiaGm {
    override val name = "bc1_transparency"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = kImgWidth + 2 * kPad
    override val height = 2 * kImgHeight + 3 * kPad

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(
            SkColorGetR(SK_ColorGREEN) / 255f,
            SkColorGetG(SK_ColorGREEN) / 255f,
            SkColorGetB(SK_ColorGREEN) / 255f,
        )

        val bc1Data = makeCompressedData()
        val rgbImage = SkImages.RasterFromCompressedTextureData(
            bc1Data, kImgWidth, kImgHeight, SkTextureCompressionType.kBC1_RGB8_UNORM,
        )
        val rgbaImage = SkImages.RasterFromCompressedTextureData(
            bc1Data, kImgWidth, kImgHeight, SkTextureCompressionType.kBC1_RGBA8_UNORM,
        )

        drawImage(canvas, rgbImage, kPad, kPad)
        drawImage(canvas, rgbaImage, kPad, 2 * kPad + kImgHeight)
    }

    private fun drawImage(canvas: GmCanvas, skImage: SkImage?, x: Int, y: Int) {
        if (skImage != null) {
            val img = skImageToKanvasImage(skImage)
            canvas.drawImage(img, Rect(x.toFloat(), y.toFloat(), (x + kImgWidth).toFloat(), (y + kImgHeight).toFloat()))
        }

        val r = Rect(
            (x - 1).toFloat(), (y - 1).toFloat(),
            (x + kImgWidth + 1).toFloat(), (y + kImgHeight + 1).toFloat(),
        )
        val redStroke = Paint(
            color = Color.fromRGBA(
                SkColorGetR(SK_ColorRED) / 255f,
                SkColorGetG(SK_ColorRED) / 255f,
                SkColorGetB(SK_ColorRED) / 255f,
            ),
            style = PaintStyle.STROKE,
            strokeWidth = 2f,
        )
        canvas.drawRect(r, redStroke)
    }

    private fun makeCompressedData(): SkData {
        val dim = SkISize.Make(kImgWidth, kImgHeight)
        val totalSize = SkCompressedDataUtils.SkCompressedDataSize(
            SkTextureCompressionType.kBC1_RGB8_UNORM, dim, null, false,
        )

        val numXBlocks = num4x4Blocks(kImgWidth)
        val numYBlocks = num4x4Blocks(kImgHeight)
        val bytes = ByteArray(totalSize.toInt())

        val transBlock = createBC1Block(transparent = true)
        val opaqueBlock = createBC1Block(transparent = false)

        for (y in 0 until numYBlocks) {
            for (x in 0 until numXBlocks) {
                val block = if (y < numYBlocks / 2) transBlock else opaqueBlock
                val off = (y * numXBlocks + x) * BC1_BLOCK_SIZE
                writeBlock(bytes, off, block)
            }
        }

        return SkData.MakeWithCopy(bytes)
    }

    private data class BC1Block(val color0: Int, val color1: Int, val indices: Int)

    private fun createBC1Block(transparent: Boolean): BC1Block {
        val byte: Int
        val color0: Int
        val color1: Int
        if (transparent) {
            color0 = to565(SK_ColorBLACK)
            color1 = to565(SK_ColorWHITE)
            byte = (0x0 shl 0) or (0x2 shl 2) or (0x3 shl 4) or (0x1 shl 6)
        } else {
            color0 = to565(SK_ColorWHITE)
            color1 = to565(SK_ColorBLACK)
            byte = (0x1 shl 0) or (0x3 shl 2) or (0x2 shl 4) or (0x0 shl 6)
        }
        val indices = (byte shl 24) or (byte shl 16) or (byte shl 8) or byte
        return BC1Block(color0, color1, indices)
    }

    private fun writeBlock(dst: ByteArray, off: Int, block: BC1Block) {
        dst[off + 0] = (block.color0 and 0xFF).toByte()
        dst[off + 1] = ((block.color0 ushr 8) and 0xFF).toByte()
        dst[off + 2] = (block.color1 and 0xFF).toByte()
        dst[off + 3] = ((block.color1 ushr 8) and 0xFF).toByte()
        dst[off + 4] = (block.indices and 0xFF).toByte()
        dst[off + 5] = ((block.indices ushr 8) and 0xFF).toByte()
        dst[off + 6] = ((block.indices ushr 16) and 0xFF).toByte()
        dst[off + 7] = ((block.indices ushr 24) and 0xFF).toByte()
    }

    private companion object {
        const val kImgWidth = 16
        const val kImgHeight = 8
        const val kPad = 4
        const val BC1_BLOCK_SIZE = 8

        private fun num4x4Blocks(size: Int): Int = ((size + 3) and 3.inv()) shr 2

        private fun to565(col: Int): Int {
            val r5 = (SkColorGetR(col) * 31 + 127) / 255
            val g6 = (SkColorGetG(col) * 63 + 127) / 255
            val b5 = (SkColorGetB(col) * 31 + 127) / 255
            return ((r5 and 0x1F) shl 11) or ((g6 and 0x3F) shl 5) or (b5 and 0x1F)
        }
    }
}

internal fun skImageToKanvasImage(skImage: SkImage): Image {
    val argb = skImage.pixels
    val w = skImage.width
    val h = skImage.height
    val rgba = ByteArray(w * h * 4)
    var di = 0
    for (pixel in argb) {
        val a = (pixel ushr 24) and 0xFF
        val r = (pixel ushr 16) and 0xFF
        val g = (pixel ushr 8) and 0xFF
        val b = pixel and 0xFF
        rgba[di] = r.toByte()
        rgba[di + 1] = g.toByte()
        rgba[di + 2] = b.toByte()
        rgba[di + 3] = a.toByte()
        di += 4
    }
    return Image.fromPixels(w, h, rgba)
}
