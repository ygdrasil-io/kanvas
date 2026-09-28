package org.graphiks.math.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PathFillScanSpansI32Test {
    @Test
    fun `right half integer rounds into span`() {
        val ready = assertIs<PathFillScanPreparationI32.Ready>(prepare(
            triangle(2f, 1f, 5f, 1f, 2f, 4f), RectI32(0, 0, 6, 6), 3,
        ))

        assertEquals(
            listOf(
                PathFillScanSpanI32(2, 5, 1),
                PathFillScanSpanI32(2, 4, 2),
                PathFillScanSpanI32(2, 3, 3),
            ),
            ready.spansI32.copySpansI32(),
        )
    }

    @Test
    fun `left mirror and reversed winding share the y one span`() {
        val clockwise = assertIs<PathFillScanPreparationI32.Ready>(prepare(
            triangle(1f, 1f, 4f, 1f, 4f, 4f), RectI32(0, 0, 6, 6), 3,
        ))
        val counterClockwise = assertIs<PathFillScanPreparationI32.Ready>(prepare(
            triangle(4f, 4f, 4f, 1f, 1f, 1f), RectI32(0, 0, 6, 6), 3,
        ))

        val expected = listOf(PathFillScanSpanI32(2, 4, 1))
        assertEquals(expected, clockwise.spansI32.copySpansI32().filter { it.yI32 == 1 })
        assertEquals(expected, counterClockwise.spansI32.copySpansI32().filter { it.yI32 == 1 })
    }

    @Test
    fun `negative clip advances edge before rounding`() {
        val ready = assertIs<PathFillScanPreparationI32.Ready>(prepare(
            triangle(0f, -1f, 3f, -1f, 0f, 2f), RectI32(0, 0, 4, 4), 2,
        ))

        assertEquals(
            listOf(PathFillScanSpanI32(0, 2, 0), PathFillScanSpanI32(0, 1, 1)),
            ready.spansI32.copySpansI32(),
        )
    }

    @Test
    fun `subpixel nonempty triangle produces Empty scan authority`() {
        val ready = assertIs<PathFillScanPreparationI32.Ready>(prepare(
            triangle(2.1f, 1.1f, 2.2f, 1.1f, 2.1f, 1.2f), RectI32(0, 0, 6, 6), 1,
        ))

        assertIs<PathFillScanSpansI32.Empty>(ready.spansI32)
        assertEquals(0, ready.spansI32.spanCountI32)
    }

    @Test
    fun `bounded span count and numeric range refuse`() {
        val overLimit = prepare(triangle(2f, 1f, 5f, 1f, 2f, 4f), RectI32(0, 0, 6, 6), 2)
        val outOfRange = prepare(
            triangle(33_554_432f, 1f, 5f, 1f, 2f, 4f), RectI32(0, 0, 6, 6), 3,
        )

        assertEquals(
            PathFillScanPreparationI32.Refused(PathFillScanRefusalReasonI32.SpanLimit),
            overLimit,
        )
        assertEquals(
            PathFillScanPreparationI32.Refused(PathFillScanRefusalReasonI32.NumericRange),
            outOfRange,
        )
    }

    @Test
    fun `scan span limit admits 4096 and refuses the 4097th source span`() {
        val admitted = assertIs<PathFillScanPreparationI32.Ready>(prepare(
            triangle(0f, 0f, 10_000f, 0f, 0f, 4_096f), RectI32(0, 0, 1, 4_096), 4_096,
        ))
        assertEquals(4_096, admitted.spansI32.spanCountI32)

        assertEquals(
            PathFillScanPreparationI32.Refused(PathFillScanRefusalReasonI32.SpanLimit),
            prepare(
                triangle(0f, 0f, 10_000f, 0f, 0f, 4_097f), RectI32(0, 0, 1, 4_097), 4_096,
            ),
        )
    }

    @Test
    fun `local scissors subtract origin once and reject overflow or target escape`() {
        val spans = PathFillScanSpansI32.NonEmpty(
            domainI32 = RectI32(2, 1, 5, 4),
            spansI32 = listOf(
                PathFillScanSpanI32(2, 5, 1),
                PathFillScanSpanI32(2, 4, 2),
                PathFillScanSpanI32(2, 3, 3),
            ),
        )

        val scissors = assertNotNull(spans.localScissorsI32OrNull(Point2I32(1, 1), SizeI32(4, 4)))
        assertEquals(
            listOf(RectI32(1, 0, 4, 1), RectI32(1, 1, 3, 2), RectI32(1, 2, 2, 3)),
            scissors.copyScissorsI32(),
        )
        val copiedScissors = scissors.copyScissorsI32()
        copiedScissors[0].left = 99
        assertEquals(RectI32(1, 0, 4, 1), scissors.copyScissorsI32()[0])
        val copiedDomain = scissors.copyDomainI32()
        copiedDomain.left = 99
        assertEquals(RectI32(1, 0, 4, 3), scissors.copyDomainI32())
        assertEquals(Point2I32(1, 1), scissors.copyOriginDeviceI32())

        assertNull(spans.localScissorsI32OrNull(Point2I32(Int.MIN_VALUE, 1), SizeI32(4, 4)))
        assertNull(spans.localScissorsI32OrNull(Point2I32(1, 1), SizeI32(3, 4)))
    }

    private fun prepare(
        triangleF32: PathFillDirectTriangleF32,
        domainI32: RectI32,
        maxSpanCountI32: Int,
    ): PathFillScanPreparationI32 = preparePathFillScanSpansI32(triangleF32, domainI32, maxSpanCountI32)

    private fun triangle(
        x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float,
    ): PathFillDirectTriangleF32 = PathFillDirectTriangleF32(
        floatArrayOf(x0, y0, x1, y1, x2, y2),
        intArrayOf(0, 1, 2),
    )
}
