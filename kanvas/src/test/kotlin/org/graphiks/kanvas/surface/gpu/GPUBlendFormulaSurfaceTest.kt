package org.graphiks.kanvas.surface.gpu

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.renderer.materials.GPUBlendFormulaLibrary
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendDestinationReadRequirement
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.alphaByte
import org.graphiks.kanvas.types.blueByte
import org.graphiks.kanvas.types.greenByte
import org.graphiks.kanvas.types.redByte
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue

@OptIn(ExperimentalUnsignedTypes::class)
class GPUBlendFormulaSurfaceTest {
    @AfterEach
    fun disposeRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

    @org.junit.jupiter.api.Test
    fun `all mode dispatcher emits every destination read formula at its stable index`() {
        val dispatcher = GPUBlendFormulaLibrary.allModeBlendDispatcherWgsl()
        val cases = destinationReadDispatcherCases()

        // Cross-check the hardcoded case list against the mode registry: every mode whose
        // canonical plan requires a destination texture must have a dispatcher case, so a
        // future dst-read mode with no case fails loudly instead of passing silently.
        // The dispatcher also carries stable advanced-formula cases for modes that plan as
        // fixed-function at full coverage on a clamped target (SCREEN), so the case list is
        // a superset of the canonical dst-read set; the 15-case size pin below keeps the
        // stable 14..28 case ABI itself honest against additions.
        val canonicalDstReadModes = BlendMode.entries
            .filter { mode ->
                mode.toGpuBlendFacts().canonicalBlendPlan().destinationReadRequirement ==
                    GPUBlendDestinationReadRequirement.DestinationTextureRequired
            }
            .toSet()
        assertTrue(
            canonicalDstReadModes.all { mode -> cases.any { (caseMode, _) -> caseMode == mode } },
            "missing dispatcher case for canonical destination-read modes: " +
                (canonicalDstReadModes - cases.map { (mode, _) -> mode }.toSet()),
        )
        assertEquals(15, cases.size)
        assertEquals(
            cases.size,
            cases.map { (_, caseLine) -> caseLine }.toSet().size,
            "destination-read mode indices must be unique",
        )
        cases.forEach { (mode, caseLine) ->
            assertTrue(
                dispatcher.contains(caseLine),
                "missing $mode dispatcher case: $caseLine",
            )
        }
    }

    @org.junit.jupiter.api.Test
    fun `DARKEN rect renders prepared through the destination read formula route`() {
        val session = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(session != null, "GPU backend unavailable in current environment")
        val source = Color.fromArgb(255, 192, 64, 32)

        // A single destination-read draw renders prepared with the GPU copy-then-formula
        // route. (A second background draw splits the frame into the two-render dst-copy
        // shape, which the prepared direct lane admits; the pixel oracle for
        // that shape lives in GPUPathClipRegressionTest and GPUAllApiBlendSurfaceTest.)
        val result = Surface(width = 32, height = 32).run {
            canvas {
                drawRect(
                    RectF32(4f, 4f, 28f, 28f),
                    Paint.fill(source).copy(antiAlias = false, blendMode = BlendMode.DARKEN),
                )
            }
            render()
        }

        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertEquals(0, result.stats.opsRefused, result.diagnostics.entries.toString())
        assertTrue(
            result.diagnostics.entries.any { entry ->
                entry.code.startsWith("route:destination-read:DrawRect") &&
                    entry.reason == "gpu-copy-then-formula"
            },
            result.diagnostics.entries.toString(),
        )
        // Pixel-oracle disclosure: the destination is transparent, so every blend mode
        // yields source-over-transparent and the oracle cannot discriminate formula math.
        // This test pins the end-to-end dst-read pipeline (copy-then-formula evidence),
        // not the formula itself — DARKEN against a real backdrop is covered by
        // GPUClipAdvancedBlendSurfaceTest.
        assertPixelNear(
            pixels = result.pixels,
            x = 16,
            y = 16,
            expected = expectedBlend(source, Color.TRANSPARENT, BlendMode.DARKEN),
            tolerance = 2,
        )
    }

