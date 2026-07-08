package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/** Port of Skia's `gm/mipmap.cpp`.
 *  Creates a gradient image and draws it at various scales to test
 *  mipmap generation and rendering.
 *  @see https://github.com/google/skia/blob/main/gm/mipmap.cpp
 */
class MipmapGm : SkiaGm {
    override val name = "mipmap"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 400
    override val height = 200

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val img = makeImage()
        val dst = Rect.fromXYWH(0f, 0f, 177f, 15f)

        canvas.translate(20f, 20f)
        for (i in 0 until 4) {
            canvas.drawImageRect(img, Rect(0f, 0f, img.width.toFloat(), img.height.toFloat()), dst)
            canvas.translate(0f, 20f)
        }
        canvas.drawImage(img, Rect(20f, 20f, (20f + img.width), (20f + img.height)))
    }

    private fun makeImage(): Image {
        val surf = Surface(319, 52)
        surf.canvas { drawColor(Color.fromRGBA(248f / 255f, 248f / 255f, 248f / 255f)) }
        val paint = Paint(antiAlias = true, style = org.graphiks.kanvas.paint.PaintStyle.STROKE)
        surf.canvas {
            for (i in 0 until 20) {
                val circle = Path { }.apply { addCircle(-4f, 25f, 20f) }
                drawPath(circle, paint)
                translate(25f, 0f)
            }
        }
        return surf.makeImageSnapshot()
    }
}
