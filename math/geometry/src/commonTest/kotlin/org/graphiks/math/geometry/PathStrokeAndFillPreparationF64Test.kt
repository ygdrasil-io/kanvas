package org.graphiks.math.geometry

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PathStrokeAndFillPreparationF64Test {
    @Test
    fun `stroke and fill publishes one winding union that covers the original contour and stroke`() {
        val geometryF32 = readyGeometryF32(closedRectanglePathF32(0f, 0f, 10f, 10f), widthF64 = 4.0)
        val contoursF32 = copyClosedContours(geometryF32)

        assertEquals(FillRule.WINDING, geometryF32.copyFillGeometryF32().fillRule)
        assertTrue(independentWindingContains(contoursF32, Point2F32(5f, 5f)))
        assertTrue(independentWindingContains(contoursF32, Point2F32(-1f, 5f)))
        assertFalse(independentWindingContains(contoursF32, Point2F32(-3f, 5f)))
    }

    @Test
    fun `opposite hole winding cannot cancel stroke and fill union`() {
        val path = PathBuilder(FillRule.WINDING)
            .moveTo(0f, 0f)
            .lineTo(20f, 0f)
            .lineTo(20f, 20f)
            .lineTo(0f, 20f)
            .close()
            .moveTo(6f, 6f)
            .lineTo(6f, 14f)
            .lineTo(14f, 14f)
            .lineTo(14f, 6f)
            .close()
            .build()

        val contoursF32 = copyClosedContours(readyGeometryF32(path, widthF64 = 4.0))

        assertTrue(independentWindingContains(contoursF32, Point2F32(1f, 10f)))
        assertFalse(independentWindingContains(contoursF32, Point2F32(10f, 10f)))
    }

    @Test
    fun `symmetric self intersection reports topology limit when f64 topology collapses in f32`() {
        val path = PathBuilder(FillRule.EVEN_ODD)
            .moveTo(0f, 0f)
            .lineTo(10f, 10f)
            .lineTo(0f, 10f)
            .lineTo(10f, 0f)
            .close()
            .build()

        // The F64 arrangement can reduce to non-zero sections that have no safe F32 embedding.
        val result = prepareProjectedPathStrokeGeometryF32(
            inputF64 = PathFillInputF64.fromPathF32(path),
            styleF64 = finiteStyleF64(2.0),
            mode = PathStrokeDrawMode.StrokeAndFill,
            projectionF64 = identityProjectionF64,
        )

        assertEquals(
            PathStrokeResourceLimitReason.TopologyLimit,
            assertIs<PathStrokePreparationResult.ResourceLimitExceeded>(result).reason,
        )
    }

    @Test
    fun `semi transparent stroke and fill has one public winding coverage authority`() {
        val geometryF32 = readyGeometryF32(closedRectanglePathF32(0f, 0f, 10f, 10f), widthF64 = 4.0)
        val fillGeometryF32 = geometryF32.copyFillGeometryF32()
        val authorityCountI32 = listOfNotNull(
            fillGeometryF32.copyDirectTriangleF32OrNull(),
            fillGeometryF32.copyStencilEdgeFanF32OrNull(),
        ).size

        assertEquals(FillRule.WINDING, fillGeometryF32.fillRule)
        assertEquals(1, authorityCountI32)
        assertTrue(independentWindingContains(copyClosedContours(geometryF32), Point2F32(-1f, 5f)))
    }

    @Test
    fun `zero finite width is exactly the device fill`() {
        val path = PathBuilder(FillRule.WINDING)
            .moveTo(0f, 0f)
            .lineTo(10f, 0f)
            .lineTo(10f, 10f)
            .lineTo(0f, 10f)
            .close()
            .moveTo(2f, 2f)
            .lineTo(2f, 8f)
            .lineTo(8f, 8f)
            .lineTo(8f, 2f)
            .close()
            .build()

        val expectedF32 = assertIs<PathFillPreparationResult.Ready>(
            preparePathFillGeometryF32(PathFillInputF64.fromPathF32(path)),
        ).geometryF32
        val actualF32 = readyGeometryF32(path, widthF64 = 0.0).copyFillGeometryF32()

        assertEquivalentPublicGeometry(expectedF32, actualF32)
    }

    @Test
    fun `zero finite width preserves the legacy empty stroke result`() {
        val result = prepareProjectedPathStrokeGeometryF32(
            inputF64 = PathFillInputF64.fromPathF32(closedRectanglePathF32(0f, 0f, 10f, 10f)),
            styleF64 = finiteStyleF64(0.0),
            mode = PathStrokeDrawMode.Stroke,
            projectionF64 = identityProjectionF64,
        )

        assertIs<PathStrokePreparationResult.Empty>(result)
    }

    @Test
    fun `topology work limit rejects the union before a geometry snapshot is published`() {
        val result = prepareProjectedPathStrokeGeometryF32(
            inputF64 = PathFillInputF64.fromPathF32(overlappingRectanglesPathF32()),
            styleF64 = finiteStyleF64(2.0),
            mode = PathStrokeDrawMode.StrokeAndFill,
            projectionF64 = identityProjectionF64,
        )

        assertEquals(
            PathStrokeResourceLimitReason.TopologyLimit,
            assertIs<PathStrokePreparationResult.ResourceLimitExceeded>(result).reason,
        )
    }

    private fun readyGeometryF32(path: PathF32, widthF64: Double): PathStrokeGeometryF32 =
        assertIs<PathStrokePreparationResult.Ready>(
            prepareProjectedPathStrokeGeometryF32(
                inputF64 = PathFillInputF64.fromPathF32(path),
                styleF64 = finiteStyleF64(widthF64),
                mode = PathStrokeDrawMode.StrokeAndFill,
                projectionF64 = identityProjectionF64,
            ),
        ).geometryF32

    private fun finiteStyleF64(
        widthF64: Double,
        join: PathStrokeJoin = PathStrokeJoin.Miter,
    ): PathStrokeStyleF64 = PathStrokeStyleF64(
        widthF64 = PathStrokeWidthF64.Finite(widthF64),
        cap = PathStrokeCap.Butt,
        join = join,
        miterLimitF64 = 4.0,
    )

    private fun closedRectanglePathF32(leftF32: Float, topF32: Float, rightF32: Float, bottomF32: Float): PathF32 =
        PathBuilder(FillRule.WINDING)
            .moveTo(leftF32, topF32)
            .lineTo(rightF32, topF32)
            .lineTo(rightF32, bottomF32)
            .lineTo(leftF32, bottomF32)
            .close()
            .build()

    private fun overlappingRectanglesPathF32(): PathF32 {
        val builder = PathBuilder(FillRule.WINDING)
        repeat(100) { indexI32 ->
            val offsetF32 = indexI32.toFloat() * 0.01f
            builder
                .moveTo(offsetF32, offsetF32)
                .lineTo(offsetF32 + 10f, offsetF32)
                .lineTo(offsetF32 + 10f, offsetF32 + 10f)
                .lineTo(offsetF32, offsetF32 + 10f)
                .close()
        }
        return builder.build()
    }

    private val identityProjectionF64: PathStrokeProjectionF64 = object : PathStrokeProjectionF64 {
        override fun projectPointF64(pointF64: Point2F64): PathStrokeProjectionPointResultF64 =
            PathStrokeProjectionPointResultF64.Ready(pointF64)

        override fun certifyOutlineIntervalF64(
            intervalF64: PathStrokeOutlineIntervalF64,
        ): PathStrokeProjectionIntervalResultF64 = PathStrokeProjectionIntervalResultF64.Bounded(
            intervalF64.sourceSagittaUpperBoundF64,
        )
    }
}

