package org.skia.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect

/**
 * R-suivi.4 — verifies the real [SkCanvasStateUtils] implementation
 * captures and re-vends an [SkCanvas]'s CTM / clip / dimensions.
 */
class SkCanvasStateUtilsTest {

    @Test
    fun `CaptureCanvasState records the canvas dimensions`() {
        val canvas = SkCanvas(SkBitmap(64, 48))
        val state = SkCanvasStateUtils.CaptureCanvasState(canvas)
        assertNotNull(state, "CaptureCanvasState must succeed for a vanilla raster canvas")
        assertEquals(64, state!!.width)
        assertEquals(48, state.height)
    }

    @Test
    fun `CaptureCanvasState records the current CTM`() {
        val canvas = SkCanvas(SkBitmap(100, 100))
        canvas.translate(10f, 20f)
        canvas.scale(2f, 3f)
        val state = SkCanvasStateUtils.CaptureCanvasState(canvas)
        assertNotNull(state)
        // Translate(10,20) * Scale(2,3) → expected matrix.
        val expected = SkMatrix.Identity.preTranslate(10f, 20f).preScale(2f, 3f)
        // Map a sample point to verify the matrices agree.
        val (ex, ey) = expected.mapXY(5f, 7f)
        val (ax, ay) = state!!.matrix.mapXY(5f, 7f)
        assertTrue(
            kotlin.math.abs(ex - ax) < 1e-3f && kotlin.math.abs(ey - ay) < 1e-3f,
            "Captured matrix must round-trip the CTM ; expected ($ex,$ey) got ($ax,$ay)",
        )
    }

    @Test
    fun `MakeFromCanvasState round-trips dimensions and matrix`() {
        val srcCanvas = SkCanvas(SkBitmap(80, 60))
        srcCanvas.translate(5f, 10f)
        val srcState = SkCanvasStateUtils.CaptureCanvasState(srcCanvas)
        assertNotNull(srcState)
        val dst = SkCanvasStateUtils.MakeFromCanvasState(srcState)
        assertNotNull(dst, "MakeFromCanvasState must succeed for a valid state")
        assertEquals(80, dst!!.width)
        assertEquals(60, dst.height)
        // CTM round-trips — sample point (0,0) must map to (5,10).
        val mappedPair = dst.getLocalToDeviceAsMatrix()?.mapXY(0f, 0f)
        assertNotNull(mappedPair)
        val (mx, my) = mappedPair!!
        assertTrue(
            kotlin.math.abs(mx - 5f) < 1e-3f && kotlin.math.abs(my - 10f) < 1e-3f,
            "Reconstructed CTM must map (0,0) to (5,10), got ($mx,$my)",
        )
    }

    @Test
    fun `MakeFromCanvasState reapplies the clip rectangle`() {
        val src = SkCanvas(SkBitmap(50, 50))
        src.clipRect(SkRect.MakeLTRB(10f, 10f, 40f, 40f))
        val state = SkCanvasStateUtils.CaptureCanvasState(src)
        assertNotNull(state)
        val dst = SkCanvasStateUtils.MakeFromCanvasState(state)!!
        // Device clip on the reconstructed canvas must equal the
        // captured rect (modulo integer rounding).
        val clip = dst.getDeviceClipBounds()
        assertTrue(clip.right <= 40 && clip.left >= 10,
            "Reconstructed clip must lie within [10,40] horizontally, got $clip")
        assertTrue(clip.bottom <= 40 && clip.top >= 10,
            "Reconstructed clip must lie within [10,40] vertically, got $clip")
    }

    @Test
    fun `MakeFromCanvasState returns null for null state`() {
        assertNull(SkCanvasStateUtils.MakeFromCanvasState(null))
    }

    @Test
    fun `MakeFromCanvasState returns null for empty dimensions`() {
        val state = SkCanvasStateUtils.SkCanvasState(
            matrix = SkMatrix.Identity,
            clipBounds = SkRect.MakeWH(0f, 0f),
            saveCount = 1,
            width = 0,
            height = 10,
        )
        assertNull(SkCanvasStateUtils.MakeFromCanvasState(state))
    }

    @Test
    fun `ReleaseCanvasState is a silent no-op`() {
        SkCanvasStateUtils.ReleaseCanvasState(null)
        val canvas = SkCanvas(SkBitmap(8, 8))
        val state = SkCanvasStateUtils.CaptureCanvasState(canvas)
        SkCanvasStateUtils.ReleaseCanvasState(state) // must not throw
    }
}
