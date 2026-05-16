package org.skia.pathops

import org.skia.core.SkBitmapDevice
import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLACK
import org.skia.math.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * D1.4 pixel-parity oracle for [SkPathOps.Op].
 *
 * **Strategy** : independent verification of `Op(A, B, op) ≅ result`
 * without leaning on a separate algorithmic implementation. The oracle
 * rasterises [pathA] and [pathB] **independently** to per-pixel
 * coverage masks, then computes the *expected* set-op result by
 * applying [op] pixel-wise (`AND` for intersect, `OR` for union,
 * `A AND NOT B` for difference, `XOR` for symmetric difference). The
 * engine's [SkPathOps.Op] result is rasterised to the same canvas
 * size and the two bitmaps are compared via a 2×2 block-diff count
 * lifted from upstream's
 * [`pathsDrawTheSame`](https://github.com/google/skia/blob/main/tests/PathOpsExtendedTest.cpp).
 *
 * **Why pixel-wise op instead of [SkRegion]** : we don't ship
 * `SkRegion::getBoundaryPath` (the upstream oracle hands the region
 * back to a `SkPath` for rasterising). Pixel-set-op is functionally
 * equivalent for binary coverage (AA off) and avoids the region →
 * path round-trip we'd otherwise have to port.
 *
 * **AA off** — every rasterise here pins `isAntiAlias = false` so the
 * mask is binary `{ black, white }` and pixel set-op is exact. AA
 * differences between the two rasterise paths are not the bug we
 * want to surface.
 *
 * **Scaling** : the oracle scales `A ∪ B` to fit
 * `kBitWidth × kBitHeight` (mirrors upstream's `scaleMatrix`) so a
 * tiny degenerate cubic exposes the same number of pixel rows as a
 * 1000-pixel rect. Constants chosen to match upstream defaults
 * (kBitWidth = kBitHeight = 64) for cross-check.
 *
 * **Error budget** : up to [MAX_2X2_ERRORS] 2×2 blocks may differ
 * before we call it a divergence. Mirrors upstream's `MAX_ERRORS = 8`
 * tolerance — absorbs single-pixel rounding noise without hiding
 * shape-level bugs.
 */
internal object PathOpsPixelOracle {

    /** Oracle bitmap dimensions — match upstream's `kBitWidth/Height`. */
    private const val K_BIT_WIDTH = 64
    private const val K_BIT_HEIGHT = 64

    /** Tolerance lifted from upstream's `comparePaths` MAX_ERRORS. */
    private const val MAX_2X2_ERRORS = 8

    enum class PixelOutcome {
        /** Engine's result rasterises within tolerance of the oracle. */
        PIXEL_MATCH,
        /** Engine's result diverges by more than [MAX_2X2_ERRORS] 2×2 blocks. */
        PIXEL_DIVERGE,
        /** Both inputs degenerate to empty under scaling — oracle skips. */
        DEGENERATE,
    }

    /** Compare engine [result] against the pixel-wise oracle for `(A, B, op)`. */
    fun compare(
        pathA: SkPath,
        pathB: SkPath,
        op: SkPathOp,
        result: SkPath,
    ): PixelOutcome {
        val unionBounds = unionBounds(pathA, pathB)
            ?: return PixelOutcome.DEGENERATE
        val scale = scaleMatrix(unionBounds)
        val scaledA = pathA.makeTransform(scale)
        val scaledB = pathB.makeTransform(scale)
        val scaledResult = result.makeTransform(scale)

        val bmA = rasterise(scaledA)
        val bmB = rasterise(scaledB)
        val bmExpected = applyPixelOp(bmA, bmB, op)
        val bmActual = rasterise(scaledResult)

        return if (twoByTwoErrors(bmExpected, bmActual) <= MAX_2X2_ERRORS) {
            PixelOutcome.PIXEL_MATCH
        } else {
            PixelOutcome.PIXEL_DIVERGE
        }
    }

    /**
     * Compute the union of `pathA` and `pathB`'s tight bounds, or
     * `null` if both are empty. An empty union means there's nothing
     * to draw — the oracle returns DEGENERATE so the harness can
     * count it separately rather than assert pixel parity on a
     * degenerate fixture.
     */
    private fun unionBounds(pathA: SkPath, pathB: SkPath): SkRect? {
        val a = pathA.computeBounds()
        val b = pathB.computeBounds()
        if (a.isEmpty && b.isEmpty) return null
        if (a.isEmpty) return b
        if (b.isEmpty) return a
        return SkRect.MakeLTRB(
            kotlin.math.min(a.left, b.left),
            kotlin.math.min(a.top, b.top),
            kotlin.math.max(a.right, b.right),
            kotlin.math.max(a.bottom, b.bottom),
        )
    }

    /**
     * Build a scale + translate matrix that maps [bounds] into the
     * `[1, K_BIT_WIDTH - 1] × [1, K_BIT_HEIGHT - 1]` interior of the
     * oracle bitmap. The 1-pixel margin protects rasteriser edge
     * cases (sub-pixel coords clipped at the bitmap boundary).
     *
     * Mirrors upstream's `scaleMatrix` algorithm — width / height
     * floors at 4 to avoid divide-by-zero for very thin paths.
     */
    private fun scaleMatrix(bounds: SkRect): SkMatrix {
        val w = bounds.width().coerceAtLeast(4f)
        val h = bounds.height().coerceAtLeast(4f)
        val sx = (K_BIT_WIDTH - 2).toFloat() / w
        val sy = (K_BIT_HEIGHT - 2).toFloat() / h
        // Translate so `(bounds.left, bounds.top)` lands at (1, 1)
        // post-scale.
        val tx = 1f - bounds.left * sx
        val ty = 1f - bounds.top * sy
        return SkMatrix.MakeScale(sx, sy).copy(tx = tx, ty = ty)
    }

    /**
     * Rasterise [path] into a fresh `K_BIT_WIDTH × K_BIT_HEIGHT`
     * bitmap as solid black on a white background, AA off. Returns
     * a [BooleanArray] of size `K_BIT_WIDTH * K_BIT_HEIGHT` where
     * `true = path covered the pixel`, `false = background`.
     */
    private fun rasterise(path: SkPath): BooleanArray {
        val bm = SkBitmap(K_BIT_WIDTH, K_BIT_HEIGHT).also { it.eraseColor(SK_ColorWHITE) }
        val canvas = SkCanvas(bm)
        canvas.drawPath(
            path,
            SkPaint(SK_ColorBLACK).apply { isAntiAlias = false },
        )
        val out = BooleanArray(K_BIT_WIDTH * K_BIT_HEIGHT)
        for (y in 0 until K_BIT_HEIGHT) {
            for (x in 0 until K_BIT_WIDTH) {
                // Black ⟹ path covered. Anything brighter ⟹ background.
                out[y * K_BIT_WIDTH + x] = bm.getPixel(x, y) == SK_ColorBLACK
            }
        }
        return out
    }

    /** Apply [op] pixel-wise to two binary masks. */
    private fun applyPixelOp(
        a: BooleanArray,
        b: BooleanArray,
        op: SkPathOp,
    ): BooleanArray {
        val out = BooleanArray(a.size)
        when (op) {
            SkPathOp.kIntersect -> for (i in a.indices) out[i] = a[i] && b[i]
            SkPathOp.kUnion -> for (i in a.indices) out[i] = a[i] || b[i]
            SkPathOp.kDifference -> for (i in a.indices) out[i] = a[i] && !b[i]
            SkPathOp.kReverseDifference -> for (i in a.indices) out[i] = b[i] && !a[i]
            SkPathOp.kXOR -> for (i in a.indices) out[i] = a[i] xor b[i]
        }
        return out
    }

    /**
     * Count 2×2 pixel blocks that differ between [expected] and
     * [actual]. Mirrors upstream's `pathsDrawTheSame` 2×2 heuristic :
     * a single-pixel difference (typically rasteriser edge rounding)
     * is forgiven ; a 2×2 block of differences signals a real shape
     * disagreement.
     */
    private fun twoByTwoErrors(expected: BooleanArray, actual: BooleanArray): Int {
        var errors = 0
        for (y in 0 until K_BIT_HEIGHT - 1) {
            for (x in 0 until K_BIT_WIDTH - 1) {
                val e00 = expected[y * K_BIT_WIDTH + x]
                val a00 = actual[y * K_BIT_WIDTH + x]
                if (e00 == a00) continue  // diagonal anchor matches → skip
                val e10 = expected[y * K_BIT_WIDTH + x + 1]
                val a10 = actual[y * K_BIT_WIDTH + x + 1]
                val e01 = expected[(y + 1) * K_BIT_WIDTH + x]
                val a01 = actual[(y + 1) * K_BIT_WIDTH + x]
                val e11 = expected[(y + 1) * K_BIT_WIDTH + x + 1]
                val a11 = actual[(y + 1) * K_BIT_WIDTH + x + 1]
                // Count if all 4 pixels of the 2×2 block disagree —
                // single-pixel noise is absorbed.
                if (e10 != a10 && e01 != a01 && e11 != a11) {
                    errors++
                }
            }
        }
        return errors
    }
}
