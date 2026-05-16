package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import kotlin.math.exp

/**
 * Port of Skia's `gm/crbug_1041204.cpp::crbug_10141204` (DEF_SIMPLE_GM,
 * 512 × 512).
 *
 * Stress test for non-axis-aligned transforms with extreme coordinate
 * magnitudes — should fill the canvas with solid blue. Originally
 * exposed a numerical issue in the GPU clip discard path under giant
 * post-scale-matrix coordinates (~ 10⁶). The CPU raster pipeline
 * routes through `drawPath(SkPath.Rect(rect))` under non-axis-aligned
 * matrices ; the test verifies that the AA scanline rasterizer
 * survives the precision squeeze.
 *
 * The upstream call uses `SkMatrix::MakeAll` with a 9-arg signature ;
 * the perspective row is `[0, 0, 1]` (identity), so we drop it and
 * pass the affine 6 args.
 */
public class Crbug10141204GM : GM() {

    override fun getName(): String = "crbug_10141204"
    override fun getISize(): SkISize = SkISize.Make(512, 512)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val extraZoom = exp(-2.3).toFloat()
        c.scale(extraZoom, extraZoom)
        c.scale(2f, 2f)
        c.concat(SkMatrix.MakeAll(
            -0.0005550860255665798f, -0.0030798374421905717f, -0.014111959825129805f,
            -0.07569627776417084f, 232.00000000000017f, 39.999999999999936f,
        ))
        c.translate(-3040103.0493857153f, 337502.1103282161f)
        c.scale(9783.93962050256f, -9783.93962050256f)

        val paint = SkPaint().apply {
            color = SK_ColorBLUE
            isAntiAlias = true
        }
        c.drawRect(SkRect.MakeWH(512f, 512f), paint)
    }
}
