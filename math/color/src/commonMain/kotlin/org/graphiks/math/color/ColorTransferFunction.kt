package org.graphiks.math.color

import kotlin.ConsistentCopyVisibility
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

/**
 * Converts a normalized encoded color component to a linear-light value.
 *
 * ICC parametric curves, PQ (ST 2084), and HLG use distinct equations and
 * are represented by distinct implementations instead of sharing an
 * incompatible seven-parameter model.
 */
public sealed interface ColorTransferFunction {
    /** Converts [encoded] to a linear-light component. */
    public fun toLinear(encoded: Float): Float

    /**
     * ICC parametric curve type 4: `y = (a*x + b)^g + e` for `x >= d`,
     * and `y = c*x + f` for `x < d`.
     */
    @ConsistentCopyVisibility
    public data class Parametric internal constructor(
        public val g: Float,
        public val a: Float,
        public val b: Float,
        public val c: Float,
        public val d: Float,
        public val e: Float,
        public val f: Float,
    ) : ColorTransferFunction {
        override fun toLinear(encoded: Float): Float =
            if (encoded >= d) {
                (a * encoded + b).pow(g) + e
            } else {
                c * encoded + f
            }
    }

    public companion object {
        /** sRGB encoded-to-linear transfer function. */
        public val sRgb: Parametric = Parametric(
            g = 2.4f, a = 1f / 1.055f, b = 0.055f / 1.055f,
            c = 1f / 12.92f, d = 0.04045f, e = 0f, f = 0f,
        )

        /** Linear transfer function (identity). */
        public val linear: Parametric = Parametric(
            g = 1f, a = 1f, b = 0f, c = 0f, d = 0f, e = 0f, f = 0f,
        )

        /** Rec. 2020 encoded-to-linear transfer function. */
        public val rec2020: Parametric = Parametric(
            g = 2.2222222f, a = 0.9096724f, b = 0.0903276f,
            c = 1f / 4.5f, d = 0.0812429f, e = 0f, f = 0f,
        )

        /** PQ (ST 2084) normalized electro-optical transfer function. */
        public val pq: ColorTransferFunction = Pq

        /** HLG reference inverse OETF, from encoded signal to scene-linear light. */
        public val hlg: ColorTransferFunction = Hlg

        /** Creates an ICC type-4 parametric transfer function. */
        public fun parametric(
            g: Float,
            a: Float,
            b: Float,
            c: Float,
            d: Float,
            e: Float,
            f: Float,
        ): Parametric = Parametric(g, a, b, c, d, e, f)
    }

    private data object Pq : ColorTransferFunction {
        override fun toLinear(encoded: Float): Float {
            require(encoded in 0f..1f) { "PQ input must be in [0, 1], got $encoded" }
            val signal = encoded.toDouble().pow(1.0 / PQ_M2)
            val numerator = max(signal - PQ_C1, 0.0)
            val denominator = PQ_C2 - PQ_C3 * signal
            return (numerator / denominator).pow(1.0 / PQ_M1).toFloat()
        }
    }

    private data object Hlg : ColorTransferFunction {
        override fun toLinear(encoded: Float): Float {
            require(encoded in 0f..1f) { "HLG input must be in [0, 1], got $encoded" }
            val signal = encoded.toDouble()
            return if (signal <= 0.5) {
                (signal * signal / 3.0).toFloat()
            } else {
                ((exp((signal - HLG_C) / HLG_A) + HLG_B) / 12.0).toFloat()
            }
        }
    }
}

private const val PQ_M1: Double = 2610.0 / 16384.0
private const val PQ_M2: Double = 2523.0 / 32.0
private const val PQ_C1: Double = 3424.0 / 4096.0
private const val PQ_C2: Double = 2413.0 / 128.0
private const val PQ_C3: Double = 2392.0 / 128.0

private const val HLG_A: Double = 0.17883277
private const val HLG_B: Double = 0.28466892
private const val HLG_C: Double = 0.55991073
