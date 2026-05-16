package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.core.SrcRectConstraint
import org.skia.math.SK_ColorBLUE
import org.skia.math.SK_ColorCYAN
import org.skia.math.SK_ColorGREEN
import org.skia.math.SK_ColorMAGENTA
import org.skia.math.SK_ColorRED
import org.skia.math.SK_ColorYELLOW
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.tools.SkRandom

/**
 * Port of Skia's `gm/drawminibitmaprect.cpp::DrawMiniBitmapRectGM`
 * (`DEF_GM` × 2 — AA / non-AA variants, names `drawminibitmaprect`
 * and `drawminibitmaprect_aa`, 1024 × 1024).
 *
 * Exercises GPU draw-op combining for `drawImageRect` by stamping
 * 64×64 destination rects sampled from a 2048×2048 radial-gradient
 * atlas across a tiled grid, randomly nudging each by a small
 * rotation in `[-10°, 10°]` per draw. The destination rect is
 * fixed ; only the src rect changes (width / height grow by 3×
 * per cell) and the canvas CTM is rotated.
 *
 * Implemented for the raster path with `SrcRectConstraint.kFast`
 * (matches upstream's `kFast_SrcRectConstraint` — the rasterizer
 * may sample texels outside the src rect during bilinear blur).
 *
 * Two `DEF_GM` entries upstream — we factor `makebm()` once into
 * a shared object, then expose [DrawMiniBitmapRectGM] (non-AA,
 * `drawminibitmaprect`) and [DrawMiniBitmapRectAaGM] (AA,
 * `drawminibitmaprect_aa`).
 */
public class DrawMiniBitmapRectGM internal constructor(
    private val fAA: Boolean,
) : GM() {

    public constructor() : this(false)

    override fun getName(): String =
        if (fAA) "drawminibitmaprect_aa" else "drawminibitmaprect"

    override fun getISize(): SkISize = SkISize.Make(GSIZE, GSIZE)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val image = sharedImage()

        val dstRect = SkRect.MakeXYWH(0f, 0f, 64f, 64f)
        // SkNextLog2(2048) == 11 → 1 << (11 + 2) = 8192.
        val kMaxSrcRectSize = 1 shl (nextLog2(GSURFACE_SIZE) + 2)

        val kPadX = 30
        val kPadY = 40

        var rowCount = 0
        c.translate(kPadX.toFloat(), kPadY.toFloat())
        c.save()
        val random = SkRandom()

        val paint = SkPaint().apply { isAntiAlias = fAA }
        var w = 1
        while (w <= kMaxSrcRectSize) {
            var h = 1
            while (h <= kMaxSrcRectSize) {
                val srcRect = SkRect.MakeXYWH(
                    ((GSURFACE_SIZE - w) / 2).toFloat(),
                    ((GSURFACE_SIZE - h) / 2).toFloat(),
                    w.toFloat(),
                    h.toFloat(),
                )
                c.save()
                when ((random.nextU().toLong() and 0xFFFFFFFFL) % 3) {
                    0L -> c.rotate(random.nextF() * 10f)
                    1L -> c.rotate(-random.nextF() * 10f)
                    else -> {} // rect stays rect
                }
                c.drawImageRect(
                    image,
                    srcRect,
                    dstRect,
                    SkSamplingOptions.Default,
                    paint,
                    SrcRectConstraint.kFast,
                )
                c.restore()

                c.translate(dstRect.width() + kPadX, 0f)
                rowCount++
                if ((dstRect.width() + 2 * kPadX) * rowCount > GSIZE) {
                    c.restore()
                    c.translate(0f, dstRect.height() + kPadY)
                    c.save()
                    rowCount = 0
                }
                h *= 3
            }
            w *= 3
        }
        c.restore()
    }

    private companion object {
        private const val GSIZE = 1024
        private const val GSURFACE_SIZE = 2048

        // SkNextLog2 — Skia's `1 + (high-bit-index of (n-1))`. For n = 2048
        // → 1 + 10 = 11.
        private fun nextLog2(n: Int): Int {
            require(n > 0)
            var v = n - 1
            var log = 0
            while (v > 0) { v = v ushr 1; log++ }
            return log
        }

        // Lazily built and cached so the AA and non-AA GMs share the
        // raster. Building the 2048² gradient texture is the slow
        // part of the GM, and the four overlapping radial draws are
        // deterministic — caching it across both tests cuts wall
        // time by 50% with no behaviour change.
        @Volatile private var cachedImage: SkImage? = null

        fun sharedImage(): SkImage {
            cachedImage?.let { return it }
            synchronized(this) {
                cachedImage?.let { return it }
                val img = makebm(GSURFACE_SIZE, GSURFACE_SIZE)
                cachedImage = img
                return img
            }
        }

        private fun makebm(w: Int, h: Int): SkImage {
            val info = SkImageInfo.MakeN32Premul(w, h)
            val surface = SkSurface.MakeRaster(info)
            val canvas = surface.canvas

            val wScalar = w.toFloat()
            val hScalar = h.toFloat()
            val pt = SkPoint(wScalar / 2f, hScalar / 2f)
            val radius = 4f * maxOf(wScalar, hScalar)

            val colors = intArrayOf(
                SK_ColorRED, SK_ColorYELLOW, SK_ColorGREEN,
                SK_ColorMAGENTA, SK_ColorBLUE, SK_ColorCYAN, SK_ColorRED,
            )
            val pos = floatArrayOf(
                0f, 1f / 6f, 2f / 6f, 3f / 6f, 4f / 6f, 5f / 6f, 1f,
            )

            val paint = SkPaint()
            var rect = SkRect.MakeWH(wScalar, hScalar)
            var mat = SkMatrix.Identity
            for (i in 0 until 4) {
                paint.shader = SkRadialGradient.Make(
                    center = pt,
                    radius = radius,
                    colors = colors,
                    positions = pos,
                    tileMode = SkTileMode.kRepeat,
                    localMatrix = mat,
                )
                canvas.drawRect(rect, paint)
                rect = rect.makeInset(wScalar / 8f, hScalar / 8f)
                mat = mat.postScale(1f / 4f, 1f / 4f)
            }
            return surface.makeImageSnapshot()
        }
    }
}

/** AA variant — `drawminibitmaprect_aa`. */
public class DrawMiniBitmapRectAaGM : GM() {
    private val inner = DrawMiniBitmapRectGM(fAA = true)
    override fun getName(): String = inner.name()
    override fun getISize(): SkISize = inner.size()
    override fun onDraw(canvas: SkCanvas?) { inner.draw(canvas) }
}
