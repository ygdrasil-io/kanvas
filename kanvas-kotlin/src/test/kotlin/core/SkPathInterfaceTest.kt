package core

import com.kanvas.core.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * Test suite for SkPathInterface implementation
 * Verifies that Kanvas Path implements all required Skia-compatible methods
 */
class SkPathInterfaceTest {

    @Test
    fun `test SkPathInterface implementation`() {
        // Create a path and verify it implements the interface
        val path = Path()
        assertTrue(path is SkPathInterface, "Path should implement SkPathInterface")
    }

    @Test
    fun `test basic path construction`() {
        val path = Path()
        
        // Test moveTo
        path.moveTo(10f, 20f)
        assertFalse(path.isEmpty())
        
        // Test lineTo
        path.lineTo(30f, 40f)
        assertEquals(2, path.getPointCount())
        
        // Test quadTo
        path.quadTo(50f, 60f, 70f, 80f)
        assertEquals(4, path.getPointCount())
        
        // Test conicTo
        path.conicTo(90f, 100f, 110f, 120f, 0.5f)
        assertEquals(6, path.getPointCount())
        
        // Test cubicTo
        path.cubicTo(130f, 140f, 150f, 160f, 170f, 180f)
        assertEquals(9, path.getPointCount())
        
        // Test close
        path.close()
        assertTrue(path.getVerbs().last() == PathVerb.CLOSE)
    }

    @Test
    fun `test relative path construction`() {
        val path = Path()
        path.moveTo(0f, 0f)
        
        // Test rQuadTo
        path.rQuadTo(10f, 20f, 30f, 40f)
        assertEquals(3, path.getPointCount())
        
        // Verify the points are calculated correctly
        val points = path.getPoints()
        assertEquals(0f, points[0].x)
        assertEquals(0f, points[0].y)
        assertEquals(10f, points[1].x)
        assertEquals(20f, points[1].y)
        assertEquals(30f, points[2].x)
        assertEquals(40f, points[2].y)
        
        // Test rCubicTo
        path.rCubicTo(5f, 10f, 15f, 20f, 25f, 30f)
        assertEquals(6, path.getPointCount())
    }

    @Test
    fun `test arc methods`() {
        val path = Path()
        
        // Test arcTo
        val rect = Rect(0f, 0f, 100f, 100f)
        path.arcTo(rect, 0f, 90f, false)
        assertFalse(path.isEmpty())
        
        // Test arcTo with forceMoveTo
        val path2 = Path()
        path2.arcTo(rect, 45f, 180f, true)
        assertFalse(path2.isEmpty())
        
        // Test addArc
        val path3 = Path()
        path3.moveTo(50f, 0f)  // Start at top center
        path3.addArc(rect, 0f, 180f)
        assertTrue(path3.getPointCount() > 2)
    }

    @Test
    fun `test shape addition methods`() {
        val path = Path()
        
        // Test addRect
        val rect = Rect(10f, 20f, 100f, 200f)
        path.addRect(rect, PathDirection.CW)
        assertFalse(path.isEmpty())
        assertTrue(path.getVerbs().last() == PathVerb.CLOSE)
        
        // Test addOval
        val path2 = Path()
        path2.addOval(rect, PathDirection.CCW)
        assertFalse(path2.isEmpty())
        
        // Test addCircle
        val path3 = Path()
        path3.addCircle(50f, 50f, 40f, PathDirection.CW)
        assertFalse(path3.isEmpty())
    }

    @Test
    fun `test path properties`() {
        val path = Path()
        
        // Test fill type
        assertEquals(FillType.WINDING, path.getFillType())
        path.setFillType(FillType.EVEN_ODD)
        assertEquals(FillType.EVEN_ODD, path.getFillType())
        
        // Test isEmpty
        assertTrue(path.isEmpty())
        path.moveTo(10f, 20f)
        assertFalse(path.isEmpty())
    }

