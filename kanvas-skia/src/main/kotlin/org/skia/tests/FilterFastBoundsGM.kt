package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkPictureRecorder
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SK_ColorGREEN
import org.skia.foundation.SK_ColorMAGENTA
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SK_ColorYELLOW
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSurfaces
import org.skia.math.SkIPoint
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/filterfastbounds.cpp::ImageFilterFastBoundGM` (GM
 * name `filterfastbounds`).
 *
 * Builds a 9-row × 7-column grid of `(filter × geometry)` cells. Each
 * row's image filter is applied to seven different geometric primitives
 * (rect / oval / rrect / drrect / triangle path / line points /
 * draw-image-rect) plus two extra columns of `saveLayer` invocations
 * with the filter applied to `picture-image-filter` and `image-source`
 * filter sources. The cell rasterises the geometry inside the
 * `paint.computeFastBounds(r)` rect (clipped to the fast-bound), then
 * strokes the original `r` in red and the fast-bound in blue.
 *
 * The GM stresses [SkPaint.computeFastBounds] +
 * [SkImageFilter.computeFastBounds] interactions — primarily the
 * conservativeness of each filter's reported bounds.
 */
public class FilterFastBoundsGM : GM() {

    init { setBGColor(0xFFCCCCCC.toInt()) }

    override fun getName(): String = "filterfastbounds"
    override fun getISize(): SkISize =
        SkISize.Make((gDrawMthds.size + kNumXtraCols) * kTileWidth, kNumVertTiles * kTileHeight)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val blackFill = SkPaint()

        // Normal paints (no source)
        val paints = mutableListOf<SkPaint>()
        createPaints(paints, null)

        // Paints with a PictureImageFilter as a source
        val rec = SkPictureRecorder()
        val pCanvas = rec.beginRecording(SkRect.MakeWH(10f, 10f))
        pCanvas.drawRect(SkRect.MakeWH(10f, 10f), blackFill)
        val pic = rec.finishRecordingAsPicture()

        val pifPaints = mutableListOf<SkPaint>()
        createPaints(pifPaints, SkImageFilters.Picture(pic))

        // Paints with a SkImageSource as a source
        val surface = SkSurfaces.Raster(SkImageInfo.MakeN32Premul(10, 10))!!
        run {
            val temp = surface.canvas
            temp.clear(SK_ColorYELLOW)
            val p = SkPaint().apply { color = SK_ColorBLUE }
            temp.drawRect(SkRect.MakeLTRB(5f, 5f, 10f, 10f), p)
            p.color = SK_ColorGREEN
            temp.drawRect(SkRect.MakeLTRB(5f, 0f, 10f, 5f), p)
        }
        val image: SkImage = surface.makeImageSnapshot()
        val imageSource: SkImageFilter =
            SkImageFilters.Image(image, SkSamplingOptions(SkFilterMode.kLinear))
        val bmsPaints = mutableListOf<SkPaint>()
        createPaints(bmsPaints, imageSource)

        require(paints.size == kNumVertTiles)
        require(paints.size == pifPaints.size)
        require(paints.size == bmsPaints.size)

        // Horizontal separators
        for (i in 1 until paints.size) {
            c.drawLine(
                0f, (i * kTileHeight).toFloat(),
                ((gDrawMthds.size + kNumXtraCols) * kTileWidth).toFloat(),
                (i * kTileHeight).toFloat(), blackFill,
            )
        }
        // Vertical separators
        for (i in 0 until gDrawMthds.size + kNumXtraCols) {
            c.drawLine(
                (i * kTileWidth).toFloat(), 0f,
                (i * kTileWidth).toFloat(),
                (paints.size * kTileWidth).toFloat(), blackFill,
            )
        }

        // Column 0 : saveLayer with PictureImageFilter
        for (i in pifPaints.indices) {
            drawSaveLayerWithPaint(SkIPoint.Make(0, i * kTileHeight), c, pifPaints[i])
        }
        // Column 1 : saveLayer with BitmapSource
        for (i in pifPaints.indices) {
            drawSaveLayerWithPaint(SkIPoint.Make(kTileWidth, i * kTileHeight), c, bmsPaints[i])
        }

        // Geometry columns
        for (i in gDrawMthds.indices) {
            for (j in paints.indices) {
                drawGeomWithPaint(
                    gDrawMthds[i],
                    SkIPoint.Make((i + kNumXtraCols) * kTileWidth, j * kTileHeight),
                    c, paints[j],
                )
            }
        }
    }

    private fun drawGeomWithPaint(
        draw: (SkCanvas, SkRect, SkPaint) -> Unit,
        off: SkIPoint, canvas: SkCanvas, p: SkPaint,
    ) {
        val redStroked = SkPaint().apply {
            color = SK_ColorRED
            style = SkPaint.Style.kStroke_Style
        }
        val blueStroked = SkPaint().apply {
            color = SK_ColorBLUE
            style = SkPaint.Style.kStroke_Style
        }
        val r = SkRect.MakeLTRB(20f, 20f, 30f, 30f)
        canvas.save()
        canvas.translate(off.fX.toFloat(), off.fY.toFloat())
        canvas.scale(1.5f, 1.5f)
        val fastBound = p.computeFastBounds(r)
        canvas.save()
        canvas.clipRect(fastBound)
        draw(canvas, r, p)
        canvas.restore()
        canvas.drawRect(r, redStroked)
        canvas.drawRect(fastBound, blueStroked)
        canvas.restore()
    }

    private fun drawSaveLayerWithPaint(off: SkIPoint, canvas: SkCanvas, p: SkPaint) {
        val redStroked = SkPaint().apply {
            color = SK_ColorRED
            style = SkPaint.Style.kStroke_Style
        }
        val blueStroked = SkPaint().apply {
            color = SK_ColorBLUE
            style = SkPaint.Style.kStroke_Style
        }
        val bounds = SkRect.MakeWH(10f, 10f)
        canvas.save()
        canvas.translate(30f, 30f)
        canvas.translate(off.fX.toFloat(), off.fY.toFloat())
        canvas.scale(1.5f, 1.5f)
        val fastBound = p.computeFastBounds(bounds)
        canvas.saveLayer(fastBound, p)
        canvas.restore()
        canvas.drawRect(bounds, redStroked)
        canvas.drawRect(fastBound, blueStroked)
        canvas.restore()
    }

    private fun createPaints(paints: MutableList<SkPaint>, source: SkImageFilter?) {
        run {
            val scale = org.skia.math.SkMatrix.MakeScale(2f, 2f)
            val scaleMIF = SkImageFilters.MatrixTransform(
                scale, SkSamplingOptions(SkFilterMode.kLinear), source,
            )
            addPaint(paints, scaleMIF)
        }
        run {
            val rot = org.skia.math.SkMatrix.MakeRotate(-33.3f)
            val rotMIF = SkImageFilters.MatrixTransform(
                rot, SkSamplingOptions(SkFilterMode.kLinear), source,
            )
            addPaint(paints, rotMIF)
        }
        run {
            val src = SkRect.MakeXYWH(20f, 20f, 10f, 10f)
            val dst = SkRect.MakeXYWH(30f, 30f, 30f, 30f)
            val tileIF = SkImageFilters.Tile(src, dst, null)
            addPaint(paints, tileIF)
        }
        run {
            val dsif = SkImageFilters.DropShadow(10f, 10f, 3f, 3f, SK_ColorRED, source)
            addPaint(paints, dsif)
        }
        run {
            val dsif = SkImageFilters.DropShadowOnly(27f, 27f, 3f, 3f, SK_ColorRED, source)
            addPaint(paints, dsif)
        }
        addPaint(paints, SkImageFilters.Blur(3f, 3f, source))
        addPaint(paints, SkImageFilters.Offset(15f, 15f, source))
    }

    private fun addPaint(paints: MutableList<SkPaint>, filter: SkImageFilter?) {
        val p = SkPaint().apply { imageFilter = filter }
        paints.add(p)
    }

    private companion object {
        const val kTileWidth = 100
        const val kTileHeight = 100
        const val kNumVertTiles = 7
        const val kNumXtraCols = 2

        val gDrawMthds: List<(SkCanvas, SkRect, SkPaint) -> Unit> = listOf(
            ::drawRect, ::drawOval, ::drawRRect, ::drawDRRect,
            ::drawPath, ::drawPoints, ::drawBitmap,
        )

        fun drawRect(canvas: SkCanvas, r: SkRect, p: SkPaint) {
            canvas.drawRect(r, p)
        }

        fun drawOval(canvas: SkCanvas, r: SkRect, p: SkPaint) {
            canvas.drawOval(r, p)
        }

        fun drawRRect(canvas: SkCanvas, r: SkRect, p: SkPaint) {
            val rr = SkRRect.MakeRectXY(r, r.width() / 4f, r.height() / 4f)
            canvas.drawRRect(rr, p)
        }

        fun drawDRRect(canvas: SkCanvas, r: SkRect, p: SkPaint) {
            val xRad = r.width() / 4f
            val yRad = r.height() / 4f
            val outer = SkRRect.MakeRectXY(r, xRad, yRad)
            val inner = SkRRect()
            inner.setRectXY(r, xRad, yRad)
            inner.inset(xRad, yRad)
            canvas.drawDRRect(outer, inner, p)
        }

        fun drawPath(canvas: SkCanvas, r: SkRect, p: SkPaint) {
            val path = org.skia.foundation.SkPath.Polygon(
                arrayOf(
                    r.left to r.top,
                    r.left to r.bottom,
                    r.right to r.bottom,
                ),
                isClosed = true,
            )
            canvas.drawPath(path, p)
        }

        fun drawPoints(canvas: SkCanvas, r: SkRect, p: SkPaint) {
            val pts0 = arrayOf(SkPoint(r.left, r.top), SkPoint(r.right, r.bottom))
            val pts1 = arrayOf(SkPoint(r.left, r.bottom), SkPoint(r.right, r.top))
            canvas.drawPoints(SkCanvas.PointMode.kLines, pts0, p)
            canvas.drawPoints(SkCanvas.PointMode.kLines, pts1, p)
        }

        fun drawBitmap(canvas: SkCanvas, r: SkRect, p: SkPaint) {
            val surf = SkSurfaces.Raster(SkImageInfo.MakeN32Premul(64, 64))!!
            surf.canvas.clear(SK_ColorMAGENTA)
            val img = surf.makeImageSnapshot()
            canvas.drawImageRect(img, SkRect.MakeIWH(img.width, img.height), r, SkSamplingOptions.Default, p)
        }

        @Suppress("unused")
        private val whiteUnused: Int = SK_ColorWHITE
        @Suppress("unused")
        private val blackUnused: Int = SK_ColorBLACK
    }
}
