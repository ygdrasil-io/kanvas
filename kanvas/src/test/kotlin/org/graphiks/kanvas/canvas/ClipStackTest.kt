package org.graphiks.kanvas.canvas

import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.pipeline.ClipOp
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.assertIs

class ClipStackTest {
    @Test fun `WideOpen is default`() { assertTrue(ClipStack.WideOpen is ClipStack) }
    @Test fun `DeviceRect clip`() { assertTrue(ClipStack.DeviceRect(Rect.fromLTRB(0f, 0f, 100f, 100f)) is ClipStack.DeviceRect) }
    @Test fun `Complex clip`() {
        val rectOp: ClipStackOp = ClipStackOp.RectOp(Rect.fromLTRB(0f, 0f, 50f, 50f), ClipOp.INTERSECT)
        val clip = ClipStack.Complex(listOf(rectOp))
        assertTrue(clip is ClipStack.Complex)
    }

    @Test
    fun `intersection retains complex difference operations instead of collapsing bounds`() {
        val outer = ClipStack.Complex(
            listOf(
                ClipStackOp.RectOp(Rect.fromLTRB(0f, 0f, 80f, 80f), ClipOp.INTERSECT, antiAlias = true),
                ClipStackOp.RectOp(Rect.fromLTRB(20f, 20f, 40f, 40f), ClipOp.DIFFERENCE, antiAlias = false),
            ),
        )

        val combined = outer.intersectWith(ClipStack.DeviceRect(Rect.fromLTRB(10f, 10f, 60f, 60f), antiAlias = true))

        assertEquals(
            outer.ops + ClipStackOp.RectOp(Rect.fromLTRB(10f, 10f, 60f, 60f), ClipOp.INTERSECT, antiAlias = true),
            assertIs<ClipStack.Complex>(combined).ops,
        )
    }

    @Test
    fun `intersection keeps overlapping device rectangles compact`() {
        val combined = ClipStack.DeviceRect(Rect.fromLTRB(0f, 0f, 50f, 50f), antiAlias = false)
            .intersectWith(ClipStack.DeviceRect(Rect.fromLTRB(20f, 20f, 80f, 80f), antiAlias = true))

        assertEquals(
            ClipStack.DeviceRect(Rect.fromLTRB(20f, 20f, 50f, 50f), antiAlias = true),
            combined,
        )
    }
}
