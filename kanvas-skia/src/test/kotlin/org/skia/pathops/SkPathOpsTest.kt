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

    @Test
    fun `Op returns null in D1_0 (not yet implemented)`() {
        val a = SkPathBuilder().addRect(SkRect.MakeLTRB(0f, 0f, 10f, 10f)).detach()
        val b = SkPathBuilder().addRect(SkRect.MakeLTRB(5f, 5f, 15f, 15f)).detach()
        for (op in SkPathOp.values()) {
            assertNull(SkPathOps.Op(a, b, op), "Op($op) should return null until D1.3")
        }
    }

    @Test
    fun `Simplify returns null in D1_0 (not yet implemented)`() {
        val p = SkPathBuilder().addRect(SkRect.MakeLTRB(0f, 0f, 10f, 10f)).detach()
        assertNull(SkPathOps.Simplify(p))
    }

    @Test
    fun `AsWinding returns null in D1_0 (not yet implemented)`() {
        val p = SkPathBuilder().addRect(SkRect.MakeLTRB(0f, 0f, 10f, 10f)).detach()
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
