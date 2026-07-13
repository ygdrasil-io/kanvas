package org.graphiks.kanvas.codec.jpeg

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.graphiks.kanvas.codec.Codec
import org.skia.foundation.SkEncodedOrigin
import org.skia.foundation.SkICC
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import java.io.ByteArrayOutputStream

class JpegMetadataTest {

    @Test
    fun `reassembles reordered ICC APP2 segments`() {
        val icc = SkICC.WriteToICC(SkNamedTransferFn.kSRGB, SkNamedGamut.kDisplayP3)
        val splitAt = icc.size / 2
        val document = documentWith(
            iccSegment(index = 2, count = 2, payload = icc.copyOfRange(splitAt, icc.size)),
            iccSegment(index = 1, count = 2, payload = icc.copyOfRange(0, splitAt)),
        )

        assertEquals(emptyList<JpegDiagnostic>(), document.metadataDiagnostics)
        val profile = document.metadata.iccProfile
        assertNotNull(profile)
        assertEquals(icc.size, profile!!.size)
        assertEquals(SkEncodedOrigin.kTopLeft, document.metadata.origin)
    }

    @Test
    fun `reads EXIF orientation with either TIFF byte order`() {
        val cases = listOf(
            false to SkEncodedOrigin.kRightTop,
            true to SkEncodedOrigin.kLeftBottom,
        )

        for ((littleEndian, expected) in cases) {
            val document = documentWith(exifOrientationSegment(if (littleEndian) 8 else 6, littleEndian))

            assertEquals(expected, document.metadata.origin, "littleEndian=$littleEndian")
            assertEquals(emptyList<JpegDiagnostic>(), document.metadataDiagnostics, "littleEndian=$littleEndian")
        }
    }

    @Test
    fun `accepts valid EXIF without an orientation tag`() {
        val document = documentWith(exifWithoutOrientationSegment())

        assertEquals(SkEncodedOrigin.kTopLeft, document.metadata.origin)
        assertEquals(emptyList<JpegDiagnostic>(), document.metadataDiagnostics)
    }

    @Test
    fun `rejects EXIF IFD truncated before next offset`() {
        val document = documentWith(exifWithoutOrientationSegment(includeNextIfdOffset = false))

        assertEquals(SkEncodedOrigin.kTopLeft, document.metadata.origin)
        assertEquals(listOf("jpeg.metadata.exif.invalid"), document.metadataDiagnostics.map(JpegDiagnostic::code))
        assertEquals(listOf(Codec.Result.kErrorInInput), document.metadataDiagnostics.map(JpegDiagnostic::result))
    }

    @Test
    fun `retains XMP bytes after the APP1 identifier`() {
        val xmp = "<x:xmpmeta>kanvas</x:xmpmeta>".encodeToByteArray()
        val document = documentWith(xmpSegment(xmp))

        assertArrayEquals(xmp, document.metadata.xmp)
        assertEquals(emptyList<JpegDiagnostic>(), document.metadataDiagnostics)
    }

    @Test
    fun `reads JFIF and Adobe semantic metadata`() {
        val document = documentWith(
            jfifSegment(major = 1, minor = 2, densityUnit = 1, xDensity = 72, yDensity = 96),
            adobeSegment(transform = 2),
        )

        assertEquals(
            JpegJfifMetadata(majorVersion = 1, minorVersion = 2, densityUnit = 1, xDensity = 72, yDensity = 96),
            document.metadata.jfif,
        )
        assertEquals(2, document.metadata.adobeTransform)
        assertEquals(emptyList<JpegDiagnostic>(), document.metadataDiagnostics)
    }

    @Test
    fun `reports stable diagnostics for malformed known metadata`() {
        val document = documentWith(
            appSegment(0xE1, EXIF_SIGNATURE + byteArrayOf(0x49, 0x49)),
            iccSegment(index = 1, count = 2, payload = byteArrayOf(1, 2, 3)),
            appSegment(0xEE, ADOBE_SIGNATURE + byteArrayOf(0)),
        )

        assertEquals(SkEncodedOrigin.kTopLeft, document.metadata.origin)
        assertNull(document.metadata.iccProfile)
        assertEquals(
            listOf("jpeg.metadata.exif.invalid", "jpeg.metadata.adobe.invalid", "jpeg.metadata.icc.incomplete"),
            document.metadataDiagnostics.map(JpegDiagnostic::code),
        )
        assertEquals(listOf(2L, 35L, 14L), document.metadataDiagnostics.map(JpegDiagnostic::offset))
        assertEquals(
            listOf(Codec.Result.kErrorInInput, Codec.Result.kErrorInInput, Codec.Result.kErrorInInput),
            document.metadataDiagnostics.map(JpegDiagnostic::result),
        )
    }

