package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/crbug_1162942.cpp::Crbug1162942`.
 * Renders paths with a near-singular CTM matrix and rotating colors.
 * @see https://github.com/google/skia/blob/main/gm/crbug_1162942.cpp
 */
class Crbug1162942Gm : SkiaGm {
    override val name = "crbug_1162942"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 620
    override val height = 200

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val ctm = Matrix33.makeAll(
            Float.fromBits(0x3FCC7F75),
            Float.fromBits(0x3D5784FC),
            Float.fromBits(0x44C48C99),
            Float.fromBits(0x3F699F7F),
            Float.fromBits(0x3E0A0D37),
            Float.fromBits(0x43908518),
            Float.fromBits(0x3AA17423),
            Float.fromBits(0x3A6CCDC3),
            Float.fromBits(0x3F2EFEEC),
        )

        val pts = arrayOf(
            Pair(Float.fromBits(0x3F39778B), Float.fromBits(0x43FF7FFC)),
            Pair(Float.fromBits(0x00000000), Float.fromBits(0x43FF7FFA)),
            Pair(Float.fromBits(0xB83B055E.toInt()), Float.fromBits(0x42500003)),
            Pair(Float.fromBits(0x3F39776F), Float.fromBits(0x4250000D)),
        )

        canvas.drawRect(Rect(0f, 0f, this.width.toFloat(), this.height.toFloat()), Paint(color = Color.WHITE))

        var color = Color.GREEN
        for (i in 0 until 6) {
            canvas.save()
            canvas.concat(ctm)
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
            canvas.translate(0f, 25f)
        }
    }

    private fun nibbleRotate(color: Color): Color {
        val packed = color.packed
        val rgb = packed and 0x00FFFFFFu
        val rotated = (rgb shl 4) or (rgb shr 20)
        return Color(0xFF000000u or (rotated and 0x00FFFFFFu))
    }
}
