package org.graphiks.math

/**
 * Bit-compatible port of `skcms_Matrix3x3`. Row-major: `vals[row][col]`.
 *
 * Two `SkcmsMatrix3x3` are considered equal when every cell matches; the
 * generated `equals`/`hashCode` would compare arrays by reference, so we
 * override.
 */
public class SkcmsMatrix3x3(public val vals: Array<FloatArray>) {

    init {
        require(vals.size == 3 && vals.all { it.size == 3 }) {
            "SkcmsMatrix3x3 must be 3x3"
        }
    }

    public operator fun get(row: Int, col: Int): Float = vals[row][col]

    public fun copy(): SkcmsMatrix3x3 = of(
        vals[0][0], vals[0][1], vals[0][2],
        vals[1][0], vals[1][1], vals[1][2],
        vals[2][0], vals[2][1], vals[2][2],
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SkcmsMatrix3x3) return false
        for (r in 0 until 3) for (c in 0 until 3) {
            if (vals[r][c] != other.vals[r][c]) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var h = 0
        for (r in 0 until 3) for (c in 0 until 3) {
            h = 31 * h + vals[r][c].toRawBits()
        }
        return h
    }

    override fun toString(): String = buildString {
        append("[[")
        for (r in 0 until 3) {
            if (r > 0) append("], [")
            for (c in 0 until 3) {
                if (c > 0) append(", ")
                append(vals[r][c])
            }
        }
        append("]]")
    }

    public companion object {
        public fun of(
            r0c0: Float, r0c1: Float, r0c2: Float,
            r1c0: Float, r1c1: Float, r1c2: Float,
            r2c0: Float, r2c1: Float, r2c2: Float,
        ): SkcmsMatrix3x3 = SkcmsMatrix3x3(arrayOf(
            floatArrayOf(r0c0, r0c1, r0c2),
            floatArrayOf(r1c0, r1c1, r1c2),
            floatArrayOf(r2c0, r2c1, r2c2),
        ))

        public val IDENTITY: SkcmsMatrix3x3 = of(
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f,
        )
    }
}
