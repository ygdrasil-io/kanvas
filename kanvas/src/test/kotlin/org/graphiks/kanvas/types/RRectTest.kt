package org.graphiks.kanvas.types

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class RRectTest {
    @Test
    fun `RRect with uniform radius constructor`() {
        val rect = Rect.fromLTRB(0f, 0f, 100f, 80f)
        val rrect = RRect(rect, 10f)
        assertEquals(CornerRadii(10f, 10f), rrect.topLeft)
        assertEquals(CornerRadii(10f, 10f), rrect.topRight)
        assertEquals(CornerRadii(10f, 10f), rrect.bottomRight)
        assertEquals(CornerRadii(10f, 10f), rrect.bottomLeft)
    }

    @Test
    fun `RRect with per-corner radii`() {
        val rect = Rect.fromLTRB(0f, 0f, 100f, 80f)
        val tl = CornerRadii(5f, 5f)
        val tr = CornerRadii(10f, 10f)
        val br = CornerRadii(15f, 20f)
        val bl = CornerRadii(0f, 0f)
        val rrect = RRect(rect, tl, tr, br, bl)
        assertEquals(tl, rrect.topLeft)
        assertEquals(tr, rrect.topRight)
        assertEquals(br, rrect.bottomRight)
        assertEquals(bl, rrect.bottomLeft)
    }

    @Test
    fun `RRect default radii are zero`() {
        val rrect = RRect(Rect.fromLTRB(0f, 0f, 10f, 10f))
        assertEquals(CornerRadii(0f, 0f), rrect.topLeft)
    }
}