private fun copyClosedContours(geometryF32: PathStrokeGeometryF32): List<List<Point2F32>> {
    val fillGeometryF32 = geometryF32.copyFillGeometryF32()
    fillGeometryF32.copyDirectTriangleF32OrNull()?.let { triangleF32 ->
        val verticesF32 = triangleF32.copyVerticesF32()
        return listOf(
            listOf(
                Point2F32(verticesF32[0], verticesF32[1]),
                Point2F32(verticesF32[2], verticesF32[3]),
                Point2F32(verticesF32[4], verticesF32[5]),
            ),
        )
    }
    val fanF32 = assertNotNull(fillGeometryF32.copyStencilEdgeFanF32OrNull())
    val verticesF32 = fanF32.copyVerticesF32()
    val startsI32 = fanF32.copyContourStartsI32()
    return startsI32.indices.map { contourIndexI32 ->
        val startI32 = startsI32[contourIndexI32]
        val endExclusiveI32 = startsI32.getOrElse(contourIndexI32 + 1) { fanF32.edgeCountI32 }
        (startI32 until endExclusiveI32).map { edgeIndexI32 ->
            val offsetI32 = edgeIndexI32 * 6
            Point2F32(verticesF32[offsetI32 + 2], verticesF32[offsetI32 + 3])
        }
    }
}

