package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

/**
 * Port of Skia's `DEF_SIMPLE_GM(clip_shader_difference, canvas, 512, 512)` in `gm/clip_shader.cpp`.
 * Tests clipShader with kDifference (SRC_OUT) using rectangle, round-rectangle, path, and text shapes.
 * @see https://github.com/google/skia/blob/main/gm/clip_shader.cpp
 */
class ClipShaderDifferenceGm : SkiaGm {
    override val name = "clip_shader_difference"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.TRIVIAL
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val bytes = this::class.java.classLoader?.getResourceAsStream("images/yellow_rose.png")?.readBytes() ?: return
        val image = Image.decode(bytes)
        canvas.drawColor(0.533f, 0.533f, 0.533f)

        val rect = Rect.fromXYWH(0f, 0f, 256f, 256f)
        val scaleX = 64f / image.width.toFloat()
        val scaleY = 64f / image.height.toFloat()
        val localMatrix = Matrix33.scale(scaleX, scaleY)
        val shader = Shader.WithLocalMatrix(
            Shader.Image(image, TileMode.REPEAT, TileMode.REPEAT, SamplingOptions.LINEAR),
            localMatrix,
        )

        val paint = Paint(color = Color.RED)

        // TL: rectangle with kDifference
        canvas.save()
        canvas.translate(0f, 0f)
        canvas.drawRect(rect, Paint(shader = shader))
        canvas.saveLayer(null, Paint(blendMode = BlendMode.SRC_OUT))
        canvas.drawRect(rect, paint)
        canvas.restore()
        canvas.restore()

        // TR: round-rectangle with kDifference
        canvas.save()
        canvas.translate(256f, 0f)
        canvas.drawRect(rect, Paint(shader = shader))
        canvas.saveLayer(null, Paint(blendMode = BlendMode.SRC_OUT))
        canvas.drawRRect(RRect(rect, 64f), paint)
        canvas.restore()
        canvas.restore()

        // BL: diamond + square path with kDifference
        canvas.save()
        canvas.translate(0f, 256f)
        canvas.drawRect(rect, Paint(shader = shader))
        canvas.saveLayer(null, Paint(blendMode = BlendMode.SRC_OUT))
        val path = Path {
            moveTo(0f, 128f)
            lineTo(128f, 256f)
            lineTo(256f, 128f)
            lineTo(128f, 0f)
            close()
        }.apply {
            val d = 64f * 1.41421356f
            addRect(Rect.fromLTRB(128f - d, 128f - d, 128f + d, 128f + d))
        }
        canvas.drawPath(path, paint)
        canvas.restore()
        canvas.restore()

        // BR: text "Hello" repeated 4 times with kDifference
        canvas.save()
        canvas.translate(256f, 256f)
        canvas.drawRect(rect, Paint(shader = shader))
        canvas.saveLayer(null, Paint(blendMode = BlendMode.SRC_OUT))
        val font = Font(typeface, size = 64f)
        for (y in 0 until 4) {
            canvas.drawString("Hello", 32f, y * 64f + 64f, font, paint)
        }
        canvas.restore()
        canvas.restore()
    }
}