    @Test
    fun `exposes the declared metadata preservation policies`() {
        assertEquals(
            listOf(
                JpegMetadataPolicy.Preserve,
                JpegMetadataPolicy.StripAll,
                JpegMetadataPolicy.ReplaceKnown,
            ),
            JpegMetadataPolicy.entries,
        )
    }

    private fun documentWith(vararg segments: ByteArray): JpegDocument {
        val bytes = ByteArrayOutputStream().apply {
            writeMarker(0xD8)
            segments.forEach(::write)
            writeMarker(0xD9)
        }.toByteArray()
        return requireNotNull(JpegDocument.open(bytes).document)
    }

    private fun iccSegment(index: Int, count: Int, payload: ByteArray): ByteArray = appSegment(0xE2) {
        write(ICC_SIGNATURE)
        write(index)
        write(count)
        write(payload)
    }

    private fun xmpSegment(xmp: ByteArray): ByteArray = appSegment(0xE1) {
        write(XMP_SIGNATURE)
        write(xmp)
    }

    private fun exifOrientationSegment(orientation: Int, littleEndian: Boolean): ByteArray = appSegment(0xE1) {
        write(EXIF_SIGNATURE)
        if (littleEndian) {
            write('I'.code)
            write('I'.code)
            writeU16LE(0x002A)
            writeU32LE(8)
            writeU16LE(1)
            writeU16LE(0x0112)
            writeU16LE(3)
            writeU32LE(1)
            writeU16LE(orientation)
            writeU16LE(0)
            writeU32LE(0)
        } else {
            write('M'.code)
            write('M'.code)
            writeU16BE(0x002A)
            writeU32BE(8)
            writeU16BE(1)
            writeU16BE(0x0112)
            writeU16BE(3)
            writeU32BE(1)
            writeU16BE(orientation)
            writeU16BE(0)
            writeU32BE(0)
        }
    }

    private fun exifWithoutOrientationSegment(includeNextIfdOffset: Boolean = true): ByteArray = appSegment(0xE1) {
        write(EXIF_SIGNATURE)
        write('I'.code)
        write('I'.code)
        writeU16LE(0x002A)
        writeU32LE(8)
        writeU16LE(0)
        if (includeNextIfdOffset) writeU32LE(0)
    }

    private fun jfifSegment(
        major: Int,
        minor: Int,
        densityUnit: Int,
        xDensity: Int,
        yDensity: Int,
    ): ByteArray = appSegment(0xE0) {
        write(JFIF_SIGNATURE)
        write(major)
        write(minor)
        write(densityUnit)
        writeU16BE(xDensity)
        writeU16BE(yDensity)
        write(0)
        write(0)
    }

    private fun adobeSegment(transform: Int): ByteArray = appSegment(0xEE) {
        write(ADOBE_SIGNATURE)
        writeU16BE(100)
        writeU16BE(0)
        writeU16BE(0)
        write(transform)
    }

    private fun appSegment(marker: Int, writePayload: ByteArrayOutputStream.() -> Unit): ByteArray {
        val payload = ByteArrayOutputStream().apply(writePayload).toByteArray()
        return ByteArrayOutputStream().apply {
            writeMarker(marker)
            writeU16BE(payload.size + 2)
            write(payload)
        }.toByteArray()
    }

    private fun appSegment(marker: Int, payload: ByteArray): ByteArray = appSegment(marker) { write(payload) }

    private fun ByteArrayOutputStream.writeMarker(marker: Int) {
        write(0xFF)
        write(marker)
    }

    private fun ByteArrayOutputStream.writeU16BE(value: Int) {
        write((value ushr 8) and 0xFF)
        write(value and 0xFF)
    }

    private fun ByteArrayOutputStream.writeU16LE(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeU32BE(value: Int) {
        write((value ushr 24) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 8) and 0xFF)
        write(value and 0xFF)
    }

    private fun ByteArrayOutputStream.writeU32LE(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 24) and 0xFF)
    }

    private companion object {
        val ICC_SIGNATURE = byteArrayOf(
            0x49, 0x43, 0x43, 0x5F, 0x50, 0x52, 0x4F, 0x46, 0x49, 0x4C, 0x45, 0x00,
        )
        val EXIF_SIGNATURE = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0x00, 0x00)
        val XMP_SIGNATURE = "http://ns.adobe.com/xap/1.0/\u0000".encodeToByteArray()
        val JFIF_SIGNATURE = byteArrayOf(0x4A, 0x46, 0x49, 0x46, 0x00)
        val ADOBE_SIGNATURE = byteArrayOf(0x41, 0x64, 0x6F, 0x62, 0x65)
    }
}
