package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/surface.cpp::snap_with_mips`.
 * Repeatedly snap a small surface and draw at decreasing scale.
 * @see https://github.com/google/skia/blob/main/gm/surface.cpp
 */
class SnapWithMipsGm : SkiaGm {
    override val name = "snap_with_mips"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 80
    override val height = 75

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val surface = Surface(32, 32)
        val kPad = 8

        fun nextImage(color: Color): Image {
            surface.canvas { clear(color) }
            val w = 32f
            val h = 32f
            val contrastPacked = color.packed.inv() or 0xFF000000u
            val contrastColor = Color(contrastPacked)
            surface.canvas {
                drawRect(
                    Rect.fromLTRB(w * 2f / 5f, h * 2f / 5f, w * 3f / 5f, h * 3f / 5f),
                    Paint(color = contrastColor),
                )
            }
            return surface.makeImageSnapshot()
        }

        val kColors = listOf(Color(0xFFF0F0F0u), Color.BLUE)

        canvas.save()
        for (y in 0 until 3) {
            canvas.save()
            for (x in 0 until 2) {
                val image = nextImage(kColors[x])
                canvas.drawImage(image, Rect.fromXYWH(0f, 0f, 32f, 32f))
                canvas.translate(40f, 0f)
            }
            canvas.restore()
            canvas.translate(0f, 40f)
            canvas.scale(0.4f, 0.4f)
        }
        canvas.restore()
    }
}
