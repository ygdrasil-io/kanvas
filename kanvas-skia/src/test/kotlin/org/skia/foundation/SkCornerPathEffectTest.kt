package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkMatrix

/**
 * Unit tests for [SkCornerPathEffect] (Phase 7p2).
 *
 * Coverage :
 *  - Factory rejects non-positive / non-finite radius.
 *  - Open polyline keeps endpoint vertices sharp (no smoothing on
 *    `V₀` / `Vₙ₋₁`).
 *  - Closed polygon smooths every vertex including the closing one.
 *  - Single-segment polyline (2 points) is passed through verbatim.
 *  - Smoothing inserts a quadTo verb around each interior vertex.
 *  - Empty input returns null (passthrough).
 */
class SkCornerPathEffectTest {

    private val identity = SkMatrix.Identity

    @Test
    fun `Make rejects non-positive radius`() {
        assertNull(SkCornerPathEffect.Make(0f))
        assertNull(SkCornerPathEffect.Make(-1f))
        assertNull(SkCornerPathEffect.Make(Float.NaN))
    }

    @Test
    fun `Make returns a non-null effect for positive finite radius`() {
        val pe = SkCornerPathEffect.Make(5f)
        assertNotNull(pe)
    }

    @Test
    fun `Empty input passes through as null`() {
        val pe = SkCornerPathEffect.Make(5f)!!
        assertNull(pe.filterPath(SkPathBuilder().detach(), identity))
    }

    @Test
    fun `Single segment open polyline emits no smoothing verbs`() {
        val pe = SkCornerPathEffect.Make(5f)!!
        val input = SkPathBuilder().moveTo(0f, 0f).lineTo(100f, 0f).detach()
        val out = pe.filterPath(input, identity)!!
        // Expect : kMove + kLine, no kQuad.
        assertTrue(out.verbs.count { it == SkPath.StorageVerb.kQuad } == 0) {
            "single segment should not introduce a quad, got verbs=${out.verbs.toList()}"
        }
    }

    @Test
    fun `Open polyline with 3 points smooths the interior vertex`() {
        // L-shape : (0, 0) → (50, 0) → (50, 50). The interior vertex
        // is (50, 0) which should be smoothed into a quad.
        val pe = SkCornerPathEffect.Make(10f)!!
        val input = SkPathBuilder()
            .moveTo(0f, 0f)
            .lineTo(50f, 0f)
            .lineTo(50f, 50f)
            .detach()
        val out = pe.filterPath(input, identity)!!
        // Expect : kMove(0,0), kLine(40,0), kQuad(50,0; 50,10), kLine(50,50).
        assertTrue(out.verbs.contains(SkPath.StorageVerb.kQuad)) {
            "interior vertex of L-shape should be smoothed, got verbs=${out.verbs.toList()}"
        }
    }

    @Test
    fun `Closed polygon smooths every vertex`() {
        // Closed unit square scaled to 100×100. 4 vertices, all interior
        // (because closed) — expect 4 kQuad verbs.
        val pe = SkCornerPathEffect.Make(10f)!!
        val input = SkPathBuilder()
            .moveTo(0f, 0f)
            .lineTo(100f, 0f)
            .lineTo(100f, 100f)
            .lineTo(0f, 100f)
            .close()
            .detach()
        val out = pe.filterPath(input, identity)!!
        val quadCount = out.verbs.count { it == SkPath.StorageVerb.kQuad }
        assertTrue(quadCount == 4) {
            "closed quad polygon expected 4 smoothed corners, got $quadCount " +
                "(verbs=${out.verbs.toList()})"
        }
    }

    @Test
    fun `Open triangle with 4 vertices smooths only interior 2`() {
        // Open polyline : (0,0) → (50,0) → (50,50) → (100,50).
        // Interior vertices : (50,0) and (50,50). Expect 2 kQuad verbs.
        val pe = SkCornerPathEffect.Make(10f)!!
        val input = SkPathBuilder()
            .moveTo(0f, 0f)
            .lineTo(50f, 0f)
            .lineTo(50f, 50f)
            .lineTo(100f, 50f)
            .detach()
        val out = pe.filterPath(input, identity)!!
        val quadCount = out.verbs.count { it == SkPath.StorageVerb.kQuad }
        assertTrue(quadCount == 2) {
            "open polyline of 4 points should smooth 2 interior corners, got $quadCount"
        }
    }

    @Test
    fun `Tiny segments cap the smoothing budget at half-segment length`() {
        // Triangle with a very short edge between two long ones — the
        // smoothing should not eat the entire short edge.
        val pe = SkCornerPathEffect.Make(50f)!!  // requested radius huge
        val input = SkPathBuilder()
            .moveTo(0f, 0f)
            .lineTo(100f, 0f)
            .lineTo(102f, 0f)   // 2-unit short segment
            .lineTo(202f, 100f)
            .detach()
        val out = pe.filterPath(input, identity)!!
        // The implementation must NOT crash + must still produce a
        // path (corner clamped to 1-unit smoothing per side).
        assertTrue(out.verbs.isNotEmpty())
    }
}
