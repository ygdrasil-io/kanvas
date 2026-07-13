package org.graphiks.kanvas.codec.jpegxl

import java.io.ByteArrayOutputStream
import java.util.Base64
import org.graphiks.kanvas.codec.Codec
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkEncodedImageFormat

class JpegXlDocumentTest {

    @Test
    fun `limits reserve headroom for every SizeHeader bit position`() {
        assertThrows(IllegalArgumentException::class.java) {
            JpegXlLimits(maxEncodedBytes = 268_435_456L)
        }
    }

    @Test
    fun `raw JPEG XL codestream is owned after its bounded size header`() {
        val codestream = rawCodestream(width = 3, height = 2)

        assertTrue(JpegXlCodec.Decoder.matches(codestream))
        val document = requireNotNull(JpegXlDocument.open(codestream).document)
        val codec = requireNotNull(Codec.MakeFromData(codestream))

        assertEquals(JpegXlContainer.CODESTREAM, document.container)
        assertEquals(JpegXlFrameInfo(width = 3, height = 2), document.frame)
        assertEquals(SkEncodedImageFormat.kJPEGXL, codec.getEncodedFormat())
        assertEquals(Codec.Result.kUnimplemented, codec.getImage().second)
        assertEquals("jpegxl.frame.entropy.unimplemented", document.decode().diagnostic?.code)
    }

    @Test
    fun `JXL container retains boxes and routes its jxlc codestream`() {
        val codestream = rawCodestream(width = 3, height = 2)
        val container = jxlContainer(codestream)

        val document = requireNotNull(JpegXlDocument.open(container).document)
        val jxlc = document.boxes.single { it.type == "jxlc" }

        assertTrue(JpegXlCodec.Decoder.matches(container))
        assertEquals(JpegXlContainer.CONTAINER, document.container)
        assertEquals(listOf("JXL ", "ftyp", "jxlc"), document.boxes.map { it.type })
        assertArrayEquals(codestream, document.copyPayload(jxlc))
        assertNotNull(Codec.MakeFromData(container))
        assertEquals("jpegxl.frame.entropy.unimplemented", document.decode().diagnostic?.code)
    }

    @Test
    fun `JXL container refuses a second signature box instead of choosing a codestream`() {
        val duplicateSignature = ByteArrayOutputStream().also { output ->
            output.writeBox("JXL ", byteArrayOf(0x0D, 0x0A, 0x87.toByte(), 0x0A))
            output.writeBox("ftyp", byteArrayOf(
                'j'.code.toByte(), 'x'.code.toByte(), 'l'.code.toByte(), ' '.code.toByte(),
                0, 0, 0, 0,
                'j'.code.toByte(), 'x'.code.toByte(), 'l'.code.toByte(), ' '.code.toByte(),
            ))
            output.writeBox("JXL ", byteArrayOf(0x0D, 0x0A, 0x87.toByte(), 0x0A))
            output.writeBox("jxlc", rawCodestream(width = 3, height = 2))
        }.toByteArray()

        val opened = JpegXlDocument.open(duplicateSignature)

        assertNull(opened.document)
        assertEquals("jpegxl.container.signature.duplicate", opened.diagnostic?.code)
        assertNull(Codec.MakeFromData(duplicateSignature))
    }

    @Test
    fun `JXL container refuses a file type box outside its required second position`() {
        val duplicateFileType = ByteArrayOutputStream().also { output ->
            output.writeBox("JXL ", byteArrayOf(0x0D, 0x0A, 0x87.toByte(), 0x0A))
            output.write(fileTypeBox())
            output.write(fileTypeBox())
            output.writeBox("jxlc", rawCodestream(width = 3, height = 2))
        }.toByteArray()

        val opened = JpegXlDocument.open(duplicateFileType)

        assertNull(opened.document)
        assertEquals("jpegxl.container.ftyp.duplicate", opened.diagnostic?.code)
        assertNull(Codec.MakeFromData(duplicateFileType))
    }

