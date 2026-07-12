package org.graphiks.kanvas.types

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

class Matrix33Test {
    @Test
    fun `identity matrix`() {
        val m = Matrix33.identity()
        assertEquals(1f, m.scaleX)
        assertEquals(0f, m.skewX)
        assertEquals(0f, m.transX)
        assertEquals(0f, m.skewY)
        assertEquals(1f, m.scaleY)
        assertEquals(0f, m.transY)
        assertEquals(0f, m.persp0)
        assertEquals(0f, m.persp1)
        assertEquals(1f, m.persp2)
    }

    @Test
    fun `translate matrix`() {
        val m = Matrix33.translate(10f, 20f)
        assertEquals(1f, m.scaleX)
        assertEquals(0f, m.skewX)
        assertEquals(10f, m.transX)
        assertEquals(0f, m.skewY)
        assertEquals(1f, m.scaleY)
        assertEquals(20f, m.transY)
    }

    @Test
    fun `scale matrix`() {
        val m = Matrix33.scale(2f, 3f)
        assertEquals(2f, m.scaleX)
        assertEquals(3f, m.scaleY)
        assertEquals(0f, m.transX)
        assertEquals(0f, m.transY)
    }

    @Test
    fun `rotate matrix`() {
        val m = Matrix33.rotate(90f)
        assertEquals(0f, m.scaleX, 0.001f)
        assertEquals(0f, m.scaleY, 0.001f)
        assertEquals(-1f, m.skewX, 0.001f)
        assertEquals(1f, m.skewY, 0.001f)
    }

    @Test
    fun `matrix multiply by point`() {
        val m = Matrix33.translate(10f, 20f)
        val p = m * Point(5f, 5f)
        assertEquals(15f, p.x)
        assertEquals(25f, p.y)
    }

    @Test
    fun `matrix multiply by matrix`() {
        val t = Matrix33.translate(10f, 0f)
        val s = Matrix33.scale(2f, 2f)
        val m = t * s
        val p = m * Point(5f, 5f)
        assertEquals(20f, p.x)
        assertEquals(10f, p.y)
    }

    @Test
    fun `axis aligned affine mapping keeps bounds ordered under negative scale`() {
        val matrix = Matrix33.translate(10f, 5f) * Matrix33.scale(-2f, 3f)
        val rrect = RRect(
            rect = Rect.fromLTRB(1f, 2f, 4f, 6f),
            topLeft = CornerRadii(1f, 2f),
            topRight = CornerRadii(2f, 3f),
            bottomRight = CornerRadii(3f, 4f),
            bottomLeft = CornerRadii(4f, 5f),
        )

        assertTrue(matrix.isAffine())
        assertTrue(matrix.isAxisAlignedAffine())
        assertEquals(Rect.fromLTRB(2f, 11f, 8f, 23f), matrix.mapAxisAlignedRect(rrect.rect))
        assertEquals(
            RRect(
                rect = Rect.fromLTRB(2f, 11f, 8f, 23f),
                topLeft = CornerRadii(2f, 6f),
                topRight = CornerRadii(4f, 9f),
                bottomRight = CornerRadii(6f, 12f),
                bottomLeft = CornerRadii(8f, 15f),
            ),
            rrect.mapAxisAligned(matrix),
        )
        assertFalse(Matrix33.rotate(45f).isAxisAlignedAffine())
        assertFalse(Matrix33.makeAll(1f, 0f, 0f, 0f, 1f, 0f, 0.1f, 0f, 1f).isAffine())
    }
}
