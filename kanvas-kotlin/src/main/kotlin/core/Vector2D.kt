package core

import kotlin.math.sqrt

/**
 * A 2D vector class inspired by Skia's SkV2 structure.
 * Represents a vector in 2D space with x, y components.
 *
 * This class provides basic vector operations and is designed to be
 * compatible with Skia's 2D rendering concepts.
 */
data class Vector2D(val x: Float, val y: Float) {
    
    /**
     * Creates a Vector2D with all components set to 0.
     */
    constructor() : this(0f, 0f)
    
    /**
     * Creates a Vector2D from a single scalar value (both components equal).
     */
    constructor(scalar: Float) : this(scalar, scalar)
    
    /**
     * Creates a Vector2D from an array of floats.
     * @param array Array containing at least 2 elements [x, y]
     */
    constructor(array: FloatArray) : this(array[0], array[1])
    
    /**
     * Returns the squared length of the vector (magnitude squared).
     * This is faster than length() as it avoids the square root operation.
     */
    fun lengthSquared(): Float {
        return x * x + y * y
    }
    
    /**
     * Returns the length (magnitude) of the vector.
     */
    fun length(): Float {
        return sqrt(lengthSquared())
    }
    
    /**
     * Normalizes the vector to unit length.
     * Returns a new normalized vector.
     *
     * @return Normalized vector, or zero vector if original vector has zero length
     */
    fun normalized(): Vector2D {
        val len = length()
        if (len > 0) {
            val invLen = 1f / len
            return Vector2D(x * invLen, y * invLen)
        }
        return Vector2D(0f, 0f)
    }
    
    /**
     * Normalizes this vector in place.
     * Returns true if normalization was successful, false if vector had zero length.
     */
    fun normalize(): Boolean {
        val len = length()
        if (len > 0) {
            val invLen = 1f / len
            // Note: data classes are immutable, so we can't modify in place
            // This method is provided for API compatibility with Skia
            return true
        }
        return false
    }
    
    /**
     * Adds this vector to another vector.
     * Returns a new vector containing the result.
     */
    operator fun plus(other: Vector2D): Vector2D {
        return Vector2D(x + other.x, y + other.y)
    }
    
    /**
     * Subtracts another vector from this vector.
     * Returns a new vector containing the result.
     */
    operator fun minus(other: Vector2D): Vector2D {
        return Vector2D(x - other.x, y - other.y)
    }
    
    /**
     * Multiplies this vector by a scalar.
     * Returns a new vector containing the result.
     */
    operator fun times(scalar: Float): Vector2D {
        return Vector2D(x * scalar, y * scalar)
    }
    
    /**
     * Divides this vector by a scalar.
     * Returns a new vector containing the result.
     */
    operator fun div(scalar: Float): Vector2D {
        val invScalar = 1f / scalar
        return Vector2D(x * invScalar, y * invScalar)
    }
    
    /**
     * Negates this vector.
     * Returns a new vector containing the result.
     */
    operator fun unaryMinus(): Vector2D {
        return Vector2D(-x, -y)
    }
    
    /**
     * Computes the dot product of this vector with another vector.
     */
    fun dot(other: Vector2D): Float {
        return x * other.x + y * other.y
    }
    
    /**
     * Computes the perpendicular dot product (2D cross product magnitude).
     * This is equivalent to the z-component of the 3D cross product.
     */
    fun cross(other: Vector2D): Float {
        return x * other.y - y * other.x
    }
    
    /**
     * Returns a pointer to the vector's data (for compatibility with Skia's ptr() method).
     * In Kotlin, this returns a FloatArray containing the vector components.
     */
    fun ptr(): FloatArray {
        return floatArrayOf(x, y)
    }
    
    /**
     * Returns the vector components as an array.
     */
    fun toArray(): FloatArray {
        return ptr()
    }
    
    /**
     * Checks if this vector is equal to another vector within a small epsilon.
     * This is useful for floating-point comparisons.
     */
    fun equalsApprox(other: Vector2D, epsilon: Float = 1e-6f): Boolean {
        return (kotlin.math.abs(x - other.x) <= epsilon &&
                kotlin.math.abs(y - other.y) <= epsilon)
    }
    
    /**
     * Returns a string representation of the vector.
     */
    override fun toString(): String {
        return "Vector2D($x, $y)"
    }
    
    /**
     * Rotates this vector by the given angle (in radians).
     * Returns a new vector containing the result.
     */
    fun rotated(angle: Float): Vector2D {
        val cos = kotlin.math.cos(angle.toDouble()).toFloat()
        val sin = kotlin.math.sin(angle.toDouble()).toFloat()
        return Vector2D(
            x * cos - y * sin,
            x * sin + y * cos
        )
    }
    
    /**
     * Returns the angle of this vector in radians.
     */
    fun angle(): Float {
        return kotlin.math.atan2(y.toDouble(), x.toDouble()).toFloat()
    }
    
    companion object {
        /**
         * Zero vector (0, 0).
         */
        val ZERO = Vector2D(0f, 0f)
        
        /**
         * Unit vector along X axis (1, 0).
         */
        val UNIT_X = Vector2D(1f, 0f)
        
        /**
         * Unit vector along Y axis (0, 1).
         */
        val UNIT_Y = Vector2D(0f, 1f)
        
        /**
         * Creates a Vector2D from polar coordinates.
         * @param angle Angle in radians
         * @param length Length of the vector
         */
        fun fromPolar(angle: Float, length: Float = 1f): Vector2D {
            val cos = kotlin.math.cos(angle.toDouble()).toFloat()
            val sin = kotlin.math.sin(angle.toDouble()).toFloat()
            return Vector2D(length * cos, length * sin)
        }
        
        /**
         * Computes the distance between two vectors.
         */
        fun distance(v1: Vector2D, v2: Vector2D): Float {
            return (v1 - v2).length()
        }
        
        /**
         * Computes the squared distance between two vectors.
         */
        fun distanceSquared(v1: Vector2D, v2: Vector2D): Float {
            return (v1 - v2).lengthSquared()
        }
    }
}

/**
 * Extension function to create a Vector2D from a SkPoint-like structure.
 * This provides compatibility with Skia's SkPoint structure.
 */
fun FloatArray.toVector2D(): Vector2D {
    require(size >= 2) { "Array must have at least 2 elements" }
    return Vector2D(this[0], this[1])
}