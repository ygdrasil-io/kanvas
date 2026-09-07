package org.graphiks.math.geometry

import org.graphiks.math.vector.Vector2F64
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertIs

class PathFillGeometryF32Test {
    @Test
    fun `device stream preserves its materialized implicit origin without inserting another`() {
        val result = preparePathFillGeometryF32(
            fillInput(
                PathFillSegmentF64.MoveTo(point(5.0, 7.0)),
                PathFillSegmentF64.LineTo(point(9.0, 7.0)),
                PathFillSegmentF64.LineTo(point(5.0, 11.0)),
                PathFillSegmentF64.Close,
            ),
        )

        val ready = assertIs<PathFillPreparationResult.Ready>(result)
        val triangle = assertNotNull(ready.geometryF32.copyDirectTriangleF32OrNull())
        assertContentEquals(
            floatArrayOf(5f, 7f, 9f, 7f, 5f, 11f),
            triangle.copyVerticesF32(),
        )
        assertEquals(3, ready.attemptedEdgeCountI32)
    }

    @Test
    fun `move closes an open contour and repeated close is a no op`() {
        val result = preparePathFillGeometryF32(
            fillInput(
                PathFillSegmentF64.MoveTo(point(0.0, 0.0)),
                PathFillSegmentF64.LineTo(point(2.0, 0.0)),
                PathFillSegmentF64.LineTo(point(0.0, 2.0)),
                PathFillSegmentF64.MoveTo(point(10.0, 0.0)),
                PathFillSegmentF64.LineTo(point(12.0, 0.0)),
                PathFillSegmentF64.LineTo(point(10.0, 2.0)),
                PathFillSegmentF64.Close,
                PathFillSegmentF64.Close,
            ),
        )

        val ready = assertIs<PathFillPreparationResult.Ready>(result)
        val fan = assertNotNull(ready.geometryF32.copyStencilEdgeFanF32OrNull())
        assertNull(ready.geometryF32.copyDirectTriangleF32OrNull())
        assertEquals(6, ready.attemptedEdgeCountI32)
        assertEquals(6, fan.edgeCountI32)
        assertContentEquals(intArrayOf(0, 3), fan.copyContourStartsI32())
    }

    @Test
    fun `arc whose endpoints coincide is a no op regardless of radius`() {
        val result = preparePathFillGeometryF32(
            fillInput(
                PathFillSegmentF64.MoveTo(point(0.0, 0.0)),
                PathFillSegmentF64.LineTo(point(4.0, 0.0)),
                PathFillSegmentF64.ArcTo(
                    radius = Vector2F64(99.0, 17.0),
                    xAxisRotationDegreesF64 = 73.0,
                    largeArc = true,
                    sweep = false,
                    point = point(4.0, 0.0),
                ),
                PathFillSegmentF64.LineTo(point(0.0, 4.0)),
                PathFillSegmentF64.Close,
            ),
        )

        val ready = assertIs<PathFillPreparationResult.Ready>(result)
        val fan = assertNotNull(ready.geometryF32.copyStencilEdgeFanF32OrNull())
        assertNull(ready.geometryF32.copyDirectTriangleF32OrNull())
        assertEquals(3, ready.attemptedEdgeCountI32)
        assertEquals(3, fan.edgeCountI32)
    }

    @Test
    fun `eccentric rotated arc keeps every sampled point within the sagitta tolerance`() {
        val fixture = EccentricArcFixture(
            start = point(79.71098790227113, 59.97919320328897),
            end = point(79.36314331652267, 59.93530189120385),
            radius = Vector2F64(100.0, 1.0),
            rotationDegreesF64 = 37.0,
            startAngleRadiansF64 = -0.06981317007977318,
            sweepAngleRadiansF64 = 0.17453292519943295,
            closingPoint = point(75.0, 65.0),
        )

        val fan = assertNotNull(
            assertIs<PathFillPreparationResult.Ready>(preparePathFillGeometryF32(eccentricArcInput(fixture)))
                .geometryF32.copyStencilEdgeFanF32OrNull(),
        )

        val maximumDistanceF64 = maximumSampledArcDistanceToFanF64(fixture, fan)
        assertTrue(
            maximumDistanceF64 <= 0.25,
            "maximum sampled arc distance was $maximumDistanceF64",
        )
    }

