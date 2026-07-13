package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.skia.RenderCost
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ZeroLengthPathLayoutTest {
    @Test
    fun `single contour cap expectations match Skia`() {
        assertEquals(0, ZeroLengthPathLayout.expectedCaps(StrokeCap.BUTT, ZeroLengthPathVerb.LINE))
        assertEquals(0, ZeroLengthPathLayout.expectedCaps(StrokeCap.ROUND, ZeroLengthPathVerb.MOVE))
        assertEquals(1, ZeroLengthPathLayout.expectedCaps(StrokeCap.ROUND, ZeroLengthPathVerb.CLOSE))
        assertEquals(1, ZeroLengthPathLayout.expectedCaps(StrokeCap.SQUARE, ZeroLengthPathVerb.ARC_CLOSE))
    }

    @Test
    fun `double contour cap expectations match Skia`() {
        assertEquals(0, ZeroLengthPathLayout.expectedCaps(StrokeCap.BUTT, ZeroLengthPathVerb.LINE, ZeroLengthPathVerb.QUAD))
        assertEquals(0, ZeroLengthPathLayout.expectedCaps(StrokeCap.ROUND, ZeroLengthPathVerb.MOVE, ZeroLengthPathVerb.MOVE))
        assertEquals(1, ZeroLengthPathLayout.expectedCaps(StrokeCap.SQUARE, ZeroLengthPathVerb.MOVE, ZeroLengthPathVerb.LINE_CLOSE))
        assertEquals(2, ZeroLengthPathLayout.expectedCaps(StrokeCap.ROUND, ZeroLengthPathVerb.LINE, ZeroLengthPathVerb.QUAD_CLOSE))
    }

    @Test
    fun `double contours use Skia's separated anchors`() {
        assertEquals(9.5f, ZeroLengthPathLayout.firstContourAnchor.x)
        assertEquals(9.5f, ZeroLengthPathLayout.firstContourAnchor.y)
        assertEquals(40.5f, ZeroLengthPathLayout.secondContourAnchor.x)
        assertEquals(9.5f, ZeroLengthPathLayout.secondContourAnchor.y)
    }

    @Test
    fun `all four diagnostic GMs are blocking`() {
        assertEquals(RenderCost.BLOCKING, ZeroLengthPathsAaGm().renderCost)
        assertEquals(RenderCost.BLOCKING, ZeroLengthPathsBwGm().renderCost)
        assertEquals(RenderCost.BLOCKING, ZeroLengthPathsDblAaGm().renderCost)
        assertEquals(RenderCost.BLOCKING, ZeroLengthPathsDblBwGm().renderCost)
    }
}
