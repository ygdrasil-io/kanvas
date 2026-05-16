package org.skia.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.graphiks.math.SkV3

/**
 * Verifies that [SkCamera3D] mirrors the orientation / projection
 * formula from upstream's
 * [`SkCamera3D`](https://github.com/google/skia/blob/main/include/utils/SkCamera.h)
 * (`src/utils/SkCamera.cpp`).
 */
class SkCamera3DTest {

    @Test
    fun `default fields match upstream reset values`() {
        val cam = SkCamera3D()
        assertEquals(SkV3(0f, 0f, -576f), cam.location)
        assertEquals(SkV3(0f, 0f, 1f), cam.axis)
        assertEquals(SkV3(0f, -1f, 0f), cam.zenith)
        assertEquals(SkV3(0f, 0f, -576f), cam.observer)
    }

    @Test
    fun `reset restores default fields`() {
        val cam = SkCamera3D()
        cam.location = SkV3(1f, 2f, 3f)
        cam.axis = SkV3(1f, 0f, 0f)
        cam.reset()
        assertEquals(SkV3(0f, 0f, -576f), cam.location)
        assertEquals(SkV3(0f, 0f, 1f), cam.axis)
    }

    @Test
    fun `patchToMatrix on a default unit quad produces an identity-like 2D matrix`() {
        val cam = SkCamera3D()
        // Default SkPatch3D : U=(1,0,0), V=(0,-1,0), origin=(0,0,0).
        // The default camera projection on this patch yields the identity transform.
        val m = cam.patchToMatrix(arrayOf(SkV3(1f, 0f, 0f), SkV3(0f, -1f, 0f), SkV3(0f, 0f, 0f)))
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
    fun `patchToMatrix with translated origin shifts tx and ty`() {
        val cam = SkCamera3D()
        // origin = (50, 20, 0) ; tx projects directly to 50, but the camera's zenith
        // (0,-1,0) inverts Y when projecting ⇒ ty = -20.
        val m = cam.patchToMatrix(arrayOf(SkV3(1f, 0f, 0f), SkV3(0f, -1f, 0f), SkV3(50f, 20f, 0f)))
        assertEquals(50f, m.tx, 1e-3f)
        assertEquals(-20f, m.ty, 1e-3f)
        assertEquals(1f, m.sx, 1e-4f)
        assertEquals(1f, m.sy, 1e-4f)
    }

    @Test
    fun `patchToMatrix requires exactly 3 SkV3 entries`() {
        val cam = SkCamera3D()
        assertThrows(IllegalArgumentException::class.java) {
            cam.patchToMatrix(arrayOf(SkV3(1f, 0f, 0f)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            cam.patchToMatrix(arrayOf(SkV3(1f, 0f, 0f), SkV3(0f, 1f, 0f), SkV3(0f, 0f, 0f), SkV3.ZERO))
        }
    }

    @Test
    fun `update flips the cached orientation flag without crashing`() {
        val cam = SkCamera3D()
        cam.location = SkV3(0f, 0f, -1000f)
        cam.observer = SkV3(0f, 0f, -1000f)
        cam.update()
        // After update, the next patchToMatrix call must succeed and produce a finite matrix.
        val m = cam.patchToMatrix(arrayOf(SkV3(1f, 0f, 0f), SkV3(0f, -1f, 0f), SkV3(0f, 0f, 0f)))
        // For a camera further back (|z| larger) the projection scales DOWN.
        // sx must remain ≈ 1 since both location.z and observer.z scaled together.
        assertEquals(1f, m.sx, 1e-4f)
    }
}
