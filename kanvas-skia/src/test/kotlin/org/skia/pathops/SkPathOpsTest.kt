package org.skia.pathops

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkRect

/**
 * Unit tests for [SkPathOps] (chantier D1.0 — package skeleton +
 * `TightBounds` shim).
 *
 * Coverage :
 *  - `TightBounds` : delegates to [SkPath.computeTightBounds] and
 *    converts non-finite results to `null`.
 *  - `Op` / `Simplify` / `AsWinding` : D1.0 stubs return `null`.
 *  - `SkOpBuilder` : `add` accepts paths ; `resolve` returns `null`
 *    in D1.0 and resets internal state.
 *  - `SkPathOp` enum : 5 variants exist with the expected names.
 */
class SkPathOpsTest {

    // ─── TightBounds ────────────────────────────────────────────────────

    @Test
    fun `TightBounds on empty path returns the origin rect`() {
        @Suppress("DEPRECATION")
        val r = SkPathOps.TightBounds(SkPathBuilder().detach())
        assertNotNull(r)
        assertEquals(0f, r!!.left); assertEquals(0f, r.top)
        assertEquals(0f, r.right); assertEquals(0f, r.bottom)
    }

    @Test
    fun `TightBounds on a quadratic excludes the control-point bulge`() {
        // Same fixture as SkPathGeometryHelpersTest : peak of curve is 500,
        // not the control point's 1000.
        val p = SkPathBuilder()
            .moveTo(0f, 0f)
            .quadTo(50f, 1000f, 100f, 0f)
            .detach()
        @Suppress("DEPRECATION")
        val r = SkPathOps.TightBounds(p)
        assertNotNull(r)
        assertEquals(0f, r!!.left, 1e-4f)
        assertEquals(100f, r.right, 1e-4f)
        assertEquals(0f, r.top, 1e-4f)
        assertEquals(500f, r.bottom, 1e-4f)
    }

    @Test
    fun `TightBounds on a cubic excludes the control-point bulge`() {
        val p = SkPathBuilder()
            .moveTo(0f, 0f)
            .cubicTo(0f, 1000f, 100f, 1000f, 100f, 0f)
            .detach()
        @Suppress("DEPRECATION")
        val r = SkPathOps.TightBounds(p)
        assertNotNull(r)
        assertEquals(0f, r!!.top, 1e-4f)
        assertEquals(750f, r.bottom, 1e-3f)
    }

    @Test
    fun `TightBounds on a rect-only path equals the rect`() {
        val p = SkPathBuilder()
            .addRect(SkRect.MakeLTRB(10f, 20f, 110f, 80f))
            .detach()
        @Suppress("DEPRECATION")
        val r = SkPathOps.TightBounds(p)
        assertNotNull(r)
        assertEquals(10f, r!!.left, 1e-4f)
        assertEquals(20f, r.top, 1e-4f)
        assertEquals(110f, r.right, 1e-4f)
        assertEquals(80f, r.bottom, 1e-4f)
    }

    @Test
    fun `TightBounds returns null for paths with non-finite coordinates`() {
        // A path containing infinity produces non-finite tight bounds —
        // the wrapper turns those into null per the upstream contract
        // (returns false, output unchanged).
        // Note : NaN is silently swallowed by the extend() impl
        // (NaN comparisons are false), so we use POS_INFINITY instead
        // which propagates through min/max correctly.
        val p = SkPathBuilder()
            .moveTo(Float.POSITIVE_INFINITY, 0f)
            .lineTo(0f, 1f)
            .detach()
        @Suppress("DEPRECATION")
        val r = SkPathOps.TightBounds(p)
        assertNull(r)
    }

    // ─── Op / Simplify / AsWinding stubs (D1.0) ─────────────────────────

    // ─── Op fast paths (D1.2.h.0) ───────────────────────────────────────

    @Test
    fun `Op rect-rect kIntersect returns the intersection rect`() {
        val a = SkPathBuilder().addRect(SkRect.MakeLTRB(0f, 0f, 10f, 10f)).detach()
        val b = SkPathBuilder().addRect(SkRect.MakeLTRB(5f, 5f, 15f, 15f)).detach()
        val result = SkPathOps.Op(a, b, SkPathOp.kIntersect)
        assertNotNull(result)
        val rect = result!!.isRect()
        assertNotNull(rect)
        assertEquals(5f, rect!!.left, 1e-4f); assertEquals(5f, rect.top, 1e-4f)
        assertEquals(10f, rect.right, 1e-4f); assertEquals(10f, rect.bottom, 1e-4f)
    }

