package org.graphiks.math.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InversePathPreparationF64Test {
    @Test
    fun `inverse empty path covers its complete finite domain`() {
        val domainI32 = RectI32(13, 17, 29, 31)

        val result = assertIs<InversePathPreparationResult.Ready>(
            prepareInversePathGeometryF32(
                finiteFillF64 = PathFillInputF64.of(FillRule.INVERSE_WINDING, emptyList()),
                deviceStrokeOutlineF64 = null,
                styleF64 = null,
                mode = InversePathDrawMode.Fill,
                domainI32 = domainI32,
                policyF64 = PathStrokePolicyF64(),
            ),
        )

        assertIs<InverseInteriorCoverageF32.Zero>(result.geometryF32.interiorCoverageF32)
        assertEquals(domainI32, result.geometryF32.copyDomainI32())
    }

    @Test
    fun `inverse winding and even odd preserve finite interiors independently of their domain`() {
        listOf(FillRule.INVERSE_WINDING, FillRule.INVERSE_EVEN_ODD).forEach { fillRule ->
            val result = assertIs<InversePathPreparationResult.Ready>(
                prepareInversePathGeometryF32(
                    finiteFillF64 = triangleInputF64(fillRule),
                    deviceStrokeOutlineF64 = null,
                    styleF64 = null,
                    mode = InversePathDrawMode.Fill,
                    domainI32 = RectI32(-20, -10, 40, 30),
                    policyF64 = PathStrokePolicyF64(),
                ),
            )

            val interiorF32 = assertIs<InverseInteriorCoverageF32.Geometry>(result.geometryF32.interiorCoverageF32)
                .copyGeometryF32()
            assertEquals(fillRule.toFiniteFillRule(), interiorF32.fillRule)
            assertEquals(RectI32(-20, -10, 40, 30), result.geometryF32.copyDomainI32())
            val mutableScissorI32 = interiorF32.copyConservativeScissorI32()
            mutableScissorI32.left = -99
            assertTrue(
                assertIs<InverseInteriorCoverageF32.Geometry>(result.geometryF32.interiorCoverageF32)
                    .copyGeometryF32().copyConservativeScissorI32().left != -99,
            )
        }
    }

    @Test
    fun `inverse stroke and fill excludes the device stroke outline from the finite interior`() {
        val result = assertIs<InversePathPreparationResult.Ready>(
            prepareInversePathGeometryF32(
                finiteFillF64 = rectangleInputF64(0.0, 0.0, 10.0, 10.0),
                deviceStrokeOutlineF64 = rectangleInputF64(4.0, -2.0, 12.0, 12.0),
                styleF64 = finiteStyleF64(2.0),
                mode = InversePathDrawMode.StrokeAndFill,
                domainI32 = RectI32(-4, -4, 16, 16),
                policyF64 = PathStrokePolicyF64(),
            ),
        )

        val interiorF32 = assertIs<InverseInteriorCoverageF32.Geometry>(result.geometryF32.interiorCoverageF32)
            .copyGeometryF32()
        assertEquals(RectI32(0, 0, 4, 10), interiorF32.copyConservativeScissorI32())
    }

    @Test
    fun `zero width inverse stroke and fill retains exactly the finite fill`() {
        val result = assertIs<InversePathPreparationResult.Ready>(
            prepareInversePathGeometryF32(
                finiteFillF64 = triangleInputF64(FillRule.INVERSE_EVEN_ODD),
                deviceStrokeOutlineF64 = rectangleInputF64(-10.0, -10.0, 20.0, 20.0),
                styleF64 = finiteStyleF64(0.0),
                mode = InversePathDrawMode.StrokeAndFill,
                domainI32 = RectI32(-2, -2, 12, 12),
                policyF64 = PathStrokePolicyF64(),
            ),
        )

        val interiorF32 = assertIs<InverseInteriorCoverageF32.Geometry>(result.geometryF32.interiorCoverageF32)
            .copyGeometryF32()
        assertEquals(FillRule.EVEN_ODD, interiorF32.fillRule)
        assertEquals(RectI32(0, 0, 8, 8), interiorF32.copyConservativeScissorI32())
    }

    @Test
    fun `nonempty path without finite interior and an empty domain still publishes zero coverage`() {
        val domainI32 = RectI32(4, 8, 4, 12)
        val result = assertIs<InversePathPreparationResult.Ready>(
            prepareInversePathGeometryF32(
                PathFillInputF64.of(
                    FillRule.INVERSE_WINDING,
                    listOf(
                        PathFillSegmentF64.MoveTo(Point2F64(0.0, 0.0)),
                        PathFillSegmentF64.LineTo(Point2F64(3.0, 0.0)),
                    ),
                ),
                null, null, InversePathDrawMode.Fill, domainI32, PathStrokePolicyF64(),
            ),
        )
        domainI32.left = -100
        val returnedDomainI32 = result.geometryF32.copyDomainI32()
        returnedDomainI32.top = -100

        assertIs<InverseInteriorCoverageF32.Zero>(result.geometryF32.interiorCoverageF32)
        assertEquals(RectI32(4, 8, 4, 12), result.geometryF32.copyDomainI32())
    }

    @Test
    fun `inverse preparation rejects an already exhausted frame even when the path is empty`() {
        val result = prepareInversePathGeometryF32(
            PathFillInputF64.of(FillRule.INVERSE_WINDING, emptyList()),
            null, null, InversePathDrawMode.Fill, RectI32(0, 0, 4, 4),
            PathStrokePolicyF64(limitsI64 = PathStrokeLimitsI64(maxSnapshotByteCountPerFrameI64 = 8L)),
            frameWorkUsageBeforeI64 = PathStrokeWorkUsageI64(snapshotByteCountI64 = 9L),
        )

        assertEquals(
            PathStrokeResourceLimitReason.SnapshotByteLimit,
            assertIs<InversePathPreparationResult.ResourceLimitExceeded>(result).reason,
        )
    }

    @Test
    fun `inverse geometry publication debits its defensive snapshot before copying it`() {
        val result = prepareInversePathGeometryF32(
            finiteFillF64 = triangleInputF64(FillRule.INVERSE_WINDING),
            deviceStrokeOutlineF64 = null,
            styleF64 = null,
            mode = InversePathDrawMode.Fill,
            domainI32 = RectI32(0, 0, 16, 16),
            policyF64 = PathStrokePolicyF64(
                // 80 bytes for the finite-input snapshot, 52 for the emitted direct triangle,
                // and 84 for the inverse geometry snapshot plus its wrapper are required.
                limitsI64 = PathStrokeLimitsI64(maxSnapshotByteCountPerPathI64 = 215L),
            ),
        )

        assertEquals(
            PathStrokeResourceLimitReason.SnapshotByteLimit,
            assertIs<InversePathPreparationResult.ResourceLimitExceeded>(result).reason,
        )
    }

    @Test
    fun `inverse boolean conversion rejects finite F64 values outside F32 instead of throwing`() {
        val result = prepareInversePathGeometryF32(
            rectangleInputF64(0.0, 0.0, Double.MAX_VALUE, 1.0),
            rectangleInputF64(0.0, 0.0, 1.0, 1.0),
            finiteStyleF64(1.0), InversePathDrawMode.StrokeAndFill, RectI32(0, 0, 4, 4), PathStrokePolicyF64(),
        )

        assertEquals(
            PathStrokeInvalidSceneReason.NonFiniteInput,
            assertIs<InversePathPreparationResult.InvalidScene>(result).reason,
        )
    }

    @Test
    fun `hairline inverse uses the general difference for disjoint same orientation boundaries`() {
        val result = assertIs<InversePathPreparationResult.Ready>(
            prepareInversePathGeometryF32(
                finiteFillF64 = rectangleInputF64(0.0, 0.0, 20.0, 10.0),
                deviceStrokeOutlineF64 = twoRectangleOutlineF64(
                    outerLeftF64 = 4.0,
                    outerTopF64 = -2.0,
                    outerRightF64 = 8.0,
                    outerBottomF64 = 8.0,
                    innerLeftF64 = 12.0,
                    innerTopF64 = 2.0,
                    innerRightF64 = 16.0,
                    innerBottomF64 = 8.0,
                    innerReversed = false,
                ),
                styleF64 = hairlineStyleF64(),
                mode = InversePathDrawMode.StrokeAndFill,
                domainI32 = RectI32(-4, -4, 28, 16),
                policyF64 = PathStrokePolicyF64(),
            ),
        )

        val interiorF32 = assertIs<InverseInteriorCoverageF32.Geometry>(result.geometryF32.interiorCoverageF32)
            .copyGeometryF32()

        assertTrue(PathAnalysisF32.contains(pathFromFillGeometryF32(interiorF32), Point2F32(2f, 5f)))
        assertFalse(PathAnalysisF32.contains(pathFromFillGeometryF32(interiorF32), Point2F32(6f, 5f)))
        assertFalse(PathAnalysisF32.contains(pathFromFillGeometryF32(interiorF32), Point2F32(14f, 5f)))
    }

    @Test
    fun `hairline inverse returns a typed topology refusal for nested same orientation boundaries`() {
        val result = prepareInversePathGeometryF32(
            finiteFillF64 = rectangleInputF64(0.0, 0.0, 20.0, 10.0),
            deviceStrokeOutlineF64 = twoRectangleOutlineF64(
                outerLeftF64 = 4.0,
                outerTopF64 = -2.0,
                outerRightF64 = 24.0,
                outerBottomF64 = 12.0,
                innerLeftF64 = 8.0,
                innerTopF64 = 2.0,
                innerRightF64 = 16.0,
                innerBottomF64 = 8.0,
                innerReversed = false,
            ),
            styleF64 = hairlineStyleF64(),
            mode = InversePathDrawMode.StrokeAndFill,
            domainI32 = RectI32(-4, -4, 28, 16),
            policyF64 = PathStrokePolicyF64(),
        )

        assertEquals(
            PathStrokeResourceLimitReason.TopologyLimit,
            assertIs<InversePathPreparationResult.ResourceLimitExceeded>(result).reason,
        )
    }

    @Test
    fun `hairline inverse retains fill outside outer and in inner while removing the stroke band`() {
        val result = assertIs<InversePathPreparationResult.Ready>(
            prepareInversePathGeometryF32(
                finiteFillF64 = rectangleInputF64(0.0, 0.0, 10.0, 10.0),
                deviceStrokeOutlineF64 = twoRectangleOutlineF64(
                    outerLeftF64 = 4.0,
                    outerTopF64 = -2.0,
                    outerRightF64 = 12.0,
                    outerBottomF64 = 12.0,
                    innerLeftF64 = 5.0,
                    innerTopF64 = 2.0,
                    innerRightF64 = 8.0,
                    innerBottomF64 = 8.0,
                    innerReversed = true,
                ),
                styleF64 = hairlineStyleF64(),
                mode = InversePathDrawMode.StrokeAndFill,
                domainI32 = RectI32(-4, -4, 28, 16),
                policyF64 = PathStrokePolicyF64(),
            ),
        )

        val interiorPathF32 = pathFromFillGeometryF32(
            assertIs<InverseInteriorCoverageF32.Geometry>(result.geometryF32.interiorCoverageF32).copyGeometryF32(),
        )

        assertTrue(PathAnalysisF32.contains(interiorPathF32, Point2F32(2f, 5f)))
        assertTrue(PathAnalysisF32.contains(interiorPathF32, Point2F32(6f, 5f)))
        assertFalse(PathAnalysisF32.contains(interiorPathF32, Point2F32(4.5f, 5f)))
        assertFalse(PathAnalysisF32.contains(interiorPathF32, Point2F32(9f, 5f)))
    }

    @Test
    fun `hairline inverse refuses snapshot exhaustion before copying its exact outline operands`() {
        val result = prepareInversePathGeometryF32(
            finiteFillF64 = rectangleInputF64(0.0, 0.0, 10.0, 8.0),
            deviceStrokeOutlineF64 = PathFillInputF64.of(
                FillRule.WINDING,
                listOf(
                    PathFillSegmentF64.MoveTo(Point2F64(0.0, 0.5)),
                    PathFillSegmentF64.LineTo(Point2F64(10.0, 0.5)),
                    PathFillSegmentF64.LineTo(Point2F64(9.5, 0.5)),
                    PathFillSegmentF64.LineTo(Point2F64(9.5, 7.5)),
                    PathFillSegmentF64.Close,
                    PathFillSegmentF64.MoveTo(Point2F64(-0.5, -0.5)),
                    PathFillSegmentF64.LineTo(Point2F64(10.5, -0.5)),
                    PathFillSegmentF64.LineTo(Point2F64(10.5, 8.5)),
                    PathFillSegmentF64.LineTo(Point2F64(-0.5, 8.5)),
                    PathFillSegmentF64.Close,
                ),
            ),
            styleF64 = hairlineStyleF64(),
            mode = InversePathDrawMode.StrokeAndFill,
            domainI32 = RectI32(-4, -4, 16, 12),
            policyF64 = PathStrokePolicyF64(
                limitsI64 = PathStrokeLimitsI64(maxSnapshotByteCountPerPathI64 = 300L),
            ),
        )

        assertEquals(
            PathStrokeResourceLimitReason.SnapshotByteLimit,
            assertIs<InversePathPreparationResult.ResourceLimitExceeded>(result).reason,
        )
    }

    private fun triangleInputF64(fillRule: FillRule): PathFillInputF64 = PathFillInputF64.of(
        fillRule,
        listOf(
            PathFillSegmentF64.MoveTo(Point2F64(0.0, 0.0)),
            PathFillSegmentF64.LineTo(Point2F64(8.0, 0.0)),
            PathFillSegmentF64.LineTo(Point2F64(0.0, 8.0)),
            PathFillSegmentF64.Close,
        ),
    )

    private fun rectangleInputF64(leftF64: Double, topF64: Double, rightF64: Double, bottomF64: Double): PathFillInputF64 =
        PathFillInputF64.of(
            FillRule.WINDING,
            listOf(
                PathFillSegmentF64.MoveTo(Point2F64(leftF64, topF64)),
                PathFillSegmentF64.LineTo(Point2F64(rightF64, topF64)),
                PathFillSegmentF64.LineTo(Point2F64(rightF64, bottomF64)),
                PathFillSegmentF64.LineTo(Point2F64(leftF64, bottomF64)),
                PathFillSegmentF64.Close,
            ),
        )

    private fun twoRectangleOutlineF64(
        outerLeftF64: Double,
        outerTopF64: Double,
        outerRightF64: Double,
        outerBottomF64: Double,
        innerLeftF64: Double,
        innerTopF64: Double,
        innerRightF64: Double,
        innerBottomF64: Double,
        innerReversed: Boolean,
    ): PathFillInputF64 = PathFillInputF64.of(
        FillRule.WINDING,
        rectangleSegmentsF64(outerLeftF64, outerTopF64, outerRightF64, outerBottomF64) +
            rectangleSegmentsF64(innerLeftF64, innerTopF64, innerRightF64, innerBottomF64, reversed = innerReversed),
    )

    private fun rectangleSegmentsF64(
        leftF64: Double,
        topF64: Double,
        rightF64: Double,
        bottomF64: Double,
        reversed: Boolean = false,
    ): List<PathFillSegmentF64> {
        val cornersF64 = if (reversed) {
            listOf(
                Point2F64(leftF64, topF64),
                Point2F64(leftF64, bottomF64),
                Point2F64(rightF64, bottomF64),
                Point2F64(rightF64, topF64),
            )
        } else {
            listOf(
                Point2F64(leftF64, topF64),
                Point2F64(rightF64, topF64),
                Point2F64(rightF64, bottomF64),
                Point2F64(leftF64, bottomF64),
            )
        }
        return listOf(
            PathFillSegmentF64.MoveTo(cornersF64[0]),
            PathFillSegmentF64.LineTo(cornersF64[1]),
            PathFillSegmentF64.LineTo(cornersF64[2]),
            PathFillSegmentF64.LineTo(cornersF64[3]),
            PathFillSegmentF64.Close,
        )
    }

    private fun finiteStyleF64(widthF64: Double): PathStrokeStyleF64 = PathStrokeStyleF64(
        widthF64 = PathStrokeWidthF64.Finite(widthF64),
        cap = PathStrokeCap.Butt,
        join = PathStrokeJoin.Miter,
        miterLimitF64 = 4.0,
    )

    private fun hairlineStyleF64(): PathStrokeStyleF64 = PathStrokeStyleF64(
        widthF64 = PathStrokeWidthF64.Hairline,
        cap = PathStrokeCap.Butt,
        join = PathStrokeJoin.Bevel,
        miterLimitF64 = 4.0,
    )
}

