package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/localmatrixshader.cpp` localmatrixshader_persp (542 × 266).
 * Tests perspective transformation with local matrix shaders on both
 * image shaders and gradient shaders, comparing multiple application
 * strategies (local matrix vs makeWithLocalMatrix vs pre-computed matrix).
 * @see https://github.com/google/skia/blob/main/gm/localmatrixshader.cpp
 */
class LocalMatrixShaderPerspGm : SkiaGm {
    override val name = "localmatrixshader_persp"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 542
    override val height = 266

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val bytes = this::class.java.classLoader?.getResourceAsStream("images/mandrill_128.png")?.readBytes()
        if (bytes == null) return
        val image = Image.decode(bytes)
        val fw = image.width.toFloat()
        val fh = image.height.toFloat()

        val scale = Matrix33.scale(0.2f, 0.2f)
        val imgShader = Shader.Image(image, TileMode.REPEAT, TileMode.REPEAT)

        val draw = { canvas: GmCanvas, shader: Shader, advance: Boolean ->
            canvas.save()
            canvas.drawRect(Rect(0f, 0f, fw, fh), Paint(shader = shader))
            canvas.restore()
            if (advance) canvas.translate(fw + 10f, 0f)
        }

        canvas.save()
        canvas.save()
        canvas.concat(Matrix33.makeAll(1f, 0.5f, 0f, -0.2f, 1f, 0f, 0.001f, 0f, 1f))
        draw(canvas, Shader.WithLocalMatrix(imgShader, scale), true)
        canvas.restore()

        val scaledShader = Shader.WithLocalMatrix(imgShader, scale)
        draw(canvas, scaledShader, true)
        draw(canvas, Shader.WithLocalMatrix(scaledShader, Matrix33.makeAll(1f, 0.5f, 0f, -0.2f, 1f, 0f, 0.001f, 0f, 1f)), true)

        canvas.restore()
        canvas.translate(0f, fh + 10f)

        val gradShader = Shader.RadialGradient(
            center = Point(fw / 2f, fh / 2f), radius = fw / 2f,
            stops = listOf(
                GradientStop(0f, Color.BLACK),
                GradientStop(1f, Color.TRANSPARENT),
            ),
            tileMode = TileMode.REPEAT,
        )

        canvas.save()
        canvas.concat(Matrix33.makeAll(1f, 0.5f, 0f, -0.2f, 1f, 0f, 0.001f, 0f, 1f))
        draw(canvas, Shader.WithLocalMatrix(gradShader, scale), true)
        canvas.restore()

        draw(canvas, Shader.WithLocalMatrix(gradShader, scale), true)
        draw(canvas, Shader.WithLocalMatrix(gradShader, scale * Matrix33.makeAll(1f, 0.5f, 0f, -0.2f, 1f, 0f, 0.001f, 0f, 1f)), true)
        canvas.restore()
    }
}
