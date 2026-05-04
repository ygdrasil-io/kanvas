package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/fillrect_gradient.cpp:FillrectGradientGM`.
 *
 * A 2-column × 9-row grid of `kCellSize × kCellSize = 50 × 50` rect cells:
 * each row is the **same** gradient stop list rendered once linearly
 * (column 0) and once radially (column 1). Stops cover all the corner
 * cases that gradient infrastructure must handle:
 *
 *  1. 2 stops at endpoints — green → white.
 *  2. 3 stops at endpoints + middle — green → white → red.
 *  3. 3 stops in a sub-range `[0.4, 0.6]` — clamps outside.
 *  4. Single stop at `0.0` — solid red.
 *  5. Single stop at `1.0` — solid red.
 *  6. Single stop at `0.5` — solid red.
 *  7. Disjoint gradients (duplicate stop position) — blue→white | red→yellow.
 *  8. Ignored duplicate stops at same position (visually same as #7).
 *  9. Unsorted input stops (visually different from #7 — Skia doesn't sort).
 *
 * Reference image: `fillrect_gradient.png`, `kNumColumns × (kCellSize + kPadSize) =
 * 120` × `kNumRows × (kCellSize + kPadSize) = 540`.
 *
 * Stresses the new [SkLinearGradient] + [SkRadialGradient] (Phase 5a)
 * end-to-end on the full clamp-mode stop interpolation path.
 */
public class FillrectGradientGM : GM() {

    override fun getName(): String = "fillrect_gradient"
    override fun getISize(): SkISize = SkISize.Make(
        kNumColumns * (kCellSize + kPadSize),
        kNumRows * (kCellSize + kPadSize),
    )

    private data class Stop(val pos: Float, val color: Int)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        // Row 1: green → white.
        drawGradient(c, listOf(Stop(0f, GREEN), Stop(1f, WHITE)))
        // Row 2: green → white → red.
        drawGradient(c, listOf(Stop(0f, GREEN), Stop(0.5f, WHITE), Stop(1f, RED)))
        // Row 3: stops in [0.4, 0.6] — clamps outside.
        drawGradient(c, listOf(Stop(0.4f, GREEN), Stop(0.5f, WHITE), Stop(0.6f, RED)))
        // Row 4-6: single stop at 0/1/0.5 — solid red.
        drawGradient(c, listOf(Stop(0f, RED)))
        drawGradient(c, listOf(Stop(1f, RED)))
        drawGradient(c, listOf(Stop(0.5f, RED)))
        // Row 7: disjoint via duplicate position 0.5.
        drawGradient(c, listOf(
            Stop(0f, BLUE), Stop(0.5f, WHITE),
            Stop(0.5f, RED), Stop(1f, YELLOW),
        ))
        // Row 8: ignored duplicates at the same position.
        drawGradient(c, listOf(
            Stop(0f, BLUE), Stop(0.5f, WHITE),
            Stop(0.5f, GRAY), Stop(0.5f, CYAN),
            Stop(0.5f, RED), Stop(1f, YELLOW),
        ))
        // Row 9: unsorted input — Skia doesn't sort, so this differs from #7.
        drawGradient(c, listOf(
            Stop(0.5f, WHITE), Stop(0.5f, GRAY),
            Stop(1f, YELLOW), Stop(0.5f, CYAN),
            Stop(0.5f, RED), Stop(0f, BLUE),
        ))
    }

    private fun drawGradient(c: SkCanvas, stops: List<Stop>) {
        val colors = IntArray(stops.size) { stops[it].color }
        val positions = FloatArray(stops.size) { stops[it].pos }
        val cellRect = SkRect.MakeXYWH(0f, 0f, kCellSize.toFloat(), kCellSize.toFloat())

        // Column 0: linear gradient from `(kCellSize, 0)` to `(kCellSize, kCellSize)` —
        // i.e. top-right to bottom-right (vertical). Note: upstream's
        // points are intentionally outside the cell on the right edge,
        // pushing the visible gradient to wrap from one side to the other.
        val linear = SkLinearGradient.Make(
            SkPoint(kCellSize.toFloat(), 0f),
            SkPoint(kCellSize.toFloat(), kCellSize.toFloat()),
            colors, positions, SkTileMode.kClamp,
        )
        val paint = SkPaint().apply {
            isAntiAlias = true
            shader = linear
        }
        c.drawRect(cellRect, paint)

        c.save()
        c.translate(kCellSize + kPadSize.toFloat(), 0f)

        // Column 1: radial gradient centred in the cell.
        val radial = SkRadialGradient.Make(
            SkPoint(kCellSize / 2f, kCellSize / 2f),
            kCellSize / 2f,
            colors, positions, SkTileMode.kClamp,
        )
        paint.shader = radial
        c.drawRect(cellRect, paint)

        c.restore()
        c.translate(0f, kCellSize + kPadSize.toFloat())
    }

    private companion object {
        // From upstream `gm/fillrect_gradient.cpp` (file-level constants).
        const val kCellSize: Int = 50
        const val kNumColumns: Int = 2
        const val kNumRows: Int = 9
        const val kPadSize: Int = 10

        // SkColor constants (ARGB).
        const val GREEN: Int = 0xFF00FF00.toInt()      // SK_ColorGREEN
        const val WHITE: Int = 0xFFFFFFFF.toInt()      // SK_ColorWHITE
        const val RED: Int = 0xFFFF0000.toInt()        // SK_ColorRED
        const val BLUE: Int = 0xFF0000FF.toInt()       // SK_ColorBLUE
        const val YELLOW: Int = 0xFFFFFF00.toInt()     // SK_ColorYELLOW
        const val GRAY: Int = 0xFF888888.toInt()       // SK_ColorGRAY
        const val CYAN: Int = 0xFF00FFFF.toInt()       // SK_ColorCYAN
    }
}
