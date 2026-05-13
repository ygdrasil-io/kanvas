package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkMatrix

/**
 * Unit tests for [SkPath1DPathEffect] + [SkPath2DPathEffect]
 * (Phase 7p_t).
 */
class SkPath1D2DPathEffectTest {

    private val identity = SkMatrix.Identity

    /** Tiny circle-ish square stamp (4×4 box centred at origin). */
    private val boxStamp: SkPath = SkPathBuilder()
        .moveTo(-2f, -2f).lineTo(2f, -2f).lineTo(2f, 2f).lineTo(-2f, 2f).close()
        .detach()

    /** A horizontal line input path (100 units long along +x). */
    private val horizontalLine: SkPath = SkPathBuilder()
        .moveTo(0f, 0f).lineTo(100f, 0f).detach()

    // -- SkPath1DPathEffect ---------------------------------------------------

    @Test
    fun `Sk1D Make rejects non-positive advance`() {
        assertNull(SkPath1DPathEffect.Make(boxStamp, 0f, 0f, SkPath1DPathEffect.Style.kTranslate))
        assertNull(SkPath1DPathEffect.Make(boxStamp, -1f, 0f, SkPath1DPathEffect.Style.kTranslate))
        assertNull(SkPath1DPathEffect.Make(boxStamp, Float.NaN, 0f, SkPath1DPathEffect.Style.kTranslate))
    }

    @Test
    fun `Sk1D Empty input returns null`() {
        val pe = SkPath1DPathEffect.Make(boxStamp, 10f, 0f, SkPath1DPathEffect.Style.kTranslate)!!
        assertNull(pe.filterPath(SkPathBuilder().detach(), identity))
    }

    @Test
    fun `Sk1D Empty stamp returns null`() {
        val pe = SkPath1DPathEffect.Make(SkPathBuilder().detach(), 10f, 0f,
            SkPath1DPathEffect.Style.kTranslate)!!
        assertNull(pe.filterPath(horizontalLine, identity))
    }

    @Test
    fun `Sk1D translate stamps at every advance unit along path`() {
        // 100-unit horizontal line, advance=20, phase=0 → stamps at
        // x = 0, 20, 40, 60, 80, 100 (6 stamps).
        val pe = SkPath1DPathEffect.Make(boxStamp, 20f, 0f,
            SkPath1DPathEffect.Style.kTranslate)!!
        val out = pe.filterPath(horizontalLine, identity)!!
        // Each stamp emits 1 moveTo + 3 lineTos + 1 close = 5 verbs.
        // 6 stamps → 30 verbs.
        val moveCount = out.verbs.count { it == SkPath.StorageVerb.kMove }
        assertEquals(6, moveCount, "expected 6 stamps for 100 units / 20 advance")
    }

    @Test
    fun `Sk1D rotate aligns each stamp with path direction`() {
        // Vertical input — stamps in kRotate should be rotated 90°.
        val verticalLine = SkPathBuilder().moveTo(0f, 0f).lineTo(0f, 100f).detach()
        val pe = SkPath1DPathEffect.Make(boxStamp, 20f, 0f,
            SkPath1DPathEffect.Style.kRotate)!!
        val out = pe.filterPath(verticalLine, identity)!!
        // 6 stamps × 4 lineTos+1moveTo = same verb count as horizontal,
        // but the coords should now be rotated 90° (x ↔ y swapped, with a sign flip).
        // Sanity : check that the first stamp's first vertex isn't at
        // the original (-2, -2) — it should be rotated.
        // Stamp 1 is at (0, 0) on the vertical line ; rotation by +90°
        // maps (-2, -2) → (2, -2).
        val firstX = out.coords[0]
        val firstY = out.coords[1]
        // Original stamp's first vertex is (-2, -2).
        assertTrue(kotlin.math.abs(firstX - 2f) < 0.1f && kotlin.math.abs(firstY - (-2f)) < 0.1f) {
            "rotated stamp first vertex expected (~2, -2), got ($firstX, $firstY)"
        }
    }

