package org.graphiks.kanvas.codec.png

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

public class PngDocument private constructor(
    private val sourceBytes: ByteArray,
    private val container: PngContainer,
    private val limits: PngContainerLimits,
    public val writePlan: PngWritePlan,
) {
    public val header: PngHeader = container.header

    public val chunks: List<PngChunkRecord> = container.chunks

    public val originalBytes: ByteArray
        get() = sourceBytes.copyOf()

    public fun withAncillaryChunk(type: String, payload: ByteArray): PngDocument {
        validateAncillaryType(type)
        if (payload.size.toLong() > limits.maxAncillaryChunkBytes) {
            throw PngDocumentEditException(
                "png.ancillary.limit",
                "PNG ancillary payload exceeds the document limit",
            )
        }
        val anchor = insertionAnchor(type)
        return copyWith(writePlan.withEdit(type, PngChunkEdit.Replacement(payload, anchor)))
    }

    public fun withoutChunks(type: String): PngDocument {
        validateAncillaryType(type)
        val hasOriginal = chunks.any { it.type == type }
        val currentEdit = writePlan.edits[type]
        if (!hasOriginal && currentEdit == null) return this
        if (!hasOriginal && currentEdit is PngChunkEdit.Replacement) {
            return copyWith(writePlan.withoutEdit(type))
        }
        return copyWith(writePlan.withEdit(type, PngChunkEdit.Removed))
    }

    public fun markPixelDataChanged(): PngDocument = when (writePlan.impact) {
        PngWriteImpact.CRITICAL -> this
        PngWriteImpact.NONE,
        PngWriteImpact.ANCILLARY,
        -> copyWith(writePlan.withCriticalImpact())
    }

    public fun save(): PngDocumentSaveResult = when (writePlan.impact) {
        PngWriteImpact.NONE -> PngDocumentSaveResult(
            bytes = sourceBytes,
            report = PngSaveReport(),
        )

        PngWriteImpact.ANCILLARY -> saveAncillaryEdits()
        PngWriteImpact.CRITICAL -> refuseCriticalSave()
    }

    private fun saveAncillaryEdits(): PngDocumentSaveResult {
        val output = ByteArrayOutputStream(sourceBytes.size)
        output.write(sourceBytes, 0, PNG_SIGNATURE_BYTES)
        val emittedReplacements = HashSet<String>()
        val reportEntries = ArrayList<PngSaveReportEntry>()
        val originalTypes = chunks.mapTo(HashSet(), PngChunkRecord::type)

        for (record in chunks) {
            emitAbsentReplacementsBefore(record, originalTypes, output, emittedReplacements, reportEntries)
            when (val edit = writePlan.edits[record.type]) {
                is PngChunkEdit.Replacement -> {
                    if (emittedReplacements.add(record.type)) {
                        output.writeChunk(record.type, edit.payloadCopy())
                        reportEntries += PngSaveReportEntry(
                            chunkType = record.type,
                            ordinal = record.ordinal,
                            status = PngSaveEntryStatus.REPLACED,
                            reasonCode = PngSaveReason.ANCILLARY_REPLACED,
                        )
                    }
                }

                PngChunkEdit.Removed -> reportEntries += PngSaveReportEntry(
                    chunkType = record.type,
                    ordinal = record.ordinal,
                    status = PngSaveEntryStatus.DROPPED,
                    reasonCode = PngSaveReason.ANCILLARY_REMOVED,
                )

                null -> {
                    output.writeRaw(record)
                    if (record.isAncillary) {
                        reportEntries += PngSaveReportEntry(
                            chunkType = record.type,
                            ordinal = record.ordinal,
                            status = PngSaveEntryStatus.PRESERVED,
                            reasonCode = PngSaveReason.METADATA_ONLY_PRESERVED,
                        )
                    }
                }
            }
        }

        check(emittedReplacements.containsAll(writePlan.edits.filterValues { it is PngChunkEdit.Replacement }.keys)) {
            "Every PNG replacement must have a valid insertion anchor"
        }
        return PngDocumentSaveResult(
            bytes = output.toByteArray(),
            report = PngSaveReport(reportEntries),
        )
    }

    private fun emitAbsentReplacementsBefore(
        record: PngChunkRecord,
        originalTypes: Set<String>,
        output: ByteArrayOutputStream,
        emitted: MutableSet<String>,
        reportEntries: MutableList<PngSaveReportEntry>,
    ) {
        if (record.type !in INSERTION_BOUNDARY_TYPES) return
        for ((type, edit) in writePlan.edits) {
            if (edit !is PngChunkEdit.Replacement || type in emitted || type in originalTypes) continue
            val insertionType = when (edit.anchor) {
                PngChunkInsertionAnchor.BEFORE_PLTE -> "PLTE"
                PngChunkInsertionAnchor.BEFORE_IDAT -> "IDAT"
                PngChunkInsertionAnchor.AFTER_IDAT -> "IEND"
            }
            if (record.type != insertionType) continue
            output.writeChunk(type, edit.payloadCopy())
            emitted += type
            reportEntries += PngSaveReportEntry(
                chunkType = type,
                ordinal = null,
                status = PngSaveEntryStatus.REPLACED,
                reasonCode = PngSaveReason.ANCILLARY_REPLACED,
            )
        }
    }

    private fun refuseCriticalSave(): PngDocumentSaveResult {
        val reportEntries = logicalAncillaryChunks().map { (type, ordinal) ->
            val (status, reason) = PngAncillaryCriticalPolicy.decision(type)
            PngSaveReportEntry(type, ordinal, status, reason)
        }.toMutableList()
        reportEntries += PngSaveReportEntry(
            chunkType = null,
            ordinal = null,
            status = PngSaveEntryStatus.REFUSED,
            reasonCode = PngSaveReason.PIXEL_REENCODE_UNSUPPORTED,
        )
        return PngDocumentSaveResult(
            bytes = sourceBytes,
            report = PngSaveReport(reportEntries),
            status = PngDocumentSaveStatus.REFUSED,
            diagnostic = PngDiagnostic(
                code = PngSaveReason.PIXEL_REENCODE_UNSUPPORTED,
                offset = 0L,
                message = "PngDocument cannot re-encode pixel or critical chunk edits",
            ),
        )
    }

    private fun logicalAncillaryChunks(): List<Pair<String, Int?>> {
        val logical = ArrayList<Pair<String, Int?>>()
        val emitted = HashSet<String>()
        for (record in chunks) {
            when (val edit = writePlan.edits[record.type]) {
                is PngChunkEdit.Replacement -> if (emitted.add(record.type)) {
                    logical += record.type to record.ordinal
                }

                PngChunkEdit.Removed -> Unit
                null -> if (record.isAncillary) logical += record.type to record.ordinal
            }
        }
        for ((type, edit) in writePlan.edits) {
            if (edit is PngChunkEdit.Replacement && emitted.add(type)) logical += type to null
        }
        return logical
    }

    private fun insertionAnchor(type: String): PngChunkInsertionAnchor {
        val records = chunks.filter { it.type == type }
        val paletteOrdinal = chunks.firstOrNull { it.type == "PLTE" }?.ordinal
        if (records.isEmpty()) {
            return if (paletteOrdinal != null && type in PRE_PALETTE_TYPES) {
                PngChunkInsertionAnchor.BEFORE_PLTE
            } else {
                PngChunkInsertionAnchor.BEFORE_IDAT
            }
        }
        val firstIdatOrdinal = chunks.first { it.type == "IDAT" }.ordinal
        val anchors = records.mapTo(LinkedHashSet()) { record ->
            when {
                record.ordinal > firstIdatOrdinal -> PngChunkInsertionAnchor.AFTER_IDAT
                paletteOrdinal != null && record.ordinal < paletteOrdinal -> PngChunkInsertionAnchor.BEFORE_PLTE
                else -> PngChunkInsertionAnchor.BEFORE_IDAT
            }
        }
        if (anchors.size != 1) {
            throw PngDocumentEditException(
                "png.ancillary.anchor.ambiguous",
                "Matching PNG chunks span multiple critical insertion anchors",
            )
        }
        val anchor = anchors.single()
        val invalidKnownAnchor =
            (paletteOrdinal != null && type in PRE_PALETTE_TYPES && anchor != PngChunkInsertionAnchor.BEFORE_PLTE) ||
                ((type in PRE_PALETTE_TYPES || type in PRE_IDAT_TYPES) &&
                    anchor == PngChunkInsertionAnchor.AFTER_IDAT)
        if (invalidKnownAnchor) {
            throw PngDocumentEditException(
                "png.ancillary.anchor.invalid",
                "PNG chunk $type is outside its required critical insertion anchor",
            )
        }
        return anchor
    }

    private fun validateAncillaryType(type: String) {
        if (type.length != 4 || !type.all { it in 'A'..'Z' || it in 'a'..'z' }) {
            throw PngDocumentEditException(
                "png.chunk.type.invalid",
                "PNG chunk types must contain exactly four ASCII letters",
            )
        }
        if (type[2] !in 'A'..'Z') {
            throw PngDocumentEditException(
                "png.chunk.type.reserved",
                "PNG chunk type reserved bit must be uppercase",
            )
        }
        if (type[0] !in 'a'..'z') {
            throw PngDocumentEditException(
                "png.chunk.type.critical",
                "Raw critical PNG replacement or removal is not supported",
            )
        }
        if (type in APNG_CHUNK_TYPES) {
            throw PngDocumentEditException("png.apng.unsupported", "APNG is outside the static PNG scope")
        }
    }

    private fun copyWith(plan: PngWritePlan): PngDocument = PngDocument(
        sourceBytes = sourceBytes,
        container = container,
        limits = limits,
        writePlan = plan,
    )

    private fun ByteArrayOutputStream.writeRaw(record: PngChunkRecord) {
        val start = record.rawRange.startInclusive.toInt()
        write(sourceBytes, start, record.rawRange.size.toInt())
    }

    private fun ByteArrayOutputStream.writeChunk(type: String, payload: ByteArray) {
        writeI32(payload.size)
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        write(typeBytes)
        write(payload)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(payload)
        writeI32(crc.value.toInt())
    }

    private fun ByteArrayOutputStream.writeI32(value: Int) {
        write((value ushr 24) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 8) and 0xFF)
        write(value and 0xFF)
    }

    public companion object {
        public fun open(
            bytes: ByteArray,
            limits: PngContainerLimits = PngContainerLimits.Default,
        ): PngDocumentOpenResult = open(bytes, limits) { it.copyOf() }

        internal fun open(
            bytes: ByteArray,
            limits: PngContainerLimits,
            snapshot: (ByteArray) -> ByteArray,
        ): PngDocumentOpenResult {
            if (bytes.size.toLong() > limits.maxInputBytes) {
                return PngDocumentOpenResult.Failure(
                    PngDiagnostic(
                        code = "png.input.limit",
                        offset = 0L,
                        message = "PNG input exceeds the configured byte limit",
                    ),
                )
            }

            val sourceBytes = snapshot(bytes)
            return when (val result = PngContainerParser.parse(sourceBytes, limits)) {
                is PngContainerParseResult.Success -> PngDocumentOpenResult.Success(
                    PngDocument(sourceBytes, result.container, limits, PngWritePlan.None),
                )

                is PngContainerParseResult.Failure -> PngDocumentOpenResult.Failure(result.diagnostic)
            }
        }

        private const val PNG_SIGNATURE_BYTES: Int = 8
        private val APNG_CHUNK_TYPES: Set<String> = setOf("acTL", "fcTL", "fdAT")
        private val INSERTION_BOUNDARY_TYPES: Set<String> = setOf("PLTE", "IDAT", "IEND")
        private val PRE_PALETTE_TYPES: Set<String> = setOf("cHRM", "cICP", "gAMA", "iCCP", "sBIT", "sRGB")
        private val PRE_IDAT_TYPES: Set<String> = setOf(
            "bKGD",
            "cLLI",
            "eXIf",
            "hIST",
            "mDCV",
            "pHYs",
            "sPLT",
            "tRNS",
        )
    }
}
