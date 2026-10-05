package org.graphiks.kanvas.picture

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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertNull

class W5ePictureImageSamplingTest {
    @Test
    fun `old archive profiles reject the new ImageNine geometry`() {
        // Literal interoperability payloads: a valid Nine record using tag13
        // cannot be smuggled into Picture8/schema2, Picture9/schema3 or10/schema3.
        // Existing old ImagePatch-Nine replay and current10 round trips remain below.
        val malformed = listOf(
            "S1BJQwAAAAgAAAAAAAAAAEBAAABAQAAArRa6rgAAAAIAAAADAAAAAwAAAARzUkdCAAAABFNSR0IAAAAEU1JHQgAAAAIAAAAFAAAAAgAAAAAAAAAAQEAAAEBAAAABAAAAAQAAAA0AAAAJdzVlLWltYWdlP4AAAD+AAABAAAAAQAAAAAAAAAAAAAAAQEAAAEBAAAAAAAABAAAABwAAAAEAAAAJdzVlLWltYWdlAAAAAQAAAAEAAAAJUkdCQV84ODg4AAAACFVOUFJFTVVMAAAABHNSR0IAAAAEU1JHQgAAAARTUkdCAAAABAAAAAQBAgMEAAAABUNMQU1QAAAABUNMQU1QAAAAAQAAAAdERUZBVUxUAAAAAgAAAAAAAAAAQEAAAEBAAAABAAAAAQAAAAE/gAAAAAAAAAAAAAAAAAAAP4AAAAAAAAAAAAAAAAAAAD+AAAAAAAAKSU1BR0VfTklORQABAAAAAQAAAAl3NWUtaW1hZ2UAAAABAAAAAQAAAAlSR0JBXzg4ODgAAAAIVU5QUkVNVUwAAAAEc1JHQgAAAARTUkdCAAAABFNSR0IAAAAEAAAABAECAwQA",
            "S1BJQwAAAAkAAAAAAAAAAEBAAABAQAAArRa6rgAAAAMAAAADAAAAAwAAAARzUkdCAAAABFNSR0IAAAAEU1JHQgAAAAIAAAAFAAAAAgAAAAAAAAAAQEAAAEBAAAABAAAAAQAAAA0AAAAJdzVlLWltYWdlP4AAAD+AAABAAAAAQAAAAAAAAAAAAAAAQEAAAEBAAAAAAAABAAAABwAAAAEAAAAJdzVlLWltYWdlAAAAAQAAAAEAAAAJUkdCQV84ODg4AAAACFVOUFJFTVVMAAAABHNSR0IAAAAEU1JHQgAAAARTUkdCAAAABAAAAAQBAgMEAAAABUNMQU1QAAAABUNMQU1QAAAAAQAAAAdERUZBVUxUAAAAAgAAAAAAAAAAQEAAAEBAAAABAAAAAQAAAAE/gAAAAAAAAAAAAAAAAAAAP4AAAAAAAAAAAAAAAAAAAD+AAAAAAAAKSU1BR0VfTklORQABAAAAAQAAAAl3NWUtaW1hZ2UAAAABAAAAAQAAAAlSR0JBXzg4ODgAAAAIVU5QUkVNVUwAAAAEc1JHQgAAAARTUkdCAAAABFNSR0IAAAAEAAAABAECAwQA",
            "S1BJQwAAAAoAAAAAAAAAAEBAAABAQAAArRa6rgAAAAMAAAADAAAAAwAAAARzUkdCAAAABFNSR0IAAAAEU1JHQgAAAAIAAAAFAAAAAgAAAAAAAAAAQEAAAEBAAAABAAAAAQAAAA0AAAAJdzVlLWltYWdlP4AAAD+AAABAAAAAQAAAAAAAAAAAAAAAQEAAAEBAAAAAAAABAAAABwAAAAEAAAAJdzVlLWltYWdlAAAAAQAAAAEAAAAJUkdCQV84ODg4AAAACFVOUFJFTVVMAAAABHNSR0IAAAAEU1JHQgAAAARTUkdCAAAABAAAAAQBAgMEAAAABUNMQU1QAAAABUNMQU1QAAAAAQAAAAdERUZBVUxUAAAAAgAAAAAAAAAAQEAAAEBAAAABAAAAAQAAAAE/gAAAAAAAAAAAAAAAAAAAP4AAAAAAAAAAAAAAAAAAAD+AAAAAAAAKSU1BR0VfTklORQABAAAAAQAAAAl3NWUtaW1hZ2UAAAABAAAAAQAAAAlSR0JBXzg4ODgAAAAIVU5QUkVNVUwAAAAEc1JHQgAAAARTUkdCAAAABFNSR0IAAAAEAAAABAECAwQA",
        )
        malformed.forEachIndexed { indexI32, literal ->
            assertNull(Picture.fromByteArray(java.util.Base64.getDecoder().decode(literal)), "old profile $indexI32")
        }
    }

