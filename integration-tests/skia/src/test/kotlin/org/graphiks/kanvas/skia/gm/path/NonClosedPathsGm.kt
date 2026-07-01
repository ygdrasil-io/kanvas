package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

class NonClosedPathsGm : SkiaGm {
    override val name = "nonclosedpaths"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 56.2
    override val width = 1220
    override val height = 1920

    private enum class ClosureType { TotallyNonClosed, FakeCloseCorner, FakeCloseMiddle }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val strokeWidths = intArrayOf(0, 10, 40, 50)
        val numWidths = strokeWidths.size
        val styles = arrayOf(PaintStyle.STROKE, PaintStyle.STROKE)
        val caps = arrayOf(StrokeCap.BUTT, StrokeCap.ROUND, StrokeCap.SQUARE)
        val joins = arrayOf(StrokeJoin.MITER, StrokeJoin.ROUND, StrokeJoin.BEVEL)
        val types = ClosureType.values()
        val lineNum = joins.size * numWidths
        var counter = 0
        var paint = Paint()

        for (type in types) {
            for (style in styles) {
                for (cap in caps) {
                    for (join in joins) {
                        for (width in strokeWidths) {
                            canvas.save()
                            setLocation(canvas, counter, lineNum)
                            val path = makePath(type)
                            paint = paint.copy(
                                style = style,
                                strokeCap = cap,
                                strokeJoin = join,
                                strokeWidth = width.toFloat(),
                            )
                            canvas.drawPath(path, paint)
                            canvas.restore()
                            counter++
                        }
                    }
                }
            }
        }

        paint = paint.copy(style = PaintStyle.FILL)
        for (type in types) {
            canvas.save()
            setLocation(canvas, counter, lineNum)
            canvas.drawPath(makePath(type), paint)
            canvas.restore()
            counter++
        }
    }

    private fun makePath(type: ClosureType): Path {
        val p = Path { }
        if (type == ClosureType.FakeCloseMiddle) {
            p.moveTo(30f, 50f)
            p.lineTo(30f, 30f)
        } else {
            p.moveTo(30f, 30f)
        }
        p.lineTo(70f, 30f)
        p.lineTo(70f, 70f)
        p.lineTo(30f, 70f)
        p.lineTo(30f, 50f)
        if (type == ClosureType.FakeCloseCorner) {
            p.lineTo(30f, 30f)
        }
        return p
    }

    private fun setLocation(canvas: GmCanvas, counter: Int, lineNum: Int) {
        val x = 100f * (counter % lineNum) + 10f + 0.25f
        val y = 100f * (counter / lineNum) + 10f + 0.75f
        canvas.translate(x, y)
    }
}
