package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33

class Crbug1167277Gm : SkiaGm {
    override val name = "crbug_1167277"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 0.0
    override val width = 230
    override val height = 320

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.translate(-1250f, -900f)

        val ctm = Matrix33.makeAll(
            Float.fromBits(0xbf8fcfae.toInt()),
            Float.fromBits(0xbeae25ee.toInt()),
            Float.fromBits(0x449ca6db),
            Float.fromBits(0x3c9dc40f),
            Float.fromBits(0xbf950e35.toInt()),
            Float.fromBits(0x4487da43),
            Float.fromBits(0xb8d4d6bc.toInt()),
            Float.fromBits(0xb92fbb29.toInt()),
            Float.fromBits(0x3f6f605c),
        )

        val clip = arrayOf(
            Pair(Float.fromBits(0x3ef434a2), Float.fromBits(0x43440004)),
            Pair(Float.fromBits(0x00000000), Float.fromBits(0x43440009)),
            Pair(Float.fromBits(0x38ef605d), Float.fromBits(0x38ef605d)),
            Pair(Float.fromBits(0x3ef436e3), Float.fromBits(0x396f5d30)),
        )

        var color = Color.GREEN
        for (flags in 0 until 15) {
            canvas.save()
            canvas.concat(ctm)
            canvas.drawPath(
                Path {
                    moveTo(clip[0].first, clip[0].second)
                    lineTo(clip[1].first, clip[1].second)
                    lineTo(clip[2].first, clip[2].second)
                    lineTo(clip[3].first, clip[3].second)
                    close()
                },
                Paint(color = color),
            )
            color = nibbleRotate(color)
            canvas.restore()
            canvas.translate(5f, 0f)
        }
    }

    private fun nibbleRotate(color: Color): Color {
        val packed = color.packed
        val rgb = packed and 0x00FFFFFFu
        val rotated = (rgb shl 4) or (rgb shr 20)
        return Color(0xFF000000u or (rotated and 0x00FFFFFFu))
    }
}
