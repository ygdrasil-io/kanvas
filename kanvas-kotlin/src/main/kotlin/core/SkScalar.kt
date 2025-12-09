package core

/**
 * Implementation of Skia's SkScalar and SkFixed types for high-precision calculations.
 * This provides the numerical precision needed for accurate graphics rendering.
 */

/**
 * SkScalar represents a high-precision floating-point value similar to Skia's SkScalar.
 * Used for all geometric calculations to ensure consistency with Skia's rendering.
 */
typealias SkScalar = Float

/**
 * SkFixed represents a 16.16 fixed-point number, providing high precision
 * for color and geometric calculations while avoiding floating-point artifacts.
 */
class SkFixed(private val value: Int) {
    companion object {
        const val SK_Fixed1: Int = 0x10000 // 1.0 in 16.16 format
        const val SK_FixedHalf: Int = 0x8000 // 0.5 in 16.16 format
        const val SK_FixedMask: Int = 0xFFFF // Mask for fractional part
        
        /**
         * Convert an integer to SkFixed (multiply by 65536)
         */
        fun fromInt(value: Int): SkFixed {
            return SkFixed(value * SK_Fixed1)
        }
        
        /**
         * Convert a float to SkFixed
         */
        fun fromFloat(value: Float): SkFixed {
            return SkFixed((value * SK_Fixed1).toInt())
        }
        
        /**
         * Convert a SkScalar to SkFixed
         */
        fun fromSkScalar(value: SkScalar): SkFixed {
            return fromFloat(value)
        }
    }
    
    /**
     * Convert SkFixed to Int (truncates fractional part)
     */
    fun toInt(): Int {
        return value / SK_Fixed1
    }
    
    /**
     * Convert SkFixed to Float
     */
    fun toFloat(): Float {
        return value.toFloat() / SK_Fixed1
    }
    
    /**
     * Convert SkFixed to SkScalar
     */
    fun toSkScalar(): SkScalar {
        return toFloat()
    }
    
    /**
     * Convert SkFixed to Byte (for color components)
     */
    fun toByte(): Byte {
        return (value / SK_Fixed1).toByte()
    }
    
    /**
     * Convert SkFixed to UByte (for color components)
     */
    fun toUByte(): UByte {
        return (value / SK_Fixed1).toUByte()
    }
    
    /**
     * Add two SkFixed values
     */
    operator fun plus(other: SkFixed): SkFixed {
        return SkFixed(value + other.value)
    }
    
    /**
     * Subtract two SkFixed values
     */
    operator fun minus(other: SkFixed): SkFixed {
        return SkFixed(value - other.value)
    }
    
    /**
     * Multiply two SkFixed values (with proper 16.16 multiplication)
     */
    operator fun times(other: SkFixed): SkFixed {
        // 16.16 * 16.16 = 32.32, then shift right 16 to get 16.16
        val result = (value.toLong() * other.value.toLong()) / SK_Fixed1
        return SkFixed(result.toInt())
    }
    
    /**
     * Multiply SkFixed by SkScalar
     */
    operator fun times(other: SkScalar): SkFixed {
        return SkFixed((value * other).toInt())
    }
    
    /**
     * Divide two SkFixed values
     */
    operator fun div(other: SkFixed): SkFixed {
        // (a/b) in fixed point = (a * 1.0) / b = (a << 16) / b
        val result = (value.toLong() * SK_Fixed1) / other.value
        return SkFixed(result.toInt())
    }
    
    /**
     * SkFixed modulo operation
     */
    fun mod(other: SkFixed): SkFixed {
        return SkFixed(value % other.value)
    }
    
    /**
     * Get the fractional part of SkFixed
     */
    fun fractional(): SkFixed {
        return SkFixed(value and SK_FixedMask)
    }
    
    /**
     * Get the integer part of SkFixed
     */
    fun integer(): SkFixed {
        return SkFixed(value and SK_FixedMask.inv())
    }
    
    /**
     * Round to nearest integer
     */
    fun round(): SkFixed {
        return SkFixed((value + SK_FixedHalf) and SK_FixedMask.inv())
    }
    
    /**
     * Ceiling function
     */
    fun ceil(): SkFixed {
        return if ((value and SK_FixedMask) == 0) {
            this
        } else {
            SkFixed((value and SK_FixedMask.inv()) + SK_Fixed1)
        }
    }
    
