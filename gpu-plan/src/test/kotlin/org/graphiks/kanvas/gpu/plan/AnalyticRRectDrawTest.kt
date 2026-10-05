package org.graphiks.kanvas.gpu.plan

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.math.color.ColorF32
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RectI32
import org.junit.jupiter.api.Test

class AnalyticRRectDrawTest {
    @Test
    fun `analytic rrect draw preserves each primitive shape and origin`() {
        val rectShape = RRectF32.of(RectF32(0f, 0f, 8f, 4f))
        val rrectShape = RRectF32.of(
            RectF32(1f, 2f, 9f, 6f),
            CornerRadiiF32.of(1f, 2f),
            CornerRadiiF32.of(2f, 1f),
            CornerRadiiF32.of(1f, 3f),
            CornerRadiiF32.of(3f, 1f),
        )
        val first = AnalyticRRectDraw.of(
            0, ColorF32.of(1f, 0f, 0f, 1f), DrawOrigin.RECT, rectShape,
            RectI32(0, 0, 8, 4), RectI32(0, 0, 8, 4),
        )
        val second = AnalyticRRectDraw.of(
            1, ColorF32.of(0f, 1f, 0f, 1f), DrawOrigin.RRECT, rrectShape,
            RectI32(1, 2, 9, 6), RectI32(1, 2, 9, 6),
        )
        rrectShape.rect.left = 99f
        val leakedShape = second.copyDeviceShape()
        leakedShape.rect.top = 99f

        assertNotSame(first, second)
        assertEquals(0, first.commandIndex)
        assertEquals(rectShape, first.copyDeviceShape())
        assertEquals(DrawOrigin.RECT, first.origin)
        assertEquals(1, second.commandIndex)
        assertEquals(RRectF32.of(
            RectF32(1f, 2f, 9f, 6f),
            CornerRadiiF32.of(1f, 2f), CornerRadiiF32.of(2f, 1f),
            CornerRadiiF32.of(1f, 3f), CornerRadiiF32.of(3f, 1f),
        ), second.copyDeviceShape())
        assertEquals(DrawOrigin.RRECT, second.origin)
        assertEquals(CoveragePlan.AnalyticScalarAA, second.coverage)
        assertEquals(SamplePlan.SingleSample, second.sample)
        assertEquals(BlendPlan.SrcOver, second.blend)
    }

    @Test
    fun `analytic rrect draw rejects unsupported primitive origins`() {
        assertFailsWith<IllegalArgumentException> {
            AnalyticRRectDraw.of(
                0, ColorF32.of(1f, 1f, 1f, 1f), DrawOrigin.PATH, RRectF32.of(RectF32(0f, 0f, 8f, 4f)),
                RectI32(0, 0, 8, 4), RectI32(0, 0, 8, 4),
            )
        }
    }
}
