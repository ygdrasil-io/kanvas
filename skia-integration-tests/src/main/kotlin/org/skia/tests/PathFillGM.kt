package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkRRect
import org.skia.utils.SkPathUtils
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.graphiks.math.SkScalar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of Skia's `gm/pathfill.cpp::PathFillGM` (640 × 480).
 *
 * Renders a vertical stack of 10 distinct filled paths (frame / triangle
 * / rect / oval / sawtooth-32 / star-5 / star-13 / line / house /
 * sawtooth-3), each followed by a vertical translate. Then draws three
 * additional pictogram-style paths (info, accessibility, visualiser)
 * with successive scale/translate tweaks to exercise the rasterizer at
 * a variety of zoom levels.
 */
public class PathFillGM : GM() {

    private data class PathDY(val path: SkPath, val dy: SkScalar)

    private lateinit var fPaths: Array<PathDY>
    private lateinit var fInfoPath: SkPath
    private lateinit var fAccessibilityPath: SkPath
    private lateinit var fVisualizerPath: SkPath

    override fun getName(): String = "pathfill"
    override fun getISize(): SkISize = SkISize.Make(640, 480)

    override fun onOnceBeforeDraw() {
        fPaths = arrayOf(
            makeFrame(),
            makeTriangle(),
            makeRect(),
            makeOval(),
            makeSawtooth(32),
            makeStar(5),
            makeStar(13),
            makeLine(),
            makeHouse(),
            makeSawtooth(3),
        )
        fInfoPath = makeInfo()
        fAccessibilityPath = makeAccessibility()
        fVisualizerPath = makeVisualizer()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply { isAntiAlias = true }

        for (i in fPaths.indices) {
            c.drawPath(fPaths[i].path, paint)
            c.translate(0f, fPaths[i].dy)
        }

        c.save()
        c.scale(0.300000011920929f, 0.300000011920929f)
        c.translate(50f, 50f)
        c.drawPath(fInfoPath, paint)
        c.restore()

        c.scale(2f, 2f)
        c.translate(5f, 15f)
        c.drawPath(fAccessibilityPath, paint)

        c.scale(0.5f, 0.5f)
        c.translate(5f, 50f)
        c.drawPath(fVisualizerPath, paint)
    }

