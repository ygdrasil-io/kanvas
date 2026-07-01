package org.graphiks.kanvas.types

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FoundationTypesTest {
    @Test
    fun `PointMode has three values`() {
        assertEquals(3, PointMode.entries.size)
        assertTrue(PointMode.entries.containsAll(listOf(PointMode.POINTS, PointMode.LINES, PointMode.POLYGON)))
    }

    @Test
    fun `Vertices minimal construction`() {
        val v = Vertices(VertexMode.TRIANGLES, listOf(Point(0f, 0f), Point(1f, 0f), Point(0f, 1f)))
        assertEquals(VertexMode.TRIANGLES, v.mode)
        assertEquals(3, v.positions.size)
        assertNull(v.texCoords)
        assertNull(v.colors)
        assertNull(v.indices)
    }

    @Test
    fun `Vertices with all fields`() {
        val v = Vertices(
            VertexMode.TRIANGLE_STRIP,
            listOf(Point(0f, 0f), Point(1f, 0f), Point(0f, 1f), Point(1f, 1f)),
            texCoords = listOf(Point(0f, 0f), Point(1f, 0f), Point(0f, 1f), Point(1f, 1f)),
            colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.WHITE),
            indices = listOf(0, 1, 2, 1, 3, 2),
        )
        assertEquals(4, v.positions.size)
        assertEquals(4, v.texCoords!!.size)
        assertEquals(4, v.colors!!.size)
        assertEquals(6, v.indices!!.size)
    }

    @Test
    fun `Lattice minimal`() {
        val l = Lattice(xDivs = listOf(10, 20), yDivs = listOf(15))
        assertEquals(listOf(10, 20), l.xDivs)
        assertEquals(listOf(15), l.yDivs)
        assertNull(l.rects)
        assertNull(l.colors)
        assertNull(l.flags)
    }

    @Test
    fun `ColorSpace SRGB`() {
        assertEquals("sRGB", ColorSpace.SRGB.name)
        assertEquals(TransferFunction.SRGB, ColorSpace.SRGB.transferFunction)
        assertEquals(Gamut.SRGB, ColorSpace.SRGB.gamut)
    }

    @Test
    fun `ColorSpace DISPLAY_P3`() {
        assertEquals("Display P3", ColorSpace.DISPLAY_P3.name)
        assertEquals(Gamut.DISPLAY_P3, ColorSpace.DISPLAY_P3.gamut)
    }

    @Test
    fun `TransferFunction enum`() {
        assertEquals(4, TransferFunction.entries.size)
        assertTrue(TransferFunction.entries.containsAll(listOf(TransferFunction.SRGB, TransferFunction.LINEAR, TransferFunction.PQ, TransferFunction.HLG)))
    }

    @Test
    fun `Gamut enum`() {
        assertEquals(3, Gamut.entries.size)
        assertTrue(Gamut.entries.containsAll(listOf(Gamut.SRGB, Gamut.DISPLAY_P3, Gamut.REC2020)))
    }
}
