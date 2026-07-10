package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/savelayer.cpp` (savelayer_initfromprev).
 * Exercises saveLayer with kInitWithPrevious flag (ignored in Kanvas).
 * Draws mandrill, opens layer with kPlus blend, punches a hole with
 * kClear circle, restores.
 * @see https://github.com/google/skia/blob/main/gm/savelayer.cpp
 */
class SaveLayerGm : SkiaGm {
    override val name = "savelayer_initfromprev"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val bytes = loadResource("images/mandrill_256.png")
            ?: return
        val image = Image.decode(bytes)
        canvas.drawImage(image, Rect.fromXYWH(0f, 0f, 256f, 256f))

        val layerPaint = Paint(
            blendMode = BlendMode.PLUS,
        )
        canvas.saveLayer(paint = layerPaint)

        val clearPaint = Paint(
            blendMode = BlendMode.CLEAR,
        )
        canvas.drawCircle(128f, 128f, 96f, clearPaint)
        canvas.restore()
    }

    private fun loadResource(path: String): ByteArray? {
        return this::class.java.classLoader?.getResourceAsStream(path)?.readBytes()
    }
}
