package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SK_ColorLTGRAY
import org.skia.foundation.SK_ColorTRANSPARENT
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorSetA
import org.skia.foundation.SkFont
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.tools.SkRandom
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/dstreadshuffle.cpp::DstReadShuffle` (DEF_GM,
 * name `dstreadshuffle`, 530 × 680).
 *
 * Renders overlapping translucent shapes with kColorBurn / kSrcOver
 * against a light-gray background. Each row tiles one of six shape
 * kinds (circle, round-rect, rect, convex quad path, concave star
 * path, glyph 'N') 16 times across the width with a 15 px stride,
 * alternating SrcOver / ColorBurn every third draw to force the GPU
 * dst-read path. A small bottom-right inset draws a hairline fan
 * rasterised into a 35×35 offscreen surface and blitted back with a
 * 5× zoom.
 *
 * Same RNG seed sequence as upstream (Skia's `SkRandom` two-stream
 * MWC) — when reseeded to 0 at the start of each row, the 16-shape
 * colour palette per row is reproducible across implementations.
 */
public class DstReadShuffleGM : GM() {

    init { setBGColor(SK_ColorLTGRAY) }

    override fun getName(): String = "dstreadshuffle"
    override fun getISize(): SkISize = SkISize.Make(530, 680)

    private val kBackground = SK_ColorLTGRAY
    private var fConvexPath: SkPath? = null
    private var fConcavePath: SkPath? = null

    private enum class ShapeType { Circle, RoundRect, Rect, ConvexPath, ConcavePath, Text }

    private fun drawShape(canvas: SkCanvas, paint: SkPaint, type: ShapeType) {
        val kRect = SkRect.MakeXYWH(0f, 0f, 75f, 85f)
        when (type) {
            ShapeType.Circle ->
                canvas.drawCircle(kRect.centerX(), kRect.centerY(), kRect.width() / 2f, paint)
            ShapeType.RoundRect ->
                canvas.drawRoundRect(kRect, 15f, 15f, paint)
            ShapeType.Rect ->
                canvas.drawRect(kRect, paint)
            ShapeType.ConvexPath -> {
                if (fConvexPath == null) {
                    // Upstream uses kRect.toQuad() → 4 corners CW from TL.
                    val p0 = SkPoint(kRect.left, kRect.top)
                    val p1 = SkPoint(kRect.right, kRect.top)
                    val p2 = SkPoint(kRect.right, kRect.bottom)
                    val p3 = SkPoint(kRect.left, kRect.bottom)
                    fConvexPath = SkPathBuilder()
                        .moveTo(p0.fX, p0.fY)
                        .quadTo(p1.fX, p1.fY, p2.fX, p2.fY)
                        .quadTo(p3.fX, p3.fY, p0.fX, p0.fY)
                        .detach()
                }
                canvas.drawPath(fConvexPath!!, paint)
            }
            ShapeType.ConcavePath -> {
                if (fConcavePath == null) {
                    val pts = arrayOf(
                        SkPoint(50f, 0f),
                        SkPoint(0f, 0f),
                        SkPoint(0f, 0f),
                        SkPoint(0f, 0f),
                        SkPoint(0f, 0f),
                    )
                    val rot = SkMatrix.MakeRotate(360f / 5f, 50f, 70f)
                    for (i in 1 until 5) {
                        val dst = arrayOf(SkPoint(0f, 0f))
                        rot.mapPoints(dst, arrayOf(pts[i - 1]), 1)
                        pts[i] = dst[0]
                    }
                    val b = SkPathBuilder()
                    b.moveTo(pts[0].fX, pts[0].fY)
                    for (i in 0 until 5) {
                        val idx = (2 * i) % 5
                        b.lineTo(pts[idx].fX, pts[idx].fY)
                    }
                    fConcavePath = b.setFillType(SkPathFillType.kEvenOdd).detach()
                }
                canvas.drawPath(fConcavePath!!, paint)
            }
            ShapeType.Text -> {
                val font = SkFont(ToolUtils.DefaultPortableTypeface(), 100f).apply {
                    isEmbolden = true
                }
                canvas.drawString("N", 0f, 100f, font, paint)
            }
        }
    }

    private fun getColor(random: SkRandom): Int {
        val color = ToolUtils.colorTo565(random.nextU() or 0xFF000000.toInt())
        return SkColorSetA(color, 0x80)
    }

    /** Draws into a small (35×35) offscreen — a rotating hairline fan. */
    private fun drawHairlines(canvas: SkCanvas) {
        canvas.clear(kBackground)
        val hairPaint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 0f
            isAntiAlias = true
        }
        val pts = arrayOf(SkPoint(3f, 7f), SkPoint(29f, 7f))
        val colorRandom = SkRandom()
        val rot = SkMatrix.MakeRotate(360f / 12f, 15.5f, 12f)
            .postConcat(SkMatrix.MakeTrans(3f, 0f))
        for (i in 0 until 12) {
            hairPaint.color = getColor(colorRandom)
            canvas.drawLine(pts[0].fX, pts[0].fY, pts[1].fX, pts[1].fY, hairPaint)
            val dst = arrayOf(SkPoint(0f, 0f), SkPoint(0f, 0f))
            rot.mapPoints(dst, pts, 2)
            pts[0] = dst[0]; pts[1] = dst[1]
        }
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        var y = 5f
        val types = ShapeType.values()
        for (type in types) {
            val colorRandom = SkRandom()
            var x = 5f
            for (r in 0..15) {
                val p = SkPaint().apply {
                    isAntiAlias = true
                    color = getColor(colorRandom)
                    blendMode = if (r % 3 == 0) SkBlendMode.kColorBurn else SkBlendMode.kSrcOver
                }
                c.save()
                c.translate(x, y)
                drawShape(c, p, type)
                c.restore()
                x += 15f
            }
            y += 110f
        }
        // Bottom-right hairlines panel.
        val info = SkImageInfo.MakeN32Premul(35, 35)
        val surf = SkSurface.MakeRaster(info)
        drawHairlines(surf.canvas)
        c.scale(5f, 5f)
        c.translate(67f, 10f)
        c.drawImage(surf.makeImageSnapshot(), 0f, 0f)
    }
}
