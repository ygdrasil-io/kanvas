package org.graphiks.kanvas.codec.png

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PngDocumentTest {

    @Test
    fun `metadata replacement preserves unrelated raw chunks and IDAT segmentation`() {
        val source = png(
            "IHDR" to ihdr(),
            "vpAg" to byteArrayOf(0x10, 0x20),
            "tEXt" to byteArrayOf(0x30),
            "IDAT" to byteArrayOf(0x40),
            "IDAT" to byteArrayOf(0x50, 0x60),
            "IEND" to ByteArray(0),
        )
        val sourceRaw = rawChunks(source)

        val edited = open(source).withAncillaryChunk("tEXt", byteArrayOf(0x70, 0x71))
        val saved = edited.save()
        val savedRaw = rawChunks(saved.bytes)

        assertEquals(PngWriteImpact.ANCILLARY, edited.writePlan.impact)
        assertEquals(PngDocumentSaveStatus.SAVED, saved.status)
        assertNull(saved.diagnostic)
        assertArrayEquals(sourceRaw.getValue("vpAg").single(), savedRaw.getValue("vpAg").single())
        assertRawChunkListsEqual(sourceRaw.getValue("IDAT"), savedRaw.getValue("IDAT"))
        assertEquals(listOf(0x70.toByte(), 0x71.toByte()), chunkPayloads(saved.bytes, "tEXt").single().toList())
        assertEquals(
            PngSaveEntryStatus.REPLACED,
            saved.report.entries.single { it.chunkType == "tEXt" }.status,
        )
        assertEquals(
            "png.ancillary.replaced",
            saved.report.entries.single { it.chunkType == "tEXt" }.reasonCode,
        )
        assertEquals(
            "png.ancillary.preserved.metadata-only",
            saved.report.entries.single { it.chunkType == "vpAg" }.reasonCode,
        )
        assertFalse(saved.report.isEmpty)
    }

    @Test
    fun `removes every matching ancillary chunk without rewriting retained chunks`() {
        val source = png(
            "IHDR" to ihdr(),
            "tEXt" to byteArrayOf(0x10),
            "tEXt" to byteArrayOf(0x20),
            "IDAT" to byteArrayOf(0x30),
            "IEND" to ByteArray(0),
        )
        val sourceIdat = rawChunks(source).getValue("IDAT")

        val saved = open(source).withoutChunks("tEXt").save()

        assertTrue(chunkPayloads(saved.bytes, "tEXt").isEmpty())
        assertRawChunkListsEqual(sourceIdat, rawChunks(saved.bytes).getValue("IDAT"))
        assertEquals(2, saved.report.entries.count { it.reasonCode == "png.ancillary.removed" })
        assertEquals(setOf(1, 2), saved.report.entries.filter { it.chunkType == "tEXt" }.mapNotNull { it.ordinal }.toSet())
        assertTrue(saved.report.entries.filter { it.chunkType == "tEXt" }.all {
            it.status == PngSaveEntryStatus.DROPPED
        })
    }

    @Test
    fun `metadata edits snapshot payloads and leave the source document pristine`() {
        val source = png(
            "IHDR" to ihdr(),
            "IDAT" to byteArrayOf(0x10),
            "IEND" to ByteArray(0),
        )
        val payload = byteArrayOf(0x20)
        val document = open(source)
        val edited = document.withAncillaryChunk("vpAg", payload)

        payload[0] = 0x7F

        assertArrayEquals(source, document.save().bytes)
        assertTrue(document.save().report.isEmpty)
        assertEquals(0x20.toByte(), chunkPayloads(edited.save().bytes, "vpAg").single().single())

        @Suppress("UNCHECKED_CAST")
        val mutableTypes = edited.writePlan.editedChunkTypes as MutableSet<String>
        @Suppress("UNCHECKED_CAST")
        val mutableEntries = edited.save().report.entries as MutableList<PngSaveReportEntry>
        assertThrows(UnsupportedOperationException::class.java) { mutableTypes.clear() }
        assertThrows(UnsupportedOperationException::class.java) { mutableEntries.clear() }
    }

    @Test
    fun `critical impact refuses reencode and reports safe unsafe and known ancillary policy`() {
        val source = png(
            "IHDR" to ihdr(),
            "vpAg" to byteArrayOf(0x10),
            "vpAG" to byteArrayOf(0x20),
            "sBIT" to byteArrayOf(8, 8, 8),
            "pHYs" to ByteArray(9),
            "sPLT" to byteArrayOf(0x21),
            "tIME" to ByteArray(7),
            "IDAT" to byteArrayOf(0x30),
            "IEND" to ByteArray(0),
        )

        val edited = open(source).markPixelDataChanged()
        val saved = edited.save()

        assertEquals(PngWriteImpact.CRITICAL, edited.writePlan.impact)
        assertEquals(PngDocumentSaveStatus.REFUSED, saved.status)
        assertEquals("png.pixel-edit.reencode.unsupported", saved.diagnostic?.code)
        assertNull(saved.outputBytes)
        assertArrayEquals(source, saved.sourceRecovery)
        val exposedRecovery = requireNotNull(saved.sourceRecovery)
        exposedRecovery[0] = 0
        assertArrayEquals(source, saved.sourceRecovery)
        val exception = assertThrows(PngSaveOutputUnavailableException::class.java) { saved.bytes }
        assertEquals("png.save.output.unavailable", exception.code)
        assertReportEntry(
            saved.report,
            "vpAg",
            PngSaveEntryStatus.PLANNED_PRESERVE,
            "png.ancillary.preserved.safe-to-copy",
        )
        assertReportEntry(
            saved.report,
            "vpAG",
            PngSaveEntryStatus.PLANNED_DROP,
            "png.ancillary.dropped.unsafe-to-copy",
        )
        assertReportEntry(
            saved.report,
            "sBIT",
            PngSaveEntryStatus.PLANNED_DROP,
            "png.ancillary.dropped.image-dependent",
        )
        assertReportEntry(
            saved.report,
            "pHYs",
            PngSaveEntryStatus.PLANNED_PRESERVE,
            "png.ancillary.preserved.known-independent",
        )
        assertReportEntry(
            saved.report,
            "sPLT",
            PngSaveEntryStatus.PLANNED_DROP,
            "png.ancillary.dropped.image-dependent",
        )
        assertReportEntry(
            saved.report,
            "tIME",
            PngSaveEntryStatus.PLANNED_DROP,
            "png.ancillary.dropped.stale",
        )
        assertReportEntry(
            saved.report,
            null,
            PngSaveEntryStatus.REFUSED,
            "png.pixel-edit.reencode.unsupported",
        )
    }

    @Test
    fun `ancillary edits never downgrade an existing critical impact`() {
        val source = png(
            "IHDR" to ihdr(),
            "vpAg" to byteArrayOf(0x10),
            "IDAT" to byteArrayOf(0x20),
            "IEND" to ByteArray(0),
        )

        val edited = open(source)
            .markPixelDataChanged()
            .withAncillaryChunk("tEXt", byteArrayOf(0x30))
            .withoutChunks("vpAg")

        assertEquals(PngWriteImpact.CRITICAL, edited.writePlan.impact)
        assertEquals(PngDocumentSaveStatus.REFUSED, edited.save().status)
        assertNull(edited.save().outputBytes)
    }

    @Test
    fun `critical refusal reports every explicitly planned removal ordinal`() {
        val source = png(
            "IHDR" to ihdr(),
            "tEXt" to byteArrayOf(0x10),
            "tEXt" to byteArrayOf(0x20),
            "IDAT" to byteArrayOf(0x30),
            "IEND" to ByteArray(0),
        )

        val saved = open(source)
            .withoutChunks("tEXt")
            .markPixelDataChanged()
            .save()

        val removals = saved.report.entries.filter { it.reasonCode == "png.ancillary.removed" }
        assertEquals(setOf(1, 2), removals.mapNotNull { it.ordinal }.toSet())
        assertTrue(removals.all { it.chunkType == "tEXt" && it.status == PngSaveEntryStatus.PLANNED_DROP })
        assertEquals(PngDocumentSaveStatus.REFUSED, saved.status)
        assertNull(saved.outputBytes)
    }

    @Test
    fun `preflights exact output byte and chunk limits before metadata emission`() {
        val source = png(
            "IHDR" to ihdr(),
            "IDAT" to byteArrayOf(0x20),
            "IEND" to ByteArray(0),
        )
        val byteLimited = open(
            source,
            PngContainerLimits.Default.copy(maxInputBytes = source.size.toLong() + 11L),
        ).withAncillaryChunk("vpAg", ByteArray(0)).save()

        assertEquals(PngDocumentSaveStatus.REFUSED, byteLimited.status)
        assertEquals("png.output.limit", byteLimited.diagnostic?.code)
        assertNull(byteLimited.outputBytes)
        assertArrayEquals(source, byteLimited.sourceRecovery)
        assertThrows(PngSaveOutputUnavailableException::class.java) { byteLimited.bytes }
        assertReportEntry(
            byteLimited.report,
            null,
            PngSaveEntryStatus.REFUSED,
            "png.output.limit",
        )
        val exactByteLimit = open(
            source,
            PngContainerLimits.Default.copy(maxInputBytes = source.size.toLong() + 12L),
        ).withAncillaryChunk("vpAg", ByteArray(0)).save()
        assertEquals(PngDocumentSaveStatus.SAVED, exactByteLimit.status)
        assertEquals(source.size + 12, exactByteLimit.bytes.size)

        val chunkLimited = open(
            source,
            PngContainerLimits.Default.copy(maxChunkCount = 3),
        ).withAncillaryChunk("vpAg", ByteArray(0)).save()

        assertEquals(PngDocumentSaveStatus.REFUSED, chunkLimited.status)
        assertEquals("png.output.chunk-count.limit", chunkLimited.diagnostic?.code)
        assertNull(chunkLimited.outputBytes)
        assertArrayEquals(source, chunkLimited.sourceRecovery)
        assertReportEntry(
            chunkLimited.report,
            null,
            PngSaveEntryStatus.REFUSED,
            "png.output.chunk-count.limit",
        )
        val exactChunkLimit = open(
            source,
            PngContainerLimits.Default.copy(maxChunkCount = 4),
        ).withAncillaryChunk("vpAg", ByteArray(0)).save()
        assertEquals(PngDocumentSaveStatus.SAVED, exactChunkLimit.status)
        assertEquals(4, chunkTypes(exactChunkLimit.bytes).size)

        val replacementAtLimit = open(
            png(
                "IHDR" to ihdr(),
                "vpAg" to byteArrayOf(0x10),
                "IDAT" to byteArrayOf(0x20),
                "IEND" to ByteArray(0),
            ),
            PngContainerLimits.Default.copy(maxChunkCount = 4),
        ).withAncillaryChunk("vpAg", byteArrayOf(0x30)).save()
        assertEquals(PngDocumentSaveStatus.SAVED, replacementAtLimit.status)
    }

    @Test
    fun `preflight refusal retains the owned source snapshot without an eager copy`() {
        val source = png(
            "IHDR" to ihdr(),
            "IDAT" to byteArrayOf(0x20),
            "IEND" to ByteArray(0),
        )
        val ownedSnapshot = source.copyOf()
        val limits = PngContainerLimits.Default.copy(maxInputBytes = source.size.toLong() + 11L)
        val opened = PngDocument.open(source, limits) { ownedSnapshot }
        assertInstanceOf(PngDocumentOpenResult.Success::class.java, opened)
        val document = (opened as PngDocumentOpenResult.Success).document

        val refused = document.withAncillaryChunk("vpAg", ByteArray(0)).save()

        assertEquals("png.output.limit", refused.diagnostic?.code)
        assertTrue(refused.referencesSourceRecoverySnapshot(ownedSnapshot))
        val publicRecovery = requireNotNull(refused.sourceRecovery)
        assertFalse(publicRecovery === ownedSnapshot)
        publicRecovery[0] = 0
        assertArrayEquals(source, refused.sourceRecovery)
    }

    @Test
    fun `validates ancillary type case payload limit and critical removal`() {
        val document = open(
            png(
                "IHDR" to ihdr(),
                "IDAT" to byteArrayOf(0x10),
                "IEND" to ByteArray(0),
            ),
            PngContainerLimits.Default.copy(maxAncillaryChunkBytes = 1),
        )

        assertEditFailure("png.chunk.type.invalid") {
            document.withAncillaryChunk("abc", ByteArray(0))
        }
        assertEditFailure("png.chunk.type.invalid") {
            document.withAncillaryChunk("ab1d", ByteArray(0))
        }
        assertEditFailure("png.chunk.type.reserved") {
            document.withAncillaryChunk("vpag", ByteArray(0))
        }
        assertEditFailure("png.chunk.type.critical") {
            document.withAncillaryChunk("IDAT", ByteArray(0))
        }
        assertEditFailure("png.chunk.type.critical") {
            document.withoutChunks("IHDR")
        }
        assertEditFailure("png.apng.unsupported") {
            document.withAncillaryChunk("acTL", ByteArray(0))
        }
        assertEditFailure("png.ancillary.limit") {
            document.withAncillaryChunk("vpAg", ByteArray(2))
        }
    }

    @Test
    fun `reuses existing insertion anchor and defaults new chunks before IDAT`() {
        val source = png(
            "IHDR" to ihdr(colorType = 3),
            "vpAg" to byteArrayOf(0x10),
            "PLTE" to byteArrayOf(0, 0, 0),
            "IDAT" to byteArrayOf(0x20),
            "IEND" to ByteArray(0),
        )

        val saved = open(source)
            .withAncillaryChunk("vpAg", byteArrayOf(0x30))
            .withAncillaryChunk("raNd", byteArrayOf(0x40))
            .save()

        assertEquals(listOf("IHDR", "vpAg", "PLTE", "raNd", "IDAT", "IEND"), chunkTypes(saved.bytes))
    }

    @Test
    fun `uses and validates required pre-palette anchors for known chunks`() {
        val source = png(
            "IHDR" to ihdr(colorType = 3),
            "PLTE" to byteArrayOf(0, 0, 0),
            "IDAT" to byteArrayOf(0x20),
            "IEND" to ByteArray(0),
        )

        val saved = open(source).withAncillaryChunk("sBIT", byteArrayOf(8, 8, 8)).save()

        assertEquals(listOf("IHDR", "sBIT", "PLTE", "IDAT", "IEND"), chunkTypes(saved.bytes))

        val invalidSource = png(
            "IHDR" to ihdr(colorType = 3),
            "PLTE" to byteArrayOf(0, 0, 0),
            "sBIT" to byteArrayOf(8, 8, 8),
            "IDAT" to byteArrayOf(0x20),
            "IEND" to ByteArray(0),
        )
        assertEditFailure("png.ancillary.anchor.invalid") {
            open(invalidSource).withAncillaryChunk("sBIT", byteArrayOf(7, 7, 7))
        }

        val postIdatSource = png(
            "IHDR" to ihdr(),
            "IDAT" to byteArrayOf(0x20),
            "sBIT" to byteArrayOf(8, 8, 8),
            "IEND" to ByteArray(0),
        )
        assertEditFailure("png.ancillary.anchor.invalid") {
            open(postIdatSource).withAncillaryChunk("sBIT", byteArrayOf(7, 7, 7))
        }
    }

    @Test
    fun `enforces HDR and palette-dependent known chunk anchors`() {
        val indexedSource = png(
            "IHDR" to ihdr(colorType = 3),
            "PLTE" to byteArrayOf(0, 0, 0),
            "IDAT" to byteArrayOf(0x20),
            "IEND" to ByteArray(0),
        )

        val hdrSaved = open(indexedSource)
            .withAncillaryChunk("mDCV", ByteArray(24))
            .withAncillaryChunk("cLLI", ByteArray(8))
            .save()
        assertEquals(listOf("IHDR", "mDCV", "cLLI", "PLTE", "IDAT", "IEND"), chunkTypes(hdrSaved.bytes))

        val paletteSaved = open(indexedSource)
            .withAncillaryChunk("bKGD", byteArrayOf(0))
            .withAncillaryChunk("hIST", byteArrayOf(0, 1))
            .withAncillaryChunk("tRNS", byteArrayOf(0x7F))
            .save()
        assertEquals(
            listOf("IHDR", "PLTE", "bKGD", "hIST", "tRNS", "IDAT", "IEND"),
            chunkTypes(paletteSaved.bytes),
        )

        for (type in listOf("bKGD", "hIST", "tRNS")) {
            val malformed = png(
                "IHDR" to ihdr(colorType = 3),
                type to byteArrayOf(0),
                "PLTE" to byteArrayOf(0, 0, 0),
                "IDAT" to byteArrayOf(0x20),
                "IEND" to ByteArray(0),
            )
            assertEditFailure("png.ancillary.anchor.invalid") {
                open(malformed).withAncillaryChunk(type, byteArrayOf(1))
            }
        }

        val noPalette = png(
            "IHDR" to ihdr(),
            "IDAT" to byteArrayOf(0x20),
            "IEND" to ByteArray(0),
        )
        assertEditFailure("png.ancillary.anchor.requires-plte") {
            open(noPalette).withAncillaryChunk("hIST", byteArrayOf(0, 1))
        }
        val existingWithoutPalette = png(
            "IHDR" to ihdr(),
            "hIST" to byteArrayOf(0, 1),
            "IDAT" to byteArrayOf(0x20),
            "IEND" to ByteArray(0),
        )
        assertEditFailure("png.ancillary.anchor.requires-plte") {
            open(existingWithoutPalette).withAncillaryChunk("hIST", byteArrayOf(0, 2))
        }
    }

    @Test
    fun `rejects every ambiguous duplicate replacement`() {
        val source = png(
            "IHDR" to ihdr(),
            "vpAg" to byteArrayOf(0x10),
            "vpAg" to byteArrayOf(0x20),
            "IDAT" to byteArrayOf(0x30),
            "IEND" to ByteArray(0),
        )

        assertEditFailure("png.ancillary.replacement.ambiguous") {
            open(source).withAncillaryChunk("vpAg", byteArrayOf(0x40))
        }
    }

    @Test
    fun `requires an existing anchor for absent unknown unsafe chunks`() {
        val source = png(
            "IHDR" to ihdr(),
            "IDAT" to byteArrayOf(0x20),
            "IEND" to ByteArray(0),
        )

        assertEditFailure("png.ancillary.unsafe.anchor.required") {
            open(source).withAncillaryChunk("vpAG", byteArrayOf(0x30))
        }

        val existingUnsafe = png(
            "IHDR" to ihdr(),
            "vpAG" to byteArrayOf(0x10),
            "IDAT" to byteArrayOf(0x20),
            "IEND" to ByteArray(0),
        )
        val saved = open(existingUnsafe).withAncillaryChunk("vpAG", byteArrayOf(0x30)).save()
        assertEquals(0x30.toByte(), chunkPayloads(saved.bytes, "vpAG").single().single())
    }

    @Test
    fun `saves pristine source bytes identically including unknown ancillary chunks and IDAT segmentation`() {
        val source = png(
            "IHDR" to ihdr(width = 2, height = 3),
            "vpAg" to byteArrayOf(0x11, 0x22),
            "IDAT" to byteArrayOf(0x33),
            "IDAT" to byteArrayOf(0x44, 0x55),
            "IEND" to ByteArray(0),
        )

        val saved = open(source).save()

        assertArrayEquals(source, saved.bytes)
        assertArrayEquals(source, saved.outputBytes)
        assertNull(saved.sourceRecovery)
        assertTrue(saved.report.isEmpty)
    }

    @Test
    fun `snapshots caller bytes before parsing and protects every byte result`() {
        val source = png(
            "IHDR" to ihdr(width = 2, height = 3),
            "IDAT" to byteArrayOf(0x11),
            "IEND" to ByteArray(0),
        )
        val expected = source.copyOf()
        val document = open(source)

        source[0] = 0
        val exposedOriginal = document.originalBytes
        exposedOriginal[0] = 0
        val exposedSave = document.save().bytes
        exposedSave[0] = 0

        assertArrayEquals(expected, document.originalBytes)
        assertArrayEquals(expected, document.save().bytes)
        assertEquals(2, document.header.width)
        assertEquals(3, document.header.height)
    }

    @Test
    fun `exposes immutable shared parser chunk records`() {
        val document = open(
            png(
                "IHDR" to ihdr(),
                "vpAg" to byteArrayOf(0x11),
                "IDAT" to byteArrayOf(0x22),
                "IEND" to ByteArray(0),
            ),
        )

        @Suppress("UNCHECKED_CAST")
        val mutableView = document.chunks as MutableList<PngChunkRecord>

        assertThrows(UnsupportedOperationException::class.java) { mutableView.clear() }
        assertEquals(listOf("IHDR", "vpAg", "IDAT", "IEND"), document.chunks.map(PngChunkRecord::type))
    }

    @Test
    fun `propagates APNG parser diagnostics with chunk type and offset`() {
        val result = PngDocument.open(
            png(
                "IHDR" to ihdr(),
                "acTL" to ByteArray(8),
                "IDAT" to byteArrayOf(0x11),
                "IEND" to ByteArray(0),
            ),
        )

        assertInstanceOf(PngDocumentOpenResult.Failure::class.java, result)
        val diagnostic = (result as PngDocumentOpenResult.Failure).diagnostic
        assertEquals("png.apng.unsupported", diagnostic.code)
        assertEquals(33L, diagnostic.offset)
        assertEquals("acTL", diagnostic.chunkType)
    }

    @Test
    fun `propagates parser diagnostics with chunk type and offset`() {
        val source = png(
            "IHDR" to ihdr(),
            "IDAT" to byteArrayOf(0x11),
            "IEND" to ByteArray(0),
        )
        source[41] = (source[41].toInt() xor 1).toByte()

        val result = PngDocument.open(source)

        assertInstanceOf(PngDocumentOpenResult.Failure::class.java, result)
        val diagnostic = (result as PngDocumentOpenResult.Failure).diagnostic
        assertEquals("png.chunk.crc.invalid", diagnostic.code)
        assertEquals(33L, diagnostic.offset)
        assertEquals("IDAT", diagnostic.chunkType)
    }

    @Test
    fun `rejects over-limit input before invoking the snapshot path`() {
        val source = ByteArray(16)
        var snapshotCalled = false

        val result = PngDocument.open(
            bytes = source,
            limits = PngContainerLimits.Default.copy(maxInputBytes = source.size.toLong() - 1L),
        ) {
            snapshotCalled = true
            error("The snapshot path must not run for over-limit input")
        }

        assertInstanceOf(PngDocumentOpenResult.Failure::class.java, result)
        val diagnostic = (result as PngDocumentOpenResult.Failure).diagnostic
        assertEquals("png.input.limit", diagnostic.code)
        assertEquals(0L, diagnostic.offset)
        assertEquals(null, diagnostic.chunkType)
        assertFalse(snapshotCalled)
    }

    private fun open(bytes: ByteArray): PngDocument {
        return open(bytes, PngContainerLimits.Default)
    }

    private fun open(bytes: ByteArray, limits: PngContainerLimits): PngDocument {
        val result = PngDocument.open(bytes, limits)
        assertInstanceOf(PngDocumentOpenResult.Success::class.java, result)
        return (result as PngDocumentOpenResult.Success).document
    }

    private fun rawChunks(bytes: ByteArray): Map<String, List<ByteArray>> {
        val document = open(bytes)
        return document.chunks.groupBy(PngChunkRecord::type).mapValues { (_, records) ->
            records.map { record ->
                bytes.copyOfRange(record.rawRange.startInclusive.toInt(), record.rawRange.endExclusive.toInt())
            }
        }
    }

    private fun chunkPayloads(bytes: ByteArray, type: String): List<ByteArray> = open(bytes).chunks
        .filter { it.type == type }
        .map { record ->
            bytes.copyOfRange(record.payloadRange.startInclusive.toInt(), record.payloadRange.endExclusive.toInt())
        }

    private fun chunkTypes(bytes: ByteArray): List<String> = open(bytes).chunks.map(PngChunkRecord::type)

    private fun assertRawChunkListsEqual(expected: List<ByteArray>, actual: List<ByteArray>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (expectedChunk, actualChunk) ->
            assertArrayEquals(expectedChunk, actualChunk)
        }
    }

    private fun assertReportEntry(
        report: PngSaveReport,
        chunkType: String?,
        status: PngSaveEntryStatus,
        reasonCode: String,
    ) {
        assertTrue(report.entries.any { entry ->
            entry.chunkType == chunkType && entry.status == status && entry.reasonCode == reasonCode
        })
    }

    private fun assertEditFailure(code: String, operation: () -> Unit) {
        val exception = assertThrows(PngDocumentEditException::class.java, operation)
        assertEquals(code, exception.code)
    }

    private fun png(vararg chunks: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().apply {
            write(PNG_SIGNATURE)
            for ((type, payload) in chunks) writeChunk(type, payload)
        }.toByteArray()

    private fun ihdr(
        width: Int = 1,
        height: Int = 1,
        colorType: Int = 2,
    ): ByteArray = ByteArray(13).also { bytes ->
        writeI32BE(bytes, 0, width)
        writeI32BE(bytes, 4, height)
        bytes[8] = 8
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
