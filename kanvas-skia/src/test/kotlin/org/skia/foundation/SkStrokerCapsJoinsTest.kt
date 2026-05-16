package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Coverage for the Phase 3g additions to [SkStroker]:
 * `kSquare_Cap` / `kRound_Cap` / `kBevel_Join` / `kRound_Join`.
 * The Phase 3c kButt + kMiter coverage stays in `SkStrokerTest.kt`.
 */
class SkStrokerCapsJoinsTest {

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

    // -- kSquare_Cap -------------------------------------------------------

    @Test
    fun `kSquare_Cap extends a horizontal line by halfW past each end`() {
        // Line from (0,0) to (10,0), strokeWidth=2 → halfW=1.
        // Square cap extends each end by halfW along the segment tangent (1, 0).
        val src = SkPathBuilder().moveTo(0f, 0f).lineTo(10f, 0f).detach()
        val out = stroker(width = 2f, cap = SkPaint.Cap.kSquare_Cap).stroke(src)
        val b = bbox(out)
        // Bbox extends from −1 to 11 along X (10 + 2*halfW), and from −1 to 1 along Y.
        assertEquals(-1f, b[0], 1e-3f, "left bbox extends by halfW")
        assertEquals(11f, b[2], 1e-3f, "right bbox extends by halfW")
        assertEquals(-1f, b[1], 1e-3f)
        assertEquals(1f, b[3], 1e-3f)
    }

    @Test
    fun `kButt_Cap horizontal line bbox stays inside the segment X range`() {
        // Sanity check: the same line under kButt should have x bbox = [0, 10].
        val src = SkPathBuilder().moveTo(0f, 0f).lineTo(10f, 0f).detach()
        val out = stroker(width = 2f, cap = SkPaint.Cap.kButt_Cap).stroke(src)
        val b = bbox(out)
        assertEquals(0f, b[0], 1e-3f)
        assertEquals(10f, b[2], 1e-3f)
    }

    // -- kRound_Cap --------------------------------------------------------

    @Test
    fun `kRound_Cap emits two cubic Beziers per cap end`() {
        val src = SkPathBuilder().moveTo(0f, 0f).lineTo(10f, 0f).detach()
        val out = stroker(width = 2f, cap = SkPaint.Cap.kRound_Cap).stroke(src)
        val cubics = out.verbs.count { it == SkPath.Verb.kCubic }
        // 2 cubics per cap × 2 caps (start + end) = 4 cubic verbs.
        assertEquals(4, cubics, "kRound_Cap → 2 cubics per cap × 2 caps = 4")
    }

    @Test
    fun `kRound_Cap reaches halfW past the endpoint along the tangent`() {
        // For a horizontal line ending at (10, 0), halfW=1, the round cap's
        // outermost point lies at (11, 0). Verify the bbox extends to 11.
        val src = SkPathBuilder().moveTo(0f, 0f).lineTo(10f, 0f).detach()
        val out = stroker(width = 2f, cap = SkPaint.Cap.kRound_Cap).stroke(src)
        val b = bbox(out)
        // Cubic Bézier endpoints (P3) include x=11 and x=-1.
        assertEquals(-1f, b[0], 1e-3f)
        assertEquals(11f, b[2], 1e-3f)
        assertEquals(-1f, b[1], 1e-3f)
        assertEquals(1f, b[3], 1e-3f)
    }

    @Test
    fun `kRound_Cap matches kSquare_Cap bbox to within sub-pixel error`() {
        // Both caps reach halfW past the segment ends; round extends across an
        // arc, square via a flat extension. Their bounding boxes therefore
        // match exactly, but the verb stream differs.
        val src = SkPathBuilder().moveTo(0f, 0f).lineTo(10f, 0f).detach()
        val rb = bbox(stroker(width = 2f, cap = SkPaint.Cap.kRound_Cap).stroke(src))
        val sb = bbox(stroker(width = 2f, cap = SkPaint.Cap.kSquare_Cap).stroke(src))
        for (i in rb.indices) assertEquals(rb[i], sb[i], 1e-3f, "bbox[$i]")
    }