    @Test
    fun `nondegenerate eccentric rotated arc converges within the default sagitta tolerance`() {
        val fixture = EccentricArcFixture(
            start = point(19.314969523370742, 7.48325829376045),
            end = point(3.1247934920285765, 5.149111097923239),
            radius = Vector2F64(60.0, 3.0),
            rotationDegreesF64 = 29.0,
            startAngleRadiansF64 = -1.2217304763960306,
            sweepAngleRadiansF64 = 2.705260340591211,
            closingPoint = point(-15.0, 10.0),
        )

        val fan = assertNotNull(
            assertIs<PathFillPreparationResult.Ready>(preparePathFillGeometryF32(eccentricArcInput(fixture)))
                .geometryF32.copyStencilEdgeFanF32OrNull(),
        )

        assertTrue(fan.edgeCountI32 > 3)
        val maximumDistanceF64 = maximumSampledArcDistanceToFanF64(fixture, fan)
        assertTrue(
            maximumDistanceF64 <= 0.25,
            "maximum sampled arc distance was $maximumDistanceF64",
        )
    }

    @Test
    fun `closed quadratic path produces retained stencil geometry`() {
        val result = preparePathFillGeometryF32(
            fillInput(
                PathFillSegmentF64.MoveTo(point(0.0, 0.0)),
                PathFillSegmentF64.QuadTo(point(2.0, 4.0), point(4.0, 0.0)),
                PathFillSegmentF64.Close,
            ),
        )

        val ready = assertIs<PathFillPreparationResult.Ready>(result)
        assertNull(ready.geometryF32.copyDirectTriangleF32OrNull())
        assertTrue(assertNotNull(ready.geometryF32.copyStencilEdgeFanF32OrNull()).edgeCountI32 >= 3)
    }

    @Test
    fun `closed cubic path produces retained stencil geometry`() {
        val result = preparePathFillGeometryF32(
            fillInput(
                PathFillSegmentF64.MoveTo(point(0.0, 0.0)),
                PathFillSegmentF64.CubicTo(point(0.0, 4.0), point(4.0, 4.0), point(4.0, 0.0)),
                PathFillSegmentF64.Close,
            ),
        )

        val ready = assertIs<PathFillPreparationResult.Ready>(result)
        assertNull(ready.geometryF32.copyDirectTriangleF32OrNull())
        assertTrue(assertNotNull(ready.geometryF32.copyStencilEdgeFanF32OrNull()).edgeCountI32 >= 3)
    }

