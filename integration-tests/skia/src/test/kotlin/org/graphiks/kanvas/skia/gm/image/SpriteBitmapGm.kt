package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.canvas.drawCircle
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class SpriteBitmapGm : SkiaGm {
    override val name = "spritebitmap"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 480

    private val bm: Image by lazy { makeBm() }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val dx = 10
        var dy = 10

        draw1Bitmap(canvas, doClip = false, dx, dy, blur = false)
        dy += 120
        draw1Bitmap(canvas, doClip = false, dx, dy, blur = true)
        dy += 120
        draw1Bitmap(canvas, doClip = true, dx, dy, blur = false)
        dy += 120
        draw1Bitmap(canvas, doClip = true, dx, dy, blur = true)
    }

    private fun draw1Bitmap(c: GmCanvas, doClip: Boolean, dx: Int, dy: Int, blur: Boolean) {
        c.save()
        val clipR = Rect(dx.toFloat(), dy.toFloat(), (dx + 100).toFloat(), (dy + 100).toFloat())
        val insetClip = Rect(clipR.left + 5f, clipR.top + 5f, clipR.right - 5f, clipR.bottom - 5f)

        val paint = if (blur) {
            Paint(imageFilter = ImageFilter.Blur(8f, 8f))
        } else {
            Paint()
        }

        c.translate(120f, 0f)

        if (doClip) {
            c.save()
            c.clipRect(insetClip)
        }
        c.drawImage(bm, Rect(dx.toFloat(), dy.toFloat(), (dx + 100).toFloat(), (dy + 100).toFloat()), paint)
        if (doClip) {
            c.restore()
        }
        c.restore()
    }

    private fun makeBm(): Image {
        val surf = Surface(100, 100)
        surf.canvas {
            drawColor(Color.BLUE)
            val paint = Paint(color = Color.RED, antiAlias = true)
            drawCircle(50f, 50f, 50f, paint)
        }
        return surf.makeImageSnapshot()
    }
}
