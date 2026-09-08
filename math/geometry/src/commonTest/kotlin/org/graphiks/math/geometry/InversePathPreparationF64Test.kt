package org.graphiks.math.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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

    private fun finiteStyleF64(widthF64: Double): PathStrokeStyleF64 = PathStrokeStyleF64(
        widthF64 = PathStrokeWidthF64.Finite(widthF64),
        cap = PathStrokeCap.Butt,
        join = PathStrokeJoin.Miter,
        miterLimitF64 = 4.0,
    )
}

private fun FillRule.toFiniteFillRule(): FillRule = when (this) {
    FillRule.WINDING, FillRule.INVERSE_WINDING -> FillRule.WINDING
    FillRule.EVEN_ODD, FillRule.INVERSE_EVEN_ODD -> FillRule.EVEN_ODD
}
