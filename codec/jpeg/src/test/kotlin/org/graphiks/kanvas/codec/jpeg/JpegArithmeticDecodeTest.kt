package org.graphiks.kanvas.codec.jpeg

import org.graphiks.kanvas.codec.Codec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.skia.foundation.SkColorType

class JpegArithmeticDecodeTest {

    @Test
    fun `decodes static SOF9 arithmetic sequential fixture`() {
        val codec = JpegCodec.Decoder.make(resource("gradient-sequential-sof9.jpg"))
        assertNotNull(codec)

        val (bitmap, result) = codec!!.getImage()

        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertGrayscaleGradient(bitmap!!)
    }

    @Test
    fun `decodes static SOF10 arithmetic progressive fixture`() {
        val codec = JpegCodec.Decoder.make(resource("gradient-progressive-sof10.jpg"))
        assertNotNull(codec)

        val (bitmap, result) = codec!!.getImage()

        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertGrayscaleGradient(bitmap!!)
    }

    @Test
    fun `decodes interleaved color arithmetic DCT fixtures`() {
        for (name in listOf("color-sequential-sof9.jpg", "color-progressive-sof10.jpg")) {
            val codec = JpegCodec.Decoder.make(resource(name))
            assertNotNull(codec, name)

            val (bitmap, result) = codec!!.getImage()

            assertEquals(Codec.Result.kSuccess, result, name)
            assertNotNull(bitmap, name)
            assertEquals(0xFF6090C0.toInt(), bitmap!!.getPixel(0, 0), name)
            assertEquals(0xFF6090C0.toInt(), bitmap.getPixel(7, 7), name)
        }
    }

    @Test
    fun `preserves SOF9 arithmetic 12 bit samples before normalized output`() {
        val data = resource("gray-12bit-sequential-sof9.jpg")
        val document = JpegDocument.open(data).document!!
        val samples = decodeArithmeticSequentialDct(parseJpeg(data, document.metadata)!!)

        assertEquals(12, samples.precision)
        assertEquals(2_049, samples.planes.single()[0])
    }

    @Test
    fun `decodes arithmetic DRI scans containing actual restart markers`() {
        for (name in listOf("restart-sequential-sof9.jpg", "restart-progressive-sof10.jpg")) {
            val codec = JpegCodec.Decoder.make(resource(name))
            assertNotNull(codec, name)

            val (bitmap, result) = codec!!.getImage()

            assertEquals(Codec.Result.kSuccess, result, name)
            assertNotNull(bitmap, name)
            assertEquals(0xFF202020.toInt(), bitmap!!.getPixel(0, 0), name)
            assertEquals(0xFFC0C0C0.toInt(), bitmap.getPixel(15, 7), name)
        }
    }

    @Test
    fun `decodes arithmetic restart sequence through RST1`() {
        for (name in listOf("restart-three-mcu-sequential-sof9.jpg", "restart-three-mcu-progressive-sof10.jpg")) {
            val codec = JpegCodec.Decoder.make(resource(name))
            assertNotNull(codec, name)

            val (bitmap, result) = codec!!.getImage()

            assertEquals(Codec.Result.kSuccess, result, name)
            assertNotNull(bitmap, name)
            assertEquals(0xFF202020.toInt(), bitmap!!.getPixel(0, 0), name)
            assertEquals(0xFF808080.toInt(), bitmap.getPixel(8, 0), name)
            assertEquals(0xFFC0C0C0.toInt(), bitmap.getPixel(23, 7), name)
        }
    }

    @Test
    fun `decodes multiple arithmetic MCUs without a restart marker`() {
        for (name in listOf("two-mcu-sequential-sof9.jpg", "two-mcu-progressive-sof10.jpg")) {
            val codec = JpegCodec.Decoder.make(resource(name))
            assertNotNull(codec, name)

            val (bitmap, result) = codec!!.getImage()

            assertEquals(Codec.Result.kSuccess, result, name)
            assertNotNull(bitmap, name)
            assertEquals(0xFF202020.toInt(), bitmap!!.getPixel(0, 0), name)
            assertEquals(0xFFC0C0C0.toInt(), bitmap.getPixel(15, 7), name)
        }
    }

    @Test
    fun `snapshots DAC conditioning for every arithmetic scan`() {
        val data = resource("gradient-progressive-sof10.jpg")
        val changed = data.copyOf()
        val dcDacValue = changed.indexOfMarkerPayload(byteArrayOf(0xFF.toByte(), 0xCC.toByte(), 0, 4, 0, 0x10)) + 5
        changed[dcDacValue] = 0x32
        val acDacValue = changed.indexOfMarkerPayload(byteArrayOf(0xFF.toByte(), 0xCC.toByte(), 0, 4, 0x10, 5)) + 5
        changed[acDacValue] = 7
        val document = JpegDocument.open(changed).document!!
        val frame = parseJpeg(changed, document.metadata)!!

        assertEquals(JpegEntropyCoding.ARITHMETIC, frame.entropyCoding)
        assertEquals(2, frame.scans.first().arithmeticConditioning!!.dcLower[0])
        assertEquals(3, frame.scans.first().arithmeticConditioning!!.dcUpper[0])
        assertEquals(5, frame.scans.first().arithmeticConditioning!!.acK[0])
        assertEquals(7, frame.scans[1].arithmeticConditioning!!.acK[0])
    }