    @Test
    fun `Picture replay preserves partial image source sampling and paint shader`() {
        val image = Image.fromPixels(
            width = 2,
            height = 1,
            pixels = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
            colorType = ColorType.RGBA_8888,
            sourceId = "w5e-partial-source",
        )
        val picture = record {
            drawImageRect(
                image = image,
                src = RectF32.ofLTRB(1f, 0f, 2f, 1f),
                dst = RectF32.ofLTRB(0f, 0f, 2f, 2f),
                sampling = SamplingOptions.LINEAR,
                paint = Paint(shader = Shader.SolidColor(ColorARGB.Red)),
            )
        }

        val restored = assertNotNull(Picture.fromByteArray(picture.toByteArray()))
        val replayed = replayIntoPicture(restored)

        assertContentEquals(picture.toByteArray(), replayed.toByteArray())
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
    fun `repeated unchanged aliases share one retained budget reservation`() {
        val source = Image.fromPixels(1, 1, byteArrayOf(1, 2, 3, 4), sourceId = "w5e-alias")
        val recorder = PictureRecorder(SceneCaptureLimits(maxImageBytesI64 = 4))
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 1f, 1f))

        canvas.drawImage(source, RectF32.ofLTRB(0f, 0f, 1f, 1f))
        canvas.drawImage(source, RectF32.ofLTRB(0f, 0f, 1f, 1f))

