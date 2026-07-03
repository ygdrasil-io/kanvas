package org.graphiks.kanvas.picture

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.canvas.DisplayListBuffer
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.KanvasTypeface
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun `opCount returns top-level operation count`() {
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(Rect.fromLTRB(0f, 0f, 100f, 100f))
        canvas.drawRect(Rect.fromLTRB(0f, 0f, 10f, 10f), Paint.fill(Color.RED))
        canvas.drawRect(Rect.fromLTRB(20f, 20f, 30f, 30f), Paint.fill(Color.BLUE))
        val picture = recorder.finishRecordingAsPicture()

        assertEquals(3, picture.opCount) // clipRect + 2x drawRect
    }

    @Test
    fun `totalOpCount includes nested pictures`() {
        val innerRec = PictureRecorder()
        val innerCanvas = innerRec.beginRecording(Rect.fromLTRB(0f, 0f, 10f, 10f))
        innerCanvas.drawRect(Rect.fromLTRB(0f, 0f, 5f, 5f), Paint.fill(Color.RED))
        val inner = innerRec.finishRecordingAsPicture()

        val outerRec = PictureRecorder()
        val outerCanvas = outerRec.beginRecording(Rect.fromLTRB(0f, 0f, 100f, 100f))
        outerCanvas.drawPicture(inner)
        val outer = outerRec.finishRecordingAsPicture()

        assertTrue(outer.totalOpCount > outer.opCount)
    }

    @Test
    fun `walkImages invokes action for each embedded image`() {
        val img = Image(4, 4, ColorType.RGBA_8888, "test", ByteArray(64) { 0 })
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(Rect.fromLTRB(0f, 0f, 100f, 100f))
        canvas.drawImage(img, Rect.fromLTRB(10f, 10f, 50f, 50f))
        canvas.drawImage(img, Rect.fromLTRB(60f, 60f, 80f, 80f))
        val picture = recorder.finishRecordingAsPicture()

        val collected = mutableListOf<Image>()
        picture.walkImages { collected.add(it) }
        assertEquals(2, collected.size)
        assertEquals(img, collected[0])
        assertEquals(img, collected[1])
    }

    @Test
    fun `walkImages does not invoke action when no images present`() {
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(Rect.fromLTRB(0f, 0f, 100f, 100f))
        canvas.drawRect(Rect.fromLTRB(10f, 10f, 50f, 50f), Paint.fill(Color.RED))
        val picture = recorder.finishRecordingAsPicture()

        var called = false
        picture.walkImages { called = true }
        assertFalse(called)
    }

    @Test
    fun `walkNestedPictures invokes action for each nested picture`() {
        val inner = PictureRecorder().apply {
            beginRecording(Rect.fromLTRB(0f, 0f, 10f, 10f)).drawRect(Rect.fromLTRB(0f, 0f, 5f, 5f), Paint.fill(Color.RED))
        }.finishRecordingAsPicture()

        val outerRec = PictureRecorder()
        val outerCanvas = outerRec.beginRecording(Rect.fromLTRB(0f, 0f, 100f, 100f))
        outerCanvas.drawPicture(inner)
        outerCanvas.drawPicture(inner)
        val outer = outerRec.finishRecordingAsPicture()

        val collected = mutableListOf<Picture>()
        outer.walkNestedPictures { collected.add(it) }
        assertEquals(2, collected.size)
        assertEquals(inner, collected[0])
        assertEquals(inner, collected[1])
    }

    @Test
    fun `walkTextBlobs deduplicates by reference and invokes action once per distinct blob`() {
        val glyphRuns = listOf(KanvasGlyphRun(listOf(65u, 66u), listOf(Point(10f, 10f), Point(30f, 10f))))
        val tf = KanvasTypeface("test-font")
        val blob1 = TextBlob(glyphRuns, tf, 16f)
        val blob2 = TextBlob(glyphRuns, tf, 16f) // structurally equal but different reference

        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(Rect.fromLTRB(0f, 0f, 200f, 200f))
        canvas.drawText(blob1, 0f, 50f, Paint.fill(Color.BLACK))
        canvas.drawText(blob1, 0f, 100f, Paint.fill(Color.BLACK)) // same reference -> dedup
        canvas.drawText(blob2, 0f, 150f, Paint.fill(Color.BLACK)) // different reference
        val picture = recorder.finishRecordingAsPicture()

        val collected = mutableListOf<TextBlob>()
        picture.walkTextBlobs { collected.add(it) }
        assertEquals(2, collected.size) // blob1 deduped to 1, blob2 = 1 more
        assertEquals(blob1, collected[0])
        assertEquals(blob2, collected[1])
    }

    @Test
    fun `walkTextBlobs does not invoke action when no text present`() {
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(Rect.fromLTRB(0f, 0f, 100f, 100f))
        canvas.drawRect(Rect.fromLTRB(10f, 10f, 50f, 50f), Paint.fill(Color.RED))
        val picture = recorder.finishRecordingAsPicture()

        var called = false
        picture.walkTextBlobs { called = true }
        assertFalse(called)
    }

    @Test
    fun `forEachOp visits all top-level ops in order`() {
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(Rect.fromLTRB(0f, 0f, 100f, 100f))
        canvas.drawRect(Rect.fromLTRB(0f, 0f, 10f, 10f), Paint.fill(Color.RED))
        canvas.drawRect(Rect.fromLTRB(20f, 20f, 30f, 30f), Paint.fill(Color.BLUE))
        val picture = recorder.finishRecordingAsPicture()

        val ops = mutableListOf<DisplayOp>()
        picture.forEachOp { ops.add(it) }
        assertEquals(picture.opCount, ops.size)
        assertTrue(ops.count { it is DisplayOp.DrawRect } == 2)
    }

    @Test
    fun `forEachOp nested visits ops from child pictures`() {
        val innerRec = PictureRecorder()
        val innerCanvas = innerRec.beginRecording(Rect.fromLTRB(0f, 0f, 10f, 10f))
        innerCanvas.drawRect(Rect.fromLTRB(0f, 0f, 5f, 5f), Paint.fill(Color.RED))
        val inner = innerRec.finishRecordingAsPicture()

        val outerRec = PictureRecorder()
        val outerCanvas = outerRec.beginRecording(Rect.fromLTRB(0f, 0f, 100f, 100f))
        outerCanvas.drawPicture(inner)
        outerCanvas.drawRect(Rect.fromLTRB(50f, 50f, 80f, 80f), Paint.fill(Color.BLUE))
        val outer = outerRec.finishRecordingAsPicture()

        val collected = mutableListOf<DisplayOp>()
        outer.forEachOp(nested = true) { collected.add(it) }

        // outer: clipRect + DrawPicture + drawRect = 3
        // inner: clipRect + drawRect = 2
        assertTrue(collected.size >= 4)
        assertTrue(collected.any { it is DisplayOp.DrawPicture })
    }
}

private class TestBuffer : DisplayListBuffer {
    private val ops = mutableListOf<DisplayOp>()
    override fun append(op: DisplayOp) { ops.add(op) }
    override fun ops(): List<DisplayOp> = ops.toList()
}
