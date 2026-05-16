package org.skia.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * R-suivi.15 — verifies the immutable-adaptation contract of
 * [Sk3DView.getMatrix] / [SkCamera3D.patchToMatrix] :
 *
 *  1. The returned [org.graphiks.math.SkMatrix] is independent of subsequent
 *     mutations of the view (no shared reference / aliasing).
 *  2. The parameter-less convenience method [Sk3DView.getMatrixCopy]
 *     behaves identically to [Sk3DView.getMatrix].
 *  3. Repeated calls return value-equal matrices when the view state
 *     hasn't changed.
 *  4. The projection picks up changes only after the corresponding
 *     mutator runs — never before.
 */
class Sk3DViewGetMatrixImmutableDocTest {

    @Test
    fun `getMatrix returns a value independent of later view mutations`() {
        val view = Sk3DView()
        view.rotateZ(45f)
        val snapshot = view.getMatrix()
        // Mutate the view ; the snapshot must NOT shift.
        view.rotateZ(45f)
        view.translate(100f, 50f, 0f)

        // Compare snapshot's sx / kx to a fresh `getMatrix()` from a
        // new view that's only been rotated by 45° — the snapshot must
        // still match this baseline.
        val baseline = Sk3DView().apply { rotateZ(45f) }.getMatrix()
        assertEquals(baseline.sx, snapshot.sx, 1e-5f)
        assertEquals(baseline.kx, snapshot.kx, 1e-5f)
        assertEquals(baseline.ky, snapshot.ky, 1e-5f)
        assertEquals(baseline.sy, snapshot.sy, 1e-5f)
        assertEquals(baseline.tx, snapshot.tx, 1e-5f)
        assertEquals(baseline.ty, snapshot.ty, 1e-5f)
    }

    @Test
    fun `getMatrixCopy is bit-identical to getMatrix`() {
        val view = Sk3DView()
        view.translate(13f, 7f, 0f)
        view.rotateY(20f)
        val a = view.getMatrix()
        val b = view.getMatrixCopy()
        assertEquals(a.sx, b.sx, 0f)
        assertEquals(a.kx, b.kx, 0f)
        assertEquals(a.tx, b.tx, 0f)
        assertEquals(a.ky, b.ky, 0f)
        assertEquals(a.sy, b.sy, 0f)
        assertEquals(a.ty, b.ty, 0f)
        assertEquals(a.persp0, b.persp0, 0f)
        assertEquals(a.persp1, b.persp1, 0f)
        assertEquals(a.persp2, b.persp2, 0f)
    }

    @Test
    fun `getMatrix is deterministic across repeated calls without mutation`() {
        val view = Sk3DView()
        view.rotateX(15f)
        view.rotateY(30f)
        view.translate(5f, -3f, 0f)
        val a = view.getMatrix()
        val b = view.getMatrix()
        val c = view.getMatrix()
        assertEquals(a.sx, b.sx, 0f); assertEquals(b.sx, c.sx, 0f)
        assertEquals(a.tx, b.tx, 0f); assertEquals(b.tx, c.tx, 0f)
        assertEquals(a.ty, b.ty, 0f); assertEquals(b.ty, c.ty, 0f)
    }

    @Test
    fun `getMatrix picks up mutations only after they run`() {
        val view = Sk3DView()
        val before = view.getMatrix()      // identity-like
        view.rotateZ(90f)
        val after = view.getMatrix()       // 2D rotation
        assertEquals(1f, before.sx, 1e-4f)
        assertEquals(0f, after.sx, 1e-4f)
        assertEquals(1f, after.kx, 1e-4f)
        // The earlier snapshot must still hold the pre-mutation value
        // — i.e. it didn't alias the view's internal state.
        assertNotEquals(before.sx, after.sx)
    }

    @Test
    fun `patchToMatrix returns a value independent of later camera mutations`() {
        val cam = SkCamera3D()
        val u = org.graphiks.math.SkV3(1f, 0f, 0f)
        val v = org.graphiks.math.SkV3(0f, -1f, 0f)
        val o = org.graphiks.math.SkV3(0f, 0f, 0f)
        val m1 = cam.patchToMatrix(arrayOf(u, v, o))
        // Re-target the camera and force a recompute.
        cam.location = org.graphiks.math.SkV3(50f, 50f, -300f)
        cam.update()
        val m2 = cam.patchToMatrix(arrayOf(u, v, o))
        // The first matrix must NOT have absorbed the new camera state.
        assertNotEquals(m1.tx, m2.tx, "patchToMatrix #1 must not see the post-update location")
    }
}
