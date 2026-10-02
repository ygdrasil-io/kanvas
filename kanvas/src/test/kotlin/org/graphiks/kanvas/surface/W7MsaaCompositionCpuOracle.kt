@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import org.graphiks.kanvas.render.ir.CompositionDomain
import org.graphiks.math.color.ColorARGB

/** Independent four-sample composition and resolve model for root Path witnesses. */
internal object W7MsaaCompositionCpuOracle {
    // WebGPU rasterization, commit 454d33cfdf6b8c8a1efafe490623cf0905e6c245.
    // These positions are test authority; production geometry and coverage are not consulted.
    private val positions = listOf(
        Point(.375f, .125f), Point(.875f, .375f),
        Point(.125f, .625f), Point(.625f, .875f),
    )

    internal data class Point(val x: Float, val y: Float)
    internal data class Pixel(val x: Int, val y: Int)
    internal data class Mask(val bits: Int) {
        val coveredCount: Int get() = Integer.bitCount(bits)
        fun covers(sample: Int): Boolean = bits and (1 shl sample) != 0
    }

    internal data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        fun mask(pixel: Pixel): Mask = Mask(positions.mapIndexedNotNull { index, point ->
            val x = pixel.x + point.x
            val y = pixel.y + point.y
            index.takeIf { x >= left && x < right && y >= top && y < bottom }
        }.fold(0) { bits, index -> bits or (1 shl index) })
    }

    internal data class Stroke(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val width: Float) {
        init { require(width > 0f && ((x1 == x2) xor (y1 == y2))) }
        fun mask(pixel: Pixel): Mask = if (x1 == x2) {
            Box(x1 - width / 2f, minOf(y1, y2), x1 + width / 2f, maxOf(y1, y2)).mask(pixel)
        } else {
            Box(minOf(x1, x2), y1 - width / 2f, maxOf(x1, x2), y1 + width / 2f).mask(pixel)
        }
    }

    internal class Samples private constructor(
        private val sampleValues: List<W7CompositionCpuOracle.CompositionEnvelope>,
        private val writes: List<List<Set<Int>>>,
    ) {
        fun draw(mask: Mask, color: ColorARGB, domain: CompositionDomain): Samples {
            val source = W7CompositionCpuOracle.solid(color, domain)
            val next = sampleValues.mapIndexed { index, stored ->
                if (!mask.covers(index)) stored else {
                    val destination = W7CompositionCpuOracle.storedSample(stored, domain)
                    W7CompositionCpuOracle.store(W7CompositionCpuOracle.srcOver(source, destination), domain)
                }
            }
            val drawWrites = next.mapIndexedNotNull { index, stored -> stored.channels.takeIf { mask.covers(index) } }
            return Samples(next, writes + drawWrites)
        }

        fun resolve(domain: CompositionDomain): W7CompositionCpuOracle.CompositionEnvelope {
            val decoded = sampleValues.map { W7CompositionCpuOracle.storedSample(it, domain) }
            val average = Array(4) { channel ->
                // Exact rational mean of the decoded sample intervals, not a WGSL operation.
                // Native resolve error is absent; tests can qualify only these witness outputs
                // empirically on the observed backend, with no general numerical bound.
                val values = decoded.map { it[channel] }
                val four = java.math.BigDecimal(4)
                WgslFloatEnvelopeV1Oracle.Interval(
                    values.fold(java.math.BigDecimal.ZERO) { sum, value -> sum + value.lower }.divide(four),
                    values.fold(java.math.BigDecimal.ZERO) { sum, value -> sum + value.upper }.divide(four),
                )
            }
            val resolved = W7CompositionCpuOracle.store(average, domain)
            return resolved.withStoreTrace(writes + resolved.storeTrace)
        }

        companion object {
            fun clear(): Samples = Samples(List(4) { W7CompositionCpuOracle.clear() }, emptyList())
        }
    }

    /** A transparent/hard-white initial target is composed at each sample, then resolved once. */
    fun pixel(
        pixel: Pixel,
        domain: CompositionDomain,
        background: ColorARGB?,
        draws: List<Pair<Mask, ColorARGB>>,
    ): W7CompositionCpuOracle.CompositionEnvelope {
        var samples = Samples.clear()
        if (background != null) samples = samples.draw(Mask(0b1111), background, domain)
        for ((mask, color) in draws) samples = samples.draw(mask, color, domain)
        return samples.resolve(domain)
    }

    fun strokeMask(stroke: Stroke, pixel: Pixel): Mask = stroke.mask(pixel)
    fun boxMask(box: Box, pixel: Pixel): Mask = box.mask(pixel)

    /** Resolve-per-draw scalar alternative used only to prove the correlation witnesses. */
    fun scalarResolvePerDraw(
        domain: CompositionDomain,
        background: ColorARGB,
        source: ColorARGB,
        coverage: Float,
        drawCount: Int,
    ): W7CompositionCpuOracle.CompositionEnvelope {
        require(coverage in 0f..1f && drawCount > 0)
        var stored = W7CompositionCpuOracle.drawOnClear(W7CompositionCpuOracle.solid(background, domain), domain)
        val base = W7CompositionCpuOracle.solid(source, domain)
        repeat(drawCount) {
            val covered = Array(4) { channel ->
                W7FloatOps.multiply(base[channel], WgslFloatEnvelopeV1Oracle.Interval.input(coverage))
            }
            stored = W7CompositionCpuOracle.store(W7CompositionCpuOracle.srcOver(
                covered, W7CompositionCpuOracle.storedSample(stored, domain),
            ), domain)
        }
        return stored
    }

    fun resolvePerDrawComplement(domain: CompositionDomain): W7CompositionCpuOracle.CompositionEnvelope {
        var stored = W7CompositionCpuOracle.drawOnClear(
            W7CompositionCpuOracle.solid(ColorARGB.White, domain), domain,
        )
        val halfBlack = Array(4) { channel ->
            val black = W7CompositionCpuOracle.solid(ColorARGB.Black, domain)[channel]
            W7FloatOps.multiply(black, WgslFloatEnvelopeV1Oracle.Interval.input(.5f))
        }
        repeat(2) {
            stored = W7CompositionCpuOracle.store(W7CompositionCpuOracle.srcOver(
                halfBlack, W7CompositionCpuOracle.storedSample(stored, domain),
            ), domain)
        }
        return stored
    }

    fun assertDisjoint(a: W7CompositionCpuOracle.CompositionEnvelope, b: W7CompositionCpuOracle.CompositionEnvelope) {
        require(a.channels.indices.any { channel -> a.channels[channel].intersect(b.channels[channel]).isEmpty() }) {
            "expected distinct code sets: ${a.channels} vs ${b.channels}"
        }
    }

}

/** Public arithmetic access remains the existing interval oracle's exact policy. */
private object W7FloatOps {
    fun multiply(a: WgslFloatEnvelopeV1Oracle.Interval, b: WgslFloatEnvelopeV1Oracle.Interval) =
        WgslFloatEnvelopeV1Oracle.gradientMultiply(a, b)
}
