package org.skia.foundation.skcms

/**
 * Bit-compatible port of `skcms_Matrix3x4`
 * ([modules/skcms/src/skcms_public.h:60-62](file:///Users/chaos/workspace/kanvas-forge/skia-main/modules/skcms/src/skcms_public.h)).
 *
 * Row-major 3x4 matrix used by ICC v4 A2B / B2A "M" matrices. Currently
 * only stored as a passthrough for Phase F1; consumers arrive in Phase F4
 * (LUT evaluation).
 */
public class SkcmsMatrix3x4(public val vals: Array<FloatArray>) {

    init {
        require(vals.size == 3 && vals.all { it.size == 4 }) {
            "SkcmsMatrix3x4 must be 3x4"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SkcmsMatrix3x4) return false
        for (r in 0 until 3) for (c in 0 until 4) {
            if (vals[r][c] != other.vals[r][c]) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var h = 0
        for (r in 0 until 3) for (c in 0 until 4) {
            h = 31 * h + vals[r][c].toRawBits()
        }
        return h
    }
}
