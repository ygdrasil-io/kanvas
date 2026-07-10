package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/** Port of Skia's `gm/bitmaprect.cpp` (draw-bitmap-rect variant 1).
 *  Draws a colour-wheel bitmap with various shaders, blur mask filters,
 *  and styles to test bitmap-rect rendering.
 *  @see https://github.com/google/skia/blob/main/gm/bitmaprect.cpp
 */
class DrawBitmapRectGm(private val variant: Variant) : SkiaGm {
    enum class Variant(val suffix: String?) {
        BITMAP(null), BITMAP_SUBSET("-subset"), IMAGE("-imagerect"), IMAGE_SUBSET("-imagerect-subset")
    }

    override val name = "drawbitmaprect" + (variant.suffix ?: "")
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = gSize
    override val height = gSize

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val shared = sharedImageAndBitmap()
        val image = shared.first; val bitmap = shared.second
        val dstRect = Rect.fromXYWH(0f, 0f, 64f, 64f)
        val kMaxSrcRectSize = 1 shl (nextLog2(gBmpSize) + 2)
        val kPadX = 30; val kPadY = 40

        val alphaPaint = Paint(color = Color.fromRGBA(0f, 0f, 0f, 0.125f))
        canvas.drawImageRect(image, Rect.fromXYWH(0f, 0f, gBmpSize.toFloat(), gBmpSize.toFloat()), Rect.fromXYWH(0f, 0f, gSize.toFloat(), gSize.toFloat()), alphaPaint)
        canvas.translate((kPadX / 2).toFloat(), (kPadY / 2).toFloat())

        val font = Font(typeface, 24f)
        canvas.drawString("Bitmap size: $gBmpSize x $gBmpSize", 0f, 24f, font, Paint(color = Color.BLACK, antiAlias = true))
        canvas.translate(0f, (kPadY / 2).toFloat() + 24f)
        var rowCount = 0; canvas.save()
        var w = 1
        while (w <= kMaxSrcRectSize) {
            var h = 1
            while (h <= kMaxSrcRectSize) {
                val srcRect = Rect.fromXYWH(((gBmpSize - w) / 2).toFloat(), ((gBmpSize - h) / 2).toFloat(), w.toFloat(), h.toFloat())
                runProc(canvas, image, bitmap, srcRect, dstRect, null)
                canvas.drawString("$w x $h", 0f, dstRect.height + 13f, Font(typeface, 10f), Paint(color = Color.BLACK, antiAlias = true))
                canvas.drawRect(dstRect, Paint(color = Color.BLACK, antiAlias = false, style = PaintStyle.STROKE, strokeWidth = 1f))
                canvas.translate(dstRect.width + kPadX, 0f)
                rowCount++
                if ((dstRect.width + kPadX) * rowCount > gSize) {
                    canvas.restore(); canvas.translate(0f, dstRect.height + kPadY); canvas.save(); rowCount = 0
                }
                h *= 4
            }
            w *= 4
        }
        canvas.restore()

        // Mask-filter chessboard draw
        val maskPaint = Paint(maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 0.57735f * 5f + 0.5f))
        val chessBm = makeChessBm(5, 5)
        val chessImage = chessBm.toImage()
        runProc(canvas, chessImage, chessBm, Rect.fromXYWH(1f, 1f, 3f, 3f), dstRect, maskPaint)
    }

    private fun runProc(canvas: GmCanvas, image: Image, bitmap: Bitmap, srcR: Rect, dstR: Rect, paint: Paint?) {
        when (variant) {
            Variant.BITMAP -> canvas.drawImageRect(image, srcR, dstR, paint)
            Variant.BITMAP_SUBSET -> {
                if (srcR.left >= 0 && srcR.top >= 0 && srcR.right <= bitmap.width && srcR.bottom <= bitmap.height) {
                    val subset = bitmap.extractSubset(srcR)
                    canvas.drawImageRect(subset.toImage(), Rect.fromXYWH(0f, 0f, subset.width.toFloat(), subset.height.toFloat()), dstR, paint)
                } else {
                    canvas.drawImageRect(image, srcR, dstR, paint)
                }
            }
            Variant.IMAGE -> canvas.drawImageRect(image, srcR, dstR, paint)
            Variant.IMAGE_SUBSET -> canvas.drawImageRect(image, srcR, dstR, paint)
        }
    }

    private companion object {
        const val gSize = 1024; const val gBmpSize = 2048
        private var cachedPair: Pair<Image, Bitmap>? = null

        fun sharedImageAndBitmap(): Pair<Image, Bitmap> {
            cachedPair?.let { return it }
            val pair = makeImageAndBitmap(gBmpSize, gBmpSize)
            cachedPair = pair; return pair
        }

        fun nextLog2(n: Int): Int { require(n > 0); var v = n - 1; var log = 0; while (v > 0) { v = v ushr 1; log++ }; return log }

        fun makeChessBm(w: Int, h: Int): Bitmap {
            val bm = Bitmap(w, h)
            for (y in 0 until h) for (x in 0 until w) bm.setPixel(x, y, if (((x + y) and 1) != 0) Color.WHITE else Color.BLACK)
            return bm
        }

        fun makeImageAndBitmap(w: Int, h: Int): Pair<Image, Bitmap> {
            val surface = Surface(w, h)
            surface.canvas {
                val wF = w.toFloat(); val hF = h.toFloat()
                val pt = Point(wF / 2f, hF / 2f); val radius = 4f * maxOf(wF, hF)
                val colors = listOf(Color.RED, Color.fromRGBA(1f, 1f, 0f), Color.GREEN, Color.fromRGBA(1f, 0f, 1f), Color.BLUE, Color.fromRGBA(0f, 1f, 1f), Color.RED)
                val pos = floatArrayOf(0f, 1f / 6f, 2f / 6f, 3f / 6f, 4f / 6f, 5f / 6f, 1f)
                val stops = colors.mapIndexed { i, c -> GradientStop(pos[i], c) }
                var rect = Rect.fromXYWH(0f, 0f, wF, hF)
                var mat = Matrix33.identity()
                for (i in 0 until 4) {
                    drawRect(rect, Paint(shader = Shader.WithLocalMatrix(Shader.RadialGradient(pt, radius, stops, TileMode.REPEAT), mat)))
                    rect = Rect(rect.left + wF / 8f, rect.top + hF / 8f, rect.right - wF / 8f, rect.bottom - hF / 8f)
                    mat = Matrix33.scale(0.25f, 0.25f) * mat
                }
            }
            val image = surface.makeImageSnapshot()
            val bitmap = Bitmap.fromImage(image)
            return Pair(image, bitmap)
        }

        fun newBitmap() = DrawBitmapRectGm(Variant.BITMAP)
        fun newSubset() = DrawBitmapRectGm(Variant.BITMAP_SUBSET)
        fun newImage() = DrawBitmapRectGm(Variant.IMAGE)
        fun newImageSubset() = DrawBitmapRectGm(Variant.IMAGE_SUBSET)
    }
}
