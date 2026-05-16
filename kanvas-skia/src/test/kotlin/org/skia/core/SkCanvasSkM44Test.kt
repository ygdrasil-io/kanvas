package org.skia.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkBitmap
import org.graphiks.math.SkM44
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkV3

/**
 * R3.1-bis — verify that `SkCanvas` correctly stores and exposes the
 * full 4×4 CTM via the new `SkM44`-flavoured API:
 *
 *  * `concat(SkM44)`        — pre-multiply with the canvas CTM,
 *                             round-trips through `getLocalToDevice()`.
 *  * `setMatrix(SkM44)`     — replaces the CTM wholesale, clears any
 *                             prior 4×4 state.
 *  * `getLocalToDevice()`   — always returns a non-null 4×4 covering
 *                             both the legacy 3×3 path and the new
 *                             perspective-carrying path.
 *  * `getLocalToDeviceAsMatrix()` — null sentinel for true 3D /
 *                             perspective CTMs.
 *
 * Wiring the 4×4 into the rasteriser is out of scope ; these tests
 * exercise pure state-management invariants on the API surface.
 */
class SkCanvasSkM44Test {

    private fun canvas(): SkCanvas = SkCanvas(SkBitmap(16, 16))

    private fun assertM44Near(expected: SkM44, actual: SkM44, eps: Float = 1e-5f) {
        for (r in 0..3) for (c in 0..3) {
            assertEquals(
                expected.rc(r, c), actual.rc(r, c), eps,
                "rc($r, $c): expected $expected got $actual",
            )
        }
    }

    @Test
    fun `getLocalToDevice on a fresh canvas returns identity`() {
        val c = canvas()
        val m = c.getLocalToDevice()
        assertTrue(m.isIdentity(), "Expected identity, got $m")
    }

    @Test
    fun `getLocalToDeviceAsMatrix on a fresh canvas returns identity`() {
        val c = canvas()
        val m = c.getLocalToDeviceAsMatrix()
        assertNotNull(m, "Fresh canvas must have a defined 3x3 CTM")
        assertTrue(m!!.isIdentity, "Expected identity 3x3, got $m")
    }

    @Test
    fun `concat with a translation roundtrips through getLocalToDevice`() {
        val c = canvas()
        val t = SkM44.translate(3f, 5f, 7f)
        c.concat(t)
        val out = c.getLocalToDevice()
        assertM44Near(t, out)
    }

    @Test
    fun `concat with a perspective makes getLocalToDeviceAsMatrix return null`() {
        val c = canvas()
        // True 3D perspective: angle != 0, far > near. The resulting
        // M44 has non-zero entries in row 2 and column 2 that would
        // not survive `asM33()` round-tripping.
        val persp = SkM44.perspective(near = 0.05f, far = 100f, angle = 1f)
        c.concat(persp)

        assertNull(
            c.getLocalToDeviceAsMatrix(),
            "Expected null for true 3D perspective, got ${c.getLocalToDeviceAsMatrix()}",
        )
        // The 4x4 is still readable.
        val full = c.getLocalToDevice()
        assertFalse(full.isIdentity(), "Perspective CTM must not be identity")
    }

    @Test
    fun `setMatrix with identity SkM44 clears any prior CTM`() {
        val c = canvas()
        c.concat(SkM44.translate(11f, 22f, 33f))
        c.concat(SkM44.rotate(SkV3(0f, 0f, 1f), 0.5f))

        c.setMatrix(SkM44.identity())

        assertTrue(c.getLocalToDevice().isIdentity(), "setMatrix(identity) must reset 4x4")
        val m33 = c.getLocalToDeviceAsMatrix()
        assertNotNull(m33)
        assertTrue(m33!!.isIdentity, "setMatrix(identity) must reset 3x3 affine")
    }

    @Test
    fun `setMatrix with a 4x4 replaces all prior 4x4 state`() {
        val c = canvas()
        c.concat(SkM44.translate(1f, 2f, 3f))
        // Mid-stream: any prior state is wiped.
        val newCTM = SkM44.scale(2f, 4f, 8f)
        c.setMatrix(newCTM)
        assertM44Near(newCTM, c.getLocalToDevice())
    }

