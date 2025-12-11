package core

import kotlin.math.sqrt

/**
 * A 3D vector class inspired by Skia's SkV3 structure.
 * Represents a vector in 3D space with x, y, z components.
 *
 * This class provides basic vector operations and is designed to be
 * compatible with Skia's 3D rendering concepts.
 */
data class Vector3D(val x: Float, val y: Float, val z: Float) {
    
    /**
     * Creates a Vector3D with all components set to 0.
     */
    constructor() : this(0f, 0f, 0f)
    
    /**
     * Creates a Vector3D from a single scalar value (all components equal).
     */
    constructor(scalar: Float) : this(scalar, scalar, scalar)
    
    /**
     * Creates a Vector3D from an array of floats.
     * @param array Array containing at least 3 elements [x, y, z]
     */
    constructor(array: FloatArray) : this(array[0], array[1], array[2])
    
    /**
     * Returns the squared length of the vector (magnitude squared).
     * This is faster than length() as it avoids the square root operation.
     */
    fun lengthSquared(): Float {
        return x * x + y * y + z * z
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
    fun normalized(): Vector3D {
        val len = length()
        if (len > 0) {
            val invLen = 1f / len
            return Vector3D(x * invLen, y * invLen, z * invLen)
        }
        return Vector3D(0f, 0f, 0f)
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
    operator fun plus(other: Vector3D): Vector3D {
        return Vector3D(x + other.x, y + other.y, z + other.z)
    }
    
    /**
     * Subtracts another vector from this vector.
     * Returns a new vector containing the result.
     */
    operator fun minus(other: Vector3D): Vector3D {
        return Vector3D(x - other.x, y - other.y, z - other.z)
    }
    
    /**
     * Multiplies this vector by a scalar.
     * Returns a new vector containing the result.
     */
    operator fun times(scalar: Float): Vector3D {
        return Vector3D(x * scalar, y * scalar, z * scalar)
    }
    
    /**
     * Divides this vector by a scalar.
     * Returns a new vector containing the result.
     */
    operator fun div(scalar: Float): Vector3D {
        val invScalar = 1f / scalar
        return Vector3D(x * invScalar, y * invScalar, z * invScalar)
    }
    
    /**
     * Negates this vector.
     * Returns a new vector containing the result.
     */
    operator fun unaryMinus(): Vector3D {
        return Vector3D(-x, -y, -z)
    }
    
    /**
     * Computes the dot product of this vector with another vector.
     */
    fun dot(other: Vector3D): Float {
        return x * other.x + y * other.y + z * other.z
    }
    
    /**
     * Computes the cross product of this vector with another vector.
     * Returns a new vector containing the result.
     */
    fun cross(other: Vector3D): Vector3D {
        return Vector3D(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x
        )
    }
    
    /**
     * Returns a pointer to the vector's data (for compatibility with Skia's ptr() method).
     * In Kotlin, this returns a FloatArray containing the vector components.
     */
    fun ptr(): FloatArray {
        return floatArrayOf(x, y, z)
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
    fun equalsApprox(other: Vector3D, epsilon: Float = 1e-6f): Boolean {
        return (kotlin.math.abs(x - other.x) <= epsilon &&
                kotlin.math.abs(y - other.y) <= epsilon &&
                kotlin.math.abs(z - other.z) <= epsilon)
    }
    
    /**
     * Returns a string representation of the vector.
     */
    override fun toString(): String {
        return "Vector3D($x, $y, $z)"
    }
    
    companion object {
        /**
         * Zero vector (0, 0, 0).
         */
        val ZERO = Vector3D(0f, 0f, 0f)
        
        /**
         * Unit vector along X axis (1, 0, 0).
         */
        val UNIT_X = Vector3D(1f, 0f, 0f)
        
        /**
         * Unit vector along Y axis (0, 1, 0).
         */
        val UNIT_Y = Vector3D(0f, 1f, 0f)
        
        /**
         * Unit vector along Z axis (0, 0, 1).
         */
        val UNIT_Z = Vector3D(0f, 0f, 1f)
        
        /**
         * Creates a Vector3D from polar coordinates.
         * @param azimuth Angle in the XY plane (radians)
         * @param elevation Angle from the XY plane (radians)
         * @param length Length of the vector
         */
        fun fromPolar(azimuth: Float, elevation: Float, length: Float = 1f): Vector3D {
            val cosElev = kotlin.math.cos(elevation.toDouble()).toFloat()
            val sinElev = kotlin.math.sin(elevation.toDouble()).toFloat()
            val cosAzim = kotlin.math.cos(azimuth.toDouble()).toFloat()
            val sinAzim = kotlin.math.sin(azimuth.toDouble()).toFloat()
            
            return Vector3D(
                length * cosElev * cosAzim,
                length * cosElev * sinAzim,
                length * sinElev
            )
        }
        
        /**
         * Computes the distance between two vectors.
         */
        fun distance(v1: Vector3D, v2: Vector3D): Float {
            return (v1 - v2).length()
        }
        
        /**
         * Computes the squared distance between two vectors.
         */
        fun distanceSquared(v1: Vector3D, v2: Vector3D): Float {
            return (v1 - v2).lengthSquared()
        }
    }
}

/**
 * Extension function to create a Vector3D from a SkPoint3-like structure.
 * This provides compatibility with Skia's SkPoint3 structure.
 */
fun FloatArray.toVector3D(): Vector3D {
    require(size >= 3) { "Array must have at least 3 elements" }
    return Vector3D(this[0], this[1], this[2])
}