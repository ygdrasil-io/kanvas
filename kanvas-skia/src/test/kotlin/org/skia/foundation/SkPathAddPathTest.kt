package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect

/**
 * Slice 3.4 — `SkPathBuilder.addPath` with matrix + `AddPathMode`.
 *
 * Mirrors `SkPathBuilder::addPath(src, dx, dy, mode)` and
 * `addPath(src, matrix, mode)` (`include/core/SkPathBuilder.h:831-861`,
 * `src/core/SkPathBuilder.cpp:776-800`).
 */
class SkPathAddPathTest {

    private fun simpleTriangle(): SkPath = SkPathBuilder()
        .moveTo(0f, 0f)
        .lineTo(10f, 0f)
        .lineTo(10f, 10f)
        .close()
        .detach()

    private fun pathWithConic(): SkPath = SkPathBuilder()
        .moveTo(0f, 0f)
        .conicTo(10f, 0f, 10f, 10f, 0.7f)
        .detach()

    // --- kAppend (default) ---------------------------------------------

    @Test
    fun `default addPath copies verbs verbatim`() {
        val src = simpleTriangle()
        val copy = SkPathBuilder().addPath(src).detach()
        assertArrayEquals(src.verbs, copy.verbs)
        assertArrayEquals(src.coords, copy.coords)
    }

    @Test
    fun `addPath preserves conic weights identity-mapped`() {
        val src = pathWithConic()
        val copy = SkPathBuilder().addPath(src, SkMatrix.Identity).detach()
        assertArrayEquals(src.conicWeights, copy.conicWeights, 0f)
    }

    @Test
    fun `kAppend after a closed contour starts a fresh contour`() {
        val src = simpleTriangle()
        val p = SkPathBuilder()
            .moveTo(50f, 50f).lineTo(60f, 50f).close()
            .addPath(src, SkMatrix.Identity, SkPath.AddPathMode.kAppend)
            .detach()
        // After dest's close, source's leading kMove should remain.
        // Verb stream prefix: kMove, kLine, kClose, then src verbs in order.
        assertEquals(SkPath.Verb.kMove, p.verbs[0])
        assertEquals(SkPath.Verb.kClose, p.verbs[2])
        assertEquals(SkPath.Verb.kMove, p.verbs[3])
    }

    // --- kAppend with offset / matrix ----------------------------------

    @Test
    fun `addPath with dx,dy translates every coord`() {
        val src = simpleTriangle()
        val copy = SkPathBuilder().addPath(src, dx = 5f, dy = 7f).detach()
        for (i in src.coords.indices step 2) {
            assertEquals(src.coords[i] + 5f, copy.coords[i], 1e-4f)
            assertEquals(src.coords[i + 1] + 7f, copy.coords[i + 1], 1e-4f)
        }
    }

    @Test
    fun `addPath with scale matrix stretches every coord`() {
        val src = simpleTriangle()
        val copy = SkPathBuilder().addPath(src, SkMatrix.MakeScale(2f, 3f)).detach()
        for (i in src.coords.indices step 2) {
            assertEquals(src.coords[i] * 2f, copy.coords[i], 1e-4f)
            assertEquals(src.coords[i + 1] * 3f, copy.coords[i + 1], 1e-4f)
        }
    }

    @Test
    fun `addPath with affine matrix preserves conic weights`() {
        val src = pathWithConic()
        val copy = SkPathBuilder().addPath(src, SkMatrix.MakeScale(2f, 3f)).detach()
        // Affine maps preserve projective conic-weight identity.
        assertArrayEquals(src.conicWeights, copy.conicWeights, 0f)
    }

    // --- kExtend on empty destination ----------------------------------

    @Test
    fun `kExtend on empty destination behaves like kAppend`() {
        val src = simpleTriangle()
        val extended = SkPathBuilder().addPath(src, SkMatrix.Identity, SkPath.AddPathMode.kExtend).detach()
        val appended = SkPathBuilder().addPath(src).detach()
        assertArrayEquals(appended.verbs, extended.verbs)
        assertArrayEquals(appended.coords, extended.coords)
    }

    // --- kExtend with non-empty destination ----------------------------

    @Test
    fun `kExtend replaces source's first kMove with a lineTo`() {
        // Dest: open polyline ending at (10, 10).
        // Src:  triangle starting at (100, 100).
        // After kExtend, the source's leading kMove(100, 100) should become
        // lineTo(100, 100) joining the dest's last point to the source.
        val dest = SkPathBuilder().moveTo(0f, 0f).lineTo(10f, 10f)
        val src = SkPathBuilder()
            .moveTo(100f, 100f).lineTo(110f, 100f).lineTo(110f, 110f).close()
            .detach()
        val p = dest.addPath(src, SkMatrix.Identity, SkPath.AddPathMode.kExtend).detach()
        // Expected: kMove(0,0), kLine(10,10), kLine(100,100), kLine(110,100), kLine(110,110), kClose.
        assertArrayEquals(
            arrayOf(
                SkPath.Verb.kMove,
                SkPath.Verb.kLine,  // dest's lineTo(10, 10)
                SkPath.Verb.kLine,  // bridge to src's first move target
                SkPath.Verb.kLine,
                SkPath.Verb.kLine,
                SkPath.Verb.kClose,
            ),
            p.verbs,
        )
        // Bridge lineTo lands on (100, 100).
        assertEquals(100f, p.coords[4], 1e-4f)
        assertEquals(100f, p.coords[5], 1e-4f)
    }

