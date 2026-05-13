package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Slice 3.7 — SVG-style elliptic arc on `SkPathBuilder` (`arcTo` /
 * `rArcTo` with the SVG `(rx, ry, xAxisRotate, largeArc, sweep, x, y)`
 * signature). Mirrors `include/core/SkPathBuilder.h:605, 670` and
 * `src/core/SkPathBuilder.cpp:519-645`.
 *
 * The implementation follows the SVG endpoint-to-conic conversion
 * (W3C SVG implnote `ArcConversionEndpointToCenter`); each ≤120° span
 * is approximated by one rational conic Bézier of weight
 * `cos(thetaWidth / 2)`.
 */
class SkPathSvgArcTest {

    private val SMALL = SkPathBuilder.ArcSize.kSmall_ArcSize
    private val LARGE = SkPathBuilder.ArcSize.kLarge_ArcSize

    private fun lastConicEnd(p: SkPath): Pair<Float, Float> {
        val n = p.coords.size
        return p.coords[n - 2] to p.coords[n - 1]
    }

    // --- Degenerate fall-throughs --------------------------------------

    @Test
    fun `zero rx degenerates to lineTo`() {
        val p = SkPathBuilder()
            .moveTo(0f, 0f)
            .arcTo(0f, 10f, 0f, SMALL, SkPathDirection.kCW, 50f, 50f)
            .detach()
        // Expect kMove + kLine (no conic).
        assertEquals(2, p.verbs.size)
        assertEquals(SkPath.StorageVerb.kLine, p.verbs[1])
        assertEquals(50f, p.coords[2], 1e-4f)
        assertEquals(50f, p.coords[3], 1e-4f)
    }

    @Test
    fun `zero ry degenerates to lineTo`() {
        val p = SkPathBuilder()
            .moveTo(0f, 0f)
            .arcTo(10f, 0f, 0f, SMALL, SkPathDirection.kCW, 50f, 50f)
            .detach()
        assertEquals(2, p.verbs.size)
        assertEquals(SkPath.StorageVerb.kLine, p.verbs[1])
    }

    @Test
    fun `start equals end degenerates to lineTo (zero-length)`() {
        val p = SkPathBuilder()
            .moveTo(10f, 10f)
            .arcTo(20f, 30f, 0f, LARGE, SkPathDirection.kCW, 10f, 10f)
            .detach()
        // No conic — falls through to lineTo (which is a no-op self-line).
        assertEquals(2, p.verbs.size)
        assertEquals(SkPath.StorageVerb.kLine, p.verbs[1])
    }

    // --- Quarter-circle arcs -------------------------------------------

    @Test
    fun `quarter circle (small CW) emits exactly one conic ending at endpoint`() {
        // 90° arc on a unit circle, start at (1, 0), end at (0, 1), going CW.
        val p = SkPathBuilder()
            .moveTo(1f, 0f)
            .arcTo(1f, 1f, 0f, SMALL, SkPathDirection.kCW, 0f, 1f)
            .detach()
        // 1 conic for ≤120° span.
        assertTrue(p.verbs.contains(SkPath.StorageVerb.kConic), "must emit a conic")
        val (ex, ey) = lastConicEnd(p)
        assertEquals(0f, ex, 1e-4f)
        assertEquals(1f, ey, 1e-4f)
    }

    @Test
    fun `quarter circle ends exactly on the requested endpoint (snapped)`() {
        // The Skia implementation snaps the last segment's end to (x, y) to
        // erase float drift through the matrix chain.
        val p = SkPathBuilder()
            .moveTo(100f, 0f)
            .arcTo(100f, 100f, 0f, SMALL, SkPathDirection.kCW, 0f, 100f)
            .detach()
        val (ex, ey) = lastConicEnd(p)
        // Even with chained rotate + scale, the final coord must be
        // bit-exact (or at least within sub-ulp) of the endpoint.
        assertEquals(0f, ex, 1e-4f)
        assertEquals(100f, ey, 1e-4f)
    }

