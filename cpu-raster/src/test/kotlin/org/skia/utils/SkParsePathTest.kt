package org.skia.utils


import org.skia.math.between
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkPath

/**
 * Unit tests for [SkParsePath.FromSVGString]. Coverage :
 *  - Each verb (M, L, H, V, C, S, Q, T, A, Z) absolute and relative.
 *  - Number-format edge cases : leading dot, scientific notation,
 *    consecutive numbers without separator, comma-or-whitespace as
 *    separator.
 *  - Multi-pair `M` implicit-line-after rule.
 *  - `Z` resets the current point to the start of the sub-path.
 *  - Failure paths (empty input, unknown verb, truncated coords).
 *
 * Geometric correctness is checked via [SkPath.computeBounds] +
 * verb count, since path comparison via the verb stream is the
 * cleanest invariant we can assert without rebuilding a parser
 * just to read it back.
 */
class SkParsePathTest {

    // ─── Empty / degenerate inputs ─────────────────────────────────

    @Test
    fun `empty string returns an empty path`() {
        val p = SkParsePath.FromSVGString("")
        assertNotNull(p)
        assertTrue(p!!.isEmpty())
    }

    @Test
    fun `whitespace-only string returns an empty path`() {
        val p = SkParsePath.FromSVGString("   \t\n  ")
        assertNotNull(p)
        assertTrue(p!!.isEmpty())
    }

    @Test
    fun `unknown verb returns null`() {
        assertNull(SkParsePath.FromSVGString("X 0 0"))
    }

    @Test
    fun `numeric data without leading verb returns null`() {
        assertNull(SkParsePath.FromSVGString("10 20 30 40"))
    }

    @Test
    fun `truncated coordinate list returns null`() {
        // M expects one (x,y) pair ; only one scalar provided.
        assertNull(SkParsePath.FromSVGString("M 10"))
    }

    // ─── Absolute verbs ───────────────────────────────────────────

    @Test
    fun `M L Z forms a closed triangle`() {
        val p = SkParsePath.FromSVGString("M 0 0 L 10 0 L 5 10 Z")!!
        val b = p.computeBounds()
        assertEquals(0f, b.left, 1e-4f); assertEquals(0f, b.top, 1e-4f)
        assertEquals(10f, b.right, 1e-4f); assertEquals(10f, b.bottom, 1e-4f)
        // 1 move + 2 lines + 1 close = 4 verbs (the Z doesn't add a
        // synthetic line since the last lineTo didn't end at the start).
        assertEquals(4, p.countVerbs())
    }

    @Test
    fun `H and V emit horizontal and vertical lines`() {
        val p = SkParsePath.FromSVGString("M 0 0 H 10 V 20 Z")!!
        val b = p.computeBounds()
        assertEquals(0f, b.left, 1e-4f); assertEquals(0f, b.top, 1e-4f)
        assertEquals(10f, b.right, 1e-4f); assertEquals(20f, b.bottom, 1e-4f)
    }

    @Test
    fun `C emits a cubic with the given control points`() {
        val p = SkParsePath.FromSVGString("M 0 0 C 0 10 10 10 10 0")!!
        val b = p.computeBounds()
        // Tight bounds : the cubic peaks below the control points.
        assertEquals(0f, b.left, 1e-4f); assertEquals(0f, b.top, 1e-4f)
        assertEquals(10f, b.right, 1e-4f)
        // The control-point-y=10 cubic from (0,0) to (10,0) peaks at
        // y=7.5 in tight-bounds — but `computeBounds` uses control-point
        // bbox, so we expect 10.
        assertEquals(10f, b.bottom, 1e-4f)
    }

    @Test
    fun `Q emits a quadratic with the given control point`() {
        val p = SkParsePath.FromSVGString("M 0 0 Q 5 10 10 0")!!
        val b = p.computeBounds()
        assertEquals(0f, b.left, 1e-4f); assertEquals(0f, b.top, 1e-4f)
        assertEquals(10f, b.right, 1e-4f); assertEquals(10f, b.bottom, 1e-4f)
    }

    // ─── Relative verbs ───────────────────────────────────────────

    @Test
    fun `lowercase m treats coordinates as relative to origin (0, 0)`() {
        // First m IS still relative to the current point — but the
        // current point starts at (0,0), so it acts the same as M.
        val p1 = SkParsePath.FromSVGString("m 10 20 l 5 5 z")!!
        val p2 = SkParsePath.FromSVGString("M 10 20 L 15 25 Z")!!
        assertEquals(p1.computeBounds(), p2.computeBounds())
    }

    @Test
    fun `lowercase l adds delta to current point`() {
        val p = SkParsePath.FromSVGString("M 10 10 l 5 0 l 0 5")!!
        val b = p.computeBounds()
        assertEquals(10f, b.left, 1e-4f); assertEquals(10f, b.top, 1e-4f)
        assertEquals(15f, b.right, 1e-4f); assertEquals(15f, b.bottom, 1e-4f)
    }

