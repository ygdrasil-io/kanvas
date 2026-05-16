package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorGREEN
import org.skia.math.SK_ColorRED
import org.skia.math.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkDashPathEffect
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkMaskFilter
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.tools.ToolUtils
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of Skia's `gm/strokedlines.cpp::StrokedLinesGM` (540 × 720).
 *
 * Each row draws a "snowflake" — three diametral spokes (rotated 0°,
 * 60°, 120° from the x-axis) with two perpendicular fins at the
 * midpoint of each half-spoke — across six columns combining
 * `(butt, round, square)` × `(aa, no-aa)`. Eight rows total:
 *  * 5 paint variants (white / red→green linear gradient / dashed /
 *    2×2 checker bitmap shader / outer-blur mask filter) drawn with
 *    the identity matrix ; and
 *  * the white paint redrawn under 3 local matrices
 *    (rotate 12° / skew (0.3, 0.5) / mild perspective).
 *
 * The boolean `useDrawPath` constructor switches every line draw between
 * `canvas.drawPath(SkPath.Line(p0,p1), paint)` (the default) and a
 * direct `canvas.drawLine`. The two variants serialise to two upstream
 * PNGs : `strokedlines.png` (drawPath) and `strokedlines_drawPoints.png`
 * (drawLine).
 *
 * C++ source : see `gm/strokedlines.cpp`. References:
 * `strokedlines.png`, `strokedlines_drawPoints.png`.
 */
public class StrokedLinesGM(private val useDrawPath: Boolean) : GM() {

    init {
        setBGColor(ToolUtils.colorTo565(0xFF1A65D7.toInt()))
    }

    private lateinit var fPaints: List<SkPaint>
    private lateinit var fMatrices: List<SkMatrix>

    override fun getName(): String =
        if (useDrawPath) "strokedlines" else "strokedlines_drawPoints"

    override fun getISize(): SkISize = SkISize.Make(
        kNumColumns * (2 * kRadius + 2 * kPad),
        kNumRows * (2 * kRadius + 2 * kPad),
    )

    override fun onOnceBeforeDraw() {
        val paints = ArrayList<SkPaint>(5)

        // basic white
        paints.add(SkPaint().apply { color = SK_ColorWHITE })

        // red → green linear gradient
        paints.add(
            SkPaint().apply {
                shader = SkLinearGradient.Make(
                    SkPoint.Make(-(kRadius + kPad).toFloat(), -(kRadius + kPad).toFloat()),
                    SkPoint.Make((kRadius + kPad).toFloat(), (kRadius + kPad).toFloat()),
                    intArrayOf(SK_ColorRED, SK_ColorGREEN),
                    null,
                    SkTileMode.kClamp,
                )
            },
        )

        // dashing (on = off = stroke width)
        paints.add(
            SkPaint().apply {
                color = SK_ColorWHITE
                pathEffect = SkDashPathEffect.Make(
                    floatArrayOf(kStrokeWidth, kStrokeWidth),
                    kStrokeWidth,
                )
            },
        )

        // 2 × 2 checker bitmap shader, rotated 12° then scaled 3×
        val bm = SkBitmap(2, 2)
        bm.setPixel(0, 0, 0xFFFFFFFF.toInt())
        bm.setPixel(1, 1, 0xFFFFFFFF.toInt())
        bm.setPixel(1, 0, 0)
        bm.setPixel(0, 1, 0)
        val m = SkMatrix.MakeRotate(12f).preScale(3f, 3f)
        paints.add(
            SkPaint().apply {
                shader = bm.makeShader(SkTileMode.kRepeat, SkTileMode.kRepeat, localMatrix = m)
            },
        )

        // outer-blur mask filter
        paints.add(
            SkPaint().apply {
                color = SK_ColorWHITE
                maskFilter = SkMaskFilter.MakeBlur(SkBlurStyle.kOuter, 3f)
            },
        )

        fPaints = paints

        val matrices = ArrayList<SkMatrix>(3)
        matrices.add(SkMatrix.MakeRotate(12f))
        matrices.add(SkMatrix.MakeSkew(0.3f, 0.5f))
        matrices.add(SkMatrix.MakePerspective(-1f / 300f, 1f / 300f))
        fMatrices = matrices
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.translate(0f, (kRadius + kPad).toFloat())

        for (paint in fPaints) {
            val sc = c.save()
            drawRow(c, paint, SkMatrix.Identity)
            c.restoreToCount(sc)
            c.translate(0f, 2f * (kRadius + kPad))
        }

        for (mtx in fMatrices) {
            val sc = c.save()
            drawRow(c, fPaints[0], mtx)
            c.restoreToCount(sc)
            c.translate(0f, 2f * (kRadius + kPad))
        }
    }

