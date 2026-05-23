package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorDKGRAY
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkSurfaces
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/closedcappedhairlines.cpp::hairlines_buttcap` (250 × 250).
 *
 * Draws a 4-row grid of hairline stroke paths (line / quad / cubic contours,
 * on- and off-pixel-line, open and closed) with [SkPaint.Cap.kButt_Cap] to
 * validate that closed contours do **not** extend capped ends beyond the path.
 *
 * Layout:
 *  - Row 1 (on-pixel, open): line, quad, cubic open contours — no caps.
 *  - Row 2 (off-pixel, open): same 3 shapes shifted 0.5 px.
 *  - Row 3 (on-pixel, closed): same 3 shapes with `close()` applied.
 *  - Row 4 (off-pixel, closed): same 3 shapes closed + 0.5 px shift.
 *
 * The result is drawn at 1× then again at 4× scale (with a pixel grid and
 * red highlight boxes around the open-contour endpoints where caps apply).
 */
public class HairlinesButtcapGM : GM() {

    override fun getName(): String = "hairlines_buttcap"
    override fun getISize(): SkISize = SkISize.Make(WIDTH, HEIGHT)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        drawHairlineContoursWithCaps(c, SkPaint.Cap.kButt_Cap)
    }

    private companion object {
        const val WIDTH = 250
        const val HEIGHT = 250
        const val SCALE = 4
        const val GRID_WH = 70
    }
}

// ─── shared helpers ─────────────────────────────────────────────────────────

internal fun drawHairlineContoursWithCaps(canvas: SkCanvas, cap: SkPaint.Cap) {
    val paint = SkPaint().apply {
        style = SkPaint.Style.kStroke_Style
        strokeWidth = 0f
        color = SK_ColorBLACK
        isAntiAlias = true
        strokeCap = cap
    }

    val pathSurface = SkSurfaces.Raster(
        SkImageInfo.MakeN32Premul(HAIRLINE_GRID_WH, HAIRLINE_GRID_WH)
    ) ?: return
    val pathCanvas = pathSurface.canvas

    // first row — on-pixel, open
    val lineOnOpen = SkPathBuilder()
        .lineTo(0f, 5f).lineTo(5f, 5f).lineTo(5f, 0f)
        .detach().makeOffset(5f, 5f)
    pathCanvas.drawPath(lineOnOpen, paint)

    val quadOnOpen = SkPathBuilder()
        .quadTo(15f, 5f, 0f, 10f)
        .detach().makeOffset(20f, 5f)
    pathCanvas.drawPath(quadOnOpen, paint)

    val cubicOnOpen = SkPathBuilder()
        .cubicTo(-5f, 0f, -5f, 5f, 0f, 10f)
        .detach().makeOffset(40f, 5f)
    pathCanvas.drawPath(cubicOnOpen, paint)

    // second row — off-pixel, open
    val lineOffOpen = lineOnOpen.makeOffset(0.5f, 0.5f).makeOffset(0f, 15f)
    pathCanvas.drawPath(lineOffOpen, paint)

    val quadOffOpen = quadOnOpen.makeOffset(0.5f, 0.5f).makeOffset(0f, 15f)
    pathCanvas.drawPath(quadOffOpen, paint)

    val cubicOffOpen = cubicOnOpen.makeOffset(0.5f, 0.5f).makeOffset(0f, 15f)
    pathCanvas.drawPath(cubicOffOpen, paint)

    // third row — on-pixel, closed
    val lineOnClosed = SkPathBuilder(lineOnOpen).close().detach().makeOffset(0f, 30f)
    pathCanvas.drawPath(lineOnClosed, paint)

    val quadOnClosed = SkPathBuilder(quadOnOpen).close().detach().makeOffset(0f, 30f)
    pathCanvas.drawPath(quadOnClosed, paint)

    val cubicOnClosed = SkPathBuilder(cubicOnOpen).close().detach().makeOffset(0f, 30f)
    pathCanvas.drawPath(cubicOnClosed, paint)

    // fourth row — off-pixel, closed
    val lineOffClosed = SkPathBuilder(lineOnOpen).close().detach()
        .makeOffset(0.5f, 0.5f).makeOffset(0f, 45f)
    pathCanvas.drawPath(lineOffClosed, paint)

    val quadOffClosed = SkPathBuilder(quadOnOpen).close().detach()
        .makeOffset(0.5f, 0.5f).makeOffset(0f, 45f)
    pathCanvas.drawPath(quadOffClosed, paint)

    val cubicOffClosed = SkPathBuilder(cubicOnOpen).close().detach()
        .makeOffset(0.5f, 0.5f).makeOffset(0f, 45f)
    pathCanvas.drawPath(cubicOffClosed, paint)

    val pathImg = pathSurface.makeImageSnapshot()
    // 1× render in top-left corner
    canvas.drawImage(pathImg, 0f, 0f)

    // 4× render with grid and highlights
    canvas.scale(HAIRLINE_SCALE.toFloat(), HAIRLINE_SCALE.toFloat())
    canvas.drawImage(pathImg, 15f, 0f)
    canvas.translate(15f, 0f)

    drawHairlineGrid(canvas)
    drawHairlineHighlights(
        canvas,
        listOf(lineOnOpen, quadOnOpen, cubicOnOpen, lineOffOpen, quadOffOpen, cubicOffOpen),
    )
}

private const val HAIRLINE_SCALE = 4
internal const val HAIRLINE_GRID_WH = 70

private fun highlightBox(p: SkPoint): SkRect {
    val offset = 2f
    return SkRect.MakeXYWH(p.fX - offset, p.fY - offset, offset * 2f, offset * 2f)
}

private fun drawHairlineGrid(canvas: SkCanvas) {
    val gridPaint = SkPaint().apply {
        color = SK_ColorDKGRAY
        style = SkPaint.Style.kStroke_Style
        strokeWidth = 0f
    }
    val wh = HAIRLINE_GRID_WH
    for (y in 0..wh) {
        canvas.drawLine(0f, y.toFloat(), wh.toFloat(), y.toFloat(), gridPaint)
    }
    for (x in 0..wh) {
        canvas.drawLine(x.toFloat(), 0f, x.toFloat(), wh.toFloat(), gridPaint)
    }
}

private fun drawHairlineHighlights(canvas: SkCanvas, paths: List<org.skia.foundation.SkPath>) {
    val highlightPaint = SkPaint().apply {
        style = SkPaint.Style.kStroke_Style
        strokeWidth = 0f
        color = SK_ColorRED
        isAntiAlias = true
    }
    for (path in paths) {
        val pts = path.points()
        if (pts.isEmpty()) continue
        canvas.drawRect(highlightBox(pts.first()), highlightPaint)
        canvas.drawRect(highlightBox(pts.last()), highlightPaint)
    }
}
