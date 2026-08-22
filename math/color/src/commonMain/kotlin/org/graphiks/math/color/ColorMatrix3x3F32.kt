package org.graphiks.math.color

import kotlin.math.abs

/**
 * An immutable 3 × 3 RGB colour matrix stored in row-major order.
 *
 * Unlike a homogeneous geometry matrix, this matrix maps exactly three RGB
 * components and never performs a homogeneous divide.
 */
public class ColorMatrix3x3F32 private constructor(private val values: FloatArray) {
    /** Returns the row-major coefficient at [row], [column]. */
    public operator fun get(row: Int, column: Int): Float {
        require(row in 0..2 && column in 0..2) {
            "get($row, $column) out of range for 3x3 colour matrix"
        }
        return values[row * 3 + column]
    }

    /**
     * Maps the RGB components at [inputOffset] into [output] at [outputOffset].
     */
    public fun map(input: FloatArray, inputOffset: Int, output: FloatArray, outputOffset: Int) {
        require(inputOffset >= 0 && inputOffset + 3 <= input.size) {
            "input must contain three RGB components at offset $inputOffset"
        }
        require(outputOffset >= 0 && outputOffset + 3 <= output.size) {
            "output must contain room for three RGB components at offset $outputOffset"
        }
        val red = input[inputOffset]
        val green = input[inputOffset + 1]
        val blue = input[inputOffset + 2]
        repeat(3) { row ->
            val base = row * 3
            output[outputOffset + row] =
                values[base] * red + values[base + 1] * green + values[base + 2] * blue
        }
    }

    /** Returns `this × right`, so [right] maps an RGB vector first. */
    public fun concat(right: ColorMatrix3x3F32): ColorMatrix3x3F32 =
        ColorMatrix3x3F32(FloatArray(9) { index ->
            val row = index / 3
            val column = index % 3
            val base = row * 3
            values[base] * right.values[column] +
                values[base + 1] * right.values[3 + column] +
                values[base + 2] * right.values[6 + column]
        })

    /**
     * Returns the inverse using the colour-management numeric acceptance rule,
     * or `null` for singular, non-finite, or Float-overflowing results.
     */
    public fun inverseOrNull(): ColorMatrix3x3F32? {
        val a = values[0].toDouble()
        val b = values[1].toDouble()
        val c = values[2].toDouble()
        val d = values[3].toDouble()
        val e = values[4].toDouble()
        val f = values[5].toDouble()
        val g = values[6].toDouble()
        val h = values[7].toDouble()
        val i = values[8].toDouble()
        val determinant = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        if (!determinant.isFinite() || determinant == 0.0) return null

        val inverseDeterminant = 1.0 / determinant
        val inverse = doubleArrayOf(
            (e * i - f * h) * inverseDeterminant,
            (c * h - b * i) * inverseDeterminant,
            (b * f - c * e) * inverseDeterminant,
            (f * g - d * i) * inverseDeterminant,
            (a * i - c * g) * inverseDeterminant,
            (c * d - a * f) * inverseDeterminant,
            (d * h - e * g) * inverseDeterminant,
            (b * g - a * h) * inverseDeterminant,
            (a * e - b * d) * inverseDeterminant,
        )
        if (inverse.any { !it.isFinite() || abs(it) > Float.MAX_VALUE.toDouble() }) return null
        return ColorMatrix3x3F32(FloatArray(9) { inverse[it].toFloat() })
    }

    /** Returns a defensive row-major copy of the nine coefficients. */
    public fun toFloatArray(): FloatArray = values.copyOf()

    override fun equals(other: Any?): Boolean =
        other is ColorMatrix3x3F32 && values.contentEquals(other.values)

    override fun hashCode(): Int = values.contentHashCode()

    public companion object {
        /** Identity RGB matrix. */
        public val Identity: ColorMatrix3x3F32 = ColorMatrix3x3F32(
            floatArrayOf(
                1f, 0f, 0f,
                0f, 1f, 0f,
                0f, 0f, 1f,
            ),
        )

        /** Creates a matrix from nine row-major coefficients. */
        public fun of(
            m00: Float, m01: Float, m02: Float,
            m10: Float, m11: Float, m12: Float,
            m20: Float, m21: Float, m22: Float,
        ): ColorMatrix3x3F32 = ColorMatrix3x3F32(
            floatArrayOf(m00, m01, m02, m10, m11, m12, m20, m21, m22),
        )

        /** Creates a matrix from a defensively copied row-major nine-tuple. */
        public fun fromRowMajor(values: FloatArray): ColorMatrix3x3F32 {
            require(values.size == 9) { "ColorMatrix3x3F32 expects 9 floats, got ${values.size}" }
            return ColorMatrix3x3F32(values.copyOf())
        }
    }
}
