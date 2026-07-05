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
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkCompressedDataUtils
import org.skia.foundation.SkData
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkImages
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathDirection
import org.skia.foundation.SkPathFillType
import org.skia.foundation.SkTextureCompressionType
import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorCYAN
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorMAGENTA
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorTRANSPARENT
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SK_ColorYELLOW
import org.graphiks.math.SK_ScalarPI
import org.skia.foundation.SkAlphaType
import org.graphiks.math.SkColor
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkScalarCos
import org.graphiks.math.SkScalarSin
import org.graphiks.math.SkVector
import kotlin.math.max
import kotlin.math.min

class CompressedTexturesGm : SkiaGm {
    override val name = "compressed_textures"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 2 * kCellWidth + 3 * kPad
    override val height = 2 * kBaseTexHeight + 3 * kPad

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(
            0xCC / 255f, 0xCC / 255f, 0xCC / 255f,
        )

        val dim = SkISize.Make(kBaseTexWidth, kBaseTexHeight)

        val opaqueBC1 = try {
            makeCompressedImage(dim, SkColorType.kRGBA_8888, opaque = true, SkTextureCompressionType.kBC1_RGB8_UNORM)
        } catch (_: NotImplementedError) { null }

        val transparentBC1 = try {
            makeCompressedImage(dim, SkColorType.kRGBA_8888, opaque = false, SkTextureCompressionType.kBC1_RGBA8_UNORM)
        } catch (_: NotImplementedError) { null }

        val etc2Image = try {
            makeCompressedImage(dim, SkColorType.kRGB_565, opaque = true, SkTextureCompressionType.kETC2_RGB8_UNORM)
        } catch (_: NotImplementedError) { null }

