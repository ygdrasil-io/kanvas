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

/** Port of Skia's `gm/image_shader.cpp` (tiny-scale variant).
 *  Applies an image shader with a 0.01x scale matrix to a decoded JPEG
 *  to exercise very-small-scale image-shader rendering.
 *  @see https://github.com/google/skia/blob/main/gm/image_shader.cpp
 */
class ImageshaderTinyscaleGm : SkiaGm {
    override val name = "imageshader_tinyscale"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 1000
    override val height = 1000

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val bytes = loadResource("images/gainmap_gcontainer_only.jpg") ?: return
        val img = Image.decode(bytes)
        if (img.width == 0) return
        val kScale = 0.01f
        val lm = Matrix33.translate(500f, 500f) * Matrix33.scale(kScale, kScale)
        val shader = Shader.WithLocalMatrix(
            img.makeShader(TileMode.CLAMP, TileMode.CLAMP), lm,
        )
        canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), Paint(shader = shader))
    }

    private fun loadResource(path: String): ByteArray? =
        this::class.java.classLoader?.getResourceAsStream(path)?.readBytes()
}
