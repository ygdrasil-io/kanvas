package org.graphiks.kanvas.codec.jpeg2000

import java.io.ByteArrayOutputStream
import org.graphiks.kanvas.codec.Codec
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkEncodedImageFormat

class Jpeg2000DocumentTest {

    @Test
    fun `raw J2K is owned by the JPEG 2000 provider and exposes a bounded document`() {
        val codestream = narrowLosslessCodestream()

        assertTrue(Jpeg2000Codec.Decoder.matches(codestream))
        val document = requireNotNull(Jpeg2000Document.open(codestream).document)
        val codec = requireNotNull(Codec.MakeFromData(codestream))

        assertEquals(Jpeg2000Container.J2K, document.container)
        assertEquals(1, document.frame.width)
        assertEquals(1, document.frame.height)
        assertEquals(SkEncodedImageFormat.kJPEG2000, codec.getEncodedFormat())
        assertEquals(Codec.Result.kUnimplemented, codec.getImage().second)
        assertEquals("jpeg2000.entropy.unimplemented", document.decode().diagnostic?.code)
    }

    @Test
    fun `JP2 retains top level boxes and routes its sole valid jp2c codestream`() {
        val codestream = narrowLosslessCodestream()
        val jp2 = jp2(codestream)

        val document = requireNotNull(Jpeg2000Document.open(jp2).document)
        val jp2c = document.boxes.single { it.type == "jp2c" }

        assertTrue(Jpeg2000Codec.Decoder.matches(jp2))
        assertEquals(Jpeg2000Container.JP2, document.container)
        assertEquals(listOf("jP  ", "ftyp", "jp2h", "jp2c"), document.boxes.map { it.type })
        assertArrayEquals(codestream, document.copyPayload(jp2c))
        assertNotNull(Codec.MakeFromData(jp2))
    }

    @Test
    fun `bounded open refuses an oversized J2K before parsing its codestream`() {
        val codestream = narrowLosslessCodestream()

        val opened = Jpeg2000Document.open(codestream, Jpeg2000Limits(maxEncodedBytes = 16))

        assertNull(opened.document)
        assertEquals("jpeg2000.limit.encoded-bytes", opened.diagnostic?.code)
        assertEquals(Codec.Result.kOutOfMemory, opened.diagnostic?.result)
    }

    @Test
    fun `JP2 refuses a truncated top level box with a stable diagnostic`() {
        val truncated = jp2(narrowLosslessCodestream()).copyOf(15)

        val opened = Jpeg2000Document.open(truncated)

        assertNull(opened.document)
        assertEquals("jpeg2000.jp2.box.truncated", opened.diagnostic?.code)
    }

    @Test
    fun `JP2 refuses a duplicate jp2c rather than choosing one codestream`() {
        val codestream = narrowLosslessCodestream()
        val duplicate = jp2(codestream) + ByteArrayOutputStream().also { it.writeBox("jp2c", codestream) }.toByteArray()

        val opened = Jpeg2000Document.open(duplicate)

        assertNull(opened.document)
        assertEquals("jpeg2000.jp2.jp2c.duplicate", opened.diagnostic?.code)
    }

    private fun narrowLosslessCodestream(): ByteArray = ByteArrayOutputStream().also { output ->
        output.writeMarker(0x4F) // SOC
        output.writeSegment(0x51, ByteArrayOutputStream().also { siz ->
            siz.writeU16(0) // Rsiz
            siz.writeU32(1); siz.writeU32(1) // Xsiz, Ysiz
            siz.writeU32(0); siz.writeU32(0) // XOsiz, YOsiz
            siz.writeU32(1); siz.writeU32(1) // XTsiz, YTsiz
            siz.writeU32(0); siz.writeU32(0) // XTOsiz, YTOsiz
            siz.writeU16(1) // Csiz
            siz.write(byteArrayOf(7, 1, 1)) // 8-bit unsigned, no subsampling
        }.toByteArray())
        output.writeSegment(0x52, byteArrayOf(
            0, // Scod: no precinct partitioning
            0, // LRCP
            0, 1, // one layer
            0, // no MCT
            0, // one resolution
            4, 4, // 64x64 code-block
            0, // no code-block style flags
            1, // reversible 5/3
        ))
        output.writeSegment(0x5C, byteArrayOf(0, 0)) // no quantization
        output.writeMarker(0x90)
        output.writeU16(10)
        output.writeU16(0) // Isot
        output.writeU32(14) // Psot: SOT (12 bytes) + SOD (2 bytes)
        output.write(0) // TPsot
        output.write(1) // TNsot
        output.writeMarker(0x93) // SOD, deliberately no EBCOT body
        output.writeMarker(0xD9) // EOC
    }.toByteArray()

    private fun jp2(codestream: ByteArray): ByteArray = ByteArrayOutputStream().also { output ->
        output.writeBox("jP  ", byteArrayOf(0x0D, 0x0A, 0x87.toByte(), 0x0A))
        output.writeBox("ftyp", byteArrayOf(
            'j'.code.toByte(), 'p'.code.toByte(), '2'.code.toByte(), ' '.code.toByte(),
            0, 0, 0, 0,
            'j'.code.toByte(), 'p'.code.toByte(), '2'.code.toByte(), ' '.code.toByte(),
        ))
        output.writeBox("jp2h", ByteArrayOutputStream().also { header ->
            header.writeBox("ihdr", ByteArrayOutputStream().also { ihdr ->
                ihdr.writeU32(1); ihdr.writeU32(1)
                ihdr.writeU16(1)
                ihdr.write(byteArrayOf(7, 7, 0, 0))
            }.toByteArray())
        }.toByteArray())
        output.writeBox("jp2c", codestream)
    }.toByteArray()

    private fun ByteArrayOutputStream.writeMarker(marker: Int) {
        write(0xFF); write(marker)
    }

    private fun ByteArrayOutputStream.writeSegment(marker: Int, payload: ByteArray) {
        writeMarker(marker)
        writeU16(payload.size + 2)
        write(payload)
    }

    private fun ByteArrayOutputStream.writeBox(type: String, payload: ByteArray) {
        require(type.length == 4)
        writeU32(payload.size + 8)
        write(type.toByteArray(Charsets.ISO_8859_1))
        write(payload)
    }

    private fun ByteArrayOutputStream.writeU16(value: Int) {
        write(value ushr 8); write(value and 0xFF)
    }

    private fun ByteArrayOutputStream.writeU32(value: Int) {
        write(value ushr 24); write(value ushr 16); write(value ushr 8); write(value and 0xFF)
    }
}
