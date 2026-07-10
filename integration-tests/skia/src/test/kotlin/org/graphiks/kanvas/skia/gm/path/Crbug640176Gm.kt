package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/conicpaths.cpp::DEF_SIMPLE_GM(crbug_640176, canvas, 250, 250)`.
 * Repro for crbug.com/640176 — a line/line/conic path with coordinates near
 * (0, 0) under translate(125, 125) and AA fill. The conic weight is
 * 0.965926f (cos(15°)).
 *
 * **Adaptation**: Kanvas Path has no `conicTo`; the conic is approximated
 * with [quadTo] (weight ≈ 1.0).
 * @see https://github.com/google/skia/blob/main/gm/conicpaths.cpp
 */
class Crbug640176Gm : SkiaGm {
    override val name = "crbug_640176"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 250
    override val height = 250

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        val path = Path {
            moveTo(Float.fromBits(0x00000000), Float.fromBits(0x00000000))
            lineTo(Float.fromBits(0x42cfd89a), Float.fromBits(0xc2700000L.toInt()))
            lineTo(Float.fromBits(0x42cfd899), Float.fromBits(0xc2700006L.toInt()))
            quadTo(
                Float.fromBits(0x42f00000), Float.fromBits(0xc2009d9cL.toInt()),
                Float.fromBits(0x42f00001), Float.fromBits(0x00000000),
            )
        }

        val paint = Paint()
        canvas.translate(125f, 125f)
        canvas.drawPath(path, paint)
    }
}
