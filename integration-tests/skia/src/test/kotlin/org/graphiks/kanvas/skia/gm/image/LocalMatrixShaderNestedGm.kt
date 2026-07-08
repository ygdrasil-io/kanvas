package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/localmatrixshader.cpp` localmatrixshader_nested (450 × 1200).
 * Tests that nested local matrix shaders compose correctly through multiple
 * levels of SkLocalMatrixShader and SkComposeShader wrapping.
 * @see https://github.com/google/skia/blob/main/gm/localmatrixshader.cpp
 */
class LocalMatrixShaderNestedGm : SkiaGm {
    override val name = "localmatrixshader_nested"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 450
    override val height = 1200

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val image = makeTestImage()
        val fw = image.width.toFloat()
        val fh = image.height.toFloat()

        val outer = Matrix33.scale(2f, 2f)
        val inner = Matrix33.translate(20f, 20f)

        val rect = Rect(0f, 0f, fw * 2f + 40f, fh * 2f + 40f)
        val border = Paint(color = Color.BLACK, style = PaintStyle.STROKE, strokeWidth = 1f)
        val empty = Paint()
        val baseShader = Shader.Image(image, TileMode.CLAMP, TileMode.CLAMP)
        val shaders = listOf(
            Shader.WithLocalMatrix(baseShader, inner * outer),
            Shader.WithLocalMatrix(Shader.WithLocalMatrix(baseShader, inner), outer),
            Shader.Blend(
                org.graphiks.kanvas.paint.BlendMode.SRC_OVER,
                Shader.SolidColor(Color.TRANSPARENT),
                Shader.WithLocalMatrix(baseShader, inner),
            ).let { Shader.WithLocalMatrix(it, outer) },
            Shader.Blend(
                org.graphiks.kanvas.paint.BlendMode.SRC_OVER,
                Shader.SolidColor(Color.TRANSPARENT),
                Shader.WithLocalMatrix(baseShader, inner),
            ).let { Shader.WithLocalMatrix(it, outer) },
        )

        for (pass in 0 until 3) {
            canvas.save()
            for (s in shaders) {
                canvas.drawRect(rect, Paint(shader = s))
                canvas.drawRect(rect, border)
                canvas.translate(0f, rect.height * 1.5f)
            }
            canvas.restore()
            if (pass == 0) {
                canvas.translate(0f, rect.height * shaders.size * 1.5f)
            } else if (pass == 1) {
                canvas.translate(rect.width * 1.5f, rect.height * shaders.size * 1.5f)
                canvas.scale(2f, 2f)
            }
        }
    }

    private fun makeTestImage(): Image {
        val surf = Surface(50, 50)
        surf.canvas {
            val circlePath = Path { }
            circlePath.addCircle(25f, 25f, 25f)
            drawPath(circlePath, Paint(color = Color.GREEN))

            val hLine = Path { moveTo(12.5f, 25f); lineTo(37.5f, 25f) }
            drawPath(hLine, Paint(color = Color.RED, strokeWidth = 2f, style = PaintStyle.STROKE))

            val vLine = Path { moveTo(25f, 12.5f); lineTo(25f, 37.5f) }
            drawPath(vLine, Paint(color = Color.RED, strokeWidth = 2f, style = PaintStyle.STROKE))
        }
        return surf.makeImageSnapshot()
    }
}
