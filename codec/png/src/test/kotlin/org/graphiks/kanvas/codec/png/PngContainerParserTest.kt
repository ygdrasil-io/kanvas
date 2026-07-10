package org.graphiks.kanvas.codec.png

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

class PngContainerParserTest {

    @Test
    fun `retains chunk records as source ranges without payload copies`() {
        val data = png(
            "IHDR" to ihdr(width = 2, height = 3),
            "vpAg" to byteArrayOf(0x11, 0x22, 0x33),
            "IDAT" to byteArrayOf(0x44, 0x55),
            "IEND" to ByteArray(0),
        )

        val container = success(data)

        assertEquals(2, container.header.width)
        assertEquals(3, container.header.height)
        assertEquals(2L, container.totalIdatBytes)
        assertEquals(listOf("IHDR", "vpAg", "IDAT", "IEND"), container.chunks.map(PngChunkRecord::type))

        val header = container.chunks[0]
        assertEquals(0, header.ordinal)
        assertEquals(PngByteRange(8L, 33L), header.rawRange)
        assertEquals(PngByteRange(16L, 29L), header.payloadRange)
        assertTrue(header.isCritical)
        assertFalse(header.isAncillary)
        assertFalse(header.isSafeToCopy)

        val unknown = container.chunks[1]
        assertEquals(1, unknown.ordinal)
        assertEquals(PngByteRange(33L, 48L), unknown.rawRange)
        assertEquals(PngByteRange(41L, 44L), unknown.payloadRange)
        assertFalse(unknown.isCritical)
        assertTrue(unknown.isAncillary)
        assertTrue(unknown.isSafeToCopy)
    }

    @Test
    fun `marks unknown ancillary chunks with uppercase fourth byte unsafe to copy`() {
        val container = success(
            png(
                "IHDR" to ihdr(),
                "vpAG" to byteArrayOf(1),
                "IDAT" to byteArrayOf(2),
                "IEND" to ByteArray(0),
            ),
        )

        val unknown = container.chunks.single { it.type == "vpAG" }
        assertTrue(unknown.isAncillary)
        assertFalse(unknown.isSafeToCopy)
    }

