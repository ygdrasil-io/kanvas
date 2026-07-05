package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/cubicpaths.cpp::CubicPathShaderGM` (1240x390).
 * Open cubic path drawn with a 3-stop linear gradient shader.
 * @see https://github.com/google/skia/blob/main/gm/cubicpaths.cpp
 */
class CubicPathShaderGm : SkiaGm {
    override val name = "cubicpath_shader"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 1240
    override val height = 390

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val path = Path {
            moveTo(25f, 10f)
            cubicTo(40f, 20f, 60f, 20f, 75f, 10f)
        }

        val shader = Shader.LinearGradient(
            start = Point(0f, 0f),
            end = Point(50f, 50f),
            stops = listOf(
                GradientStop(0f, Color.fromRGBA(0x80 / 255f, 0xF0 / 255f, 0x00 / 255f, 0x80 / 255f)),
                GradientStop(0.5f, Color.fromRGBA(0xF0 / 255f, 0xF0 / 255f, 0x80 / 255f, 0xF0 / 255f)),
                GradientStop(1f, Color.fromRGBA(0x80 / 255f, 0x00 / 255f, 0xF0 / 255f, 0x80 / 255f)),
            ),
            tileMode = TileMode.CLAMP,
        )

        val paint = Paint(shader = shader, strokeWidth = 10f, antiAlias = true)
        canvas.drawPath(path, paint)
    }
}
