package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkDashPathEffect
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathDirection
import org.skia.foundation.SkRRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/pathcontourstart.cpp::ContourStartGM` (GM
 * registered name `contour_start`).
 *
 * C++ original :
 * ```cpp
 * SkString getName() const override { return SkString("contour_start"); }
 * SkISize getISize() override { return SkISize::Make(1200, 600); }
 *
 * void onOnceBeforeDraw() override {
 *     const SkScalar kMaxDashLen = 100;
 *     const SkScalar kDashGrowth = 1.2f;
 *     STArray<100, SkScalar> intervals;
 *     for (SkScalar len = 1; len < kMaxDashLen; len *= kDashGrowth) {
 *         intervals.push_back(len);
 *         intervals.push_back(len);
 *     }
 *     fDashPaint.setAntiAlias(true);
 *     fDashPaint.setStyle(SkPaint::kStroke_Style);
 *     fDashPaint.setStrokeWidth(6);
 *     fDashPaint.setColor(0xff008000);
 *     fDashPaint.setPathEffect(SkDashPathEffect::Make(intervals, 0));
 *     fPointsPaint.setColor(0xff800000);
 *     fPointsPaint.setStrokeWidth(3);
 *     fRect = SkRect::MakeLTRB(10, 10, 100, 70);
 * }
 *
 * void onDraw(SkCanvas* canvas) override {
 *     drawDirs(canvas, [](const SkRect& rect, SkPathDirection dir, unsigned startIndex) {
 *         return SkPath::Rect(rect, dir, startIndex);
 *     });
 *     drawDirs(canvas, [](const SkRect& rect, SkPathDirection dir, unsigned startIndex) {
 *         return SkPath::Oval(rect, dir, startIndex);
 *     });
 *     drawDirs(canvas, [](const SkRect& rect, SkPathDirection dir, unsigned startIndex) {
 *         SkRRect rrect;
 *         const SkVector radii[4] = { {15, 15}, {15, 15}, {15, 15}, {15, 15}};
 *         rrect.setRectRadii(rect, radii);
 *         return SkPath::RRect(rrect, dir, startIndex);
 *     });
 *     drawDirs(canvas, [](const SkRect& rect, SkPathDirection dir, unsigned startIndex) {
 *         SkRRect rrect;
 *         rrect.setRect(rect);
 *         return SkPath::RRect(rrect, dir, startIndex);
 *     });
 *     drawDirs(canvas, [](const SkRect& rect, SkPathDirection dir, unsigned startIndex) {
 *         SkRRect rrect;
 *         rrect.setOval(rect);
 *         return SkPath::RRect(rrect, dir, startIndex);
 *     });
 * }
 * ```
 *
 * 1200×600 canvas. Five families of paths (rect, oval, rrect-with-radii,
 * rrect-as-rect, rrect-as-oval), each drawn 16 times (8 starting indices
 * × 2 directions [CW + CCW]) with a long geometric-progression dash
 * pattern that rotates around the contour as the contour's start point
 * shifts.
 */
public class ContourStartGM : GM() {

    private val fDashPaint = SkPaint()
    private val fPointsPaint = SkPaint()
    private var fRect: SkRect = SkRect.MakeLTRB(0f, 0f, 0f, 0f)

    override fun getName(): String = "contour_start"
    override fun getISize(): SkISize = SkISize.Make(kImageWidth, kImageHeight)

    override fun onOnceBeforeDraw() {
        val kMaxDashLen = 100f
        val kDashGrowth = 1.2f

        val intervals = ArrayList<Float>(100)
        var len = 1f
        while (len < kMaxDashLen) {
            intervals.add(len)
            intervals.add(len)
            len *= kDashGrowth
        }

        fDashPaint.isAntiAlias = true
        fDashPaint.style = SkPaint.Style.kStroke_Style
        fDashPaint.strokeWidth = 6f
        fDashPaint.color = 0xff008000.toInt()
        fDashPaint.pathEffect = SkDashPathEffect.Make(intervals.toFloatArray(), 0f)

        fPointsPaint.color = 0xff800000.toInt()
        fPointsPaint.strokeWidth = 3f

        fRect = SkRect.MakeLTRB(10f, 10f, 100f, 70f)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        drawDirs(c) { rect, dir, startIndex ->
            SkPath.Rect(rect, dir, startIndex)
        }
        drawDirs(c) { rect, dir, startIndex ->
            SkPath.Oval(rect, dir, startIndex)
        }
        drawDirs(c) { rect, dir, startIndex ->
            val rrect = SkRRect()
            val radii: Array<SkPoint> = arrayOf(
                SkPoint(15f, 15f),
                SkPoint(15f, 15f),
                SkPoint(15f, 15f),
                SkPoint(15f, 15f),
            )
            rrect.setRectRadii(rect, radii)
            SkPath.RRect(rrect, dir, startIndex)
        }
        drawDirs(c) { rect, dir, startIndex ->
            val rrect = SkRRect()
            rrect.setRect(rect)
            SkPath.RRect(rrect, dir, startIndex)
        }
        drawDirs(c) { rect, dir, startIndex ->
            val rrect = SkRRect()
            rrect.setOval(rect)
            SkPath.RRect(rrect, dir, startIndex)
        }
    }

    private fun drawDirs(
        canvas: SkCanvas,
        makePath: (SkRect, SkPathDirection, Int) -> SkPath,
    ) {
        drawOneColumn(canvas, SkPathDirection.kCW, makePath)
        canvas.translate((kImageWidth / 10).toFloat(), 0f)
        drawOneColumn(canvas, SkPathDirection.kCCW, makePath)
        canvas.translate((kImageWidth / 10).toFloat(), 0f)
    }

    private fun drawOneColumn(
        canvas: SkCanvas,
        dir: SkPathDirection,
        makePath: (SkRect, SkPathDirection, Int) -> SkPath,
    ) {
        val saveCount = canvas.save()
        try {
            for (i in 0 until 8) {
                val path = makePath(fRect, dir, i)
                canvas.drawPath(path, fDashPaint)
                canvas.drawPoints(SkCanvas.PointMode.kPoints, path.points(), fPointsPaint)
                canvas.translate(0f, (kImageHeight / 8).toFloat())
            }
        } finally {
            canvas.restoreToCount(saveCount)
        }
    }

    private companion object {
        const val kImageWidth: Int = 1200
        const val kImageHeight: Int = 600
    }
}
