package org.graphiks.math.geometry

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PathStrokeStyleF64Test {
    @Test
    fun `stroke policy exposes documented defaults and accepts finite positive tolerances`() {
        val defaults = PathStrokePolicyF64()
        assertEquals(0.25, defaults.maximumSagittaErrorF64)
        assertEquals(0.0625, defaults.maximumDashArcLengthErrorF64)
        assertEquals(PathStrokeLimitsI32(), defaults.limitsI32)
        assertEquals(PathStrokeLimitsI64(), defaults.limitsI64)

        val configured = PathStrokePolicyF64(
            maximumSagittaErrorF64 = 0.5,
            maximumDashArcLengthErrorF64 = 0.125,
        )
        assertEquals(0.5, configured.maximumSagittaErrorF64)
        assertEquals(0.125, configured.maximumDashArcLengthErrorF64)
    }

    @Test
    fun `stroke policy rejects non-finite zero and negative tolerances`() {
        assertFailsWith<IllegalArgumentException> { PathStrokePolicyF64(maximumSagittaErrorF64 = Double.NaN) }
        assertFailsWith<IllegalArgumentException> {
            PathStrokePolicyF64(maximumSagittaErrorF64 = Double.POSITIVE_INFINITY)
        }
        assertFailsWith<IllegalArgumentException> { PathStrokePolicyF64(maximumDashArcLengthErrorF64 = Double.NaN) }
        assertFailsWith<IllegalArgumentException> {
            PathStrokePolicyF64(maximumDashArcLengthErrorF64 = Double.POSITIVE_INFINITY)
        }
        assertFailsWith<IllegalArgumentException> { PathStrokePolicyF64(maximumSagittaErrorF64 = 0.0) }
        assertFailsWith<IllegalArgumentException> { PathStrokePolicyF64(maximumDashArcLengthErrorF64 = 0.0) }
        assertFailsWith<IllegalArgumentException> { PathStrokePolicyF64(maximumSagittaErrorF64 = -0.25) }
        assertFailsWith<IllegalArgumentException> { PathStrokePolicyF64(maximumDashArcLengthErrorF64 = -0.0625) }
    }

    @Test
    fun `dash rejects malformed intervals and phase while preserving valid zero intervals`() {
        assertFailsWith<IllegalArgumentException> { PathStrokeDashF64.of(doubleArrayOf(1.0), 0.0) }
        assertFailsWith<IllegalArgumentException> { PathStrokeDashF64.of(doubleArrayOf(1.0, -1.0), 0.0) }
        assertFailsWith<IllegalArgumentException> { PathStrokeDashF64.of(doubleArrayOf(0.0, 0.0), 0.0) }
        assertFailsWith<IllegalArgumentException> { PathStrokeDashF64.of(doubleArrayOf(Double.NaN, 1.0), 0.0) }
        assertFailsWith<IllegalArgumentException> { PathStrokeDashF64.of(doubleArrayOf(1.0, 1.0), Double.POSITIVE_INFINITY) }

        assertEquals(2, PathStrokeDashF64.of(doubleArrayOf(1.0, 0.0), 0.0).intervalCountI32)
        assertEquals(2, PathStrokeDashF64.of(doubleArrayOf(2.0, 3.0), -1.0).intervalCountI32)
    }

    @Test
    fun `dash snapshots both its construction array and returned arrays`() {
        val source = doubleArrayOf(2.0, 0.0)
        val dash = PathStrokeDashF64.of(source, -1.0)
        source[0] = 99.0

        val returned = dash.copyIntervalsF64()
        returned[1] = 99.0

        assertContentEquals(doubleArrayOf(2.0, 0.0), dash.copyIntervalsF64())
    }

    @Test
    fun `style rejects invalid finite widths and miter limits while allowing finite bevel selection`() {
        assertFailsWith<IllegalArgumentException> {
            PathStrokeStyleF64(PathStrokeWidthF64.Finite(-1.0), PathStrokeCap.Butt, PathStrokeJoin.Miter, 4.0)
        }
        assertFailsWith<IllegalArgumentException> {
            PathStrokeStyleF64(PathStrokeWidthF64.Finite(Double.NaN), PathStrokeCap.Butt, PathStrokeJoin.Miter, 4.0)
        }
        assertFailsWith<IllegalArgumentException> {
            PathStrokeStyleF64(PathStrokeWidthF64.Finite(1.0), PathStrokeCap.Butt, PathStrokeJoin.Miter, Double.NaN)
        }
        assertFailsWith<IllegalArgumentException> {
            PathStrokeStyleF64(
                PathStrokeWidthF64.Hairline,
                PathStrokeCap.Butt,
                PathStrokeJoin.Miter,
                Double.NEGATIVE_INFINITY,
            )
        }

        val bevelStyle = PathStrokeStyleF64(
            PathStrokeWidthF64.Finite(1.0),
            PathStrokeCap.Butt,
            PathStrokeJoin.Miter,
            0.5,
        )
        assertEquals(0.5, bevelStyle.miterLimitF64)
    }
}
