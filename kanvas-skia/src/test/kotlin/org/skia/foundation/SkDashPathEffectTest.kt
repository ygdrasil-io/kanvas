package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkMatrix

/**
 * Unit tests for [SkDashPathEffect] (Phase 7b).
 *
 * Coverage :
 *  - Factory rejects malformed intervals (odd count, negative entries).
 *  - Single-segment line dashing produces the expected number of "on"
 *    sub-segments under a given pattern + phase.
 *  - Phase wraps modulo the cycle length.
 *  - Per-contour reset : a `kMove` to a disjoint contour restarts the
 *    cycle.
 *  - Degenerate intervals (`[0, 0]`) yield an empty output path.
 *  - Per-segment continuity : a contour made of two `kLine`s sharing
 *    a vertex produces dashes whose phase carries across the join.
 */
class SkDashPathEffectTest {

    private val identity = SkMatrix.Identity

    @Test
    fun `Make rejects odd-length interval arrays`() {
        try {
            SkDashPathEffect.Make(floatArrayOf(5f, 3f, 2f), 0f)
            assertTrue(false, "expected exception")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("even") == true)
        }
    }

    @Test
    fun `Make rejects empty interval arrays`() {
        try {
            SkDashPathEffect.Make(floatArrayOf(), 0f)
            assertTrue(false, "expected exception")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("≥ 2") == true)
        }
    }

    @Test
    fun `Make rejects negative intervals`() {
        try {
            SkDashPathEffect.Make(floatArrayOf(5f, -1f), 0f)
            assertTrue(false, "expected exception")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("non-negative") == true)
        }
    }

    @Test
    fun `Single line full-cycle pattern emits the expected dashes`() {
        // 100-unit line dashed [10 on, 10 off] → 5 on-segments + 0
        // trailing tail (the last 10 units fall in the "off" half).
        val pe = SkDashPathEffect.Make(floatArrayOf(10f, 10f), 0f)
        val input = SkPathBuilder().moveTo(0f, 0f).lineTo(100f, 0f).detach()
        val out = pe.filterPath(input, identity)
        assertNotNull(out)
        // Each "on" dash = 1 moveTo + 1 lineTo = 2 verbs.
        // 5 dashes × 2 = 10 verbs.
        val moveCount = out!!.verbs.count { it == SkPath.Verb.kMove }
        val lineCount = out.verbs.count { it == SkPath.Verb.kLine }
        assertEquals(5, moveCount, "expected 5 dashes (moveTo)")
        assertEquals(5, lineCount, "expected 5 dash bodies (lineTo)")
    }

    @Test
    fun `Single line non-divisible length truncates the trailing dash`() {
        // 25-unit line, [10, 10] pattern : dashes at 0..10 and 20..25.
        // The second dash is truncated to 5 units.
        val pe = SkDashPathEffect.Make(floatArrayOf(10f, 10f), 0f)
        val input = SkPathBuilder().moveTo(0f, 0f).lineTo(25f, 0f).detach()
        val out = pe.filterPath(input, identity)
        assertNotNull(out)
        // Expect 2 on-segments : 0..10 and 20..25.
        assertEquals(2, out!!.verbs.count { it == SkPath.Verb.kMove })
        assertEquals(2, out.verbs.count { it == SkPath.Verb.kLine })
        // Verify the truncated dash's endpoint is at x=25.
        val coords = out.coords
        // Last lineTo target.
        val lastIdx = coords.size - 2
        assertEquals(25f, coords[lastIdx], 1e-4f)
        assertEquals(0f, coords[lastIdx + 1], 1e-4f)
    }

    @Test
    fun `Phase shifts the cycle along the path`() {
        // Same 25-unit line but with phase = 5 (skip the first 5 units).
        // Cycle position at start = 5 ⇒ start in interval [5..10] of "on",
        // then off [10..20], then on [20..30] truncated at 25.
        // Expected segments : 0..5 (within the first on dash, 5 long),
        // 20..25 (5 long).
        val pe = SkDashPathEffect.Make(floatArrayOf(10f, 10f), 5f)
        val input = SkPathBuilder().moveTo(0f, 0f).lineTo(25f, 0f).detach()
        val out = pe.filterPath(input, identity)
        assertNotNull(out)
        // 2 on-segments expected.
        assertEquals(2, out!!.verbs.count { it == SkPath.Verb.kMove })
    }

    @Test
    fun `Phase wraps modulo total cycle`() {
        // [10, 10] with phase = 25 should be equivalent to phase = 5.
        val pe1 = SkDashPathEffect.Make(floatArrayOf(10f, 10f), 5f)
        val pe2 = SkDashPathEffect.Make(floatArrayOf(10f, 10f), 25f)
        val input = SkPathBuilder().moveTo(0f, 0f).lineTo(100f, 0f).detach()
        val out1 = pe1.filterPath(input, identity)!!
        val out2 = pe2.filterPath(input, identity)!!
        // Same number of on-segments + same coords.
        assertEquals(out1.verbs.size, out2.verbs.size, "same verb count")
        for (i in out1.coords.indices) {
            assertEquals(out1.coords[i], out2.coords[i], 1e-4f, "coord $i")
        }
    }

    @Test
    fun `Zero-zero intervals produce an empty path`() {
        val pe = SkDashPathEffect.Make(floatArrayOf(0f, 0f), 0f)
        val input = SkPathBuilder().moveTo(0f, 0f).lineTo(100f, 0f).detach()
        val out = pe.filterPath(input, identity)
        assertNotNull(out)
        assertTrue(out!!.isEmpty(), "expected empty path for [0,0] intervals")
    }

    @Test
    fun `Empty input produces an empty path`() {
        val pe = SkDashPathEffect.Make(floatArrayOf(5f, 5f), 0f)
        val out = pe.filterPath(SkPathBuilder().detach(), identity)
        assertNotNull(out)
        assertTrue(out!!.isEmpty())
    }

    @Test
    fun `Per-contour reset starts each contour at phase 0`() {
        // Two disjoint contours of length 10, [10, 10] pattern, phase = 0.
        // Each contour starts in the "on" interval ⇒ each contour emits
        // exactly one full dash from 0..10.
        val pe = SkDashPathEffect.Make(floatArrayOf(10f, 10f), 0f)
        val input = SkPathBuilder()
            .moveTo(0f, 0f).lineTo(10f, 0f)
            .moveTo(0f, 20f).lineTo(10f, 20f)
            .detach()
        val out = pe.filterPath(input, identity)
        assertNotNull(out)
        assertEquals(2, out!!.verbs.count { it == SkPath.Verb.kMove }, "expected 2 dashes")
        assertEquals(2, out.verbs.count { it == SkPath.Verb.kLine })
    }

    @Test
    fun `Phase carries across consecutive lines in same contour`() {
        // Single contour : two consecutive lines forming an L-shape, total
        // arc length 20 (10 + 10). Pattern [10, 10] starts on at the
        // origin, off at distance 10 (= the corner), on again at distance
        // 20 — but distance 20 is the contour end, so only the first
        // dash is emitted.
        val pe = SkDashPathEffect.Make(floatArrayOf(10f, 10f), 0f)
        val input = SkPathBuilder()
            .moveTo(0f, 0f)
            .lineTo(10f, 0f)
            .lineTo(10f, 10f)
            .detach()
        val out = pe.filterPath(input, identity)
        assertNotNull(out)
        // First dash (0..10 horizontal) is emitted ; second segment
        // (10..20 down) is in the "off" half.
        assertEquals(1, out!!.verbs.count { it == SkPath.Verb.kMove })
        assertEquals(1, out.verbs.count { it == SkPath.Verb.kLine })
        // Validate the dash endpoint at (10, 0).
        val coords = out.coords
        assertEquals(10f, coords[2], 1e-4f)
        assertEquals(0f, coords[3], 1e-4f)
    }

    @Test
    fun `Quad input flattens and dashes by chord`() {
        // A flat-ish quadratic (very low curvature) should produce an
        // output path whose total emitted "on" length is approximately
        // half the input arc length under [10, 10] pattern.
        val pe = SkDashPathEffect.Make(floatArrayOf(10f, 10f), 0f)
        val input = SkPathBuilder()
            .moveTo(0f, 0f)
            .quadTo(50f, 1f, 100f, 0f)   // nearly straight, length ≈ 100
            .detach()
        val out = pe.filterPath(input, identity)
        assertNotNull(out)
        // The flattening produces several short chords ; each emits its
        // own segment if it lands in an "on" interval, so the dash count
        // is approximate. We want at least 4 (the "ideal" count for a
        // straight-line equivalent) and bound at 12 (worst-case
        // fine subdivision).
        val dashCount = out!!.verbs.count { it == SkPath.Verb.kMove }
        assertTrue(dashCount in 4..12) {
            "quad dashing: expected 4..12 dashes, got $dashCount"
        }
    }
}