private fun pathFromFillGeometryF32(geometryF32: PathFillGeometryF32): PathF32 {
    geometryF32.copyDirectTriangleF32OrNull()?.let { triangleF32 ->
        val verticesF32 = triangleF32.copyVerticesF32()
        return PathBuilder(geometryF32.fillRule)
            .moveTo(verticesF32[0], verticesF32[1])
            .lineTo(verticesF32[2], verticesF32[3])
            .lineTo(verticesF32[4], verticesF32[5])
            .close()
            .build()
    }
    val fanF32 = checkNotNull(geometryF32.copyStencilEdgeFanF32OrNull())
    val verticesF32 = fanF32.copyVerticesF32()
    val contourStartsI32 = fanF32.copyContourStartsI32()
    val builderF32 = PathBuilder(geometryF32.fillRule)
    contourStartsI32.forEachIndexed { contourIndexI32, startI32 ->
        val endI32 = contourStartsI32.getOrElse(contourIndexI32 + 1) { fanF32.edgeCountI32 }
        for (edgeIndexI32 in startI32 until endI32) {
            val offsetI32 = edgeIndexI32 * 6 + 2
            if (edgeIndexI32 == startI32) {
                builderF32.moveTo(verticesF32[offsetI32], verticesF32[offsetI32 + 1])
            } else {
                builderF32.lineTo(verticesF32[offsetI32], verticesF32[offsetI32 + 1])
            }
        }
        builderF32.close()
    }
    return builderF32.build()
}

private fun FillRule.toFiniteFillRule(): FillRule = when (this) {
    FillRule.WINDING, FillRule.INVERSE_WINDING -> FillRule.WINDING
    FillRule.EVEN_ODD, FillRule.INVERSE_EVEN_ODD -> FillRule.EVEN_ODD
}
