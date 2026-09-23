@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.picture

import java.nio.ByteBuffer
import java.util.Base64
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.surface.W6dLightingCpuOracle
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point3F32
import org.graphiks.math.geometry.RectF32
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class W6dLightingPictureTest {
    @Test
    fun historicalV14TwoDimensionalLightingFixtureFailsClosed() {
        assertNull(Picture.fromByteArray(Base64.getDecoder().decode(
            "S1BJQwAAAA4AAAAAAAAAAEAAAAA/gAAArRa6rgAAAAgAAAACAAAAAQAAAARzUkdCAAAABFNSR0IAAAAEU1JHQgAAAAEAAAAKP4AAAAAAAAD/////P4AAAD+AAAAAAAABAAAAAgAAAAUAAAACAAAAAAAAAABAAAAAP4AAAAEAAAABAAAAAQAAAAAAAAAAQAAAAD+AAAAAAAAC/////wAAAAlIQVJEX0VER0UAAAACAAAAAAAAAABAAAAAP4AAAAEAAAAEAAAACFNSQ19PVkVSAAAAAAIAAAABAAAABAAAAAA/gAAAAAAAAAAAAAAAAAAAP4AAAAAAAAAAAAAAAAAAAD+AAAAAAAAEUkVDVAH/////AAAAAAhTUkNfT1ZFUgAAAAABAAAAAAAAAARGSUxMAAAAAAAAAARCVVRUAAAABU1JVEVSQIAAAAAAAA==",
        )))
        assertNotNull(Picture.fromByteArray(historicalNonLightingFixture()))
    }

    @Test
    fun picture15PreservesLightingZThroughMemoryAndWireReplay() {
        val first = picture(Point3F32(1f, 0f, 1f))
        val second = picture(Point3F32(1f, 0f, 2f))
        val firstBytes = first.toByteArray()
        assertEquals(15, ByteBuffer.wrap(firstBytes).getInt(4))
        assertEquals(9, ByteBuffer.wrap(firstBytes).getInt(28))
        val decoded = assertNotNull(Picture.fromByteArray(firstBytes))
        assertEquals(first.ops, decoded.ops)
        assertNotEquals(first.ops, second.ops)
        assertContentEquals(firstBytes, decoded.toByteArray())
        assert(!firstBytes.contentEquals(second.toByteArray()))
    }

    /**
     * Catches a Picture capture or wire replay which loses the frozen Z component and therefore
     * reuses the same lighting graph identity for otherwise equal point lights.
     */
    @Test
    fun pictureMemoryAndWireReplayKeepZDistinctLightingPixels() {
        val alpha = floatArrayOf(
            0f, 1f, 0f,
            1f, 1f, 0f,
            0f, 1f, 0f,
        )
        // The independent oracle is deliberately complete before recording either Picture.
        val nearExpected = W6dLightingCpuOracle.remainingFamilyRgba8(
            W6dLightingCpuOracle.Family.POINT_DIFFUSE, 3, 3, alpha,
            locationX = 1f, locationY = 0f, locationZ = 1f,
            surfaceDepth = 1f, coefficient = 1f,
        )
        val farExpected = W6dLightingCpuOracle.remainingFamilyRgba8(
            W6dLightingCpuOracle.Family.POINT_DIFFUSE, 3, 3, alpha,
            locationX = 1f, locationY = 0f, locationZ = 100f,
            surfaceDepth = 1f, coefficient = 1f,
        )
        assertFalse(nearExpected.contentEquals(farExpected), "The independent fixture must distinguish Z at fixed XY.")

        val nearPicture = lightingPicture(Point3F32(1f, 0f, 1f))
        val farPicture = lightingPicture(Point3F32(1f, 0f, 100f))
        assertNotEquals(nearPicture.ops, farPicture.ops)

        listOf(
            nearExpected to nearPicture,
            farExpected to farPicture,
        ).forEach { (expected, memoryPicture) ->
            val wirePicture = assertNotNull(Picture.fromByteArray(memoryPicture.toByteArray()))
            listOf(memoryPicture, wirePicture).forEach { replayPicture ->
                val result = Surface(3, 3).also { surface ->
                    surface.canvas { replayPicture.playback(this) }
                }.render()
                assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), result.nativeEvidenceScopeKinds.toString())
                assertPixelsNear(expected, result.pixels, maxChannelDelta = 2)
            }
        }
    }

    private fun picture(location: Point3F32): Picture = PictureRecorder().also { recorder ->
        recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 2f, 1f)).drawRect(
            RectF32.ofLTRB(0f, 0f, 2f, 1f),
            Paint(color = ColorARGB.White, imageFilter = ImageFilter.PointLitDiffuse(location, ColorARGB.White, 1f, 1f), antiAlias = false),
        )
    }.finishRecordingAsPicture()

    private fun lightingPicture(location: Point3F32): Picture = PictureRecorder().also { recorder ->
        recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 3f, 3f)).apply {
            saveLayer(org.graphiks.kanvas.canvas.SaveLayerRec(paint = Paint(
                color = ColorARGB.White,
                imageFilter = ImageFilter.PointLitDiffuse(location, ColorARGB.White, 1f, 1f),
                antiAlias = false,
            )))
            listOf(1 to 0, 0 to 1, 1 to 1, 1 to 2).forEach { (x, y) ->
                drawRect(RectF32.ofLTRB(x.toFloat(), y.toFloat(), x + 1f, y + 1f), Paint(ColorARGB.White, antiAlias = false))
            }
            restore()
        }
    }.finishRecordingAsPicture()

    private fun assertPixelsNear(expected: UByteArray, actual: UByteArray, maxChannelDelta: Int) {
        assertEquals(expected.size, actual.size)
        expected.indices.forEach { index ->
            assertTrue(kotlin.math.abs(expected[index].toInt() - actual[index].toInt()) <= maxChannelDelta,
                "channel $index expected=${expected[index]} actual=${actual[index]}")
        }
    }

    private fun historicalNonLightingFixture(): ByteArray = ByteBuffer.allocate(33)
        .put("KPIC".encodeToByteArray())
        .putInt(8)
        .putFloat(0f).putFloat(0f).putFloat(8f).putFloat(8f)
        .putInt(1)
        .put(14)
        .putInt(ColorARGB.Blue.toPackedInt())
        .array()
}
