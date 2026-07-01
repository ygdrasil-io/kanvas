package org.graphiks.kanvas.picture

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.canvas.DisplayListBuffer
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PictureTest {
    @Test
    fun `PictureRecorder records and produces Picture`() {
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(Rect.fromLTRB(0f, 0f, 100f, 100f))
        canvas.drawRect(Rect.fromLTRB(10f, 10f, 50f, 50f), Paint.fill(org.graphiks.kanvas.types.Color.RED))
        val picture = recorder.finishRecordingAsPicture()

        assertEquals(Rect.fromLTRB(0f, 0f, 100f, 100f), picture.cullRect)
        assertEquals(2, picture.approximateOpCount()) // clipRect + drawRect
        assertTrue(picture.uniqueID > 0)
    }

    @Test
    fun `Picture playback replays ops on target canvas`() {
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(Rect.fromLTRB(0f, 0f, 100f, 100f))
        canvas.drawRect(Rect.fromLTRB(10f, 10f, 50f, 50f), Paint.fill(org.graphiks.kanvas.types.Color.RED))
        val picture = recorder.finishRecordingAsPicture()

        val targetBuffer = TestBuffer()
        val targetCanvas = Canvas(targetBuffer)
        picture.playback(targetCanvas)

        val ops = targetBuffer.ops()
        assertTrue(ops.isNotEmpty())
        assertTrue(ops.any { it is DisplayOp.DrawRect })
    }

    @Test
    fun `approximateOpCount with nested pictures`() {
        val r1 = PictureRecorder()
        val c1 = r1.beginRecording(Rect.fromLTRB(0f, 0f, 10f, 10f))
        c1.drawRect(Rect.fromLTRB(0f, 0f, 5f, 5f), Paint.fill(org.graphiks.kanvas.types.Color.RED))
        val inner = r1.finishRecordingAsPicture()

        val r2 = PictureRecorder()
        val c2 = r2.beginRecording(Rect.fromLTRB(0f, 0f, 100f, 100f))
        c2.drawPicture(inner)
        val outer = r2.finishRecordingAsPicture()

        assertTrue(outer.approximateOpCount(true) > outer.approximateOpCount(false))
    }

    @Test
    fun `serialize and deserialize roundtrip`() {
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(Rect.fromLTRB(0f, 0f, 100f, 100f))
        canvas.drawRect(Rect.fromLTRB(10f, 10f, 50f, 50f), Paint.fill(Color.RED))
        canvas.drawRect(Rect.fromLTRB(60f, 60f, 80f, 80f), Paint.fill(Color.BLUE))
        val original = recorder.finishRecordingAsPicture()

        val bytes = original.toByteArray()
        assertTrue(bytes.isNotEmpty())

        val restored = Picture.fromByteArray(bytes)
        assertNotNull(restored)
        assertEquals(original.cullRect, restored.cullRect)
        assertEquals(original.approximateOpCount(), restored.approximateOpCount())
    }

    @Test
    fun `fromByteArray returns null for invalid data`() {
        assertNull(Picture.fromByteArray(byteArrayOf(0, 1, 2, 3)))
    }

    @Test
    fun `fromByteArray returns null for empty data`() {
        assertNull(Picture.fromByteArray(ByteArray(0)))
    }
}

private class TestBuffer : DisplayListBuffer {
    private val ops = mutableListOf<DisplayOp>()
    override fun append(op: DisplayOp) { ops.add(op) }
    override fun ops(): List<DisplayOp> = ops.toList()
}
