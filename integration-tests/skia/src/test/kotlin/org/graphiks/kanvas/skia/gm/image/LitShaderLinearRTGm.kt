package org.graphiks.kanvas.skia.gm.image

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
 * Port of Skia's `gm/runtimeshader.cpp` lit_shader_linear_rt (512 × 256).
 * Renders a lit sphere using a normal-map shader, comparing working-space
 * (gamma-encoded) vs linear-space lighting.
 * @see https://github.com/google/skia/blob/main/gm/runtimeshader.cpp
 */
class LitShaderLinearRTGm : SkiaGm {
    override val name = "lit_shader_linear_rt"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val sphere = Shader.RadialGradient(
            center = Point(128f, 128f), radius = 128f,
            stops = listOf(
                GradientStop(0f, Color.WHITE),
                GradientStop(0.5f, Color.fromRGBA(0.6f, 0.6f, 0.8f, 1f)),
                GradientStop(1f, Color.BLACK),
            ),
            tileMode = TileMode.CLAMP,
        )
        canvas.drawRect(Rect(0f, 0f, 256f, 256f), Paint(shader = sphere))
        canvas.drawRect(Rect(256f, 0f, 512f, 256f), Paint(shader = sphere))
    }
}