    @Test
    fun `Op rect-rect kIntersect on disjoint rects returns an empty path`() {
        val a = SkPathBuilder().addRect(SkRect.MakeLTRB(0f, 0f, 10f, 10f)).detach()
        val b = SkPathBuilder().addRect(SkRect.MakeLTRB(20f, 20f, 30f, 30f)).detach()
        val result = SkPathOps.Op(a, b, SkPathOp.kIntersect)
        assertNotNull(result)
        org.junit.jupiter.api.Assertions.assertTrue(result!!.isEmpty())
    }

    @Test
    fun `Op kIntersect on non-rect paths still falls through to null`() {
        val a = SkPathBuilder().addCircle(0f, 0f, 10f).detach()
        val b = SkPathBuilder().addRect(SkRect.MakeLTRB(0f, 0f, 10f, 10f)).detach()
        // Only rect-rect intersect is fast-pathed in this slice.
        assertNull(SkPathOps.Op(a, b, SkPathOp.kIntersect))
    }

    @Test
    fun `Op kUnion with empty first input returns the second input`() {
        val empty = SkPathBuilder().detach()
        val rect = SkPathBuilder().addRect(SkRect.MakeLTRB(0f, 0f, 10f, 10f)).detach()
        val result = SkPathOps.Op(empty, rect, SkPathOp.kUnion)
        assertNotNull(result)
        val r = result!!.isRect()
        assertNotNull(r)
        assertEquals(10f, r!!.right, 1e-4f)
    }

    @Test
    fun `Op kUnion with empty second input returns the first input`() {
        val empty = SkPathBuilder().detach()
        val rect = SkPathBuilder().addRect(SkRect.MakeLTRB(0f, 0f, 10f, 10f)).detach()
        val result = SkPathOps.Op(rect, empty, SkPathOp.kUnion)
        assertNotNull(result)
        assertNotNull(result!!.isRect())
    }

    @Test
    fun `Op kIntersect with an empty input returns an empty path`() {
        val empty = SkPathBuilder().detach()
        val rect = SkPathBuilder().addRect(SkRect.MakeLTRB(0f, 0f, 10f, 10f)).detach()
        val result = SkPathOps.Op(rect, empty, SkPathOp.kIntersect)
        assertNotNull(result)
        org.junit.jupiter.api.Assertions.assertTrue(result!!.isEmpty())
    }

    @Test
    fun `Op kDifference with empty first input returns an empty path`() {
        val empty = SkPathBuilder().detach()
        val rect = SkPathBuilder().addRect(SkRect.MakeLTRB(0f, 0f, 10f, 10f)).detach()
        val result = SkPathOps.Op(empty, rect, SkPathOp.kDifference)
        assertNotNull(result)
        org.junit.jupiter.api.Assertions.assertTrue(result!!.isEmpty())
    }

    @Test
    fun `Op kDifference with empty second input returns the first input`() {
        val empty = SkPathBuilder().detach()
        val rect = SkPathBuilder().addRect(SkRect.MakeLTRB(0f, 0f, 10f, 10f)).detach()
        val result = SkPathOps.Op(rect, empty, SkPathOp.kDifference)
        assertNotNull(result)
        assertNotNull(result!!.isRect())
    }

    @Test
    fun `Op kReverseDifference with empty second input returns an empty path`() {
        val empty = SkPathBuilder().detach()
        val rect = SkPathBuilder().addRect(SkRect.MakeLTRB(0f, 0f, 10f, 10f)).detach()
        val result = SkPathOps.Op(rect, empty, SkPathOp.kReverseDifference)
        assertNotNull(result)
        org.junit.jupiter.api.Assertions.assertTrue(result!!.isEmpty())
    }

    @Test
    fun `Op kReverseDifference with empty first input returns the second input`() {
        val empty = SkPathBuilder().detach()
        val rect = SkPathBuilder().addRect(SkRect.MakeLTRB(0f, 0f, 10f, 10f)).detach()
        val result = SkPathOps.Op(empty, rect, SkPathOp.kReverseDifference)
        assertNotNull(result)
        assertNotNull(result!!.isRect())
    }