    @Test
    fun `getTotalMatrix matches the 3x3 affine subset when no perspective`() {
        @Suppress("DEPRECATION")
        val c = canvas()
        // Pure affine 4x4: a translate.
        c.concat(SkM44.translate(10f, 20f))

        @Suppress("DEPRECATION")
        val totalMatrix = c.getTotalMatrix()
        val asM33 = c.getLocalToDeviceAsMatrix()
        assertNotNull(asM33, "Pure translate must have a defined 3x3")
        assertEquals(asM33, totalMatrix, "getTotalMatrix must equal getLocalToDeviceAsMatrix when affine")
        assertEquals(10f, totalMatrix.tx, 1e-5f)
        assertEquals(20f, totalMatrix.ty, 1e-5f)
    }

    @Test
    fun `concat is pre-multiply matching upstream`() {
        // upstream `SkCanvas::concat(SkM44)` pre-concats : the new
        // transform applies first. So translate-then-scale should map
        // local (0,0) -> (tx*sx ? no — scale*translate = translate-then-scale).
        // Specifically: CTM = T · S. Point (0,0) -> T(S(0,0)) = T(0,0) = (3,5).
        val c = canvas()
        c.concat(SkM44.translate(3f, 5f, 0f))
        c.concat(SkM44.scale(2f, 4f, 1f))

        val m = c.getLocalToDevice()
        // M = T · S. Apply to (0,0,0,1): yields (3,5,0,1).
        val mapped = m.map(0f, 0f, 0f, 1f)
        assertEquals(3f, mapped.x, 1e-5f)
        assertEquals(5f, mapped.y, 1e-5f)

        // Apply to (1,1,0,1): T(S((1,1))) = T(2,4) = (5, 9).
        val mapped2 = m.map(1f, 1f, 0f, 1f)
        assertEquals(5f, mapped2.x, 1e-5f)
        assertEquals(9f, mapped2.y, 1e-5f)
    }

    @Test
    fun `save and restore preserve the SkM44 CTM`() {
        val c = canvas()
        c.concat(SkM44.translate(7f, 11f, 13f))
        val before = c.getLocalToDevice()

        val sc = c.save()
        c.concat(SkM44.scale(2f, 3f, 5f))
        // Inside the save, the CTM has changed.
        val inside = c.getLocalToDevice()
        assertFalse(inside == before, "Inside save: CTM must have changed")

        c.restoreToCount(sc)
        assertM44Near(before, c.getLocalToDevice())
    }

    @Test
    fun `2D mutator after concat(SkM44) keeps the canvas usable`() {
        // After a 4x4 concat, calling translate() must clear the 4x4
        // slot (per the dual-storage policy) and the canvas keeps
        // working as a plain 2D surface.
        val c = canvas()
        c.concat(SkM44.translate(4f, 5f, 6f))
        c.translate(1f, 2f)

        val m33 = c.getLocalToDeviceAsMatrix()
        assertNotNull(m33, "After 2D mutator the CTM must collapse to affine")
        // The 4x4 stored at the time of concat had tx=4, ty=5. The
        // subsequent `translate(1,2)` pre-translates the 3x3 (which
        // was tx=4, ty=5 after collapse), so the final tx/ty are
        // 4 + 1*1 = 5 and 5 + 1*2 = 7 respectively.
        assertEquals(5f, m33!!.tx, 1e-5f)
        assertEquals(7f, m33.ty, 1e-5f)
    }

    @Test
    fun `setMatrix(SkMatrix) overload still works and clears m44`() {
        val c = canvas()
        c.concat(SkM44.perspective(near = 0.05f, far = 100f, angle = 1f))
        // Confirm perspective is present.
        assertNull(c.getLocalToDeviceAsMatrix())

        // Reset via the legacy 3x3 overload.
        c.setMatrix(SkMatrix.Identity)
        assertTrue(c.getLocalToDevice().isIdentity())
        assertNotNull(c.getLocalToDeviceAsMatrix())
    }
}
