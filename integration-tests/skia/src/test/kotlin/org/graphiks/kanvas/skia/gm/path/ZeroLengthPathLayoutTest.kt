package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.geometry.PathVerb
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.types.Point
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
    fun `zero length forms construct Skia-compatible verbs and points`() {
        val anchor = Point(24.5f, 9.5f)
        val expectedPaths = mapOf(
            ZeroLengthPathVerb.MOVE to ExpectedPath(listOf(PathVerb.MOVE), listOf(anchor)),
            ZeroLengthPathVerb.CLOSE to ExpectedPath(listOf(PathVerb.MOVE, PathVerb.CLOSE), listOf(anchor)),
            ZeroLengthPathVerb.LINE to ExpectedPath(listOf(PathVerb.MOVE, PathVerb.LINE), listOf(anchor, anchor)),
            ZeroLengthPathVerb.LINE_CLOSE to ExpectedPath(listOf(PathVerb.MOVE, PathVerb.LINE, PathVerb.CLOSE), listOf(anchor, anchor)),
            ZeroLengthPathVerb.QUAD to ExpectedPath(listOf(PathVerb.MOVE, PathVerb.QUAD), List(3) { anchor }),
            ZeroLengthPathVerb.QUAD_CLOSE to ExpectedPath(listOf(PathVerb.MOVE, PathVerb.QUAD, PathVerb.CLOSE), List(3) { anchor }),
            ZeroLengthPathVerb.CUBIC to ExpectedPath(listOf(PathVerb.MOVE, PathVerb.CUBIC), List(4) { anchor }),
            ZeroLengthPathVerb.CUBIC_CLOSE to ExpectedPath(listOf(PathVerb.MOVE, PathVerb.CUBIC, PathVerb.CLOSE), List(4) { anchor }),
            ZeroLengthPathVerb.ARC to ExpectedPath(listOf(PathVerb.MOVE, PathVerb.LINE), listOf(anchor, anchor)),
            ZeroLengthPathVerb.ARC_CLOSE to ExpectedPath(listOf(PathVerb.MOVE, PathVerb.LINE, PathVerb.CLOSE), listOf(anchor, anchor)),
        )

        expectedPaths.forEach { (verb, expected) ->
            val path = buildZeroLengthPath(verb, anchor)
            assertEquals(expected.verbs, pathVerbs(path), verb.name)
            assertEquals(expected.points, pathPoints(path), verb.name)
        }
    }

    @Test
    fun `all four diagnostic GMs are blocking`() {
        assertEquals(RenderCost.BLOCKING, ZeroLengthPathsAaGm().renderCost)
        assertEquals(RenderCost.BLOCKING, ZeroLengthPathsBwGm().renderCost)
        assertEquals(RenderCost.BLOCKING, ZeroLengthPathsDblAaGm().renderCost)
        assertEquals(RenderCost.BLOCKING, ZeroLengthPathsDblBwGm().renderCost)
    }

    @Test
    fun `validation cells have unique deterministic source ids across both matrices`() {
        val simpleSourceIds = validationCellSourceIds(
            firstVerbs = ZeroLengthPathVerb.entries,
            secondVerbs = listOf(null),
        )
        assertEquals(180, simpleSourceIds.size)
        assertEquals(180, simpleSourceIds.distinct().size)

        val doubleSourceIds = validationCellSourceIds(
            firstVerbs = ZeroLengthPathVerb.entries.take(6),
            secondVerbs = ZeroLengthPathVerb.entries.take(6),
        )
        assertEquals(648, doubleSourceIds.size)
        assertEquals(648, doubleSourceIds.distinct().size)

        val tuple = ZeroLengthPathLayout.validationCellSourceId(
            StrokeCap.ROUND,
            strokeWidthIndex = 3,
            first = ZeroLengthPathVerb.LINE,
            second = ZeroLengthPathVerb.QUAD_CLOSE,
        )
        assertEquals(
            tuple,
            ZeroLengthPathLayout.validationCellSourceId(
                StrokeCap.ROUND,
                strokeWidthIndex = 3,
                first = ZeroLengthPathVerb.LINE,
                second = ZeroLengthPathVerb.QUAD_CLOSE,
            ),
        )
    }

    private fun validationCellSourceIds(
        firstVerbs: List<ZeroLengthPathVerb>,
        secondVerbs: List<ZeroLengthPathVerb?>,
    ): List<String> = buildList {
        for (cap in listOf(StrokeCap.BUTT, StrokeCap.ROUND, StrokeCap.SQUARE)) {
            for (strokeWidthIndex in 0 until 6) {
                for (first in firstVerbs) {
                    for (second in secondVerbs) {
                        add(ZeroLengthPathLayout.validationCellSourceId(cap, strokeWidthIndex, first, second))
                    }
                }
            }
        }
    }

    private fun buildZeroLengthPath(verb: ZeroLengthPathVerb, anchor: Point): Path {
        val method = Class.forName("org.graphiks.kanvas.skia.gm.path.ZeroLengthPathsGmKt")
            .getDeclaredMethod("zeroLengthPath", ZeroLengthPathVerb::class.java, Point::class.java)
        method.isAccessible = true
        return method.invoke(null, verb, anchor) as Path
    }

    @Suppress("UNCHECKED_CAST")
    private fun pathVerbs(path: Path): List<PathVerb> =
        Path::class.java.getDeclaredField("verbs").run {
            isAccessible = true
            (get(path) as List<PathVerb>).toList()
        }

    @Suppress("UNCHECKED_CAST")
    private fun pathPoints(path: Path): List<Point> =
        Path::class.java.getDeclaredField("points").run {
            isAccessible = true
            (get(path) as List<Point>).toList()
        }

    private data class ExpectedPath(
        val verbs: List<PathVerb>,
        val points: List<Point>,
    )
}
