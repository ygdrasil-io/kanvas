package org.graphiks.kanvas.skia.gm.image

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
 * Port of Skia's `gm/localmatriximageshader.cpp::localmatriximageshader_filtering` (256 x 256).
 * Tests that filtering decisions (e.g. bicubic for upscale) are made correctly
 * when the scale comes from a local matrix shader.
 * @see https://github.com/google/skia/blob/main/gm/localmatriximageshader.cpp
 */
class LocalMatrixImageShaderFilteringGm : SkiaGm {
    override val name = "localmatriximageshader_filtering"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val bytes = this::class.java.classLoader?.getResourceAsStream("images/mandrill_128.png")?.readBytes()
        if (bytes == null) return
        val image = org.graphiks.kanvas.image.Image.decode(bytes)
        val m = Matrix33.scale(2f, 2f)
        val shader = Shader.Image(image, TileMode.CLAMP, TileMode.CLAMP, SamplingOptions.Cubic.Mitchell)
        val localShader = Shader.WithLocalMatrix(shader, m)
        val paint = Paint(shader = localShader)
        canvas.drawRect(Rect.fromXYWH(0f, 0f, 256f, 256f), paint)
    }
}
