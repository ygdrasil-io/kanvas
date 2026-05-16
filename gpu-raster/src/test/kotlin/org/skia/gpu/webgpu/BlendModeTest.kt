package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SK_ColorGREEN
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorSetARGB
import org.skia.foundation.SkPaint
import org.skia.math.SkRect

/**
 * G2.2 acceptance tests — multi-mode pipeline cache + 4 WebGPU-native
 * Porter-Duff blend modes (kClear / kSrc / kSrcOver / kDstOver).
 *
 * Each test scopes one mode against a known background + source and
 * asserts the centre pixel of the drawn rect plus a control pixel
 * outside the rect. Pipeline selection is exercised end-to-end :
 * `paint.blendMode` → `RectDraw.mode` → `pipelineFor(mode)` →
 * `setPipeline` in the render pass.
 */
class BlendModeTest {

    @Test
    fun `kSrc replaces destination pixels with the source color`() {
        // bg = red opaque ; rect = opaque blue with kSrc.
        // Expected : src overwrites dst entirely inside the rect, dst untouched outside.
        val pixels = runOneRectOverBg(
            bgArgb = SK_ColorRED,
            srcArgb = SK_ColorBLUE,
            mode = SkBlendMode.kSrc,
        )

        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(50, 50), "outside : bg red unchanged")
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(20, 20), "inside : src blue replaces red")
    }

    @Test
    fun `kClear zeros pixels under the rect regardless of source color`() {
        // bg = red opaque ; rect = (intentionally bright green) with kClear.
        // Both src factor and dst factor are Zero, so the output is (0,0,0,0)
        // whatever the source color is.
        val pixels = runOneRectOverBg(
            bgArgb = SK_ColorRED,
            srcArgb = SK_ColorGREEN,
            mode = SkBlendMode.kClear,
        )

        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(50, 50), "outside : bg red unchanged")
        assertEquals(listOf(0, 0, 0, 0), pixels.rgbaAt(20, 20), "inside : kClear zeroes everything")
    }

    @Test
    fun `kSrcOver blends through the new pipeline-cache path`() {
        // Re-asserts G2.1 math but routed through pipelineFor(kSrcOver)
        // instead of the old hardcoded pipeline. Same math, same bytes.
        val translucentBlue = SkColorSetARGB(0x80, 0x00, 0x00, 0xFF)
        val pixels = runOneRectOverBg(
            bgArgb = SK_ColorRED,
            srcArgb = translucentBlue,
            mode = SkBlendMode.kSrcOver,
        )

        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(50, 50), "outside : bg red unchanged")
        // (127, 0, 128, 255) +/- 1 ulp on R/B, see TranslucentSrcOverTest math.
        val inside = pixels.rgbaAt(20, 20)
        assertNear(listOf(127, 0, 128, 255), inside, tolerance = 1, label = "kSrcOver blend")
    }

    @Test
    fun `kDstOver paints under dst — visible on transparent, hidden under opaque`() {
        // First half : bg = transparent. Drawing opaque blue under-mode lands on top
        // (dst.a == 0, so src factor 1-0 = 1, dst factor 1 contributes nothing).
        val onTransparent = runOneRectOverBg(
            bgArgb = SkColorSetARGB(0, 0, 0, 0),
            srcArgb = SK_ColorBLUE,
            mode = SkBlendMode.kDstOver,
        )
        assertEquals(listOf(0, 0, 0, 0), onTransparent.rgbaAt(50, 50), "outside : bg transparent unchanged")
        assertEquals(listOf(0, 0, 255, 255), onTransparent.rgbaAt(20, 20), "kDstOver fills the transparent hole")

        // Second half : bg = red opaque. kDstOver leaves dst untouched
        // (dst.a == 1, so src factor 1-1 = 0 contributes nothing).
        val onOpaque = runOneRectOverBg(
            bgArgb = SK_ColorRED,
            srcArgb = SK_ColorBLUE,
            mode = SkBlendMode.kDstOver,
        )
        assertEquals(listOf(255, 0, 0, 255), onOpaque.rgbaAt(50, 50), "outside : bg red unchanged")
        assertEquals(listOf(255, 0, 0, 255), onOpaque.rgbaAt(20, 20), "inside : opaque dst hides the src")
    }

    @Test
    fun `unsupported blend mode throws a helpful error at flush`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // kPlus needs fragment-side blending — out of scope for G2.2. The
        // pipeline cache lazy-creates on first use inside flush(), so the
        // throw fires there rather than at drawRect.
        val paint = SkPaint().apply {
            color = SK_ColorBLUE
            blendMode = SkBlendMode.kPlus
        }
        val thrown = assertThrows(IllegalStateException::class.java) {
            context!!.use { ctx ->
                SkWebGpuDevice(ctx, W, H).use { device ->
                    SkCanvas(device).drawRect(SkRect.MakeLTRB(10f, 10f, 30f, 30f), paint)
                    device.flush()
                }
            }
        }
        assertTrue(
            thrown.message?.contains("kPlus") == true &&
                thrown.message?.contains("G2.2") == true,
            "error must name the offending mode and point to the right phase, " +
                "got: ${thrown.message}",
        )
    }

    private fun runOneRectOverBg(
        bgArgb: Int,
        srcArgb: Int,
        mode: SkBlendMode,
    ): ByteArray {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val paint = SkPaint().apply {
            color = srcArgb
            blendMode = mode
        }
        return context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(bgArgb)
                SkCanvas(device).drawRect(SkRect.MakeLTRB(10f, 10f, 30f, 30f), paint)
                device.flush()
            }
        }
    }

    private fun ByteArray.rgbaAt(x: Int, y: Int): List<Int> {
        val i = (y * W + x) * 4
        return listOf(
            this[i].toInt() and 0xFF,
            this[i + 1].toInt() and 0xFF,
            this[i + 2].toInt() and 0xFF,
            this[i + 3].toInt() and 0xFF,
        )
    }

    private fun assertNear(expected: List<Int>, actual: List<Int>, tolerance: Int, label: String) {
        for (c in 0 until 4) {
            assertTrue(
                kotlin.math.abs(expected[c] - actual[c]) <= tolerance,
                "$label channel $c: expected ${expected[c]} +/- $tolerance, got ${actual[c]} " +
                    "(full pixel expected=$expected, actual=$actual)",
            )
        }
    }

    private companion object {
        const val W: Int = 64
        const val H: Int = 64
    }
}
