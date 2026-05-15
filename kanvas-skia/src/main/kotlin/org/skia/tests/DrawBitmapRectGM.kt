package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkColor4f
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkMaskFilter
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSurfaces
import org.skia.foundation.SkTileMode
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/drawbitmaprect.cpp::DrawBitmapRectGM` (four GM
 * variants — `drawbitmaprect`, `drawbitmaprect-subset`,
 * `drawbitmaprect-imagerect`, `drawbitmaprect-imagerect-subset`).
 *
 * Builds a 2048×2048 radial-gradient bitmap and draws successively
 * smaller `srcRect → 64×64 dstRect` slices through one of four
 * "DrawRectRectProc" implementations :
 *
 *  - **bitmapproc** (`drawbitmaprect`) — `drawImageRect(image, src, dst)`.
 *  - **bitmapsubsetproc** (`-subset`) — when `srcRect` is contained in
 *    the bitmap, [SkBitmap.extractSubset] + draw at `dstRect`.
 *  - **imageproc** (`-imagerect`) — same as bitmapproc but via the
 *    "image-only" path (no bitmap argument).
 *  - **imagesubsetproc** (`-imagerect-subset`) — when `srcRect` is
 *    contained in the image, [SkImage.makeSubset] + draw at `dstRect`.
 *
 * Bottom-right corner exercises a 5×5 chessboard via a 3×3 subset blurred
 * by a [SkMaskFilter.MakeBlur] kNormal filter (mask-filter draw path).
 */
public class DrawBitmapRectGM(
    private val variant: Variant,
) : GM() {

    public enum class Variant(public val suffix: String?) {
        BITMAP(null),
        BITMAP_SUBSET("-subset"),
        IMAGE("-imagerect"),
        IMAGE_SUBSET("-imagerect-subset"),
    }

    override fun getName(): String =
        "drawbitmaprect" + (variant.suffix ?: "")
    override fun getISize(): SkISize = SkISize.Make(gSize, gSize)

    private lateinit var image: SkImage
    private lateinit var bitmap: SkBitmap

    override fun onOnceBeforeDraw() {
        val pair = makeImageAndBitmap(gBmpSize, gBmpSize)
        image = pair.first
        bitmap = pair.second
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val dstRect = SkRect.MakeLTRB(0f, 0f, 64f, 64f)
        // kMaxSrcRectSize = 1 << (log2(gBmpSize) + 2) = 1 << 13 = 8192
        val kMaxSrcRectSize = 1 shl (nextLog2(gBmpSize) + 2)

        val kPadX = 30
        val kPadY = 40
        val alphaPaint = SkPaint().apply { alphaf = 0.125f }
        c.drawImageRect(
            image, SkRect.MakeIWH(gBmpSize, gBmpSize), SkRect.MakeIWH(gSize, gSize),
            SkSamplingOptions.Default, alphaPaint,
        )
        c.translate((kPadX / 2).toFloat(), (kPadY / 2).toFloat())

        val blackPaint = SkPaint().apply {
            color = SK_ColorBLACK
            isAntiAlias = true
        }
        val titleHeight = 24f
        val font = ToolUtils.DefaultPortableFont(titleHeight)
        val title = "Bitmap size: $gBmpSize x $gBmpSize"
        c.drawString(title, 0f, titleHeight, font, blackPaint)

        c.translate(0f, (kPadY / 2).toFloat() + titleHeight)
        var rowCount = 0
        c.save()
        var w = 1
        while (w <= kMaxSrcRectSize) {
            var h = 1
            while (h <= kMaxSrcRectSize) {
                val srcRect = SkIRect.MakeXYWH((gBmpSize - w) / 2, (gBmpSize - h) / 2, w, h)
                runProc(c, srcRect, dstRect, SkSamplingOptions.Default, null)

                val label = "$w x $h"
                blackPaint.isAntiAlias = true
                blackPaint.style = SkPaint.Style.kFill_Style
                font.size = 10f
                val baseline = dstRect.height() + font.size + 3f
                c.drawString(label, 0f, baseline, font, blackPaint)
                blackPaint.style = SkPaint.Style.kStroke_Style
                blackPaint.strokeWidth = 1f
                blackPaint.isAntiAlias = false
                c.drawRect(dstRect, blackPaint)

                c.translate(dstRect.width() + kPadX.toFloat(), 0f)
                rowCount++
                if ((dstRect.width() + kPadX) * rowCount > gSize) {
                    c.restore()
                    c.translate(0f, dstRect.height() + kPadY.toFloat())
                    c.save()
                    rowCount = 0
                }
                h *= 4
            }
            w *= 4
        }

        // Mask-filter chessboard draw — exercises the
        // SkGpuDevice::drawWithMaskFilter path.
        run {
            val maskPaint = SkPaint().apply {
                maskFilter = SkMaskFilter.MakeBlur(SkBlurStyle.kNormal, convertRadiusToSigma(5f))
            }
            val chessBm = makeChessBm(5, 5)
            val savedImage = image
            val savedBitmap = bitmap
            image = chessBm.asImage()
            bitmap = chessBm
            val srcRect = SkIRect.MakeXYWH(1, 1, 3, 3)
            runProc(c, srcRect, dstRect, SkSamplingOptions(SkFilterMode.kLinear), maskPaint)
            image = savedImage
            bitmap = savedBitmap
        }
    }

    private fun runProc(
        canvas: SkCanvas, srcR: SkIRect, dstR: SkRect,
        sampling: SkSamplingOptions, paint: SkPaint?,
    ) {
        when (variant) {
            Variant.BITMAP -> bitmapProc(canvas, srcR, dstR, sampling, paint)
            Variant.BITMAP_SUBSET -> bitmapSubsetProc(canvas, srcR, dstR, sampling, paint)
            Variant.IMAGE -> imageProc(canvas, srcR, dstR, sampling, paint)
            Variant.IMAGE_SUBSET -> imageSubsetProc(canvas, srcR, dstR, sampling, paint)
        }
    }

    private fun bitmapProc(
        canvas: SkCanvas, srcR: SkIRect, dstR: SkRect,
        sampling: SkSamplingOptions, paint: SkPaint?,
    ) {
        canvas.drawImageRect(image, SkRect.Make(srcR), dstR, sampling, paint,
            org.skia.core.SrcRectConstraint.kStrict)
    }

    private fun bitmapSubsetProc(
        canvas: SkCanvas, srcR: SkIRect, dstR: SkRect,
        sampling: SkSamplingOptions, paint: SkPaint?,
    ) {
        val bmBounds = SkIRect.MakeWH(bitmap.width, bitmap.height)
        if (!bmBounds.contains(srcR)) {
            bitmapProc(canvas, srcR, dstR, sampling, paint)
            return
        }
        val subset = SkBitmap.Make(srcR.width(), srcR.height())
        if (bitmap.extractSubset(subset, srcR)) {
            val sub = subset.asImage()
            canvas.drawImageRect(
                sub, SkRect.MakeIWH(sub.width, sub.height), dstR, sampling, paint,
            )
        }
    }

    private fun imageProc(
        canvas: SkCanvas, srcR: SkIRect, dstR: SkRect,
        sampling: SkSamplingOptions, paint: SkPaint?,
    ) {
        canvas.drawImageRect(image, SkRect.Make(srcR), dstR, sampling, paint,
            org.skia.core.SrcRectConstraint.kStrict)
    }

    private fun imageSubsetProc(
        canvas: SkCanvas, srcR: SkIRect, dstR: SkRect,
        sampling: SkSamplingOptions, paint: SkPaint?,
    ) {
        val imgBounds = SkIRect.MakeWH(image.width, image.height)
        if (!imgBounds.contains(srcR)) {
            imageProc(canvas, srcR, dstR, sampling, paint)
            return
        }
        val sub = image.makeSubset(srcR) ?: return
        canvas.drawImageRect(
            sub, SkRect.MakeIWH(sub.width, sub.height), dstR, sampling, paint,
        )
    }

    public companion object {
        private const val gSize = 1024
        private const val gBmpSize = 2048

        /** `drawbitmaprect` — bitmapproc variant. */
        public fun newBitmap(): DrawBitmapRectGM = DrawBitmapRectGM(Variant.BITMAP)
        /** `drawbitmaprect-subset` — bitmapsubsetproc variant. */
        public fun newSubset(): DrawBitmapRectGM = DrawBitmapRectGM(Variant.BITMAP_SUBSET)
        /** `drawbitmaprect-imagerect` — imageproc variant. */
        public fun newImage(): DrawBitmapRectGM = DrawBitmapRectGM(Variant.IMAGE)
        /** `drawbitmaprect-imagerect-subset` — imagesubsetproc variant. */
        public fun newImageSubset(): DrawBitmapRectGM = DrawBitmapRectGM(Variant.IMAGE_SUBSET)

        fun nextLog2(n: Int): Int {
            require(n > 0)
            return 32 - Integer.numberOfLeadingZeros(n - 1)
        }

        fun convertRadiusToSigma(radius: Float): Float =
            if (radius > 0f) 0.57735f * radius + 0.5f else 0f

        fun makeChessBm(w: Int, h: Int): SkBitmap {
            val bm = SkBitmap.Make(w, h)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    bm.setPixel(x, y, if (((x + y) and 1) != 0) SK_ColorWHITE else SK_ColorBLACK)
                }
            }
            return bm
        }

        fun makeImageAndBitmap(w: Int, h: Int): Pair<SkImage, SkBitmap> {
            val info = SkImageInfo.MakeN32Premul(w, h)
            val surface = SkSurfaces.Raster(info)!!
            val c = surface.canvas
            c.clear(0x00000000)
            val wF = w.toFloat()
            val hF = h.toFloat()
            val pt = SkPoint(wF / 2f, hF / 2f)
            var radius = 4f * maxOf(wF, hF)
            val colors = intArrayOf(
                SkColor4f.kRed.toSkColor(), SkColor4f.kYellow.toSkColor(),
                SkColor4f.kGreen.toSkColor(), SkColor4f.kMagenta.toSkColor(),
                SkColor4f.kBlue.toSkColor(), SkColor4f.kCyan.toSkColor(),
                SkColor4f.kRed.toSkColor(),
            )
            val pos = floatArrayOf(0f, 1f / 6f, 2f / 6f, 3f / 6f, 4f / 6f, 5f / 6f, 1f)

            val paint = SkPaint()
            var rect = SkRect.MakeWH(wF, hF)
            var matrix = org.skia.math.SkMatrix.Identity
            for (i in 0 until 4) {
                paint.shader = SkRadialGradient.Make(
                    pt, radius, colors, pos, SkTileMode.kRepeat, matrix,
                )
                c.drawRect(rect, paint)
                rect = SkRect.MakeLTRB(
                    rect.left + wF / 8f,
                    rect.top + hF / 8f,
                    rect.right - wF / 8f,
                    rect.bottom - hF / 8f,
                )
                matrix = matrix.postScale(0.25f, 0.25f)
            }
            val image = surface.makeImageSnapshot()
            val bitmap = SkBitmap.Make(w, h)
            // Mirror upstream's `image->asLegacyBitmap(&tempBM)` snapshot
            // by copying the image pixels into a parallel bitmap.
            for (y in 0 until h) {
                for (x in 0 until w) {
                    bitmap.setPixel(x, y, image.peekPixel(x, y))
                }
            }
            return Pair(image, bitmap)
        }
    }

}