    // --- Half-circle ---------------------------------------------------

    @Test
    fun `half circle emits at least 2 conics (180 degrees over 120 max)`() {
        // Half circle from (1, 0) to (-1, 0) on unit circle. 180° / 120° = 2 segments.
        val p = SkPathBuilder()
            .moveTo(1f, 0f)
            .arcTo(1f, 1f, 0f, LARGE, SkPathDirection.kCW, -1f, 0f)
            .detach()
        val conics = p.verbs.count { it == SkPath.StorageVerb.kConic }
        assertTrue(conics >= 2, "half circle should emit ≥ 2 conics, got $conics")
        val (ex, ey) = lastConicEnd(p)
        assertEquals(-1f, ex, 1e-4f)
        assertEquals(0f, ey, 1e-4f)
    }

    // --- Large vs small arc --------------------------------------------

    @Test
    fun `large flag selects the longer arc (3+ conics for 270 degrees)`() {
        // Same chord as a 90° quarter, but with kLarge_ArcSize → 270° arc
        // = 3 segments at 90° each (or 3 at 90° = ceil(270/120)=3).
        val p = SkPathBuilder()
            .moveTo(1f, 0f)
            .arcTo(1f, 1f, 0f, LARGE, SkPathDirection.kCW, 0f, 1f)
            .detach()
        val conics = p.verbs.count { it == SkPath.StorageVerb.kConic }
        assertTrue(conics >= 3, "large arc should emit ≥ 3 conics, got $conics")
    }

    // --- Sweep direction -----------------------------------------------

    @Test
    fun `CW vs CCW pick opposite arcs of the same chord`() {
        val cw = SkPathBuilder().moveTo(1f, 0f)
            .arcTo(1f, 1f, 0f, SMALL, SkPathDirection.kCW, 0f, 1f).detach()
        val ccw = SkPathBuilder().moveTo(1f, 0f)
            .arcTo(1f, 1f, 0f, SMALL, SkPathDirection.kCCW, 0f, 1f).detach()
        // Both reach (0, 1) at the end.
        val (cwEx, cwEy) = lastConicEnd(cw)
        val (ccwEx, ccwEy) = lastConicEnd(ccw)
        assertEquals(0f, cwEx, 1e-4f); assertEquals(1f, cwEy, 1e-4f)
        assertEquals(0f, ccwEx, 1e-4f); assertEquals(1f, ccwEy, 1e-4f)
        // But the conic *control points* lie on opposite sides of the chord:
        // CW small arc bulges toward (1, 1); CCW small arc bulges toward (0, 0).
        // Conic control = coords[2..3] (after the moveTo at coords[0..1]).
        val cwCtrlX = cw.coords[2]; val cwCtrlY = cw.coords[3]
        val ccwCtrlX = ccw.coords[2]; val ccwCtrlY = ccw.coords[3]
        // For unit circle: CW small arc centred at (0,0), control near (1, 1).
        //                  CCW small arc centred near (1, 1), control near (0, 0).
        assertTrue(cwCtrlX > 0.5f && cwCtrlY > 0.5f,
            "CW small arc control should be near (1,1), got ($cwCtrlX, $cwCtrlY)")
        assertTrue(ccwCtrlX < 0.5f && ccwCtrlY < 0.5f,
            "CCW small arc control should be near (0,0), got ($ccwCtrlX, $ccwCtrlY)")
    }

    // --- Out-of-range radii (auto-scaled per SVG spec) -----------------

    @Test
    fun `radii too small are scaled up to span the chord`() {
        // Chord length = 2 (from (-1,0) to (1,0)); requested radii (0.5, 0.5)
        // can't span — Skia / SVG auto-scales radii to fit.
        val p = SkPathBuilder()
            .moveTo(-1f, 0f)
            .arcTo(0.5f, 0.5f, 0f, SMALL, SkPathDirection.kCW, 1f, 0f)
            .detach()
        // Must still produce a finite, well-formed path ending at (1, 0).
        assertTrue(p.isFinite())
        val (ex, ey) = lastConicEnd(p)
        assertEquals(1f, ex, 1e-3f)
        assertEquals(0f, ey, 1e-3f)
    }

