package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private class BoundedPathBuilder {
    val path = Path { }
    private var minX = Float.MAX_VALUE
    private var minY = Float.MAX_VALUE
    private var maxX = -Float.MAX_VALUE
    private var maxY = -Float.MAX_VALUE

    fun moveTo(x: Float, y: Float): BoundedPathBuilder {
        include(x, y); path.moveTo(x, y); return this
    }

    fun lineTo(x: Float, y: Float): BoundedPathBuilder {
        include(x, y); path.lineTo(x, y); return this
    }

    fun quadTo(cx: Float, cy: Float, x: Float, y: Float): BoundedPathBuilder {
        include(cx, cy); include(x, y); path.quadTo(cx, cy, x, y); return this
    }

    fun cubicTo(cx1: Float, cy1: Float, cx2: Float, cy2: Float, x: Float, y: Float): BoundedPathBuilder {
        include(cx1, cy1); include(cx2, cy2); include(x, y); path.cubicTo(cx1, cy1, cx2, cy2, x, y); return this
    }

    fun close(): BoundedPathBuilder { path.close(); return this }

    fun detach(): Pair<Path, Rect> = path to Rect.fromLTRB(minX, minY, maxX, maxY)

    private fun include(x: Float, y: Float) {
        if (x < minX) minX = x; if (y < minY) minY = y
        if (x > maxX) maxX = x; if (y > maxY) maxY = y
    }
}

private val boundedPaths: List<Pair<Path, Rect>> = buildBoundedPaths()

private fun buildBoundedPaths(): List<Pair<Path, Rect>> {
    val out = mutableListOf<Pair<Path, Rect>>()

    val lineAngles = BoundedPathBuilder()
    val numAngles = 15
    val radius = 40f
    for (i in 0 until numAngles) {
        val angle = PI.toFloat() * i / numAngles
        val xx = radius * cos(angle)
        val yy = radius * sin(angle)
        lineAngles.moveTo(xx, yy).lineTo(-xx, -yy)
    }
    out.add(lineAngles.detach())

    out.add(BoundedPathBuilder().moveTo(0f, -10f).quadTo(100f, 100f, -10f, 0f).detach())
    out.add(BoundedPathBuilder().moveTo(0f, -5f).quadTo(100f, 100f, -5f, 0f).detach())
    out.add(BoundedPathBuilder().moveTo(0f, -2f).quadTo(100f, 100f, -2f, 0f).detach())
    out.add(BoundedPathBuilder().moveTo(0f, -1f).quadTo(100f, 100f, -2f + 306f / 4f, 75f).detach())
    out.add(BoundedPathBuilder().moveTo(0f, -1f).quadTo(100f, 100f, -1f, 0f).detach())
    out.add(BoundedPathBuilder().moveTo(0f, 0f).quadTo(100f, 100f, 0f, 0f).detach())
    out.add(BoundedPathBuilder().moveTo(0f, 0f).quadTo(100f, 100f, 75f, 75f).detach())

    out.add(
        BoundedPathBuilder().moveTo(4f, 6f)
            .cubicTo(5f, 6f, 5f, 4f, 4f, 0f).close().detach()
    )
    out.add(
        BoundedPathBuilder().moveTo(5f, 1f)
            .lineTo(4.32787323f, 1.67212653f)
            .cubicTo(2.75223875f, 3.24776125f, 3.00581908f, 4.51236057f, 3.7580452f, 4.37367964f)
            .cubicTo(4.66472578f, 3.888381f, 5f, 2.875f, 5f, 1f)
            .close().detach()
    )

    out.add(
        BoundedPathBuilder()
            .moveTo(6.5f, 5.5f).lineTo(3.5f, 0.5f)
            .moveTo(0.5f, 5.5f).lineTo(3.5f, 0.5f)
            .detach()
    )
    out.add(
        BoundedPathBuilder()
            .moveTo(1f, 1f).lineTo(6f, 6f)
            .moveTo(1f, 6f).lineTo(6f, 1f)
            .detach()
    )
    out.add(
        BoundedPathBuilder()
            .moveTo(5.5f, 5.5f).lineTo(5.5f, 0.5f).lineTo(0.5f, 0.5f)
            .detach()
    )

    run {
        val rad = 2000f
        val startAngle = 262.59717f
        val sweepAngle = 17.188717f / 2f
        val startRad = startAngle * PI.toFloat() / 180f
        val endRad = (startAngle + sweepAngle) * PI.toFloat() / 180f
        val numSteps = 20

        val sx = rad * cos(startRad); val sy = rad * sin(startRad)
        val ex = rad * cos(endRad); val ey = rad * sin(endRad)

        val bug = BoundedPathBuilder()
            .moveTo(sx, sy)
        for (i in 1..numSteps) {
            val t = i.toFloat() / numSteps
            val a = startRad + t * (endRad - startRad)
            bug.lineTo(rad * cos(a), rad * sin(a))
        }
        bug.moveTo(sx, sy).lineTo(ex, ey)
        out.add(bug.detach())
    }

    return out
}

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
class HairlinesGm : SkiaGm {
    override val name = "hairlines"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 1250
    override val height = 1250

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val alphaValue = intArrayOf(0xFF, 0x40)
        val widths = floatArrayOf(0f, 0.5f, 1.5f)
        val margin = 5
        val wrapX = 1250 - margin

        var maxH = 0f
        canvas.translate(margin.toFloat(), margin.toFloat())
        canvas.save()

        var x = margin.toFloat()
        for ((p, bounds) in boundedPaths) {
            for (a in alphaValue) {
                for (aa in 0..1) {
                    for (w in widths) {
                        val bw = bounds.width

                        if (x + bw > wrapX) {
                            canvas.restore()
                            canvas.translate(0f, maxH + margin)
                            canvas.save()
                            maxH = 0f
                            x = margin.toFloat()
                        }

                        val paint = Paint(
                            color = Color.fromRGBA(0f, 0f, 0f, a / 255f),
                            antiAlias = aa != 0,
                            style = PaintStyle.STROKE,
                            strokeWidth = w,
                        )
                        canvas.save()
                        canvas.translate(-bounds.left, -bounds.top)
                        canvas.drawPath(p, paint)
                        canvas.restore()

                        if (bounds.height > maxH) maxH = bounds.height
                        val dx = bw + margin
                        x += dx
                        canvas.translate(dx, 0f)
                    }
                }
            }
        }
        canvas.restore()
    }
}