        assertContentEquals(
            record(cullRect = RectF32.ofLTRB(0f, 0f, 1f, 1f)) {
                drawImage(source, RectF32.ofLTRB(0f, 0f, 1f, 1f))
                drawImage(source, RectF32.ofLTRB(0f, 0f, 1f, 1f))
            }.toByteArray(),
            recorder.finishRecordingAsPicture().toByteArray(),
        )
    }

    @Test
    fun `changed source bytes reserve a distinct retained snapshot`() {
        val sourcePixels = byteArrayOf(1, 2, 3, 4)
        val source = Image.fromPixels(1, 1, sourcePixels, sourceId = "w5e-changed-source")
        val recorder = PictureRecorder(SceneCaptureLimits(maxImageBytesI64 = 7))
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 1f, 1f))

        canvas.drawImage(source, RectF32.ofLTRB(0f, 0f, 1f, 1f))
        sourcePixels[0] = 9

        val refusal = assertFailsWith<SceneRecordingLimitException> {
            canvas.drawImage(source, RectF32.ofLTRB(0f, 0f, 1f, 1f))
        }

        assertEquals("scene-recording-image-bytes-exceeded", refusal.diagnostic.code.value)
        sourcePixels[0] = 1
        canvas.drawImage(source, RectF32.ofLTRB(0f, 0f, 1f, 1f))
        assertContentEquals(
            record(cullRect = RectF32.ofLTRB(0f, 0f, 1f, 1f)) {
                drawImage(source, RectF32.ofLTRB(0f, 0f, 1f, 1f))
                drawImage(source, RectF32.ofLTRB(0f, 0f, 1f, 1f))
            }.toByteArray(),
            recorder.finishRecordingAsPicture().toByteArray(),
        )
    }

    @Test
    fun `failed image reservation rolls back and permits a later valid capture`() {
        val recorder = PictureRecorder(SceneCaptureLimits(maxImageBytesI64 = 4))
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 1f, 1f))
        val oversized = Image.fromPixels(1, 1, ByteArray(5), sourceId = "w5e-oversized")
        val accepted = Image.fromPixels(1, 1, byteArrayOf(1, 2, 3, 4), sourceId = "w5e-accepted")

        assertFailsWith<SceneRecordingLimitException> {
            canvas.drawImage(oversized, RectF32.ofLTRB(0f, 0f, 1f, 1f))
        }
        canvas.drawImage(accepted, RectF32.ofLTRB(0f, 0f, 1f, 1f))

        assertContentEquals(
            record(cullRect = RectF32.ofLTRB(0f, 0f, 1f, 1f)) {
                drawImage(accepted, RectF32.ofLTRB(0f, 0f, 1f, 1f))
            }.toByteArray(),
            recorder.finishRecordingAsPicture().toByteArray(),
        )
    }

    @Test
    fun `legacyImagePayloadDefaultsToNearest`() {
        val bytes = java.util.Base64.getDecoder().decode(
            requireNotNull(javaClass.getResource("/picture/format-9-image-nearest.base64")).readText().trim(),
        )
        val replayed = replayIntoPicture(assertNotNull(Picture.fromByteArray(bytes)))

        assertContentEquals(readFixture("format-10-v9-image-nearest-expected.base64"), replayed.toByteArray())
    }

    @Test
    fun `legacy v8 image payload replays through the public recorder`() {
        val legacy = assertNotNull(Picture.fromByteArray(readFixture("format-8-image-nearest.base64")))

        assertContentEquals(
            record {
                drawImage(
                    Image.fromPixels(1, 1, byteArrayOf(1, 2, 3, 4), sourceId = "w5e-v8-image"),
                    RectF32.ofLTRB(0f, 0f, 2f, 2f),
                    SamplingOptions.NEAREST,
                )
            }.toByteArray(),
            replayIntoPicture(legacy).toByteArray(),
        )
    }

    @Test
    fun `historical v9 image nine patch normalizes through public replay`() {
        val historical = assertNotNull(Picture.fromByteArray(readFixture("format-9-image-nine.base64")))

        assertContentEquals(
            record {
                drawImageNine(
                    image(),
                    RectF32.ofLTRB(1f, 1f, 2f, 2f),
                    RectF32.ofLTRB(0f, 0f, 3f, 3f),
                )
            }.toByteArray(),
            replayIntoPicture(historical).toByteArray(),
        )
    }

    private fun image(): Image = Image.fromPixels(
        width = 1,
        height = 1,
        pixels = byteArrayOf(1, 2, 3, 4),
        sourceId = "w5e-image",
    )

    private fun record(
        cullRect: RectF32 = RectF32.ofLTRB(0f, 0f, 3f, 3f),
        draw: org.graphiks.kanvas.canvas.Canvas.() -> Unit,
    ): Picture =
        PictureRecorder().also { recorder ->
            recorder.beginRecording(cullRect).draw()
        }.finishRecordingAsPicture()

    private fun replayIntoPicture(picture: Picture): Picture = PictureRecorder().also { recorder ->
        picture.playback(recorder.beginRecording(picture.cullRect))
    }.finishRecordingAsPicture()

    private fun readFixture(name: String): ByteArray = java.util.Base64.getDecoder().decode(
        requireNotNull(javaClass.getResource("/picture/$name")).readText().trim(),
    )

    private fun assertStablePicturePayload(picture: Picture) {
        val bytes = picture.toByteArray()
        val restored = assertNotNull(Picture.fromByteArray(bytes))
        assertEquals(10, ByteBuffer.wrap(bytes, 4, 4).int)
        kotlin.test.assertContentEquals(bytes, restored.toByteArray())
    }

}
