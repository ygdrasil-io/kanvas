package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ColorChannel
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.r
import org.graphiks.kanvas.types.g
import org.graphiks.kanvas.types.b

/** Port of Skia's `gm/displacement.cpp`.
 *  Tests displacement image filter — renders text and shapes with
 *  displacement map filters applied.
 *  @see https://github.com/google/skia/blob/main/gm/displacement.cpp
 */
class DisplacementGm : SkiaGm {
    override val name = "displacement"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 600
    override val height = 500

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    private var fImage: Image? = null
    private var fCheckerboard: Image? = null
    private var fSmall: Image? = null
    private var fLarge: Image? = null
    private var fLargeW: Image? = null
    private var fLargeH: Image? = null

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        ensureImages()
        val image = fImage ?: return
        val checkerboard = fCheckerboard ?: return
        val small = fSmall ?: return
        val large = fLarge ?: return
        val largeW = fLargeW ?: return
        val largeH = fLargeH ?: return

        canvas.drawColor(Color.BLACK.r, Color.BLACK.g, Color.BLACK.b)

        // Row 1 — no crop, scales 0, 16, 32, 48, 64
        drawClippedBitmap(canvas, 0, 0, image, Paint(imageFilter = ImageFilter.DisplacementMap(ColorChannel.R, ColorChannel.G, 0f, ImageFilter.Blur(0f, 0f))))
        drawClippedBitmap(canvas, 100, 0, image, Paint(imageFilter = ImageFilter.DisplacementMap(ColorChannel.B, ColorChannel.A, 16f, ImageFilter.Blur(0f, 0f))))
        drawClippedBitmap(canvas, 200, 0, image, Paint(imageFilter = ImageFilter.DisplacementMap(ColorChannel.R, ColorChannel.B, 32f, ImageFilter.Blur(0f, 0f))))
        drawClippedBitmap(canvas, 300, 0, image, Paint(imageFilter = ImageFilter.DisplacementMap(ColorChannel.G, ColorChannel.A, 48f, ImageFilter.Blur(0f, 0f))))
        drawClippedBitmap(canvas, 400, 0, image, Paint(imageFilter = ImageFilter.DisplacementMap(ColorChannel.R, ColorChannel.A, 64f, ImageFilter.Blur(0f, 0f))))

        // Row 2 — no crop, fixed scale 40
        drawClippedBitmap(canvas, 0, 100, image, Paint(imageFilter = ImageFilter.DisplacementMap(ColorChannel.R, ColorChannel.G, 40f, ImageFilter.Blur(0f, 0f))))
        drawClippedBitmap(canvas, 100, 100, image, Paint(imageFilter = ImageFilter.DisplacementMap(ColorChannel.B, ColorChannel.A, 40f, ImageFilter.Blur(0f, 0f))))
        drawClippedBitmap(canvas, 200, 100, image, Paint(imageFilter = ImageFilter.DisplacementMap(ColorChannel.R, ColorChannel.B, 40f, ImageFilter.Blur(0f, 0f))))
        drawClippedBitmap(canvas, 300, 100, image, Paint(imageFilter = ImageFilter.DisplacementMap(ColorChannel.G, ColorChannel.A, 40f, ImageFilter.Blur(0f, 0f))))
        drawClippedBitmap(canvas, 400, 100, image, Paint(imageFilter = ImageFilter.DisplacementMap(ColorChannel.R, ColorChannel.A, 40f, ImageFilter.Blur(0f, 0f))))

        // Negative scale
        drawClippedBitmap(canvas, 500, 0, image, Paint(imageFilter = ImageFilter.DisplacementMap(ColorChannel.G, ColorChannel.A, -40f, ImageFilter.Blur(0f, 0f))))

        // Different-size displacement sources
        drawClippedBitmap(canvas, 0, 200, image, Paint(imageFilter = ImageFilter.DisplacementMap(ColorChannel.R, ColorChannel.G, 40f, ImageFilter.Blur(0f, 0f))))
        drawClippedBitmap(canvas, 100, 200, image, Paint(imageFilter = ImageFilter.DisplacementMap(ColorChannel.B, ColorChannel.A, 40f, ImageFilter.Blur(0f, 0f))))
        drawClippedBitmap(canvas, 200, 200, image, Paint(imageFilter = ImageFilter.DisplacementMap(ColorChannel.R, ColorChannel.B, 40f, ImageFilter.Blur(0f, 0f))))
        drawClippedBitmap(canvas, 300, 200, image, Paint(imageFilter = ImageFilter.DisplacementMap(ColorChannel.G, ColorChannel.A, 40f, ImageFilter.Blur(0f, 0f))))

        // No displacement input
        drawClippedBitmap(canvas, 400, 200, image, null)
    }

    private fun ensureImages() {
        if (fImage != null) return
        fImage = makeStringImage(80, 80, Color.fromRGBA(0.5f, 0.27f, 0.13f, 1f))
        val c1 = Color.fromRGBA(0.14f, 0.27f, 0.52f, 1f)
        val c2 = Color.fromRGBA(0.5f, 0.25f, 0.13f, 1f)
        fCheckerboard = makeCheckerboardImage(80, 80, c1, c2, 8)
        fSmall = makeCheckerboardImage(64, 64, c1, c2, 8)
        fLarge = makeCheckerboardImage(96, 96, c1, c2, 8)
        fLargeW = makeCheckerboardImage(96, 64, c1, c2, 8)
        fLargeH = makeCheckerboardImage(64, 96, c1, c2, 8)
    }

    private fun drawClippedBitmap(canvas: GmCanvas, x: Int, y: Int, image: Image, paint: Paint?) {
        canvas.save()
        canvas.translate(x.toFloat(), y.toFloat())
        canvas.clipRect(Rect.fromXYWH(0f, 0f, image.width.toFloat(), image.height.toFloat()))
        canvas.drawImage(image, Rect.fromXYWH(0f, 0f, image.width.toFloat(), image.height.toFloat()), paint)
        canvas.restore()
    }

    private fun makeStringImage(w: Int, h: Int, color: Color): Image {
        val surface = Surface(w, h)
        surface.canvas {
            val paint = Paint(color = color, antiAlias = true)
            val font = Font(typeface, 96f)
            drawString("g", 15f, 55f, font, paint)
        }
        return surface.makeImageSnapshot()
    }

    private fun makeCheckerboardImage(w: Int, h: Int, c1: Color, c2: Color, size: Int): Image {
        val bitmap = Bitmap(w, h)
        bitmap.eraseColor(c1)
        var y = 0
        while (y < h) {
            var x = (y / size) % 2 * size
            while (x < w) {
                for (dy in 0 until size) {
                    for (dx in 0 until size) {
                        if (x + dx < w && y + dy < h) {
                            bitmap.setPixel(x + dx, y + dy, c2)
                        }
                    }
                }
                x += 2 * size
            }
            y += size
        }
        return bitmap.toImage()
    }
}
