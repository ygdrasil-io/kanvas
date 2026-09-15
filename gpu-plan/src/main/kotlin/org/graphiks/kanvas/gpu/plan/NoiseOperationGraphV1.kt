package org.graphiks.kanvas.gpu.plan

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import org.graphiks.kanvas.gpu.plan.ColorOperationGraphV1.Predicate as P
import org.graphiks.kanvas.gpu.plan.ColorOperationGraphV1.Scalar as S
import org.graphiks.math.geometry.SizeI32

/** Captured scalar decisions; never part of a program's structural identity. */
internal class NoiseParametersV1(
    baseX: Float, baseY: Float, val octavesI32: Int, seedI32: Int,
    tile: SizeI32?, val fractal: Boolean,
) {
    val normalizedSeedI32: Int = normalizeNoiseSeedI32(seedI32)
    val frequencyXF32: Float
    val frequencyYF32: Float
    val periodX: BigInteger
    val periodY: BigInteger
    val stitched: Boolean
    val integralPhaseBoundI32: Int

    init {
        require(baseX.isFinite() && baseY.isFinite() && baseX >= 0f && baseY >= 0f && octavesI32 in 0..255) {
            W5gPlanDiagnostics.NoiseParameters
        }
        require(tile == null || tile.width >= 0 && tile.height >= 0) { W5gPlanDiagnostics.NoiseTile }
        stitched = tile != null && tile.width > 0 && tile.height > 0
        fun adjusted(base: Float, size: Int): Pair<Float, BigInteger> {
            if (base == 0f || octavesI32 == 0) return base to BigInteger.ZERO
            val tileF32 = size.toFloat()
            val product = tileF32 * base
            require(product.isFinite()) { W5gPlanDiagnostics.NoiseNumericDomainUnbounded }
            val low = kotlin.math.floor(product.toDouble()).toFloat() / tileF32
            val high = kotlin.math.ceil(product.toDouble()).toFloat() / tileF32
            val frequency = if (low != 0f && base / low < high / base) low else high
            val periodProduct = tileF32 * frequency
            require(frequency.isFinite() && periodProduct.isFinite() && periodProduct >= 0f) {
                W5gPlanDiagnostics.NoiseNumericDomainUnbounded
            }
            val period = BigDecimal(periodProduct.toDouble()).setScale(0, RoundingMode.HALF_UP).toBigIntegerExact()
            require(period.signum() >= 0 && period.bitLength() <= 128) { W5gPlanDiagnostics.NoiseNumericDomainUnbounded }
            return frequency to period
        }
        val x = if (stitched) adjusted(baseX, requireNotNull(tile).width) else baseX to BigInteger.ZERO
        val y = if (stitched) adjusted(baseY, requireNotNull(tile).height) else baseY to BigInteger.ZERO
        frequencyXF32 = x.first; periodX = x.second
        frequencyYF32 = y.first; periodY = y.second
        integralPhaseBoundI32 = maxOf(phaseBound(frequencyXF32), phaseBound(frequencyYF32))
    }

    companion object {
        fun phaseBound(frequency: Float): Int {
            require(frequency.isFinite() && frequency >= 0f) { W5gPlanDiagnostics.NoiseParameters }
            if (frequency == 0f) return 0
            val bits = frequency.toRawBits()
            val exponent = (bits ushr 23) and 255
            val significand = (bits and 0x7fffff) or if (exponent == 0) 0 else 0x800000
            val leastExponent = (if (exponent == 0) -149 else exponent - 150) +
                Integer.numberOfTrailingZeros(significand)
            return minOf(149, maxOf(0, 25 - leastExponent))
        }
    }
}

/**
 * Code-shaped bounded state region. The common color proof and emitter consume
 * these exact scalar operations; this class neither evaluates noise nor owns bytes.
 */
