package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/ninepatchstretch.cpp` (`NinePatchStretchGM`,
 * GM name `ninepatch-stretch`, 760 × 800).
 *
 * Builds a 64x64 source image — red rounded rectangle with green vertical
 * and blue horizontal middle strips — then renders 8 stretched
 * destinations through drawImageNine.
 * @see https://github.com/google/skia/blob/main/gm/ninepatchstretch.cpp
 */
class NinePatchStretchGm : SkiaGm {
    override val name = "ninepatch-stretch"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 760
    override val height = 800

    private var fImage: Image? = null
    private var fCenter = Rect(0f, 0f, 0f, 0f)

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        if (fImage == null) {
            fImage = makeImage(canvas)
        }
        val img = fImage!!

        val fixed = (img.width - fCenter.width).toFloat()

        val sizes = listOf(
            Rect.fromXYWH(0f, 0f, fixed * 4f / 5f, fixed * 4f / 5f),
            Rect.fromXYWH(0f, 0f, fixed * 4f / 5f, fixed * 4f),
            Rect.fromXYWH(0f, 0f, fixed * 4f, fixed * 4f / 5f),
            Rect.fromXYWH(0f, 0f, fixed * 4f, fixed * 4f),
        )

        canvas.drawImage(img, Rect.fromXYWH(10f, 10f, 80f, 80f))

        val x = 100f
        val y = 100f

        for (pass in 0..1) {
            for (iy in 0..1) {
                for (ix in 0..1) {
                    val i = ix * 2 + iy
                    val r = Rect.fromXYWH(
                        x + ix * fixed,
                        y + iy * fixed,
                        sizes[i].width,
                        sizes[i].height,
                    )
                    canvas.drawImageNine(img, fCenter, r)
                }
            }
            canvas.translate(0f, 400f)
        }
    }

    private fun makeImage(canvas: GmCanvas): Image {
        val kFixed = 28
        val kStretchy = 8
        val kSize = 2 * kFixed + kStretchy

        val surf = Surface(kSize, kSize)
        val sizeF = kSize.toFloat()

        fCenter = Rect.fromLTRB(
            kFixed.toFloat(), kFixed.toFloat(),
            (kFixed + kStretchy).toFloat(), (kFixed + kStretchy).toFloat(),
        )

        surf.canvas {
            var r = Rect.fromXYWH(0f, 0f, sizeF, sizeF)
            val strokeWidth = 6f
            val radius = kFixed.toFloat() - strokeWidth / 2f

            val paint = Paint(antiAlias = true, color = Color.RED)
            drawRRect(RRect(r, radius), paint)

            r = Rect.fromXYWH(kFixed.toFloat(), 0f, kStretchy.toFloat(), sizeF)
            val paint2 = Paint(color = Color.fromRGBA(136f / 255f, 1f, 0f))
            drawRect(r, paint2)

            r = Rect.fromXYWH(0f, kFixed.toFloat(), sizeF, kStretchy.toFloat())
            val paint3 = Paint(color = Color.fromRGBA(136f / 255f, 0f, 1f))
            drawRect(r, paint3)
        }
        return surf.makeImageSnapshot()
    }
}
