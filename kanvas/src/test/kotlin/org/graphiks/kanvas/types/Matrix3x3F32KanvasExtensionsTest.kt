package org.graphiks.kanvas.types

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.graphiks.math.matrix.Matrix3x3F32

class Matrix3x3F32KanvasExtensionsTest {
    @Test
    fun `maps an axis aligned rrect and preserves asymmetric mirrored corners`() {
        val matrix = Matrix3x3F32.translation(10f, 5f) * Matrix3x3F32.scaling(-2f, 3f)
        val rrect = RRect(
            rect = Rect.fromLTRB(1f, 2f, 4f, 6f),
            topLeft = CornerRadii(1f, 2f),
            topRight = CornerRadii(2f, 3f),
            bottomRight = CornerRadii(3f, 4f),
            bottomLeft = CornerRadii(4f, 5f),
        )

        assertEquals(Rect.fromLTRB(2f, 11f, 8f, 23f), matrix.mapAxisAlignedRect(rrect.rect))
        assertEquals(
            RRect(
                rect = Rect.fromLTRB(2f, 11f, 8f, 23f),
                topLeft = CornerRadii(4f, 9f),
                topRight = CornerRadii(2f, 6f),
                bottomRight = CornerRadii(8f, 15f),
                bottomLeft = CornerRadii(6f, 12f),
            ),
            rrect.mapAxisAligned(matrix),
        )
    }

    @Test
    fun `rejects rotated and perspective transforms for axis aligned geometry`() {
        val rect = Rect.fromLTRB(1f, 2f, 4f, 6f)

        assertFailsWith<IllegalArgumentException> {
            Matrix3x3F32.rotation(45f).mapAxisAlignedRect(rect)
        }
        assertFailsWith<IllegalArgumentException> {
            Matrix3x3F32.of(1f, 0f, 0f, 0f, 1f, 0f, 0.1f, 0f, 1f).mapAxisAlignedRect(rect)
        }
    }
}
