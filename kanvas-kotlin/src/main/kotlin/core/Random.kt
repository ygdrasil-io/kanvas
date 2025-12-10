package com.kanvas.core

/**
 * Simple random number generator for Kanvas
 * Based on Kotlin's Random but with Skia-like API
 */
class SkRandom(private var seed: Long = System.currentTimeMillis()) {
    
    private companion object {
        const val MULTIPLIER = 0x5DEECE66DL
        const val ADDEND = 0xBL
        const val MASK = (1L shl 48) - 1
    }
    
    /**
     * Returns a random float between 0 (inclusive) and 1 (exclusive)
     */
    fun nextFloat(): Float {
        seed = (seed * MULTIPLIER + ADDEND) and MASK
        return (seed ushr 16).toFloat() / (1 shl 31).toFloat()
    }
    
    /**
     * Returns a random float in the specified range [min, max)
     */
    fun nextRangeScalar(min: Float, max: Float): Float {
        return min + nextFloat() * (max - min)
    }
    
    /**
     * Returns a random unsigned integer
     */
    fun nextU(): Int {
        seed = (seed * MULTIPLIER + ADDEND) and MASK
        return (seed ushr 16).toInt()
    }
    
    /**
     * Returns a random color
     */
    fun nextColor(): Color {
        val r = nextU() and 0xFF
        val g = nextU() and 0xFF
        val b = nextU() and 0xFF
        return Color(r, g, b, 255) // Fully opaque random color
    }
}

/**
 * Extension function to convert degrees to radians
 */
fun Float.toRadians(): Float = this * (Math.PI.toFloat() / 180f)

/**
 * Extension function to convert radians to degrees
 */
fun Float.toDegrees(): Float = this * (180f / Math.PI.toFloat())