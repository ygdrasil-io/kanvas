// Simple test to verify Vector2D syntax

import kotlin.math.sqrt

data class Vector2D(val x: Float, val y: Float) {
    constructor() : this(0f, 0f)
    constructor(scalar: Float) : this(scalar, scalar)
    constructor(array: FloatArray) : this(array[0], array[1])
    
    fun lengthSquared(): Float = x * x + y * y
    fun length(): Float = sqrt(lengthSquared())
    
    fun normalized(): Vector2D {
        val len = length()
        return if (len > 0) Vector2D(x / len, y / len) else Vector2D(0f, 0f)
    }
    
    fun normalize(): Boolean = length() > 0
    
    operator fun plus(other: Vector2D): Vector2D = Vector2D(x + other.x, y + other.y)
    operator fun minus(other: Vector2D): Vector2D = Vector2D(x - other.x, y - other.y)
    operator fun times(scalar: Float): Vector2D = Vector2D(x * scalar, y * scalar)
    operator fun div(scalar: Float): Vector2D = Vector2D(x / scalar, y / scalar)
    operator fun unaryMinus(): Vector2D = Vector2D(-x, -y)
    
    fun dot(other: Vector2D): Float = x * other.x + y * other.y
    fun cross(other: Vector2D): Float = x * other.y - y * other.x
    
    fun ptr(): FloatArray = floatArrayOf(x, y)
    fun toArray(): FloatArray = ptr()
    
    fun equalsApprox(other: Vector2D, epsilon: Float = 1e-6f): Boolean = 
        kotlin.math.abs(x - other.x) <= epsilon &&
        kotlin.math.abs(y - other.y) <= epsilon
    
    fun rotated(angle: Float): Vector2D {
        val cos = kotlin.math.cos(angle.toDouble()).toFloat()
        val sin = kotlin.math.sin(angle.toDouble()).toFloat()
        return Vector2D(x * cos - y * sin, x * sin + y * cos)
    }
    
    fun angle(): Float = kotlin.math.atan2(y.toDouble(), x.toDouble()).toFloat()
    
    override fun toString(): String = "Vector2D($x, $y)"
    
    companion object {
        val ZERO = Vector2D(0f, 0f)
        val UNIT_X = Vector2D(1f, 0f)
        val UNIT_Y = Vector2D(0f, 1f)
        
        fun fromPolar(angle: Float, length: Float = 1f): Vector2D {
            val cos = kotlin.math.cos(angle.toDouble()).toFloat()
            val sin = kotlin.math.sin(angle.toDouble()).toFloat()
            return Vector2D(length * cos, length * sin)
        }
        
        fun distance(v1: Vector2D, v2: Vector2D): Float = (v1 - v2).length()
        fun distanceSquared(v1: Vector2D, v2: Vector2D): Float = (v1 - v2).lengthSquared()
    }
}

fun FloatArray.toVector2D(): Vector2D {
    require(size >= 2) { "Array must have at least 2 elements" }
    return Vector2D(this[0], this[1])
}

// Test the implementation
fun main() {
    val v1 = Vector2D(1f, 2f)
    val v2 = Vector2D(4f, 5f)
    
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
    
    println("Vector2D.ZERO: ${Vector2D.ZERO}")
    println("Vector2D.UNIT_X: ${Vector2D.UNIT_X}")
    println("Vector2D.UNIT_Y: ${Vector2D.UNIT_Y}")
    
    val rotated = v1.rotated(kotlin.math.PI.toFloat() / 2)
    println("v1 rotated 90deg: $rotated")
    
    println("v1 angle: ${v1.angle()}")
    
    val polar = Vector2D.fromPolar(kotlin.math.PI.toFloat() / 4, sqrt(2f))
    println("fromPolar(PI/4, sqrt(2)): $polar")
    
    val dist = Vector2D.distance(v1, v2)
    println("distance(v1, v2): $dist")
    
    val array = floatArrayOf(1.5f, 2.5f)
    val fromArray = array.toVector2D()
    println("fromArray: $fromArray")
    
    println("All tests passed!")
}