public class NoiseOperationGraphV1 internal constructor(
    public val ownerNodeIndexI32: Int,
    public val wordOffsetU32: Long,
    public val localX: S,
    public val localY: S,
) {
    public class Phase internal constructor(public val q: S)
    public enum class PhaseKind { FLOOR, FRACTION }
    public class Corner internal constructor(public val phase: Phase, public val offsetI32: Int,
        public val periodWordOffsetU32: Long) {
        init { require(offsetI32 in 0..1 && periodWordOffsetU32 >= 0L) }
    }
    public class GradientRead internal constructor(
        public val ownerNodeIndexI32: Int,
        public val tableRangeWordOffsetU32: Long,
        public val x: Corner, public val y: Corner,
        public val channelI32: Int, public val axisI32: Int,
    ) {
        init { require(channelI32 in 0..3 && axisI32 in 0..1) }
    }

    public val states: List<S.NoiseStateF32> = List(7) { S.NoiseStateF32(it) }
    public val xPhase: Phase = Phase(states[0])
    public val yPhase: Phase = Phase(states[1])
    public val integral: S.NoiseIntegralF32 = S.NoiseIntegralF32(xPhase, yPhase)
    public val initialState: List<S>
    public val nextState: List<S>
    public val outputs: List<S>
    internal val originalFractions: List<S.NoisePhaseComponent>
    internal val originalGradientReads: List<S.NoiseGradientU16>
    internal val originalSmooths: List<S.Multiply>
    internal val originalLerps: List<S.Add>
    internal val originalSamples: List<S>
    public val requestedOctavesWordOffsetU32: Long get() = wordOffsetU32 + 2L
    public val tableRangeWordOffsetU32: Long get() = wordOffsetU32 + 4L

    init {
        require(ownerNodeIndexI32 >= 0 && wordOffsetU32 in 0L..UInt.MAX_VALUE.toLong() - 15L)
        fun c(value: Float): S = ColorOperationGraphV1.constant(value)
        val zero = c(0f); val one = c(1f); val half = c(.5f)
        val isIntegral = P.Equal(integral, one)
        val isFractal = P.UniformU32Equal(wordOffsetU32 + 3L, 1u)
        initialState = listOf(S.Multiply(S.Add(localX, half), S.DynamicF32(wordOffsetU32)),
            S.Multiply(S.Add(localY, half), S.DynamicF32(wordOffsetU32 + 1L)), one, zero, zero, zero, zero)
        val fx = S.NoisePhaseComponent(xPhase, PhaseKind.FRACTION)
        val fy = S.NoisePhaseComponent(yPhase, PhaseKind.FRACTION)
        originalFractions = listOf(fx, fy)
        val reads = mutableListOf<S.NoiseGradientU16>()
        fun smooth(f: S) = S.Multiply(S.Multiply(f, f), S.Subtract(c(3f), S.Multiply(c(2f), f)))
        val sx = smooth(fx); val sy = smooth(fy)
        originalSmooths = listOf(sx,sy)
        val xs = List(2) { Corner(xPhase, it, wordOffsetU32 + 8L) }
        val ys = List(2) { Corner(yPhase, it, wordOffsetU32 + 12L) }
        val lerps = mutableListOf<S.Add>()
        fun lerp(a: S, b: S, t: S): S = S.Add(a, S.Multiply(S.Subtract(b, a), t)).also(lerps::add)
        fun gradient(channel: Int, x: Int, y: Int, axis: Int): S = S.Subtract(S.Divide(
            S.NoiseGradientU16(GradientRead(ownerNodeIndexI32, tableRangeWordOffsetU32, xs[x], ys[y], channel, axis)).also(reads::add),
            c(32767.5f)), one)
        fun dot(channel: Int, x: Int, y: Int): S = S.Add(
            S.Multiply(gradient(channel, x, y, 0), if (x == 0) fx else S.Subtract(fx, one)),
            S.Multiply(gradient(channel, x, y, 1), if (y == 0) fy else S.Subtract(fy, one)))
        originalSamples = List(4) { channel ->
            lerp(lerp(dot(channel, 0, 0), dot(channel, 1, 0), sx),
                lerp(dot(channel, 0, 1), dot(channel, 1, 1), sx), sy)
        }
        originalGradientReads = immutableList(reads)
        originalLerps = immutableList(lerps)
        val terms = originalSamples.map { noise ->
            S.LazyBranch(isIntegral, zero, S.LazyBranch(isFractal, noise, S.Abs(noise)))
        }
        nextState = listOf(S.LazyBranch(isIntegral, states[0], S.Multiply(states[0], c(2f))),
            S.LazyBranch(isIntegral, states[1], S.Multiply(states[1], c(2f))),
            S.Multiply(states[2], half)) + List(4) { channel ->
            // Even an integral tail keeps its original multiply and accumulator Add.
            S.Add(states[channel + 3], S.Multiply(terms[channel], states[2]))
        }
        val clamped = List(4) { channel -> S.Clamp01(S.LazyBranch(isFractal,
            S.Add(S.Multiply(states[channel + 3], half), half), states[channel + 3])) }
        outputs = List(4) { if (it == 3) clamped[3] else S.Multiply(clamped[it], clamped[3]) }
    }

    internal fun rebind(offsetU32: Long, bind: (S) -> S): NoiseOperationGraphV1 =
        NoiseOperationGraphV1(ownerNodeIndexI32, Math.addExact(wordOffsetU32, offsetU32), bind(localX), bind(localY))

    public companion object {
        public const val HEADER_BYTES_I64: Long = 64L
        public const val OCTAVE_BOUND_I32: Int = 255
    }
}
