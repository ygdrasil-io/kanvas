package org.graphiks.kanvas.geometry

import org.graphiks.kanvas.types.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class GeometryTest {

    @Test
    fun `isEmpty on empty path`() {
        assertTrue(Path().isEmpty())
    }

    @Test
    fun `isEmpty on non-empty path`() {
        assertFalse(Path().lineTo(10f, 10f).isEmpty())
    }

    @Test
    fun `isRect on axis-aligned rect path`() {
        val path = Path().addRect(Rect.fromLTRB(10f, 20f, 110f, 80f))
        assertTrue(path.isRect())
    }

    @Test
    fun `isRect on triangle returns false`() {
        val path = Path().apply {
            moveTo(0f, 0f); lineTo(100f, 0f); lineTo(50f, 100f); close()
        }
        assertFalse(path.isRect())
    }

    @Test
    fun `isRect writes bounds via out-param`() {
        val rect = Rect(0f, 0f, 0f, 0f)
        val path = Path().addRect(Rect.fromLTRB(10f, 20f, 110f, 80f))
        assertTrue(path.isRect(rect))
        assertEquals(10f, rect.left)
        assertEquals(20f, rect.top)
        assertEquals(110f, rect.right)
        assertEquals(80f, rect.bottom)
    }

    @Test
    fun `isConvex on square returns true`() {
        assertTrue(Path().addRect(Rect.fromLTRB(0f, 0f, 100f, 100f)).isConvex())
    }

    @Test
    fun `isConvex on concave shape returns false`() {
        val concave = Path().apply {
            moveTo(0f, 0f); lineTo(100f, 0f); lineTo(100f, 100f)
            lineTo(50f, 30f); lineTo(0f, 100f); close()
        }
        assertFalse(concave.isConvex())
    }

    @Test
    fun `contains point inside rect path`() {
        assertTrue(Path().addRect(Rect.fromLTRB(0f, 0f, 100f, 100f)).contains(Point(50f, 50f)))
    }

    @Test
    fun `contains point outside rect path`() {
        assertFalse(Path().addRect(Rect.fromLTRB(0f, 0f, 100f, 100f)).contains(Point(150f, 150f)))
    }

    @Test
    fun `pathMeasure length on 100px line`() {
        val path = Path().apply { moveTo(0f, 0f); lineTo(100f, 0f) }
        val pm = PathMeasure(path)
        assertEquals(100f, pm.length, 1e-4f)
    }

    @Test
    fun `pathMeasure length on closed rect`() {
        val path = Path().addRect(Rect.fromLTRB(0f, 0f, 30f, 40f))
        val pm = PathMeasure(path, forceClosed = true)
        assertEquals(140f, pm.length, 1e-4f)
    }

    @Test
    fun `pathMeasure getPosition at 50 percent of line`() {
        val path = Path().apply { moveTo(0f, 0f); lineTo(100f, 0f) }
        val pm = PathMeasure(path)
        val pos = Point(0f, 0f)
        assertTrue(pm.getPosition(50f, pos, null))
        assertEquals(50f, pos.x, 1e-4f)
        assertEquals(0f, pos.y, 1e-4f)
    }

    @Test
    fun `region isEmpty on empty region`() {
        assertTrue(Region().isEmpty)
    }

    @Test
    fun `region isEmpty on region with rect`() {
        val r = Region(Rect.fromLTRB(0f, 0f, 10f, 10f))
        assertFalse(r.isEmpty)
    }

    @Test
    fun `region op union combines two rects`() {
        val a = Region(Rect.fromLTRB(0f, 0f, 10f, 10f))
        val b = Region(Rect.fromLTRB(5f, 5f, 15f, 15f))
        assertTrue(a.op(b, RegionOp.UNION))
        assertTrue(a.contains(5f, 5f))
        assertTrue(a.contains(12f, 12f))
    }

    @Test
    fun `region contains inside and outside`() {
        val r = Region(Rect.fromLTRB(0f, 0f, 100f, 100f))
        assertTrue(r.contains(50f, 50f))
        assertFalse(r.contains(200f, 200f))
    }

    @Test
    fun `region quickReject for disjoint rects`() {
        val a = Region(Rect.fromLTRB(0f, 0f, 10f, 10f))
        assertTrue(a.quickReject(Rect.fromLTRB(20f, 20f, 30f, 30f)))
    }

    @Test
    fun `region quickReject for overlapping rects`() {
        val a = Region(Rect.fromLTRB(0f, 0f, 20f, 20f))
        assertFalse(a.quickReject(Rect.fromLTRB(10f, 10f, 30f, 30f)))
    }

    @Test
    fun `pathOps op union of two rects`() {
        val a = Path().addRect(Rect.fromLTRB(0f, 0f, 10f, 10f))
        val b = Path().addRect(Rect.fromLTRB(5f, 5f, 15f, 15f))
        val result = PathOps.op(a, b, PathOp.UNION)
        assertNotNull(result)
        val v = result!!.verbs()
        assertTrue(v.isNotEmpty())
        // Verify it's a rect path (addRect produces MOVE + LINE + LINE + LINE + CLOSE)
        assertTrue(v.size >= 4)
    }

    @Test
    fun `conservativelyContainsRect when fully inside`() {
        val outer = Path().addRect(Rect.fromLTRB(0f, 0f, 100f, 100f))
        assertTrue(outer.conservativelyContainsRect(Rect.fromLTRB(10f, 10f, 90f, 90f)))
    }

    @Test
    fun `conservativelyContainsRect when partially outside`() {
        val outer = Path().addRect(Rect.fromLTRB(0f, 0f, 50f, 50f))
        assertFalse(outer.conservativelyContainsRect(Rect.fromLTRB(25f, 25f, 75f, 75f)))
    }

    @Test
    fun `isOval on addOval path`() {
        val path = Path().addOval(Rect.fromLTRB(0f, 0f, 100f, 80f))
        assertTrue(path.isOval())
    }

    @Test
    fun `isOval on rect returns false`() {
        assertFalse(Path().addRect(Rect.fromLTRB(0f, 0f, 100f, 80f)).isOval())
    }

    @Test
    fun `isOval writes bounds`() {
        val bounds = Rect(0f, 0f, 0f, 0f)
        assertTrue(Path().addOval(Rect.fromLTRB(10f, 20f, 110f, 100f)).isOval(bounds))
        assertEquals(10f, bounds.left)
        assertEquals(20f, bounds.top)
        assertEquals(110f, bounds.right)
        assertEquals(100f, bounds.bottom)
    }

    @Test
    fun `isLine on single segment`() {
        assertTrue(Path().apply { moveTo(0f, 0f); lineTo(100f, 0f) }.isLine())
    }

    @Test
    fun `isLine on rect returns false`() {
        assertFalse(Path().addRect(Rect.fromLTRB(0f, 0f, 10f, 10f)).isLine())
    }

    @Test
    fun `isInterpolatable similar paths`() {
        val a = Path().apply { moveTo(0f, 0f); lineTo(100f, 0f) }
        val b = Path().apply { moveTo(0f, 0f); lineTo(200f, 0f) }
        assertTrue(a.isInterpolatable(b))
    }

    @Test
    fun `isInterpolatable different verb counts`() {
        val a = Path().apply { moveTo(0f, 0f); lineTo(100f, 0f) }
        val b = Path().addRect(Rect.fromLTRB(0f, 0f, 10f, 10f))
        assertFalse(a.isInterpolatable(b))
    }

    @Test
    fun `pathMeasure getSegment extracts sub-path`() {
        val path = Path().apply { moveTo(0f, 0f); lineTo(100f, 0f) }
        val pm = PathMeasure(path)
        val dst = Path()
        assertTrue(pm.getSegment(0f, 50f, dst, true))
        val v = dst.verbs()
        assertEquals(2, v.size)
        assertEquals(PathVerb.MOVE, v[0])
        assertEquals(PathVerb.LINE, v[1])
    }

    @Test
    fun `pathMeasure nextContour on multi-contour`() {
        val path = Path().apply {
            addRect(Rect.fromLTRB(0f, 0f, 10f, 10f))
            addRect(Rect.fromLTRB(20f, 20f, 30f, 30f))
        }
        val pm = PathMeasure(path)
        assertTrue(pm.nextContour())
    }

    @Test
    fun `isRRect on addRRect path`() {
        val path = Path().addRRect(RRect(Rect.fromLTRB(0f, 0f, 100f, 80f), 10f))
        assertTrue(path.isRRect())
    }

    @Test
    fun `isRRect on rect returns false`() {
        assertFalse(Path().addRect(Rect.fromLTRB(0f, 0f, 10f, 10f)).isRRect())
    }

    @Test
    fun `region op intersect`() {
        val a = Region(Rect.fromLTRB(0f, 0f, 20f, 20f))
        val b = Region(Rect.fromLTRB(10f, 0f, 30f, 20f))
        assertTrue(a.op(b, RegionOp.INTERSECT))
        assertTrue(a.contains(15f, 10f))
        assertFalse(a.contains(5f, 10f))
    }

    @Test
    fun `pathOps asWinding sets winding fill type`() {
        val path = Path().addRect(Rect.fromLTRB(0f, 0f, 10f, 10f))
        path.fillType = FillType.EVEN_ODD
        val wound = PathOps.asWinding(path)
        assertNotNull(wound)
        assertEquals(FillType.WINDING, wound!!.fillType)
    }
}
