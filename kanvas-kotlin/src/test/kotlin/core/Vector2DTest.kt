package core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class Vector2DTest {
    
    @Test
    fun `test Vector2D creation and basic properties`() {
        val v1 = Vector2D(1f, 2f)
        assertEquals(1f, v1.x)
        assertEquals(2f, v1.y)
        
        val v2 = Vector2D()
        assertEquals(0f, v2.x)
        assertEquals(0f, v2.y)
        
        val v3 = Vector2D(2.5f)
        assertEquals(2.5f, v3.x)
        assertEquals(2.5f, v3.y)
        
        val v4 = Vector2D(floatArrayOf(1f, 4f))
        assertEquals(1f, v4.x)
        assertEquals(4f, v4.y)
    }
    
    @Test
    fun `test Vector2D length calculations`() {
        val v1 = Vector2D(3f, 4f)
        assertEquals(25f, v1.lengthSquared())
        assertEquals(5f, v1.length())
        
        val v2 = Vector2D(1f, 1f)
        assertEquals(2f, v2.lengthSquared())
        assertEquals(sqrt(2f), v2.length())
        
        val v3 = Vector2D()
        assertEquals(0f, v3.lengthSquared())
        assertEquals(0f, v3.length())
    }
    
    @Test
    fun `test Vector2D normalization`() {
        val v1 = Vector2D(3f, 4f)
        val normalized = v1.normalized()
        
        assertEquals(0.6f, normalized.x)
        assertEquals(0.8f, normalized.y)
        assertEquals(1f, normalized.length())
        
        // Test zero vector normalization
        val v2 = Vector2D()
        val zeroNormalized = v2.normalized()
        assertEquals(0f, zeroNormalized.x)
        assertEquals(0f, zeroNormalized.y)
    }
    
    @Test
    fun `test Vector2D arithmetic operations`() {
        val v1 = Vector2D(1f, 2f)
        val v2 = Vector2D(4f, 5f)
        
        // Addition
        val sum = v1 + v2
        assertEquals(Vector2D(5f, 7f), sum)
        
        // Subtraction
        val diff = v1 - v2
        assertEquals(Vector2D(-3f, -3f), diff)
        
        // Scalar multiplication
        val scaled = v1 * 2f
        assertEquals(Vector2D(2f, 4f), scaled)
        
        // Scalar division
        val divided = v2 / 2f
        assertEquals(Vector2D(2f, 2.5f), divided)
        
        // Negation
        val negated = -v1
        assertEquals(Vector2D(-1f, -2f), negated)
    }
    
    @Test
    fun `test Vector2D dot and cross products`() {
        val v1 = Vector2D(1f, 0f)
        val v2 = Vector2D(0f, 1f)
        
        // Dot product
        assertEquals(0f, v1.dot(v2))
        assertEquals(1f, v1.dot(v1))
        
        // Cross product (2D perpendicular dot product)
        assertEquals(1f, v1.cross(v2))
        assertEquals(-1f, v2.cross(v1))
    }
    
    @Test
    fun `test Vector2D ptr and array conversion`() {
        val v = Vector2D(1.5f, 2.5f)
        
        val ptr = v.ptr()
        assertEquals(2, ptr.size)
        assertEquals(1.5f, ptr[0])
        assertEquals(2.5f, ptr[1])
        
        val array = v.toArray()
        assertEquals(ptr.size, array.size)
        assertEquals(ptr[0], array[0])
        assertEquals(ptr[1], array[1])
    }
    
    @Test
    fun `test Vector2D equality and approximation`() {
        val v1 = Vector2D(1f, 2f)
        val v2 = Vector2D(1f, 2f)
        val v3 = Vector2D(1.0001f, 2.0001f)
        
        // Exact equality
        assertEquals(v1, v2)
        assertNotEquals(v1, v3)
        
        // Approximate equality
        assertTrue(v1.equalsApprox(v3, 0.001f))
        assertFalse(v1.equalsApprox(v3, 0.00001f))
    }
    
    @Test
    fun `test Vector2D rotation`() {
        val v1 = Vector2D(1f, 0f)
        
        // Rotate 90 degrees (PI/2 radians)
        val rotated90 = v1.rotated(kotlin.math.PI.toFloat() / 2)
        assertTrue(rotated90.equalsApprox(Vector2D(0f, 1f), 1e-6f))
        
        // Rotate 180 degrees (PI radians)
        val rotated180 = v1.rotated(kotlin.math.PI.toFloat())
        assertTrue(rotated180.equalsApprox(Vector2D(-1f, 0f), 1e-6f))
        
        // Rotate 270 degrees (3*PI/2 radians)
        val rotated270 = v1.rotated(3 * kotlin.math.PI.toFloat() / 2)
        assertTrue(rotated270.equalsApprox(Vector2D(0f, -1f), 1e-6f))
        
        // Rotate 360 degrees (2*PI radians) - should be same as original
        val rotated360 = v1.rotated(2 * kotlin.math.PI.toFloat())
        assertTrue(rotated360.equalsApprox(v1, 1e-6f))
    }
    
    @Test
    fun `test Vector2D angle`() {
        val v1 = Vector2D(1f, 0f)  // 0 radians
        assertEquals(0f, v1.angle())
        
        val v2 = Vector2D(0f, 1f)  // PI/2 radians
        assertEquals(kotlin.math.PI.toFloat() / 2, v2.angle())
        
        val v3 = Vector2D(-1f, 0f)  // PI radians
        assertEquals(kotlin.math.PI.toFloat(), v3.angle())
        
        val v4 = Vector2D(0f, -1f)  // -PI/2 radians (or 3*PI/2)
        assertEquals(-kotlin.math.PI.toFloat() / 2, v4.angle())
    }
    
    @Test
    fun `test Vector2D companion object constants`() {
        assertEquals(Vector2D(0f, 0f), Vector2D.ZERO)
        assertEquals(Vector2D(1f, 0f), Vector2D.UNIT_X)
        assertEquals(Vector2D(0f, 1f), Vector2D.UNIT_Y)
    }
    
    @Test
    fun `test Vector2D polar coordinates`() {
        // Test along X axis
        val v1 = Vector2D.fromPolar(0f, 1f)
        assertTrue(v1.equalsApprox(Vector2D.UNIT_X, 1e-6f))
        
        // Test along Y axis
        val v2 = Vector2D.fromPolar(kotlin.math.PI.toFloat() / 2, 1f)
        assertTrue(v2.equalsApprox(Vector2D.UNIT_Y, 1e-6f))
        
        // Test 45 degrees
        val v3 = Vector2D.fromPolar(kotlin.math.PI.toFloat() / 4, sqrt(2f))
        assertTrue(v3.equalsApprox(Vector2D(1f, 1f), 1e-6f))
    }
    
    @Test
    fun `test Vector2D distance calculations`() {
        val v1 = Vector2D(0f, 0f)
        val v2 = Vector2D(3f, 4f)
        
        assertEquals(5f, Vector2D.distance(v1, v2))
        assertEquals(25f, Vector2D.distanceSquared(v1, v2))
        
        val v3 = Vector2D(1f, 1f)
        val v4 = Vector2D(4f, 5f)
        
        val expectedDist = sqrt(32f) // sqrt((3^2 + 4^2))
        assertEquals(expectedDist, Vector2D.distance(v3, v4))
        assertEquals(32f, Vector2D.distanceSquared(v3, v4))
    }
    
    @Test
    fun `test Vector2D extension function`() {
        val array = floatArrayOf(1.5f, 2.5f)
        val v = array.toVector2D()
        
        assertEquals(1.5f, v.x)
        assertEquals(2.5f, v.y)
    }
    
    @Test
    fun `test Vector2D string representation`() {
        val v = Vector2D(1.5f, 2.5f)
        assertEquals("Vector2D(1.5, 2.5)", v.toString())
    }
}