package org.skia.gpu.webgpu

import org.graphiks.math.SK_ColorGRAY
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.core.SrcRectConstraint
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkSamplingOptions

class SrcRectConstraintWebGpuTest {
    @Test
    fun `strict drawImageRect prevents linear filter guard-pixel bleed on webgpu`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val source = SkBitmap(6, 6).apply {
            eraseColor(SK_ColorRED)
            for (y in 1 until 5) {
                for (x in 1 until 5) setPixel(x, y, SK_ColorGREEN)
            }
        }.asImage()

        val srcRect = SkRect.MakeLTRB(1f, 1f, 5f, 5f)
        val sampling = SkSamplingOptions(SkFilterMode.kLinear)

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, 16, 8).use { device ->
                device.setBackground(SK_ColorGRAY)
                val canvas = SkCanvas(device)
                canvas.drawImageRect(
                    source, srcRect, SkRect.MakeXYWH(0f, 0f, 8f, 8f), sampling,
                    constraint = SrcRectConstraint.kStrict,
                )
                canvas.drawImageRect(
                    source, srcRect, SkRect.MakeXYWH(8f, 0f, 8f, 8f), sampling,
                    constraint = SrcRectConstraint.kFast,
                )
                device.flush()
            }
        }

        val strictEdge = rgbaAt(pixels, 0, 0, 16)
        val fastEdge = rgbaAt(pixels, 8, 0, 16)
        assertEquals(0, strictEdge[0], "kStrict edge R")
        assertEquals(255, strictEdge[1], "kStrict edge G")
        assertTrue(fastEdge[0] > 0, "kFast should pick red guard pixels at subset edge")
        assertTrue(fastEdge[1] in 1 until 255, "kFast should blend guard red with interior green")
    }

    private fun rgbaAt(pixels: ByteArray, x: Int, y: Int, width: Int): List<Int> {
        val i = (y * width + x) * 4
        return listOf(
            pixels[i].toInt() and 0xFF,
            pixels[i + 1].toInt() and 0xFF,
            pixels[i + 2].toInt() and 0xFF,
            pixels[i + 3].toInt() and 0xFF,
        )
    }
}
