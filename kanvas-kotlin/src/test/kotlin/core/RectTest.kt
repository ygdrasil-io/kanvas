package core

import com.kanvas.core.Point
import com.kanvas.core.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the enhanced Rect class with SkRect-compatible functionality
 */
class RectTest {

    @Test
    fun `test intersectInPlace with overlapping rectangles`() {
        val rect1 = Rect(10f, 20f, 30f, 40f)
        val rect2 = Rect(20f, 30f, 40f, 50f)
        
        val result = rect1.intersectInPlace(rect2)
        
        assertTrue(result, "Intersection should be non-empty")
        assertEquals(20f, rect1.left, "Left should be 20")
        assertEquals(30f, rect1.top, "Top should be 30")
        assertEquals(30f, rect1.right, "Right should be 30")
        assertEquals(40f, rect1.bottom, "Bottom should be 40")
    }

    @Test
    fun `test intersectInPlace with no intersection`() {
        val rect1 = Rect(10f, 20f, 30f, 40f)
        val rect2 = Rect(50f, 60f, 70f, 80f)
        
        val result = rect1.intersectInPlace(rect2)
        
        assertFalse(result, "Intersection should be empty")
        // rect1 should remain unchanged when intersection is empty
        assertEquals(10f, rect1.left, "Left should remain 10")
        assertEquals(20f, rect1.top, "Top should remain 20")
        assertEquals(30f, rect1.right, "Right should remain 30")
        assertEquals(40f, rect1.bottom, "Bottom should remain 40")
    }

    @Test
    fun `test join with two rectangles`() {
        val rect1 = Rect(10f, 20f, 30f, 40f)
        val rect2 = Rect(20f, 30f, 40f, 50f)
        
        rect1.join(rect2)
        
        assertEquals(10f, rect1.left, "Left should be 10")
        assertEquals(20f, rect1.top, "Top should be 20")
        assertEquals(40f, rect1.right, "Right should be 40")
        assertEquals(50f, rect1.bottom, "Bottom should be 50")
    }

    @Test
    fun `test join with empty rectangle`() {
        val rect1 = Rect(10f, 20f, 30f, 40f)
        val rect2 = Rect.makeEmpty()
        
        rect1.join(rect2)
        
        // Should remain unchanged when joining with empty rectangle
        assertEquals(10f, rect1.left, "Left should remain 10")
        assertEquals(20f, rect1.top, "Top should remain 20")
        assertEquals(30f, rect1.right, "Right should remain 30")
        assertEquals(40f, rect1.bottom, "Bottom should remain 40")
    }

    @Test
    fun `test growToInclude with point inside rectangle`() {
        val rect = Rect(10f, 20f, 30f, 40f)
        
        rect.growToInclude(15f, 25f)
        
        // Should remain unchanged when point is inside
        assertEquals(10f, rect.left, "Left should remain 10")
        assertEquals(20f, rect.top, "Top should remain 20")
        assertEquals(30f, rect.right, "Right should remain 30")
        assertEquals(40f, rect.bottom, "Bottom should remain 40")
    }

    @Test
    fun `test growToInclude with point outside rectangle`() {
        val rect = Rect(10f, 20f, 30f, 40f)
        
        rect.growToInclude(5f, 45f)
        
        assertEquals(5f, rect.left, "Left should be 5")
        assertEquals(20f, rect.top, "Top should remain 20")
        assertEquals(30f, rect.right, "Right should remain 30")
        assertEquals(45f, rect.bottom, "Bottom should be 45")
    }

    @Test
    fun `test setBounds with multiple points`() {
        val rect = Rect.makeEmpty()
        val points = listOf(
            Point(10f, 20f),
            Point(5f, 25f),
            Point(15f, 15f),
            Point(8f, 30f)
        )
        
        rect.setBounds(points)
        
        assertEquals(5f, rect.left, "Left should be 5")
        assertEquals(15f, rect.top, "Top should be 15")
        assertEquals(15f, rect.right, "Right should be 15")
        assertEquals(30f, rect.bottom, "Bottom should be 30")
    }

    @Test
    fun `test setBounds with empty point list`() {
        val rect = Rect(10f, 20f, 30f, 40f)
        val points = emptyList<Point>()
        
        rect.setBounds(points)
        
        assertTrue(rect.isEmpty, "Rectangle should be empty")
    }

