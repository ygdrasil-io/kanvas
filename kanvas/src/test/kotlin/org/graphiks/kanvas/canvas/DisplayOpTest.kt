package org.graphiks.kanvas.canvas

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.types.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class DisplayOpTest {
    @Test fun `DrawRect op`() { assertTrue(DisplayOp.DrawRect(Rect.fromLTRB(0f,0f,100f,80f), Paint.fill(Color.RED), Matrix33.identity(), ClipStack.WideOpen) is DisplayOp.DrawRect) }
    @Test fun `DrawPath op`() { assertTrue(DisplayOp.DrawPath(Path().addRect(Rect.fromLTRB(0f,0f,100f,100f)), Paint.fill(Color.BLUE), Matrix33.identity(), ClipStack.WideOpen) is DisplayOp.DrawPath) }
    @Test fun `SetTransform op`() { assertTrue(DisplayOp.SetTransform(Matrix33.translate(10f, 20f)) is DisplayOp.SetTransform) }
    @Test fun `BeginLayer and EndLayer`() { assertTrue(DisplayOp.BeginLayer(null, null) is DisplayOp.BeginLayer); assertTrue(DisplayOp.EndLayer is DisplayOp.EndLayer) }
}
