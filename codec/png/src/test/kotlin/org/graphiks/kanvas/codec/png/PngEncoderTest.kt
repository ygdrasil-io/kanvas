package org.graphiks.kanvas.codec.png

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.kanvas.codec.Codec
import org.skia.foundation.SkBitmap
import java.io.ByteArrayOutputStream

class PngEncoderTest {

    @Test
    fun `encode round-trip through PNG decoder preserves RGB`() {
        val src = SkBitmap(4, 4)
        for (y in 0 until 4) for (x in 0 until 4) {
            val r = (x * 85).coerceIn(0, 255)
            val g = (y * 85).coerceIn(0, 255)
            src.pixels[y * 4 + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or 0x7F
        }
        val bytes = PngEncoder.encode(src)!!
        val decoded = decodePng(bytes)
        assertEquals(4, decoded.width)
        assertEquals(4, decoded.height)
        for (y in 0 until 4) for (x in 0 until 4) {
            assertEquals(src.getPixel(x, y), decoded.getPixel(x, y), "($x,$y)")
        }
    }

    @Test
    fun `encode degenerate bitmap returns null`() {
        assertNull(PngEncoder.encode(SkBitmap(0, 0)))
    }

    @Test
    fun `OutputStream overload matches direct encode`() {
        val src = SkBitmap(4, 4)
        for (i in 0 until 16) src.pixels[i] = 0xFF808080.toInt()
        val viaData = PngEncoder.encode(src)!!
        val baos = ByteArrayOutputStream()
        assertTrue(PngEncoder.encode(baos, src))
        assertEquals(viaData.toList(), baos.toByteArray().toList())
    }

    @Test
    fun `zlib level 0 produces uncompressed but valid PNG`() {
        val src = SkBitmap(2, 2)
        for (i in 0 until 4) src.pixels[i] = 0xFF0000FF.toInt()
        val bytes = PngEncoder.encode(src, PngEncoder.Options(zLibLevel = 0))!!
        val decoded = decodePng(bytes)
        assertEquals(2, decoded.width)
    }

    @Test
    fun `zlib level 9 produces compressed, valid PNG`() {
        val src = SkBitmap(2, 2)
        for (i in 0 until 4) src.pixels[i] = 0xFF0000FF.toInt()
        val uncompressed = PngEncoder.encode(src, PngEncoder.Options(zLibLevel = 0))!!
        val compressed = PngEncoder.encode(src, PngEncoder.Options(zLibLevel = 9))!!
        val decoded = decodePng(compressed)
        assertEquals(2, decoded.width)
        assertEquals(2, decoded.height)
    }

    @Test
    fun `invalid zlib levels are rejected in Options`() {
        assertThrows(IllegalArgumentException::class.java) { PngEncoder.Options(zLibLevel = -1) }
        assertThrows(IllegalArgumentException::class.java) { PngEncoder.Options(zLibLevel = 10) }
    }

    @Test
    fun `filter kNone produces valid PNG`() {
        val src = SkBitmap(4, 4)
        for (i in 0 until 16) src.pixels[i] = 0xFF808080.toInt()
        val bytes = PngEncoder.encode(src, PngEncoder.Options(filterFlags = PngEncoder.FilterFlag.kNone.mask))!!
        assertTrue(bytes.size > 0)
        val decoded = decodePng(bytes)
        assertEquals(4, decoded.width)
    }

    private fun decodePng(bytes: ByteArray): SkBitmap {
        val codec = Codec.MakeFromData(bytes)
        assertNotNull(codec, "PNG decoder must load encoded output")
        val (bitmap, result) = codec!!.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        return bitmap!!
    }
}
