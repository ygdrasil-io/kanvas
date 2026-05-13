package org.skia.utils

import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap

/**
 * R1 stub-coverage tests for [SkCanvasStateUtils]. Every method
 * should return `null` / be a no-op without throwing.
 */
class SkCanvasStateUtilsTest {

    @Test
    fun `CaptureCanvasState returns null in R1 stub`() {
        val canvas = SkCanvas(SkBitmap(4, 4))
        assertNull(SkCanvasStateUtils.CaptureCanvasState(canvas))
    }

    @Test
    fun `MakeFromCanvasState returns null in R1 stub`() {
        assertNull(SkCanvasStateUtils.MakeFromCanvasState(null))
    }

    @Test
    fun `ReleaseCanvasState is a silent no-op`() {
        SkCanvasStateUtils.ReleaseCanvasState(null)
    }
}
