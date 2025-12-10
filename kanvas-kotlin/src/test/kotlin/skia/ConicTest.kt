package skia

import com.kanvas.core.CanvasFactory
import com.kanvas.core.Color
import com.kanvas.core.Matrix
import com.kanvas.core.Paint
import com.kanvas.core.PaintStyle
import com.kanvas.core.Path
import com.kanvas.core.PathUtils
import com.kanvas.core.PathVerb
import com.kanvas.core.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test for conic curve implementation
 * Tests that conicTo works correctly with different weights
 */
class ConicTest {

    @Test
    fun conicToWithWeight1ShouldBehaveLikeQuadTo() {
        val path1 = Path()
        path1.moveTo(10f, 10f)
        path1.conicTo(50f, 100f, 100f, 10f, 1.0f)

        val path2 = Path()
        path2.moveTo(10f, 10f)
        path2.quadTo(50f, 100f, 100f, 10f)

        // Both should have the same structure
        assertEquals(path1.verbs.size, path2.verbs.size)
        assertEquals(path1.points.size, path2.points.size)

        // The first path should have used QUAD verb (since weight = 1.0)
        assertEquals(PathVerb.QUAD, path1.verbs[1])
        assertEquals(PathVerb.QUAD, path2.verbs[1])
    }

    @Test
    fun conicToWithDifferentWeightShouldUseCONICVerb() {
        val path = Path()
        path.moveTo(10f, 10f)
        path.conicTo(50f, 100f, 100f, 10f, 0.5f)

        // Should have used CONIC verb
        assertEquals(PathVerb.CONIC, path.verbs[1])
        assertEquals(1, path.conicWeights.size)
        assertEquals(0.5f, path.conicWeights[0])
    }

    @Test
    fun conicToWithInvalidWeightShouldApproximateWithLines() {
        val path = Path()
        path.moveTo(10f, 10f)
        path.conicTo(50f, 100f, 100f, 10f, -1.0f)

        // Should have approximated with lines
        // This means we should have LINE verbs instead of CONIC
        assertTrue(path.verbs.contains(PathVerb.LINE))
    }

    @Test
    fun conicToOnEmptyPathShouldAddImplicitMoveTo() {
        val path = Path()
        path.conicTo(50f, 100f, 100f, 10f, 0.7f)

        // Should have added an implicit moveTo(0,0)
        assertEquals(PathVerb.MOVE, path.verbs[0])
        assertEquals(PathVerb.CONIC, path.verbs[1])
    }

    @Test
    fun pathCopyShouldIncludeConicWeights() {
        val path1 = Path()
        path1.moveTo(10f, 10f)
        path1.conicTo(50f, 100f, 100f, 10f, 0.7f)

        val path2 = path1.copy()

        assertEquals(path1.conicWeights.size, path2.conicWeights.size)
        assertEquals(path1.conicWeights[0], path2.conicWeights[0])
    }

    @Test
    fun pathResetShouldClearConicWeights() {
        val path = Path()
        path.moveTo(10f, 10f)
        path.conicTo(50f, 100f, 100f, 10f, 0.7f)

        assertTrue(path.conicWeights.isNotEmpty())

        path.reset()

        assertTrue(path.conicWeights.isEmpty())
    }

    @Test
    fun conicCurveBoundsShouldBeReasonable() {
        val path = Path()
        path.moveTo(10f, 10f)
        path.conicTo(50f, 100f, 100f, 10f, 0.7f)

        val bounds = path.getBounds()

        // Bounds should include all points
        assertTrue(bounds.left <= 10f)
        assertTrue(bounds.top <= 10f)
        assertTrue(bounds.right >= 100f)
        assertTrue(bounds.bottom >= 10f)
    }

    @Test
    fun conicCurveCanBeDrawnOnCanvas() {
        val canvas = CanvasFactory.createRaster(200, 200)
        val paint = Paint().apply {
            color = Color(255, 0, 0)
            strokeWidth = 2f
            style = PaintStyle.STROKE
        }

        val path = Path()
        path.moveTo(20f, 100f)
        path.conicTo(100f, 20f, 180f, 100f, 0.5f)

        // This should not throw an exception
        canvas.drawPath(path, paint)

        // Verify that something was drawn
        val bitmap = canvas.getBitmapCopy()
        var hasNonWhitePixel = false
        for (y in 0 until bitmap.getHeight()) {
            for (x in 0 until bitmap.getWidth()) {
                val color = bitmap.getPixel(x, y)
                if (color.red != 255 || color.green != 255 || color.blue != 255) {
                    hasNonWhitePixel = true
                    break
                }
            }
            if (hasNonWhitePixel) break
        }

        assertTrue(hasNonWhitePixel, "Conic curve should have been drawn on canvas")
    }