private fun independentWindingContains(contoursF32: List<List<Point2F32>>, pointF32: Point2F32): Boolean {
    var windingI32 = 0
    contoursF32.forEach { contourF32 ->
        contourF32.indices.forEach { indexI32 ->
            val firstF32 = contourF32[indexI32]
            val secondF32 = contourF32[(indexI32 + 1) % contourF32.size]
            val sideF32 = (secondF32.x - firstF32.x) * (pointF32.y - firstF32.y) -
                (pointF32.x - firstF32.x) * (secondF32.y - firstF32.y)
            if (firstF32.y <= pointF32.y) {
                if (secondF32.y > pointF32.y && sideF32 > 0f) windingI32 += 1
            } else if (secondF32.y <= pointF32.y && sideF32 < 0f) {
                windingI32 -= 1
            }
        }
    }
    return windingI32 != 0
}

private fun assertEquivalentPublicGeometry(expectedF32: PathFillGeometryF32, actualF32: PathFillGeometryF32) {
    assertEquals(expectedF32.fillRule, actualF32.fillRule)
    assertEquals(expectedF32.emittedNonZeroClosedEdgeCountI32, actualF32.emittedNonZeroClosedEdgeCountI32)
    assertEquals(expectedF32.copyConservativeScissorI32(), actualF32.copyConservativeScissorI32())
    val expectedTriangleF32 = expectedF32.copyDirectTriangleF32OrNull()
    val actualTriangleF32 = actualF32.copyDirectTriangleF32OrNull()
    assertEquals(expectedTriangleF32 != null, actualTriangleF32 != null)
    if (expectedTriangleF32 != null && actualTriangleF32 != null) {
        assertContentEquals(expectedTriangleF32.copyVerticesF32(), actualTriangleF32.copyVerticesF32())
        assertContentEquals(expectedTriangleF32.copyIndicesI32(), actualTriangleF32.copyIndicesI32())
        return
    }
    assertNull(expectedTriangleF32)
    assertNull(actualTriangleF32)
    val expectedFanF32 = assertNotNull(expectedF32.copyStencilEdgeFanF32OrNull())
    val actualFanF32 = assertNotNull(actualF32.copyStencilEdgeFanF32OrNull())
    assertContentEquals(expectedFanF32.copyVerticesF32(), actualFanF32.copyVerticesF32())
    assertContentEquals(expectedFanF32.copyIndicesI32(), actualFanF32.copyIndicesI32())
    assertContentEquals(expectedFanF32.copyContourStartsI32(), actualFanF32.copyContourStartsI32())
}