    @org.junit.jupiter.api.Test
    fun `Kanvas compatibility programs assemble formula bodies from the gpu-renderer registry`() {
        val destinationRead = GPUBlendFormulaLibrary.advancedBlendDispatcherWgsl()
        val allModes = GPUBlendFormulaLibrary.allModeBlendDispatcherWgsl()

        assertTrue(BLEND_FORMULA_WGSL.contains(destinationRead))
        assertTrue(CLIP_BLEND_FORMULA_WGSL.contains(destinationRead))
        assertTrue(SCISSOR_CLIP_BLEND_FORMULA_WGSL.contains(destinationRead))
        assertTrue(CLIP_COVERAGE_BLEND_WGSL.contains(allModes))
    }

    private fun expectedBlend(source: Color, destination: Color, mode: BlendMode): Color {
        val src = source.toLinearRgb()
        val dst = destination.toLinearRgb()
        val sourceAlpha = source.alphaByte / 255f
        val destinationAlpha = destination.alphaByte / 255f
        val blended = blendColor(src, dst, mode)
        val outputAlpha = sourceAlpha + destinationAlpha * (1f - sourceAlpha)
        if (outputAlpha == 0f) return Color.fromRGBA(0f, 0f, 0f, 0f)
        val outputPremul = FloatArray(3) { channel ->
            src[channel] * sourceAlpha * (1f - destinationAlpha) +
                dst[channel] * destinationAlpha * (1f - sourceAlpha) +
                sourceAlpha * destinationAlpha * blended[channel]
        }
        return Color.fromRGBA(
            linearToSrgb(outputPremul[0]),
            linearToSrgb(outputPremul[1]),
            linearToSrgb(outputPremul[2]),
            outputAlpha,
        )
    }

    private fun blendColor(source: FloatArray, destination: FloatArray, mode: BlendMode): FloatArray =
        FloatArray(3) { channel ->
            val src = source[channel]
            val dst = destination[channel]
            when (mode) {
                BlendMode.MULTIPLY -> src * dst
                BlendMode.SCREEN -> src + dst - src * dst
                BlendMode.OVERLAY -> if (dst <= 0.5f) 2f * src * dst else 1f - 2f * (1f - src) * (1f - dst)
                BlendMode.DARKEN -> min(src, dst)
                BlendMode.LIGHTEN -> max(src, dst)
                BlendMode.COLOR_DODGE -> if (dst == 0f) 0f else if (src == 1f) 1f else min(1f, dst / (1f - src))
                BlendMode.COLOR_BURN -> if (dst == 1f) 1f else if (src == 0f) 0f else 1f - min(1f, (1f - dst) / src)
                BlendMode.HARD_LIGHT -> if (src <= 0.5f) 2f * src * dst else 1f - 2f * (1f - src) * (1f - dst)
                BlendMode.SOFT_LIGHT -> softLight(dst, src)
                BlendMode.DIFFERENCE -> abs(dst - src)
                BlendMode.EXCLUSION -> src + dst - 2f * src * dst
                BlendMode.HUE,
                BlendMode.SATURATION,
                BlendMode.COLOR,
                BlendMode.LUMINOSITY,
                -> 0f
                else -> error("Not a destination-read blend mode: $mode")
            }
        }.let { channelBlend ->
            when (mode) {
                BlendMode.HUE -> setLum(setSat(source, sat(destination)), lum(destination))
                BlendMode.SATURATION -> setLum(setSat(destination, sat(source)), lum(destination))
                BlendMode.COLOR -> setLum(source, lum(destination))
                BlendMode.LUMINOSITY -> setLum(destination, lum(source))
                else -> channelBlend
            }
        }

    private fun softLight(backdrop: Float, source: Float): Float =
        if (source <= 0.5f) {
            backdrop - (1f - 2f * source) * backdrop * (1f - backdrop)
        } else {
            val d = if (backdrop <= 0.25f) {
                ((16f * backdrop - 12f) * backdrop + 4f) * backdrop
            } else {
                sqrt(backdrop)
            }
            backdrop + (2f * source - 1f) * (d - backdrop)
        }

    private fun lum(color: FloatArray): Float =
        color[0] * 0.3f + color[1] * 0.59f + color[2] * 0.11f

    private fun sat(color: FloatArray): Float =
        color.maxOrNull()!! - color.minOrNull()!!

    private fun setSat(color: FloatArray, saturation: Float): FloatArray {
        val min = color.minOrNull()!!
        val max = color.maxOrNull()!!
        if (max == min) return FloatArray(3)
        return FloatArray(3) { channel -> (color[channel] - min) * saturation / (max - min) }
    }

