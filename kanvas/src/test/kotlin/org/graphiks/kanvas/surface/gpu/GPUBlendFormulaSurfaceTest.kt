package org.graphiks.kanvas.surface.gpu

import java.util.stream.Stream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.renderer.materials.GPUBlendFormulaLibrary
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.alphaByte
import org.graphiks.kanvas.types.blueByte
import org.graphiks.kanvas.types.greenByte
import org.graphiks.kanvas.types.redByte
import org.graphiks.kanvas.types.Rect
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

@OptIn(ExperimentalUnsignedTypes::class)
class GPUBlendFormulaSurfaceTest {
    @AfterEach
    fun disposeRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

    @org.junit.jupiter.api.Test
    fun everyDestinationReadModeHasAUniqueIndex() {
        val modes = GPUBlendMode.entries.filter { mode ->
            mode.canonicalBlendPlan().destinationReadRequirement ==
                org.graphiks.kanvas.gpu.renderer.passes.GPUBlendDestinationReadRequirement.DestinationTextureRequired
        }
        val indices = modes.map { destinationReadBlendModeIndex(it) }

        assertEquals(14, modes.size)
        assertTrue(indices.all { it != null })
        assertEquals(14, indices.filterNotNull().toSet().size)
        assertEquals(0, destinationReadBlendModeIndex(GPUBlendMode.MULTIPLY))
        assertEquals(6, destinationReadBlendModeIndex(GPUBlendMode.EXCLUSION))
        assertEquals(7, destinationReadBlendModeIndex(GPUBlendMode.COLOR_DODGE))
        assertEquals(14, destinationReadBlendModeIndex(GPUBlendMode.LUMINOSITY))
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("sourceDestinationCases")
    fun `every formula route matches the CPU pixel oracle`(case: SourceDestinationCase) {
        val session = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(session != null, "GPU backend unavailable in current environment")
        val before = session!!.runtimeTelemetry

        destinationReadBlendModes.forEach { mode ->
            FormulaRoute.entries.forEach { route ->
                val result = render(mode, case.source, case.destination, route)

                assertPixelNear(
                    pixels = result.pixels,
                    x = 16,
                    y = 16,
                    expected = expectedBlend(case.source, case.destination, mode),
                    tolerance = 2,
                )
                assertEquals(0, result.diagnostics.fatalCount, "$mode/$route: ${result.diagnostics.entries}")
            }
        }

        val after = GPUBackendRuntimeFactory.createOrNull()!!.runtimeTelemetry
        assertEquals(before.destinationReadbackSnapshots, after.destinationReadbackSnapshots)
    }

    private fun render(mode: BlendMode, source: Color, destination: Color, route: FormulaRoute) =
        Surface(width = 32, height = 32).run {
            canvas {
                drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(destination))
                if (route != FormulaRoute.Direct) {
                    save()
                    clipRect(
                        Rect(8f, 8f, 24f, 24f),
                        ClipOp.INTERSECT,
                        antiAlias = route == FormulaRoute.AlphaMask,
                    )
                }
                drawRect(
                    Rect(4f, 4f, 28f, 28f),
                    Paint.fill(source).copy(antiAlias = false, blendMode = mode),
                )
                if (route != FormulaRoute.Direct) restore()
            }
            render()
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

    private enum class FormulaRoute { Direct, Scissor, AlphaMask }

    data class SourceDestinationCase(
        val label: String,
        val source: Color,
        val destination: Color,
    ) {
        override fun toString(): String = label
    }

    private companion object {
        val destinationReadBlendModes = listOf(
            BlendMode.MULTIPLY,
            BlendMode.SCREEN,
            BlendMode.OVERLAY,
            BlendMode.DARKEN,
            BlendMode.LIGHTEN,
            BlendMode.COLOR_DODGE,
            BlendMode.COLOR_BURN,
            BlendMode.HARD_LIGHT,
            BlendMode.SOFT_LIGHT,
            BlendMode.DIFFERENCE,
            BlendMode.EXCLUSION,
            BlendMode.HUE,
            BlendMode.SATURATION,
            BlendMode.COLOR,
            BlendMode.LUMINOSITY,
        )

        @JvmStatic
        fun sourceDestinationCases(): Stream<SourceDestinationCase> = Stream.of(
            SourceDestinationCase(
                "opaque source and destination",
                Color.fromArgb(255, 192, 64, 32),
                Color.fromArgb(255, 64, 128, 192),
            ),
            SourceDestinationCase(
                "W3C color dodge prioritizes a black backdrop",
                Color.WHITE,
                Color.BLACK,
            ),
            SourceDestinationCase(
                "W3C color burn prioritizes a white backdrop",
                Color.BLACK,
                Color.WHITE,
            ),
            SourceDestinationCase(
                "transparent source",
                Color.fromArgb(0, 192, 64, 32),
                Color.fromArgb(255, 64, 128, 192),
            ),
            SourceDestinationCase(
                "transparent destination",
                Color.fromArgb(255, 192, 64, 32),
                Color.fromArgb(0, 64, 128, 192),
            ),
            SourceDestinationCase(
                "50 percent alpha source and destination",
                Color.fromArgb(128, 192, 64, 32),
                Color.fromArgb(128, 64, 128, 192),
            ),
        )
    }
}
