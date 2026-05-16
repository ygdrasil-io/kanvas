package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorTRANSPARENT
import org.skia.foundation.SkBitmap
import org.skia.math.SkColor4f
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkPoint
import kotlin.math.sqrt

/**
 * Port of Skia's `gm/tablecolorfilter.cpp::TableColorFilterGM`
 * (`tablecolorfilter`, 700 × 1650).
 *
 * Visual regression for [SkColorFilters.Table] and [SkColorFilters.TableARGB]
 * composed via [SkImageFilters.ColorFilter] chains. For each of two
 * 120 × 120 source bitmaps (a 7-stop linear gradient ; a 3-stop radial
 * gradient on a transparent BG), the test renders :
 *
 *  - **First row** : the original image followed by 4 copies, each
 *    pre-filtered by one of the 4 table color filters (3 single-channel
 *    plus 1 TableARGB).
 *  - **Following 4 rows** : every `colorFilter1 → colorFilter2`
 *    combination (4×4 = 16 cells per bitmap, but stride is `4` per row
 *    starting at column 1 — the loop draws 4 cells per row from
 *    column 0 up to column 3).
 *
 * Each follow-up draws via `paint.setImageFilter(Compose(cfA, cfB))`
 * built as `SkImageFilters.ColorFilter(cfA, SkImageFilters.ColorFilter(cfB))`.
 * The legacy [SkBitmapDevice.drawRect] path doesn't honour
 * `paint.imageFilter` directly, so we wrap each filtered `drawImage`
 * in a `saveLayer(paint{imageFilter=F})` + plain `drawImage` +
 * `restore()` dance — same trick as [Crbug905548GM]. Output is
 * pixel-equivalent because Skia internally synthesises the same layer
 * when its `drawImage` sees an image-filter paint.
 *
 * Original C++ : see `gm/tablecolorfilter.cpp::TableColorFilterGM` —
 * the structural shape is preserved verbatim, only the layer-dance
 * substitution differs.
 */
public class TableColorFilterGM : GM() {

    override fun getName(): String = "tablecolorfilter"
    override fun getISize(): SkISize = SkISize.Make(700, 1650)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.drawColor(0xFFDDDDDD.toInt())
        c.translate(20f, 20f)

        // Note : index 0 is "null" (no filter) — the leftmost cell of
        // row 0 draws the bare source image.
        val filterMakers: Array<() -> SkColorFilter?> = arrayOf(
            ::makeNullCF,
            ::makeCF0,
            ::makeCF1,
            ::makeCF2,
            ::makeCF3,
        )
        val bitmapMakers: Array<(SkBitmap) -> Unit> = arrayOf(::makeBm0, ::makeBm1)

