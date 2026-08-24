package org.graphiks.math.geometry

import kotlin.test.Test
import kotlin.test.assertEquals

class RRectF32Test {
    @Test
    fun `uniform-radius factory assigns every corner`() {
        val rounded = RRectF32.of(RectF32.ofOriginSize(2f, 3f, 12f, 8f), radius = 4f)

        assertEquals(RectF32.ofLTRB(2f, 3f, 14f, 11f), rounded.rect)
        assertEquals(CornerRadiiF32.of(4f, 4f), rounded.topLeft)
        assertEquals(CornerRadiiF32.of(4f, 4f), rounded.topRight)
        assertEquals(CornerRadiiF32.of(4f, 4f), rounded.bottomRight)
        assertEquals(CornerRadiiF32.of(4f, 4f), rounded.bottomLeft)
    }
}
