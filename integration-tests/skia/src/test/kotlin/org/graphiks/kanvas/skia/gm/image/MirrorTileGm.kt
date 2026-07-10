package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.paint.TileMode

/**
 * Port of Skia's `gm/mirrortile.cpp::mirror_tile`.
 * Tests image-shader kMirror tile mode with scale factors of 1 and -1,
 * both nearest and linear filters, with/without half-pixel offset.
 * @see https://github.com/google/skia/blob/main/gm/mirrortile.cpp
 */
class MirrorTileGm : SkiaGm {
    override val name = "mirror_tile"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 140
    override val height = 370

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val imgx = Bitmap(3, 1).apply {
            setPixel(0, 0, Color.RED)
            setPixel(1, 0, Color.GREEN)
            setPixel(2, 0, Color.BLUE)
        }
        val imgy = Bitmap(1, 3).apply {
            setPixel(0, 0, Color.RED)
            setPixel(0, 1, Color.GREEN)
            setPixel(0, 2, Color.BLUE)
        }

        val offscreenW = 140 / 8 + 1
        val offscreenH = 370 / 8 + 1
        val surf = Surface(offscreenW, offscreenH)
        surf.canvas {
            drawColor(Color.WHITE)

            val offsets = booleanArrayOf(false, true)
            val filters = arrayOf(SamplingOptions.NEAREST, SamplingOptions.LINEAR)
            for (offset in offsets) {
                for (fm in filters) {
                    val paintH = Paint(shader = imgx.makeShader(
                        tileX = TileMode.MIRROR, tileY = TileMode.CLAMP, sampling = fm,
                    ))
                    save()
                    translate(imgx.width.toFloat(), 0f)
                    if (offset) translate(0.5f, 0f)
                    drawRect(
                        Rect(-imgx.width.toFloat(), 0f, 3f * imgx.width, 5f),
                        paintH,
                    )
                    restore()

                    val paintV = Paint(shader = imgy.makeShader(
                        tileX = TileMode.CLAMP, tileY = TileMode.MIRROR, sampling = fm,
                    ))
                    save()
                    translate(3f * imgx.width + 3f, imgy.height.toFloat())
                    if (offset) translate(0f, 0.5f)
                    drawRect(
                        Rect(0f, -imgy.height.toFloat(), 5f, 3f * imgy.height),
                        paintV,
                    )
                    restore()

                    translate(0f, 3f * imgy.height + 3f)
                }
            }
        }

        val snapshot = surf.makeImageSnapshot()
        canvas.scale(8f, 8f)
        canvas.drawImage(snapshot, Rect(0f, 0f, offscreenW.toFloat(), offscreenH.toFloat()))
    }
}
