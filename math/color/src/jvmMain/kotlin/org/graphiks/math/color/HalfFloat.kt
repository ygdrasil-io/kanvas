package org.graphiks.math.color

/**
 * Converts an IEEE 754 half-precision float ([Short] bit pattern) to a
 * single-precision [Float].
 *
 * Handles subnormals, infinity, and NaN.
 */
public fun halfToFloat(h: Short): Float {
    val bits = h.toInt() and 0xFFFF
    val sign = (bits and 0x8000) shl 16
    val exp = (bits ushr 10) and 0x1F
    val mant = bits and 0x3FF
    return when (exp) {
        0 -> {
            if (mant == 0) java.lang.Float.intBitsToFloat(sign)
            else {
                var m = mant; var e = 1
                while ((m and 0x400) == 0) { m = m shl 1; e-- }
                m = m and 0x3FF
                val f32Exp = (e + (127 - 15)) shl 23
                val f32Mant = m shl 13
                java.lang.Float.intBitsToFloat(sign or f32Exp or f32Mant)
            }
        }
        0x1F -> {
            val f32Exp = 0xFF shl 23
            val f32Mant = mant shl 13
            java.lang.Float.intBitsToFloat(sign or f32Exp or f32Mant)
        }
        else -> {
            val f32Exp = (exp + (127 - 15)) shl 23
            val f32Mant = mant shl 13
            java.lang.Float.intBitsToFloat(sign or f32Exp or f32Mant)
        }
    }
}

/**
 * Converts a single-precision [Float] to an IEEE 754 half-precision float
 * bit pattern stored as a [Short].
 *
 * Rounds to nearest-even. Subnormals, infinity, and NaN are preserved.
 */
public fun floatToHalf(f: Float): Short {
    val bits = java.lang.Float.floatToRawIntBits(f)
    val sign = (bits ushr 16) and 0x8000
    val exp32 = (bits ushr 23) and 0xFF
    val mant32 = bits and 0x7FFFFF
    return when {
        exp32 == 0xFF -> {
            val mant16 = if (mant32 != 0) ((mant32 ushr 13) or 0x200) else 0
            (sign or 0x7C00 or mant16).toShort()
        }
        exp32 > 142 -> (sign or 0x7C00).toShort()
        exp32 < 102 -> sign.toShort()
        exp32 < 113 -> {
            val mantWithImplicit = mant32 or 0x800000
            val shift = 126 - exp32
            val truncated = mantWithImplicit ushr shift
            val remainder = mantWithImplicit and ((1 shl shift) - 1)
            val halfway = 1 shl (shift - 1)
            val rounded = truncated + if (
                remainder > halfway || (remainder == halfway && truncated and 1 != 0)
            ) 1 else 0
            (sign or rounded).toShort()
        }
        else -> {
            val expHalf = (exp32 - 127 + 15) shl 10
            val mantHalf = mant32 ushr 13
            val remainder = mant32 and 0x1FFF
            val halfway = 0x1000
            val increment = if (
                remainder > halfway || (remainder == halfway && mantHalf and 1 != 0)
            ) 1 else 0
            (sign or expHalf or mantHalf).plus(increment).toShort()
        }
    }
}
