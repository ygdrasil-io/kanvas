package org.graphiks.math.matrix

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32

class AxisAlignedGeometryF32Test {
    @Test
    fun `maps an axis aligned rrect and preserves asymmetric mirrored corners`() {
        val matrix = Matrix3x3F32.translation(10f, 5f) * Matrix3x3F32.scaling(-2f, 3f)
        val rrect = RRectF32.of(
            rect = RectF32.ofLTRB(1f, 2f, 4f, 6f),
            topLeft = CornerRadiiF32.of(1f, 2f),
            topRight = CornerRadiiF32.of(2f, 3f),
            bottomRight = CornerRadiiF32.of(3f, 4f),
            bottomLeft = CornerRadiiF32.of(4f, 5f),
        )

        assertEquals(RectF32.ofLTRB(2f, 11f, 8f, 23f), matrix.mapAxisAlignedRect(rrect.rect))
        assertEquals(
            RRectF32.of(
                rect = RectF32.ofLTRB(2f, 11f, 8f, 23f),
                topLeft = CornerRadiiF32.of(4f, 9f),
                topRight = CornerRadiiF32.of(2f, 6f),
                bottomRight = CornerRadiiF32.of(8f, 15f),
                bottomLeft = CornerRadiiF32.of(6f, 12f),
            ),
            rrect.mapAxisAligned(matrix),
        )
    }

    @Test
    fun `rejects rotated and perspective transforms for axis aligned geometry`() {
        val rect = RectF32.ofLTRB(1f, 2f, 4f, 6f)

        assertFailsWith<IllegalArgumentException> {
            Matrix3x3F32.rotation(45f).mapAxisAlignedRect(rect)
        }
        assertFailsWith<IllegalArgumentException> {
            Matrix3x3F32.of(1f, 0f, 0f, 0f, 1f, 0f, 0.1f, 0f, 1f).mapAxisAlignedRect(rect)
        }
    }
}