        var x: Float
        var y = 0f
        for (bitmapMaker in bitmapMakers) {
            val bm = SkBitmap(120, 120).also { bitmapMaker(it) }
            val xOffset = (bm.width * 9 / 8).toFloat()
            val yOffset = (bm.height * 9 / 8).toFloat()

            // First-row drawing : original then each single-filter copy.
            x = 0f
            c.drawImage(bm.asImage(), x, y, SkSamplingOptions.Default, null)
            for (i in 1 until filterMakers.size) {
                x += xOffset
                val paint = SkPaint().apply { colorFilter = filterMakers[i]() }
                c.drawImage(bm.asImage(), x, y, SkSamplingOptions.Default, paint)
            }

            // Compose-rows : 4 rows, each draws 4 cells of
            // `Compose(filter1, filter2)` for `j in 1..4`.
            for (i in 0 until filterMakers.size) {
                val cf1 = filterMakers[i]()
                val ifLeaf: SkImageFilter? = cf1?.let { SkImageFilters.ColorFilter(it, null) }
                y += yOffset
                x = 0f
                for (j in 1 until filterMakers.size) {
                    val cf2 = filterMakers[j]()
                    val ifChain: SkImageFilter? =
                        if (cf2 != null) SkImageFilters.ColorFilter(cf2, ifLeaf) else ifLeaf
                    val paint = SkPaint().apply { imageFilter = ifChain }
                    drawImageWithImageFilter(c, bm, x, y, paint)
                    x += xOffset
                }
            }
            y += yOffset
        }
    }

    /**
     * `paint.setImageFilter(F); canvas.drawImage(bm, x, y, paint)` —
     * substituted by `saveLayer(paint{imageFilter=F})` + plain
     * `drawImage` + `restore()`. Matches the trick documented on
     * [Crbug905548GM] for filtered rect / image draws.
     *
     * The layer rect is the destination cell, so the filter samples
     * the bm pixels in the right region. If [paint.imageFilter] is
     * null, we short-circuit to the direct `drawImage` path.
     */
    private fun drawImageWithImageFilter(
        canvas: SkCanvas,
        bm: SkBitmap,
        x: Float,
        y: Float,
        paint: SkPaint,
    ) {
        if (paint.imageFilter == null) {
            canvas.drawImage(bm.asImage(), x, y, SkSamplingOptions.Default, null)
            return
        }
        val layerBounds = org.skia.math.SkRect.MakeXYWH(x, y, bm.width.toFloat(), bm.height.toFloat())
        canvas.saveLayer(layerBounds, paint)
        canvas.drawImage(bm.asImage(), x, y, SkSamplingOptions.Default, null)
        canvas.restore()
    }

    private companion object {
        // ─── Bitmaps ───────────────────────────────────────────────

        fun makeShader0(w: Int, h: Int): org.skia.foundation.SkShader {
            // 7-stop linear gradient — top-left → bottom-right.
            val colors = intArrayOf(
                SkColor4f.kBlack.toSkColor(),
                SkColor4f.kGreen.toSkColor(),
                SkColor4f.kCyan.toSkColor(),
                SkColor4f.kRed.toSkColor(),
                SK_ColorTRANSPARENT,
                SkColor4f.kBlue.toSkColor(),
                SkColor4f.kWhite.toSkColor(),
            )
            return SkLinearGradient.Make(
                SkPoint(0f, 0f),
                SkPoint(w.toFloat(), h.toFloat()),
                colors, null,
                SkTileMode.kClamp,
            )
        }

        fun makeBm0(bm: SkBitmap) {
            bm.eraseColor(SK_ColorTRANSPARENT)
            val canvas = SkCanvas(bm)
            val paint = SkPaint().apply { shader = makeShader0(bm.width, bm.height) }
            canvas.drawPaint(paint)
        }

        fun makeShader1(w: Int, h: Int): org.skia.foundation.SkShader {
            val cx = w / 2f
            val cy = h / 2f
            val colors = intArrayOf(
                SkColor4f.kRed.toSkColor(),
                SkColor4f.kGreen.toSkColor(),
                SkColor4f.kBlue.toSkColor(),
            )
            return SkRadialGradient.Make(
                SkPoint(cx, cy),
                cx,
                colors, null,
                SkTileMode.kClamp,
            )
        }

        fun makeBm1(bm: SkBitmap) {
            bm.eraseColor(SK_ColorTRANSPARENT)
            val canvas = SkCanvas(bm)
            val cx = bm.width / 2f
            val paint = SkPaint().apply {
                shader = makeShader1(bm.width, bm.height)
                isAntiAlias = true
            }
            canvas.drawCircle(cx, bm.height / 2f, cx, paint)
        }

        // ─── Tables ────────────────────────────────────────────────

        fun makeTable0(): ByteArray {
            val t = ByteArray(256)
            for (i in 0 until 256) {
                val n = i shr 5
                t[i] = ((n shl 5) or (n shl 2) or (n shr 1)).toByte()
            }
            return t
        }

        fun makeTable1(): ByteArray {
            val t = ByteArray(256)
            for (i in 0 until 256) {
                t[i] = (i * i / 255).toByte()
            }
            return t
        }

        fun makeTable2(): ByteArray {
            val t = ByteArray(256)
            for (i in 0 until 256) {
                val fi = i / 255f
                t[i] = (sqrt(fi) * 255f).toInt().toByte()
            }
            return t
        }

        // ─── Filter makers ─────────────────────────────────────────

        fun makeNullCF(): SkColorFilter? = null
        fun makeCF0(): SkColorFilter = SkColorFilters.Table(makeTable0())
        fun makeCF1(): SkColorFilter = SkColorFilters.Table(makeTable1())
        fun makeCF2(): SkColorFilter = SkColorFilters.Table(makeTable2())
        fun makeCF3(): SkColorFilter = SkColorFilters.TableARGB(
            a = null, r = makeTable0(), g = makeTable1(), b = makeTable2(),
        )
    }
}
