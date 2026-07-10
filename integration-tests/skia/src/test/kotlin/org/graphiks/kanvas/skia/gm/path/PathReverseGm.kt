package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/pathreverse.cpp` —
 * `DEF_SIMPLE_GM_BG_NAME(pathreverse, canvas, 640, 480, SK_ColorWHITE,
 *  SkString("path-reverse"))`.
 *
 * The GM exercises path reversal by drawing paths and their mirrored versions.
 *
 * @see https://github.com/google/skia/blob/main/gm/pathreverse.cpp
 */
class PathReverseGm : SkiaGm {
    override val name = "path-reverse"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 480

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        var r = Rect(10f, 10f, 100f, 60f)

        var builder = Path { }

        builder.addRect(r)
        testRev(canvas, builder)

        canvas.translate(0f, 100f)
        builder = builder.transform(Matrix33.translate(20f, 20f))
        builder.addRect(r)
        testRev(canvas, builder)

        canvas.translate(0f, 100f)
        val builder2 = Path { 
            moveTo(10f, 10f)
            lineTo(30f, 30f)
        }.also {
            it.addOval(r)
            val r2 = Rect(r.left + 50f, r.top + 20f, r.right + 50f, r.bottom + 20f)
            it.addOval(r2)
        }
        testRev(canvas, builder2)

        val path = hiraginoMaruGothProE()
        canvas.translate(0f, 100f)
        testRev(canvas, path)
    }

    private fun testPath(canvas: GmCanvas, path: Path) {
        val fillPaint = Paint(antiAlias = true)
        canvas.drawPath(path, fillPaint)

        val strokePaint = Paint(
            antiAlias = true,
            style = PaintStyle.STROKE,
            color = Color.RED,
        )
        canvas.drawPath(path, strokePaint)
    }

    private fun testRev(canvas: GmCanvas, path: Path) {
        testPath(canvas, path)

        val rev = Path { }.apply { reverseAddPath(path) }
        canvas.save()
        canvas.translate(150f, 0f)
        testPath(canvas, rev)
        canvas.restore()
    }

    private fun hiraginoMaruGothProE(): Path {
        return Path {
            moveTo(98.6f, 24.7f)
            cubicTo(101.7f, 24.7f, 103.6f, 22.8f, 103.6f, 19.2f)
            cubicTo(103.6f, 18.9f, 103.6f, 18.7f, 103.6f, 18.4f)
            cubicTo(102.6f, 5.3f, 94.4f, -6.1f, 79.8f, -6.1f)
            cubicTo(63.5f, -6.1f, 54.5f, 6f, 54.5f, 23.3f)
            cubicTo(54.5f, 40.6f, 64f, 52.2f, 80.4f, 52.2f)
            cubicTo(93.4f, 52.2f, 99.2f, 45.6f, 102.4f, 39f)
            cubicTo(102.8f, 38.4f, 102.9f, 37.8f, 102.9f, 37.2f)
            cubicTo(102.9f, 35.4f, 101.5f, 34.2f, 99.8f, 33.7f)
            cubicTo(99.1f, 33.5f, 98.4f, 33.3f, 97.7f, 33.3f)
            cubicTo(96.3f, 33.3f, 95f, 34f, 94.1f, 35.8f)
            cubicTo(91.7f, 41.1f, 87.7f, 44.7f, 80.5f, 44.7f)
            cubicTo(69.7f, 44.7f, 63.6f, 37f, 63.4f, 24.7f)
            lineTo(98.6f, 24.7f)
            close()
            moveTo(63.7f, 17.4f)
            cubicTo(65f, 7.6f, 70.2f, 1.2f, 79.8f, 1.2f)
            cubicTo(89f, 1.2f, 93.3f, 8.5f, 94.5f, 15.6f)
            cubicTo(94.5f, 15.8f, 94.5f, 16f, 94.5f, 16.1f)
            cubicTo(94.5f, 17f, 94.1f, 17.4f, 93f, 17.4f)
            lineTo(63.7f, 17.4f)
            close()
        }
    }
}
