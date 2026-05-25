package org.skia.gpu.webgpu.crossbackend

import org.graphiks.math.SK_ColorGRAY
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
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
import org.skia.gpu.webgpu.SkWebGpuDevice
import org.skia.gpu.webgpu.WebGpuContext

class SrcRectConstraintCrossBackendTest {
    @Test
    fun `strict and fast edge behavior matches raster on webgpu`() {
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

        fun drawWithCanvas(canvas: SkCanvas) {
            canvas.drawImageRect(
                source, srcRect, SkRect.MakeXYWH(0f, 0f, 8f, 8f), sampling,
                constraint = SrcRectConstraint.kStrict,
            )
            canvas.drawImageRect(
                source, srcRect, SkRect.MakeXYWH(8f, 0f, 8f, 8f), sampling,
                constraint = SrcRectConstraint.kFast,
            )
        }

        val raster = SkBitmap(16, 8).also { it.eraseColor(SK_ColorGRAY) }
        drawWithCanvas(SkCanvas(raster))

        val gpu = context!!.use { ctx ->
            SkWebGpuDevice(ctx, 16, 8).use { device ->
                device.setBackground(SK_ColorGRAY)
                drawWithCanvas(SkCanvas(device))
                device.flush()
            }
        }

        val rasterStrict = rgbaAtBitmap(raster, 0, 0)
        val rasterFast = rgbaAtBitmap(raster, 8, 0)
        val gpuStrict = rgbaAt(gpu, 0, 0, 16)
        val gpuFast = rgbaAt(gpu, 8, 0, 16)

        assertEquals(rasterStrict, gpuStrict, "kStrict edge pixel should match raster")
        assertTrue(gpuFast[0] > 0, "kFast edge should include red guard contribution")
        assertTrue(gpuFast[1] in 1 until 255, "kFast edge should keep partial green")
        assertTrue(rasterFast[0] > 0, "raster oracle: kFast edge includes red guard")
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

    private fun rgbaAtBitmap(bitmap: SkBitmap, x: Int, y: Int): List<Int> {
        val c = bitmap.getPixel(x, y)
        return listOf(SkColorGetR(c), SkColorGetG(c), SkColorGetB(c), SkColorGetA(c))
    }
}
