package org.graphiks.math.matrix

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import org.graphiks.math.geometry.PathBuilder
import org.graphiks.math.geometry.PathFillSegmentF64
import org.graphiks.math.geometry.Point2F64

class PathFillTransformsF64Test {
    @Test
    fun `drawing verb before MoveTo starts at device origin`() {
        val source = PathBuilder().lineTo(2f, 1f).build()

        val mapped = Matrix3x3F32.translation(5f, 7f).mapPathFillInputF64(source)

        assertEquals(2, mapped.segmentCountI32)
        assertEquals(PathFillSegmentF64.MoveTo(Point2F64(5.0, 7.0)), mapped.segmentAtI32(0))
        assertEquals(PathFillSegmentF64.LineTo(Point2F64(7.0, 8.0)), mapped.segmentAtI32(1))
    }

    @Test
    fun `negative axis scale maps points and flips SVG arc sweep`() {
        val source = PathBuilder()
            .moveTo(1f, 2f)
            .arcTo(4f, 5f, 0f, largeArc = true, sweep = true, x = 2f, y = 1f)
            .build()

        val mapped = Matrix3x3F32.scaling(-2f, 3f).mapPathFillInputF64(source)
        val arc = mapped.segmentAtI32(1) as PathFillSegmentF64.ArcTo

        assertEquals(PathFillSegmentF64.MoveTo(Point2F64(-2.0, 6.0)), mapped.segmentAtI32(0))
        assertEquals(Point2F64(-4.0, 3.0), arc.point)
        assertEquals(8.0, arc.radius.x)
        assertEquals(15.0, arc.radius.y)
        assertEquals(180.0, arc.xAxisRotationDegreesF64)
        assertFalse(arc.sweep)
    }

    @Test
    fun `F64 transform does not round an intermediate coordinate to F32`() {
        val coordinate = 0.1f
        val scale = 1.0000001f
        val translation = 0.1f
        val source = PathBuilder()
            .moveTo(coordinate, 0f)
            .arcTo(1f, 1f, 0f, largeArc = false, sweep = true, x = coordinate, y = 0f)
            .build()
        val matrix = Matrix3x3F32(sx = scale, sy = 1f, tx = translation)

        val actual = matrix.mapPathFillInputF64(source).segmentAtI32(0) as PathFillSegmentF64.MoveTo
        val expected = canonicalF32(scale) * canonicalF32(coordinate) + canonicalF32(translation)
        val roundedViaPathF32 = Float.fromBits(expected.toFloat().toRawBits()).toDouble()

        assertEquals(expected, actual.point.x)
        assertNotEquals(roundedViaPathF32, actual.point.x)
        val arc = matrix.mapPathFillInputF64(source).segmentAtI32(1) as PathFillSegmentF64.ArcTo
        assertEquals(expected, arc.point.x)
        assertNotEquals(roundedViaPathF32, arc.point.x)
    }

    @Test
    fun `non finite matrix or mapped coordinate is rejected`() {
        val finitePath = PathBuilder().moveTo(1f, 2f).build()
        val nonFiniteMatrices = listOf(
            Matrix3x3F32(sx = Float.NaN),
            Matrix3x3F32(kx = Float.POSITIVE_INFINITY),
            Matrix3x3F32(tx = Float.NEGATIVE_INFINITY),
            Matrix3x3F32(ky = Float.NaN),
            Matrix3x3F32(sy = Float.POSITIVE_INFINITY),
            Matrix3x3F32(ty = Float.NEGATIVE_INFINITY),
            Matrix3x3F32(persp0 = Float.NaN),
            Matrix3x3F32(persp1 = Float.POSITIVE_INFINITY),
            Matrix3x3F32(persp2 = Float.NEGATIVE_INFINITY),
        )

        nonFiniteMatrices.forEach { matrix ->
            assertFailsWith<IllegalArgumentException> { matrix.mapPathFillInputF64(finitePath) }
        }
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { coordinate ->
            val nonFinitePath = PathBuilder().moveTo(coordinate, 0f).build()
            assertFailsWith<IllegalArgumentException> {
                Matrix3x3F32.Identity.mapPathFillInputF64(nonFinitePath)
            }
        }
    }

    private fun canonicalF32(value: Float): Double = Float.fromBits(value.toRawBits()).toDouble()
}
