package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `DEF_SIMPLE_GM(clip_shader_layer, canvas, 430, 320)` in `gm/complexclip.cpp`.
 * Tests clipShader interaction with saveLayer: an image shader clip mask followed by a nested saveLayer with solid red fill.
 * @see https://github.com/google/skia/blob/main/gm/complexclip.cpp
 */
class ClipShaderLayerGm : SkiaGm {
    override val name = "clip_shader_layer"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 0.0
    override val width = 430
    override val height = 320

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val bytes = this::class.java.classLoader?.getResourceAsStream("images/yellow_rose.png")?.readBytes() ?: return
        val img = Image.decode(bytes)
        val shader = img.makeShader(TileMode.CLAMP, TileMode.CLAMP, SamplingOptions.LINEAR)
        val imgRect = Rect(0f, 0f, img.width.toFloat(), img.height.toFloat())

        canvas.translate(10f, 10f)
        canvas.clipRect(imgRect)

        // Apply clipShader as SRC_IN mask
        canvas.drawRect(imgRect, Paint(shader = shader))
        canvas.saveLayer(null, Paint(blendMode = BlendMode.SRC_IN))

        // saveLayer as in the original, then fill red
        canvas.saveLayer(imgRect, null)
        canvas.drawColor(1f, 0f, 0f)
        canvas.restore()

        canvas.restore()
    }
}
