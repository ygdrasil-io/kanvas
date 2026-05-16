package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.tools.ToolUtils
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Port of Skia's `gm/polygonoffset.cpp::PolygonOffsetGM` (512 × 512).
 *
 * Lays out a sequence of polygons (up to `kNumPaths = 20`) and for
 * each draws :
 *  1. the original polygon (1px black stroke), and
 *  2. a sequence of inset (convex variant) or offset (simple variant)
 *     contours coloured by their offset distance.
 *
 * The two `DEF_GM` registrations specialise on `convexOnly = true`
 * (`convex-polygon-inset`) and `convexOnly = false`
 * (`simple-polygon-offset`).
 *
 * **kanvas-skia adaptation** : upstream's offset algorithm relies on
 * `SkInsetConvexPolygon` (`src/utils/SkPolyUtils.cpp`) and
 * `SkOffsetSimplePolygon` (same file) which are not yet ported to
 * `:kanvas-skia` (a ≈1.5 kLOC dependency tracked as a separate
 * follow-up — see [SkShadowTessellator] for the same blocker). We
 * therefore approximate :
 *  - **Convex inset** : a uniform centroid-shrink — each vertex moves
 *    toward the polygon centroid by `offset / d_to_edge` (a coarse
 *    but visually-similar surrogate for the true offset polygon).
 *    Holds for the convex variant only ; the offset shapes are smaller
 *    than the originals but their shape may differ slightly from
 *    upstream's true normal-offset polygon.
 *  - **Simple offset** (concave) : we draw only the original polygons.
 *    The reference `simple-polygon-offset.png` shows extra outer +
 *    inner concentric copies that our port omits ; the structural
 *    polygon centres still line up with the reference.
 *
 * C++ source : see `gm/polygonoffset.cpp`. References:
 * `convex-polygon-inset.png`, `simple-polygon-offset.png`.
 */
public class PolygonOffsetGM(private val convexOnly: Boolean) : GM() {

    init { setBGColor(0xFFFFFFFF.toInt()) }

    override fun getName(): String =
        if (convexOnly) "convex-polygon-inset" else "simple-polygon-offset"

