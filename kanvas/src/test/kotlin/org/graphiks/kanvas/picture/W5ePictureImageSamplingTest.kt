package org.graphiks.kanvas.picture

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.canvas.DisplayListBuffer
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.SceneRecordingLimitException
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.render.ir.SceneCaptureLimits
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertFalse

class W5ePictureImageSamplingTest {
    @Test
    fun `explicit image sampling preserves paint shader through Picture replay`() {
        val image = Image.fromPixels(
            width = 1,
            height = 1,
            pixels = byteArrayOf(-1),
            colorType = ColorType.ALPHA_8,
            sourceId = "w5e-alpha-mask",
        )
        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 1f, 1f)).drawImage(
                image = image,
                dst = RectF32.ofLTRB(0f, 0f, 1f, 1f),
                sampling = SamplingOptions.LINEAR,
                paint = Paint(shader = Shader.SolidColor(ColorARGB.Red)),
            )
        }.finishRecordingAsPicture()

        val restored = assertNotNull(Picture.fromByteArray(picture.toByteArray()))
        val replay = RecordingBuffer()
        restored.playback(Canvas(replay))

        assertEquals(
            Shader.SolidColor(ColorARGB.Red),
            assertIs<DisplayOp.DrawImage>(replay.ops().single()).paint?.shader,
        )
    }

    @Test
    fun `drawImageSamplingPayloadRoundTripIsStable`() {
        val source = image()
        val picture = record { drawImage(source, RectF32.ofLTRB(0f, 0f, 2f, 2f), SamplingOptions.LINEAR) }

        assertStablePicturePayload(picture)
    }

    @Test
    fun `cubicBitsProduceDistinctStablePicturePayloads`() {
        val source = image()
        val first = record { drawImage(source, RectF32.ofLTRB(0f, 0f, 2f, 2f), SamplingOptions.Cubic(Float.fromBits(0x3eaaaaaa), .5f)) }
        val second = record { drawImage(source, RectF32.ofLTRB(0f, 0f, 2f, 2f), SamplingOptions.Cubic(Float.fromBits(0x3eaaaaab), .5f)) }

        assertFalse(first.toByteArray().contentEquals(second.toByteArray()))
        assertStablePicturePayload(first)
        assertStablePicturePayload(second)
    }

    @Test
    fun `imageNinePayloadRemainsDistinctFromImagePatch`() {
        val source = image()
        val direct = record { drawImage(source, RectF32.ofLTRB(0f, 0f, 3f, 3f)) }
        val nine = record { drawImageNine(source, RectF32.ofLTRB(1f, 1f, 2f, 2f), RectF32.ofLTRB(0f, 0f, 3f, 3f)) }

        assertFalse(direct.toByteArray().contentEquals(nine.toByteArray()))
        assertStablePicturePayload(nine)
    }

    @Test
    fun `pixelRowBytesPayloadRoundTripIsStable`() {
        val source = Image.fromPixels(
            width = 1,
            height = 1,
            pixels = byteArrayOf(1, 2, 3, 4, 9, 9, 9, 9, 7, 7),
            colorType = ColorType.RGBA_8888,
            sourceId = "w5e-stride",
            rowBytesI32 = 8,
        )
        val restored = assertNotNull(Picture.fromByteArray(record { drawImage(source, RectF32.ofLTRB(0f, 0f, 1f, 1f)) }.toByteArray()))
        val restoredImages = mutableListOf<Image>()
        restored.walkImages(restoredImages::add)
        val restoredImage = restoredImages.single()

        assertEquals(8, restoredImage.rowBytesI32)
        assertStablePicturePayload(restored)
    }

    @Test
    fun `recordingBudgetCountsPaddingAndPayloadTailBeforeSnapshotCopy`() {
        val paddedAndTailed = Image.fromPixels(
            width = 1,
            height = 1,
            pixels = ByteArray(10),
            colorType = ColorType.RGBA_8888,
            sourceId = "w5e-budget",
            rowBytesI32 = 8,
        )
        val recorder = PictureRecorder(SceneCaptureLimits(maxImageBytesI64 = 9))

        val refusal = assertFailsWith<SceneRecordingLimitException> {
            recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 1f, 1f))
                .drawImage(paddedAndTailed, RectF32.ofLTRB(0f, 0f, 1f, 1f))
        }

        assertEquals("scene-recording-image-bytes-exceeded", refusal.diagnostic.code.value)
    }

    @Test
    fun `legacyImagePayloadDefaultsToNearest`() {
        val bytes = java.util.Base64.getDecoder().decode(
            requireNotNull(javaClass.getResource("/picture/format-9-image-nearest.base64")).readText().trim(),
        )
        val replay = RecordingBuffer()
        assertNotNull(Picture.fromByteArray(bytes)).playback(Canvas(replay))

        assertEquals(SamplingOptions.NEAREST, assertIs<DisplayOp.DrawImage>(replay.ops().single { it is DisplayOp.DrawImage }).sampling)
    }

    private fun image(): Image = Image.fromPixels(
        width = 1,
        height = 1,
        pixels = byteArrayOf(1, 2, 3, 4),
        sourceId = "w5e-image",
    )

    private fun record(draw: org.graphiks.kanvas.canvas.Canvas.() -> Unit): Picture =
        PictureRecorder().also { recorder ->
            recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 3f, 3f)).draw()
        }.finishRecordingAsPicture()

    private fun assertStablePicturePayload(picture: Picture) {
        val bytes = picture.toByteArray()
        val restored = assertNotNull(Picture.fromByteArray(bytes))
        assertEquals(10, ByteBuffer.wrap(bytes, 4, 4).int)
        kotlin.test.assertContentEquals(bytes, restored.toByteArray())
    }


    private class RecordingBuffer : DisplayListBuffer {
        private val recorded = mutableListOf<DisplayOp>()

        override fun append(op: DisplayOp) {
            recorded += op
        }

        override fun ops(): List<DisplayOp> = recorded.toList()
    }
}