    @Test
    fun `test path analysis`() {
        val path = Path()
        
        // Test bounds on empty path
        val emptyBounds = path.getBounds()
        assertEquals(Rect(0f, 0f, 0f, 0f), emptyBounds)
        
        // Test bounds on non-empty path
        path.moveTo(10f, 20f)
        path.lineTo(100f, 200f)
        path.quadTo(150f, 250f, 50f, 300f)
        
        val bounds = path.getBounds()
        assertTrue(bounds.left <= 10f)
        assertTrue(bounds.top <= 20f)
        assertTrue(bounds.right >= 150f)
        assertTrue(bounds.bottom >= 300f)
        
        // Test tight bounds
        val tightBounds = path.computeTightBounds()
        assertNotNull(tightBounds)
        
        // Test length
        val length = path.getLength()
        assertTrue(length > 0)
    }

    @Test
    fun `test path operations`() {
        val path = Path()
        
        // Create a simple path
        path.moveTo(0f, 0f)
        path.lineTo(100f, 0f)
        path.lineTo(100f, 100f)
        path.lineTo(0f, 100f)
        path.close()
        
        // Test transform
        val matrix = Matrix().apply {
            scaleX = 2f
            scaleY = 2f
            transX = 10f
            transY = 20f
        }
        
        val transformedPath = path.transform(matrix)
        assertFalse(transformedPath.isEmpty())
        
        // Verify transformation
        val transformedBounds = transformedPath.getBounds()
        assertTrue(abs(transformedBounds.left - 10f) < 1e-5f)
        assertTrue(abs(transformedBounds.top - 20f) < 1e-5f)
        assertTrue(abs(transformedBounds.right - 210f) < 1e-5f)
        assertTrue(abs(transformedBounds.bottom - 220f) < 1e-5f)
        
        // Test offset
        val offsetPath = path.offset(5f, 10f)
        assertFalse(offsetPath.isEmpty())
        
        val offsetBounds = offsetPath.getBounds()
        assertTrue(abs(offsetBounds.left - 5f) < 1e-5f)
        assertTrue(abs(offsetBounds.top - 10f) < 1e-5f)
        
        // Test asWinding
        path.setFillType(FillType.EVEN_ODD)
        val windingPath = path.asWinding()
        assertEquals(FillType.WINDING, windingPath.getFillType())
    }

    @Test
    fun `test path contour operations`() {
        val path = Path()
        
        // Test empty path
        assertEquals(0, path.getPointCount())
        assertTrue(path.getPoints().isEmpty())
        assertTrue(path.getVerbs().isEmpty())
        
        // Test non-empty path
        path.moveTo(10f, 20f)
        path.lineTo(30f, 40f)
        path.quadTo(50f, 60f, 70f, 80f)
        
        assertEquals(4, path.getPointCount())
        assertEquals(3, path.getVerbs().size)
        assertEquals(PathVerb.MOVE, path.getVerbs()[0])
        assertEquals(PathVerb.LINE, path.getVerbs()[1])
        assertEquals(PathVerb.QUAD, path.getVerbs()[2])
    }

    @Test
    fun `test Skia compatibility patterns`() {
        val path = Path()
        
        // Test Skia-like pattern: conicTo with weight = 1.0 (equivalent to quadTo)
        path.moveTo(0f, 0f)
        path.conicTo(50f, 100f, 100f, 0f, 1.0f)
        
        // Test Skia-like pattern: complex path with mixed verbs
        path.lineTo(150f, 0f)
        path.quadTo(200f, 50f, 150f, 100f)
        path.cubicTo(100f, 150f, 50f, 150f, 0f, 100f)
        path.close()
        
        // Verify the path is valid
        assertFalse(path.isEmpty())
        assertTrue(path.getBounds().width > 0)
        assertTrue(path.getBounds().height > 0)
        
        // Test that the path can be transformed
        val matrix = Matrix().apply { rotateZ(45f) }
        val transformed = path.transform(matrix)
        assertFalse(transformed.isEmpty())
    }
}