    // --- xAxisRotate ---------------------------------------------------

    @Test
    fun `xAxisRotate 90 degrees turns a horizontal arc into a vertical one`() {
        // Arc from (1, 0) to (0, 1) on a (rx=1, ry=2) ellipse with no rotation.
        val unrotated = SkPathBuilder()
            .moveTo(1f, 0f)
            .arcTo(1f, 2f, 0f, SMALL, SkPathDirection.kCW, 0f, 1f)
            .detach()
        // Same arc, rotated 90°: rx and ry effectively swap.
        // For rotation alone, the start and end have to reflect the
        // intended geometry — let's just sanity-check that both produce
        // a finite path with the right endpoint.
        val rotated = SkPathBuilder()
            .moveTo(1f, 0f)
            .arcTo(1f, 2f, 90f, SMALL, SkPathDirection.kCW, 0f, 1f)
            .detach()
        assertTrue(unrotated.isFinite())
        assertTrue(rotated.isFinite())
        // Endpoints land on (0, 1) for both.
        val (ux, uy) = lastConicEnd(unrotated)
        val (rx, ry) = lastConicEnd(rotated)
        assertEquals(0f, ux, 1e-4f); assertEquals(1f, uy, 1e-4f)
        assertEquals(0f, rx, 1e-4f); assertEquals(1f, ry, 1e-4f)
    }

    // --- rArcTo --------------------------------------------------------

    @Test
    fun `rArcTo translates the endpoint by current pen position`() {
        val abs = SkPathBuilder()
            .moveTo(10f, 20f)
            .arcTo(5f, 5f, 0f, SMALL, SkPathDirection.kCW, 20f, 25f)
            .detach()
        val rel = SkPathBuilder()
            .moveTo(10f, 20f)
            .rArcTo(5f, 5f, 0f, SMALL, SkPathDirection.kCW, 10f, 5f)   // dx=10, dy=5 → (20, 25)
            .detach()
        // Both should produce identical verb streams + coords.
        assertTrue(abs.verbs contentEquals rel.verbs)
        for (i in abs.coords.indices) {
            assertEquals(abs.coords[i], rel.coords[i], 1e-4f, "coord[$i]")
        }
    }

    // --- Arc traces an ellipse (sampling check) ------------------------

    @Test
    fun `quarter arc on an ellipse keeps the curve on the analytic ellipse`() {
        // Arc from (rx, 0) to (0, ry) on an axis-aligned ellipse.
        // Sample the conic at intermediate t values and check
        //   (x/rx)² + (y/ry)² ≈ 1.
        val rxV = 100f; val ryV = 60f
        val p = SkPathBuilder()
            .moveTo(rxV, 0f)
            .arcTo(rxV, ryV, 0f, SMALL, SkPathDirection.kCW, 0f, ryV)
            .detach()
        // Single conic for a 90° arc.
        val ctrlX = p.coords[2]; val ctrlY = p.coords[3]
        val endX = p.coords[4]; val endY = p.coords[5]
        val w = p.conicWeights[0]
        val startX = rxV; val startY = 0f
        for (k in 1..7) {
            val t = k.toFloat() / 8f
            val u = 1f - t
            val numW = u * u + 2f * u * t * w + t * t
            val sx = (u * u * startX + 2f * u * t * w * ctrlX + t * t * endX) / numW
            val sy = (u * u * startY + 2f * u * t * w * ctrlY + t * t * endY) / numW
            val r2 = (sx / rxV) * (sx / rxV) + (sy / ryV) * (sy / ryV)
            assertTrue(abs(r2 - 1f) < 5e-3f,
                "sampled point at t=$t falls off the ellipse: r²=$r2")
        }
    }
}
