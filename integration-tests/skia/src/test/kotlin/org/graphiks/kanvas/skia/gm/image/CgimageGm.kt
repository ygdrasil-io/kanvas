package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32

/**
 * Port of Skia's CGImage GM (800 × 250).
 * Tests drawing with CGImage-backed images (macOS platform).
 * Simplified Kanvas port using an offscreen surface snapshot.
 * @see https://github.com/google/skia/blob/main/gm/cgimage.cpp
 */
class CgimageGm : SkiaGm {
    override val name = "cgimage"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 800
    override val height = 250

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val surf = Surface(256, 256)
        surf.canvas {
            drawRect(RectF32(10f, 10f, 246f, 246f), Paint(color = ColorARGB.Blue))
            drawRect(RectF32(20f, 20f, 236f, 236f), Paint(color = ColorARGB.Green))
            drawRect(RectF32(30f, 30f, 226f, 226f), Paint(color = ColorARGB.Red))
        }
        val img = surf.makeImageSnapshot()
        canvas.drawImage(img, RectF32(0f, 0f, 256f, 256f))
        canvas.drawImage(img, RectF32(256f, 0f, 512f, 256f))
        canvas.drawImage(img, RectF32(512f, 0f, 768f, 256f))
    }
}
