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
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.surface.Surface
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test

/** Public memory/wire witnesses for filter-owned Picture sources. */
class W6dPictureRuntimeEffectPictureTest {
    @Test
    fun backdropAndPreviousReplayAcrossMemoryAndWire() {
        // Backdrop is the sole initializer even when initWithPrevious is also captured. Its
        // half-red snapshot is established before the opaque green child, in memory and wire.
        val expected = ubyteArrayOf(43u, 181u, 93u, 255u, 176u, 35u, 51u, 128u)
        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 2f, 1f)).apply {
                drawRect(RectF32.ofLTRB(0f, 0f, 2f, 1f), Paint(ColorARGB.of(255, 239, 51, 73), antiAlias = false))
                saveLayer(SaveLayerRec(
                    backdrop = imageOpacity(.5f),
                    initWithPrevious = true,
                    paint = Paint(blendMode = BlendMode.SRC, antiAlias = false),
                ))
                drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.of(255, 43, 181, 93), antiAlias = false))
                restore()
            }
        }.finishRecordingAsPicture()
        val decoded = assertNotNull(Picture.fromByteArray(picture.toByteArray()))

        for (candidate in listOf(picture, decoded)) {
            val result = Surface(2, 1).also { surface -> surface.canvas { drawPicture(candidate) } }.render()
            assertContentEquals(expected, result.pixels)
            assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
        }
    }

    @Test
    fun runtimeImageOpacityWireReplayKeepsW5hHashes() {
        val expected = ubyteArrayOf(60u, 30u, 15u, 128u)
        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 1f, 1f)).drawRect(
                RectF32.ofLTRB(0f, 0f, 1f, 1f),
                Paint(shader = runtimeImageOpacitySourceShader, imageFilter = imageOpacity(.5f), antiAlias = false),
            )
        }.finishRecordingAsPicture()
        val decoded = assertNotNull(Picture.fromByteArray(picture.toByteArray()))

        for (candidate in listOf(picture, decoded)) {
            val result = Surface(1, 1).also { surface -> surface.canvas { drawPicture(candidate) } }.render()
            assertContentEquals(expected, result.pixels)
            assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
        }
        assertEquals(
            "2c7646732d3484bdc2d99a813af1ae721872bbfeb3b2030e2ee4eb39be53d6c8",
            assertNotNull(RuntimeEffect.registered("kanvas.runtime.child-opacity", 1)).abiHash,
        )
    }

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
    fun historicalPicture14Schema8NonLightingReplaysWhileOldTwoDimensionalLightingFailsClosed() {
        val historicalBytes = historicalV14Schema8NonLightingFixture()
        assertEquals(14, ByteBuffer.wrap(historicalBytes).getInt(4))
        assertEquals(8, ByteBuffer.wrap(historicalBytes).getInt(28))
        val historical = assertNotNull(Picture.fromByteArray(historicalBytes))
        val oldLighting = Base64.getDecoder().decode(
            "S1BJQwAAAA4AAAAAAAAAAEAAAAA/gAAArRa6rgAAAAgAAAACAAAAAQAAAARzUkdCAAAABFNSR0IAAAAEU1JHQgAAAAEAAAAKP4AAAAAAAAD/////P4AAAD+AAAAAAAABAAAAAgAAAAUAAAACAAAAAAAAAABAAAAAP4AAAAEAAAABAAAAAQAAAAAAAAAAQAAAAD+AAAAAAAAC/////wAAAAlIQVJEX0VER0UAAAACAAAAAAAAAABAAAAAP4AAAAEAAAAEAAAACFNSQ19PVkVSAAAAAAIAAAABAAAABAAAAAA/gAAAAAAAAAAAAAAAAAAAP4AAAAAAAAAAAAAAAAAAAD+AAAAAAAAEUkVDVAH/////AAAAAAhTUkNfT1ZFUgAAAAABAAAAAAAAAARGSUxMAAAAAAAAAARCVVRUAAAABU1JVEVSQIAAAAAAAA==",
        )

        val roundTripBytes = historical.toByteArray()
        assertEquals(15, ByteBuffer.wrap(roundTripBytes).getInt(4))
        assertEquals(9, ByteBuffer.wrap(roundTripBytes).getInt(28))
        val roundTrip = assertNotNull(Picture.fromByteArray(roundTripBytes))
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

    // Matches the surface fixture's exact encoded sRGB input for .5f linear-premul opacity.
    private val runtimeImageOpacitySourceShader: Shader = Shader.Image(
        Image.fromPixels(1, 1, byteArrayOf(85, 45, 24, -1), alphaType = AlphaType.PREMUL),
    )

    private fun imageOpacity(alpha: Float): ImageFilter.RuntimeEffect = ImageFilter.RuntimeEffect(
        requireNotNull(RuntimeEffect.registered("kanvas.runtime.image-opacity", 1)),
        UniformBlock { float1("alpha", alpha) },
    )

    /**
     * Immutable non-lighting output from the historical Picture writer at e5ce423ab
     * (`cf57e09d2^`): its own exporter test observed wire 14 and SceneArchive schema 8.
     * This resource is deliberately not generated by the current writer.
     */
    private fun historicalV14Schema8NonLightingFixture(): ByteArray = Base64.getDecoder().decode(
        requireNotNull(javaClass.getResource("/picture/format-14-schema-8-opaque-blue-e5ce423ab.base64"))
            .readText()
            .trim(),
    )
}
