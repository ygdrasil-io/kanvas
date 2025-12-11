// Simple test to verify Vector3D syntax

import kotlin.math.sqrt

data class Vector3D(val x: Float, val y: Float, val z: Float) {
    constructor() : this(0f, 0f, 0f)
    constructor(scalar: Float) : this(scalar, scalar, scalar)
    constructor(array: FloatArray) : this(array[0], array[1], array[2])
    
    fun lengthSquared(): Float = x * x + y * y + z * z
    fun length(): Float = sqrt(lengthSquared())
    
    fun normalized(): Vector3D {
        val len = length()
        return if (len > 0) Vector3D(x / len, y / len, z / len) else Vector3D(0f, 0f, 0f)
    }
    
    fun normalize(): Boolean = length() > 0
    
    operator fun plus(other: Vector3D): Vector3D = Vector3D(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3D): Vector3D = Vector3D(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float): Vector3D = Vector3D(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Float): Vector3D = Vector3D(x / scalar, y / scalar, z / scalar)
    operator fun unaryMinus(): Vector3D = Vector3D(-x, -y, -z)
    
    fun dot(other: Vector3D): Float = x * other.x + y * other.y + z * other.z
    
    fun cross(other: Vector3D): Vector3D = Vector3D(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )
    
    fun ptr(): FloatArray = floatArrayOf(x, y, z)
    fun toArray(): FloatArray = ptr()
    
    fun equalsApprox(other: Vector3D, epsilon: Float = 1e-6f): Boolean = 
        kotlin.math.abs(x - other.x) <= epsilon &&
        kotlin.math.abs(y - other.y) <= epsilon &&
        kotlin.math.abs(z - other.z) <= epsilon
    
    override fun toString(): String = "Vector3D($x, $y, $z)"
    
    companion object {
        val ZERO = Vector3D(0f, 0f, 0f)
        val UNIT_X = Vector3D(1f, 0f, 0f)
        val UNIT_Y = Vector3D(0f, 1f, 0f)
        val UNIT_Z = Vector3D(0f, 0f, 1f)
        
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
        
        fun distance(v1: Vector3D, v2: Vector3D): Float = (v1 - v2).length()
        fun distanceSquared(v1: Vector3D, v2: Vector3D): Float = (v1 - v2).lengthSquared()
    }
}

fun FloatArray.toVector3D(): Vector3D {
    require(size >= 3) { "Array must have at least 3 elements" }
    return Vector3D(this[0], this[1], this[2])
}

// Test the implementation
fun main() {
    val v1 = Vector3D(1f, 2f, 3f)
    val v2 = Vector3D(4f, 5f, 6f)
    
    println("v1: $v1")
    println("v2: $v2")
    println("v1 + v2: ${v1 + v2}")
    println("v1 - v2: ${v1 - v2}")
    println("v1 * 2: ${v1 * 2f}")
    println("v2 / 2: ${v2 / 2f}")
    println("-v1: ${-v1}")
    println("v1.dot(v2): ${v1.dot(v2)}")
    println("v1.cross(v2): ${v1.cross(v2)}")
    println("v1.length(): ${v1.length()}")
    println("v1.normalized(): ${v1.normalized()}")
    println("v1.ptr(): ${v1.ptr().contentToString()}")
    
    println("Vector3D.ZERO: ${Vector3D.ZERO}")
    println("Vector3D.UNIT_X: ${Vector3D.UNIT_X}")
    println("Vector3D.UNIT_Y: ${Vector3D.UNIT_Y}")
    println("Vector3D.UNIT_Z: ${Vector3D.UNIT_Z}")
    
    val polar = Vector3D.fromPolar(0f, 0f, 1f)
    println("fromPolar(0,0,1): $polar")
    
    val dist = Vector3D.distance(v1, v2)
    println("distance(v1, v2): $dist")
    
    val array = floatArrayOf(1.5f, 2.5f, 3.5f)
    val fromArray = array.toVector3D()
    println("fromArray: $fromArray")
    
    println("All tests passed!")
}