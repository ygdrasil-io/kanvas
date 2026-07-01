package org.graphiks.kanvas.canvas

import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.pipeline.ClipOp
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class ClipStackTest {
    @Test fun `WideOpen is default`() { assertTrue(ClipStack.WideOpen is ClipStack) }
    @Test fun `DeviceRect clip`() { assertTrue(ClipStack.DeviceRect(Rect.fromLTRB(0f, 0f, 100f, 100f)) is ClipStack.DeviceRect) }
    @Test fun `Complex clip`() {
        val rectOp: ClipStackOp = ClipStackOp.RectOp(Rect.fromLTRB(0f, 0f, 50f, 50f), ClipOp.INTERSECT)
        val clip = ClipStack.Complex(listOf(rectOp))
        assertTrue(clip is ClipStack.Complex)
    }
}
