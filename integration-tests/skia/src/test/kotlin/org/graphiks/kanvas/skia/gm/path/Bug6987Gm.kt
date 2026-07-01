package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/strokefill.cpp::bug6987`.
 * A tiny triangle (1-px scale) stroked with strokeWidth = 0.0001 then scaled
 * by 50000x so the stroker resolution matches the reference rasterization.
 * @see https://github.com/google/skia/blob/main/gm/strokefill.cpp
 */
class Bug6987Gm : SkiaGm {
    override val name = "bug6987"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 93.3
    override val width = 200
    override val height = 200

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(
            style = PaintStyle.STROKE,
            strokeWidth = 0.0001f,
            antiAlias = true,
        )
        val path = Path {
            moveTo(0.0005f, 0.0004f)
            lineTo(0.0008f, 0.0010f)
            lineTo(0.0002f, 0.0010f)
            close()
        }
        canvas.save()
        canvas.scale(50000f, 50000f)
        canvas.drawPath(path, paint)
        canvas.restore()
    }
}