    @Test
    fun `rejects invalid DAC table identifiers`() {
        val malformed = resource("gradient-sequential-sof9.jpg").copyOf()
        val acDacIndex = malformed.indexOfMarkerPayload(byteArrayOf(0xFF.toByte(), 0xCC.toByte(), 0, 6, 0, 0x10, 0x10, 5)) + 6
        malformed[acDacIndex] = 0x20

        assertNull(JpegCodec.Decoder.make(malformed))
    }

    @Test
    fun `rejects DAC DC conditioning with lower bound above upper bound`() {
        val malformed = resource("gradient-sequential-sof9.jpg").copyOf()
        val dcDacValue = malformed.indexOfMarkerPayload(byteArrayOf(0xFF.toByte(), 0xCC.toByte(), 0, 6, 0, 0x10)) + 5
        malformed[dcDacValue] = 0x01

        assertNull(JpegCodec.Decoder.make(malformed))
    }

    @Test
    fun `reports a stable diagnostic when arithmetic restart sequence is wrong`() {
        val malformed = resource("restart-sequential-sof9.jpg").copyOf()
        val restart = malformed.indexOfMarkerPayload(byteArrayOf(0xFF.toByte(), 0xD0.toByte()))
        malformed[restart + 1] = 0xD1.toByte()
        val document = JpegDocument.open(malformed).document!!

        val result = document.decode(JpegDecodeRequest(SkColorType.kRGBA_8888, null))

        assertEquals("jpeg.arithmetic.restart.marker", result.diagnostic?.code)
        assertEquals(Codec.Result.kErrorInInput, result.diagnostic?.result)
    }

    @Test
    fun `reports a stable diagnostic when arithmetic second restart is repeated`() {
        val malformed = resource("restart-three-mcu-sequential-sof9.jpg").copyOf()
        val firstRestart = malformed.indexOfMarkerPayload(byteArrayOf(0xFF.toByte(), 0xD0.toByte()))
        val secondRestart = malformed.indexOfMarkerPayload(byteArrayOf(0xFF.toByte(), 0xD1.toByte()), firstRestart + 2)
        malformed[secondRestart + 1] = 0xD0.toByte()
        val document = JpegDocument.open(malformed).document!!

        val result = document.decode(JpegDecodeRequest(SkColorType.kRGBA_8888, null))

        assertEquals("jpeg.arithmetic.restart.marker", result.diagnostic?.code)
        assertEquals(Codec.Result.kErrorInInput, result.diagnostic?.result)
    }

    @Test
    fun `refuses SOF11 arithmetic lossless with a stable diagnostic`() {
        val sof11 = resource("gradient-sequential-sof9.jpg").copyOf()
        sof11[sof11.indexOf(0xC9.toByte())] = 0xCB.toByte()
        val sos = sof11.indexOfMarkerPayload(byteArrayOf(0xFF.toByte(), 0xDA.toByte(), 0, 8, 1, 1, 0, 0, 0x3F, 0))
        sof11[sos + 7] = 1
        sof11[sos + 8] = 0
        val document = JpegDocument.open(sof11).document!!

        val result = document.decode(JpegDecodeRequest(SkColorType.kRGBA_8888, null))

        assertEquals("jpeg.arithmetic.lossless.unsupported", result.diagnostic?.code)
        assertEquals(Codec.Result.kErrorInInput, result.diagnostic?.result)
    }

    private fun resource(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/jpeg-arithmetic/$name")) { name }.readBytes()

    private fun assertGrayscaleGradient(bitmap: org.skia.foundation.SkBitmap) {
        val expected = intArrayOf(
            0, 32, 96, 160,
            16, 64, 128, 193,
            48, 112, 176, 224,
            80, 144, 208, 255,
        )
        assertEquals(4, bitmap.width)
        assertEquals(4, bitmap.height)
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                val value = expected[y * 4 + x]
                assertEquals(
                    (0xFF shl 24) or (value shl 16) or (value shl 8) or value,
                    bitmap.getPixel(x, y),
                    "x=$x y=$y",
                )
            }
        }
    }

    private fun ByteArray.indexOfMarkerPayload(needle: ByteArray, startAt: Int = 0): Int {
        for (offset in startAt..size - needle.size) {
            if (needle.indices.all { index -> this[offset + index] == needle[index] }) return offset
        }
        error("marker payload missing")
    }
}
