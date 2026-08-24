package org.graphiks.kanvas.picture

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.ClipStackOp
import org.graphiks.kanvas.canvas.DisplayListBuffer
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.DrawPathSourceOperation
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.KanvasTypeface
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.types.Color
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PictureTest {
    @Test
    fun `format 6 preserves expanded text provenance through round trip and playback`() {
        val path = DisplayOp.DrawPath.withSourceOperation(
            path = Path().addRect(RectF32.ofLTRB(1f, 2f, 3f, 4f)),
            paint = Paint.fill(Color.RED),
            transform = Matrix3x3F32.Identity,
            clip = ClipStack.WideOpen,
            sourceOperation = DrawPathSourceOperation.TEXT_EXPANDED,
        )
        val original = Picture(RectF32.ofLTRB(0f, 0f, 8f, 8f), listOf(path))

        val encoded = original.toByteArray()
        assertEquals(6, encoded.readBigEndianInt(offset = 4))
        val restored = requireNotNull(Picture.fromByteArray(encoded))
        assertEquals("text-expanded", assertIs<DisplayOp.DrawPath>(restored.ops.single()).sourceOperation)

        val playback = TestBuffer()
        restored.playback(Canvas(playback))
        assertEquals(
            "text-expanded",
            assertIs<DisplayOp.DrawPath>(playback.ops().single()).sourceOperation,
        )
    }

    @Test
    fun `format 5 DrawPath remains decodable with the truthful legacy source`() {
        val source = "text-expanded"
        val current = Picture(
            RectF32.ofLTRB(0f, 0f, 8f, 8f),
            listOf(
                DisplayOp.DrawPath.withSourceOperation(
                    path = Path().addRect(RectF32.ofLTRB(1f, 2f, 3f, 4f)),
                    paint = Paint.fill(Color.RED),
                    transform = Matrix3x3F32.Identity,
                    clip = ClipStack.WideOpen,
                    sourceOperation = DrawPathSourceOperation.TEXT_EXPANDED,
                ),
            ),
        ).toByteArray()
        val legacy = current.copyOf(current.size - 2 - source.encodeToByteArray().size).also { bytes ->
            bytes.writeBigEndianInt(offset = 4, value = 5)
        }

        val restored = requireNotNull(Picture.fromByteArray(legacy))

        assertEquals("drawPath", assertIs<DisplayOp.DrawPath>(restored.ops.single()).sourceOperation)
    }

    @Test
    fun `format 6 leaves non path opcode payload compatible with format 5`() {
        val current = Picture(
            RectF32.ofLTRB(0f, 0f, 8f, 8f),
            listOf(
                DisplayOp.DrawRect(
                    rect = RectF32.ofLTRB(1f, 2f, 3f, 4f),
                    paint = Paint.fill(Color.BLUE),
                    transform = Matrix3x3F32.Identity,
                    clip = ClipStack.WideOpen,
                ),
            ),
        ).toByteArray()
        val legacyHeader = current.copyOf().also { bytes ->
            bytes.writeBigEndianInt(offset = 4, value = 5)
        }

        val restored = requireNotNull(Picture.fromByteArray(legacyHeader))

        assertEquals(
            DisplayOp.DrawRect(
                rect = RectF32.ofLTRB(1f, 2f, 3f, 4f),
                paint = Paint.fill(Color.BLUE),
                transform = Matrix3x3F32.Identity,
                clip = ClipStack.WideOpen,
            ),
            restored.ops.single(),
        )
    }

    @Test
    fun `format 6 refuses an arbitrary serialized DrawPath source`() {
        val encoded = Picture(
            RectF32.ofLTRB(0f, 0f, 8f, 8f),
            listOf(
                DisplayOp.DrawPath.withSourceOperation(
                    path = Path().addRect(RectF32.ofLTRB(1f, 2f, 3f, 4f)),
                    paint = Paint.fill(Color.RED),
                    transform = Matrix3x3F32.Identity,
                    clip = ClipStack.WideOpen,
                    sourceOperation = DrawPathSourceOperation.TEXT_EXPANDED,
                ),
            ),
        ).toByteArray()
        "forged-source".encodeToByteArray().copyInto(
            destination = encoded,
            destinationOffset = encoded.size - "forged-source".length,
        )

        assertNull(Picture.fromByteArray(encoded))
    }

    @Test
    fun `format 5 round trip preserves each explicit image alpha authority`() {
        for (alpha in listOf(AlphaType.PREMUL, AlphaType.OPAQUE, AlphaType.UNPREMUL)) {
            val picture = pictureWithImageAlpha(alpha)
            val restored = requireNotNull(Picture.fromByteArray(picture.toByteArray()))
            val images = mutableListOf<Image>()
            restored.walkImages(images::add)
            val restoredImage = images.single()

            assertEquals(alpha, restoredImage.alphaType)
        }
    }

    @Test
    fun `formats one through four decode legacy images as conservative unpremultiplied`() {
        val v5 = pictureWithImageAlpha(AlphaType.PREMUL).toByteArray()
        val sourceId = "legacy-alpha".encodeToByteArray()
        val sourceIndex = v5.indexOfSubArray(sourceId)
        assertTrue(sourceIndex >= 0)
        val afterSource = sourceIndex + sourceId.size
        val colorSpaceStart = if (v5[afterSource].toInt() == 0) {
            afterSource + 1
        } else {
            val pixelLength = ((v5[afterSource + 1].toInt() and 0xFF) shl 24) or
                ((v5[afterSource + 2].toInt() and 0xFF) shl 16) or
                ((v5[afterSource + 3].toInt() and 0xFF) shl 8) or
                (v5[afterSource + 4].toInt() and 0xFF)
            afterSource + 5 + pixelLength
        }
        val colorSpaceNameLength = ((v5[colorSpaceStart].toInt() and 0xFF) shl 8) or (v5[colorSpaceStart + 1].toInt() and 0xFF)
        val alphaIndex = colorSpaceStart + 2 + colorSpaceNameLength + 2
        val legacy = v5.copyInto(ByteArray(v5.size - 1), 0, 0, alphaIndex).also { target ->
            v5.copyInto(target, alphaIndex, alphaIndex + 1, v5.size)
        }
        for (version in 1..4) {
            legacy[4] = 0
            legacy[5] = 0
            legacy[6] = 0
            legacy[7] = version.toByte()
            val restored = requireNotNull(Picture.fromByteArray(legacy))
            val images = mutableListOf<Image>()
            restored.walkImages(images::add)
            assertEquals(AlphaType.UNPREMUL, images.single().alphaType)
        }
    }

    @Test
    fun `PictureRecorder records and produces Picture`() {
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 100f, 100f))
        canvas.drawRect(RectF32.ofLTRB(10f, 10f, 50f, 50f), Paint.fill(org.graphiks.kanvas.types.Color.RED))
        val picture = recorder.finishRecordingAsPicture()

        assertEquals(RectF32.ofLTRB(0f, 0f, 100f, 100f), picture.cullRect)
        assertEquals(2, picture.approximateOpCount()) // clipRect + drawRect
        assertTrue(picture.uniqueID > 0)
    }

    @Test
    fun `Picture playback replays ops on target canvas`() {
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 100f, 100f))
        canvas.drawRect(RectF32.ofLTRB(10f, 10f, 50f, 50f), Paint.fill(org.graphiks.kanvas.types.Color.RED))
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
        val c1 = r1.beginRecording(RectF32.ofLTRB(0f, 0f, 10f, 10f))
        c1.drawRect(RectF32.ofLTRB(0f, 0f, 5f, 5f), Paint.fill(org.graphiks.kanvas.types.Color.RED))
        val inner = r1.finishRecordingAsPicture()

        val r2 = PictureRecorder()
        val c2 = r2.beginRecording(RectF32.ofLTRB(0f, 0f, 100f, 100f))
        c2.drawPicture(inner)
        val outer = r2.finishRecordingAsPicture()

        assertTrue(outer.approximateOpCount(true) > outer.approximateOpCount(false))
    }

    @Test
    fun `serialize and deserialize roundtrip`() {
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 100f, 100f))
        canvas.drawRect(RectF32.ofLTRB(10f, 10f, 50f, 50f), Paint.fill(Color.RED))
        canvas.drawRect(RectF32.ofLTRB(60f, 60f, 80f, 80f), Paint.fill(Color.BLUE))
        val original = recorder.finishRecordingAsPicture()

        val bytes = original.toByteArray()
        assertTrue(bytes.isNotEmpty())

        val restored = Picture.fromByteArray(bytes)
        assertNotNull(restored)
        assertEquals(original.cullRect, restored.cullRect)
        assertEquals(original.approximateOpCount(), restored.approximateOpCount())
    }

    @Test
    fun `roundtrip preserves a backdrop save layer record`() {
        val crop = RectF32.ofLTRB(0f, 10f, 100f, 90f)
        val rec = SaveLayerRec(
            backdrop = ImageFilter.Crop(crop, TileMode.DECAL, ImageFilter.Blur(3f, 3f)),
        )
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 100f, 100f))
        canvas.saveLayer(rec)
        canvas.restore()
        val original = recorder.finishRecordingAsPicture()

        val restored = requireNotNull(Picture.fromByteArray(original.toByteArray()))

        assertEquals(original.ops, restored.ops)
    }

    @Test
    fun `roundtrip preserves deferred outer clip on a save layer`() {
        val outerClip = RectF32.ofLTRB(10f, 10f, 90f, 90f)
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 100f, 100f))
        canvas.clipRect(outerClip, antiAlias = false)
        canvas.saveLayer()
        canvas.drawRect(RectF32.ofLTRB(0f, 0f, 100f, 100f), Paint.fill(Color.RED))
        canvas.restore()
        val original = recorder.finishRecordingAsPicture()

        val restored = requireNotNull(Picture.fromByteArray(original.toByteArray()))

        assertEquals(original.ops, restored.ops)
    }

    @Test
    fun `decodes fixed version 1 picture layer fixture`() {
        val picture = requireNotNull(Picture.fromByteArray(V1_LAYER_PICTURE_FIXTURE))

        assertEquals(RectF32.ofLTRB(0f, 0f, 10f, 10f), picture.cullRect)
        assertEquals(listOf(DisplayOp.BeginLayer(SaveLayerRec()), DisplayOp.EndLayer), picture.ops)
    }

    @Test
    fun `decodes fixed version 2 picture layer fixture`() {
        val picture = requireNotNull(Picture.fromByteArray(V2_LAYER_PICTURE_FIXTURE))

        assertEquals(RectF32.ofLTRB(0f, 0f, 10f, 10f), picture.cullRect)
        assertEquals(listOf(DisplayOp.BeginLayer(SaveLayerRec()), DisplayOp.EndLayer), picture.ops)
    }

    @Test
    fun `playback intersects serialized layer clip with the host clip and defers it from children`() {
        val pictureClip = RectF32.ofLTRB(10f, 10f, 50f, 50f)
        val hostClip = RectF32.ofLTRB(30f, 30f, 70f, 70f)
        val recorder = PictureRecorder()
        val recordingCanvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 100f, 100f))
        recordingCanvas.clipRect(pictureClip, antiAlias = true)
        recordingCanvas.saveLayer()
        recordingCanvas.drawRect(RectF32.ofLTRB(0f, 0f, 100f, 100f), Paint.fill(Color.RED))
        recordingCanvas.restore()
        val picture = recorder.finishRecordingAsPicture()

        val targetBuffer = TestBuffer()
        val targetCanvas = Canvas(targetBuffer)
        targetCanvas.clipRect(hostClip, antiAlias = true)
        picture.playback(targetCanvas)

        val begin = targetBuffer.ops().filterIsInstance<DisplayOp.BeginLayer>().single()
        val compositeClip = assertIs<ClipStack.Complex>(begin.rec.compositeClip)
        val rectOps = compositeClip.ops.filterIsInstance<ClipStackOp.RectOp>()
        assertEquals(
            listOf(
                RectF32.ofLTRB(0f, 0f, 100f, 100f),
                pictureClip,
                hostClip,
            ),
            rectOps.map(ClipStackOp.RectOp::rect),
        )
        assertTrue(rectOps.all(ClipStackOp.RectOp::antiAlias))
        assertEquals(
            ClipStack.WideOpen,
            targetBuffer.ops().filterIsInstance<DisplayOp.DrawRect>().single().clip,
        )
    }

    @Test
    fun `fromByteArray returns null for invalid data`() {
        assertNull(Picture.fromByteArray(byteArrayOf(0, 1, 2, 3)))
    }

    @Test
    fun `fromByteArray returns null for empty data`() {
        assertNull(Picture.fromByteArray(ByteArray(0)))
    }

    private fun ByteArray.readBigEndianInt(offset: Int): Int =
        ((this[offset].toInt() and 0xff) shl 24) or
            ((this[offset + 1].toInt() and 0xff) shl 16) or
            ((this[offset + 2].toInt() and 0xff) shl 8) or
            (this[offset + 3].toInt() and 0xff)

    private fun ByteArray.writeBigEndianInt(offset: Int, value: Int) {
        this[offset] = (value ushr 24).toByte()
        this[offset + 1] = (value ushr 16).toByte()
        this[offset + 2] = (value ushr 8).toByte()
        this[offset + 3] = value.toByte()
    }

    @Test
    fun `opCount returns top-level operation count`() {
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 100f, 100f))
        canvas.drawRect(RectF32.ofLTRB(0f, 0f, 10f, 10f), Paint.fill(Color.RED))
        canvas.drawRect(RectF32.ofLTRB(20f, 20f, 30f, 30f), Paint.fill(Color.BLUE))
        val picture = recorder.finishRecordingAsPicture()

        assertEquals(3, picture.opCount) // clipRect + 2x drawRect
    }

    @Test
    fun `totalOpCount includes nested pictures`() {
        val innerRec = PictureRecorder()
        val innerCanvas = innerRec.beginRecording(RectF32.ofLTRB(0f, 0f, 10f, 10f))
        innerCanvas.drawRect(RectF32.ofLTRB(0f, 0f, 5f, 5f), Paint.fill(Color.RED))
        val inner = innerRec.finishRecordingAsPicture()

        val outerRec = PictureRecorder()
        val outerCanvas = outerRec.beginRecording(RectF32.ofLTRB(0f, 0f, 100f, 100f))
        outerCanvas.drawPicture(inner)
        val outer = outerRec.finishRecordingAsPicture()

        assertTrue(outer.totalOpCount > outer.opCount)
    }

    @Test
    fun `walkImages invokes action for each embedded image`() {
        val img = Image(4, 4, ColorType.RGBA_8888, "test", ByteArray(64) { 0 })
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 100f, 100f))
        canvas.drawImage(img, RectF32.ofLTRB(10f, 10f, 50f, 50f))
        canvas.drawImage(img, RectF32.ofLTRB(60f, 60f, 80f, 80f))
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
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 100f, 100f))
        canvas.drawRect(RectF32.ofLTRB(10f, 10f, 50f, 50f), Paint.fill(Color.RED))
        val picture = recorder.finishRecordingAsPicture()

        var called = false
        picture.walkImages { called = true }
        assertFalse(called)
    }

    @Test
    fun `walkNestedPictures invokes action for each nested picture`() {
        val inner = PictureRecorder().apply {
            beginRecording(RectF32.ofLTRB(0f, 0f, 10f, 10f)).drawRect(RectF32.ofLTRB(0f, 0f, 5f, 5f), Paint.fill(Color.RED))
        }.finishRecordingAsPicture()

        val outerRec = PictureRecorder()
        val outerCanvas = outerRec.beginRecording(RectF32.ofLTRB(0f, 0f, 100f, 100f))
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
        val glyphRuns = listOf(KanvasGlyphRun(listOf(65u, 66u), listOf(Point2F32(10f, 10f), Point2F32(30f, 10f))))
        val tf = KanvasTypeface("test-font")
        val blob1 = TextBlob(glyphRuns, tf, 16f)
        val blob2 = TextBlob(glyphRuns, tf, 16f) // structurally equal but different reference

        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 200f, 200f))
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
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 100f, 100f))
        canvas.drawRect(RectF32.ofLTRB(10f, 10f, 50f, 50f), Paint.fill(Color.RED))
        val picture = recorder.finishRecordingAsPicture()

        var called = false
        picture.walkTextBlobs { called = true }
        assertFalse(called)
    }

    @Test
    fun `forEachOp visits all top-level ops in order`() {
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 100f, 100f))
        canvas.drawRect(RectF32.ofLTRB(0f, 0f, 10f, 10f), Paint.fill(Color.RED))
        canvas.drawRect(RectF32.ofLTRB(20f, 20f, 30f, 30f), Paint.fill(Color.BLUE))
        val picture = recorder.finishRecordingAsPicture()

        val ops = mutableListOf<DisplayOp>()
        picture.forEachOp { ops.add(it) }
        assertEquals(picture.opCount, ops.size)
        assertTrue(ops.count { it is DisplayOp.DrawRect } == 2)
    }

    @Test
    fun `forEachOp nested visits ops from child pictures`() {
        val innerRec = PictureRecorder()
        val innerCanvas = innerRec.beginRecording(RectF32.ofLTRB(0f, 0f, 10f, 10f))
        innerCanvas.drawRect(RectF32.ofLTRB(0f, 0f, 5f, 5f), Paint.fill(Color.RED))
        val inner = innerRec.finishRecordingAsPicture()

        val outerRec = PictureRecorder()
        val outerCanvas = outerRec.beginRecording(RectF32.ofLTRB(0f, 0f, 100f, 100f))
        outerCanvas.drawPicture(inner)
        outerCanvas.drawRect(RectF32.ofLTRB(50f, 50f, 80f, 80f), Paint.fill(Color.BLUE))
        val outer = outerRec.finishRecordingAsPicture()

        val collected = mutableListOf<DisplayOp>()
        outer.forEachOp(nested = true) { collected.add(it) }

        // outer: clipRect + DrawPicture + drawRect = 3
        // inner: clipRect + drawRect = 2
        assertTrue(collected.size >= 4)
        assertTrue(collected.any { it is DisplayOp.DrawPicture })
    }
}

