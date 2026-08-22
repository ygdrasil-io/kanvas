package org.graphiks.math.color

import kotlin.ConsistentCopyVisibility
import kotlin.math.abs
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
            g = 1f, a = 1f, b = 0f, c = 1f, d = 0f, e = 0f, f = 0f,
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
            return pqEotf(encoded.toDouble()).toFloat()
        }
    }

    private data object Hlg : ColorTransferFunction {
        override fun toLinear(encoded: Float): Float {
            require(encoded in 0f..1f) { "HLG input must be in [0, 1], got $encoded" }
            return hlgInverseOetf(encoded.toDouble()).toFloat()
        }
    }
}

/** Converts a linear-light component through an ICC type-4 parametric curve. */
public fun ColorTransferFunction.Parametric.toEncoded(linear: Float): Float {
    val lowerLimit = c * d + f
    val upperLimit = toLinear(d)
    return when {
        linear < lowerLimit && c > 0f -> (linear - f) / c
        linear < upperLimit -> d
        else -> ((linear - e).pow(1f / g) - b) / a
    }
}

/** Returns whether every ICC parametric coefficient differs by at most [tolerance]. */
public fun ColorTransferFunction.Parametric.isNear(
    other: ColorTransferFunction.Parametric,
    tolerance: Float,
): Boolean {
    require(tolerance.isFinite() && tolerance >= 0f) { "tolerance must be finite and non-negative" }
    return listOf(
        g to other.g,
        a to other.a,
        b to other.b,
        c to other.c,
        d to other.d,
        e to other.e,
        f to other.f,
    ).all { (left, right) -> abs(left - right) <= tolerance }
}