    private fun drawRow(canvas: SkCanvas, src: SkPaint, localMatrix: SkMatrix) {
        canvas.translate((kRadius + kPad).toFloat(), 0f)

        val caps = arrayOf(SkPaint.Cap.kButt_Cap, SkPaint.Cap.kRound_Cap, SkPaint.Cap.kSquare_Cap)
        for (cap in caps) {
            for (isAA in booleanArrayOf(true, false)) {
                val tmp = SkPaint().apply {
                    color = src.color
                    shader = src.shader
                    pathEffect = src.pathEffect
                    maskFilter = src.maskFilter
                    isAntiAlias = isAA
                    style = SkPaint.Style.kStroke_Style
                    strokeWidth = kStrokeWidth
                    strokeCap = cap
                }

                val saveCount = canvas.save()
                canvas.concat(localMatrix)
                drawSnowflake(canvas, tmp)
                canvas.restoreToCount(saveCount)

                canvas.translate(2f * (kRadius + kPad), 0f)
            }
        }
    }

    private fun drawSnowflake(canvas: SkCanvas, paint: SkPaint) {
        canvas.clipRect(
            SkRect.MakeLTRB(
                -(kRadius + kPad).toFloat(),
                -(kRadius + kPad).toFloat(),
                (kRadius + kPad).toFloat(),
                (kRadius + kPad).toFloat(),
            ),
        )

        val piF = PI.toFloat()
        var angle = 0f
        val step = piF / (kNumSpokes / 2f)
        for (i in 0 until kNumSpokes / 2) {
            val s = sin(angle) * kRadius
            val cs = cos(angle) * kRadius

            // main spoke
            drawLine(canvas, -cs, -s, cs, s, paint)

            // positive-side fins
            drawFins(canvas, 0.5f * cs, 0.5f * s, angle, paint)
            // negative-side fins (angle + π)
            drawFins(canvas, -0.5f * cs, -0.5f * s, angle + piF, paint)

            angle += step
        }
    }

    private fun drawFins(
        canvas: SkCanvas,
        ox: Float,
        oy: Float,
        angle: Float,
        paint: SkPaint,
    ) {
        val piF = PI.toFloat()
        val half = kRadius / 2f

        var s = sin(angle + piF / 4f) * half
        var cs = cos(angle + piF / 4f) * half
        drawLine(canvas, ox, oy, ox + cs, oy + s, paint)

        s = sin(angle - piF / 4f) * half
        cs = cos(angle - piF / 4f) * half
        drawLine(canvas, ox, oy, ox + cs, oy + s, paint)
    }

    private fun drawLine(
        canvas: SkCanvas,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        paint: SkPaint,
    ) {
        if (useDrawPath) {
            canvas.drawPath(SkPath.Line(x0 to y0, x1 to y1), paint)
        } else {
            canvas.drawLine(x0, y0, x1, y1, paint)
        }
    }

    private companion object {
        const val kNumColumns: Int = 6
        const val kNumRows: Int = 8
        const val kRadius: Int = 40
        const val kPad: Int = 5
        const val kNumSpokes: Int = 6
        const val kStrokeWidth: Float = 5.0f
    }
}
