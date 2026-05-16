package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkBitmapDevice
import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLUE
import org.skia.math.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkPaint
import org.skia.math.SkRect

/**
 * G1.3 acceptance test — minimal cross-backend rendering parity check.
 *
 * **Why a custom test, not the BigRectGM port the original plan named.**
 * `BigRectGM` exercises stroke (kStroke_Style), AA (`isAntiAlias = true`),
 * CTM `translate` mid-draw, `clipRect`, and 240+ rects per call —
 * everything SkWebGpuDevice does NOT yet support in G1.2. Running it
 * against SkWebGpuDevice would either crash (the `require` guards in
 * `drawRect`) or score near 0% (every stroke / AA cell falls through).
 *
 * Instead, this test scopes the cross-validation to exactly what G1.2
 * delivers: a single axis-aligned, opaque-colour, fill-style, non-AA
 * rectangle. Renders it through both backends via SkCanvas → SkDevice
 * and checks that the resulting bytes match. If this test stays green,
 * the SkDevice abstraction extracted in G1.1 actually works end-to-end
 * across raster and GPU.
 *
 * BigRectGM (and the full GM-port cross-validation harness with a
 * ratchet file) gets unlocked phase by phase as SkWebGpuDevice gains AA
 * (G2), strokes (G2), and clip handling (G2) — see the master plan.
 */
class RectFillCrossTest {

    @Test
    fun `single blue rect on white renders identical pixels on raster and gpu`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(
            context != null,
            "No WebGPU adapter available — skipping cross-backend rect test",
        )

        val rect = SkRect.MakeLTRB(10f, 10f, 30f, 30f)
        val paint = SkPaint().apply { color = SK_ColorBLUE }

        val rasterRgba = renderRasterToRgba(rect, paint)
        val gpuRgba = renderGpuToRgba(context!!, rect, paint)

        assertArrayEquals(
            rasterRgba, gpuRgba,
            "raster and GPU outputs disagree — the SkDevice abstraction is not " +
                "honouring identical drawRect semantics across backends",
        )
    }

    @Test
    fun `rect bottom-right clipped at viewport edge stays clipped on gpu`() {
        // Sanity check on the pixel-edge + viewport-coerce math : a rect
        // that extends past the right/bottom edges still produces the same
        // sub-rect on both backends.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter available")

        val rect = SkRect.MakeLTRB(50f, 50f, 100f, 100f) // extends past 64
        val paint = SkPaint().apply { color = SK_ColorBLUE }

        val rasterRgba = renderRasterToRgba(rect, paint)
        val gpuRgba = renderGpuToRgba(context!!, rect, paint)

        assertArrayEquals(rasterRgba, gpuRgba)
    }

    private fun renderRasterToRgba(rect: SkRect, paint: SkPaint): ByteArray {
        val bitmap = SkBitmap(W, H, colorType = SkColorType.kRGBA_8888).apply {
            eraseColor(SK_ColorWHITE)
        }
        val device = SkBitmapDevice(bitmap)
        val canvas = SkCanvas(device)
        canvas.drawRect(rect, paint)
        return bitmap.pixels8888.toRgbaBytes()
    }

    private fun renderGpuToRgba(context: WebGpuContext, rect: SkRect, paint: SkPaint): ByteArray =
        context.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                canvas.drawRect(rect, paint)
                device.flush()
            }
        }

    /**
     * SkBitmap stores `kRGBA_8888` pixels as `0xAARRGGBB` ints (per the
     * doc comment on `SkBitmap.pixels8888`). Lay them out as RGBA bytes
     * so we can compare against [SkWebGpuDevice.flush]'s readback which
     * comes back row-major RGBA.
     */
    private fun IntArray.toRgbaBytes(): ByteArray {
        val out = ByteArray(size * 4)
        for (i in indices) {
            val pixel = this[i]
            out[i * 4]     = ((pixel ushr 16) and 0xFF).toByte() // R
            out[i * 4 + 1] = ((pixel ushr 8)  and 0xFF).toByte() // G
            out[i * 4 + 2] = ((pixel)         and 0xFF).toByte() // B
            out[i * 4 + 3] = ((pixel ushr 24) and 0xFF).toByte() // A
        }
        return out
    }

    private companion object {
        const val W: Int = 64
        const val H: Int = 64
    }
}
