package org.graphiks.kanvas.codec.jpeg2000

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
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
    fun `pinned OpenJPEG J2K fixture has its documented SHA-256`() {
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(Jpeg2000TestFixtures.openJpegLossless5x3())
            .joinToString(separator = "") { byte ->
                byte.toInt().and(0xff).toString(16).padStart(2, '0')
            }

        assertEquals(
            "078395a38f631ae8eb94476d01fe54a1d002a706ca7ad86849b15917cae937b4",
            actual,
        )
    }

    @Test
    fun `OpenJPEG reversible lossless J2K fixture is owned structurally before entropy decode`() {
        val codestream = Jpeg2000TestFixtures.openJpegLossless5x3()

        val document = requireNotNull(Jpeg2000Document.open(codestream).document)

        assertEquals(Jpeg2000Container.J2K, document.container)
        assertEquals(Jpeg2000FrameInfo(5, 3, 1, 8), document.frame)
        assertEquals("jpeg2000.entropy.unimplemented", document.decode().diagnostic?.code)
        assertEquals(Codec.Result.kUnimplemented, requireNotNull(Codec.MakeFromData(codestream)).getImage().second)
    }

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

    @Test
    fun `raw J2K requires SIZ immediately after SOC`() {
        val canonical = narrowLosslessCodestream()
        val forbiddenFirstMarkers = listOf(
            0x52 to narrowCodPayload(),
            0x5C to byteArrayOf(0, 0),
            0x64 to byteArrayOf(),
        )

        forbiddenFirstMarkers.forEach { (marker, payload) ->
            val reordered = ByteArrayOutputStream().also { output ->
                output.writeMarker(0x4F)
                output.writeSegment(marker, payload)
                output.write(canonical, 2, canonical.size - 2)
            }.toByteArray()

            val opened = Jpeg2000Document.open(reordered)

            assertNull(opened.document)
            assertEquals("jpeg2000.siz.order", opened.diagnostic?.code)
            assertEquals(Codec.Result.kErrorInInput, opened.diagnostic?.result)
        }
    }

    @Test
    fun `JP2 refuses duplicate signature rather than retaining an ambiguous container`() {
        val duplicate = jp2(narrowLosslessCodestream()) + boxed("jP  ", jp2SignaturePayload())

        val opened = Jpeg2000Document.open(duplicate)

        assertNull(opened.document)
        assertEquals("jpeg2000.jp2.signature.duplicate", opened.diagnostic?.code)
        assertEquals(Codec.Result.kErrorInInput, opened.diagnostic?.result)
    }

    @Test
    fun `JP2 refuses duplicate ftyp rather than accepting ambiguous brands`() {
        val duplicate = jp2(narrowLosslessCodestream()) + boxed("ftyp", fileTypePayload())

        val opened = Jpeg2000Document.open(duplicate)

        assertNull(opened.document)
        assertEquals("jpeg2000.jp2.ftyp.duplicate", opened.diagnostic?.code)
        assertEquals(Codec.Result.kErrorInInput, opened.diagnostic?.result)
    }

    @Test
    fun `JP2 requires ftyp directly after its signature`() {
        val codestream = narrowLosslessCodestream()
        val misplaced = ByteArrayOutputStream().also { output ->
            output.writeBox("jP  ", jp2SignaturePayload())
            output.writeBox("free", byteArrayOf())
            output.writeBox("ftyp", fileTypePayload())
            output.writeBox("jp2h", imageHeaderPayload())
            output.writeBox("jp2c", codestream)
        }.toByteArray()

        val opened = Jpeg2000Document.open(misplaced)

        assertNull(opened.document)
        assertEquals("jpeg2000.jp2.ftyp.order", opened.diagnostic?.code)
        assertEquals(Codec.Result.kErrorInInput, opened.diagnostic?.result)
    }

    @Test
    fun `JP2 requires its signature at byte zero`() {
        val codestream = narrowLosslessCodestream()
        val misplaced = ByteArrayOutputStream().also { output ->
            output.writeBox("free", byteArrayOf())
            output.writeBox("jP  ", jp2SignaturePayload())
            output.writeBox("ftyp", fileTypePayload())
            output.writeBox("jp2h", imageHeaderPayload())
            output.writeBox("jp2c", codestream)
        }.toByteArray()

        val opened = Jpeg2000Document.open(misplaced)

        assertNull(opened.document)
        assertEquals("jpeg2000.signature.missing", opened.diagnostic?.code)
        assertEquals(Codec.Result.kErrorInInput, opened.diagnostic?.result)
    }

    @Test
    fun `JP2 requires jp2h before its sole jp2c codestream`() {
        val codestream = narrowLosslessCodestream()
        val misplaced = ByteArrayOutputStream().also { output ->
            output.writeBox("jP  ", jp2SignaturePayload())
            output.writeBox("ftyp", fileTypePayload())
            output.writeBox("jp2c", codestream)
            output.writeBox("jp2h", imageHeaderPayload())
        }.toByteArray()

        val opened = Jpeg2000Document.open(misplaced)

        assertNull(opened.document)
        assertEquals("jpeg2000.jp2.jp2c.order", opened.diagnostic?.code)
        assertEquals(Codec.Result.kErrorInInput, opened.diagnostic?.result)
    }

    @Test
    fun `JP2 requires jp2h directly after ftyp`() {
        val codestream = narrowLosslessCodestream()
        val misplaced = ByteArrayOutputStream().also { output ->
            output.writeBox("jP  ", jp2SignaturePayload())
            output.writeBox("ftyp", fileTypePayload())
            output.writeBox("free", byteArrayOf())
            output.writeBox("jp2h", imageHeaderPayload())
            output.writeBox("jp2c", codestream)
        }.toByteArray()

        val opened = Jpeg2000Document.open(misplaced)

        assertNull(opened.document)
        assertEquals("jpeg2000.jp2.jp2h.order", opened.diagnostic?.code)
        assertEquals(Codec.Result.kErrorInInput, opened.diagnostic?.result)
    }

    @Test
    fun `JP2 requires jp2c directly after jp2h`() {
        val codestream = narrowLosslessCodestream()
        val misplaced = ByteArrayOutputStream().also { output ->
            output.writeBox("jP  ", jp2SignaturePayload())
            output.writeBox("ftyp", fileTypePayload())
            output.writeBox("jp2h", imageHeaderPayload())
            output.writeBox("free", byteArrayOf())
            output.writeBox("jp2c", codestream)
        }.toByteArray()

        val opened = Jpeg2000Document.open(misplaced)

        assertNull(opened.document)
        assertEquals("jpeg2000.jp2.jp2c.order", opened.diagnostic?.code)
        assertEquals(Codec.Result.kErrorInInput, opened.diagnostic?.result)
    }

    @Test
    fun `JP2 refuses duplicate jp2h rather than choosing a header`() {
        val duplicate = jp2(narrowLosslessCodestream()) + boxed("jp2h", imageHeaderPayload())

        val opened = Jpeg2000Document.open(duplicate)

        assertNull(opened.document)
        assertEquals("jpeg2000.jp2.jp2h.duplicate", opened.diagnostic?.code)
        assertEquals(Codec.Result.kErrorInInput, opened.diagnostic?.result)
    }

    @Test
    fun `JP2 refuses duplicate ihdr inside jp2h`() {
        val duplicateHeader = imageHeaderBox() + imageHeaderBox()

        val opened = Jpeg2000Document.open(jp2(narrowLosslessCodestream(), duplicateHeader))

        assertNull(opened.document)
        assertEquals("jpeg2000.jp2.ihdr.duplicate", opened.diagnostic?.code)
        assertEquals(Codec.Result.kErrorInInput, opened.diagnostic?.result)
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
        output.writeSegment(0x52, narrowCodPayload())
        output.writeSegment(0x5C, byteArrayOf(0x40, 0x40)) // reversible 5/3, no quantization
        output.writeMarker(0x90)
        output.writeU16(10)
        output.writeU16(0) // Isot
        output.writeU32(14) // Psot: SOT (12 bytes) + SOD (2 bytes)
        output.write(0) // TPsot
        output.write(1) // TNsot
        output.writeMarker(0x93) // SOD, deliberately no EBCOT body
        output.writeMarker(0xD9) // EOC
    }.toByteArray()

    private fun jp2(codestream: ByteArray, headerPayload: ByteArray = imageHeaderPayload()): ByteArray = ByteArrayOutputStream().also { output ->
        output.writeBox("jP  ", jp2SignaturePayload())
        output.writeBox("ftyp", fileTypePayload())
        output.writeBox("jp2h", headerPayload)
        output.writeBox("jp2c", codestream)
    }.toByteArray()

    private fun jp2SignaturePayload(): ByteArray = byteArrayOf(0x0D, 0x0A, 0x87.toByte(), 0x0A)

    private fun fileTypePayload(): ByteArray = byteArrayOf(
            'j'.code.toByte(), 'p'.code.toByte(), '2'.code.toByte(), ' '.code.toByte(),
            0, 0, 0, 0,
            'j'.code.toByte(), 'p'.code.toByte(), '2'.code.toByte(), ' '.code.toByte(),
    )

    private fun imageHeaderPayload(): ByteArray = imageHeaderBox()

    private fun imageHeaderBox(): ByteArray = boxed("ihdr", ByteArrayOutputStream().also { ihdr ->
        ihdr.writeU32(1); ihdr.writeU32(1)
        ihdr.writeU16(1)
        ihdr.write(byteArrayOf(7, 7, 0, 0))
    }.toByteArray())

    private fun narrowCodPayload(): ByteArray = byteArrayOf(
        0, // Scod: no precinct partitioning
        0, // LRCP
        0, 1, // one layer
        0, // no MCT
        0, // one resolution
        4, 4, // 64x64 code-block
        0, // no code-block style flags
        1, // reversible 5/3
    )

    private fun boxed(type: String, payload: ByteArray): ByteArray = ByteArrayOutputStream().also {
        it.writeBox(type, payload)
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
