package org.graphiks.kanvas.codec.png

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import org.graphiks.kanvas.color.ColorProfiles
import org.graphiks.kanvas.color.icc.IccProfileWriter
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
        val invalidDocument = open(invalidSource)
        assertEquals(
            "png.metadata.sBIT.order",
            (invalidDocument.sBIT as PngMetadataValue.Refused).diagnostic.code,
        )
        assertArrayEquals(invalidSource, invalidDocument.save().bytes)

        val postIdatSource = png(
            "IHDR" to ihdr(),
            "IDAT" to byteArrayOf(0x20),
            "sBIT" to byteArrayOf(8, 8, 8),
            "IEND" to ByteArray(0),
        )
        val postIdatDocument = open(postIdatSource)
        assertEquals(
            "png.metadata.sBIT.order",
            (postIdatDocument.sBIT as PngMetadataValue.Refused).diagnostic.code,
        )
        assertArrayEquals(postIdatSource, postIdatDocument.save().bytes)
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
            .withAncillaryChunk("cICP", byteArrayOf(1, 13, 0, 1))
            .withAncillaryChunk("mDCV", ByteArray(24))
            .withAncillaryChunk("cLLI", ByteArray(8))
            .save()
        assertEquals(
            listOf("IHDR", "cICP", "mDCV", "cLLI", "PLTE", "IDAT", "IEND"),
            chunkTypes(hdrSaved.bytes),
        )

        val paletteSaved = open(indexedSource)
            .withAncillaryChunk("bKGD", byteArrayOf(0))
            .withAncillaryChunk("hIST", byteArrayOf(0, 1))
            .withAncillaryChunk("tRNS", byteArrayOf(0x7F))
            .save()
        assertEquals(
            listOf("IHDR", "PLTE", "bKGD", "hIST", "tRNS", "IDAT", "IEND"),
            chunkTypes(paletteSaved.bytes),
        )

        for ((type, code) in listOf(
            "bKGD" to "png.metadata.bKGD.order",
            "hIST" to "png.metadata.hIST.plte.required",
        )) {
            val malformed = png(
                "IHDR" to ihdr(colorType = 3),
                type to byteArrayOf(0),
                "PLTE" to byteArrayOf(0, 0, 0),
                "IDAT" to byteArrayOf(0x20),
                "IEND" to ByteArray(0),
            )
            val document = open(malformed)
            assertEquals(code, (metadataValueFor(document, type) as PngMetadataValue.Refused).diagnostic.code)
            assertArrayEquals(malformed, document.save().bytes)
        }

        val malformedTransparency = png(
            "IHDR" to ihdr(colorType = 3),
            "tRNS" to byteArrayOf(0),
            "PLTE" to byteArrayOf(0, 0, 0),
            "IDAT" to byteArrayOf(0x20),
            "IEND" to ByteArray(0),
        )
        assertEditFailure("png.ancillary.anchor.invalid") {
            open(malformedTransparency).withAncillaryChunk("tRNS", byteArrayOf(1))
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
        val existingWithoutPaletteDocument = open(existingWithoutPalette)
        assertEquals(
            "png.metadata.hIST.plte.required",
            (existingWithoutPaletteDocument.hIST as PngMetadataValue.Refused).diagnostic.code,
        )
        assertArrayEquals(existingWithoutPalette, existingWithoutPaletteDocument.save().bytes)
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

    @Test
    fun `exposes resolved typed views for every supported static metadata chunk`() {
        val iccProfile = IccProfileWriter.writeMatrixTrc(ColorProfiles.sRGB())
        val source = png(
            "IHDR" to ihdr(),
            "cHRM" to chromaticities(),
            "gAMA" to u32(45_455),
            "iCCP" to iccp("sRGB", iccProfile),
            "sRGB" to byteArrayOf(0),
            "cICP" to byteArrayOf(1, 13, 0, 1),
            "mDCV" to masteringDisplay(),
            "cLLI" to u32(10_000_000) + u32(2_500_000),
            "sBIT" to byteArrayOf(8, 8, 8),
            "pHYs" to u32(3_780) + u32(3_780) + byteArrayOf(1),
            "sPLT" to suggestedPalette("desktop"),
            "bKGD" to byteArrayOf(0, 1, 0, 2, 0, 3),
            "tRNS" to u16(1) + u16(2) + u16(3),
            "eXIf" to byteArrayOf('I'.code.toByte(), 'I'.code.toByte(), 42, 0, 8, 0, 0, 0),
            "tIME" to byteArrayOf(0x07, 0xEA.toByte(), 7, 10, 12, 34, 56),
            "tEXt" to textChunk("Title", "Kanvas"),
            "zTXt" to compressedTextChunk("Copyright", "2026 Kanvas"),
            "iTXt" to internationalTextChunk("Description", "fr", "Description", "Metadonnees"),
            "IDAT" to byteArrayOf(0x10),
            "IEND" to ByteArray(0),
        )

        val document = open(source)

        assertEquals("sRGB", resolved(document.iCCP).profileName)
        assertEquals(0, resolved(document.sRGB).renderingIntent)
        assertEquals(45_455L, resolved(document.gAMA).encodedGamma)
        assertEquals(31_270L, resolved(document.cHRM).whitePoint.x)
        assertEquals(1, resolved(document.cICP).info.primaries)
        assertEquals(10_000_000L, resolved(document.mDCV).maximumLuminance)
        assertEquals(2_500_000L, resolved(document.cLLI).maximumFrameAverageLightLevel)
        assertEquals(PngExifByteOrder.LITTLE_ENDIAN, resolved(document.eXIf).byteOrder)
        assertEquals(8L, resolved(document.eXIf).firstIfdOffset)
        assertEquals(3_780L, resolved(document.pHYs).pixelsPerUnitX)
        assertEquals(2026, resolved(document.tIME).year)
        assertEquals("Title", resolved(document.tEXt.single()).keyword)
        assertEquals("2026 Kanvas", resolved(document.zTXt.single()).text)
        assertEquals("Metadonnees", resolved(document.iTXt.single()).text)
        assertEquals(listOf(8, 8, 8), resolved(document.sBIT).channelBits)
        assertEquals(3, resolved(document.bKGD).blue)
        assertEquals(1, resolved(document.tRNS).redSample)
        assertEquals(1, resolved(document.sPLT.single()).entries.size)
        assertTrue(document.metadata.diagnostics.isEmpty())
        assertArrayEquals(source, document.save().bytes)
    }

    @Test
    fun `malformed fixed metadata retains raw chunks and exposes typed refusals`() {
        val malformedPayloads = linkedMapOf(
            "iCCP" to byteArrayOf(0),
            "sRGB" to byteArrayOf(4),
            "gAMA" to ByteArray(3),
            "cHRM" to ByteArray(31),
            "cICP" to ByteArray(3),
            "mDCV" to ByteArray(23),
            "cLLI" to ByteArray(7),
            "eXIf" to byteArrayOf(0, 1, 2, 3),
            "pHYs" to ByteArray(8),
            "tIME" to ByteArray(6),
            "sBIT" to byteArrayOf(8, 8),
            "bKGD" to ByteArray(5),
            "hIST" to byteArrayOf(0),
            "sPLT" to byteArrayOf('p'.code.toByte(), 0, 7),
        )

        for ((type, payload) in malformedPayloads) {
            val source = malformedStaticMetadataPng(type, payload)
            val document = open(source)
            val metadata = metadataValueFor(document, type)

            assertInstanceOf(PngMetadataValue.Refused::class.java, metadata, type)
            val refusal = metadata as PngMetadataValue.Refused
            assertEquals(type, refusal.record.type)
            assertEquals(type, refusal.diagnostic.chunkType)
            assertTrue(refusal.diagnostic.code.startsWith("png.metadata.$type."))
            assertTrue(document.metadata.diagnostics.contains(refusal.diagnostic))
            assertArrayEquals(source, document.save().bytes)
        }
    }

    @Test
    fun `bounded text decoding refuses compressed and direct text without losing source bytes`() {
        val limits = PngContainerLimits.Default.copy(
            metadata = PngMetadataLimits(
                maxTextBytes = 8,
                maxInflatedTextBytes = 8,
                maxIccProfileBytes = PngMetadataLimits.Default.maxIccProfileBytes,
                maxSuggestedPaletteEntries = PngMetadataLimits.Default.maxSuggestedPaletteEntries,
            ),
        )
        val text = "0123456789"
        val sources = listOf(
            "tEXt" to png(
                "IHDR" to ihdr(),
                "tEXt" to textChunk("Title", text),
                "IDAT" to byteArrayOf(0x10),
                "IEND" to ByteArray(0),
            ),
            "zTXt" to png(
                "IHDR" to ihdr(),
                "zTXt" to compressedTextChunk("Title", text),
                "IDAT" to byteArrayOf(0x10),
                "IEND" to ByteArray(0),
            ),
            "iTXt" to png(
                "IHDR" to ihdr(),
                "iTXt" to internationalTextChunk("Title", "", "", text, compressed = true),
                "IDAT" to byteArrayOf(0x10),
                "IEND" to ByteArray(0),
            ),
        )

        for ((type, source) in sources) {
            val document = open(source, limits)
            val metadata = metadataValueFor(document, type)

            assertInstanceOf(PngMetadataValue.Refused::class.java, metadata, type)
            assertEquals("png.metadata.$type.text.limit", (metadata as PngMetadataValue.Refused).diagnostic.code)
            assertArrayEquals(source, document.save().bytes)
        }
    }

    @Test
    fun `retains ancillary structural violations as typed refusals`() {
        val fixtures = listOf(
            "gAMA" to png(
                "IHDR" to ihdr(),
                "gAMA" to u32(45_455),
                "gAMA" to u32(45_455),
                "IDAT" to byteArrayOf(0x10),
                "IEND" to ByteArray(0),
            ) to "png.metadata.gAMA.duplicate",
            "hIST" to png(
                "IHDR" to ihdr(colorType = 2),
                "hIST" to byteArrayOf(0, 1),
                "IDAT" to byteArrayOf(0x10),
                "IEND" to ByteArray(0),
            ) to "png.metadata.hIST.plte.required",
            "mDCV" to png(
                "IHDR" to ihdr(),
                "mDCV" to masteringDisplay(),
                "IDAT" to byteArrayOf(0x10),
                "IEND" to ByteArray(0),
            ) to "png.metadata.mDCV.cicp.required",
            "tRNS" to png(
                "IHDR" to ihdr(colorType = 3),
                "tRNS" to byteArrayOf(0x7F),
                "PLTE" to byteArrayOf(0, 0, 0),
                "IDAT" to byteArrayOf(0x10),
                "IEND" to ByteArray(0),
            ) to "png.metadata.tRNS.order",
        )

        for ((fixture, code) in fixtures) {
            val (type, source) = fixture
            val document = open(source)
            val metadata = metadataValueFor(document, type)

            assertInstanceOf(PngMetadataValue.Refused::class.java, metadata, type)
            assertEquals(code, (metadata as PngMetadataValue.Refused).diagnostic.code)
            assertTrue(document.metadata.diagnostics.contains(metadata.diagnostic))
            assertArrayEquals(source, document.originalBytes)
            assertArrayEquals(source, document.save().bytes)
        }

        val duplicateSuggestedPalette = png(
            "IHDR" to ihdr(),
            "sPLT" to suggestedPalette("display"),
            "sPLT" to suggestedPalette("display"),
            "IDAT" to byteArrayOf(0x10),
            "IEND" to ByteArray(0),
        )
        val suggestedPaletteDocument = open(duplicateSuggestedPalette)
        assertInstanceOf(PngMetadataValue.Resolved::class.java, suggestedPaletteDocument.sPLT.first())
        assertEquals(
            "png.metadata.sPLT.name.duplicate",
            (suggestedPaletteDocument.sPLT.last() as PngMetadataValue.Refused).diagnostic.code,
        )
        assertArrayEquals(duplicateSuggestedPalette, suggestedPaletteDocument.save().bytes)
    }

    @Test
    fun `iTXt ignores uncompressed method and rejects null text bytes`() {
        val uncompressedUnknownMethod = png(
            "IHDR" to ihdr(),
            "iTXt" to "Title".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0, 0, 7, 0, 0, 'o'.code.toByte(), 'k'.code.toByte()),
            "IDAT" to byteArrayOf(0x10),
            "IEND" to ByteArray(0),
        )
        assertEquals("ok", resolved(open(uncompressedUnknownMethod).iTXt.single()).text)

        val directNull = png(
            "IHDR" to ihdr(),
            "iTXt" to "Title".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0, 0, 0, 0, 0, 'a'.code.toByte(), 0, 'b'.code.toByte()),
            "IDAT" to byteArrayOf(0x10),
            "IEND" to ByteArray(0),
        )
        val compressedNull = png(
            "IHDR" to ihdr(),
            "iTXt" to "Title".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0, 1, 0, 0, 0) +
                deflate(byteArrayOf('a'.code.toByte(), 0, 'b'.code.toByte())),
            "IDAT" to byteArrayOf(0x10),
            "IEND" to ByteArray(0),
        )

        for (source in listOf(directNull, compressedNull)) {
            val document = open(source)
            assertEquals(
                "png.metadata.iTXt.text.nul",
                (document.iTXt.single() as PngMetadataValue.Refused).diagnostic.code,
            )
            assertArrayEquals(source, document.save().bytes)
        }
    }

    @Test
    fun `enforces document wide decoded text and repeatable text count budgets`() {
        val countLimited = png(
            "IHDR" to ihdr(),
            "tEXt" to textChunk("One", "a"),
            "tEXt" to textChunk("Two", "b"),
            "IDAT" to byteArrayOf(0x10),
            "IEND" to ByteArray(0),
        )
        val countDocument = open(
            countLimited,
            PngContainerLimits.Default.copy(
                metadata = PngMetadataLimits.Default.copy(maxTextChunkCount = 1),
            ),
        )
        assertInstanceOf(PngMetadataValue.Resolved::class.java, countDocument.tEXt.first())
        assertEquals(
            "png.metadata.tEXt.count.limit",
            (countDocument.tEXt.last() as PngMetadataValue.Refused).diagnostic.code,
        )
        assertArrayEquals(countLimited, countDocument.save().bytes)

        val compressed = png(
            "IHDR" to ihdr(),
            "zTXt" to compressedTextChunk("One", "abc"),
            "zTXt" to compressedTextChunk("Two", "def"),
            "IDAT" to byteArrayOf(0x10),
            "IEND" to ByteArray(0),
        )
        val compressedDocument = open(
            compressed,
            PngContainerLimits.Default.copy(
                metadata = PngMetadataLimits.Default.copy(maxDecodedMetadataBytes = 8),
            ),
        )
        assertInstanceOf(PngMetadataValue.Resolved::class.java, compressedDocument.zTXt.first())
        assertEquals(
            "png.metadata.zTXt.decoded.limit",
            (compressedDocument.zTXt.last() as PngMetadataValue.Refused).diagnostic.code,
        )
        assertArrayEquals(compressed, compressedDocument.save().bytes)
    }

    @Test
    fun `bounds histogram and aggregate suggested palette materialization`() {
        val palette256 = ByteArray(256 * 3)
        val histogram256 = ByteArray(256 * 2)
        val maximum = png(
            "IHDR" to ihdr(colorType = 3),
            "PLTE" to palette256,
            "hIST" to histogram256,
            "IDAT" to byteArrayOf(0x10),
            "IEND" to ByteArray(0),
        )
        assertEquals(256, resolved(open(maximum).hIST).frequencies.size)
        val histogramLimited = open(
            maximum,
            PngContainerLimits.Default.copy(
                metadata = PngMetadataLimits.Default.copy(maxHistogramEntries = 255),
            ),
        )
        assertEquals(
            "png.metadata.hIST.entries.limit",
            (histogramLimited.hIST as PngMetadataValue.Refused).diagnostic.code,
        )
        assertArrayEquals(maximum, histogramLimited.save().bytes)

        val oversizedPalette = png(
            "IHDR" to ihdr(colorType = 3),
            "PLTE" to ByteArray(257 * 3),
            "hIST" to ByteArray(257 * 2),
            "IDAT" to byteArrayOf(0x10),
            "IEND" to ByteArray(0),
        )
        assertOpenFailure(oversizedPalette, "png.plte.entries.limit")

        val suggestedPalettes = png(
            "IHDR" to ihdr(),
            "sPLT" to suggestedPalette("one"),
            "sPLT" to suggestedPalette("two"),
            "IDAT" to byteArrayOf(0x10),
            "IEND" to ByteArray(0),
        )
        val countLimited = open(
            suggestedPalettes,
            PngContainerLimits.Default.copy(
                metadata = PngMetadataLimits.Default.copy(maxSuggestedPaletteCount = 1),
            ),
        )
        assertEquals(
            "png.metadata.sPLT.count.limit",
            (countLimited.sPLT.last() as PngMetadataValue.Refused).diagnostic.code,
        )
        val entryLimited = open(
            suggestedPalettes,
            PngContainerLimits.Default.copy(
                metadata = PngMetadataLimits.Default.copy(maxSuggestedPaletteEntriesTotal = 1),
            ),
        )
        assertEquals(
            "png.metadata.sPLT.entries.total.limit",
            (entryLimited.sPLT.last() as PngMetadataValue.Refused).diagnostic.code,
        )
        assertArrayEquals(suggestedPalettes, entryLimited.save().bytes)
        val budgetLimited = open(
            suggestedPalettes,
            PngContainerLimits.Default.copy(
                metadata = PngMetadataLimits.Default.copy(maxDecodedMetadataBytes = 75),
            ),
        )
        assertEquals(
            "png.metadata.sPLT.decoded.limit",
            (budgetLimited.sPLT.last() as PngMetadataValue.Refused).diagnostic.code,
        )
        assertArrayEquals(suggestedPalettes, budgetLimited.save().bytes)
    }

    @Test
    fun `masks low bit background samples and keeps grayscale cICP resolved`() {
        for (bitDepth in listOf(1, 2, 4, 8)) {
            val source = png(
                "IHDR" to ihdr(bitDepth = bitDepth, colorType = 0),
                "bKGD" to u16(0xFFFF),
                "IDAT" to byteArrayOf(0x10),
                "IEND" to ByteArray(0),
            )
            assertEquals((1 shl bitDepth) - 1, resolved(open(source).bKGD).grayscale)
        }
        val rgb = png(
            "IHDR" to ihdr(bitDepth = 8, colorType = 2),
            "bKGD" to u16(0xFF01) + u16(0xFF02) + u16(0xFF03),
            "IDAT" to byteArrayOf(0x10),
            "IEND" to ByteArray(0),
        )
        val background = resolved(open(rgb).bKGD)
        assertEquals(1, background.red)
        assertEquals(2, background.green)
        assertEquals(3, background.blue)

        for (colorType in listOf(0, 4)) {
            val grayscaleCicp = png(
                "IHDR" to ihdr(colorType = colorType),
                "cICP" to byteArrayOf(1, 13, 0, 0),
                "IDAT" to byteArrayOf(0x10),
                "IEND" to ByteArray(0),
            )
            val cicp = resolved(open(grayscaleCicp).cICP)
            assertEquals(1, cicp.info.primaries)
            assertFalse(cicp.info.fullRange)
            assertEquals(PngCicpProfileResolution.GRAYSCALE_INFO_ONLY, cicp.profileResolution)
            assertNull(cicp.profile)
        }
    }

    @Test
    fun `exposes typed transparency layouts and keeps metadata current after ancillary edits`() {
        val paletteTransparency = png(
            "IHDR" to ihdr(colorType = 3),
            "PLTE" to byteArrayOf(0, 0, 0, 1, 1, 1),
            "tRNS" to byteArrayOf(0x7F),
            "IDAT" to byteArrayOf(0x10),
            "IEND" to ByteArray(0),
        )
        assertEquals(listOf(127), resolved(open(paletteTransparency).tRNS).paletteAlpha)

        val invalidTransparency = png(
            "IHDR" to ihdr(colorType = 4),
            "tRNS" to byteArrayOf(0, 1),
            "IDAT" to byteArrayOf(0x10),
            "IEND" to ByteArray(0),
        )
        assertEquals(
            "png.metadata.tRNS.color-type.invalid",
            (open(invalidTransparency).tRNS as PngMetadataValue.Refused).diagnostic.code,
        )
        val invalidPaletteTransparency = png(
            "IHDR" to ihdr(colorType = 3),
            "PLTE" to byteArrayOf(0, 0, 0),
            "tRNS" to byteArrayOf(0x7F, 0x7E),
            "IDAT" to byteArrayOf(0x10),
            "IEND" to ByteArray(0),
        )
        assertEquals(
            "png.metadata.tRNS.palette.length",
            (open(invalidPaletteTransparency).tRNS as PngMetadataValue.Refused).diagnostic.code,
        )

        val source = png(
            "IHDR" to ihdr(),
            "tEXt" to textChunk("Title", "Original"),
            "IDAT" to byteArrayOf(0x10),
            "IEND" to ByteArray(0),
        )
        val replaced = open(source).withAncillaryChunk("tEXt", textChunk("Title", "Updated"))
        assertEquals("Updated", resolved(replaced.tEXt.single()).text)
        val removed = replaced.withoutChunks("tEXt")
        assertTrue(removed.tEXt.isEmpty())
        assertTrue(chunkPayloads(removed.save().bytes, "tEXt").isEmpty())
    }

    @Test
    fun `rejects critical palette violations when opening a document`() {
        for (colorType in listOf(0, 4)) {
            val source = png(
                "IHDR" to ihdr(colorType = colorType),
                "PLTE" to byteArrayOf(0, 0, 0),
                "hIST" to byteArrayOf(0, 1),
                "IDAT" to byteArrayOf(0x10),
                "IEND" to ByteArray(0),
            )
            assertOpenFailure(source, "png.plte.color-type.forbidden")
        }
        assertOpenFailure(
            png(
                "IHDR" to ihdr(colorType = 3),
                "IDAT" to byteArrayOf(0x10),
                "IEND" to ByteArray(0),
            ),
            "png.plte.required",
        )
    }

    @Test
    fun `masks sub sixteen bit transparency samples without mutating raw chunks`() {
        for (bitDepth in listOf(1, 2, 4, 8)) {
            val source = png(
                "IHDR" to ihdr(bitDepth = bitDepth, colorType = 0),
                "tRNS" to u16(0xFFFF),
                "IDAT" to byteArrayOf(0x10),
                "IEND" to ByteArray(0),
            )
            val document = open(source)
            assertEquals((1 shl bitDepth) - 1, resolved(document.tRNS).grayscaleSample)
            assertArrayEquals(source, document.save().bytes)
        }

        val rgb8 = png(
            "IHDR" to ihdr(bitDepth = 8, colorType = 2),
            "tRNS" to u16(0xFF01) + u16(0xFF02) + u16(0xFF03),
            "IDAT" to byteArrayOf(0x10),
            "IEND" to ByteArray(0),
        )
        val rgb8Document = open(rgb8)
        val rgb8Transparency = resolved(rgb8Document.tRNS)
        assertEquals(1, rgb8Transparency.redSample)
        assertEquals(2, rgb8Transparency.greenSample)
        assertEquals(3, rgb8Transparency.blueSample)
        assertArrayEquals(rgb8, rgb8Document.save().bytes)

        val rgb16 = png(
            "IHDR" to ihdr(bitDepth = 16, colorType = 2),
            "tRNS" to u16(0xABCD) + u16(0x1234) + u16(0xFEDC),
            "IDAT" to byteArrayOf(0x10),
            "IEND" to ByteArray(0),
        )
        val rgb16Document = open(rgb16)
        val rgb16Transparency = resolved(rgb16Document.tRNS)
        assertEquals(0xABCD, rgb16Transparency.redSample)
        assertEquals(0x1234, rgb16Transparency.greenSample)
        assertEquals(0xFEDC, rgb16Transparency.blueSample)
        assertArrayEquals(rgb16, rgb16Document.save().bytes)
    }

    @Test
    fun `projects ancillary metadata without serializing the PNG document`() {
        val source = png(
            "IHDR" to ihdr(),
            "tEXt" to textChunk("Title", "Original"),
            "IDAT" to ByteArray(256 * 1024) { 0x5A },
            "IEND" to ByteArray(0),
        )
        val replacement = textChunk("Title", "Updated")

        val edited = open(source).withAncillaryChunk("tEXt", replacement)

        assertEquals("Updated", resolved(edited.tEXt.single()).text)
        assertEquals(0L, edited.metadataProjectionStats.serializedPngBytes)
        assertEquals(replacement.size.toLong(), edited.metadataProjectionStats.copiedPayloadBytes)

        val restored = open(
            png(
                "IHDR" to ihdr(),
                "IDAT" to byteArrayOf(0x10),
                "IEND" to ByteArray(0),
            ),
        ).withAncillaryChunk("tEXt", replacement).withoutChunks("tEXt")
        assertEquals(PngWriteImpact.NONE, restored.writePlan.impact)
        assertTrue(restored.tEXt.isEmpty())
    }

    @Test
    fun `metadata records after ancillary edits locate planned output chunks`() {
        val replacement = textChunk("Title", "Updated")
        val physicalDimensions = u32(2_835) + u32(2_835) + byteArrayOf(1)
        val exif = byteArrayOf('I'.code.toByte(), 'I'.code.toByte(), 42, 0, 8, 0, 0, 0)
        val source = open(
            png(
                "IHDR" to ihdr(),
                "tEXt" to textChunk("Title", "Original"),
                "pHYs" to physicalDimensions,
                "IDAT" to byteArrayOf(0x10),
                "IEND" to ByteArray(0),
            ),
        )
        val edited = source.withAncillaryChunk("tEXt", replacement)
            .withAncillaryChunk("eXIf", exif)

        val output = edited.save().bytes
        val records = listOf(
            edited.tEXt.single().record to ("tEXt" to replacement),
            requireNotNull(edited.pHYs).record to ("pHYs" to physicalDimensions),
            requireNotNull(edited.eXIf).record to ("eXIf" to exif),
        )
        val outputRecords = open(output).chunks

        for ((record, expected) in records) {
            val (type, payload) = expected
            assertRecordMatchesOutput(output, record, type, payload)
            assertEquals(outputRecords.single { it.type == type }, record)
        }

        assertTrue(
            requireNotNull(source.pHYs).record.rawRange.startInclusive !=
                requireNotNull(edited.pHYs).record.rawRange.startInclusive,
        )
        assertEquals(0L, edited.metadataProjectionStats.serializedPngBytes)
    }

    @Test
    fun `does not release decoded budget after late metadata refusals`() {
        val textLimits = PngContainerLimits.Default.copy(
            metadata = PngMetadataLimits.Default.copy(
                maxTextBytes = 64,
                maxInflatedTextBytes = 64,
                maxDecodedMetadataBytes = 128,
            ),
        )
        val invalidLatin1 = "a".repeat(63) + '\u0001'
        val validText = "b".repeat(64)
        val compressedText = png(
            "IHDR" to ihdr(),
            "zTXt" to compressedTextChunk("A", invalidLatin1),
            "zTXt" to compressedTextChunk("B", validText),
            "IDAT" to byteArrayOf(0x10),
            "IEND" to ByteArray(0),
        )
        val compressedTextDocument = open(compressedText, textLimits)
        assertEquals(
            "png.metadata.zTXt.text.invalid",
            (compressedTextDocument.zTXt.first() as PngMetadataValue.Refused).diagnostic.code,
        )
        assertEquals(
            "png.metadata.zTXt.decoded.limit",
            (compressedTextDocument.zTXt.last() as PngMetadataValue.Refused).diagnostic.code,
        )
        assertArrayEquals(compressedText, compressedTextDocument.save().bytes)

        val invalidInternationalText = "c".repeat(63) + '\u0000'
        val internationalText = png(
            "IHDR" to ihdr(),
            "iTXt" to internationalTextChunk("A", "", "", invalidInternationalText, compressed = true),
            "iTXt" to internationalTextChunk("B", "", "", validText, compressed = true),
            "IDAT" to byteArrayOf(0x10),
            "IEND" to ByteArray(0),
        )
        val internationalTextDocument = open(internationalText, textLimits)
        assertEquals(
            "png.metadata.iTXt.text.nul",
            (internationalTextDocument.iTXt.first() as PngMetadataValue.Refused).diagnostic.code,
        )
        assertEquals(
            "png.metadata.iTXt.decoded.limit",
            (internationalTextDocument.iTXt.last() as PngMetadataValue.Refused).diagnostic.code,
        )
        assertArrayEquals(internationalText, internationalTextDocument.save().bytes)

        val palettes = png(
            "IHDR" to ihdr(),
            "sPLT" to suggestedPalette("A", intArrayOf(2, 3)),
            "sPLT" to suggestedPalette("B", intArrayOf(3, 2)),
            "IDAT" to byteArrayOf(0x10),
            "IEND" to ByteArray(0),
        )
        val paletteDocument = open(
            palettes,
            PngContainerLimits.Default.copy(
                metadata = PngMetadataLimits.Default.copy(maxDecodedMetadataBytes = 193),
            ),
        )
        assertEquals(
            "png.metadata.sPLT.frequency.order",
            (paletteDocument.sPLT.first() as PngMetadataValue.Refused).diagnostic.code,
        )
        assertEquals(
            "png.metadata.sPLT.decoded.limit",
            (paletteDocument.sPLT.last() as PngMetadataValue.Refused).diagnostic.code,
        )
        assertArrayEquals(palettes, paletteDocument.save().bytes)
    }

    @Test
    fun `refuses invalid typed metadata values without losing their raw chunks`() {
        val semanticFixtures = listOf(
            "cICP" to byteArrayOf(1, 13, 1, 1) to "png.metadata.cICP.matrix.unsupported",
            "cICP" to byteArrayOf(1, 13, 0, 2) to "png.metadata.cICP.range.invalid",
            "pHYs" to (ByteArray(8) + byteArrayOf(2)) to "png.metadata.pHYs.unit.invalid",
            "tIME" to byteArrayOf(0x07, 0xEA.toByte(), 2, 30, 0, 0, 0) to "png.metadata.tIME.date.invalid",
            "sBIT" to byteArrayOf(9, 8, 8) to "png.metadata.sBIT.value.invalid",
            "sPLT" to byteArrayOf('p'.code.toByte(), 0, 8, 1) to "png.metadata.sPLT.layout",
        )

        for ((fixture, code) in semanticFixtures) {
            val (type, payload) = fixture
            val source = png(
                "IHDR" to ihdr(),
                type to payload,
                "IDAT" to byteArrayOf(0x10),
                "IEND" to ByteArray(0),
            )
            val document = open(source)
            val metadata = metadataValueFor(document, type)

            assertInstanceOf(PngMetadataValue.Refused::class.java, metadata, type)
            assertEquals(code, (metadata as PngMetadataValue.Refused).diagnostic.code)
            assertArrayEquals(source, document.save().bytes)
        }

        val invalidUtf8 = png(
            "IHDR" to ihdr(),
            "iTXt" to "Title".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0, 0, 0, 0, 0, 0xC3.toByte()),
            "IDAT" to byteArrayOf(0x10),
            "IEND" to ByteArray(0),
        )
        val invalidUtf8Document = open(invalidUtf8)
        assertEquals(
            "png.metadata.iTXt.utf8.invalid",
            (metadataValueFor(invalidUtf8Document, "iTXt") as PngMetadataValue.Refused).diagnostic.code,
        )

        val trailingZlib = png(
            "IHDR" to ihdr(),
            "zTXt" to (compressedTextChunk("Title", "ok") + byteArrayOf(0)),
            "IDAT" to byteArrayOf(0x10),
            "IEND" to ByteArray(0),
        )
        val trailingZlibDocument = open(trailingZlib)
        assertEquals(
            "png.metadata.zTXt.compression.trailing",
            (metadataValueFor(trailingZlibDocument, "zTXt") as PngMetadataValue.Refused).diagnostic.code,
        )
    }

    @Test
    fun `accepts bounded iTXt fields when the configured text limit reaches Int max`() {
        val source = png(
            "IHDR" to ihdr(),
            "iTXt" to internationalTextChunk("Title", "en", "Title", "Kanvas"),
            "IDAT" to byteArrayOf(0x10),
            "IEND" to ByteArray(0),
        )
        val document = open(
            source,
            PngContainerLimits.Default.copy(
                metadata = PngMetadataLimits.Default.copy(maxTextBytes = Int.MAX_VALUE),
            ),
        )

        assertEquals("Kanvas", resolved(document.iTXt.single()).text)
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

    private fun assertRecordMatchesOutput(
        bytes: ByteArray,
        record: PngChunkRecord,
        type: String,
        payload: ByteArray,
    ) {
        assertTrue(record.rawRange.startInclusive >= PNG_SIGNATURE.size.toLong())
        assertTrue(record.rawRange.endExclusive <= bytes.size.toLong())
        assertEquals(record.rawRange.startInclusive + 8L, record.payloadRange.startInclusive)
        assertEquals(record.payloadRange.endExclusive + 4L, record.rawRange.endExclusive)

        val rawStart = record.rawRange.startInclusive.toInt()
        assertEquals(payload.size.toLong(), readU32BE(bytes, rawStart))
        assertEquals(type, String(bytes, rawStart + 4, 4, Charsets.US_ASCII))
        assertArrayEquals(
            payload,
            bytes.copyOfRange(record.payloadRange.startInclusive.toInt(), record.payloadRange.endExclusive.toInt()),
        )
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

    private fun assertOpenFailure(bytes: ByteArray, code: String) {
        val result = PngDocument.open(bytes)
        assertInstanceOf(PngDocumentOpenResult.Failure::class.java, result)
        assertEquals(code, (result as PngDocumentOpenResult.Failure).diagnostic.code)
    }

    private fun malformedStaticMetadataPng(type: String, payload: ByteArray): ByteArray = when (type) {
        "hIST" -> png(
            "IHDR" to ihdr(colorType = 3),
            "PLTE" to byteArrayOf(0, 0, 0),
            type to payload,
            "IDAT" to byteArrayOf(0x10),
            "IEND" to ByteArray(0),
        )

        "mDCV" -> png(
            "IHDR" to ihdr(),
            "cICP" to byteArrayOf(1, 13, 0, 1),
            type to payload,
            "IDAT" to byteArrayOf(0x10),
            "IEND" to ByteArray(0),
        )

        else -> png(
            "IHDR" to ihdr(),
            type to payload,
            "IDAT" to byteArrayOf(0x10),
            "IEND" to ByteArray(0),
        )
    }

    private fun metadataValueFor(document: PngDocument, type: String): PngMetadataValue<*> = when (type) {
        "iCCP" -> requireNotNull(document.iCCP)
        "sRGB" -> requireNotNull(document.sRGB)
        "gAMA" -> requireNotNull(document.gAMA)
        "cHRM" -> requireNotNull(document.cHRM)
        "cICP" -> requireNotNull(document.cICP)
        "mDCV" -> requireNotNull(document.mDCV)
        "cLLI" -> requireNotNull(document.cLLI)
        "eXIf" -> requireNotNull(document.eXIf)
        "pHYs" -> requireNotNull(document.pHYs)
        "tIME" -> requireNotNull(document.tIME)
        "tEXt" -> document.tEXt.single()
        "zTXt" -> document.zTXt.single()
        "iTXt" -> document.iTXt.single()
        "sBIT" -> requireNotNull(document.sBIT)
        "bKGD" -> requireNotNull(document.bKGD)
        "hIST" -> requireNotNull(document.hIST)
        "sPLT" -> document.sPLT.single()
        "tRNS" -> requireNotNull(document.tRNS)
        else -> error("Unexpected metadata type $type")
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> resolved(metadata: PngMetadataValue<T>?): T {
        val present = requireNotNull(metadata)
        assertInstanceOf(PngMetadataValue.Resolved::class.java, present)
        return (present as PngMetadataValue.Resolved<T>).value
    }

    private fun chromaticities(): ByteArray = intArrayOf(
        31_270,
        32_900,
        64_000,
        33_000,
        30_000,
        60_000,
        15_000,
        6_000,
    ).fold(ByteArray(0)) { bytes, value -> bytes + u32(value) }

    private fun masteringDisplay(): ByteArray = byteArrayOf(
        0x8A.toByte(), 0x48,
        0x39, 0x08,
        0x21, 0x34,
        0x9B.toByte(), 0xAA.toByte(),
        0x19, 0x96.toByte(),
        0x08, 0xFC.toByte(),
        0x3D, 0x13,
        0x40, 0x42,
    ) + u32(10_000_000) + u32(500)

    private fun suggestedPalette(name: String): ByteArray = suggestedPalette(name, intArrayOf(1))

    private fun suggestedPalette(name: String, frequencies: IntArray): ByteArray = ByteArrayOutputStream().apply {
        write(name.toByteArray(Charsets.ISO_8859_1))
        write(0)
        write(8)
        for (frequency in frequencies) {
            write(1)
            write(2)
            write(3)
            write(4)
            write((frequency ushr 8) and 0xFF)
            write(frequency and 0xFF)
        }
    }.toByteArray()

    private fun iccp(name: String, profile: ByteArray): ByteArray =
        name.toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0, 0) + deflate(profile)

    private fun textChunk(keyword: String, text: String): ByteArray =
        keyword.toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0) + text.toByteArray(Charsets.ISO_8859_1)

    private fun compressedTextChunk(keyword: String, text: String): ByteArray =
        keyword.toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0, 0) + deflate(text.toByteArray(Charsets.ISO_8859_1))

    private fun internationalTextChunk(
        keyword: String,
        languageTag: String,
        translatedKeyword: String,
        text: String,
        compressed: Boolean = false,
    ): ByteArray {
        val textBytes = text.toByteArray(Charsets.UTF_8)
        return keyword.toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0, if (compressed) 1 else 0, 0) +
            languageTag.toByteArray(Charsets.US_ASCII) + byteArrayOf(0) +
            translatedKeyword.toByteArray(Charsets.UTF_8) + byteArrayOf(0) +
            if (compressed) deflate(textBytes) else textBytes
    }

    private fun deflate(bytes: ByteArray): ByteArray = ByteArrayOutputStream().use { output ->
        val deflater = Deflater()
        deflater.setInput(bytes)
        deflater.finish()
        val buffer = ByteArray(256)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            output.write(buffer, 0, count)
        }
        deflater.end()
        output.toByteArray()
    }

    private fun u16(value: Int): ByteArray = byteArrayOf((value ushr 8).toByte(), value.toByte())

    private fun u32(value: Int): ByteArray = ByteArray(4).also { bytes -> writeI32BE(bytes, 0, value) }

    private fun png(vararg chunks: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().apply {
            write(PNG_SIGNATURE)
            for ((type, payload) in chunks) writeChunk(type, payload)
        }.toByteArray()

    private fun ihdr(
        width: Int = 1,
        height: Int = 1,
        bitDepth: Int = 8,
        colorType: Int = 2,
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

    private fun readU32BE(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toInt() and 0xFF).toLong() shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF).toLong() shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF).toLong() shl 8) or
            (bytes[offset + 3].toInt() and 0xFF).toLong()

    private companion object {
        val PNG_SIGNATURE: ByteArray = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
    }
}