    @Test
    fun `F32 consecutive dedup keeps the attempted edge debit`() {
        val result = preparePathFillGeometryF32(
            fillInput(
                PathFillSegmentF64.MoveTo(point(0.0, 0.0)),
                PathFillSegmentF64.LineTo(point(1.0, 0.0)),
                PathFillSegmentF64.LineTo(point(1.0000000009313226, 0.0)),
                PathFillSegmentF64.LineTo(point(0.0, 1.0)),
                PathFillSegmentF64.Close,
            ),
        )

        val ready = assertIs<PathFillPreparationResult.Ready>(result)
        val triangle = assertNotNull(ready.geometryF32.copyDirectTriangleF32OrNull())
        assertEquals(4, ready.attemptedEdgeCountI32)
        assertEquals(3, ready.geometryF32.emittedNonZeroClosedEdgeCountI32)
        assertContentEquals(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f), triangle.copyVerticesF32())
    }

    @Test
    fun `contours with fewer than three distinct vertices or entirely collinear vertices are empty`() {
        val fewerThanThree = preparePathFillGeometryF32(
            fillInput(
                PathFillSegmentF64.MoveTo(point(0.0, 0.0)),
                PathFillSegmentF64.LineTo(point(1.0, 0.0)),
                PathFillSegmentF64.Close,
            ),
        )
        val collinear = preparePathFillGeometryF32(
            fillInput(
                PathFillSegmentF64.MoveTo(point(0.0, 0.0)),
                PathFillSegmentF64.LineTo(point(1.0, 0.0)),
                PathFillSegmentF64.LineTo(point(2.0, 0.0)),
                PathFillSegmentF64.Close,
            ),
        )

        assertEquals(2, assertIs<PathFillPreparationResult.Empty>(fewerThanThree).attemptedEdgeCountI32)
        assertEquals(3, assertIs<PathFillPreparationResult.Empty>(collinear).attemptedEdgeCountI32)
    }

    @Test
    fun `retrace is retained by the stencil route`() {
        val result = preparePathFillGeometryF32(
            fillInput(
                PathFillSegmentF64.MoveTo(point(0.0, 0.0)),
                PathFillSegmentF64.LineTo(point(4.0, 0.0)),
                PathFillSegmentF64.LineTo(point(0.0, 0.0)),
                PathFillSegmentF64.LineTo(point(0.0, 4.0)),
                PathFillSegmentF64.Close,
            ),
        )

        val ready = assertIs<PathFillPreparationResult.Ready>(result)
        assertNull(ready.geometryF32.copyDirectTriangleF32OrNull())
        assertEquals(4, assertNotNull(ready.geometryF32.copyStencilEdgeFanF32OrNull()).edgeCountI32)
    }

    @Test
    fun `self intersecting contour is retained by the stencil route`() {
        val result = preparePathFillGeometryF32(
            fillInput(
                PathFillSegmentF64.MoveTo(point(0.0, 0.0)),
                PathFillSegmentF64.LineTo(point(4.0, 4.0)),
                PathFillSegmentF64.LineTo(point(0.0, 4.0)),
                PathFillSegmentF64.LineTo(point(4.0, 0.0)),
                PathFillSegmentF64.Close,
            ),
        )

        val ready = assertIs<PathFillPreparationResult.Ready>(result)
        assertNull(ready.geometryF32.copyDirectTriangleF32OrNull())
        assertEquals(4, assertNotNull(ready.geometryF32.copyStencilEdgeFanF32OrNull()).edgeCountI32)
    }

    @Test
    fun `strict winding triangle uses direct triangle geometry with exact costs`() {
        val result = preparePathFillGeometryF32(
            fillInput(
                PathFillSegmentF64.MoveTo(point(1.0, 2.0)),
                PathFillSegmentF64.LineTo(point(5.0, 2.0)),
                PathFillSegmentF64.LineTo(point(1.0, 6.0)),
                PathFillSegmentF64.Close,
            ),
        )

        val ready = assertIs<PathFillPreparationResult.Ready>(result)
        val geometry = ready.geometryF32
        val triangle = assertNotNull(geometry.copyDirectTriangleF32OrNull())
        assertNull(geometry.copyStencilEdgeFanF32OrNull())
        assertEquals(3, ready.attemptedEdgeCountI32)
        assertEquals(3, geometry.emittedNonZeroClosedEdgeCountI32)
        assertEquals(3L, geometry.vertexCostI64)
        assertEquals(3L, geometry.indexCostI64)
        assertEquals(RectI32(1, 2, 5, 6), geometry.copyConservativeScissorI32())
        assertContentEquals(floatArrayOf(1f, 2f, 5f, 2f, 1f, 6f), triangle.copyVerticesF32())
        assertContentEquals(intArrayOf(0, 1, 2), triangle.copyIndicesI32())
    }

    @Test
    fun `snapshot byte cost includes every retained numeric fill payload`() {
        val direct = assertIs<PathFillPreparationResult.Ready>(
            preparePathFillGeometryF32(
                fillInput(
                    PathFillSegmentF64.MoveTo(point(0.0, 0.0)),
                    PathFillSegmentF64.LineTo(point(2.0, 0.0)),
                    PathFillSegmentF64.LineTo(point(0.0, 2.0)),
                    PathFillSegmentF64.Close,
                ),
            ),
        ).geometryF32
        val fan = assertIs<PathFillPreparationResult.Ready>(
            preparePathFillGeometryF32(
                fillInput(
                    PathFillSegmentF64.MoveTo(point(0.0, 0.0)),
                    PathFillSegmentF64.LineTo(point(2.0, 0.0)),
                    PathFillSegmentF64.LineTo(point(2.0, 2.0)),
                    PathFillSegmentF64.LineTo(point(0.0, 2.0)),
                    PathFillSegmentF64.Close,
                ),
            ),
        ).geometryF32

        assertEquals(52L, direct.snapshotByteCostI64)
        assertEquals(164L, fan.snapshotByteCostI64)
    }

    @Test
    fun `even odd triangle uses a stencil edge fan`() {
        val result = preparePathFillGeometryF32(
            fillInput(
                PathFillSegmentF64.MoveTo(point(0.0, 0.0)),
                PathFillSegmentF64.LineTo(point(4.0, 0.0)),
                PathFillSegmentF64.LineTo(point(0.0, 4.0)),
                PathFillSegmentF64.Close,
                fillRule = FillRule.EVEN_ODD,
            ),
        )

        val ready = assertIs<PathFillPreparationResult.Ready>(result)
        assertNull(ready.geometryF32.copyDirectTriangleF32OrNull())
        assertEquals(3, assertNotNull(ready.geometryF32.copyStencilEdgeFanF32OrNull()).edgeCountI32)
    }

    @Test
    fun `concave path emits an anchored edge fan with sequential indices`() {
        val result = preparePathFillGeometryF32(
            fillInput(
                PathFillSegmentF64.MoveTo(point(0.0, 0.0)),
                PathFillSegmentF64.LineTo(point(4.0, 0.0)),
                PathFillSegmentF64.LineTo(point(4.0, 4.0)),
                PathFillSegmentF64.LineTo(point(2.0, 2.0)),
                PathFillSegmentF64.LineTo(point(0.0, 4.0)),
                PathFillSegmentF64.Close,
            ),
        )

        val ready = assertIs<PathFillPreparationResult.Ready>(result)
        val geometry = ready.geometryF32
        val fan = assertNotNull(geometry.copyStencilEdgeFanF32OrNull())
        assertEquals(5, fan.edgeCountI32)
        assertEquals(15, fan.vertexCountI32)
        assertEquals(15, fan.indexCountI32)
        assertEquals(19L, geometry.vertexCostI64)
        assertEquals(21L, geometry.indexCostI64)
        assertContentEquals(
            floatArrayOf(
                0f, 0f, 0f, 0f, 4f, 0f,
                0f, 0f, 4f, 0f, 4f, 4f,
                0f, 0f, 4f, 4f, 2f, 2f,
                0f, 0f, 2f, 2f, 0f, 4f,
                0f, 0f, 0f, 4f, 0f, 0f,
            ),
            fan.copyVerticesF32(),
        )
        assertContentEquals(intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14), fan.copyIndicesI32())
        assertContentEquals(intArrayOf(0), fan.copyContourStartsI32())
    }

    @Test
    fun `geometry snapshots cannot be mutated through copied arrays or scissor`() {
        val direct = assertIs<PathFillPreparationResult.Ready>(
            preparePathFillGeometryF32(
                fillInput(
                    PathFillSegmentF64.MoveTo(point(0.0, 0.0)),
                    PathFillSegmentF64.LineTo(point(2.0, 0.0)),
                    PathFillSegmentF64.LineTo(point(0.0, 2.0)),
                    PathFillSegmentF64.Close,
                ),
            ),
        ).geometryF32
        val triangle = assertNotNull(direct.copyDirectTriangleF32OrNull())
        val triangleVertices = triangle.copyVerticesF32()
        val triangleIndices = triangle.copyIndicesI32()
        val scissor = direct.copyConservativeScissorI32()
        triangleVertices[0] = 99f
        triangleIndices[0] = 99
        scissor.left = 99

        assertContentEquals(floatArrayOf(0f, 0f, 2f, 0f, 0f, 2f), triangle.copyVerticesF32())
        assertContentEquals(intArrayOf(0, 1, 2), triangle.copyIndicesI32())
        assertEquals(RectI32(0, 0, 2, 2), direct.copyConservativeScissorI32())

        val fan = assertNotNull(
            assertIs<PathFillPreparationResult.Ready>(
                preparePathFillGeometryF32(
                    fillInput(
                        PathFillSegmentF64.MoveTo(point(0.0, 0.0)),
                        PathFillSegmentF64.LineTo(point(3.0, 0.0)),
                        PathFillSegmentF64.LineTo(point(3.0, 3.0)),
                        PathFillSegmentF64.LineTo(point(1.0, 1.0)),
                        PathFillSegmentF64.LineTo(point(0.0, 3.0)),
                        PathFillSegmentF64.Close,
                    ),
                ),
            ).geometryF32.copyStencilEdgeFanF32OrNull(),
        )
        val fanVertices = fan.copyVerticesF32()
        val fanIndices = fan.copyIndicesI32()
        val contourStarts = fan.copyContourStartsI32()
        fanVertices[0] = 99f
        fanIndices[0] = 99
        contourStarts[0] = 99

        assertContentEquals(floatArrayOf(0f, 0f, 0f, 0f, 3f, 0f), fan.copyVerticesF32().copyOfRange(0, 6))
        assertContentEquals(intArrayOf(0, 1, 2), fan.copyIndicesI32().copyOfRange(0, 3))
        assertContentEquals(intArrayOf(0), fan.copyContourStartsI32())
    }

    private fun fillInput(
        vararg segments: PathFillSegmentF64,
        fillRule: FillRule = FillRule.WINDING,
    ): PathFillInputF64 = PathFillInputF64.of(fillRule, segments.asList())

    private fun eccentricArcInput(fixture: EccentricArcFixture): PathFillInputF64 = fillInput(
        PathFillSegmentF64.MoveTo(fixture.start),
        PathFillSegmentF64.ArcTo(
            radius = fixture.radius,
            xAxisRotationDegreesF64 = fixture.rotationDegreesF64,
            largeArc = false,
            sweep = true,
            point = fixture.end,
        ),
        PathFillSegmentF64.LineTo(fixture.closingPoint),
        PathFillSegmentF64.Close,
    )

    private fun maximumSampledArcDistanceToFanF64(
        fixture: EccentricArcFixture,
        fan: PathStencilEdgeFanF32,
    ): Double {
        val verticesF32 = fan.copyVerticesF32()
        val arcEdgeCountI32 = fan.edgeCountI32 - 2
        var maximumDistanceF64 = 0.0
        for (sampleIndexI32 in 0..1_024) {
            val point = fixture.pointAt(sampleIndexI32.toDouble() / 1_024.0)
            var minimumDistanceF64 = Double.POSITIVE_INFINITY
            for (edgeIndexI32 in 0 until arcEdgeCountI32) {
                val offsetI32 = edgeIndexI32 * 6
                val start = Point2F64(
                    verticesF32[offsetI32 + 2].toDouble(),
                    verticesF32[offsetI32 + 3].toDouble(),
                )
                val end = Point2F64(
                    verticesF32[offsetI32 + 4].toDouble(),
                    verticesF32[offsetI32 + 5].toDouble(),
                )
                minimumDistanceF64 = minOf(minimumDistanceF64, distanceToSegmentF64(point, start, end))
            }
            maximumDistanceF64 = maxOf(maximumDistanceF64, minimumDistanceF64)
        }
        return maximumDistanceF64
    }

    private fun EccentricArcFixture.pointAt(parameterF64: Double): Point2F64 {
        val angleRadiansF64 = startAngleRadiansF64 + sweepAngleRadiansF64 * parameterF64
        val rotationRadiansF64 = rotationDegreesF64 * PI / 180.0
        val cosRotationF64 = cos(rotationRadiansF64)
        val sinRotationF64 = sin(rotationRadiansF64)
        val cosAngleF64 = cos(angleRadiansF64)
        val sinAngleF64 = sin(angleRadiansF64)
        return Point2F64(
            radius.x * cosRotationF64 * cosAngleF64 - radius.y * sinRotationF64 * sinAngleF64,
            radius.x * sinRotationF64 * cosAngleF64 + radius.y * cosRotationF64 * sinAngleF64,
        )
    }

    private fun distanceToSegmentF64(point: Point2F64, start: Point2F64, end: Point2F64): Double {
        val deltaX = end.x - start.x
        val deltaY = end.y - start.y
        val lengthSquaredF64 = deltaX * deltaX + deltaY * deltaY
        if (lengthSquaredF64 == 0.0) return sqrt((point.x - start.x) * (point.x - start.x) + (point.y - start.y) * (point.y - start.y))
        val projectionF64 = (
            (point.x - start.x) * deltaX + (point.y - start.y) * deltaY
            ) / lengthSquaredF64
        val clampedProjectionF64 = projectionF64.coerceIn(0.0, 1.0)
        val distanceX = point.x - (start.x + clampedProjectionF64 * deltaX)
        val distanceY = point.y - (start.y + clampedProjectionF64 * deltaY)
        return sqrt(distanceX * distanceX + distanceY * distanceY)
    }

    private fun point(x: Double, y: Double): Point2F64 = Point2F64(x, y)

    private data class EccentricArcFixture(
        val start: Point2F64,
        val end: Point2F64,
        val radius: Vector2F64,
        val rotationDegreesF64: Double,
        val startAngleRadiansF64: Double,
        val sweepAngleRadiansF64: Double,
        val closingPoint: Point2F64,
    )
}
