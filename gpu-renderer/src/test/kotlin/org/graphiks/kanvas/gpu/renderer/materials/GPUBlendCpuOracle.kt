package org.graphiks.kanvas.gpu.renderer.materials

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode

internal data class BlendPremulColor(
    val r: Float,
    val g: Float,
    val b: Float,
    val a: Float,
) {
    init {
        require(listOf(r, g, b, a).all(Float::isFinite))
    }

    operator fun get(index: Int): Float = when (index) {
        0 -> r
        1 -> g
        2 -> b
        3 -> a
        else -> error("channel index must be in 0..3")
    }

    fun toArray(): FloatArray = floatArrayOf(r, g, b, a)
}

/** Independent test-only premultiplied reference; it consumes no production formula identifiers or WGSL. */
internal object GPUBlendCpuOracle {
    fun blend(
        mode: GPUBlendMode,
        source: BlendPremulColor,
        destination: BlendPremulColor,
        coverage: Float = 1f,
    ): BlendPremulColor {
        require(coverage in 0f..1f)
        val full = blendAtFullCoverage(mode, source, destination)
        return interpolate(destination, full, floatArrayOf(coverage, coverage, coverage))
    }

    fun blendLcd(
        mode: GPUBlendMode,
        source: BlendPremulColor,
        destination: BlendPremulColor,
        coverageRgb: FloatArray,
    ): BlendPremulColor {
        require(coverageRgb.size == 3)
        require(coverageRgb.all { it in 0f..1f })
        return interpolate(destination, blendAtFullCoverage(mode, source, destination), coverageRgb)
    }

    fun blendAtFullCoverage(
        mode: GPUBlendMode,
        source: BlendPremulColor,
        destination: BlendPremulColor,
    ): BlendPremulColor {
        val s = source.toArray()
        val d = destination.toArray()
        val result = when (mode) {
            GPUBlendMode.CLEAR -> FloatArray(4)
            GPUBlendMode.SRC -> s
            GPUBlendMode.DST -> d
            GPUBlendMode.SRC_OVER -> combine(s, d) { sc, dc -> sc + dc * (1f - source.a) }
            GPUBlendMode.DST_OVER -> combine(s, d) { sc, dc -> dc + sc * (1f - destination.a) }
            GPUBlendMode.SRC_IN -> FloatArray(4) { s[it] * destination.a }
            GPUBlendMode.DST_IN -> FloatArray(4) { d[it] * source.a }
            GPUBlendMode.SRC_OUT -> FloatArray(4) { s[it] * (1f - destination.a) }
            GPUBlendMode.DST_OUT -> FloatArray(4) { d[it] * (1f - source.a) }
            GPUBlendMode.SRC_ATOP -> combine(s, d) { sc, dc -> sc * destination.a + dc * (1f - source.a) }
            GPUBlendMode.DST_ATOP -> combine(s, d) { sc, dc -> dc * source.a + sc * (1f - destination.a) }
            GPUBlendMode.XOR -> combine(s, d) { sc, dc ->
                sc * (1f - destination.a) + dc * (1f - source.a)
            }
            GPUBlendMode.PLUS -> combine(s, d) { sc, dc -> min(1f, sc + dc) }
            GPUBlendMode.MODULATE -> combine(s, d) { sc, dc -> sc * dc }
            GPUBlendMode.SCREEN,
            GPUBlendMode.MULTIPLY,
            GPUBlendMode.OVERLAY,
            GPUBlendMode.DARKEN,
            GPUBlendMode.LIGHTEN,
            GPUBlendMode.COLOR_DODGE,
            GPUBlendMode.COLOR_BURN,
            GPUBlendMode.HARD_LIGHT,
            GPUBlendMode.SOFT_LIGHT,
            GPUBlendMode.DIFFERENCE,
            GPUBlendMode.EXCLUSION,
            GPUBlendMode.HUE,
            GPUBlendMode.SATURATION,
            GPUBlendMode.COLOR,
            GPUBlendMode.LUMINOSITY,
            -> advanced(mode, source, destination)
        }
        return BlendPremulColor(result[0], result[1], result[2], result[3])
    }

    private fun interpolate(
        destination: BlendPremulColor,
        full: BlendPremulColor,
        coverageRgb: FloatArray,
    ): BlendPremulColor {
        val d = destination.toArray()
        val b = full.toArray()
        val alphaCandidates = FloatArray(3) { channel ->
            d[3] + coverageRgb[channel] * (b[3] - d[3])
        }
        return BlendPremulColor(
            r = d[0] + coverageRgb[0] * (b[0] - d[0]),
            g = d[1] + coverageRgb[1] * (b[1] - d[1]),
            b = d[2] + coverageRgb[2] * (b[2] - d[2]),
            a = alphaCandidates.maxOrNull()!!,
        )
    }

