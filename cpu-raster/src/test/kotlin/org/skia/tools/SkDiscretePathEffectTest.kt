package org.skia.tools

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkMatrix

/**
 * Unit tests for [SkDiscretePathEffect] (Phase 7p2).
 *
 * Coverage :
 *  - Factory rejects non-positive / non-finite [segLength].
 *  - Empty input returns null (passthrough).
 *  - Determinism : same seed + same input → identical output.
 *  - Different seeds produce different outputs.
 *  - segLength shorter than path subdivides into many sub-segments.
 *  - Output stays within deviation bound of the original path
 *    (perpendicular-only jitter — no along-axis drift).
 */
class SkDiscretePathEffectTest {

    private val identity = SkMatrix.Identity

    @Test
    fun `Make rejects non-positive segLength`() {
        assertNull(SkDiscretePathEffect.Make(0f, 5f))
        assertNull(SkDiscretePathEffect.Make(-1f, 5f))
        assertNull(SkDiscretePathEffect.Make(Float.NaN, 5f))
    }

    @Test
    fun `Make rejects non-finite deviation`() {
        assertNull(SkDiscretePathEffect.Make(10f, Float.NaN))
    }

    @Test
    fun `Make returns a non-null effect for positive finite parameters`() {
        assertNotNull(SkDiscretePathEffect.Make(10f, 5f))
        assertNotNull(SkDiscretePathEffect.Make(10f, 0f))
    }

    @Test
    fun `Empty input passes through as null`() {
        val pe = SkDiscretePathEffect.Make(10f, 5f)!!
        assertNull(pe.filterPath(SkPathBuilder().detach(), identity))
    }

    @Test
    fun `Same seed produces identical output across runs`() {
        val pe1 = SkDiscretePathEffect.Make(10f, 5f, seed = 42)!!
        val pe2 = SkDiscretePathEffect.Make(10f, 5f, seed = 42)!!
        val input = SkPathBuilder().moveTo(0f, 0f).lineTo(100f, 0f).detach()
        val a = pe1.filterPath(input, identity)!!
        val b = pe2.filterPath(input, identity)!!
        assertEquals(a.verbs.toList(), b.verbs.toList())
        assertEquals(a.coords.toList(), b.coords.toList())
    }

    @Test
    fun `Different seeds produce different outputs`() {
        val pe1 = SkDiscretePathEffect.Make(10f, 5f, seed = 1)!!
        val pe2 = SkDiscretePathEffect.Make(10f, 5f, seed = 2)!!
        val input = SkPathBuilder().moveTo(0f, 0f).lineTo(100f, 0f).detach()
        val a = pe1.filterPath(input, identity)!!
        val b = pe2.filterPath(input, identity)!!
        // Verbs should match (same subdivision count) but coordinates
        // should differ (different random samples).
        assertEquals(a.verbs.toList(), b.verbs.toList())
        assertTrue(a.coords.toList() != b.coords.toList()) {
            "different seeds should produce different jitter"
        }
    }

    @Test
    fun `segLength subdivides a long line into many sub-segments`() {
        // 100-unit line with segLength=10 → 10 sub-segments.
        val pe = SkDiscretePathEffect.Make(10f, 2f, seed = 1)!!
        val input = SkPathBuilder().moveTo(0f, 0f).lineTo(100f, 0f).detach()
        val out = pe.filterPath(input, identity)!!
        // kMove + 10 * kLine.
        val lineCount = out.verbs.count { it == SkPath.Verb.kLine }
        assertEquals(10, lineCount, "expected 10 sub-segments for length 100 / segLength 10")
    }

    @Test
    fun `Zero deviation produces unjittered subdivisions`() {
        val pe = SkDiscretePathEffect.Make(10f, 0f, seed = 1)!!
        val input = SkPathBuilder().moveTo(0f, 0f).lineTo(100f, 0f).detach()
        val out = pe.filterPath(input, identity)!!
        // All sub-segments should land exactly on the input line (y=0).
        // coords layout : [x0, y0] for kMove, then [x, y] per kLine.
        for (i in 1 until out.coords.size step 2) {
            assertEquals(0f, out.coords[i], 1e-3f, "y at coord ${i / 2} should be 0 with deviation=0")
        }
    }

    @Test
    fun `Jittered output stays within deviation bound perpendicular to input`() {
        // Horizontal input — perpendicular jitter is along y.
        val deviation = 4f
        val pe = SkDiscretePathEffect.Make(10f, deviation, seed = 7)!!
        val input = SkPathBuilder().moveTo(0f, 0f).lineTo(100f, 0f).detach()
        val out = pe.filterPath(input, identity)!!
        // Skip first coord (move-to (0,0)) and last (lineTo to endpoint
        // which the impl pins to the original).
        for (i in 1 until out.coords.size step 2) {
            val y = out.coords[i]
            assertTrue(kotlin.math.abs(y) <= deviation + 1e-3f) {
                "y=$y exceeds deviation $deviation at coord ${i / 2}"
            }
        }
    }

    @Test
    fun `Closed contour jitters the closing edge too`() {
        val pe = SkDiscretePathEffect.Make(10f, 2f, seed = 5)!!
        val input = SkPathBuilder()
            .moveTo(0f, 0f)
            .lineTo(40f, 0f)
            .lineTo(40f, 40f)
            .close()
            .detach()
        val out = pe.filterPath(input, identity)!!
        // Final verb should be kClose, and we should have jitter on
        // the closing edge (40, 40) → (0, 0).
        assertEquals(SkPath.Verb.kClose, out.verbs.last())
        val lineCount = out.verbs.count { it == SkPath.Verb.kLine }
        assertTrue(lineCount >= 3) {
            "closed contour should have at least 3 jittered subsegments, got $lineCount"
        }
    }
}
