package org.graphiks.kanvas.surface

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class SurfaceTest {
    @Test fun `Surface dimensions`() { val s = Surface(320, 240); assertEquals(320, s.width); assertEquals(240, s.height); assertEquals(PixelFormat.RGBA8, s.format) }
    @Test fun `Surface BGRA8`() { assertEquals(PixelFormat.BGRA8, Surface(100, 100, PixelFormat.BGRA8).format) }
    @Test fun `Surface canvas DSL`() { val s = Surface(320, 240); s.canvas { drawRect(Rect.fromLTRB(0f,0f,100f,80f), Paint.fill(Color.RED)) }; val r = s.render(); assertEquals(1, r.stats.opsDispatched) }
}