    @Test
    fun `public chunk record constructor and copy reject invalid type codes`() {
        val valid = PngChunkRecord(
            type = "vpAg",
            ordinal = 0,
            rawRange = PngByteRange(0L, 13L),
            payloadRange = PngByteRange(8L, 9L),
        )

        for (type in listOf("vp1g", "vpgg", "\u00c9pAg", "vpA\u0101")) {
            assertThrows(IllegalArgumentException::class.java) {
                PngChunkRecord(
                    type = type,
                    ordinal = 0,
                    rawRange = PngByteRange(0L, 13L),
                    payloadRange = PngByteRange(8L, 9L),
                )
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(type = "vpgg")
        }
    }

    @Test
    fun `public chunk record constructor and copy require exact framing ranges`() {
        val valid = PngChunkRecord(
            type = "vpAg",
            ordinal = 0,
            rawRange = PngByteRange(0L, 13L),
            payloadRange = PngByteRange(8L, 9L),
        )

        assertThrows(IllegalArgumentException::class.java) {
            PngChunkRecord(
                type = "vpAg",
                ordinal = 0,
                rawRange = PngByteRange(0L, 14L),
                payloadRange = PngByteRange(9L, 10L),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PngChunkRecord(
                type = "vpAg",
                ordinal = 0,
                rawRange = PngByteRange(0L, 14L),
                payloadRange = PngByteRange(8L, 9L),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(
                rawRange = PngByteRange(0L, 14L),
                payloadRange = PngByteRange(9L, 10L),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PngChunkRecord(
                type = "IEND",
                ordinal = 0,
                rawRange = PngByteRange(0L, 0L),
                payloadRange = PngByteRange(0L, 0L),
            )
        }
        assertThrows(IllegalArgumentException::class.java) { PngByteRange(-1L, 0L) }
        assertThrows(IllegalArgumentException::class.java) { PngByteRange(1L, 0L) }
    }

    @Test
    fun `public chunk record rejects overflow-shaped ranges at Long bounds`() {
        val overflowRaw = PngByteRange(Long.MAX_VALUE - 4L, Long.MAX_VALUE)
        val overflowPayload = PngByteRange(0L, Long.MAX_VALUE - 4L)

        assertThrows(IllegalArgumentException::class.java) {
            PngChunkRecord(
                type = "vpAg",
                ordinal = 0,
                rawRange = overflowRaw,
                payloadRange = overflowPayload,
            )
        }

        val valid = PngChunkRecord(
            type = "vpAg",
            ordinal = 0,
            rawRange = PngByteRange(0L, 13L),
            payloadRange = PngByteRange(8L, 9L),
        )
        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(rawRange = overflowRaw, payloadRange = overflowPayload)
        }
        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(
                rawRange = PngByteRange(Long.MAX_VALUE - 12L, Long.MAX_VALUE),
                payloadRange = PngByteRange(Long.MAX_VALUE - 4L, Long.MAX_VALUE),
            )
        }

        val upperBound = PngChunkRecord(
            type = "vpAg",
            ordinal = 0,
            rawRange = PngByteRange(Long.MAX_VALUE - 13L, Long.MAX_VALUE),
            payloadRange = PngByteRange(Long.MAX_VALUE - 5L, Long.MAX_VALUE - 4L),
        )
        assertEquals(1L, upperBound.payloadRange.size)
    }

    @Test
    fun `exposes an immutable chunk record list`() {
        val container = success(
            png(
                "IHDR" to ihdr(),
                "IDAT" to byteArrayOf(1),
                "IEND" to ByteArray(0),
            ),
        )

        @Suppress("UNCHECKED_CAST")
        val mutableView = container.chunks as MutableList<PngChunkRecord>
        assertThrows(UnsupportedOperationException::class.java) {
            mutableView.clear()
        }
        assertEquals(3, container.chunks.size)
    }

    @Test
    fun `rejects invalid signature and truncated chunk ranges`() {
        assertFailure(ByteArray(0), "png.signature.invalid")
        assertFailure(PNG_SIGNATURE + byteArrayOf(0, 0, 0, 1), "png.chunk.truncated")
        assertFailure(
            PNG_SIGNATURE + byteArrayOf(
                0x7F, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
                'I'.code.toByte(), 'D'.code.toByte(), 'A'.code.toByte(), 'T'.code.toByte(),
            ),
            "png.chunk.truncated",
        )
        assertFailure(
            PNG_SIGNATURE + byteArrayOf(
                0x80.toByte(), 0, 0, 0,
                'I'.code.toByte(), 'D'.code.toByte(), 'A'.code.toByte(), 'T'.code.toByte(),
                0, 0, 0, 0,
            ),
            "png.chunk.length.invalid",
        )
    }

    @Test
    fun `rejects non-letter and reserved lowercase chunk type bytes`() {
        assertFailure(
            png(
                "IHDR" to ihdr(),
                "vp1g" to ByteArray(0),
                "IDAT" to byteArrayOf(1),
                "IEND" to ByteArray(0),
            ),
            "png.chunk.type.invalid",
            "vp1g",
        )
        assertFailure(
            png(
                "IHDR" to ihdr(),
                "vpgg" to ByteArray(0),
                "IDAT" to byteArrayOf(1),
                "IEND" to ByteArray(0),
            ),
            "png.chunk.type.reserved",
            "vpgg",
        )
    }

    @Test
    fun `rejects CRC mismatch with chunk location`() {
        val data = png(
            "IHDR" to ihdr(),
            "IDAT" to byteArrayOf(1, 2, 3),
            "IEND" to ByteArray(0),
        )
        data[41] = (data[41].toInt() xor 1).toByte()

        val diagnostic = failure(data)

        assertEquals("png.chunk.crc.invalid", diagnostic.code)
        assertEquals("IDAT", diagnostic.chunkType)
        assertEquals(33L, diagnostic.offset)
        assertEquals(PngDiagnosticSeverity.ERROR, diagnostic.severity)
    }

    @Test
    fun `rejects unknown critical chunks`() {
        assertFailure(
            png(
                "IHDR" to ihdr(),
                "ABCD" to byteArrayOf(1),
                "IDAT" to byteArrayOf(2),
                "IEND" to ByteArray(0),
            ),
            "png.critical.unsupported",
            "ABCD",
        )
    }

    @Test
    fun `refuses every APNG chunk with typed diagnostic`() {
        val apngChunks = listOf(
            "acTL" to ByteArray(8),
            "fcTL" to ByteArray(26),
            "fdAT" to ByteArray(4),
        )

        for ((type, payload) in apngChunks) {
            assertFailure(
                png(
                    "IHDR" to ihdr(),
                    type to payload,
                    "IDAT" to byteArrayOf(1),
                    "IEND" to ByteArray(0),
                ),
                "png.apng.unsupported",
                type,
            )
        }
    }

    @Test
    fun `requires one first IHDR and one terminal IEND around IDAT`() {
        assertFailure(
            png(
                "vpAg" to ByteArray(0),
                "IHDR" to ihdr(),
                "IDAT" to byteArrayOf(1),
                "IEND" to ByteArray(0),
            ),
            "png.ihdr.order",
        )
        assertFailure(
            png(
                "IHDR" to ihdr(),
                "IHDR" to ihdr(),
                "IDAT" to byteArrayOf(1),
                "IEND" to ByteArray(0),
            ),
            "png.ihdr.duplicate",
        )
        assertFailure(
            png(
                "IHDR" to ihdr(),
                "IEND" to ByteArray(0),
            ),
            "png.idat.required",
        )
        assertFailure(
            png(
                "IHDR" to ihdr(),
                "IDAT" to byteArrayOf(1),
            ),
            "png.iend.required",
        )
        assertFailure(
            png(
                "IHDR" to ihdr(),
                "IDAT" to byteArrayOf(1),
                "IEND" to byteArrayOf(0),
            ),
            "png.iend.length",
        )
    }

    @Test
    fun `rejects PLTE after IDAT and non-contiguous IDAT`() {
        assertFailure(
            png(
                "IHDR" to ihdr(colorType = 3),
                "IDAT" to byteArrayOf(1),
                "PLTE" to byteArrayOf(0, 0, 0),
                "IEND" to ByteArray(0),
            ),
            "png.plte.order",
        )
        assertFailure(
            png(
                "IHDR" to ihdr(),
                "IDAT" to byteArrayOf(1),
                "vpAg" to ByteArray(0),
                "IDAT" to byteArrayOf(2),
                "IEND" to ByteArray(0),
            ),
            "png.idat.noncontiguous",
        )
    }

    @Test
    fun `rejects bytes after IEND`() {
        val data = png(
            "IHDR" to ihdr(),
            "IDAT" to byteArrayOf(1),
            "IEND" to ByteArray(0),
        ) + byteArrayOf(0)

        assertFailure(data, "png.iend.trailing_data", "IEND")
    }

    @Test
    fun `validates IHDR length encoding and configured dimension ranges`() {
        assertFailure(
            png(
                "IHDR" to ByteArray(12),
                "IDAT" to byteArrayOf(1),
                "IEND" to ByteArray(0),
            ),
            "png.ihdr.length",
        )
        assertFailure(
            png(
                "IHDR" to ihdr(width = 0),
                "IDAT" to byteArrayOf(1),
                "IEND" to ByteArray(0),
            ),
            "png.ihdr.dimensions.invalid",
        )
        assertFailure(
            png(
                "IHDR" to ihdr(bitDepth = 4, colorType = 2),
                "IDAT" to byteArrayOf(1),
                "IEND" to ByteArray(0),
            ),
            "png.ihdr.encoding.invalid",
        )
        assertFailure(
            png(
                "IHDR" to ihdr(width = 3, height = 2),
                "IDAT" to byteArrayOf(1),
                "IEND" to ByteArray(0),
            ),
            "png.dimension.limit",
            limits = PngContainerLimits.Default.copy(maxWidth = 2, maxHeight = 2),
        )
    }

    @Test
    fun `applies input chunk ancillary and IDAT limits`() {
        val idat = byteArrayOf(1, 2, 3)
        val data = png(
            "IHDR" to ihdr(),
            "vpAg" to byteArrayOf(4, 5, 6),
            "IDAT" to idat,
            "IEND" to ByteArray(0),
        )

        assertFailure(
            data,
            "png.input.limit",
            limits = PngContainerLimits.Default.copy(maxInputBytes = data.size.toLong() - 1L),
        )
        assertFailure(
            data,
            "png.chunk.count.limit",
            limits = PngContainerLimits.Default.copy(maxChunkCount = 3),
        )
        assertFailure(
            data,
            "png.ancillary.limit",
            "vpAg",
            limits = PngContainerLimits.Default.copy(maxAncillaryChunkBytes = 2L),
        )
        assertFailure(
            data,
            "png.idat.limit",
            "IDAT",
            limits = PngContainerLimits.Default.copy(maxTotalIdatBytes = 2L),
        )
    }

    @Test
    fun `refuses duplicate singleton static metadata chunks`() {
        val singletonTypes = listOf(
            "iCCP",
            "sRGB",
            "gAMA",
            "cHRM",
            "cICP",
            "mDCV",
            "cLLI",
            "eXIf",
            "pHYs",
            "tIME",
            "sBIT",
            "bKGD",
            "hIST",
        )

        for (type in singletonTypes) {
            val chunks = when (type) {
                "hIST" -> arrayOf(
                    "IHDR" to ihdr(colorType = 3),
                    "PLTE" to byteArrayOf(0, 0, 0),
                    type to staticMetadataPayload(type),
                    type to staticMetadataPayload(type),
                    "IDAT" to byteArrayOf(1),
                    "IEND" to ByteArray(0),
                )

                "bKGD" -> arrayOf(
                    "IHDR" to ihdr(colorType = 3),
                    "PLTE" to byteArrayOf(0, 0, 0),
                    type to staticMetadataPayload(type),
                    type to staticMetadataPayload(type),
                    "IDAT" to byteArrayOf(1),
                    "IEND" to ByteArray(0),
                )

                "mDCV" -> arrayOf(
                    "IHDR" to ihdr(),
                    "cICP" to byteArrayOf(1, 13, 0, 1),
                    type to staticMetadataPayload(type),
                    type to staticMetadataPayload(type),
                    "IDAT" to byteArrayOf(1),
                    "IEND" to ByteArray(0),
                )

                else -> arrayOf(
                    "IHDR" to ihdr(),
                    type to staticMetadataPayload(type),
                    type to staticMetadataPayload(type),
                    "IDAT" to byteArrayOf(1),
                    "IEND" to ByteArray(0),
                )
            }

            assertFailure(png(*chunks), "png.metadata.$type.duplicate", type)
        }
    }

    @Test
    fun `refuses static metadata order palette and HDR dependencies`() {
        for (type in listOf("iCCP", "sRGB", "gAMA", "cHRM", "cICP", "mDCV", "cLLI", "sBIT")) {
            val prefix = if (type == "mDCV") {
                arrayOf("IHDR" to ihdr(colorType = 3), "cICP" to byteArrayOf(1, 13, 0, 1))
            } else {
                arrayOf("IHDR" to ihdr(colorType = 3))
            }
            assertFailure(
                png(
                    *prefix,
                    "PLTE" to byteArrayOf(0, 0, 0),
                    type to staticMetadataPayload(type),
                    "IDAT" to byteArrayOf(1),
                    "IEND" to ByteArray(0),
                ),
                "png.metadata.$type.order",
                type,
            )
        }
        for (type in listOf("eXIf", "pHYs", "sPLT")) {
            assertFailure(
                png(
                    "IHDR" to ihdr(),
                    "IDAT" to byteArrayOf(1),
                    type to staticMetadataPayload(type),
                    "IEND" to ByteArray(0),
                ),
                "png.metadata.$type.order",
                type,
            )
        }
        assertFailure(
            png(
                "IHDR" to ihdr(colorType = 3),
                "hIST" to byteArrayOf(0, 0),
                "PLTE" to byteArrayOf(0, 0, 0),
                "IDAT" to byteArrayOf(1),
                "IEND" to ByteArray(0),
            ),
            "png.metadata.hIST.plte.required",
            "hIST",
        )
        assertFailure(
            png(
                "IHDR" to ihdr(),
                "mDCV" to ByteArray(24),
                "IDAT" to byteArrayOf(1),
                "IEND" to ByteArray(0),
            ),
            "png.metadata.mDCV.cicp.required",
            "mDCV",
        )
    }

    @Test
    fun `permits repeated text but refuses duplicate suggested palette names`() {
        val repeatedText = png(
            "IHDR" to ihdr(),
            "tEXt" to byteArrayOf('T'.code.toByte(), 0, '1'.code.toByte()),
            "tEXt" to byteArrayOf('T'.code.toByte(), 0, '2'.code.toByte()),
            "zTXt" to byteArrayOf('Z'.code.toByte(), 0, 0),
            "iTXt" to byteArrayOf('I'.code.toByte(), 0, 0, 0, 0, 0),
            "IDAT" to byteArrayOf(1),
            "IEND" to ByteArray(0),
        )
        assertEquals(7, success(repeatedText).chunks.size)

        assertFailure(
            png(
                "IHDR" to ihdr(),
                "sPLT" to suggestedPalette("display"),
                "sPLT" to suggestedPalette("display"),
                "IDAT" to byteArrayOf(1),
                "IEND" to ByteArray(0),
            ),
            "png.metadata.sPLT.name.duplicate",
            "sPLT",
        )
    }

    private fun success(
        data: ByteArray,
        limits: PngContainerLimits = PngContainerLimits.Default,
    ): PngContainer {
        val result = PngContainerParser.parse(data, limits)
        assertInstanceOf(PngContainerParseResult.Success::class.java, result)
        return (result as PngContainerParseResult.Success).container
    }

    private fun failure(
        data: ByteArray,
        limits: PngContainerLimits = PngContainerLimits.Default,
    ): PngDiagnostic {
        val result = PngContainerParser.parse(data, limits)
        assertInstanceOf(PngContainerParseResult.Failure::class.java, result)
        return (result as PngContainerParseResult.Failure).diagnostic
    }

    private fun assertFailure(
        data: ByteArray,
        code: String,
        chunkType: String? = null,
        limits: PngContainerLimits = PngContainerLimits.Default,
    ) {
        val diagnostic = failure(data, limits)
        assertEquals(code, diagnostic.code)
        if (chunkType != null) assertEquals(chunkType, diagnostic.chunkType)
    }

    private fun staticMetadataPayload(type: String): ByteArray = when (type) {
        "iCCP" -> byteArrayOf('p'.code.toByte(), 0, 0)
        "sRGB" -> byteArrayOf(0)
        "gAMA" -> ByteArray(4)
        "cHRM" -> ByteArray(32)
        "cICP" -> byteArrayOf(1, 13, 0, 1)
        "mDCV" -> ByteArray(24)
        "cLLI" -> ByteArray(8)
        "eXIf" -> byteArrayOf('I'.code.toByte(), 'I'.code.toByte(), 42, 0)
        "pHYs" -> ByteArray(9)
        "tIME" -> byteArrayOf(0x07, 0xEA.toByte(), 7, 10, 12, 34, 56)
        "sBIT" -> byteArrayOf(8, 8, 8)
        "bKGD" -> byteArrayOf(0)
        "hIST" -> byteArrayOf(0, 0)
        "sPLT" -> suggestedPalette("display")
        else -> error("Unexpected static metadata type $type")
    }

    private fun suggestedPalette(name: String): ByteArray =
        name.toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0, 8, 1, 2, 3, 4, 0, 1)

    private fun png(vararg chunks: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().apply {
            write(PNG_SIGNATURE)
            for ((type, payload) in chunks) writeChunk(type, payload)
        }.toByteArray()

    private fun ihdr(
        width: Int = 1,
        height: Int = 1,
        bitDepth: Int = 8,
        colorType: Int = 0,
    ): ByteArray = ByteArray(13).also { bytes ->
        writeI32BE(bytes, 0, width)
        writeI32BE(bytes, 4, height)
        bytes[8] = bitDepth.toByte()
        bytes[9] = colorType.toByte()
    }

    private fun ByteArrayOutputStream.writeChunk(type: String, payload: ByteArray) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        writeI32BE(payload.size)
        write(typeBytes)
        write(payload)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(payload)
        writeI32BE(crc.value.toInt())
    }

    private fun ByteArrayOutputStream.writeI32BE(value: Int) {
        write((value ushr 24) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 8) and 0xFF)
        write(value and 0xFF)
    }

    private fun writeI32BE(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 24).toByte()
        bytes[offset + 1] = (value ushr 16).toByte()
        bytes[offset + 2] = (value ushr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }

    private companion object {
        val PNG_SIGNATURE: ByteArray = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
    }
}