    private fun setLum(color: FloatArray, luminosity: Float): FloatArray {
        val delta = luminosity - lum(color)
        return clipColor(FloatArray(3) { channel -> color[channel] + delta })
    }

    private fun clipColor(color: FloatArray): FloatArray {
        val luminosity = lum(color)
        val min = color.minOrNull()!!
        val max = color.maxOrNull()!!
        var clipped = color.copyOf()
        if (min < 0f) {
            clipped = FloatArray(3) { channel ->
                luminosity + (clipped[channel] - luminosity) * luminosity / (luminosity - min)
            }
        }
        if (max > 1f) {
            clipped = FloatArray(3) { channel ->
                luminosity + (clipped[channel] - luminosity) * (1f - luminosity) / (max - luminosity)
            }
        }
        return clipped
    }

    private fun Color.toLinearRgb(): FloatArray = floatArrayOf(
        srgbToLinear(redByte / 255f),
        srgbToLinear(greenByte / 255f),
        srgbToLinear(blueByte / 255f),
    )

    private fun srgbToLinear(value: Float): Float =
        if (value <= 0.04045f) value / 12.92f else ((value + 0.055f) / 1.055f).pow(2.4f)

    private fun linearToSrgb(value: Float): Float =
        if (value <= 0.0031308f) value * 12.92f else 1.055f * value.pow(1f / 2.4f) - 0.055f

    private fun assertPixelNear(pixels: UByteArray, x: Int, y: Int, expected: Color, tolerance: Int) {
        val offset = (y * 32 + x) * 4
        val actual = intArrayOf(
            pixels[offset].toInt(),
            pixels[offset + 1].toInt(),
            pixels[offset + 2].toInt(),
            pixels[offset + 3].toInt(),
        )
        val wanted = intArrayOf(expected.redByte, expected.greenByte, expected.blueByte, expected.alphaByte)
        wanted.indices.forEach { channel ->
            assertTrue(
                abs(wanted[channel] - actual[channel]) <= tolerance,
                "channel=$channel at ($x,$y): actual=${actual.toList()} expected=${wanted.toList()}",
            )
        }
    }

    private companion object {
        /**
         * The stable all-mode dispatcher ABI maps destination-read modes onto cases
         * 14..28, each delegating to its canonical advanced formula index.
         */
        fun destinationReadDispatcherCases(): List<Pair<BlendMode, String>> = listOf(
            BlendMode.MULTIPLY to "case 14u: { return kanvasBlendAdvancedPremul(src, dst, 0u); }",
            BlendMode.SCREEN to "case 15u: { return kanvasBlendAdvancedPremul(src, dst, 1u); }",
            BlendMode.OVERLAY to "case 16u: { return kanvasBlendAdvancedPremul(src, dst, 2u); }",
            BlendMode.DARKEN to "case 17u: { return kanvasBlendAdvancedPremul(src, dst, 3u); }",
            BlendMode.LIGHTEN to "case 18u: { return kanvasBlendAdvancedPremul(src, dst, 4u); }",
            BlendMode.COLOR_DODGE to "case 19u: { return kanvasBlendAdvancedPremul(src, dst, 7u); }",
            BlendMode.COLOR_BURN to "case 20u: { return kanvasBlendAdvancedPremul(src, dst, 8u); }",
            BlendMode.HARD_LIGHT to "case 21u: { return kanvasBlendAdvancedPremul(src, dst, 9u); }",
            BlendMode.SOFT_LIGHT to "case 22u: { return kanvasBlendAdvancedPremul(src, dst, 10u); }",
            BlendMode.DIFFERENCE to "case 23u: { return kanvasBlendAdvancedPremul(src, dst, 5u); }",
            BlendMode.EXCLUSION to "case 24u: { return kanvasBlendAdvancedPremul(src, dst, 6u); }",
            BlendMode.HUE to "case 25u: { return kanvasBlendAdvancedPremul(src, dst, 11u); }",
            BlendMode.SATURATION to "case 26u: { return kanvasBlendAdvancedPremul(src, dst, 12u); }",
            BlendMode.COLOR to "case 27u: { return kanvasBlendAdvancedPremul(src, dst, 13u); }",
            BlendMode.LUMINOSITY to "case 28u: { return kanvasBlendAdvancedPremul(src, dst, 14u); }",
        )
    }
}