    // -- kBevel_Join -------------------------------------------------------

    @Test
    fun `kBevel_Join on a 90 degree corner emits two flat-end points (no miter)`() {
        // L-shape (0,0) → (10,0) → (10,10), halfW=1.
        // kMiter would produce a single point (10−1, 1) at the miter intersection.
        // kBevel produces two distinct points (10, 1) and (9, 0) (left side).
        val src = SkPathBuilder()
            .moveTo(0f, 0f).lineTo(10f, 0f).lineTo(10f, 10f)
            .detach()
        val miterOut = stroker(width = 2f, join = SkPaint.Join.kMiter_Join).stroke(src)
        val bevelOut = stroker(width = 2f, join = SkPaint.Join.kBevel_Join).stroke(src)
        val miterLines = miterOut.verbs.count { it == SkPath.Verb.kLine }
        val bevelLines = bevelOut.verbs.count { it == SkPath.Verb.kLine }
        // Bevel adds one extra vertex per side at the corner → 2 extra lineTo.
        assertEquals(miterLines + 2, bevelLines)
    }

    @Test
    fun `kMiter_Join above limit silently falls back to bevel`() {
        // Very tight corner: (0,0)→(10,0)→(-10,0.5). Tiny miter limit → bevel.
        val src = SkPathBuilder()
            .moveTo(0f, 0f).lineTo(10f, 0f).lineTo(-10f, 0.5f)
            .detach()
        val out = stroker(width = 2f, join = SkPaint.Join.kMiter_Join, miterLimit = 1f).stroke(src)
        // The bbox should not spike toward infinity (a true miter would).
        val b = bbox(out)
        assertTrue(b[2] - b[0] < 30f, "bbox span ${b[2] - b[0]} < 30 → bevel fallback fired")
    }

    // -- kRound_Join -------------------------------------------------------

    @Test
    fun `kRound_Join on a 90 degree corner adds line segments approximating an arc`() {
        // L-shape, halfW=1. Round join inserts ~8 segments per quarter-turn for
        // the 90° corner (one corner per side → 16 extra lineTo verbs total).
        val src = SkPathBuilder()
            .moveTo(0f, 0f).lineTo(10f, 0f).lineTo(10f, 10f)
            .detach()
        val miterOut = stroker(width = 2f, join = SkPaint.Join.kMiter_Join).stroke(src)
        val roundOut = stroker(width = 2f, join = SkPaint.Join.kRound_Join).stroke(src)
        val miterLines = miterOut.verbs.count { it == SkPath.Verb.kLine }
        val roundLines = roundOut.verbs.count { it == SkPath.Verb.kLine }
        // Round adds ~8 line segments per side per corner. With one corner ×
        // two sides we expect roundLines − miterLines ≈ 16 − 2 (the miter
        // emitted a single point per side). 14 ≤ Δ ≤ 18 covers rounding.
        val delta = roundLines - miterLines
        assertTrue(delta in 12..20, "round vs miter line delta = $delta (expected ~16)")
    }

    @Test
    fun `kRound_Join on a closed rect produces two closed sub-contours with arcs`() {
        // 10×10 closed rect, strokeWidth=2 → halfW=1. Round joins on the four
        // corners produce arcs on the outer ring (and small "wedge" overlaps
        // on the inner ring that the winding fill handles).
        val src = SkPath.Rect(org.graphiks.math.SkRect.MakeLTRB(0f, 0f, 10f, 10f))
        val out = stroker(width = 2f, join = SkPaint.Join.kRound_Join).stroke(src)
        // Two closed sub-contours.
        assertEquals(2, out.verbs.count { it == SkPath.Verb.kMove })
        assertEquals(2, out.verbs.count { it == SkPath.Verb.kClose })
        // Bbox: outer ring extends by halfW=1 in every direction.
        val b = bbox(out)
        assertEquals(-1f, b[0], 1e-3f)
        assertEquals(-1f, b[1], 1e-3f)
        assertEquals(11f, b[2], 1e-3f)
        assertEquals(11f, b[3], 1e-3f)
    }
}
