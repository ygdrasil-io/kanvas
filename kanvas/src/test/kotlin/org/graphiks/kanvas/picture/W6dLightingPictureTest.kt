package org.graphiks.kanvas.picture

import java.nio.ByteBuffer
import java.util.Base64
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point3F32
import org.graphiks.math.geometry.RectF32
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

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

    private fun picture(location: Point3F32): Picture = PictureRecorder().also { recorder ->
        recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 2f, 1f)).drawRect(
            RectF32.ofLTRB(0f, 0f, 2f, 1f),
            Paint(color = ColorARGB.White, imageFilter = ImageFilter.PointLitDiffuse(location, ColorARGB.White, 1f, 1f), antiAlias = false),
        )
    }.finishRecordingAsPicture()

    private fun historicalNonLightingFixture(): ByteArray = ByteBuffer.allocate(33)
        .put("KPIC".encodeToByteArray())
        .putInt(8)
        .putFloat(0f).putFloat(0f).putFloat(8f).putFloat(8f)
        .putInt(1)
        .put(14)
        .putInt(ColorARGB.Blue.toPackedInt())
        .array()
}