    private fun makeFrame(): PathDY {
        val r = SkRect.MakeLTRB(10f, 10f, 630f, 470f)
        val rrect = SkRRect.MakeRectXY(r, 15f, 15f)
        val src = SkPath.RRect(rrect)
        val stroke = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 5f
        }
        val dst = SkPathBuilder()
        SkPathUtils.FillPathWithPaint(src, stroke, dst)
        return PathDY(dst.detach(), 15f)
    }

    private fun makeTriangle(): PathDY {
        val pb = SkPathBuilder()
        pb.moveTo(10f, 20f).lineTo(15f, 5f).lineTo(30f, 30f).close().offset(10f, 0f)
        return PathDY(pb.detach(), 30f)
    }

    private fun makeRect(): PathDY {
        val r = SkRect.MakeLTRB(10f, 10f, 30f, 30f)
        val pb = SkPathBuilder().addRect(r).offset(10f, 0f)
        return PathDY(pb.detach(), 30f)
    }

    private fun makeOval(): PathDY {
        val r = SkRect.MakeLTRB(10f, 10f, 30f, 30f)
        val pb = SkPathBuilder().addOval(r).offset(10f, 0f)
        return PathDY(pb.detach(), 30f)
    }

    private fun makeSawtooth(teeth: Int): PathDY {
        var x = 20f
        val y = 20f
        val x0 = x
        val dx = 5f
        val dy = 10f
        val pb = SkPathBuilder().moveTo(x, y)
        for (i in 0 until teeth) {
            x += dx
            pb.lineTo(x, y - dy)
            x += dx
            pb.lineTo(x, y + dy)
        }
        pb.lineTo(x, y + 2f * dy)
        pb.lineTo(x0, y + 2f * dy)
        pb.close()
        return PathDY(pb.detach(), 30f)
    }

    private fun makeStar(n: Int): PathDY {
        val c = 45f
        val r = 20f
        var rad = -PI.toFloat() / 2f
        val drad = (n shr 1).toFloat() * PI.toFloat() * 2f / n
        val pb = SkPathBuilder().moveTo(c, c - r)
        for (i in 1 until n) {
            rad += drad
            pb.lineTo(c + cos(rad) * r, c + sin(rad) * r)
        }
        pb.close()
        return PathDY(pb.detach(), r * 2f * 6f / 5f)
    }

    private fun makeLine(): PathDY {
        val pb = SkPathBuilder()
            .moveTo(30f, 30f).lineTo(120f, 40f).close()
            .moveTo(150f, 30f).lineTo(150f, 30f).lineTo(300f, 40f).close()
        return PathDY(pb.detach(), 40f)
    }

    private fun makeHouse(): PathDY {
        // 20-point polygon outer ring, 10-point polyline cavity (auto-line-to).
        val pb = SkPathBuilder().addPolygon(
            arrayOf(
                21f to 23f,        21f to 11.534f,
                22.327f to 12.741f, 23.673f to 11.261f,
                12f to 0.648f,     8f to 4.285f,
                8f to 2f,          4f to 2f,
                4f to 7.921f,      0.327f to 11.26f,
                1.673f to 12.74f,  3f to 11.534f,
                3f to 23f,         11f to 23f,
                11f to 18f,        13f to 18f,
                13f to 23f,        21f to 23f,
            ),
            isClosed = true,
        ).polylineTo(
            arrayOf(
                9f to 16f, 9f to 21f, 5f to 21f, 5f to 9.715f,
                12f to 3.351f, 19f to 9.715f, 19f to 21f, 15f to 21f,
                15f to 16f, 9f to 16f,
            ),
        ).close().offset(20f, 0f)
        return PathDY(pb.detach(), 30f)
    }

    private fun makeInfo(): SkPath {
        val pb = SkPathBuilder()
        pb.moveTo(24f, 4f)
        pb.cubicTo(12.9499998f, 4f, 4f, 12.9499998f, 4f, 24f)
        pb.cubicTo(4f, 35.0499992f, 12.9499998f, 44f, 24f, 44f)
        pb.cubicTo(35.0499992f, 44f, 44f, 35.0499992f, 44f, 24f)
        pb.cubicTo(44f, 12.9500008f, 35.0499992f, 4f, 24f, 4f)
        pb.close()
        pb.moveTo(26f, 34f); pb.lineTo(22f, 34f); pb.lineTo(22f, 22f); pb.lineTo(26f, 22f); pb.lineTo(26f, 34f); pb.close()
        pb.moveTo(26f, 18f); pb.lineTo(22f, 18f); pb.lineTo(22f, 14f); pb.lineTo(26f, 14f); pb.lineTo(26f, 18f); pb.close()
        return pb.detach()
    }

    private fun makeAccessibility(): SkPath {
        val pb = SkPathBuilder()
        pb.moveTo(12f, 2f)
        pb.cubicTo(13.10000038f, 2f, 14f, 2.900000095f, 14f, 4f)
        pb.cubicTo(14f, 5.099999904f, 13.10000038f, 6f, 12f, 6f)
        pb.cubicTo(10.89999961f, 6f, 10f, 5.099999904f, 10f, 4f)
        pb.cubicTo(10f, 2.900000095f, 10.89999961f, 2f, 12f, 2f)
        pb.close()
        pb.moveTo(21f, 9f); pb.lineTo(15f, 9f); pb.lineTo(15f, 22f); pb.lineTo(13f, 22f)
        pb.lineTo(13f, 16f); pb.lineTo(11f, 16f); pb.lineTo(11f, 22f); pb.lineTo(9f, 22f)
        pb.lineTo(9f, 9f); pb.lineTo(3f, 9f); pb.lineTo(3f, 7f); pb.lineTo(21f, 7f); pb.lineTo(21f, 9f)
        pb.close()
        return pb.detach()
    }

    private fun makeVisualizer(): SkPath {
        val pb = SkPathBuilder()
        pb.moveTo(1.9520f, 2.0000f)
        pb.conicTo(1.5573f, 1.9992f, 1.2782f, 2.2782f, 0.9235f)
        pb.conicTo(0.9992f, 2.5573f, 1.0000f, 2.9520f, 0.9235f)
        pb.lineTo(1.0000f, 5.4300f); pb.lineTo(17.0000f, 5.4300f); pb.lineTo(17.0000f, 2.9520f)
        pb.conicTo(17.0008f, 2.5573f, 16.7218f, 2.2782f, 0.9235f)
        pb.conicTo(16.4427f, 1.9992f, 16.0480f, 2.0000f, 0.9235f)
        pb.lineTo(1.9520f, 2.0000f); pb.close()
        for (cx in floatArrayOf(2.7140f, 5.0000f, 7.2860f)) {
            pb.moveTo(cx, 3.1430f)
            pb.conicTo(cx + 0.3407f, 3.1287f, cx + 0.5152f, 3.4216f, 0.8590f)
            pb.conicTo(cx + 0.6898f, 3.7145f, cx + 0.5152f, 4.0074f, 0.8590f)
            pb.conicTo(cx + 0.3407f, 4.3003f, cx, 4.2860f, 0.8590f)
            pb.conicTo(cx - 0.5481f, 4.2631f, cx - 0.5481f, 3.7145f, 0.7217f)
            pb.conicTo(cx - 0.5481f, 3.1659f, cx, 3.1430f, 0.7217f)
            pb.lineTo(cx, 3.1430f); pb.close()
        }
        pb.moveTo(1.0000f, 6.1900f); pb.lineTo(1.0000f, 14.3810f)
        pb.conicTo(0.9992f, 14.7757f, 1.2782f, 15.0548f, 0.9235f)
        pb.conicTo(1.5573f, 15.3338f, 1.9520f, 15.3330f, 0.9235f)
        pb.lineTo(16.0480f, 15.3330f)
        pb.conicTo(16.4427f, 15.3338f, 16.7218f, 15.0548f, 0.9235f)
        pb.conicTo(17.0008f, 14.7757f, 17.0000f, 14.3810f, 0.9235f)
        pb.lineTo(17.0000f, 6.1910f); pb.lineTo(1.0000f, 6.1910f); pb.lineTo(1.0000f, 6.1900f)
        pb.close()
        return pb.detach()
    }
}
