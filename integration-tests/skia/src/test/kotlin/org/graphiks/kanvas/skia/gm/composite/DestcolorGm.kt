package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32

/**
 * Port of Skia's `gm/destcolor.cpp`.
 * Tests DST color blending with a mandrill image and DIFFERENCE mode oval.
 * @see https://github.com/google/skia/blob/main/gm/destcolor.cpp
 */
class DestcolorGm : SkiaGm {
    override val name = "destcolor"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 640

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val bytes = this::class.java.classLoader
            ?.getResourceAsStream("images/mandrill_512.png")
            ?.readBytes()
        val mandrill = bytes?.let { Image.decode(it) }
        if (mandrill != null) {
            canvas.drawImage(mandrill, RectF32(0f, 0f, 512f, 512f))
        }

        val paint = Paint(
            color = ColorARGB.fromRGBA(1f, 1f, 1f, 1f),
            blendMode = BlendMode.DIFFERENCE,
            antiAlias = true,
        )
        canvas.drawOval(RectF32.ofLTRB(128f, 128f, 640f, 640f), paint)
    }
}
