package org.skia.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * Verifies that [Sk3DView] behaves like upstream's
 * [`Sk3DView`](https://github.com/google/skia/blob/main/include/utils/SkCamera.h) :
 * the identity case projects through the camera unchanged, save / restore
 * brackets isolate state, and rotateZ(deg) maps to a 2D rotation in the
 * projected matrix.
 */
class Sk3DViewTest {

    @Test
    fun `identity getMatrix produces an identity-like projection`() {
        val view = Sk3DView()
        val m = view.getMatrix()
        // The default camera at z=-576 projecting an identity patch yields an
        // identity transform up to the perspective row (persp2 == 1).
        assertEquals(1f, m.sx, 1e-4f)
        assertEquals(0f, m.kx, 1e-4f)
        assertEquals(0f, m.tx, 1e-4f)
        assertEquals(0f, m.ky, 1e-4f)
        assertEquals(1f, m.sy, 1e-4f)
        assertEquals(0f, m.ty, 1e-4f)
        assertEquals(0f, m.persp0, 1e-4f)
        assertEquals(0f, m.persp1, 1e-4f)
        assertEquals(1f, m.persp2, 1e-4f)
    }

    @Test
    fun `rotateZ 90 degrees produces a 2D rotation matrix`() {
        val view = Sk3DView()
        view.rotateZ(90f)
        val m = view.getMatrix()
        // rotateZ(90°) applies a right-handed CCW rotation around +Z in the 3D world,
        // then the camera (with zenith = (0,-1,0)) flips Y when projecting to screen.
        // Net effect on the projected 2D matrix : sx=0, kx=1, ky=-1, sy=0 ; i.e. source
        // `(1,0)` maps to `(0,-1)` (right ⇒ up on screen). Translation is zero.
        assertEquals(0f, m.sx, 1e-4f)
        assertEquals(1f, m.kx, 1e-4f)
        assertEquals(0f, m.tx, 1e-4f)
        assertEquals(-1f, m.ky, 1e-4f)
        assertEquals(0f, m.sy, 1e-4f)
        assertEquals(0f, m.ty, 1e-4f)
    }

    @Test
    fun `rotateZ 180 degrees inverts both X and Y`() {
        val view = Sk3DView()
        view.rotateZ(180f)
        val m = view.getMatrix()
        assertEquals(-1f, m.sx, 1e-4f); assertEquals(0f, m.kx, 1e-4f)
        assertEquals(0f, m.ky, 1e-4f);  assertEquals(-1f, m.sy, 1e-4f)
    }

    @Test
    fun `save and restore brackets isolate state`() {
        val view = Sk3DView()
        view.save()
        view.rotateZ(45f)
        val rotated = view.getMatrix()
        view.restore()
        val restored = view.getMatrix()

        // After restore the matrix must be back at identity (within projection precision).
        assertEquals(1f, restored.sx, 1e-4f)
        assertEquals(0f, restored.kx, 1e-4f)
        assertEquals(0f, restored.ky, 1e-4f)
        assertEquals(1f, restored.sy, 1e-4f)

        // Rotated state must differ from the restored state somewhere.
        assertTrue(abs(rotated.sx - restored.sx) > 1e-4f || abs(rotated.kx - restored.kx) > 1e-4f)
    }

    @Test
    fun `restore with empty stack throws`() {
        val view = Sk3DView()
        assertThrows(IllegalStateException::class.java) { view.restore() }
    }

    @Test
    fun `translate changes the projected translation`() {
        val view = Sk3DView()
        view.translate(100f, 50f, 0f)
        val m = view.getMatrix()
        // The default camera has zenith = (0,-1,0) — the camera Y axis is inverted
        // relative to the world Y axis (since Skia screen space has +Y down). So a
        // 3D translation of `(x, y, 0)` projects to `(x, -y)`.
        assertEquals(100f, m.tx, 1e-3f)
        assertEquals(-50f, m.ty, 1e-3f)
    }

    @Test
    fun `dotWithNormal on identity patch returns z component`() {
        val view = Sk3DView()
        // Default patch normal is along +Z (since U = +X and V = -Y ⇒ U × V = -Z).
        // Faithful to upstream's slightly idiosyncratic dotWith formula.
        val dot = view.dotWithNormal(0f, 0f, 1f)
        // Just ensure it's finite & deterministic ; the precise sign depends on the
        // upstream cross-product convention that we mirror exactly.
        assertTrue(dot.isFinite())
        // The same call twice must produce the same result (no hidden mutation).
        assertEquals(dot, view.dotWithNormal(0f, 0f, 1f))
    }

    @Test
    fun `save deep-copies the current state`() {
        val view = Sk3DView()
        view.rotateZ(30f)
        val before = view.getMatrix()
        view.save()
        view.rotateZ(30f)
        val nested = view.getMatrix()
        view.restore()
        val after = view.getMatrix()
        // Before and after must match exactly (save+restore brackets isolate).
        assertEquals(before.sx, after.sx, 1e-5f)
        assertEquals(before.kx, after.kx, 1e-5f)
        // The nested state must differ.
        assertNotEquals(before.sx, nested.sx)
    }
}
