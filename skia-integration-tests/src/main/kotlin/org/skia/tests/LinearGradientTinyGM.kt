package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/gradients_no_texture.cpp::LinearGradientTinyGM`
 * (600 × 500). Stress-tests the linear-gradient stop interpolation at
 * **degenerate stop positions** (epsilon-close stops, near-collinear
 * endpoints) by laying out a 4-column × 3-row grid of 100×100 rects,
 * each carrying the same green-red-green triple stop with a per-cell
 * (endpoints, position) tweak :
 *
 *  - rows 1-2 : `pts (0, 0) → (10, 0)` / `pts (0, 0) → (0, 10)` with
 *               middle stop pinned to `0.999999`, `0.000001`,
 *               `0.999999999`, `0.000000001` — exercises the
 *               near-degenerate position lookup.
 *  - row 3    : near-coincident endpoints (`pts (0, 0) → (0.00001, 0)`,
 *               etc.) with the middle stop at 0.5 — exercises the
 *               zero-length / quasi-zero-length gradient direction.
 */
public class LinearGradientTinyGM : GM() {

    override fun getName(): String = "linear_gradient_tiny"
    override fun getISize(): SkISize = SkISize.Make(600, 500)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val kRectSize = 100f
        val colors = intArrayOf(SK_ColorGREEN, SK_ColorRED, SK_ColorGREEN)

        data class Config(val p0: SkPoint, val p1: SkPoint, val pos: FloatArray)
        val configs = listOf(
            Config(SkPoint(0f, 0f), SkPoint(10f, 0f),       floatArrayOf(0f, 0.999999f,    1f)),
            Config(SkPoint(0f, 0f), SkPoint(10f, 0f),       floatArrayOf(0f, 0.000001f,    1f)),
            Config(SkPoint(0f, 0f), SkPoint(10f, 0f),       floatArrayOf(0f, 0.999999999f, 1f)),
            Config(SkPoint(0f, 0f), SkPoint(10f, 0f),       floatArrayOf(0f, 0.000000001f, 1f)),

            Config(SkPoint(0f, 0f), SkPoint(0f, 10f),       floatArrayOf(0f, 0.999999f,    1f)),
            Config(SkPoint(0f, 0f), SkPoint(0f, 10f),       floatArrayOf(0f, 0.000001f,    1f)),
            Config(SkPoint(0f, 0f), SkPoint(0f, 10f),       floatArrayOf(0f, 0.999999999f, 1f)),
            Config(SkPoint(0f, 0f), SkPoint(0f, 10f),       floatArrayOf(0f, 0.000000001f, 1f)),

            Config(SkPoint(0f, 0f),       SkPoint(0.00001f, 0f), floatArrayOf(0f, 0.5f, 1f)),
            Config(SkPoint(9.99999f, 0f), SkPoint(10f, 0f),      floatArrayOf(0f, 0.5f, 1f)),
            Config(SkPoint(0f, 0f),       SkPoint(0f, 0.00001f), floatArrayOf(0f, 0.5f, 1f)),
            Config(SkPoint(0f, 9.99999f), SkPoint(0f, 10f),      floatArrayOf(0f, 0.5f, 1f)),
        )

        val paint = SkPaint()
        for (i in configs.indices) {
            val cfg = configs[i]
            val saveCount = c.save()
            paint.shader = SkLinearGradient.Make(
                cfg.p0, cfg.p1, colors, cfg.pos, SkTileMode.kClamp,
            )
            c.translate(
                kRectSize * ((i % 4) * 1.5f + 0.25f),
                kRectSize * ((i / 4) * 1.5f + 0.25f),
            )
            c.drawRect(SkRect.MakeWH(kRectSize, kRectSize), paint)
            c.restoreToCount(saveCount)
        }
    }
}
