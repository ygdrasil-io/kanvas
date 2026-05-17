package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkBitmapDevice
import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType

/**
 * G1.4 acceptance test — `SkCanvas.clear(color)` cross-backend parity.
 *
 * Before G1.4, `canvas.clear(color)` threw `IllegalStateException` on
 * `SkWebGpuDevice` because the `drawColor(kSrc, full-clip)` fast path
 * called `bitmap.eraseColor` unconditionally, and that accessor casts
 * the device to [SkBitmapDevice]. G1.4 gates the fast path on the
 * device being a raster device ; non-raster devices fall through to
 * `device.drawPaint(...)` with the same paint/blend, which the GPU
 * device already supports (G3.2).
 *
 * The cross-test renders `canvas.clear(black)` on a white-initialised
 * surface through both backends and compares the readback bytes. If
 * this stays green, the pass-through is bit-exact for the common
 * "GMs that start with `c.clear(SK_ColorWHITE)`" pattern.
 */
class CanvasClearCrossTest {

    @Test
    fun `canvas clear renders the clear color on the GPU device`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(
            context != null,
            "No WebGPU adapter available — skipping canvas.clear cross-test",
        )

        val rasterRgba = renderRasterToRgba()
        val gpuRgba = renderGpuToRgba(context!!)

        assertArrayEquals(
            rasterRgba, gpuRgba,
            "raster and GPU outputs disagree on canvas.clear(black) — the G1.4 " +
                "pass-through is not honouring kSrc/full-clip semantics on the GPU",
        )
    }

    private fun renderRasterToRgba(): ByteArray {
        val bitmap = SkBitmap(W, H, colorType = SkColorType.kRGBA_8888).apply {
            eraseColor(SK_ColorWHITE)
        }
        val canvas = SkCanvas(SkBitmapDevice(bitmap))
        canvas.clear(SK_ColorBLACK)
        return bitmap.pixels8888.toRgbaBytes()
    }

    private fun renderGpuToRgba(context: WebGpuContext): ByteArray =
        context.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).clear(SK_ColorBLACK)
                device.flush()
            }
        }

    private fun IntArray.toRgbaBytes(): ByteArray {
        val out = ByteArray(size * 4)
        for (i in indices) {
            val pixel = this[i]
            out[i * 4]     = ((pixel ushr 16) and 0xFF).toByte()
            out[i * 4 + 1] = ((pixel ushr 8)  and 0xFF).toByte()
            out[i * 4 + 2] = ((pixel)         and 0xFF).toByte()
            out[i * 4 + 3] = ((pixel ushr 24) and 0xFF).toByte()
        }
        return out
    }

    private companion object {
        const val W: Int = 64
        const val H: Int = 64
    }
}