private fun pictureWithImageAlpha(alphaType: AlphaType): Picture {
    val recorder = PictureRecorder()
    recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 2f, 2f)).drawImage(
        Image(1, 1, ColorType.RGBA_8888, "legacy-alpha", byteArrayOf(1, 2, 3, 4), alphaType = alphaType),
        RectF32.ofLTRB(0f, 0f, 1f, 1f),
    )
    return recorder.finishRecordingAsPicture()
}

private fun ByteArray.indexOfSubArray(needle: ByteArray): Int =
    indices.firstOrNull { index -> index + needle.size <= size && needle.indices.all { offset -> this[index + offset] == needle[offset] } } ?: -1

private val V1_LAYER_PICTURE_FIXTURE = byteArrayOf(
    0x4B, 0x50, 0x49, 0x43, // KPIC
    0x00, 0x00, 0x00, 0x01, // format version 1
    0x00, 0x00, 0x00, 0x00, // cull left
    0x00, 0x00, 0x00, 0x00, // cull top
    0x41, 0x20, 0x00, 0x00, // cull right = 10f
    0x41, 0x20, 0x00, 0x00, // cull bottom = 10f
    0x00, 0x00, 0x00, 0x02, // op count
    0x11, // BeginLayer
    0x00, // bounds absent
    0x00, // paint absent
    0x12, // EndLayer
)

private val V2_LAYER_PICTURE_FIXTURE = byteArrayOf(
    0x4B, 0x50, 0x49, 0x43, // KPIC
    0x00, 0x00, 0x00, 0x02, // format version 2
    0x00, 0x00, 0x00, 0x00, // cull left
    0x00, 0x00, 0x00, 0x00, // cull top
    0x41, 0x20, 0x00, 0x00, // cull right = 10f
    0x41, 0x20, 0x00, 0x00, // cull bottom = 10f
    0x00, 0x00, 0x00, 0x02, // op count
    0x11, // BeginLayer
    0x00, // bounds absent
    0x00, // paint absent
    0xFF.toByte(), // null v2 backdrop filter
    0x12, // EndLayer
)

private class TestBuffer : DisplayListBuffer {
    private val ops = mutableListOf<DisplayOp>()
    override fun append(op: DisplayOp) { ops.add(op) }
    override fun ops(): List<DisplayOp> = ops.toList()
}
