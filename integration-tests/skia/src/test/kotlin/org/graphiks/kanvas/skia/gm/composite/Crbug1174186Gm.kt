package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33

/**
 * Port of Skia's `gm/crbug_1174186.cpp::Crbug1174186`.
 * Renders paths with a specific CTM matrix across multiple iterations.
 * @see https://github.com/google/skia/blob/main/gm/crbug_1174186.cpp
 */
class Crbug1174186Gm : SkiaGm {
    override val name = "crbug_1174186"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 0.0
    override val width = 1200
    override val height = 1200

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val m = Matrix33.makeAll(
            Float.fromBits(0x24480629),
            Float.fromBits(0xbf3555c2.toInt()),
            Float.fromBits(0x4377d67b),
            Float.fromBits(0x23a61d51),
            Float.fromBits(0x3f34b400),
            Float.fromBits(0x4453f572),
            Float.fromBits(0x00000000),
            Float.fromBits(0x00000000),
            Float.fromBits(0x3f800000),
        )

        val pts = arrayOf(
            Pair(Float.fromBits(0x3f7ffff2), Float.fromBits(0x43483d60)),
            Pair(Float.fromBits(0x00000000), Float.fromBits(0x43483d60)),
            Pair(Float.fromBits(0x00000000), Float.fromBits(0x4311a628)),
            Pair(Float.fromBits(0x3f800000), Float.fromBits(0x43130f8c)),
        )

        var color = Color.GREEN
        canvas.translate(-500f, 0f)
        for (i in 0 until 10) {
            for (flags in 0 until 15) {
                canvas.save()
                canvas.concat(m)
                canvas.drawPath(
                    Path {
                        moveTo(pts[0].first, pts[0].second)
                        lineTo(pts[1].first, pts[1].second)
                        lineTo(pts[2].first, pts[2].second)
                        lineTo(pts[3].first, pts[3].second)
                        close()
                    },
                    Paint(color = color),
                )
                color = nibbleRotate(color)
                canvas.restore()
                canvas.translate(5.1f, 0f)
            }
        }
    }

    private fun nibbleRotate(color: Color): Color {
        val packed = color.packed
        val rgb = packed and 0x00FFFFFFu
        val rotated = (rgb shl 4) or (rgb shr 20)
        return Color(0xFF000000u or (rotated and 0x00FFFFFFu))
    }
}
