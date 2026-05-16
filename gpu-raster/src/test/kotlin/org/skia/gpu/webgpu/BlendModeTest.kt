package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLUE
import org.skia.math.SK_ColorGREEN
import org.skia.math.SK_ColorRED
import org.skia.foundation.SkBlendMode
import org.skia.math.SkColorSetARGB
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

        // kModulate (`r = s * d`) is not expressible as a single WebGPU
        // BlendComponent without fragment-side blending — pick it as the
        // canary for the "unsupported mode" path now that kPlus has
        // moved into the supported set (G3.3a.1).
        val paint = SkPaint().apply {
            color = SK_ColorBLUE
            blendMode = SkBlendMode.kModulate
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
            thrown.message?.contains("kModulate") == true,
            "error must name the offending mode, got: ${thrown.message}",
        )
    }

    @Test
    fun `kPlus adds source onto destination with channel saturation`() {
        // Two overlapping translucent rects with kPlus :
        //   bg = white opaque (premul (1,1,1,1))
        //   first  draw  : alpha=0x80 red kSrc -> overwrites bg
        //   second draw  : alpha=0x80 blue kPlus over alpha=0x80 red
        // For the kPlus check we use a transparent black bg so the math
        // is easy to reason about.
        //   bg = transparent (0,0,0,0)
        //   red  src_premul   = (0x80/0xFF, 0, 0, 0x80/0xFF) ~= (0.502, 0, 0, 0.502)
        //   blue src_premul   = (0, 0, 0x80/0xFF, 0x80/0xFF) ~= (0, 0, 0.502, 0.502)
        //   kPlus sum (premul) = (0.502, 0, 0.502, 1.0)  -- alpha saturates
        //   bytes              = (128, 0, 128, 255 - tolerance for clamping)
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val translucentRed = SkColorSetARGB(0x80, 0xFF, 0x00, 0x00)
        val translucentBlue = SkColorSetARGB(0x80, 0x00, 0x00, 0xFF)
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SkColorSetARGB(0, 0, 0, 0))
                val canvas = SkCanvas(device)
                // Both rects cover the same area. First with kSrc to seed,
                // then with kPlus to test the channel-saturated add.
                canvas.drawRect(
                    SkRect.MakeLTRB(10f, 10f, 30f, 30f),
                    SkPaint().apply { color = translucentRed; blendMode = SkBlendMode.kSrc },
                )
                canvas.drawRect(
                    SkRect.MakeLTRB(10f, 10f, 30f, 30f),
                    SkPaint().apply { color = translucentBlue; blendMode = SkBlendMode.kPlus },
                )
                device.flush()
            }
        }

        // Inside the overlap : kPlus sum of red premul + blue premul.
        // 128 + 0 = 128 on R, 0 + 0 = 0 on G, 0 + 128 = 128 on B,
        // 128 + 128 = 255 (clamped) on A. Tolerance +/- 1 for FP rounding.
        val inside = pixels.rgbaAt(20, 20)
        assertEquals(128, inside[0], "R channel : red premul preserved")
        assertEquals(0, inside[1], "G channel : zero")
        assertEquals(128, inside[2], "B channel : blue premul preserved")
        assertEquals(255, inside[3], "A channel : 128 + 128 saturates to 255")
        // Outside : transparent bg untouched.
        assertEquals(listOf(0, 0, 0, 0), pixels.rgbaAt(50, 50), "outside : bg transparent")
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
