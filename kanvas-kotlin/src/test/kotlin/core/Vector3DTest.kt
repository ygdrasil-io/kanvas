package core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class Vector3DTest {
    
    @Test
    fun `test Vector3D creation and basic properties`() {
        val v1 = Vector3D(1f, 2f, 3f)
        assertEquals(1f, v1.x)
        assertEquals(2f, v1.y)
        assertEquals(3f, v1.z)
        
        val v2 = Vector3D()
        assertEquals(0f, v2.x)
        assertEquals(0f, v2.y)
        assertEquals(0f, v2.z)
        
        val v3 = Vector3D(2.5f)
        assertEquals(2.5f, v3.x)
        assertEquals(2.5f, v3.y)
        assertEquals(2.5f, v3.z)
        
        val v4 = Vector3D(floatArrayOf(1f, 4f, 9f))
        assertEquals(1f, v4.x)
        assertEquals(4f, v4.y)
        assertEquals(9f, v4.z)
    }
    
    @Test
    fun `test Vector3D length calculations`() {
        val v1 = Vector3D(3f, 4f, 0f)
        assertEquals(25f, v1.lengthSquared())
        assertEquals(5f, v1.length())
        
        val v2 = Vector3D(1f, 1f, 1f)
        assertEquals(3f, v2.lengthSquared())
        assertEquals(sqrt(3f), v2.length())
        
        val v3 = Vector3D()
        assertEquals(0f, v3.lengthSquared())
        assertEquals(0f, v3.length())
    }
    
    @Test
    fun `test Vector3D normalization`() {
        val v1 = Vector3D(3f, 4f, 0f)
        val normalized = v1.normalized()
        
        assertEquals(0.6f, normalized.x)
        assertEquals(0.8f, normalized.y)
        assertEquals(0f, normalized.z)
        assertEquals(1f, normalized.length())
        
        // Test zero vector normalization
        val v2 = Vector3D()
        val zeroNormalized = v2.normalized()
        assertEquals(0f, zeroNormalized.x)
        assertEquals(0f, zeroNormalized.y)
        assertEquals(0f, zeroNormalized.z)
    }
    
    @Test
    fun `test Vector3D arithmetic operations`() {
        val v1 = Vector3D(1f, 2f, 3f)
        val v2 = Vector3D(4f, 5f, 6f)
        
        // Addition
        val sum = v1 + v2
        assertEquals(Vector3D(5f, 7f, 9f), sum)
        
        // Subtraction
        val diff = v1 - v2
        assertEquals(Vector3D(-3f, -3f, -3f), diff)
        
        // Scalar multiplication
        val scaled = v1 * 2f
        assertEquals(Vector3D(2f, 4f, 6f), scaled)
        
        // Scalar division
        val divided = v2 / 2f
        assertEquals(Vector3D(2f, 2.5f, 3f), divided)
        
        // Negation
        val negated = -v1
        assertEquals(Vector3D(-1f, -2f, -3f), negated)
    }
    
    @Test
    fun `test Vector3D dot and cross products`() {
        val v1 = Vector3D(1f, 0f, 0f)
        val v2 = Vector3D(0f, 1f, 0f)
        
        // Dot product
        assertEquals(0f, v1.dot(v2))
        assertEquals(1f, v1.dot(v1))
        
        // Cross product
        val cross = v1.cross(v2)
        assertEquals(Vector3D(0f, 0f, 1f), cross)
        
        val v3 = Vector3D(0f, 0f, 1f)
        val cross2 = v2.cross(v3)
        assertEquals(Vector3D(1f, 0f, 0f), cross2)
    }
    
    @Test
    fun `test Vector3D ptr and array conversion`() {
        val v = Vector3D(1.5f, 2.5f, 3.5f)
        
        val ptr = v.ptr()
        assertEquals(3, ptr.size)
        assertEquals(1.5f, ptr[0])
        assertEquals(2.5f, ptr[1])
        assertEquals(3.5f, ptr[2])
        
        val array = v.toArray()
        assertEquals(ptr.size, array.size)
        assertEquals(ptr[0], array[0])
        assertEquals(ptr[1], array[1])
        assertEquals(ptr[2], array[2])
    }
    
    @Test
    fun `test Vector3D equality and approximation`() {
        val v1 = Vector3D(1f, 2f, 3f)
        val v2 = Vector3D(1f, 2f, 3f)
        val v3 = Vector3D(1.0001f, 2.0001f, 3.0001f)
        
        // Exact equality
        assertEquals(v1, v2)
        assertNotEquals(v1, v3)
        
        // Approximate equality
        assertTrue(v1.equalsApprox(v3, 0.001f))
        assertFalse(v1.equalsApprox(v3, 0.00001f))
    }
    
    @Test
    fun `test Vector3D companion object constants`() {
        assertEquals(Vector3D(0f, 0f, 0f), Vector3D.ZERO)
        assertEquals(Vector3D(1f, 0f, 0f), Vector3D.UNIT_X)
        assertEquals(Vector3D(0f, 1f, 0f), Vector3D.UNIT_Y)
        assertEquals(Vector3D(0f, 0f, 1f), Vector3D.UNIT_Z)
    }
    
    @Test
    fun `test Vector3D polar coordinates`() {
        // Test along X axis
        val v1 = Vector3D.fromPolar(0f, 0f, 1f)
        assertTrue(v1.equalsApprox(Vector3D.UNIT_X, 1e-6f))
        
        // Test along Y axis
        val v2 = Vector3D.fromPolar(kotlin.math.PI.toFloat() / 2, 0f, 1f)
        assertTrue(v2.equalsApprox(Vector3D.UNIT_Y, 1e-6f))
        
        // Test along Z axis
        val v3 = Vector3D.fromPolar(0f, kotlin.math.PI.toFloat() / 2, 1f)
        assertTrue(v3.equalsApprox(Vector3D.UNIT_Z, 1e-6f))
    }
    
    @Test
    fun `test Vector3D distance calculations`() {
        val v1 = Vector3D(0f, 0f, 0f)
        val v2 = Vector3D(3f, 4f, 0f)
        
        assertEquals(5f, Vector3D.distance(v1, v2))
        assertEquals(25f, Vector3D.distanceSquared(v1, v2))
        
        val v3 = Vector3D(1f, 1f, 1f)
        val v4 = Vector3D(4f, 5f, 6f)
        
        val expectedDist = sqrt(27f) // sqrt((3^2 + 4^2 + 5^2))
        assertEquals(expectedDist, Vector3D.distance(v3, v4))
        assertEquals(27f, Vector3D.distanceSquared(v3, v4))
    }
    
    @Test
    fun `test Vector3D extension function`() {
        val array = floatArrayOf(1.5f, 2.5f, 3.5f)
        val v = array.toVector3D()
        
        assertEquals(1.5f, v.x)
        assertEquals(2.5f, v.y)
        assertEquals(3.5f, v.z)
    }
    
    @Test
    fun `test Vector3D string representation`() {
        val v = Vector3D(1.5f, 2.5f, 3.5f)
        assertEquals("Vector3D(1.5, 2.5, 3.5)", v.toString())
    }
}