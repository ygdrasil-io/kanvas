package org.graphiks.kanvas.skia.gm.composite

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
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `DEF_SIMPLE_GM(clip_shader, canvas, 840, 650)` in `gm/clip_shader.cpp`.
 * Tests basic clipShader operations: intersect (SRC_IN), difference (SRC_OUT), and two nested intersects using an image shader.
 * @see https://github.com/google/skia/blob/main/gm/clip_shader.cpp
 */
class ClipShaderSimpleGm : SkiaGm {
    override val name = "clip_shader"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 840
    override val height = 650

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val bytes = this::class.java.classLoader?.getResourceAsStream("images/yellow_rose.png")?.readBytes() ?: return
        val img = Image.decode(bytes)
        val shader = img.makeShader(TileMode.CLAMP, TileMode.CLAMP, SamplingOptions.LINEAR)
        val imgRect = Rect(0f, 0f, img.width.toFloat(), img.height.toFloat())

        canvas.translate(10f, 10f)

        // TL: original image (no clip-shader)
        canvas.drawImage(img, imgRect)

        // TR: intersect clip-shader -> draw red rect
        canvas.save()
        canvas.translate(img.width + 10f, 0f)
        canvas.drawRect(imgRect, Paint(shader = shader))
        canvas.saveLayer(null, Paint(blendMode = BlendMode.SRC_IN))
        canvas.drawRect(imgRect, Paint(color = Color.RED))
        canvas.restore()
        canvas.restore()

        // BL: difference clip-shader -> draw green rect
        canvas.save()
        canvas.translate(0f, img.height + 10f)
        canvas.drawRect(imgRect, Paint(shader = shader))
        canvas.saveLayer(null, Paint(blendMode = BlendMode.SRC_OUT))
        canvas.drawRect(imgRect, Paint(color = Color.GREEN))
        canvas.restore()
        canvas.restore()

        // BR: two nested intersect clip-shaders -> draw image
        canvas.save()
        canvas.translate(img.width + 10f, img.height + 10f)

        // First clip: image shader at natural scale
        canvas.drawRect(imgRect, Paint(shader = shader))
        canvas.saveLayer(null, Paint(blendMode = BlendMode.SRC_IN))

        // Second clip: image shader at 1/5 scale, tiled
        val scale = Matrix33.scale(1.0f / 5, 1.0f / 5)
        val scaledShader = Shader.WithLocalMatrix(
            Shader.Image(img, TileMode.REPEAT, TileMode.REPEAT, SamplingOptions.LINEAR),
            scale,
        )
        canvas.drawRect(imgRect, Paint(shader = scaledShader))
        canvas.saveLayer(null, Paint(blendMode = BlendMode.SRC_IN))

        canvas.drawImage(img, imgRect)

        canvas.restore()
        canvas.restore()
        canvas.restore()
    }
}
