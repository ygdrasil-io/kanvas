package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import kotlin.random.Random

/** Port of Skia's `gm/drawminibitmaprect.cpp`.
 *  Tests mini bitmap-rect drawing — creates a gradient image and renders
 *  it with various local-matrix shader variants.
 *  @see https://github.com/google/skia/blob/main/gm/drawminibitmaprect.cpp
 */
class DrawMiniBitmapRectGm : SkiaGm {
    override val name = "drawminibitmaprect"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = GSIZE
    override val height = GSIZE

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val image = sharedImage()
        val dstRect = Rect.fromXYWH(0f, 0f, 64f, 64f)
        val kMaxSrcRectSize = 1 shl (nextLog2(GSURFACE_SIZE) + 2)
        val kPadX = 30
        val kPadY = 40

        var rowCount = 0
        canvas.translate(kPadX.toFloat(), kPadY.toFloat())
        canvas.save()
        val random = Random(42)

        var w = 1
        while (w <= kMaxSrcRectSize) {
            var h = 1
            while (h <= kMaxSrcRectSize) {
                val srcRect = Rect.fromXYWH(
                    ((GSURFACE_SIZE - w) / 2).toFloat(),
                    ((GSURFACE_SIZE - h) / 2).toFloat(),
                    w.toFloat(),
                    h.toFloat(),
                )
                canvas.save()
                when (random.nextInt() % 3) {
                    0 -> canvas.rotate(random.nextFloat() * 10f)
                    1 -> canvas.rotate(-random.nextFloat() * 10f)
                }
                canvas.drawImageRect(image, srcRect, dstRect)
                canvas.restore()

                canvas.translate(dstRect.width + kPadX, 0f)
                rowCount++
                if ((dstRect.width + 2 * kPadX) * rowCount > GSIZE) {
                    canvas.restore()
                    canvas.translate(0f, dstRect.height + kPadY)
                    canvas.save()
                    rowCount = 0
                }
                h *= 3
            }
            w *= 3
        }
        canvas.restore()
    }

    private companion object {
        private const val GSIZE = 1024
        private const val GSURFACE_SIZE = 2048

        private fun nextLog2(n: Int): Int {
            require(n > 0)
            var v = n - 1
            var log = 0
            while (v > 0) { v = v ushr 1; log++ }
            return log
        }

        @Volatile private var cachedImage: Image? = null

        fun sharedImage(): Image {
            cachedImage?.let { return it }
            synchronized(this) {
                cachedImage?.let { return it }
                val img = makebm(GSURFACE_SIZE, GSURFACE_SIZE)
                cachedImage = img
                return img
            }
        }

        private fun makebm(w: Int, h: Int): Image {
            val surface = Surface(w, h)
            val wScalar = w.toFloat()
            val hScalar = h.toFloat()
            val pt = Point(wScalar / 2f, hScalar / 2f)
            val radius = 4f * maxOf(wScalar, hScalar)

            val colors = listOf(
                Color.RED, Color.fromRGBA(1f, 1f, 0f, 1f), Color.GREEN,
                Color.fromRGBA(1f, 0f, 1f, 1f), Color.BLUE, Color.fromRGBA(0f, 1f, 1f, 1f), Color.RED,
            )
            val pos = floatArrayOf(
                0f, 1f / 6f, 2f / 6f, 3f / 6f, 4f / 6f, 5f / 6f, 1f,
            )
            val stops = colors.mapIndexed { i, c -> GradientStop(pos[i], c) }

            surface.canvas {
                var rect = Rect.fromXYWH(0f, 0f, wScalar, hScalar)
                var mat = Matrix33.identity()
                for (i in 0 until 4) {
                    val shader = Shader.RadialGradient(
                        center = pt,
                        radius = radius,
                        stops = stops,
                        tileMode = TileMode.REPEAT,
                    )
                    drawRect(rect, Paint(shader = Shader.WithLocalMatrix(shader, mat)))
                    val inset = wScalar / 8f
                    rect = Rect(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
                    mat = mat * Matrix33.scale(0.25f, 0.25f)
                }
            }
            return surface.makeImageSnapshot()
        }
    }
}