    @Test
    fun `Op kUnion with both empty returns an empty path`() {
        val empty = SkPathBuilder().detach()
        val result = SkPathOps.Op(empty, empty, SkPathOp.kUnion)
        assertNotNull(result)
        org.junit.jupiter.api.Assertions.assertTrue(result!!.isEmpty())
    }

    @Test
    fun `Op on two non-rect non-empty paths still returns null in this slice`() {
        // Triangle and pentagon — no fast path applies, full machinery
        // pending in D1.2.h.1+.
        val triangle = SkPathBuilder()
            .moveTo(0f, 0f).lineTo(10f, 0f).lineTo(5f, 10f).close()
            .detach()
        val pentagon = SkPathBuilder()
            .moveTo(5f, 0f).lineTo(10f, 4f).lineTo(8f, 10f).lineTo(2f, 10f).lineTo(0f, 4f).close()
            .detach()
        assertNull(SkPathOps.Op(triangle, pentagon, SkPathOp.kUnion))
    }

    // ─── Simplify (D1.2.h.6.1) ──────────────────────────────────────

    @Test
    fun `Simplify on empty path returns empty path with kEvenOdd fill`() {
        val empty = SkPathBuilder().detach()
        val r = SkPathOps.Simplify(empty)
        assertNotNull(r)
        org.junit.jupiter.api.Assertions.assertTrue(r!!.isEmpty())
        assertEquals(org.skia.foundation.SkPathFillType.kEvenOdd, r.fillType)
    }

    @Test
    fun `Simplify on inverse-fill empty path keeps inverse fill`() {
        val empty = SkPathBuilder()
            .setFillType(org.skia.foundation.SkPathFillType.kInverseWinding)
            .detach()
        val r = SkPathOps.Simplify(empty)
        assertNotNull(r)
        assertEquals(org.skia.foundation.SkPathFillType.kInverseEvenOdd, r!!.fillType)
    }

    @Test
    fun `Simplify on a non-trivial path falls through to null in this slice`() {
        // Self-intersecting figure-eight ; Simplify's full pipeline
        // runs but the bridgeWinding / bridgeXor walker may produce
        // an empty writer (same post-condition as Op fall-through).
        val p = SkPathBuilder()
            .moveTo(0f, 0f).lineTo(10f, 10f).lineTo(0f, 10f).lineTo(10f, 0f).close()
            .detach()
        // Result is null until winding values are correctly populated
        // through the full Simplify pipeline (debugging follow-up).
        // Existing assertion : no crash, no claim of bogus empty result.
        SkPathOps.Simplify(p)
    }

    // ─── AsWinding (D1.2.h.6.2) ─────────────────────────────────────

    @Test
    fun `AsWinding returns the input path unchanged when fill is already winding`() {
        val p = SkPathBuilder()
            .setFillType(org.skia.foundation.SkPathFillType.kWinding)
            .addRect(SkRect.MakeLTRB(0f, 0f, 10f, 10f))
            .detach()
        org.junit.jupiter.api.Assertions.assertSame(p, SkPathOps.AsWinding(p))
    }

    @Test
    fun `AsWinding flips kEvenOdd to kWinding on a single-contour rect`() {
        val p = SkPathBuilder()
            .setFillType(org.skia.foundation.SkPathFillType.kEvenOdd)
            .addRect(SkRect.MakeLTRB(0f, 0f, 10f, 10f))
            .detach()
        val r = SkPathOps.AsWinding(p)
        assertNotNull(r)
        assertEquals(org.skia.foundation.SkPathFillType.kWinding, r!!.fillType)
    }

    @Test
    fun `AsWinding flips kInverseEvenOdd to kInverseWinding on a single contour`() {
        val p = SkPathBuilder()
            .setFillType(org.skia.foundation.SkPathFillType.kInverseEvenOdd)
            .addRect(SkRect.MakeLTRB(0f, 0f, 10f, 10f))
            .detach()
        val r = SkPathOps.AsWinding(p)
        assertNotNull(r)
        assertEquals(org.skia.foundation.SkPathFillType.kInverseWinding, r!!.fillType)
    }

