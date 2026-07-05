package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/shadermaskfilter.cpp`.
 * Builds a linear gradient wrapped in a ShaderMaskFilter, used as the
 * mask filter when drawing a red oval at 2x device-space scale.
 * @see https://github.com/google/skia/blob/main/gm/shadermaskfilter.cpp
 */
class ShaderMaskFilterGm : SkiaGm {
    override val name = "shadermaskfilter_gradient"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.TRIVIAL
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val r = Rect.fromLTRB(0f, 0f, 100f, 150f)

        val shader = Shader.LinearGradient(
            start = Point(r.left, r.top),
            end = Point(r.right, r.bottom),
            stops = listOf(
                GradientStop(0f, Color.fromRGBA(0f, 0f, 0f, 0f)),
                GradientStop(1f, Color.WHITE),
            ),
            tileMode = TileMode.REPEAT,
        )
        val mf = MaskFilter.Shader(shader)

        canvas.translate(20f, 20f)
        canvas.scale(2f, 2f)

        val paint = Paint(
            maskFilter = mf,
            color = Color.RED,
            antiAlias = true,
        )
        canvas.drawOval(r, paint)
    }
}