    @Test
    fun `test subtract with no intersection`() {
        val rect1 = Rect(10f, 20f, 30f, 40f)
        val rect2 = Rect(50f, 60f, 70f, 80f)
        
        val result = rect1.subtract(rect2)
        
        assertNotNull(result, "Result should not be null")
        assertEquals(10f, result.left, "Left should be 10")
        assertEquals(20f, result.top, "Top should be 20")
        assertEquals(30f, result.right, "Right should be 30")
        assertEquals(40f, result.bottom, "Bottom should be 40")
    }

    @Test
    fun `test subtract with partial overlap`() {
        val rect1 = Rect(10f, 20f, 50f, 60f)
        val rect2 = Rect(30f, 40f, 70f, 80f)
        
        val result = rect1.subtract(rect2)
        
        assertNotNull(result, "Result should not be null")
        // Should return the largest remaining rectangle
        // In this case, it could be the left strip (10,20,30,60)
        assertTrue(result.width > 0 && result.height > 0, "Result should have positive area")
    }

    @Test
    fun `test subtract with complete overlap`() {
        val rect1 = Rect(10f, 20f, 30f, 40f)
        val rect2 = Rect(10f, 20f, 30f, 40f)
        
        val result = rect1.subtract(rect2)
        
        assertNull(result, "Result should be null when completely overlapped")
    }

    @Test
    fun `test all enhanced methods together`() {
        // Test a sequence of operations similar to Skia usage
        val rect = Rect.makeEmpty()
        
        // Start with a point
        rect.growToInclude(10f, 20f)
        assertEquals(10f, rect.left, "Left should be 10")
        assertEquals(20f, rect.top, "Top should be 20")
        assertEquals(11f, rect.right, "Right should be 11")
        assertEquals(21f, rect.bottom, "Bottom should be 21")
        
        // Grow to include more points
        rect.growToInclude(30f, 40f)
        assertEquals(10f, rect.left, "Left should be 10")
        assertEquals(20f, rect.top, "Top should be 20")
        assertEquals(30f, rect.right, "Right should be 30")
        assertEquals(40f, rect.bottom, "Bottom should be 40")
        
        // Join with another rectangle
        val other = Rect(20f, 30f, 50f, 60f)
        rect.join(other)
        assertEquals(10f, rect.left, "Left should be 10")
        assertEquals(20f, rect.top, "Top should be 20")
        assertEquals(50f, rect.right, "Right should be 50")
        assertEquals(60f, rect.bottom, "Bottom should be 60")
        
        // Intersect with a smaller rectangle
        val intersectRect = Rect(15f, 25f, 45f, 55f)
        val intersectResult = rect.intersectInPlace(intersectRect)
        assertTrue(intersectResult, "Intersection should be non-empty")
        assertEquals(15f, rect.left, "Left should be 15")
        assertEquals(25f, rect.top, "Top should be 25")
        assertEquals(45f, rect.right, "Right should be 45")
        assertEquals(55f, rect.bottom, "Bottom should be 55")
    }

    @Test
    fun `test Skia compatibility edge cases`() {
        // Test empty rectangle behavior
        val emptyRect = Rect.makeEmpty()
        assertTrue(emptyRect.isEmpty, "Empty rectangle should be empty")
        
        // Test rectangle with negative coordinates
        val negativeRect = Rect(-10f, -20f, -5f, -15f)
        assertFalse(negativeRect.isEmpty, "Rectangle with negative coords should not be empty")
        assertEquals(5f, negativeRect.width, "Width should be 5")
        assertEquals(5f, negativeRect.height, "Height should be 5")
        
        // Test rectangle spanning zero
        val zeroRect = Rect(0f, 0f, 0f, 0f)
        assertTrue(zeroRect.isEmpty, "Zero rectangle should be empty")
    }

    @Test
    fun `test half width and height methods`() {
        val rect = Rect(10f, 20f, 30f, 40f)
        
        assertEquals(20f, rect.halfWidth(), "Half width should be 20")
        assertEquals(30f, rect.halfHeight(), "Half height should be 30")
    }

    @Test
    fun `test large rectangle creation methods`() {
        val largeRect = Rect.makeLarge()
        assertFalse(largeRect.isEmpty, "Large rectangle should not be empty")
        assertTrue(largeRect.width > 0, "Large rectangle should have positive width")
        assertTrue(largeRect.height > 0, "Large rectangle should have positive height")
        
        val largestRect = Rect.makeLargest()
        assertFalse(largestRect.isEmpty, "Largest rectangle should not be empty")
        assertTrue(largestRect.width > 0, "Largest rectangle should have positive width")
        assertTrue(largestRect.height > 0, "Largest rectangle should have positive height")
    }
}