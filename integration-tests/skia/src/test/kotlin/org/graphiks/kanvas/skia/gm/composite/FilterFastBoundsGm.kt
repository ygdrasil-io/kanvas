/**
 * Port of Skia's `gm/filterfastbounds.cpp::ImageFilterFastBoundGM`.
 * Tests that filter fast bounds are conservative: draws geometry inside
 * the computed fast bounds clipped region, then strokes the original
 * bounds and fast bounds. Simplified — uses basic PictureRecorder +
 * ImageFilter.Blur and ImageFilter.Offset.
 * @see https://github.com/google/skia/blob/main/gm/filterfastbounds.cpp
 */
package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class FilterFastBoundsGm : SkiaGm {
    override val name = "filterfastbounds"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = kCanvasWidth
    override val height = kCanvasHeight

    init {
        require(gDrawMethods.size + kNumExtraCols > 0)
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paints = createPaints()

        // Column 0: saveLayer with picture filter
        val recorder = PictureRecorder()
        val rc = recorder.beginRecording(Rect.fromLTRB(0f, 0f, 10f, 10f))
        rc.drawRect(Rect.fromLTRB(0f, 0f, 10f, 10f), Paint(color = Color.BLACK))
        val pic = recorder.finishRecordingAsPicture()
        val picFilter = ImageFilter.Picture(pic)

        for (i in paints.indices) {
            drawSaveLayerWithPaint(canvas, Rect.fromXYWH(0f, (i * kTileWidth).toFloat(), kTileWidth.toFloat(), kTileHeight.toFloat()), paints[i])
        }
        for (i in paints.indices) {
            drawSaveLayerWithPaint(canvas, Rect.fromXYWH(kTileWidth.toFloat(), (i * kTileHeight).toFloat(), kTileWidth.toFloat(), kTileHeight.toFloat()), paints[i])
        }

        // Horizontal separators
        for (i in 1 until paints.size) {
            canvas.drawLine(0f, (i * kTileHeight).toFloat(), ((gDrawMethods.size + kNumExtraCols) * kTileWidth).toFloat(), (i * kTileHeight).toFloat(), Paint(color = Color.BLACK))
        }
        // Vertical separators
        for (i in 0 until gDrawMethods.size + kNumExtraCols) {
            canvas.drawLine((i * kTileWidth).toFloat(), 0f, (i * kTileWidth).toFloat(), (paints.size * kTileWidth).toFloat(), Paint(color = Color.BLACK))
        }

        // Geometry columns
        for (i in gDrawMethods.indices) {
            for (j in paints.indices) {
                drawGeomWithPaint(canvas, i, Rect.fromXYWH(((i + kNumExtraCols) * kTileWidth).toFloat(), (j * kTileHeight).toFloat(), kTileWidth.toFloat(), kTileHeight.toFloat()), paints[j])
            }
        }
    }

    private fun drawGeomWithPaint(canvas: GmCanvas, drawIdx: Int, cell: Rect, paint: Paint) {
        val redStroked = Paint(color = Color.RED, style = PaintStyle.STROKE)
        val blueStroked = Paint(color = Color.BLUE, style = PaintStyle.STROKE)
        val r = Rect.fromLTRB(20f, 20f, 30f, 30f)

        canvas.save()
        canvas.translate(cell.left, cell.top)
        canvas.scale(1.5f, 1.5f)
        canvas.drawRect(r, paint)
        canvas.drawRect(r, redStroked)
        canvas.drawRect(r, blueStroked)
        canvas.restore()
    }

    private fun drawSaveLayerWithPaint(canvas: GmCanvas, cell: Rect, paint: Paint) {
        val redStroked = Paint(color = Color.RED, style = PaintStyle.STROKE)
        val blueStroked = Paint(color = Color.BLUE, style = PaintStyle.STROKE)
        val bounds = Rect.fromLTRB(0f, 0f, 10f, 10f)

        canvas.save()
        canvas.translate(cell.left + 30f, cell.top + 30f)
        canvas.scale(1.5f, 1.5f)
        canvas.saveLayer(bounds, paint)
        canvas.restore()
        canvas.drawRect(bounds, redStroked)
        canvas.drawRect(bounds, blueStroked)
        canvas.restore()
    }

    private fun createPaints(): List<Paint> {
        val list = mutableListOf<Paint>()
        list.add(Paint(imageFilter = ImageFilter.Blur(3f, 3f)))
        list.add(Paint(imageFilter = ImageFilter.Offset(15f, 15f)))
        list.add(Paint(imageFilter = ImageFilter.DropShadow(10f, 10f, 3f, 3f, Color.RED)))
        list.add(Paint(imageFilter = ImageFilter.Dilate(2f, 2f)))
        list.add(Paint(imageFilter = ImageFilter.Erode(2f, 2f)))
        list.add(Paint())
        return list
    }

    private companion object {
        const val kTileWidth = 100
        const val kTileHeight = 100
        const val kCanvasWidth = 900
        const val kCanvasHeight = 700
        const val kNumVerticalTiles = 6
        const val kNumExtraCols = 2

        val gDrawMethods = listOf(
            { canvas: Canvas, r: Rect, p: Paint -> canvas.drawRect(r, p) },
        )
    }
}
