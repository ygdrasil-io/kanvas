@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.render.ir.CompositionDomain
import org.graphiks.math.color.ColorARGB

/** Independent one-pixel composition oracle for the bounded W7 Surface contract. */
internal object W7CompositionCpuOracle {
    private val zero = WgslFloatEnvelopeV1Oracle.Interval.ZERO

    /**
     * W7's multi-target composition has one fixed-function store per target
     * write.  Those independent stores are deliberately not squeezed through
     * W5f's historical two-adjacent-code [DrawResult.Bounded] gate: this test
     * only envelope retains every code admitted by the existing primitives.
     */
    internal class CompositionEnvelope private constructor(
        internal val channels: List<Set<Int>>,
        internal val storeTrace: List<List<Set<Int>>>,
    ) {
        init {
            require(channels.size == 4)
            require(channels.all { it.isNotEmpty() && it.all { code -> code in 0..255 } })
            require(storeTrace.isNotEmpty())
        }

        internal fun withStoreTrace(trace: List<List<Set<Int>>>): CompositionEnvelope =
            CompositionEnvelope(channels, trace)

        companion object {
            internal fun stored(codes: List<Set<Int>>): CompositionEnvelope {
                val copied = codes.map { it.toSet() }
                return CompositionEnvelope(copied, listOf(copied))
            }
        }
    }

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
        return WgslFloatEnvelopeV1Oracle.nativeSrcOver(source, destination)
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
    ): CompositionEnvelope {
        val codes = value.mapIndexed { channel, component ->
            if (channel == 3 || domain == CompositionDomain.SRGB_ENCODED)
                WgslFloatEnvelopeV1Oracle.unormStoreCodes(component)
            else WgslFloatEnvelopeV1Oracle.srgbStoreCodes(component)
        }
        return CompositionEnvelope.stored(codes)
    }

    fun storedSample(
        value: CompositionEnvelope,
        domain: CompositionDomain,
    ): Array<WgslFloatEnvelopeV1Oracle.Interval> {
        val linear = WgslFloatEnvelopeV1Oracle.decodeStoredCodes(value.channels)
        return Array(4) { channel ->
            if (domain == CompositionDomain.LINEAR) linear[channel]
            else WgslFloatEnvelopeV1Oracle.unormCodeEnvelope(value.channels[channel])
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

    fun trace(vararg stores: CompositionEnvelope): CompositionEnvelope {
        require(stores.isNotEmpty())
        return stores.last().withStoreTrace(stores.flatMap { it.storeTrace })
    }

    fun swizzle(value: CompositionEnvelope, format: PixelFormat): CompositionEnvelope {
        if (format == PixelFormat.RGBA8) return value
        val order = listOf(2, 1, 0, 3)
        return CompositionEnvelope.stored(order.map(value.channels::get)).withStoreTrace(
            value.storeTrace.map { store -> order.map(store::get) },
        )
    }

    fun assertAdmits(expected: CompositionEnvelope, observed: UByteArray) {
        require(observed.size == 4)
        expected.channels.forEachIndexed { channel, codes ->
            require(observed[channel].toInt() in codes) {
                "channel=$channel observed=${observed[channel]} expected=$codes trace=${expected.storeTrace}"
            }
        }
    }
}
