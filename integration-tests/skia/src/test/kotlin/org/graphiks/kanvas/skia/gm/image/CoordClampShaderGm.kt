package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/coordclampshader.cpp::coordclampshader`.
 * @see https://github.com/google/skia/blob/main/gm/coordclampshader.cpp
 */
class CoordClampShaderGm : SkiaGm {
    override val name = "coordclampshader"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 1074
    override val height = 795

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        val bytes = loadResource("images/mandrill_256.png") ?: return
        var image = Image.decode(bytes, "image/png")
        if (image.width > 0 && image.pixels != null) {
            val trimmed = ByteArray((image.height - 1) * image.width * 4)
            System.arraycopy(image.pixels, 0, trimmed, 0, trimmed.size)
            image = Image(image.width, image.height - 1, sourceId = "mandrill_256_trimmed", pixels = trimmed)
        }

        val imageShader = image.makeShader(TileMode.CLAMP, TileMode.CLAMP, SamplingOptions.LINEAR)
        val drawRect = Rect.fromLTRB(0f, 0f, image.width.toFloat(), image.height.toFloat())
        val cx = image.width.toFloat() / 2f
        val cy = image.height.toFloat() / 2f
        val rotate = Matrix33.translate(cx, cy) * Matrix33.rotate(45f) * Matrix33.translate(-cx, -cy)
        val clampRect = Rect(drawRect.left + 20f, drawRect.top + 40f, drawRect.right - 20f, drawRect.bottom - 40f)

        canvas.translate(10f, 10f)

        canvas.drawRect(drawRect, Paint(shader = Shader.CoordClamp(imageShader, clampRect)))

        canvas.save()
        canvas.translate(image.width.toFloat(), 0f)
        canvas.drawRect(drawRect, Paint(shader = Shader.CoordClamp(
            Shader.WithLocalMatrix(imageShader, rotate), clampRect,
        )))
        canvas.restore()

        canvas.save()
        canvas.translate(0f, image.height.toFloat())
        canvas.drawRect(drawRect, Paint(shader = Shader.WithLocalMatrix(
            Shader.CoordClamp(imageShader, clampRect), rotate,
        )))
        canvas.restore()

        canvas.save()
        canvas.translate(image.width.toFloat(), image.height.toFloat())
        canvas.drawRect(drawRect, Paint(shader = Shader.WithLocalMatrix(
            Shader.CoordClamp(Shader.WithLocalMatrix(imageShader, rotate), clampRect), rotate,
        )))
        canvas.restore()

        canvas.translate(0f, 2f * image.height.toFloat() + 10f)

        val samplers = listOf(
            SamplingOptions.NEAREST,
            SamplingOptions.LINEAR,
            SamplingOptions.LINEAR,
            SamplingOptions.Cubic.Mitchell,
        )
        val scale03 = Matrix33.scale(0.3f, 1f)
        for (sampler in samplers) {
            val s = image.makeShader(TileMode.MIRROR, TileMode.MIRROR, sampler)
            canvas.drawRect(drawRect, Paint(shader = Shader.CoordClamp(Shader.WithLocalMatrix(s, scale03), clampRect)))
            canvas.translate(image.width.toFloat() + 10f, 0f)
        }
    }

    private fun loadResource(path: String): ByteArray? =
        this::class.java.classLoader?.getResourceAsStream(path)?.readBytes()
}