    override fun getISize(): SkISize = SkISize.Make(kGMWidth, kGMHeight)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val offset = SkPoint.Make(0f, kMaxPathHeight / 2f + (if (!convexOnly) kMaxOutset.toFloat() else 0f))
        val pos = floatArrayOf(offset.fX, offset.fY)
        for (i in 0 until kNumPaths) {
            drawPolygon(c, i, pos)
        }
    }

    private fun drawPolygon(canvas: SkCanvas, index: Int, position: FloatArray) {
        val data = if (convexOnly) getConvexPolygon(index, dirCW = true) else getSimplePolygon(index, dirCW = true)
        val bounds = computeBounds(data)
        if (!convexOnly) {
            // outset by kMaxOutset to avoid clipping the offset rings.
            bounds.outset(kMaxOutset.toFloat(), kMaxOutset.toFloat())
        }
        if (position[0] + bounds.width() > kGMWidth) {
            position[0] = 0f
            position[1] += kMaxPathHeight
        }
        val cx = position[0] + bounds.width() / 2f
        val cy = position[1]
        position[0] += bounds.width()

        // Use the per-orientation polygon for the actual draw.
        val drawData = if (convexOnly)
            getConvexPolygon(index, dirCW = (index % 2 == 0))
        else
            getSimplePolygon(index, dirCW = (index % 2 == 0))

        val paint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 1f
        }

        // Original polygon — black.
        canvas.save()
        canvas.translate(cx, cy)
        canvas.drawPath(toClosedPath(drawData), paint)
        canvas.restore()

        // Approximated inset rings (convex variant only).
        if (convexOnly) {
            val insets = floatArrayOf(5f, 10f, 15f, 20f, 25f, 30f, 35f, 40f)
            val colors = intArrayOf(
                0xFF901313.toInt(), 0xFF8D6214.toInt(), 0xFF698B14.toInt(),
                0xFF1C8914.toInt(), 0xFF148755.toInt(), 0xFF146C84.toInt(),
                0xFF142482.toInt(), 0xFF4A1480.toInt(),
            )
            for (i in insets.indices) {
                val inset = approxInsetPolygon(drawData, insets[i]) ?: continue
                paint.color = ToolUtils.colorTo565(colors[i])
                canvas.save()
                canvas.translate(cx, cy)
                canvas.drawPath(toClosedPath(inset), paint)
                canvas.restore()
            }
        }
    }

    // --- helpers ----------------------------------------------------------

    private fun toClosedPath(pts: Array<SkPoint>): SkPath {
        val pb = SkPathBuilder()
        if (pts.isEmpty()) return pb.detach()
        pb.moveTo(pts[0].fX, pts[0].fY)
        for (i in 1 until pts.size) pb.lineTo(pts[i].fX, pts[i].fY)
        pb.close()
        return pb.detach()
    }

    private fun computeBounds(pts: Array<SkPoint>): SkRect {
        if (pts.isEmpty()) return SkRect.MakeLTRB(0f, 0f, 0f, 0f)
        var l = pts[0].fX
        var r = l
        var t = pts[0].fY
        var b = t
        for (p in pts) {
            l = min(l, p.fX); r = max(r, p.fX)
            t = min(t, p.fY); b = max(b, p.fY)
        }
        return SkRect.MakeLTRB(l, t, r, b)
    }

    /**
     * Coarse centroid-shrink approximation of the upstream
     * `SkInsetConvexPolygon`. For each vertex `v` we step toward the
     * centroid by `offset` units. Returns null when the inset is
     * larger than the polygon's "radius" to centroid (would collapse).
     */
    private fun approxInsetPolygon(pts: Array<SkPoint>, offset: Float): Array<SkPoint>? {
        if (pts.size < 3) return null
        val cx = pts.fold(0f) { a, p -> a + p.fX } / pts.size
        val cy = pts.fold(0f) { a, p -> a + p.fY } / pts.size
        val out = Array(pts.size) { SkPoint.Make(0f, 0f) }
        for (i in pts.indices) {
            val dx = pts[i].fX - cx
            val dy = pts[i].fY - cy
            val len = kotlin.math.sqrt(dx * dx + dy * dy)
            if (len <= offset + 1e-3f) return null
            val s = (len - offset) / len
            out[i] = SkPoint.Make(cx + dx * s, cy + dy * s)
        }
        return out
    }

    // --- polygon data (convex only — concave/simple is procedural) -------

    private fun getConvexPolygon(index: Int, dirCW: Boolean): Array<SkPoint> {
        val raw: Array<SkPoint> = when (index) {
            0 -> arrayOf(p(-1.5f, -50f), p(1.5f, -50f), p(1.5f, 50f), p(-1.5f, 50f))
            1 -> arrayOf(p(-50f, -49f), p(-49f, -50f), p(50f, 49f), p(49f, 50f))
            2 -> arrayOf(p(-10f, -50f), p(10f, -50f), p(50f, 50f), p(-50f, 50f))
            3 -> arrayOf(p(-50f, -50f), p(0f, -50f), p(50f, 50f), p(0f, 50f))
            4 -> arrayOf(
                p(-6f, -50f), p(4f, -50f), p(5f, -25f), p(6f, 0f),
                p(5f, 25f), p(4f, 50f), p(-4f, 50f),
            )
            5 -> arrayOf(p(-0.025f, -0.025f), p(0.025f, -0.025f), p(0.025f, 0.025f), p(-0.025f, 0.025f))
            6 -> arrayOf(p(-20f, -13f), p(-20f, -13.05f), p(20f, -13f), p(20f, 27f))
            7 -> arrayOf(
                p(-10f, -50f), p(10f, -50f), p(10f, -20f), p(10f, 0f),
                p(10f, 35f), p(10f, 50f), p(-10f, 50f),
            )
            8 -> arrayOf(
                p(50f, 50f), p(0f, 50f), p(-15.45f, 47.55f), p(-29.39f, 40.45f),
                p(-40.45f, 29.39f), p(-47.55f, 15.45f), p(-50f, 0f),
                p(-47.55f, -15.45f), p(-40.45f, -29.39f), p(-29.39f, -40.45f),
                p(-15.45f, -47.55f), p(0f, -50f), p(50f, -50f),
            )
            9 -> arrayOf(
                p(4.39f, 40.45f), p(-9.55f, 47.55f), p(-25f, 50f), p(-40.45f, 47.55f),
                p(-54.39f, 40.45f), p(-65.45f, 29.39f), p(-72.55f, 15.45f), p(-75f, 0f),
                p(-72.55f, -15.45f), p(-65.45f, -29.39f), p(-54.39f, -40.45f),
                p(-40.45f, -47.55f), p(-25f, -50f), p(-9.55f, -47.55f), p(4.39f, -40.45f),
                p(75f, 0f),
            )
            10 -> arrayOf(
                p(-10f, -50f), p(10f, -50f), p(50f, 31f), p(40f, 50f),
                p(-40f, 50f), p(-50f, 31f),
            )
            else -> {
                // Procedurally generated n-gon — same numPtsArray as upstream.
                val numPtsArray = intArrayOf(3, 4, 5, 5, 6, 8, 8, 20, 100)
                val arrayIndex = (index - 11).coerceIn(0, numPtsArray.size - 1)
                val n = numPtsArray[arrayIndex]
                var w = (kMaxPathHeight / 2f).toFloat()
                val h = w
                if (arrayIndex == 3 || arrayIndex == 6) w = (kMaxPathHeight / 5f).toFloat()
                createNgon(n, w, h)
            }
        }

        return if (dirCW) raw else raw.reversedArray()
    }

    private fun getSimplePolygon(index: Int, dirCW: Boolean): Array<SkPoint> {
        // The 15 hand-coded simple polygons of the upstream GM are
        // structurally diverse. For our approximated port we re-use the
        // convex set (clamped to its 11 entries) and procedural n-gons
        // for the remaining indices ; the visual layout is preserved.
        if (index < 11) return getConvexPolygon(index, dirCW)
        val numPtsArray = intArrayOf(5, 7, 8, 20, 100)
        val arrayIndex = (index - 11).coerceIn(0, numPtsArray.size - 1)
        val n = numPtsArray[arrayIndex]
        val w = (kMaxPathHeight / 5f)
        val h = (kMaxPathHeight / 2f)
        val raw = createNgon(n, w, h)
        return if (dirCW) raw else raw.reversedArray()
    }

    private fun createNgon(n: Int, w: Float, h: Float): Array<SkPoint> {
        val deg = 360f / n
        var angle = if (n % 2 == 1) deg / 2f else 0f
        return Array(n) {
            val rad = angle * (PI.toFloat() / 180f)
            val pt = SkPoint.Make(-sin(rad) * w, cos(rad) * h)
            angle += deg
            pt
        }
    }

    private fun p(x: Float, y: Float): SkPoint = SkPoint.Make(x, y)

    private companion object {
        const val kNumPaths: Int = 20
        const val kMaxPathHeight: Int = 100
        const val kMaxOutset: Int = 16
        const val kGMWidth: Int = 512
        const val kGMHeight: Int = 512
    }
}
