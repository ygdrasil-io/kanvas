package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.BlendMode
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
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `DEF_SIMPLE_GM(clip_shader_nested, canvas, 256, 256)` in `gm/complexclip.cpp`.
 * Tests nested clip-shaders: two stacked gradient shader masks, plus clipShader combined with RRect and star-path clipping.
 * @see https://github.com/google/skia/blob/main/gm/complexclip.cpp
 */
class ClipShaderNestedGm : SkiaGm {
    override val name = "clip_shader_nested"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val w = 64f
        val h = 64f

        val gradShader = Shader.RadialGradient(
            center = Point(0.5f * w, 0.5f * h),
            radius = 0.1f * w,
            stops = listOf(
                GradientStop(0f, Color.BLACK),
                GradientStop(1f, Color.fromRGBA(0.5f, 0.5f, 0.5f, 0.5f)),
            ),
            tileMode = TileMode.REPEAT,
        )

        // TL: large black rect through two nested gradient clip-shaders
        canvas.save()
        canvas.drawRect(Rect.fromXYWH(0f, 0f, w, h), Paint(shader = gradShader))
        canvas.saveLayer(null, Paint(blendMode = BlendMode.SRC_IN))
        canvas.scale(2f, 2f)
        canvas.drawRect(Rect.fromXYWH(0f, 0f, w, h), Paint(shader = gradShader))
        canvas.saveLayer(null, Paint(blendMode = BlendMode.SRC_IN))
        canvas.drawRect(Rect.fromXYWH(0f, 0f, 2f * w, 2f * h), Paint(color = Color.BLACK))
        canvas.restore()
        canvas.restore()
        canvas.restore()

        // BL: small red rect, no clipping
        canvas.translate(0f, 2f * h)
        canvas.save()
        canvas.drawRect(Rect.fromXYWH(0f, 0f, w, h), Paint(color = Color.RED))
        canvas.restore()

        // TR: small green rect, clip-shader + RRect clip
        canvas.translate(2f * w, -2f * h)
        canvas.save()
        canvas.drawRect(Rect.fromXYWH(0f, 0f, w, h), Paint(shader = gradShader))
        canvas.saveLayer(null, Paint(blendMode = BlendMode.SRC_IN))
        canvas.clipRRect(RRect(Rect.fromXYWH(0f, 0f, w, h), 10f), antiAlias = true)
        canvas.drawRect(Rect.fromXYWH(0f, 0f, w, h), Paint(color = Color.GREEN))
        canvas.restore()
        canvas.restore()

        // BR: small blue rect, clip-shader + star-path clip
        canvas.translate(0f, 2f * h)
        canvas.save()
        canvas.drawRect(Rect.fromXYWH(0f, 0f, w, h), Paint(shader = gradShader))
        canvas.saveLayer(null, Paint(blendMode = BlendMode.SRC_IN))
        val starPath = Path {
            moveTo(0.0f, -33.3333f)
            lineTo(9.62f, -16.6667f)
            lineTo(28.867f, -16.6667f)
            lineTo(19.24f, 0.0f)
            lineTo(28.867f, 16.6667f)
            lineTo(9.62f, 16.6667f)
            lineTo(0.0f, 33.3333f)
            lineTo(-9.62f, 16.6667f)
            lineTo(-28.867f, 16.6667f)
            lineTo(-19.24f, 0.0f)
            lineTo(-28.867f, -16.6667f)
            lineTo(-9.62f, -16.6667f)
            close()
        }
        canvas.translate(w / 2, h / 2)
        canvas.clipPath(starPath)
        canvas.translate(-w / 2, -h / 2)
        canvas.drawRect(Rect.fromXYWH(0f, 0f, w, h), Paint(color = Color.BLUE))
        canvas.restore()
        canvas.restore()
    }
}
