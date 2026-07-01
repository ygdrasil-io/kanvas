package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/pathfill.cpp::bug7792`.
 * 14 line-only paths exercising moveTo/close edge cases for the fill rasterizer.
 * @see https://github.com/google/skia/blob/main/gm/pathfill.cpp
 */
class Bug7792Gm : SkiaGm {
    override val name = "bug7792"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 800
    override val height = 800

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val p = Paint(color = Color.BLACK)

        canvas.drawPath(Path {
            moveTo(10f, 10f)
            moveTo(75f, 75f)
            lineTo(150f, 75f)
            lineTo(150f, 150f)
            lineTo(75f, 150f)
        }, p)

        canvas.translate(200f, 0f)
        canvas.drawPath(Path {
            moveTo(75f, 50f)
            moveTo(100f, 75f)
            lineTo(150f, 75f)
            lineTo(150f, 150f)
            lineTo(75f, 150f)
            lineTo(75f, 50f)
            close()
        }, p)

        canvas.translate(200f, 0f)
        canvas.drawPath(Path {
            moveTo(10f, 10f)
            moveTo(75f, 75f)
            lineTo(150f, 75f)
            lineTo(150f, 150f)
            lineTo(75f, 150f)
            close()
        }, p)

        canvas.translate(-400f, 200f)
        canvas.drawPath(Path {
            moveTo(75f, 150f)
            lineTo(75f, 75f)
            lineTo(150f, 75f)
            lineTo(150f, 150f)
            lineTo(75f, 150f)
            moveTo(75f, 150f)
        }, p)

        canvas.translate(200f, 0f)
        canvas.drawPath(Path {
            moveTo(250f, 75f)
            moveTo(250f, 75f)
            moveTo(250f, 75f)
            moveTo(100f, 75f)
            lineTo(150f, 75f)
            lineTo(150f, 150f)
            lineTo(75f, 150f)
            lineTo(75f, 75f)
            close()
            lineTo(0f, 0f)
            close()
        }, p)

        canvas.translate(200f, 0f)
        canvas.drawPath(Path {
            moveTo(75f, 75f)
            lineTo(150f, 75f)
            lineTo(150f, 150f)
            lineTo(75f, 150f)
            moveTo(250f, 75f)
        }, p)

        canvas.translate(-400f, 200f)
        canvas.drawPath(Path {
            moveTo(75f, 10f)
            moveTo(75f, 75f)
            lineTo(150f, 75f)
            lineTo(150f, 150f)
            lineTo(75f, 150f)
            lineTo(75f, 10f)
            close()
        }, p)

        canvas.translate(200f, 0f)
        canvas.drawPath(Path {
            moveTo(75f, 75f)
            lineTo(75f, 75f)
            lineTo(75f, 75f)
            lineTo(75f, 75f)
            lineTo(150f, 75f)
            lineTo(150f, 150f)
            lineTo(75f, 150f)
            close()
            moveTo(10f, 10f)
            lineTo(30f, 10f)
            lineTo(10f, 30f)
        }, p)

        canvas.translate(200f, 0f)
        canvas.drawPath(Path {
            moveTo(75f, 75f)
            lineTo(75f, 75f)
            moveTo(75f, 75f)
            lineTo(75f, 75f)
            lineTo(150f, 75f)
            lineTo(150f, 150f)
            lineTo(75f, 150f)
            close()
        }, p)

        canvas.translate(-400f, 200f)
        canvas.drawPath(Path {
            moveTo(75f, 75f)
            lineTo(150f, 75f)
            lineTo(150f, 150f)
            lineTo(75f, 150f)
            lineTo(75f, 250f)
            moveTo(75f, 75f)
            close()
        }, p)

        canvas.translate(200f, 0f)
        canvas.drawPath(Path {
            moveTo(75f, 75f)
            lineTo(150f, 75f)
            lineTo(150f, 150f)
            lineTo(75f, 150f)
            lineTo(75f, 10f)
            moveTo(75f, 75f)
            close()
        }, p)

        canvas.translate(200f, 0f)
        canvas.drawPath(Path {
            moveTo(75f, 75f)
            lineTo(150f, 75f)
            lineTo(150f, 150f)
            lineTo(10f, 150f)
            moveTo(75f, 75f)
            lineTo(75f, 75f)
        }, p)

        canvas.translate(200f, -600f)
        canvas.drawPath(Path {
            moveTo(150f, 75f)
            lineTo(150f, 150f)
            lineTo(75f, 150f)
            lineTo(75f, 100f)
        }, p)

        canvas.translate(0f, 200f)
        canvas.drawPath(Path {
            moveTo(150f, 100f)
            lineTo(150f, 100f)
            lineTo(150f, 150f)
            lineTo(75f, 150f)
            lineTo(75f, 100f)
            lineTo(75f, 75f)
            lineTo(150f, 75f)
            close()
        }, p)

        canvas.translate(0f, 200f)
        canvas.drawPath(Path {
            moveTo(75f, 75f)
            lineTo(150f, 75f)
            lineTo(150f, 150f)
            lineTo(140f, 150f)
            lineTo(140f, 75f)
            moveTo(75f, 75f)
            close()
        }, p)

        canvas.translate(0f, 200f)
        canvas.drawPath(Path {
            moveTo(75f, 75f)
            lineTo(150f, 75f)
            lineTo(150f, 150f)
            lineTo(140f, 150f)
            lineTo(140f, 75f)
            moveTo(75f, 75f)
            close()
        }, p)
    }
}
