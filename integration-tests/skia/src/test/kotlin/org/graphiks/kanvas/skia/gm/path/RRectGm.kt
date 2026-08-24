package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32

/**
 * Port of Skia's `gm/rrect.cpp`. A 4-row x 4-column grid of stroked rrects,
 * each row showing a different inset strategy and each column a different
 * starting RRectF32 type (rect / oval / simple / complex per-corner radii).
 * Within each cell the inset is applied for d in [-30, 30] step 5.
 */
/**
 * Port of Skia's `gm/rrect.cpp`.
 * 4×4 grid of stroked rrects with different inset strategies.
 * @see https://github.com/google/skia/blob/main/gm/rrect.cpp
 */
class RRectGm : SkiaGm {
    override val name = "rrect"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 76.4
    override val width = 820
    override val height = 710

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val insetProcs: List<(RRectF32, Float, Float) -> RRectF32> = listOf(
            ::inset0, ::inset1, ::inset2, ::inset3
        )

        val r = RectF32.ofLTRB(0f, 0f, 120f, 100f)
        val radii = arrayOf(
            CornerRadiiF32.of(0f, 0f),
            CornerRadiiF32.of(30f, 1f),
            CornerRadiiF32.of(10f, 40f),
            CornerRadiiF32.of(40f, 40f),
        )

        val rrects = arrayOf(
            RRectF32.of(r, 0f),
            RRectF32.of(r, CornerRadiiF32.of(r.width() / 2f, r.height() / 2f)),
            RRectF32.of(r, 20f),
            RRectF32.of(r, radii[0], radii[1], radii[2], radii[3]),
        )

        canvas.translate(50.5f, 50.5f)
        for (j in insetProcs.indices) {
            canvas.save()
            for (i in rrects.indices) {
                drawRR(canvas, rrects[i], insetProcs[j])
                canvas.translate(200f, 0f)
            }
            canvas.restore()
            canvas.translate(0f, 170f)
        }
    }

    private fun drawRR(
        canvas: GmCanvas,
        src: RRectF32,
        proc: (RRectF32, Float, Float) -> RRectF32,
    ) {
        var d = -30f
        while (d <= 30f) {
            val rr = proc(src, d, d)
            drawColored(canvas, rr)
            d += 5f
        }
    }

    private fun drawColored(canvas: GmCanvas, rrect: RRectF32) {
        val paint = Paint(
            antiAlias = true,
            style = PaintStyle.STROKE,
            color = when {
                isRect(rrect) -> ColorARGB.Red
                isOval(rrect) -> ColorARGB.fromRGBA(0f, 0x88 / 255f, 0f, 1f)
                isSimple(rrect) -> ColorARGB.Blue
                else -> ColorARGB.fromRGBA(0f, 0f, 0f, 1f)
            },
        )
        val path = Path { }.apply { addRRect(rrect) }
        canvas.drawPath(path, paint)
    }

    // ----- inset procs -----

    private fun inset0(src: RRectF32, dx: Float, dy: Float): RRectF32 {
        val r = insetRect(src.rect, dx, dy) ?: return RRectF32.of(RectF32(0f, 0f, 0f, 0f), 0f)
        return RRectF32.of(
            r,
            clampRadii(src.topLeft, dx, dy),
            clampRadii(src.topRight, dx, dy),
            clampRadii(src.bottomRight, dx, dy),
            clampRadii(src.bottomLeft, dx, dy),
        )
    }

    private fun inset1(src: RRectF32, dx: Float, dy: Float): RRectF32 {
        val r = insetRect(src.rect, dx, dy) ?: return RRectF32.of(RectF32(0f, 0f, 0f, 0f), 0f)
        return RRectF32.of(r, src.topLeft, src.topRight, src.bottomRight, src.bottomLeft)
    }

    private fun inset2(src: RRectF32, dx: Float, dy: Float): RRectF32 {
        val r = insetRect(src.rect, dx, dy) ?: return RRectF32.of(RectF32(0f, 0f, 0f, 0f), 0f)
        return RRectF32.of(
            r,
            if (src.topLeft.x != 0f) CornerRadiiF32.of(src.topLeft.x - dx, src.topLeft.y - dy) else CornerRadiiF32.of(0f, 0f),
            if (src.topRight.x != 0f) CornerRadiiF32.of(src.topRight.x - dx, src.topRight.y - dy) else CornerRadiiF32.of(0f, 0f),
            if (src.bottomRight.x != 0f) CornerRadiiF32.of(src.bottomRight.x - dx, src.bottomRight.y - dy) else CornerRadiiF32.of(0f, 0f),
            if (src.bottomLeft.x != 0f) CornerRadiiF32.of(src.bottomLeft.x - dx, src.bottomLeft.y - dy) else CornerRadiiF32.of(0f, 0f),
        )
    }

    private fun inset3(src: RRectF32, dx: Float, dy: Float): RRectF32 {
        val r = insetRect(src.rect, dx, dy) ?: return RRectF32.of(RectF32(0f, 0f, 0f, 0f), 0f)
        val ow = src.rect.width()
        val oh = src.rect.height()
        val nw = r.width
        val nh = r.height
        return RRectF32.of(
            r,
            CornerRadiiF32.of(nw * src.topLeft.x / ow, nh * src.topLeft.y / oh),
            CornerRadiiF32.of(nw * src.topRight.x / ow, nh * src.topRight.y / oh),
            CornerRadiiF32.of(nw * src.bottomRight.x / ow, nh * src.bottomRight.y / oh),
            CornerRadiiF32.of(nw * src.bottomLeft.x / ow, nh * src.bottomLeft.y / oh),
        )
    }

    private fun clampRadii(v: CornerRadiiF32, dx: Float, dy: Float): CornerRadiiF32 =
        CornerRadiiF32.of(maxOf(v.x - dx, 0f), maxOf(v.y - dy, 0f))

    private fun insetRect(rect: RectF32, dx: Float, dy: Float): RectF32? {
        val r = RectF32.ofLTRB(rect.left + dx, rect.top + dy, rect.right - dx, rect.bottom - dy)
        return if (r.left >= r.right || r.top >= r.bottom) null else r
    }

    // ----- RRectF32 classification helpers -----

    private fun isRect(rr: RRectF32): Boolean =
        rr.topLeft.x == 0f && rr.topLeft.y == 0f &&
            rr.topRight.x == 0f && rr.topRight.y == 0f &&
            rr.bottomRight.x == 0f && rr.bottomRight.y == 0f &&
            rr.bottomLeft.x == 0f && rr.bottomLeft.y == 0f

    private fun isOval(rr: RRectF32): Boolean {
        val w2 = rr.rect.width / 2f
        val h2 = rr.rect.height / 2f
        return rr.topLeft.x == w2 && rr.topLeft.y == h2 &&
            rr.topRight.x == w2 && rr.topRight.y == h2 &&
            rr.bottomRight.x == w2 && rr.bottomRight.y == h2 &&
            rr.bottomLeft.x == w2 && rr.bottomLeft.y == h2
    }

    private fun isSimple(rr: RRectF32): Boolean =
        rr.topLeft == rr.topRight && rr.topLeft == rr.bottomRight && rr.topLeft == rr.bottomLeft
}
