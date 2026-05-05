package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSweepGradient
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/gradients.cpp::sweep_tiling` (`DEF_SIMPLE_GM`,
 * 690 × 512).
 *
 * 3 rows × 4 columns of `160 × 160` rects, each painted with a sweep
 * gradient centred in the cell. Rows = `{kClamp, kRepeat, kMirror}`,
 * columns = the four `{startAngle, endAngle}` pairs:
 *  - `(-330, -270)` — 60° sweep behind the +X axis (negative t-range)
 *  - `(  30,   90)` — 60° sweep in the lower-right quadrant
 *  - `( 390,  450)` — equivalent to `(30, 90)` but angle wraps past 360°
 *  - `( -30,  800)` — 830° sweep covering well over a full revolution
 *
 * Gradient stops: `{Blue@0, Yellow@.25, Green@.5}` — only fills half of
 * `[0, 1]`, so each tile mode behaves visibly differently in the
 * unmapped half. Cells translate by `size * 1.1` for layout breathing
 * room — final image is `(size + size·.1) × 4 + ε` ≈ 690 × 528 (we
 * actually clip to 512 vertical via `getISize`, matching upstream).
 */
public class SweepTilingGM : GM() {

    override fun getName(): String = "sweep_tiling"
    override fun getISize(): SkISize = SkISize.Make(690, 512)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val colors = intArrayOf(
            0xFF0000FF.toInt(),  // Blue
            0xFFFFFF00.toInt(),  // Yellow
            0xFF00FF00.toInt(),  // Green
        )
        val positions = floatArrayOf(0f, 0.25f, 0.50f)

        val modes = arrayOf(SkTileMode.kClamp, SkTileMode.kRepeat, SkTileMode.kMirror)

        // (start, end) in degrees. Match upstream order.
        val angles = arrayOf(
            -330f to -270f,
              30f to   90f,
             390f to  450f,
             -30f to  800f,
        )

        val r = SkRect.MakeWH(SIZE, SIZE)

        for (mode in modes) {
            c.save()
            for (angle in angles) {
                val shader = SkSweepGradient.Make(
                    center = SkPoint(SIZE / 2f, SIZE / 2f),
                    startAngle = angle.first,
                    endAngle = angle.second,
                    colors = colors,
                    positions = positions,
                    tileMode = mode,
                )
                val paint = SkPaint().apply { this.shader = shader }
                c.drawRect(r, paint)
                c.translate(SIZE * 1.1f, 0f)
            }
            c.restore()
            c.translate(0f, SIZE * 1.1f)
        }
    }

    private companion object {
        const val SIZE: Float = 160f
    }
}
