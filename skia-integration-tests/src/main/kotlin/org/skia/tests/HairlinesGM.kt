package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SkColorSetARGB
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize
import org.skia.math.SkRect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of Skia's `gm/hairlines.cpp::HairlinesGM` (1250 × 1250).
 *
 * 14 stress paths × 3 stroke widths (`{0, 0.5, 1.5}`) × 2 AA modes
 * × 2 alpha values (`{0xFF, 0x40}`) = 168 draws, packed left-to-right
 * with row-wraparound at `wrapX = 1245`. Each cell is sized by its
 * path's bounds and translated to start at `(-bounds.left,
 * -bounds.top)` so the path renders in the corner of the cell.
 *
 * Paths span : 15-spoke star, near-vertical-tangent quads (4 widths),
 * 2 cubic-cusp regression paths, 3 missing-end-cap line bundles, an
 * arc + chord at radius 2000.
 */
public class HairlinesGM : GM() {

    override fun getName(): String = "hairlines"
    override fun getISize(): SkISize = SkISize.Make(1250, 1250)

    private val paths: List<SkPath> = buildPaths()

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val alphaValue = intArrayOf(0xFF, 0x40)
        val widths = floatArrayOf(0f, 0.5f, 1.5f)
        val margin = 5
        val wrapX = 1250 - margin

        var maxH = 0f
        c.translate(margin.toFloat(), margin.toFloat())
        c.save()

        var x = margin.toFloat()
        for (p in paths) {
            for (a in alphaValue) {
                for (aa in 0..1) {
                    for (w in widths) {
                        val bounds = p.computeBounds()

                        if (x + bounds.width() > wrapX) {
                            c.restore()
                            c.translate(0f, maxH + margin)
                            c.save()
                            maxH = 0f
                            x = margin.toFloat()
                        }

                        val paint = SkPaint().apply {
                            color = SkColorSetARGB(a, 0, 0, 0)
                            isAntiAlias = aa != 0
                            style = SkPaint.Style.kStroke_Style
                            strokeWidth = w
                        }
                        c.save()
                        c.translate(-bounds.left, -bounds.top)
                        c.drawPath(p, paint)
                        c.restore()

                        if (bounds.height() > maxH) maxH = bounds.height()
                        val dx = bounds.width() + margin
                        x += dx
                        c.translate(dx, 0f)
                    }
                }
            }
        }
        c.restore()
    }

    private fun buildPaths(): List<SkPath> {
        val out = mutableListOf<SkPath>()

        // 15-spoke star at radius 40.
        val lineAngles = SkPathBuilder()
        val numAngles = 15
        val radius = 40f
        for (i in 0 until numAngles) {
            val angle = PI.toFloat() * i / numAngles
            val xx = radius * cos(angle)
            val yy = radius * sin(angle)
            lineAngles.moveTo(xx, yy).lineTo(-xx, -yy)
        }
        out.add(lineAngles.detach())

        out.add(SkPathBuilder().moveTo(0f, -10f).quadTo(100f, 100f, -10f, 0f).detach())
        out.add(SkPathBuilder().moveTo(0f, -5f).quadTo(100f, 100f, -5f, 0f).detach())
        out.add(SkPathBuilder().moveTo(0f, -2f).quadTo(100f, 100f, -2f, 0f).detach())
        out.add(SkPathBuilder().moveTo(0f, -1f).quadTo(100f, 100f, -2f + 306f / 4f, 75f).detach())
        out.add(SkPathBuilder().moveTo(0f, -1f).quadTo(100f, 100f, -1f, 0f).detach())
        out.add(SkPathBuilder().moveTo(0f, 0f).quadTo(100f, 100f, 0f, 0f).detach())
        out.add(SkPathBuilder().moveTo(0f, 0f).quadTo(100f, 100f, 75f, 75f).detach())

        // shapeops bbox-bug regressions.
        out.add(
            SkPathBuilder().moveTo(4f, 6f)
                .cubicTo(5f, 6f, 5f, 4f, 4f, 0f).close().detach()
        )
        out.add(
            SkPathBuilder().moveTo(5f, 1f)
                .lineTo(4.32787323f, 1.67212653f)
                .cubicTo(2.75223875f, 3.24776125f, 3.00581908f, 4.51236057f, 3.7580452f, 4.37367964f)
                .cubicTo(4.66472578f, 3.888381f, 5f, 2.875f, 5f, 1f)
                .close().detach()
        )

        // Three missing-end-cap regressions.
        out.add(SkPathBuilder()
            .moveTo(6.5f, 5.5f).lineTo(3.5f, 0.5f)
            .moveTo(0.5f, 5.5f).lineTo(3.5f, 0.5f)
            .detach())
        // crbug 137317 X.
        out.add(SkPathBuilder()
            .moveTo(1f, 1f).lineTo(6f, 6f)
            .moveTo(1f, 6f).lineTo(6f, 1f)
            .detach())
        // crbug 137465 / 256776 right angle.
        out.add(SkPathBuilder()
            .moveTo(5.5f, 5.5f).lineTo(5.5f, 0.5f).lineTo(0.5f, 0.5f)
            .detach())

        // crbug 295626 arc + chord at r=2000.
        run {
            val rad = 2000f
            val startAngle = 262.59717f
            val sweepAngle = 17.188717f / 2f

            val bug = SkPathBuilder()
            val circle = SkRect.MakeLTRB(-rad, -rad, rad, rad)
            bug.addArc(circle, startAngle, sweepAngle)
            val s0 = startAngle * PI.toFloat() / 180f
            val s1 = (startAngle + sweepAngle) * PI.toFloat() / 180f
            val p0x = rad * cos(s0); val p0y = rad * sin(s0)
            val p1x = rad * cos(s1); val p1y = rad * sin(s1)
            bug.moveTo(p0x, p0y)
            bug.lineTo(p1x, p1y)
            out.add(bug.detach())
        }

        return out
    }
}
