@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import java.math.BigDecimal
import java.math.RoundingMode
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.render.ir.CompositionDomain
import org.graphiks.math.color.ColorARGB

/** Independent one-pixel composition oracle for the bounded W7 Surface contract. */
internal object W7CompositionCpuOracle {
    private val zero = WgslFloatEnvelopeV1Oracle.Interval.ZERO
    private val one = WgslFloatEnvelopeV1Oracle.Interval.ONE

    fun solid(color: ColorARGB, domain: CompositionDomain): Array<WgslFloatEnvelopeV1Oracle.Interval> {
        val alpha = WgslFloatEnvelopeV1Oracle.Interval.input(color.alpha / 255f)
        val channels = intArrayOf(color.red, color.green, color.blue)
        return Array(4) { channel ->
            if (channel == 3) alpha else {
                val encoded = WgslFloatEnvelopeV1Oracle.Interval.input(channels[channel] / 255f)
                val component = if (domain == CompositionDomain.LINEAR)
                    WgslFloatEnvelopeV1Oracle.imageSrgbToLinear(encoded) else encoded
                WgslFloatEnvelopeV1Oracle.gradientMultiply(component, alpha)
            }
        }
    }

    fun srcOver(
        source: Array<WgslFloatEnvelopeV1Oracle.Interval>,
        destination: Array<WgslFloatEnvelopeV1Oracle.Interval>,
    ): Array<WgslFloatEnvelopeV1Oracle.Interval> {
        val inverseAlpha = WgslFloatEnvelopeV1Oracle.gradientSubtract(one, source[3])
        return Array(4) { channel -> WgslFloatEnvelopeV1Oracle.gradientAdd(
            source[channel], WgslFloatEnvelopeV1Oracle.gradientMultiply(destination[channel], inverseAlpha),
        ) }
    }

    /** Restore opacity scales premultiplied RGB and alpha exactly once before SrcOver. */
    fun opacity(
        value: Array<WgslFloatEnvelopeV1Oracle.Interval>,
        alpha: Int,
    ): Array<WgslFloatEnvelopeV1Oracle.Interval> {
        val factor = WgslFloatEnvelopeV1Oracle.Interval.input(alpha / 255f)
        return Array(4) { channel -> WgslFloatEnvelopeV1Oracle.gradientMultiply(value[channel], factor) }
    }

    /** Models the UNORM8 store at every target write; no production evaluator participates. */
    fun store(
        value: Array<WgslFloatEnvelopeV1Oracle.Interval>,
        domain: CompositionDomain,
    ): WgslFloatEnvelopeV1Oracle.DrawResult {
        val codes = value.mapIndexed { channel, component -> setOf(quantize(
            if (channel == 3 || domain == CompositionDomain.SRGB_ENCODED) component
            else WgslFloatEnvelopeV1Oracle.filterLinearToSrgb(component),
        )) }
        return WgslFloatEnvelopeV1Oracle.DrawResult.Bounded(
            codes,
            WgslFloatEnvelopeV1Oracle.AttachmentState(Array(4) { zero }),
        )
    }

    fun storedSample(
        value: WgslFloatEnvelopeV1Oracle.DrawResult,
        domain: CompositionDomain,
    ): Array<WgslFloatEnvelopeV1Oracle.Interval> {
        val bounded = value as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded
        return Array(4) { channel ->
            val code = bounded.channels[channel].single()
            val encoded = WgslFloatEnvelopeV1Oracle.Interval.input(code / 255f)
            if (channel == 3 || domain == CompositionDomain.SRGB_ENCODED) encoded
            else WgslFloatEnvelopeV1Oracle.imageSrgbToLinear(encoded)
        }
    }

    /** SOURCE_SPACE image replay is decoded only when the destination is LINEAR. */
    fun sourceSpacePremul(
        bytes: ByteArray,
        colorType: ColorType,
        alphaType: AlphaType,
        domain: CompositionDomain,
    ): Array<WgslFloatEnvelopeV1Oracle.Interval> {
        require(bytes.size == 4 && alphaType == AlphaType.PREMUL)
        val raw = bytes.map { WgslFloatEnvelopeV1Oracle.imageUnorm8(it.toInt() and 255) }
        val alpha = raw[3]
        if (alpha == zero) return Array(4) { zero }
        val order = if (colorType == ColorType.BGRA_8888) listOf(raw[2], raw[1], raw[0]) else raw.take(3)
        return Array(4) { channel ->
            if (channel == 3) alpha else if (domain == CompositionDomain.SRGB_ENCODED) order[channel] else {
                val straight = WgslFloatEnvelopeV1Oracle.gradientDivide(order[channel], alpha)
                WgslFloatEnvelopeV1Oracle.gradientMultiply(WgslFloatEnvelopeV1Oracle.imageSrgbToLinear(straight), alpha)
            }
        }
    }

    fun swizzle(value: WgslFloatEnvelopeV1Oracle.DrawResult, format: PixelFormat): WgslFloatEnvelopeV1Oracle.DrawResult {
        if (format == PixelFormat.RGBA8) return value
        val bounded = value as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded
        return WgslFloatEnvelopeV1Oracle.DrawResult.Bounded(
            listOf(bounded.channels[2], bounded.channels[1], bounded.channels[0], bounded.channels[3]),
            WgslFloatEnvelopeV1Oracle.AttachmentState(Array(4) { zero }),
        )
    }

    private fun quantize(value: WgslFloatEnvelopeV1Oracle.Interval): Int {
        val midpoint = value.lower.add(value.upper).divide(BigDecimal.TWO)
            .coerceIn(BigDecimal.ZERO, BigDecimal.ONE)
        return midpoint.multiply(BigDecimal(255)).setScale(0, RoundingMode.HALF_UP).intValueExact()
    }
}
