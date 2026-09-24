@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.picture

import java.nio.ByteBuffer
import java.util.Base64
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.Surface
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test

/** Public memory/wire witnesses for filter-owned Picture sources. */
class W6dPictureRuntimeEffectPictureTest {
    @Test
    fun equalPictureFiltersRemainDistinctAcrossWireReplay() {
        val expected = opaqueRedPair()
        val source = redSourcePicture()
        val first = ImageFilter.Picture(source)
        val second = ImageFilter.Picture(source)
        assertEquals(first, second)
        assertNotSame(first, second)

        val recorded = pictureWithTwoFilterOccurrences(first, second)

        assertReplayPixels(expected, recorded)
    }

    @Test
    fun sharedPictureFilterNodeReplaysInTwoSourceContexts() {
        val expected = opaqueRedPair()
        val shared = ImageFilter.Picture(redSourcePicture())
        val recorded = pictureWithTwoFilterOccurrences(shared, shared)

        assertReplayPixels(expected, recorded)
    }

    @Test
    fun pictureFilterCaptureIgnoresLaterSrcMutation() {
        val expected = ubyteArrayOf(
            255u, 0u, 0u, 255u,
            0u, 0u, 0u, 0u,
        )
        val sourceCull = RectF32.ofLTRB(0f, 0f, 2f, 1f)
        val source = PictureRecorder().also { recorder ->
            recorder.beginRecording(sourceCull).apply {
                drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Red, antiAlias = false))
                drawRect(RectF32.ofLTRB(1f, 0f, 2f, 1f), Paint(ColorARGB.Green, antiAlias = false))
            }
        }.finishRecordingAsPicture()
        val src = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        val recorded = PictureRecorder().also { recorder ->
            recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 2f, 1f)).drawRect(
                RectF32.ofLTRB(0f, 0f, 2f, 1f),
                Paint(ColorARGB.Blue, imageFilter = ImageFilter.Picture(source, src), antiAlias = false),
            )
        }.finishRecordingAsPicture()

        sourceCull.setLTRB(1f, 0f, 2f, 1f)
        src.setLTRB(1f, 0f, 2f, 1f)

        assertReplayPixels(expected, recorded)
    }

    @Test
    fun historicalNonLightingPictureStillDecodesWhileOldTwoDimensionalLightingFailsClosed() {
        val historical = assertNotNull(Picture.fromByteArray(historicalNonLightingFixture()))
        val oldLighting = Base64.getDecoder().decode(
            "S1BJQwAAAA4AAAAAAAAAAEAAAAA/gAAArRa6rgAAAAgAAAACAAAAAQAAAARzUkdCAAAABFNSR0IAAAAEU1JHQgAAAAEAAAAKP4AAAAAAAAD/////P4AAAD+AAAAAAAABAAAAAgAAAAUAAAACAAAAAAAAAABAAAAAP4AAAAEAAAABAAAAAQAAAAAAAAAAQAAAAD+AAAAAAAAC/////wAAAAlIQVJEX0VER0UAAAACAAAAAAAAAABAAAAAP4AAAAEAAAAEAAAACFNSQ19PVkVSAAAAAAIAAAABAAAABAAAAAA/gAAAAAAAAAAAAAAAAAAAP4AAAAAAAAAAAAAAAAAAAD+AAAAAAAAEUkVDVAH/////AAAAAAhTUkNfT1ZFUgAAAAABAAAAAAAAAARGSUxMAAAAAAAAAARCVVRUAAAABU1JVEVSQIAAAAAAAA==",
        )

        val roundTrip = assertNotNull(Picture.fromByteArray(historical.toByteArray()))
        val expected = UByteArray(8 * 8 * 4) { channel -> when (channel % 4) {
            2, 3 -> 255u
            else -> 0u
        } }
        val domain = RectF32.ofLTRB(0f, 0f, 8f, 8f)
        listOf(historical, roundTrip).forEach { candidate ->
            val result = Surface(8, 8).also { surface ->
                surface.canvas {
                    drawRect(
                        domain,
                        Paint(ColorARGB.Red, imageFilter = ImageFilter.Picture(candidate), antiAlias = false),
                    )
                }
            }.render()
            assertContentEquals(expected, result.pixels)
            assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
        }
        assertNull(Picture.fromByteArray(oldLighting))
    }

    private fun assertReplayPixels(expected: UByteArray, recorded: Picture) {
        val decoded = assertNotNull(Picture.fromByteArray(recorded.toByteArray()))
        for (candidate in listOf(recorded, decoded)) {
            val surface = Surface(2, 1)
            surface.canvas { drawPicture(candidate) }
            val result = surface.render()
            assertContentEquals(expected, result.pixels)
            assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
        }
    }

    private fun pictureWithTwoFilterOccurrences(first: ImageFilter.Picture, second: ImageFilter.Picture): Picture {
        val unit = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        return PictureRecorder().also { recorder ->
            recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 2f, 1f)).apply {
                drawRect(unit, Paint(ColorARGB.Green, imageFilter = first, antiAlias = false))
                save()
                translate(1f, 0f)
                drawRect(unit, Paint(ColorARGB.Green, imageFilter = second, antiAlias = false))
                restore()
            }
        }.finishRecordingAsPicture()
    }

    private fun redSourcePicture(): Picture = PictureRecorder().also { recorder ->
        val unit = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        recorder.beginRecording(unit).drawRect(unit, Paint(ColorARGB.Red, antiAlias = false))
    }.finishRecordingAsPicture()

    private fun opaqueRedPair(): UByteArray = ubyteArrayOf(
        255u, 0u, 0u, 255u,
        255u, 0u, 0u, 255u,
    )

    /** Checked-in historical non-lighting bytes, not output from the current writer. */
    private fun historicalNonLightingFixture(): ByteArray = ByteBuffer.allocate(33)
        .put("KPIC".encodeToByteArray())
        .putInt(8)
        .putFloat(0f).putFloat(0f).putFloat(8f).putFloat(8f)
        .putInt(1)
        .put(14)
        .putInt(ColorARGB.Blue.toPackedInt())
        .array()
}
