package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/runtimeshader.cpp` local_matrix_shader_rt (256 × 256).
 * Regression test for skbug.com/40044685 — GPU was double-applying the
 * local matrix when a runtime shader wrapped an image shader with a local matrix.
 * @see https://github.com/google/skia/blob/main/gm/runtimeshader.cpp
 */
class LocalMatrixShaderRTGm : SkiaGm {
    override val name = "local_matrix_shader_rt"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val bytes = this::class.java.classLoader?.getResourceAsStream("images/mandrill_128.png")?.readBytes()
        if (bytes == null) return
        val image = Image.decode(bytes)
        val fw = image.width.toFloat()
        val fh = image.height.toFloat()

        val imgShader = Shader.Image(image, TileMode.CLAMP, TileMode.CLAMP)
        val r = Rect(0f, 0f, fw, fh)

        canvas.drawRect(r, Paint(shader = imgShader))

        canvas.save()
        canvas.translate(fw, 0f)
        canvas.drawRect(r, Paint(shader = imgShader))
        canvas.restore()

        canvas.save()
        canvas.translate(0f, fh)
        val cx = fw / 2f; val cy = fh / 2f
        val lm = Matrix33.translate(cx, cy) * Matrix33.rotate(90f) * Matrix33.translate(-cx, -cy)
        val localImgShader = Shader.WithLocalMatrix(imgShader, lm)
        canvas.drawRect(r, Paint(shader = localImgShader))
        canvas.restore()

        canvas.save()
        canvas.translate(fw, fh)
        canvas.drawRect(r, Paint(shader = localImgShader))
        canvas.restore()
    }
}
