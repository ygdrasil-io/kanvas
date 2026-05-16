package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLACK
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.tools.SkRandom
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/degeneratesegments.cpp::DegenerateSegmentsGM`
 * (896 × 930).
 *
 * Random-but-deterministic paths assembled from a palette of 21
 * "segment functions" (mix of degenerate moves, lines, quads,
 * cubics, and the corresponding `MoveX`/`MoveXClose` variants).
 * Each cell picks a random style / cap / fill / 5 segments and
 * draws the resulting path clipped to a 220 × 50 rect. Labels
 * underneath identify the recipe used.
 *
 * Uses [SkRandom] (Skia's deterministic PRNG) so the cell layout
 * exactly mirrors upstream's frame.
 */
public class DegenerateSegmentsGM : GM() {

    override fun getName(): String = "degeneratesegments"
    override fun getISize(): SkISize = SkISize.Make(896, 930)

    private fun interface AddSegment {
        fun add(path: SkPathBuilder, start: SkPoint): SkPoint
    }

    private fun pt(x: Float, y: Float): SkPoint = SkPoint.Make(x, y)

    private fun addMove(p: SkPathBuilder, startPt: SkPoint): SkPoint {
        val moveTo = pt(startPt.fX, startPt.fY + 10f)
        p.moveTo(moveTo.fX, moveTo.fY)
        return moveTo
    }
    private fun addMoveClose(p: SkPathBuilder, startPt: SkPoint): SkPoint {
        val moveTo = pt(startPt.fX, startPt.fY + 10f)
        p.moveTo(moveTo.fX, moveTo.fY); p.close()
        return moveTo
    }
    private fun addDegenLine(p: SkPathBuilder, startPt: SkPoint): SkPoint {
        p.lineTo(startPt.fX, startPt.fY); return startPt
    }
    private fun addMoveDegenLine(p: SkPathBuilder, startPt: SkPoint): SkPoint {
        val moveTo = pt(startPt.fX, startPt.fY + 10f)
        p.moveTo(moveTo.fX, moveTo.fY); p.lineTo(moveTo.fX, moveTo.fY)
        return moveTo
    }
    private fun addMoveDegenLineClose(p: SkPathBuilder, startPt: SkPoint): SkPoint {
        val moveTo = pt(startPt.fX, startPt.fY + 10f)
        p.moveTo(moveTo.fX, moveTo.fY); p.lineTo(moveTo.fX, moveTo.fY); p.close()
        return moveTo
    }
    private fun addDegenQuad(p: SkPathBuilder, startPt: SkPoint): SkPoint {
        p.quadTo(startPt.fX, startPt.fY, startPt.fX, startPt.fY); return startPt
    }
    private fun addMoveDegenQuad(p: SkPathBuilder, startPt: SkPoint): SkPoint {
        val moveTo = pt(startPt.fX, startPt.fY + 10f)
        p.moveTo(moveTo.fX, moveTo.fY); p.quadTo(moveTo.fX, moveTo.fY, moveTo.fX, moveTo.fY)
        return moveTo
    }
    private fun addMoveDegenQuadClose(p: SkPathBuilder, startPt: SkPoint): SkPoint {
        val moveTo = pt(startPt.fX, startPt.fY + 10f)
        p.moveTo(moveTo.fX, moveTo.fY); p.quadTo(moveTo.fX, moveTo.fY, moveTo.fX, moveTo.fY); p.close()
        return moveTo
    }
    private fun addDegenCubic(p: SkPathBuilder, startPt: SkPoint): SkPoint {
        p.cubicTo(startPt.fX, startPt.fY, startPt.fX, startPt.fY, startPt.fX, startPt.fY)
        return startPt
    }
    private fun addMoveDegenCubic(p: SkPathBuilder, startPt: SkPoint): SkPoint {
        val moveTo = pt(startPt.fX, startPt.fY + 10f)
        p.moveTo(moveTo.fX, moveTo.fY)
        p.cubicTo(moveTo.fX, moveTo.fY, moveTo.fX, moveTo.fY, moveTo.fX, moveTo.fY)
        return moveTo
    }
    private fun addMoveDegenCubicClose(p: SkPathBuilder, startPt: SkPoint): SkPoint {
        val moveTo = pt(startPt.fX, startPt.fY + 10f)
        p.moveTo(moveTo.fX, moveTo.fY)
        p.cubicTo(moveTo.fX, moveTo.fY, moveTo.fX, moveTo.fY, moveTo.fX, moveTo.fY); p.close()
        return moveTo
    }
    private fun addClose(p: SkPathBuilder, startPt: SkPoint): SkPoint {
        p.close(); return startPt
    }
    private fun addLine(p: SkPathBuilder, startPt: SkPoint): SkPoint {
        val endPt = pt(startPt.fX + 40f, startPt.fY)
        p.lineTo(endPt.fX, endPt.fY); return endPt
    }
    private fun addMoveLine(p: SkPathBuilder, startPt: SkPoint): SkPoint {
        val moveTo = pt(startPt.fX, startPt.fY + 10f)
        val endPt = pt(moveTo.fX + 40f, moveTo.fY)
        p.moveTo(moveTo.fX, moveTo.fY); p.lineTo(endPt.fX, endPt.fY)
        return endPt
    }
    private fun addMoveLineClose(p: SkPathBuilder, startPt: SkPoint): SkPoint {
        val moveTo = pt(startPt.fX, startPt.fY + 10f)
        val endPt = pt(moveTo.fX + 40f, moveTo.fY)
        p.moveTo(moveTo.fX, moveTo.fY); p.lineTo(endPt.fX, endPt.fY); p.close()
        return endPt
    }
    private fun addQuad(p: SkPathBuilder, startPt: SkPoint): SkPoint {
        val midPt = pt(startPt.fX + 20f, startPt.fY + 5f)
        val endPt = pt(startPt.fX + 40f, startPt.fY)
        p.quadTo(midPt.fX, midPt.fY, endPt.fX, endPt.fY); return endPt
    }
    private fun addMoveQuad(p: SkPathBuilder, startPt: SkPoint): SkPoint {
        val moveTo = pt(startPt.fX, startPt.fY + 10f)
        val midPt = pt(moveTo.fX + 20f, moveTo.fY + 5f)
        val endPt = pt(moveTo.fX + 40f, moveTo.fY)
        p.moveTo(moveTo.fX, moveTo.fY); p.quadTo(midPt.fX, midPt.fY, endPt.fX, endPt.fY)
        return endPt
    }
    private fun addMoveQuadClose(p: SkPathBuilder, startPt: SkPoint): SkPoint {
        val moveTo = pt(startPt.fX, startPt.fY + 10f)
        val midPt = pt(moveTo.fX + 20f, moveTo.fY + 5f)
        val endPt = pt(moveTo.fX + 40f, moveTo.fY)
        p.moveTo(moveTo.fX, moveTo.fY); p.quadTo(midPt.fX, midPt.fY, endPt.fX, endPt.fY); p.close()
        return endPt
    }
    private fun addCubic(p: SkPathBuilder, startPt: SkPoint): SkPoint {
        val t1 = pt(startPt.fX + 15f, startPt.fY + 5f)
        val t2 = pt(startPt.fX + 25f, startPt.fY + 5f)
        val endPt = pt(startPt.fX + 40f, startPt.fY)
        p.cubicTo(t1.fX, t1.fY, t2.fX, t2.fY, endPt.fX, endPt.fY)
        return endPt
    }
    private fun addMoveCubic(p: SkPathBuilder, startPt: SkPoint): SkPoint {
        val moveTo = pt(startPt.fX, startPt.fY + 10f)
        val t1 = pt(moveTo.fX + 15f, moveTo.fY + 5f)
        val t2 = pt(moveTo.fX + 25f, moveTo.fY + 5f)
        val endPt = pt(moveTo.fX + 40f, moveTo.fY)
        p.moveTo(moveTo.fX, moveTo.fY)
        p.cubicTo(t1.fX, t1.fY, t2.fX, t2.fY, endPt.fX, endPt.fY)
        return endPt
    }
    private fun addMoveCubicClose(p: SkPathBuilder, startPt: SkPoint): SkPoint {
        val moveTo = pt(startPt.fX, startPt.fY + 10f)
        val t1 = pt(moveTo.fX + 15f, moveTo.fY + 5f)
        val t2 = pt(moveTo.fX + 25f, moveTo.fY + 5f)
        val endPt = pt(moveTo.fX + 40f, moveTo.fY)
        p.moveTo(moveTo.fX, moveTo.fY)
        p.cubicTo(t1.fX, t1.fY, t2.fX, t2.fY, endPt.fX, endPt.fY); p.close()
        return endPt
    }

    private fun drawPath(
        path: SkPath,
        canvas: SkCanvas,
        color: Int,
        clip: SkRect,
        cap: SkPaint.Cap,
        join: SkPaint.Join,
        style: SkPaint.Style,
        fill: SkPathFillType,
        strokeWidth: Float,
    ) {
        val typed = path.makeFillType(fill)
        val paint = SkPaint().apply {
            strokeCap = cap
            this.strokeWidth = strokeWidth
            strokeJoin = join
            this.color = color
            this.style = style
        }
        canvas.save()
        canvas.clipRect(clip)
        canvas.drawPath(typed, paint)
        canvas.restore()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val segments: Array<AddSegment> = arrayOf(
            AddSegment(::addMove),
            AddSegment(::addMoveClose),
            AddSegment(::addDegenLine),
            AddSegment(::addMoveDegenLine),
            AddSegment(::addMoveDegenLineClose),
            AddSegment(::addDegenQuad),
            AddSegment(::addMoveDegenQuad),
            AddSegment(::addMoveDegenQuadClose),
            AddSegment(::addDegenCubic),
            AddSegment(::addMoveDegenCubic),
            AddSegment(::addMoveDegenCubicClose),
            AddSegment(::addClose),
            AddSegment(::addLine),
            AddSegment(::addMoveLine),
            AddSegment(::addMoveLineClose),
            AddSegment(::addQuad),
            AddSegment(::addMoveQuad),
            AddSegment(::addMoveQuadClose),
            AddSegment(::addCubic),
            AddSegment(::addMoveCubic),
            AddSegment(::addMoveCubicClose),
        )

        val fills = arrayOf(
            SkPathFillType.kWinding,
            SkPathFillType.kEvenOdd,
            SkPathFillType.kInverseWinding,
            SkPathFillType.kInverseEvenOdd,
        )
        val styles = arrayOf(
            SkPaint.Style.kFill_Style,
            SkPaint.Style.kStroke_Style,
            SkPaint.Style.kStrokeAndFill_Style,
        )
        // Pairs of (cap, join).
        val caps = arrayOf(
            Pair(SkPaint.Cap.kButt_Cap, SkPaint.Join.kBevel_Join),
            Pair(SkPaint.Cap.kRound_Cap, SkPaint.Join.kRound_Join),
            Pair(SkPaint.Cap.kSquare_Cap, SkPaint.Join.kBevel_Join),
        )

        val rand = SkRandom()
        val rect = SkRect.MakeWH(220f, 50f)
        c.save()
        c.translate(2f, 30f) // title row offset
        c.save()
        val numSegments = segments.size
        val numCaps = caps.size
        val numStyles = styles.size
        val numFills = fills.size
        for (row in 0 until 6) {
            if (row > 0) c.translate(0f, rect.height() + 100f)
            c.save()
            for (column in 0 until 4) {
                if (column > 0) c.translate(rect.width() + 4f, 0f)

                val color = ToolUtils.colorTo565(0xff007000.toInt())
                val style = styles[(rand.nextU() ushr 16) % numStyles]
                val cap = caps[(rand.nextU() ushr 16) % numCaps]
                val fill = fills[(rand.nextU() ushr 16) % numFills]
                val s1 = (rand.nextU() ushr 16) % numSegments
                val s2 = (rand.nextU() ushr 16) % numSegments
                val s3 = (rand.nextU() ushr 16) % numSegments
                val s4 = (rand.nextU() ushr 16) % numSegments
                val s5 = (rand.nextU() ushr 16) % numSegments

                var pt = SkPoint.Make(10f, 0f)
                val builder = SkPathBuilder()
                pt = segments[s1].add(builder, pt)
                pt = segments[s2].add(builder, pt)
                pt = segments[s3].add(builder, pt)
                pt = segments[s4].add(builder, pt)
                pt = segments[s5].add(builder, pt)

                drawPath(builder.detach(), c, color, rect,
                    cap.first, cap.second, style, fill, 6f)

                val rectPaint = SkPaint().apply {
                    this.color = SK_ColorBLACK
                    this.style = SkPaint.Style.kStroke_Style
                    strokeWidth = 0f
                    isAntiAlias = true
                }
                c.drawRect(rect, rectPaint)
            }
            c.restore()
        }
        c.restore()
        c.restore()
    }
}