    @Test
    fun `kExtend with translate maps the bridge target through the matrix`() {
        val dest = SkPathBuilder().moveTo(0f, 0f).lineTo(10f, 10f)
        val src = SkPathBuilder().moveTo(100f, 100f).lineTo(110f, 110f).detach()
        val p = dest.addPath(src, dx = 1f, dy = 2f, mode = SkPath.AddPathMode.kExtend).detach()
        // Bridge lands on the *mapped* move target (101, 102).
        assertEquals(101f, p.coords[4], 1e-4f)
        assertEquals(102f, p.coords[5], 1e-4f)
        // Subsequent line to mapped (111, 112).
        assertEquals(111f, p.coords[6], 1e-4f)
        assertEquals(112f, p.coords[7], 1e-4f)
    }

    @Test
    fun `kExtend after dest close re-anchors via Phase 1_2 ensureContour`() {
        // Dest closes at (10, 10) → contour started at (0, 0). After close,
        // ensureContour (called by the bridge lineTo in kExtend) emits an
        // implicit moveTo back to (0, 0) before the bridge line.
        val dest = SkPathBuilder().moveTo(0f, 0f).lineTo(10f, 10f).close()
        val src = SkPathBuilder().moveTo(50f, 50f).lineTo(60f, 60f).detach()
        val p = dest.addPath(src, SkMatrix.Identity, SkPath.AddPathMode.kExtend).detach()
        // Verb stream prefix: kMove(0,0), kLine(10,10), kClose,
        // then implicit kMove(0,0) (Phase 1.2), kLine(50,50) (bridge), kLine(60,60).
        assertArrayEquals(
            arrayOf(
                SkPath.Verb.kMove, SkPath.Verb.kLine, SkPath.Verb.kClose,
                SkPath.Verb.kMove,    // ensureContour after close
                SkPath.Verb.kLine,    // bridge to src's first move target
                SkPath.Verb.kLine,    // src's lineTo
            ),
            p.verbs,
        )
        // Implicit moveTo lands on (0, 0).
        assertEquals(0f, p.coords[4], 1e-4f)
        assertEquals(0f, p.coords[5], 1e-4f)
        // Bridge line lands on (50, 50).
        assertEquals(50f, p.coords[6], 1e-4f)
        assertEquals(50f, p.coords[7], 1e-4f)
    }

    @Test
    fun `kExtend only replaces the source's *first* kMove`() {
        // Source has two contours; only the first move should be replaced.
        val dest = SkPathBuilder().moveTo(0f, 0f).lineTo(10f, 10f)
        val src = SkPathBuilder()
            .moveTo(50f, 50f).lineTo(60f, 60f)
            .moveTo(80f, 80f).lineTo(90f, 90f)
            .detach()
        val p = dest.addPath(src, SkMatrix.Identity, SkPath.AddPathMode.kExtend).detach()
        assertArrayEquals(
            arrayOf(
                SkPath.Verb.kMove,    // dest
                SkPath.Verb.kLine,    // dest
                SkPath.Verb.kLine,    // bridge (replaces 1st src move)
                SkPath.Verb.kLine,    // src 1st contour line
                SkPath.Verb.kMove,    // src 2nd contour move (kept!)
                SkPath.Verb.kLine,    // src 2nd contour line
            ),
            p.verbs,
        )
    }

    // --- empty source --------------------------------------------------

    @Test
    fun `addPath with empty source is a no-op (kAppend)`() {
        val empty = SkPathBuilder().detach()
        val dest = SkPathBuilder().moveTo(0f, 0f).lineTo(1f, 1f).addPath(empty).detach()
        assertArrayEquals(arrayOf(SkPath.Verb.kMove, SkPath.Verb.kLine), dest.verbs)
    }

    @Test
    fun `addPath with empty source is a no-op (kExtend)`() {
        val empty = SkPathBuilder().detach()
        val dest = SkPathBuilder()
            .moveTo(0f, 0f).lineTo(1f, 1f)
            .addPath(empty, SkMatrix.Identity, SkPath.AddPathMode.kExtend)
            .detach()
        assertArrayEquals(arrayOf(SkPath.Verb.kMove, SkPath.Verb.kLine), dest.verbs)
    }

    // --- AddPathMode enum identity --------------------------------------

    @Test
    fun `AddPathMode enum has exactly two values matching Skia`() {
        // Sanity-check: parity with SkPath::AddPathMode (kAppend, kExtend).
        assertEquals(2, SkPath.AddPathMode.entries.size)
        assertTrue(SkPath.AddPathMode.entries.contains(SkPath.AddPathMode.kAppend))
        assertTrue(SkPath.AddPathMode.entries.contains(SkPath.AddPathMode.kExtend))
    }

    // --- invariance under fillType --------------------------------------

    @Test
    fun `addPath does not change the destination builder's fillType`() {
        val src = SkPathBuilder().setFillType(SkPathFillType.kEvenOdd).addRect(SkRect.MakeLTRB(0f, 0f, 5f, 5f)).detach()
        val p = SkPathBuilder()
            .setFillType(SkPathFillType.kInverseWinding)
            .addPath(src)
            .detach()
        assertEquals(SkPathFillType.kInverseWinding, p.fillType)
    }
}
