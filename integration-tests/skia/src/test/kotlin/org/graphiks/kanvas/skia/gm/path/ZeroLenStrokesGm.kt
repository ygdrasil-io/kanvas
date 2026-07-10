package org.graphiks.kanvas.skia.gm.path

/**
 * Port of Skia's `gm/zerolenstrokes.cpp`.
 * Tests rendering of zero-length paths (move+line, move+close, cubic, quad to same point).
 * @see https://github.com/google/skia/blob/main/gm/zerolenstrokes.cpp
 */

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class ZeroLenStrokesGm : SkiaGm {
    override val name = "zeroPath"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 92.4
    override val width = 400
    override val height = 800

    private val fMoveHfPath: Path = Path {
        moveTo(0f, 0f); lineTo(0f, 0f)
        moveTo(10f, 0f); lineTo(10f, 0f)
        moveTo(20f, 0f); lineTo(20f, 0f)
    }

    private val fMoveZfPath: Path = Path {
        moveTo(0f, 0f); close()
        moveTo(10f, 0f); close()
        moveTo(20f, 0f); close()
    }

    private val fDashedfPath: Path = Path {
        moveTo(0f, 0f); lineTo(25f, 0f)
    }

    private val fCubicPath: Path = Path {
        moveTo(0f, 0f); cubicTo(0f, 0f, 0f, 0f, 0f, 0f)
    }

    private val fQuadPath: Path = Path {
        moveTo(0f, 0f); quadTo(0f, 0f, 0f, 0f)
    }

    private val fLinePath: Path = Path {
        moveTo(0f, 0f); lineTo(0f, 0f)
    }

    private val fRefPath: List<Path> = run {
        val builders = Array(4) { Path { } }
        for (i in 0 until 3) {
            builders[0].addCircle(i * 10f, 0f, 5f)
            builders[1].addCircle(i * 10f, 0f, 10f)
            builders[2].addRect(Rect(i * 10f - 4f, -2f, i * 10f + 4f, 6f))
            builders[3].addRect(Rect(i * 10f - 10f, -10f, i * 10f + 10f, 10f))
        }
        builders.toList()
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val fillPaint = Paint(antiAlias = true)
        val strokePaint = Paint(
            antiAlias = true,
            style = PaintStyle.STROKE,
        )

        for (i in 0 until 2) {
            val sw = if (i != 0) 8f else 10f
            val cap = if (i != 0) StrokeCap.SQUARE else StrokeCap.ROUND

            canvas.save()
            canvas.translate(10f + i * 100f, 10f)
            canvas.drawPath(fMoveHfPath, strokePaint.copy(strokeWidth = sw, strokeCap = cap))
            canvas.translate(0f, 20f)
            canvas.drawPath(fMoveZfPath, strokePaint.copy(strokeWidth = sw, strokeCap = cap))

            canvas.translate(0f, 20f)
            canvas.drawPath(fDashedfPath, strokePaint.copy(
                strokeWidth = sw,
                strokeCap = cap,
                pathEffect = PathEffect.Dash(floatArrayOf(0f, 10f), 0f),
            ))

            canvas.translate(0f, 20f)
            canvas.drawPath(fRefPath[i * 2], fillPaint)

            canvas.translate(0f, 50f)
            canvas.drawPath(fMoveHfPath, strokePaint.copy(strokeWidth = 20f, strokeCap = cap, color = Color.fromRGBA(0f, 0f, 0f, 0.5f)))
            canvas.translate(0f, 30f)
            canvas.drawPath(fMoveZfPath, strokePaint.copy(strokeWidth = 20f, strokeCap = cap, color = Color.fromRGBA(0f, 0f, 0f, 0.5f)))
            canvas.translate(0f, 30f)
            canvas.drawPath(fRefPath[1 + i * 2], fillPaint.copy(color = Color.fromRGBA(0f, 0f, 0f, 0.5f)))
            canvas.translate(0f, 30f)
            canvas.drawPath(fCubicPath, strokePaint.copy(strokeWidth = 20f, strokeCap = cap, color = Color.fromRGBA(0f, 0f, 0f, 0.5f)))
            canvas.translate(0f, 30f)
            canvas.drawPath(fQuadPath, strokePaint.copy(strokeWidth = 20f, strokeCap = cap, color = Color.fromRGBA(0f, 0f, 0f, 0.5f)))
            canvas.translate(0f, 30f)
            canvas.drawPath(fLinePath, strokePaint.copy(strokeWidth = 20f, strokeCap = cap, color = Color.fromRGBA(0f, 0f, 0f, 0.5f)))
            canvas.restore()
        }
    }
}
