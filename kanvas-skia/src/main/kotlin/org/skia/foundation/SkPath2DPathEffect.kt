package org.skia.foundation

import org.graphiks.math.SkMatrix
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Mirrors Skia's
 * [`SkPath2DPathEffect`](https://github.com/google/skia/blob/main/include/effects/Sk2DPathEffect.h)
 * — tile a custom *stamp* path across the canvas in a regular 2-D
 * grid defined by [matrix].
 *
 * The matrix encodes the tile basis : column vectors `(M.sx, M.ky)`
 * and `(M.kx, M.sy)` give the world-space spacing between adjacent
 * tile origins along the grid's `i` and `j` axes. A simple
 * `MakeScale(s, s)` matrix produces an `s × s` square grid.
 *
 * For each integer `(i, j)` whose tile origin `M * (i, j)` falls
 * inside (or near) the input path's device-space bounds, a
 * translated copy of [stamp] is emitted into the output path. The
 * resulting path is the union of all stamps — typically used to
 * "etch" or "halftone-tile" the input shape's bounding region.
 *
 * Construct via [Make]. `matrix.invert() == null` returns `null`
 * (degenerate basis ⇒ no tiling possible).
 *
 * **Phase 7p_t coverage** :
 *  - Translate stamps only — Skia upstream's `SkPath2DPathEffect`
 *    also rotates each stamp to align with the matrix basis ; we
 *    skip that for v1 since the canonical use case
 *    (`gm/patheffects.cpp::tile_pe` with `MakeScale(12, 12)` + a
 *    circle stamp) is rotation-invariant.
 *  - The grid range is computed from the input path's
 *    [SkPath.computeBounds] (axis-aligned bbox in source space).
 *    This conservatively over-tiles for non-axis-aligned bounds —
 *    fine since the rasterizer's clip will mask the unused stamps.
 */
public class SkPath2DPathEffect private constructor(
    private val matrix: SkMatrix,
    private val stamp: SkPath,
) : SkPathEffect() {

    private val invMatrix: SkMatrix? = matrix.invert()

    override fun filterPath(input: SkPath, ctm: SkMatrix): SkPath? {
        if (input.isEmpty() || stamp.isEmpty() || invMatrix == null) return null
        val bounds = input.computeBounds()
        if (bounds.isEmpty) return null

        // Compute the (i, j) range that covers the input path's bounds.
        // Map each corner of the bounds rectangle through invMatrix to
        // get its position in stamp-grid coords ; the integer floor /
        // ceil of the resulting min/max gives the tile index range.
        val corners = floatArrayOf(
            bounds.left,  bounds.top,
            bounds.right, bounds.top,
            bounds.right, bounds.bottom,
            bounds.left,  bounds.bottom,
        )
        var minI = Int.MAX_VALUE; var maxI = Int.MIN_VALUE
        var minJ = Int.MAX_VALUE; var maxJ = Int.MIN_VALUE
        for (k in 0 until 4) {
            val (i, j) = invMatrix.mapXY(corners[k * 2], corners[k * 2 + 1])
            minI = minOf(minI, floor(i).toInt())
            maxI = maxOf(maxI, ceil(i).toInt())
            minJ = minOf(minJ, floor(j).toInt())
            maxJ = maxOf(maxJ, ceil(j).toInt())
        }

        // Stamp at every grid intersection in the (i, j) range.
        val out = SkPathBuilder()
        for (j in minJ..maxJ) {
            for (i in minI..maxI) {
                val (wx, wy) = matrix.mapXY(i.toFloat(), j.toFloat())
                val translated = stamp.makeTransform(SkMatrix.MakeTrans(wx, wy))
                out.addPath(translated)
            }
        }
        return out.detach()
    }

    public companion object {
        /**
         * Mirrors Skia's `SkPath2DPathEffect::Make(matrix, stamp)`.
         * Returns `null` when [matrix] is non-invertible (degenerate
         * basis ⇒ no tiling possible).
         */
        public fun Make(matrix: SkMatrix, stamp: SkPath): SkPathEffect? {
            if (matrix.invert() == null) return null
            return SkPath2DPathEffect(matrix, stamp)
        }
    }
}