    @Test
    fun `JXL container refuses a file type payload with a partial compatible brand`() {
        val malformedFileType = ByteArrayOutputStream().also { output ->
            output.writeBox("JXL ", byteArrayOf(0x0D, 0x0A, 0x87.toByte(), 0x0A))
            output.writeBox("ftyp", fileTypePayload() + byteArrayOf(0))
            output.writeBox("jxlc", rawCodestream(width = 3, height = 2))
        }.toByteArray()

        val opened = JpegXlDocument.open(malformedFileType)

        assertNull(opened.document)
        assertEquals("jpegxl.container.ftyp.invalid", opened.diagnostic?.code)
    }

    @Test
    fun `opens libjxl extended-size JXL container fixture through the header boundary`() {
        val fixture = Base64.getDecoder().decode(
            "AAAADEpYTCANCocKAAAAFGZ0eXBqeGwgAAAAAGp4bCAAAAABanhsYwAAAAAAAABI/wpBBgASiAIAtABVDwAAqFAZZdzg5VzPlx86LKZtXGdoq20LSxJFxrFJOoFDkkjSYCCZwXBaJQk=",
        )

        val document = requireNotNull(JpegXlDocument.open(fixture).document)

        assertEquals(JpegXlContainer.CONTAINER, document.container)
        assertEquals(JpegXlFrameInfo(width = 8, height = 8), document.frame)
        assertEquals(listOf("JXL ", "ftyp", "jxlc"), document.boxes.map { it.type })
        assertEquals(Codec.Result.kUnimplemented, requireNotNull(Codec.MakeFromData(fixture)).getImage().second)
    }

    private fun rawCodestream(width: Int, height: Int): ByteArray = ByteArrayOutputStream().also { output ->
        output.write(0xFF)
        output.write(0x0A)
        BitWriter(output).apply {
            bit(0) // SizeHeader.small = false.
            u32Offset9(height)
            bits(0, 3) // explicit x size.
            u32Offset9(width)
            finish()
        }
    }.toByteArray()

    private fun jxlContainer(codestream: ByteArray): ByteArray = ByteArrayOutputStream().also { output ->
        output.writeBox("JXL ", byteArrayOf(0x0D, 0x0A, 0x87.toByte(), 0x0A))
        output.write(fileTypeBox())
        output.writeBox("jxlc", codestream)
    }.toByteArray()

    private fun fileTypeBox(): ByteArray = ByteArrayOutputStream().also { output ->
        output.writeBox("ftyp", fileTypePayload())
    }.toByteArray()

    private fun fileTypePayload(): ByteArray = byteArrayOf(
            'j'.code.toByte(), 'x'.code.toByte(), 'l'.code.toByte(), ' '.code.toByte(),
            0, 0, 0, 0,
            'j'.code.toByte(), 'x'.code.toByte(), 'l'.code.toByte(), ' '.code.toByte(),
        )

    private fun ByteArrayOutputStream.writeBox(type: String, payload: ByteArray) {
        require(type.length == 4)
        writeU32(payload.size + 8)
        write(type.toByteArray(Charsets.ISO_8859_1))
        write(payload)
    }

    private fun ByteArrayOutputStream.writeU32(value: Int) {
        write(value ushr 24)
        write(value ushr 16)
        write(value ushr 8)
        write(value)
    }

    private class BitWriter(private val output: ByteArrayOutputStream) {
        private var pending: Int = 0
        private var bits: Int = 0

        fun bit(value: Int) = bits(value, 1)

        fun bits(value: Int, count: Int) {
            require(value >= 0 && value ushr count == 0)
            pending = pending or (value shl bits)
            bits += count
            while (bits >= 8) {
                output.write(pending and 0xFF)
                pending = pending ushr 8
                bits -= 8
            }
        }

        fun u32Offset9(value: Int) {
            require(value in 1..512)
            bits(0, 2) // U32 selector 0: nine stored bits plus offset 1.
            bits(value - 1, 9)
        }

        fun finish() {
            if (bits != 0) output.write(pending)
        }
    }
}