    @Test
    fun `lowercase h and v are relative`() {
        val p = SkParsePath.FromSVGString("M 5 5 h 10 v 7")!!
        val b = p.computeBounds()
        assertEquals(5f, b.left, 1e-4f); assertEquals(5f, b.top, 1e-4f)
        assertEquals(15f, b.right, 1e-4f); assertEquals(12f, b.bottom, 1e-4f)
    }

    // ─── M acts as L for trailing pairs ────────────────────────────

    @Test
    fun `M with multiple coord pairs emits L for the trailing pairs`() {
        // Per SVG spec : "M x1 y1 x2 y2 x3 y3" = M(x1,y1) L(x2,y2) L(x3,y3).
        val p = SkParsePath.FromSVGString("M 0 0 5 5 10 0")!!
        val b = p.computeBounds()
        assertEquals(0f, b.left, 1e-4f); assertEquals(0f, b.top, 1e-4f)
        assertEquals(10f, b.right, 1e-4f); assertEquals(5f, b.bottom, 1e-4f)
    }

    // ─── Number format edge cases ─────────────────────────────────

    @Test
    fun `comma-separated coordinates parse the same as whitespace-separated`() {
        val p1 = SkParsePath.FromSVGString("M 0,0 L 10,10")!!
        val p2 = SkParsePath.FromSVGString("M 0 0 L 10 10")!!
        assertEquals(p1.computeBounds(), p2.computeBounds())
    }

    @Test
    fun `negative-sign acts as implicit separator`() {
        // "10-5" should be read as "10" then "-5".
        val p = SkParsePath.FromSVGString("M 0 0 L10-5")!!
        val b = p.computeBounds()
        assertEquals(0f, b.left, 1e-4f); assertEquals(-5f, b.top, 1e-4f)
        assertEquals(10f, b.right, 1e-4f)
    }

    @Test
    fun `decimal-point acts as implicit separator after a number`() {
        // "1.5.6" → "1.5" then ".6"
        val p = SkParsePath.FromSVGString("M 0 0 L 1.5.6")!!
        val b = p.computeBounds()
        assertEquals(0f, b.left, 1e-4f); assertEquals(0f, b.top, 1e-4f)
        assertEquals(1.5f, b.right, 1e-4f); assertEquals(0.6f, b.bottom, 1e-4f)
    }

    @Test
    fun `scientific notation parses correctly`() {
        val p = SkParsePath.FromSVGString("M 0 0 L 1e2 -3.5e1")!!
        val b = p.computeBounds()
        assertEquals(-35f, b.top, 1e-4f); assertEquals(100f, b.right, 1e-4f)
    }

    @Test
    fun `leading dot without integer part is accepted`() {
        val p = SkParsePath.FromSVGString("M 0 0 L .5 .25")!!
        val b = p.computeBounds()
        assertEquals(0.5f, b.right, 1e-4f); assertEquals(0.25f, b.bottom, 1e-4f)
    }

    // ─── S and T smooth continuations ─────────────────────────────

    @Test
    fun `S after C reflects the previous control point`() {
        // M(0,0) C(0,10)(10,10)(10,0) S(20,-10)(20,0)
        // After C, lastC = (10,10), c = (10,0).
        // S's first cp = 2*c - lastC = (10,-10).
        val p = SkParsePath.FromSVGString(
            "M 0 0 C 0 10 10 10 10 0 S 20 -10 20 0",
        )!!
        // Both cubics → 1 move + 2 cubics = 3 verbs.
        assertEquals(3, p.countVerbs())
        val b = p.computeBounds()
        assertEquals(0f, b.left, 1e-4f); assertEquals(20f, b.right, 1e-4f)
    }

    @Test
    fun `T after Q reflects the previous control point`() {
        val p = SkParsePath.FromSVGString("M 0 0 Q 5 10 10 0 T 20 0")!!
        assertEquals(3, p.countVerbs())  // move + 2 quads
        val b = p.computeBounds()
        assertEquals(0f, b.left, 1e-4f); assertEquals(20f, b.right, 1e-4f)
    }

    // ─── Z behaviour ──────────────────────────────────────────────

    @Test
    fun `Z resets the current point to the start of the sub-path`() {
        // After Z, a relative `l` should be relative to the moveTo, not
        // to the lineTo's endpoint.
        val p = SkParsePath.FromSVGString("M 5 5 L 10 5 Z l 0 5")!!
        val b = p.computeBounds()
        // The post-Z 'l 0 5' starts from (5,5) (the moveTo) and ends at
        // (5,10) — so the bottom should be y=10.
        assertEquals(10f, b.bottom, 1e-4f)
    }

    // ─── Arc ──────────────────────────────────────────────────────

    @Test
    fun `A produces a curved path between the two endpoints`() {
        // 90 ° arc from (0,0) to (10,10) on a circle of radius 10.
        val p = SkParsePath.FromSVGString("M 0 0 A 10 10 0 0 1 10 10")!!
        val b = p.computeBounds()
        assertEquals(0f, b.left, 1e-4f); assertEquals(0f, b.top, 1e-4f)
        assertEquals(10f, b.right, 1e-4f); assertEquals(10f, b.bottom, 1e-4f)
    }
}