    /**
     * Floor function
     */
    fun floor(): SkFixed {
        return SkFixed(value and SK_FixedMask.inv())
    }
    
    override fun toString(): String {
        return "${toFloat()}"
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SkFixed) return false
        return value == other.value
    }
    
    override fun hashCode(): Int {
        return value
    }
}

/**
 * Skia's PI constant in SkScalar format
 */
const val SK_ScalarPI: SkScalar = 3.14159265358979323846f

/**
 * Skia's 2PI constant
 */
const val SK_Scalar2PI: SkScalar = 6.28318530717958647692f

/**
 * Skia's PI/2 constant
 */
const val SK_ScalarPI_2: SkScalar = 1.57079632679489661923f

/**
 * Skia's mathematical functions with SkScalar precision
 */
fun SkScalarSin(radians: SkScalar): SkScalar {
    return kotlin.math.sin(radians.toDouble()).toFloat()
}

fun SkScalarCos(radians: SkScalar): SkScalar {
    return kotlin.math.cos(radians.toDouble()).toFloat()
}

fun SkScalarTan(radians: SkScalar): SkScalar {
    return kotlin.math.tan(radians.toDouble()).toFloat()
}

fun SkScalarMod(value: SkScalar, modulus: SkScalar): SkScalar {
    return value % modulus
}

fun SkScalarAbs(value: SkScalar): SkScalar {
    return kotlin.math.abs(value)
}

fun SkScalarCeil(value: SkScalar): Int {
    return kotlin.math.ceil(value).toInt()
}

fun SkScalarFloor(value: SkScalar): Int {
    return kotlin.math.floor(value).toInt()
}

fun SkScalarRound(value: SkScalar): Int {
    return kotlin.math.round(value).toInt()
}

/**
 * Convert degrees to radians
 */
fun SkScalarDegreesToRadians(degrees: SkScalar): SkScalar {
    return degrees * (SK_ScalarPI / 180.0f)
}

/**
 * Convert radians to degrees
 */
fun SkScalarRadiansToDegrees(radians: SkScalar): SkScalar {
    return radians * (180.0f / SK_ScalarPI)
}

/**
 * Skia's color component type (0-255)
 */
typealias SkAlpha = UByte

/**
 * Convert SkScalar to SkAlpha (0-255)
 */
fun SkAlphaFromSkScalar(value: SkScalar): SkAlpha {
    val clamped = value.coerceIn(0.0f, 1.0f)
    return (clamped * 255f).toInt().toUByte()
}

/**
 * Convert SkFixed to SkAlpha
 */
fun SkAlphaFromFixed(value: SkFixed): SkAlpha {
    return value.toUByte()
}

/**
 * Skia's color multiplication with fixed-point precision
 */
fun SkAlphaMulQ(alpha: SkAlpha, color: Int): Int {
    // Multiply color by alpha using fixed-point arithmetic
    val a = alpha.toInt()
    val r = (color shr 16) and 0xFF
    val g = (color shr 8) and 0xFF
    val b = color and 0xFF
    
    val resultR = ((r * a) / 255) shl 16
    val resultG = ((g * a) / 255) shl 8
    val resultB = (b * a) / 255
    
    return resultR or resultG or resultB
}

/**
 * Skia's fixed-point multiplication
 */
fun SkFixedMul(a: SkFixed, b: SkFixed): SkFixed {
    // Convert to raw values for multiplication
    val aValue = a.toFloat() * SkFixed.SK_Fixed1.toFloat()
    val bValue = b.toFloat() * SkFixed.SK_Fixed1.toFloat()
    
    // 16.16 * 16.16 = 32.32, then shift right 16 to get 16.16
    val result = (aValue.toLong() * bValue.toLong()) / SkFixed.SK_Fixed1
    return SkFixed.fromFloat(result.toFloat() / SkFixed.SK_Fixed1)
}

/**
 * Minimum of two SkFixed values
 */
fun SkMinFixed(a: SkFixed, b: SkFixed): SkFixed {
    return if (a.toInt() < b.toInt()) a else b
}

/**
 * Maximum of two SkFixed values
 */
fun SkMaxFixed(a: SkFixed, b: SkFixed): SkFixed {
    return if (a.toInt() > b.toInt()) a else b
}