    private fun advanced(
        mode: GPUBlendMode,
        source: BlendPremulColor,
        destination: BlendPremulColor,
    ): FloatArray {
        if (source.a == 0f) return destination.toArray()
        val sourceColor = unpremul(source)
        val destinationColor = unpremul(destination)
        val blended = blendColor(mode, sourceColor, destinationColor)
        val rgb = FloatArray(3) { channel ->
            source[channel] * (1f - destination.a) +
                destination[channel] * (1f - source.a) +
                source.a * destination.a * blended[channel]
        }
        return floatArrayOf(rgb[0], rgb[1], rgb[2], source.a + destination.a * (1f - source.a))
    }

    private fun blendColor(mode: GPUBlendMode, source: FloatArray, destination: FloatArray): FloatArray {
        val separable = FloatArray(3) { channel ->
            val s = source[channel]
            val d = destination[channel]
            when (mode) {
                GPUBlendMode.MULTIPLY -> s * d
                GPUBlendMode.SCREEN -> s + d - s * d
                GPUBlendMode.OVERLAY -> if (d <= .5f) 2f * s * d else 1f - 2f * (1f - s) * (1f - d)
                GPUBlendMode.DARKEN -> min(s, d)
                GPUBlendMode.LIGHTEN -> max(s, d)
                GPUBlendMode.COLOR_DODGE -> if (d == 0f) 0f else if (s == 1f) 1f else min(1f, d / (1f - s))
                GPUBlendMode.COLOR_BURN -> if (d == 1f) 1f else if (s == 0f) 0f else 1f - min(1f, (1f - d) / s)
                GPUBlendMode.HARD_LIGHT -> if (s <= .5f) 2f * s * d else 1f - 2f * (1f - s) * (1f - d)
                GPUBlendMode.SOFT_LIGHT -> softLight(d, s)
                GPUBlendMode.DIFFERENCE -> abs(d - s)
                GPUBlendMode.EXCLUSION -> s + d - 2f * s * d
                GPUBlendMode.HUE,
                GPUBlendMode.SATURATION,
                GPUBlendMode.COLOR,
                GPUBlendMode.LUMINOSITY,
                -> 0f
                else -> error("$mode is not an advanced blend mode")
            }
        }
        return when (mode) {
            GPUBlendMode.HUE -> setLum(setSat(source, sat(destination)), lum(destination))
            GPUBlendMode.SATURATION -> setLum(setSat(destination, sat(source)), lum(destination))
            GPUBlendMode.COLOR -> setLum(source, lum(destination))
            GPUBlendMode.LUMINOSITY -> setLum(destination, lum(source))
            else -> separable
        }
    }

    private fun unpremul(color: BlendPremulColor): FloatArray =
        if (color.a == 0f) FloatArray(3) else floatArrayOf(color.r / color.a, color.g / color.a, color.b / color.a)

    private fun softLight(backdrop: Float, source: Float): Float = if (source <= .5f) {
        backdrop - (1f - 2f * source) * backdrop * (1f - backdrop)
    } else {
        val d = if (backdrop <= .25f) {
            ((16f * backdrop - 12f) * backdrop + 4f) * backdrop
        } else {
            sqrt(backdrop)
        }
        backdrop + (2f * source - 1f) * (d - backdrop)
    }

    private fun lum(color: FloatArray): Float = .3f * color[0] + .59f * color[1] + .11f * color[2]
    private fun sat(color: FloatArray): Float = color.maxOrNull()!! - color.minOrNull()!!

    private fun setSat(color: FloatArray, saturation: Float): FloatArray {
        val low = color.minOrNull()!!
        val high = color.maxOrNull()!!
        if (high == low) return FloatArray(3)
        return FloatArray(3) { channel -> (color[channel] - low) * saturation / (high - low) }
    }

    private fun setLum(color: FloatArray, luminosity: Float): FloatArray =
        clipColor(FloatArray(3) { channel -> color[channel] + luminosity - lum(color) })

    private fun clipColor(color: FloatArray): FloatArray {
        val luminosity = lum(color)
        val low = color.minOrNull()!!
        val high = color.maxOrNull()!!
        var result = color.copyOf()
        if (low < 0f && luminosity != low) {
            result = FloatArray(3) { channel ->
                luminosity + (result[channel] - luminosity) * luminosity / (luminosity - low)
            }
        }
        if (high > 1f && high != luminosity) {
            result = FloatArray(3) { channel ->
                luminosity + (result[channel] - luminosity) * (1f - luminosity) / (high - luminosity)
            }
        }
        return result
    }

    private inline fun combine(
        source: FloatArray,
        destination: FloatArray,
        operation: (Float, Float) -> Float,
    ): FloatArray = FloatArray(4) { channel -> operation(source[channel], destination[channel]) }
}
