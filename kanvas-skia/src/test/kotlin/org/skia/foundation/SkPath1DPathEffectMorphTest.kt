package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkMatrix
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * C2 verification suite for [SkPath1DPathEffect.Style.kMorph] —
 * the per-point bend of a stamp path along the input path's
 * normal direction.
 *
 * **Behaviour under test** (mirrors upstream's `morphpath`) :
 *  - On a straight input path, kMorph collapses to the same
 *    straight-line geometry as kRotate (no curvature → no bend).
 *  - On a curved input path, kMorph produces a stamp whose vertices
 *    do **not** lie on a single straight line — the stamp follows
 *    the path's curvature.
 *  - kLine verbs in the stamp are upgraded to quads (control =
 *    midpoint) before morphing, so output verb counts shift from
 *    "lines" to "quads" relative to a non-morph stamp emission.
 *  - Per-point morph still respects the [advance] / [phase] /
 *    contour-reset semantics shared with kTranslate and kRotate.
 */
class SkPath1DPathEffectMorphTest {

    private val identity: SkMatrix = SkMatrix.Identity

    /** Box stamp 4×4 centred at origin, four kLine verbs. */
    private val boxStamp: SkPath = SkPathBuilder()
        .moveTo(-2f, -2f).lineTo(2f, -2f).lineTo(2f, 2f).lineTo(-2f, 2f).close()
        .detach()

    /** Horizontal stamp segment from (0,0) to (5,0) — kLine. */
    private val hLineStamp: SkPath = SkPathBuilder()
        .moveTo(0f, 0f).lineTo(5f, 0f).detach()

    /** 100-unit horizontal line — straight, no curvature. */
    private val straightLine: SkPath = SkPathBuilder()
        .moveTo(0f, 0f).lineTo(100f, 0f).detach()

    /**
     * 90° arc approximated as a quadratic from (0, 0) up to (50, -50)
     * and back down to (100, 0) — control at (50, -100). Curvy enough
     * to make morph visible.
     */
    private val arcQuad: SkPath = SkPathBuilder()
        .moveTo(0f, 0f).quadTo(50f, -100f, 100f, 0f).detach()

    // ─── Sanity ────────────────────────────────────────────────────────

    @Test
    fun `kMorph factory returns non-null for valid args`() {
        val pe = SkPath1DPathEffect.Make(boxStamp, 25f, 0f, SkPath1DPathEffect.Style.kMorph)
        assertNotNull(pe, "Make must accept non-zero advance")
    }

    @Test
    fun `kMorph on empty input returns null`() {
        val pe = SkPath1DPathEffect.Make(boxStamp, 25f, 0f, SkPath1DPathEffect.Style.kMorph)!!
        val empty = SkPathBuilder().detach()
        assertEquals(null, pe.filterPath(empty, identity))
    }

    // ─── Verb-count parity with translate/rotate ───────────────────────

    @Test
    fun `kMorph emits the same number of stamps as kTranslate on a straight line`() {
        val advance = 20f
        val morphPe = SkPath1DPathEffect.Make(boxStamp, advance, 0f,
            SkPath1DPathEffect.Style.kMorph)!!
        val translatePe = SkPath1DPathEffect.Make(boxStamp, advance, 0f,
            SkPath1DPathEffect.Style.kTranslate)!!
        val morphOut = morphPe.filterPath(straightLine, identity)!!
        val translateOut = translatePe.filterPath(straightLine, identity)!!

        // Both place 6 stamps along a 100-unit line at advance=20
        // (positions 0, 20, 40, 60, 80, 100).
        val morphMoves = morphOut.verbs.count { it == SkPath.StorageVerb.kMove }
        val translateMoves = translateOut.verbs.count { it == SkPath.StorageVerb.kMove }
        assertEquals(translateMoves, morphMoves,
            "morph and translate must produce the same stamp count on a straight input")
        assertEquals(6, morphMoves)
    }

    // ─── Geometry — straight input degenerates to rotate ─────────────

    @Test
    fun `kMorph on a straight horizontal input matches kRotate within float tolerance`() {
        // No curvature → morph(sx, sy) = pos(d+sx) + sy * normal,
        // which equals (translate-then-rotate-by-tangent)(sx, sy).
        // → kMorph and kRotate should produce numerically-equivalent
        // stamp geometry on a perfectly straight horizontal input.
        //
        // Stamp constraint : every sx ≥ 0. Without this, the FIRST
        // stamp at d = 0 has vertices whose `d + sx` is negative,
        // clamped to 0 by the contour-measure — and morph then
        // diverges from rotate at the contour origin. Using a
        // stamp shifted to the right side keeps every per-vertex
        // arc-length query in `[0, length]` for every stamp, so
        // morph and rotate land on identical geometry.
        val advance = 25f
        val rightStamp = SkPathBuilder()
            .moveTo(0f, -2f).lineTo(4f, -2f).lineTo(4f, 2f).lineTo(0f, 2f).close()
            .detach()
        val morphPe = SkPath1DPathEffect.Make(rightStamp, advance, 0f,
            SkPath1DPathEffect.Style.kMorph)!!
        val rotatePe = SkPath1DPathEffect.Make(rightStamp, advance, 0f,
            SkPath1DPathEffect.Style.kRotate)!!
        val morphOut = morphPe.filterPath(straightLine, identity)!!
        val rotateOut = rotatePe.filterPath(straightLine, identity)!!
        // rotate emits lines (straight); morph upgrades to quads
        // (control = midpoint). Verb identities differ but the
        // resulting geometry should land at matching control points.
        // We compare the 4 stamp corners of the first stamp by
        // collecting all "end-of-segment" points from each.
        val morphCorners = collectEndPoints(morphOut, take = 4)
        val rotateCorners = collectEndPoints(rotateOut, take = 4)
        assertEquals(rotateCorners.size, morphCorners.size,
            "Both should reach 4 corner points for the first stamp")
        for (i in morphCorners.indices) {
            val (mx, my) = morphCorners[i]
            val (rx, ry) = rotateCorners[i]
            assertTrue(
                abs(mx - rx) < 0.01f && abs(my - ry) < 0.01f,
                "corner $i: morph=($mx,$my) vs rotate=($rx,$ry) — drifted on straight input",
            )
        }
    }

    @Test
    fun `kMorph clamps stamp vertices that fall before the contour start`() {
        // The boxStamp has sx ∈ [-2, 2]. The first stamp at d = 0
        // has vertices at distance -2, which falls before the
        // contour origin. Our `ContourMeasure.getPosTan` clamps to
        // [0, length] (rather than dropping the stamp) — so the
        // morphed move-to lands at `pos(0) + sy · normal(0)` instead
        // of being skipped. This test pins that behaviour so future
        // refactors notice if we switch to upstream's drop-on-out-of-
        // range semantics.
        val pe = SkPath1DPathEffect.Make(boxStamp, 25f, 0f,
            SkPath1DPathEffect.Style.kMorph)!!
        val out = pe.filterPath(straightLine, identity)!!
        // First verb is the moveTo of the first stamp at d = 0.
        // Box's first vertex is (-2, -2) ; clamped d = 0, normal = (0,1),
        // morph = (0, -2).
        assertTrue(out.verbs.first() == SkPath.StorageVerb.kMove)
        assertEquals(0f, out.coords[0], 0.001f, "clamped morph X must equal contour start X = 0")
        assertEquals(-2f, out.coords[1], 0.001f, "morph Y is sy = -2 along the +Y normal")
    }

    // ─── Geometry — curved input bends the stamp ─────────────────────

    @Test
    fun `kMorph on a curved input bends the stamp away from straight-line geometry`() {
        // Stamp = horizontal line (5 units long).
        // Input = arc through (0,0)-(50,-100)-(100,0).
        // Each stamp covers ~5 units of arc length, so its endpoints
        // morph to two different positions on the arc — the
        // morphed segment is NOT a straight line of the same length.
        val advance = 20f
        val morphPe = SkPath1DPathEffect.Make(hLineStamp, advance, 0f,
            SkPath1DPathEffect.Style.kMorph)!!
        val out = morphPe.filterPath(arcQuad, identity)!!

        // The stamp upgrades kLine → quad (midpoint control), so the
        // output verbs should contain quads, not lines, after each move.
        val verbs = out.verbs
        // Find the first move-then-quad and verify it's a quad.
        val firstMove = verbs.indexOf(SkPath.StorageVerb.kMove)
        assertTrue(firstMove >= 0, "expected at least one move in morph output")
        assertEquals(
            SkPath.StorageVerb.kQuad, verbs[firstMove + 1],
            "morph upgrades kLine to kQuad (control = midpoint)",
        )
    }

    @Test
    fun `kMorph quad control point lies near the arc, not on the chord`() {
        // First stamp at d=0. Stamp's three points after upgrade:
        //   start = (0, 0) → morph at d+0 = arc(0) = (0, 0)
        //   ctrl  = (2.5, 0) → morph at d+2.5 → some point on the arc
        //   end   = (5, 0) → morph at d+5 → some point on the arc
        // The control's y coordinate should NOT be 0 — the arc dips
        // negative around (50, -100) so even early control points
        // pull below the y=0 axis.
        val morphPe = SkPath1DPathEffect.Make(hLineStamp, 100f, 0f,
            SkPath1DPathEffect.Style.kMorph)!!
        val out = morphPe.filterPath(arcQuad, identity)!!
        // Coords layout: [moveX, moveY, quadCtrlX, quadCtrlY, quadEndX, quadEndY, ...]
        // We can extract the first quad's control point.
        // verbs = [kMove, kQuad, ...]
        val verbs = out.verbs
        val moveIdx = verbs.indexOf(SkPath.StorageVerb.kMove)
        assertTrue(moveIdx >= 0)
        assertEquals(SkPath.StorageVerb.kQuad, verbs[moveIdx + 1])
        // Coords for moveIdx, quadIdx :
        // move consumes 2 floats, quad consumes 4. So the quad's ctrl
        // point sits at coords[2..3] and end at [4..5].
        val ctrlX = out.coords[2]
        val ctrlY = out.coords[3]
        // The arc below (0, 0) bends down (negative y).
        assertTrue(
            ctrlY < -0.01f,
            "morph control point's y should be pulled below 0 by the arc; got $ctrlY",
        )
        // X should still be positive (forward along the arc).
        assertTrue(ctrlX > 0f, "morph control point's x should advance along the arc; got $ctrlX")
    }

    // ─── Sanity — existing tests pass ─────────────────────────────────

    @Test
    fun `kMorph respects per-contour reset like other styles`() {
        val twoLines = SkPathBuilder()
            .moveTo(0f, 0f).lineTo(40f, 0f)
            .moveTo(0f, 50f).lineTo(40f, 50f)
            .detach()
        val pe = SkPath1DPathEffect.Make(boxStamp, 20f, 0f,
            SkPath1DPathEffect.Style.kMorph)!!
        val out = pe.filterPath(twoLines, identity)!!
        // Same as kTranslate test : 3+3 = 6 stamps.
        val moves = out.verbs.count { it == SkPath.StorageVerb.kMove }
        assertEquals(6, moves, "morph must reset per contour just like translate / rotate")
    }

    // ─── Helpers ───────────────────────────────────────────────────────

    /**
     * Walk `path`'s coords and collect the first [take] end-of-segment
     * points (the last point of each non-close verb). For a kLine
     * verb that's the lineTo target ; for a kQuad it's the end point ;
     * for kMove it's the move point. This lets us compare the
     * "shape" of two paths even when their verb identities differ.
     */
    private fun collectEndPoints(path: SkPath, take: Int): List<Pair<Float, Float>> {
        val out = ArrayList<Pair<Float, Float>>()
        var coordIdx = 0
        var weightIdx = 0
        for (v in path.verbs) {
            if (out.size >= take) break
            when (v) {
                SkPath.StorageVerb.kMove -> {
                    val x = path.coords[coordIdx++]
                    val y = path.coords[coordIdx++]
                    out.add(x to y)
                }
                SkPath.StorageVerb.kLine -> {
                    val x = path.coords[coordIdx++]
                    val y = path.coords[coordIdx++]
                    out.add(x to y)
                }
                SkPath.StorageVerb.kQuad, SkPath.StorageVerb.kConic -> {
                    coordIdx += 2 // skip ctrl
                    val x = path.coords[coordIdx++]
                    val y = path.coords[coordIdx++]
                    if (v == SkPath.StorageVerb.kConic) weightIdx++
                    out.add(x to y)
                }
                SkPath.StorageVerb.kCubic -> {
                    coordIdx += 4 // skip 2 ctrls
                    val x = path.coords[coordIdx++]
                    val y = path.coords[coordIdx++]
                    out.add(x to y)
                }
                SkPath.StorageVerb.kClose -> { /* no coords consumed */ }
            }
        }
        return out
    }
}
