package org.graphiks.kanvas.skia

import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.ClipStackOp
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.math.geometry.Point2F32
import org.graphiks.kanvas.types.Rect
import org.graphiks.math.color.ColorARGB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GmCanvasTest {
    @Test
    fun `clear records a canvas clear operation`() {
        val surface = Surface(width = 10, height = 10)
        val canvas = GmCanvas(surface.canvas(), width = 10, height = 10)

        canvas.clear(Color.RED)

        assertEquals(listOf(DisplayOp.Clear(Color.RED)), surface.snapshotOps())
    }

    @Test
    fun `clear accepts skia color constants`() {
        val surface = Surface(width = 10, height = 10)
        val canvas = GmCanvas(surface.canvas(), width = 10, height = 10)

        canvas.clear(ColorARGB.Gray.toPackedInt())

        assertEquals(listOf(DisplayOp.Clear(Color.fromArgbInt(ColorARGB.Gray.toPackedInt()))), surface.snapshotOps())
    }

    @Test
    fun `restore removes path clip added after save`() {
        val surface = Surface(width = 20, height = 20)
        val canvas = GmCanvas(surface.canvas(), width = 20, height = 20)
        val clip = Path { }.apply { addRect(Rect.fromLTRB(2f, 2f, 8f, 8f)) }

        canvas.save()
        canvas.clipPath(clip)
        canvas.restore()
        canvas.drawRect(Rect.fromLTRB(0f, 0f, 10f, 10f), Paint())

        val draw = surface.snapshotOps().filterIsInstance<DisplayOp.DrawRect>().single()
        assertEquals(ClipStack.WideOpen, draw.clip)
    }

    @Test
    fun `clip path follows adapter transform`() {
        val surface = Surface(width = 40, height = 40)
        val canvas = GmCanvas(surface.canvas(), width = 40, height = 40)
        val clip = Path { }.apply { addRect(Rect.fromLTRB(1f, 2f, 5f, 6f)) }

        canvas.translate(10f, 20f)
        canvas.clipPath(clip)

        val setClip = surface.snapshotOps().filterIsInstance<DisplayOp.SetClip>().single()
        val pathOp = (setClip.clip as ClipStack.Complex).ops.single() as ClipStackOp.PathOp
        assertTrue(pathOp.path.contains(Point2F32(13f, 24f)))
        assertFalse(pathOp.path.contains(Point2F32(3f, 4f)))
    }

    @Test
    fun `clip rect defaults to a hard device clip`() {
        val surface = Surface(width = 32, height = 32)
        val canvas = GmCanvas(surface.canvas(), width = 32, height = 32)

        canvas.clipRect(Rect.fromLTRB(2f, 4f, 12f, 20f))
        canvas.drawRect(Rect.fromLTRB(0f, 0f, 24f, 24f), Paint())

        val draw = surface.snapshotOps().filterIsInstance<DisplayOp.DrawRect>().single()
        assertEquals(
            ClipStack.DeviceRect(Rect.fromLTRB(2f, 4f, 12f, 20f), antiAlias = false),
            draw.clip,
        )
    }

    @Test
    fun `rotated clip rect is captured as a device path`() {
        val surface = Surface(width = 32, height = 32)

        surface.canvas {
            rotate(45f)
            clipRect(Rect(2f, 2f, 10f, 10f), antiAlias = true)
        }

        val clip = surface.snapshotOps().filterIsInstance<DisplayOp.SetClip>().single().clip
        val element = (clip as ClipStack.Complex).ops.single()
        assertTrue(element is ClipStackOp.PathOp)
    }
}