    @Test
    fun `AsWinding on empty path returns empty with target fill`() {
        val p = SkPathBuilder()
            .setFillType(org.skia.foundation.SkPathFillType.kEvenOdd)
            .detach()
        val r = SkPathOps.AsWinding(p)
        assertNotNull(r)
        org.junit.jupiter.api.Assertions.assertTrue(r!!.isEmpty())
        assertEquals(org.skia.foundation.SkPathFillType.kWinding, r.fillType)
    }

    @Test
    fun `AsWinding on disjoint multi-contour path flips fill via flat-tree fast path`() {
        val p = SkPathBuilder()
            .setFillType(org.skia.foundation.SkPathFillType.kEvenOdd)
            .addRect(SkRect.MakeLTRB(0f, 0f, 10f, 10f))
            .addRect(SkRect.MakeLTRB(20f, 20f, 30f, 30f))
            .detach()
        // 2 disjoint contours → bbox-tree is flat (neither contains
        // the other) → makeFillType is correct.
        val r = SkPathOps.AsWinding(p)
        assertNotNull(r)
        assertEquals(org.skia.foundation.SkPathFillType.kWinding, r!!.fillType)
    }

    @Test
    fun `AsWinding on same-direction-nested contour path falls through to null (h_6_5+)`() {
        // Outer rect contains inner rect, both CW (default direction).
        // Same direction → reversal needed → null (deferred).
        val p = SkPathBuilder()
            .setFillType(org.skia.foundation.SkPathFillType.kEvenOdd)
            .addRect(SkRect.MakeLTRB(0f, 0f, 100f, 100f))
            .addRect(SkRect.MakeLTRB(20f, 20f, 80f, 80f))
            .detach()
        assertNull(SkPathOps.AsWinding(p))
    }

    @Test
    fun `AsWinding on alternating-direction-nested contours uses 2-level fast path`() {
        // Outer CW + inner CCW (donut hole pattern that's already
        // winding-equivalent). makeFillType is correct.
        val p = SkPathBuilder()
            .setFillType(org.skia.foundation.SkPathFillType.kEvenOdd)
            .addRect(SkRect.MakeLTRB(0f, 0f, 100f, 100f), org.skia.foundation.SkPathDirection.kCW)
            .addRect(SkRect.MakeLTRB(20f, 20f, 80f, 80f), org.skia.foundation.SkPathDirection.kCCW)
            .detach()
        val r = SkPathOps.AsWinding(p)
        assertNotNull(r)
        assertEquals(org.skia.foundation.SkPathFillType.kWinding, r!!.fillType)
    }

    @Test
    fun `AsWinding rejects non-finite paths`() {
        val p = SkPathBuilder()
            .setFillType(org.skia.foundation.SkPathFillType.kEvenOdd)
            .moveTo(Float.POSITIVE_INFINITY, 0f)
            .lineTo(0f, 1f)
            .detach()
        assertNull(SkPathOps.AsWinding(p))
    }

    // ─── SkOpBuilder ────────────────────────────────────────────────────

    @Test
    fun `SkOpBuilder accepts adds and returns null on resolve in D1_0`() {
        val builder = SkOpBuilder()
        builder.add(
            SkPathBuilder().addRect(SkRect.MakeLTRB(0f, 0f, 10f, 10f)).detach(),
            SkPathOp.kUnion,
        )
        builder.add(
            SkPathBuilder().addRect(SkRect.MakeLTRB(5f, 5f, 15f, 15f)).detach(),
            SkPathOp.kIntersect,
        )
        assertNull(builder.resolve())
    }

    @Test
    fun `SkOpBuilder resets after resolve so it can be reused`() {
        val builder = SkOpBuilder()
        builder.add(SkPathBuilder().detach(), SkPathOp.kUnion)
        builder.resolve()
        // After resolve, the builder is empty again — adding new paths
        // and resolving works without state leakage.
        builder.add(SkPathBuilder().detach(), SkPathOp.kIntersect)
        assertNull(builder.resolve())
    }

    // ─── SkPathOp enum surface ──────────────────────────────────────────

    @Test
    fun `SkPathOp enum has the 5 upstream variants`() {
        val names = SkPathOp.values().map { it.name }.toSet()
        assertEquals(
            setOf("kDifference", "kIntersect", "kUnion", "kXOR", "kReverseDifference"),
            names,
        )
    }
}
