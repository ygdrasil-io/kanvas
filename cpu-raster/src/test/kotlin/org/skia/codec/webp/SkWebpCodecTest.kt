package org.skia.codec.webp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.codec.SkCodec
import org.skia.foundation.SkEncodedImageFormat
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import java.io.ByteArrayInputStream

/**
 * D3.4 verification suite for [SkWebpCodec].
 *
 * Covers :
 *  - WEBP signature dispatch (`RIFF…WEBP` 12-byte prefix), including
 *    the negative case for `RIFF` containers that are *not* WEBP
 *    (`RIFF…WAVE`, `RIFF…AVI `, etc.).
 *  - End-to-end decode of a real, upstream-provided WEBP fixture
 *    (`stoplight.webp` from the Skia DM resources tree, 340 bytes,
 *    11×29 px) — verifies the TwelveMonkeys ImageIO plugin is
 *    reachable on the runtime classpath.
 *  - `getInfo` / `getImage` produce `kRGBA_8888 / kUnpremul / sRGB`
 *    bitmaps of the right dimensions.
 *  - `getPixels` rejects mismatched destination geometry.
 */
class SkWebpCodecTest {

    @Test
    fun `MakeFromData rejects non-WEBP bytes`() {
        assertNull(SkCodec.MakeFromData(ByteArray(0)))
        assertNull(SkCodec.MakeFromData("not a webp".toByteArray()))
        // RIFF container that is NOT WEBP (e.g. WAV) — must not match.
        // Layout : "RIFF" + 4 size bytes + "WAVE" tag.
        val wav = byteArrayOf(
            0x52, 0x49, 0x46, 0x46, 0x10, 0x00, 0x00, 0x00,
            0x57, 0x41, 0x56, 0x45,
        )
        assertNull(SkCodec.MakeFromData(wav))
    }

    @Test
    fun `MakeFromData dispatches a real WEBP fixture to SkWebpCodec`() {
        val bytes = loadFixture("stoplight.webp")
        val codec = SkCodec.MakeFromData(bytes)
        assertNotNull(codec, "stoplight.webp must decode through TwelveMonkeys imageio-webp")
        assertTrue(codec is SkWebpCodec)
        assertEquals(SkEncodedImageFormat.kWEBP, codec!!.getEncodedFormat())
        // stoplight.webp is 11×29 — established by inspecting the
        // upstream resource. If TwelveMonkeys ever reports something
        // different, the test failure flags the regression.
        assertEquals(11, codec.dimensions().width)
        assertEquals(29, codec.dimensions().height)
    }

    @Test
    fun `MakeFromStream is equivalent to MakeFromData`() {
        val bytes = loadFixture("stoplight.webp")
        val viaData = SkCodec.MakeFromData(bytes)!!
        val viaStream = SkCodec.MakeFromStream(ByteArrayInputStream(bytes))!!
        assertEquals(viaData.dimensions(), viaStream.dimensions())
        assertEquals(viaData.getEncodedFormat(), viaStream.getEncodedFormat())
    }

    @Test
    fun `getImage produces an 8888 sRGB unpremul bitmap`() {
        val codec = SkCodec.MakeFromData(loadFixture("stoplight.webp"))!!
        val info = codec.getInfo()
        assertEquals(SkColorType.kRGBA_8888, info.colorType)
        assertEquals(SkAlphaType.kUnpremul, info.alphaType)
        assertTrue(info.colorSpace.isSRGB())
        // No ICC handling for WEBP yet — see class kdoc.
        assertNull(codec.getICCProfile())

        val (bitmap, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(11, bitmap!!.width)
        assertEquals(29, bitmap.height)
    }

    @Test
    fun `getPixels rejects mismatched destination geometry`() {
        val codec = SkCodec.MakeFromData(loadFixture("stoplight.webp"))!!
        val info = codec.getInfo()
        val tooSmall = SkBitmap(2, 2, info.colorSpace, SkColorType.kRGBA_8888)
        assertEquals(SkCodec.Result.kInvalidParameters, codec.getPixels(info, tooSmall))
        val wrongType = SkBitmap(info.width, info.height, info.colorSpace, SkColorType.kRGBA_F16Norm)
        assertEquals(SkCodec.Result.kInvalidParameters, codec.getPixels(info, wrongType))
    }

    private fun loadFixture(name: String): ByteArray {
        val path = "codec-fixtures/$name"
        return SkWebpCodecTest::class.java.classLoader.getResourceAsStream(path)
            ?.use { it.readBytes() }
            ?: error("missing test fixture: $path")
    }
}
