package org.graphiks.math.geometry

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PathOpsF32Test {
    private val left = PathBuilder().addRect(RectF32.ofLTRB(0f, 0f, 10f, 10f)).build()
    private val right = PathBuilder().addRect(RectF32.ofLTRB(5f, 0f, 15f, 10f)).build()

    @Test
    fun `boolean operations are observed through membership`() {
        val intersection = PathOpsF32.op(left, right, PathBooleanOp.INTERSECT)
        val union = PathOpsF32.op(left, right, PathBooleanOp.UNION)
        val difference = PathOpsF32.op(left, right, PathBooleanOp.DIFFERENCE)
        val xor = PathOpsF32.op(left, right, PathBooleanOp.XOR)
        val reverseDifference = PathOpsF32.op(left, right, PathBooleanOp.REVERSE_DIFFERENCE)

        assertTrue(PathAnalysisF32.contains(intersection, Point2F32(7f, 5f)))
        assertFalse(PathAnalysisF32.contains(intersection, Point2F32(2f, 5f)))
        assertTrue(PathAnalysisF32.contains(union, Point2F32(2f, 5f)))
        assertTrue(PathAnalysisF32.contains(union, Point2F32(12f, 5f)))
        assertTrue(PathAnalysisF32.contains(difference, Point2F32(2f, 5f)))
        assertFalse(PathAnalysisF32.contains(difference, Point2F32(7f, 5f)))
        assertTrue(PathAnalysisF32.contains(xor, Point2F32(2f, 5f)))
        assertFalse(PathAnalysisF32.contains(xor, Point2F32(7f, 5f)))
        assertTrue(PathAnalysisF32.contains(reverseDifference, Point2F32(12f, 5f)))
    }

    @Test
    fun `operations preserve an immutable PathF32 input contract`() {
        val result = PathOpsF32.op(left, right, PathBooleanOp.UNION)

        assertTrue(PathAnalysisF32.contains(left, Point2F32(2f, 5f)))
        assertFalse(PathAnalysisF32.contains(left, Point2F32(12f, 5f)))
        assertTrue(PathAnalysisF32.contains(result, Point2F32(12f, 5f)))
    }

    @Test
    fun `boolean operations combine non rectangular closed geometry`() {
        val first = PathBuilder().moveTo(0f, 0f).lineTo(10f, 0f).lineTo(5f, 10f).close().build()
        val second = PathBuilder().moveTo(0f, 10f).lineTo(5f, 0f).lineTo(10f, 10f).close().build()

        val union = PathOpsF32.op(first, second, PathBooleanOp.UNION)
        val intersection = PathOpsF32.op(first, second, PathBooleanOp.INTERSECT)

        assertTrue(PathAnalysisF32.contains(union, Point2F32(5f, 1f)))
        assertTrue(PathAnalysisF32.contains(union, Point2F32(5f, 9f)))
        assertTrue(PathAnalysisF32.contains(intersection, Point2F32(5f, 5f)))
        assertFalse(PathAnalysisF32.contains(intersection, Point2F32(1f, 1f)))
    }
}
