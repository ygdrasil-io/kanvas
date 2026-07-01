package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/emptyshader.cpp`.
 * Exercises 5 shader cases that should each fall back to an "empty shader":
 * direct null, degenerate sweep, degenerate linear, degenerate radial,
 * degenerate conical.
 * @see https://github.com/google/skia/blob/main/gm/emptyshader.cpp
 */
class EmptyShaderGm : SkiaGm {
    override val name = "emptyshader"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 0.0
    override val width = 128
    override val height = 88

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0xCC / 255f, 0xCC / 255f, 0xCC / 255f)

        val stroke = Paint(style = PaintStyle.STROKE)

        val builders: List<(Rect) -> Shader?> = listOf(
            { _ -> null },
            { r -> degenSweep(r) },
            { r -> degenLinear(r) },
            { _ -> null },
            { r -> degenConical(r) },
        )

        var left = K_PAD.toFloat()
        var top = K_PAD.toFloat()
        for (build in builders) {
            val r = Rect.fromXYWH(left, top, K_SIZE.toFloat(), K_SIZE.toFloat())
            val p = Paint(color = Color.BLUE, shader = build(r))
            canvas.drawRect(r, p)
            canvas.drawRect(r, stroke)
            left += K_SIZE + K_PAD
            if (left >= width) {
                left = K_PAD.toFloat()
                top += K_SIZE + K_PAD
            }
        }
    }

    private fun degenSweep(r: Rect): Shader? {
        val start = 0f
        val end = Math.nextUp(start)
        return Shader.SweepGradient(
            center = Point(r.center.x, r.center.y),
            startAngle = start,
            endAngle = end,
            stops = listOf(
                GradientStop(0f, Color.fromRGBA(1f, 0f, 0f, 1f)),
                GradientStop(1f, Color.fromRGBA(0f, 1f, 0f, 1f)),
            ),
            tileMode = TileMode.DECAL,
        )
    }

    private fun degenLinear(r: Rect): Shader? {
        val pt = Point(r.center.x, r.center.y)
        return Shader.LinearGradient(
            start = pt, end = pt,
            stops = listOf(
                GradientStop(0f, Color.fromRGBA(1f, 0f, 0f, 1f)),
                GradientStop(1f, Color.fromRGBA(0f, 1f, 0f, 1f)),
            ),
            tileMode = TileMode.DECAL,
        )
    }

    private fun degenConical(r: Rect): Shader? {
        val pt = Point(r.center.x, r.center.y)
        return Shader.ConicalGradient(
            start = pt, startRadius = 0f,
            end = pt, endRadius = 0f,
            stops = listOf(
                GradientStop(0f, Color.fromRGBA(1f, 0f, 0f, 1f)),
                GradientStop(1f, Color.fromRGBA(0f, 1f, 0f, 1f)),
            ),
            tileMode = TileMode.DECAL,
        )
    }

    private companion object {
        private const val K_PAD = 8
        private const val K_SIZE = 32
    }
}