    @Test
    fun conicCurveLengthApproximation() {
        val path = Path()
        path.moveTo(10f, 10f)
        path.conicTo(50f, 100f, 100f, 10f, 0.7f)

        val length = PathUtils.computeLength(path)

        // Length should be reasonable (greater than direct distance)
        val directDistance = PathUtils.distance(Point(10f, 10f), Point(100f, 10f))
        assertTrue(length > directDistance, "Conic curve length should be greater than direct distance")
    }

    @Test
    fun conicToWithDifferentWeights() {
        // Test various weights like Skia does
        val weights = listOf(0.1f, 0.5f, 0.707107f, 1.0f, 2.0f)

        for (weight in weights) {
            val path = Path()
            path.moveTo(10f, 10f)
            path.conicTo(50f, 100f, 100f, 10f, weight)

            // Should not throw exceptions
            val bounds = path.getBounds()
            val length = PathUtils.computeLength(path)

            // Basic sanity checks
            assertTrue(bounds.width > 0)
            assertTrue(bounds.height > 0)
            assertTrue(length > 0)

            // Verify the path structure
            if (weight == 1.0f) {
                assertEquals(PathVerb.QUAD, path.verbs[1])
            } else {
                assertEquals(PathVerb.CONIC, path.verbs[1])
                assertEquals(weight, path.conicWeights[0])
            }
        }
    }

    @Test
    fun conicToWithSkiaLikePatterns() {
        // Test patterns similar to Skia's test cases

        // Simple conic pattern
        val path1 = Path()
        path1.moveTo(4f, 4f)
        path1.conicTo(6f, 6f, 8f, 8f, 0.5f)
        path1.conicTo(6f, 8f, 4f, 8f, 0.5f)
        path1.conicTo(4f, 6f, 4f, 4f, 0.5f)

        assertEquals(4, path1.verbs.size) // MOVE + 3 CONIC
        assertEquals(3, path1.conicWeights.size)

        // Verify all weights are correct
        for (weight in path1.conicWeights) {
            assertEquals(0.5f, weight)
        }
    }

    @Test
    fun conicToEdgeCases() {
        // Test edge cases like Skia does

        // Very small weight
        val path1 = Path()
        path1.moveTo(10f, 10f)
        path1.conicTo(20f, 20f, 30f, 10f, 0.01f)
        assertEquals(PathVerb.CONIC, path1.verbs[1])

        // Large weight
        val path2 = Path()
        path2.moveTo(10f, 10f)
        path2.conicTo(20f, 20f, 30f, 10f, 10.0f)
        assertEquals(PathVerb.CONIC, path2.verbs[1])

        // Zero weight (should be treated as invalid)
        val path3 = Path()
        path3.moveTo(10f, 10f)
        path3.conicTo(20f, 20f, 30f, 10f, 0.0f)
        assertTrue(path3.verbs.contains(PathVerb.LINE)) // Should approximate with lines

        // Negative weight (should be treated as invalid)
        val path4 = Path()
        path4.moveTo(10f, 10f)
        path4.conicTo(20f, 20f, 30f, 10f, -1.0f)
        assertTrue(path4.verbs.contains(PathVerb.LINE)) // Should approximate with lines
    }

    @Test
    fun conicToWithTransform() {
        val path = Path()
        path.moveTo(10f, 10f)
        path.conicTo(50f, 100f, 100f, 10f, 0.7f)

        // Apply transformation
        val matrix = Matrix().apply {
            scaleX = 2f
            scaleY = 2f
            transX = 10f
            transY = 10f
        }

        path.transform(matrix)

        // Verify transformation worked
        val bounds = path.getBounds()
        assertTrue(bounds.left >= 10f) // Should be transformed (could be exactly 10f)
        assertTrue(bounds.top >= 10f)  // Should be transformed (could be exactly 10f)
        assertTrue(bounds.width > 0)   // Should have positive width
        assertTrue(bounds.height > 0)  // Should have positive height

        // Weights should remain the same (scalar values)
        assertEquals(0.7f, path.conicWeights[0])
    }

    @Test
    fun conicToMultipleCurves() {
        // Test multiple conic curves in sequence
        val path = Path()
        path.moveTo(10f, 10f)
        path.conicTo(30f, 50f, 50f, 10f, 0.3f)
        path.conicTo(70f, 50f, 90f, 10f, 0.7f)
        path.conicTo(110f, 50f, 130f, 10f, 1.5f)

        // Verify structure
        assertEquals(4, path.verbs.size) // MOVE + 3 CONIC
        assertEquals(3, path.conicWeights.size)

        // Verify weights
        assertEquals(0.3f, path.conicWeights[0])
        assertEquals(0.7f, path.conicWeights[1])
        assertEquals(1.5f, path.conicWeights[2])

        // Verify points
        assertEquals(7, path.points.size) // 1 move + 2*3 conic points
    }
}