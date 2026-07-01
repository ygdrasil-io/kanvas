package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.CornerRadii
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/rrect.cpp`. A 4-row x 4-column grid of stroked rrects,
 * each row showing a different inset strategy and each column a different
 * starting RRect type (rect / oval / simple / complex per-corner radii).
 * Within each cell the inset is applied for d in [-30, 30] step 5.
 */
class RRectGm : SkiaGm {
    override val name = "rrect"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 820
    override val height = 710

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val insetProcs: List<(RRect, Float, Float) -> RRect> = listOf(
            ::inset0, ::inset1, ::inset2, ::inset3
        )

        val r = Rect.fromLTRB(0f, 0f, 120f, 100f)
        val radii = arrayOf(
            CornerRadii(0f, 0f),
            CornerRadii(30f, 1f),
            CornerRadii(10f, 40f),
            CornerRadii(40f, 40f),
        )

        val rrects = arrayOf(
            RRect(r, 0f),
            RRect(r, CornerRadii(r.width / 2f, r.height / 2f)),
            RRect(r, 20f),
            RRect(r, radii[0], radii[1], radii[2], radii[3]),
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
        src: RRect,
        proc: (RRect, Float, Float) -> RRect,
    ) {
        var d = -30f
        while (d <= 30f) {
            val rr = proc(src, d, d)
            drawColored(canvas, rr)
            d += 5f
        }
    }

    private fun drawColored(canvas: GmCanvas, rrect: RRect) {
        val paint = Paint(
            antiAlias = true,
            style = PaintStyle.STROKE,
            color = when {
                isRect(rrect) -> Color.RED
                isOval(rrect) -> Color.fromRGBA(0f, 0x88 / 255f, 0f, 1f)
                isSimple(rrect) -> Color.BLUE
                else -> Color.fromRGBA(0f, 0f, 0f, 1f)
            },
        )
        val path = Path { }.apply { addRRect(rrect) }
        canvas.drawPath(path, paint)
    }

    // ----- inset procs -----

    private fun inset0(src: RRect, dx: Float, dy: Float): RRect {
        val r = insetRect(src.rect, dx, dy) ?: return RRect(Rect(0f, 0f, 0f, 0f), 0f)
        return RRect(
            r,
            clampRadii(src.topLeft, dx, dy),
            clampRadii(src.topRight, dx, dy),
            clampRadii(src.bottomRight, dx, dy),
            clampRadii(src.bottomLeft, dx, dy),
        )
    }

    private fun inset1(src: RRect, dx: Float, dy: Float): RRect {
        val r = insetRect(src.rect, dx, dy) ?: return RRect(Rect(0f, 0f, 0f, 0f), 0f)
        return RRect(r, src.topLeft, src.topRight, src.bottomRight, src.bottomLeft)
    }

    private fun inset2(src: RRect, dx: Float, dy: Float): RRect {
        val r = insetRect(src.rect, dx, dy) ?: return RRect(Rect(0f, 0f, 0f, 0f), 0f)
        return RRect(
            r,
            if (src.topLeft.x != 0f) CornerRadii(src.topLeft.x - dx, src.topLeft.y - dy) else CornerRadii(0f, 0f),
            if (src.topRight.x != 0f) CornerRadii(src.topRight.x - dx, src.topRight.y - dy) else CornerRadii(0f, 0f),
            if (src.bottomRight.x != 0f) CornerRadii(src.bottomRight.x - dx, src.bottomRight.y - dy) else CornerRadii(0f, 0f),
            if (src.bottomLeft.x != 0f) CornerRadii(src.bottomLeft.x - dx, src.bottomLeft.y - dy) else CornerRadii(0f, 0f),
        )
    }

    private fun inset3(src: RRect, dx: Float, dy: Float): RRect {
        val r = insetRect(src.rect, dx, dy) ?: return RRect(Rect(0f, 0f, 0f, 0f), 0f)
        val ow = src.rect.width
        val oh = src.rect.height
        val nw = r.width
        val nh = r.height
        return RRect(
            r,
            CornerRadii(nw * src.topLeft.x / ow, nh * src.topLeft.y / oh),
            CornerRadii(nw * src.topRight.x / ow, nh * src.topRight.y / oh),
            CornerRadii(nw * src.bottomRight.x / ow, nh * src.bottomRight.y / oh),
            CornerRadii(nw * src.bottomLeft.x / ow, nh * src.bottomLeft.y / oh),
        )
    }

    private fun clampRadii(v: CornerRadii, dx: Float, dy: Float): CornerRadii =
        CornerRadii(maxOf(v.x - dx, 0f), maxOf(v.y - dy, 0f))

    private fun insetRect(rect: Rect, dx: Float, dy: Float): Rect? {
        val r = Rect.fromLTRB(rect.left + dx, rect.top + dy, rect.right - dx, rect.bottom - dy)
        return if (r.left >= r.right || r.top >= r.bottom) null else r
    }

    // ----- RRect classification helpers -----

    private fun isRect(rr: RRect): Boolean =
        rr.topLeft.x == 0f && rr.topLeft.y == 0f &&
            rr.topRight.x == 0f && rr.topRight.y == 0f &&
            rr.bottomRight.x == 0f && rr.bottomRight.y == 0f &&
            rr.bottomLeft.x == 0f && rr.bottomLeft.y == 0f

    private fun isOval(rr: RRect): Boolean {
        val w2 = rr.rect.width / 2f
        val h2 = rr.rect.height / 2f
        return rr.topLeft.x == w2 && rr.topLeft.y == h2 &&
            rr.topRight.x == w2 && rr.topRight.y == h2 &&
            rr.bottomRight.x == w2 && rr.bottomRight.y == h2 &&
            rr.bottomLeft.x == w2 && rr.bottomLeft.y == h2
    }

    private fun isSimple(rr: RRect): Boolean =
        rr.topLeft == rr.topRight && rr.topLeft == rr.bottomRight && rr.topLeft == rr.bottomLeft
}
