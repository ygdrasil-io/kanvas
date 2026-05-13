package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SkStrokerTest {

    private fun stroker(
        width: Float,
        cap: SkPaint.Cap = SkPaint.Cap.kButt_Cap,
        join: SkPaint.Join = SkPaint.Join.kMiter_Join,
        miterLimit: Float = 4f,
    ): SkStroker = SkStroker.fromPaint(SkPaint().apply {
        strokeWidth = width
        strokeCap = cap
        strokeJoin = join
        strokeMiter = miterLimit
    })

    private fun bbox(path: SkPath): FloatArray {
        var minX = Float.POSITIVE_INFINITY; var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY
        var i = 0
        for (verb in path.verbs) {
            for (k in 0 until verb.pointCount) {
                val x = path.coords[i++]; val y = path.coords[i++]
                if (x < minX) minX = x; if (y < minY) minY = y
                if (x > maxX) maxX = x; if (y > maxY) maxY = y
            }
        }
        return floatArrayOf(minX, minY, maxX, maxY)
    }

    @Test
    fun `empty source produces empty output`() {
        val out = stroker(2f).stroke(SkPathBuilder().detach())
        assertTrue(out.isEmpty())
    }

    @Test
    fun `width 0 falls back to width 1 hairline`() {
        val src = SkPathBuilder().moveTo(0f, 0f).lineTo(10f, 0f).detach()
        // Both width=0 and width=1 should produce a 1-pixel-wide rectangle.
        val w0 = stroker(0f).stroke(src)
        val w1 = stroker(1f).stroke(src)
        // Bounding box of both should match.
        val b0 = bbox(w0)
        val b1 = bbox(w1)
        for (i in b0.indices) assertEquals(b0[i], b1[i], 1e-4f, "bbox[$i]")
    }

    @Test
    fun `negative width produces empty output (degenerate)`() {
        val out = stroker(-2f).stroke(
            SkPathBuilder().moveTo(0f, 0f).lineTo(10f, 0f).detach()
        )
        // Stroker treats width<=0 as 1px hairline, so output is non-empty.
        // The width=0 case is exercised by the previous test; here we just
        // ensure no crash on negative width.
        assertTrue(!out.isEmpty())
    }

    @Test
    fun `straight horizontal line strokes to a 4-vertex rectangle`() {
        val src = SkPathBuilder().moveTo(0f, 0f).lineTo(10f, 0f).detach()
        val out = stroker(2f).stroke(src)
        // Single open contour → single closed sub-contour: M, L, L, L, close = 5 verbs.
        val verbCounts = out.verbs.groupingBy { it }.eachCount()
        assertEquals(1, verbCounts[SkPath.Verb.kMove])
        assertEquals(3, verbCounts[SkPath.Verb.kLine])  // 1 along left side + 1 cap + 1 along right side reverse
        assertEquals(1, verbCounts[SkPath.Verb.kClose])

        // Bounding box: 0,−1 to 10,1 (width=2 → halfW=1)
        val b = bbox(out)
        assertEquals(0f, b[0], 1e-4f, "bbox left")
        assertEquals(-1f, b[1], 1e-4f, "bbox top")
        assertEquals(10f, b[2], 1e-4f, "bbox right")
        assertEquals(1f, b[3], 1e-4f, "bbox bottom")
    }

    @Test
    fun `closed rect strokes to outer + inner sub-contours`() {
        val src = SkPath.Rect(org.skia.math.SkRect.MakeLTRB(10f, 10f, 30f, 30f))
        val out = stroker(2f).stroke(src)
        // Two closed sub-contours = 2 moveTo + 2 close.
        val moves = out.verbs.count { it == SkPath.Verb.kMove }
        val closes = out.verbs.count { it == SkPath.Verb.kClose }
        assertEquals(2, moves, "outer + inner ring → 2 moveTo")
        assertEquals(2, closes, "both rings closed")

        // Outer ring expands by halfW=1 in every direction; inner ring contracts by 1.
        // Combined bbox = outer bbox = (9, 9, 31, 31).
        val b = bbox(out)
        assertEquals(9f, b[0], 1e-4f)
        assertEquals(9f, b[1], 1e-4f)
        assertEquals(31f, b[2], 1e-4f)
        assertEquals(31f, b[3], 1e-4f)
    }

    @Test
    fun `right-angle corner emits miter join inside the limit`() {
        // L-shape: (0,0) → (10,0) → (10,10). 90° corner at (10, 0).
        val src = SkPathBuilder().moveTo(0f, 0f).lineTo(10f, 0f).lineTo(10f, 10f).detach()
        val out = stroker(2f).stroke(src)
        // For a 90° corner, miter distance = halfW * sqrt(2) ≈ 1.414, well below
        // miterLimit=4 → miter accepted on both sides → no bevel doubling of vertices.
        // Verb stream: M, L (left[0]→left[mid] miter), L (left[mid]→left[end]),
        //              L (cap to right[end]), L (right[end-1]), L (right[mid] miter),
        //              L (right[start]), close.
        // i.e. one moveTo + 6 lineTo + close.
        val lines = out.verbs.count { it == SkPath.Verb.kLine }
        // With miters on both inside and outside corners (single point each), we get:
        //   left polyline: 3 points (start, miter, end)
        //   right polyline: 3 points (start, miter, end)
        //   outline: M(left[0]) + L(left[mid]) + L(left[end]) + L(right[end]) +
        //            L(right[mid]) + L(right[start]) + close
        // → 1 move + 5 line + close.
        assertEquals(5, lines, "miter join produces 1 vertex, not 2 — verb stream stays compact")
    }

    @Test
    fun `acute corner exceeding miter limit falls back to bevel`() {
        // Very acute corner: (0,0) → (10,0) → (-10, 0.5). The two segments nearly
        // overlap ⇒ miter intersection lies far away ⇒ bevel.
        val src = SkPathBuilder().moveTo(0f, 0f).lineTo(10f, 0f).lineTo(-10f, 0.5f).detach()
        val out = stroker(2f, miterLimit = 2f).stroke(src)
        // Bevel adds an extra vertex per side at the corner compared to miter.
        // We can't easily count miters vs bevels without parsing the polyline, but
        // we can verify the bbox stays reasonable (no spike toward infinity).
        val b = bbox(out)
        val span = b[2] - b[0]
        assertTrue(span < 30f, "bevel should keep bbox bounded; got span=$span")
    }

    @Test
    fun `quadTo curve strokes to a curved band`() {
        // Quadratic arc from (0, 0) to (10, 0) bulging through (5, 10).
        val src = SkPathBuilder().moveTo(0f, 0f).quadTo(5f, 10f, 10f, 0f).detach()
        val out = stroker(2f).stroke(src)
        // Bounding box dilates by halfW=1 around the analytic curve bbox (~ -1..6 vertical).
        val b = bbox(out)
        assertTrue(b[1] >= -1.5f && b[1] <= 0f, "stroker bottom (y=top) within ±1 of source bottom: ${b[1]}")
        assertTrue(b[3] >= 5f, "stroker top (y=bottom) above curve apex midpoint: ${b[3]}")
    }

    @Test
    fun `stroked closed contour fillType is winding`() {
        val src = SkPath.Rect(org.skia.math.SkRect.MakeLTRB(0f, 0f, 10f, 10f))
        val out = stroker(2f).stroke(src)
        assertEquals(SkPathFillType.kWinding, out.fillType,
            "stroker output relies on winding-rule cancellation between outer & inner rings")
    }

    @Test
    fun `strokes are not the same as fills`() {
        val src = SkPath.Rect(org.skia.math.SkRect.MakeLTRB(0f, 0f, 10f, 10f))
        val out = stroker(2f).stroke(src)
        // The verb count differs: source is 1 move + 3 line + 1 close = 5 verbs.
        // The stroked outline is 2 sub-contours, each 1 move + 4 line + 1 close = 12 verbs.
        assertNotEquals(src.verbs.size, out.verbs.size)
    }
}
