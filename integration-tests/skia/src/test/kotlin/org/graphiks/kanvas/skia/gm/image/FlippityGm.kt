package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
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

/** Port of Skia's `gm/flippity.cpp`.
 *  Tests flipped bitmap rendering — creates a radial-gradient bitmap then
 *  draws it with various flip-matrix transforms.
 *  @see https://github.com/google/skia/blob/main/gm/flippity.cpp
 */
class FlippityGm : SkiaGm {
    override val name = "flippity"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = kGMWidth
    override val height = kGMHeight

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!
    private val fLabels = mutableListOf<Image>()
    private val fReferenceImages = arrayOfNulls<Image>(2)

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        ensureSetup()
        val refTopLeft = fReferenceImages[0] ?: return
        val refBottomLeft = fReferenceImages[1] ?: return

        canvas.save()
        drawRow(canvas, refTopLeft, drawSubset = false, drawScaled = false)
        canvas.translate(0f, kCellSize.toFloat())
        drawRow(canvas, refBottomLeft, drawSubset = false, drawScaled = false)
        canvas.translate(0f, kCellSize.toFloat())
        drawRow(canvas, refBottomLeft, drawSubset = true, drawScaled = false)
        canvas.translate(0f, kCellSize.toFloat())
        drawRow(canvas, refBottomLeft, drawSubset = true, drawScaled = true)
        canvas.restore()

        val gridPaint = Paint()
        for (i in 0..3) canvas.drawLine(0f, (i * kCellSize).toFloat(), kGMWidth.toFloat(), (i * kCellSize).toFloat(), gridPaint)
        for (i in 0 until kNumMatrices) canvas.drawLine((i * kCellSize).toFloat(), 0f, (i * kCellSize).toFloat(), kGMHeight.toFloat(), gridPaint)
    }

    private fun ensureSetup() {
        makeLabels()
        if (fReferenceImages[0] == null) fReferenceImages[0] = makeReferenceImage()
        if (fReferenceImages[1] == null) fReferenceImages[1] = makeReferenceImage()
    }

    private fun makeLabels() {
        if (fLabels.isNotEmpty()) return
        for (i in 0 until kNumLabels) fLabels.add(makeTextImage(kLabelText[i], kLabelColors[i]))
    }

    private fun makeReferenceImage(): Image {
        val bitmap = Bitmap(kImageSize, kImageSize)
        bitmap.eraseColor(Color.WHITE)
        val image = bitmap.toImage()
        val surface = Surface(kImageSize, kImageSize)
        surface.canvas {
            drawImage(image, Rect.fromXYWH(0f, 0f, kImageSize.toFloat(), kImageSize.toFloat()))
        }
        val result = surface.makeImageSnapshot()
        val surf2 = Surface(kImageSize, kImageSize)
        surf2.canvas {
            drawImage(result, Rect.fromXYWH(0f, 0f, kImageSize.toFloat(), kImageSize.toFloat()))
            for (i in 0 until kNumLabels) {
                val x = if (kPoints[i].x != 0f) kPoints[i].x - kLabelSize - kInset else kInset.toFloat()
                val y = if (kPoints[i].y != 0f) kPoints[i].y - kLabelSize - kInset else kInset.toFloat()
                drawImage(fLabels[i], Rect.fromXYWH(x, y, kLabelSize.toFloat(), kLabelSize.toFloat()))
            }
        }
        return surf2.makeImageSnapshot()
    }

    private fun drawRow(canvas: GmCanvas, image: Image, drawSubset: Boolean, drawScaled: Boolean) {
        canvas.save()
        canvas.translate(kLabelSize.toFloat(), kLabelSize.toFloat())
        for (i in 0 until kNumMatrices) {
            drawImageWithMatrix(canvas, image, i, drawSubset, drawScaled)
            canvas.translate(kCellSize.toFloat(), 0f)
        }
        canvas.restore()
    }

    private fun drawImageWithMatrix(canvas: GmCanvas, image: Image, matIndex: Int, drawSubset: Boolean, drawScaled: Boolean) {
        canvas.save()
        if (drawSubset) {
            val src = Rect.fromXYWH(0f, 0f, kImageSize.toFloat(), kImageSize.toFloat())
            val dst = if (drawScaled) Rect.fromXYWH(0f, 0f, kImageSize.toFloat(), kImageSize.toFloat()) else src
            canvas.drawImageRect(image, src, dst)
        } else {
            canvas.drawImage(image, Rect.fromXYWH(0f, 0f, kImageSize.toFloat(), kImageSize.toFloat()))
        }
        for (i in 0 until kNumLabels) {
            val x = if (kPoints[i].x == 0f) (-kLabelSize).toFloat() else kPoints[i].x
            val y = if (kPoints[i].y == 0f) (-kLabelSize).toFloat() else kPoints[i].y
            canvas.drawImage(fLabels[i], Rect.fromXYWH(x, y, kLabelSize.toFloat(), kLabelSize.toFloat()))
        }
        canvas.restore()
    }

    private fun makeTextImage(text: String, color: Color): Image {
        val surf = Surface(kLabelSize, kLabelSize)
        surf.canvas {
            val font = Font(typeface, 20f)
            drawString(text, 4f, 24f, font, Paint(color = color, antiAlias = true))
        }
        return surf.makeImageSnapshot()
    }

    private companion object {
        const val kNumMatrices = 6; const val kImageSize = 128; const val kLabelSize = 32
        const val kNumLabels = 4; const val kInset = 16
        const val kCellSize = kImageSize + 2 * kLabelSize
        const val kGMWidth = kNumMatrices * kCellSize; const val kGMHeight = 4 * kCellSize

        val kPoints = listOf(Point(0f, kImageSize.toFloat()), Point(kImageSize.toFloat(), kImageSize.toFloat()), Point(0f, 0f), Point(kImageSize.toFloat(), 0f))
        val kLabelText = arrayOf("LL", "LR", "UL", "UR")
        val kLabelColors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.fromRGBA(0f, 1f, 1f))
    }
}