    @Test
    fun `Sk1D phase shifts the first stamp position`() {
        // 100-unit line, advance=20, phase=10 → stamps at x = 10, 30, 50, 70, 90 (5 stamps).
        val pe = SkPath1DPathEffect.Make(boxStamp, 20f, 10f,
            SkPath1DPathEffect.Style.kTranslate)!!
        val out = pe.filterPath(horizontalLine, identity)!!
        val moveCount = out.verbs.count { it == SkPath.StorageVerb.kMove }
        assertEquals(5, moveCount, "expected 5 stamps for phase=10 advance=20")
    }

    @Test
    fun `Sk1D per-contour reset starts each contour at phase`() {
        // Two disjoint horizontal lines, each 40 units, advance=20.
        // Each contour stamps independently → 3+3 = 6 stamps total.
        val twoLines = SkPathBuilder()
            .moveTo(0f, 0f).lineTo(40f, 0f)
            .moveTo(0f, 50f).lineTo(40f, 50f)
            .detach()
        val pe = SkPath1DPathEffect.Make(boxStamp, 20f, 0f,
            SkPath1DPathEffect.Style.kTranslate)!!
        val out = pe.filterPath(twoLines, identity)!!
        // Stamps at (0, 0), (20, 0), (40, 0), (0, 50), (20, 50), (40, 50) = 6.
        // Each stamp = 1 moveTo + 3 lineTo + 1 close.
        val moveCount = out.verbs.count { it == SkPath.StorageVerb.kMove }
        assertEquals(6, moveCount)
    }

    // -- SkPath2DPathEffect ---------------------------------------------------

    @Test
    fun `Sk2D Make rejects non-invertible matrix`() {
        // A scale-by-zero matrix is non-invertible.
        val degenerate = SkMatrix.MakeAll(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f)
        assertNull(SkPath2DPathEffect.Make(degenerate, boxStamp))
    }

    @Test
    fun `Sk2D Make accepts valid scale matrix`() {
        val scale = SkMatrix.MakeScale(10f, 10f)
        assertNotNull(SkPath2DPathEffect.Make(scale, boxStamp))
    }

    @Test
    fun `Sk2D Empty input returns null`() {
        val pe = SkPath2DPathEffect.Make(SkMatrix.MakeScale(10f, 10f), boxStamp)!!
        assertNull(pe.filterPath(SkPathBuilder().detach(), identity))
    }

    @Test
    fun `Sk2D Empty stamp returns null`() {
        val pe = SkPath2DPathEffect.Make(SkMatrix.MakeScale(10f, 10f),
            SkPathBuilder().detach())!!
        assertNull(pe.filterPath(horizontalLine, identity))
    }

    @Test
    fun `Sk2D tiles a stamp across the input bounding box`() {
        // Input : 50×50 rectangle. Tile basis : 20×20 grid.
        // The bounding box [0..50] × [0..50] in stamp coords (using
        // invMatrix = scale(0.05)) becomes [0..2.5] × [0..2.5] →
        // floor=0, ceil=3 → tile range i,j ∈ [0..3] = 4×4 = 16 stamps.
        val rect = SkPathBuilder()
            .moveTo(0f, 0f).lineTo(50f, 0f).lineTo(50f, 50f).lineTo(0f, 50f).close()
            .detach()
        val pe = SkPath2DPathEffect.Make(SkMatrix.MakeScale(20f, 20f), boxStamp)!!
        val out = pe.filterPath(rect, identity)!!
        val stampCount = out.verbs.count { it == SkPath.StorageVerb.kMove }
        // 16 stamps expected (4 columns × 4 rows).
        assertEquals(16, stampCount,
            "expected 16 stamps for 50×50 bbox / 20-unit grid")
    }

    @Test
    fun `Sk2D large grid spacing tiles fewer stamps`() {
        // Same 50×50 input, larger grid 50×50 → tile range [0..1] = 2×2 = 4.
        val rect = SkPathBuilder()
            .moveTo(0f, 0f).lineTo(50f, 0f).lineTo(50f, 50f).lineTo(0f, 50f).close()
            .detach()
        val pe = SkPath2DPathEffect.Make(SkMatrix.MakeScale(50f, 50f), boxStamp)!!
        val out = pe.filterPath(rect, identity)!!
        val stampCount = out.verbs.count { it == SkPath.StorageVerb.kMove }
        // [0/50..50/50] = [0..1] → ceil/floor → range [0..1] = 2×2.
        assertEquals(4, stampCount)
    }
}