        drawCell(canvas, etc2Image, kPad, kPad)
        drawCell(canvas, opaqueBC1, 2 * kPad + kCellWidth, kPad)
        drawCell(canvas, transparentBC1, 2 * kPad + kCellWidth, 2 * kPad + kBaseTexHeight)
    }

    private fun drawCell(canvas: GmCanvas, image: SkImage?, x: Int, y: Int) {
        val r = Rect(x.toFloat(), y.toFloat(), (x + kBaseTexWidth).toFloat(), (y + kBaseTexHeight).toFloat())

        if (image != null) {
            val img = skImageToKanvasImage(image)
            canvas.drawImage(img, r)
        }

        val redStroke = Paint(
            color = Color.RED,
            style = PaintStyle.STROKE,
            strokeWidth = 1f,
        )
        canvas.drawRect(r, redStroke)
    }

    private companion object {
        const val kPad = 8
        const val kBaseTexWidth = 64
        const val kBaseTexHeight = 64
        const val kCellWidth = (1.5f * kBaseTexWidth).toInt()

        private fun makeCompressedImage(
            dimensions: SkISize,
            colorType: SkColorType,
            opaque: Boolean,
            compression: SkTextureCompressionType,
        ): SkImage? {
            val totalSize = SkCompressedDataUtils.SkCompressedDataSize(
                compression, dimensions, mipMapOffsetsAndSizes = null, mipMapped = true,
            )

            val bytes = ByteArray(totalSize.toInt())
            val numMipLevels = computeLevelCount(dimensions) + 1

            val kColors = intArrayOf(
                SK_ColorRED, SK_ColorGREEN, SK_ColorBLUE,
                SK_ColorCYAN, SK_ColorMAGENTA, SK_ColorYELLOW, SK_ColorWHITE,
            )

            var offset = 0L
            var levelDims = dimensions
            for (i in 0 until numMipLevels) {
                val levelSize = SkCompressedDataUtils.SkCompressedDataSize(
                    compression, levelDims, mipMapOffsetsAndSizes = null, mipMapped = false,
                )

                val bm = renderLevel(levelDims, kColors[i % 7], colorType, opaque)
                when (compression) {
                    SkTextureCompressionType.kETC2_RGB8_UNORM -> {
                        check(bm.colorType == SkColorType.kRGB_565) { "ETC2 requires kRGB_565 source" }
                        check(opaque) { "ETC2 requires opaque source" }
                        SkCompressedDataUtils.Etc1EncodeImage(
                            srcBitmap = bm, dst = bytes, dstOffset = offset.toInt(),
                        )
                    }
                    SkTextureCompressionType.kBC1_RGB8_UNORM,
                    SkTextureCompressionType.kBC1_RGBA8_UNORM -> {
                        SkCompressedDataUtils.TwoColorBC1Compress(
                            srcBitmap = bm, otherColor = kColors[i % 7],
                            dst = bytes, dstOffset = offset.toInt(),
                        )
                    }
                    SkTextureCompressionType.kNone -> error("compression == kNone")
                }

                offset += levelSize
                levelDims = SkISize.Make(
                    max(1, levelDims.width / 2), max(1, levelDims.height / 2),
                )
            }

            return SkImages.RasterFromCompressedTextureData(
                SkData.MakeWithCopy(bytes), dimensions.width, dimensions.height, compression,
            )
        }

        private fun renderLevel(dimensions: SkISize, color: SkColor, colorType: SkColorType, opaque: Boolean): SkBitmap {
            val path = makeGear(dimensions, numTeeth = 9)
            val ii = SkImageInfo.Make(
                dimensions.width, dimensions.height, colorType,
                if (opaque) SkAlphaType.kOpaque else SkAlphaType.kPremul,
            )
            val bm = SkBitmap.allocPixels(ii)
            bm.eraseColor(if (opaque) SK_ColorBLACK else SK_ColorTRANSPARENT)
            val c = SkCanvas(bm)
            val paint = SkPaint().apply {
                this.color = color or 0xFF000000.toInt()
                this.isAntiAlias = false
            }
            c.translate(dimensions.width / 2.0f, dimensions.height / 2.0f)
            c.drawPath(path, paint)
            return bm
        }

        private fun makeGear(dimensions: SkISize, numTeeth: Int): SkPath {
            val outerRad = SkVector(dimensions.width / 2.0f, dimensions.height / 2.0f)
            val innerRad = SkVector(dimensions.width / 2.5f, dimensions.height / 2.5f)
            val kAnglePerTooth: Float = 2.0f * SK_ScalarPI / (3 * numTeeth)
            var angle = 0.0f
            val tmp = SkPathBuilder(SkPathFillType.kWinding)
            tmp.moveTo(genPt(angle, outerRad).fX, genPt(angle, outerRad).fY)
            for (i in 0 until numTeeth) {
                val p1 = genPt(angle + kAnglePerTooth, outerRad)
                val p2 = genPt(angle + 1.5f * kAnglePerTooth, innerRad)
                val p3 = genPt(angle + 2.5f * kAnglePerTooth, innerRad)
                val p4 = genPt(angle + 3.0f * kAnglePerTooth, outerRad)
                tmp.lineTo(p1.fX, p1.fY); tmp.lineTo(p2.fX, p2.fY)
                tmp.lineTo(p3.fX, p3.fY); tmp.lineTo(p4.fX, p4.fY)
                angle += 3 * kAnglePerTooth
            }
            tmp.close()
            val fInnerRad = 0.1f * min(dimensions.width, dimensions.height)
            if (fInnerRad > 0.5f) {
                tmp.addCircle(0.0f, 0.0f, fInnerRad, SkPathDirection.kCCW)
            }
            return tmp.detach()
        }

        private fun genPt(angle: Float, scale: SkVector): SkPoint {
            val s = SkScalarSin(angle); val c = SkScalarCos(angle)
            return SkPoint(scale.fX * c, scale.fY * s)
        }

        private fun computeLevelCount(dim: SkISize): Int {
            var n = max(dim.width, dim.height); var levels = 0
            while (n > 1) { n = n shr 1; levels++ }
            return levels
        }
    }
}
