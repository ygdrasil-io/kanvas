package org.graphiks.kanvas.codec.png

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

public class PngDocument private constructor(
    private val sourceBytes: ByteArray,
    private val container: PngContainer,
    private val limits: PngContainerLimits,
    /** Typed metadata for the bytes that [save] will emit for the current write plan. */
    public val metadata: PngMetadata,
    public val writePlan: PngWritePlan,
) {
    public val header: PngHeader = container.header

    public val chunks: List<PngChunkRecord> = container.chunks

    public val originalBytes: ByteArray
        get() = sourceBytes.copyOf()

    public val iCCP: PngMetadataValue<PngIccProfileMetadata>?
        get() = metadata.iCCP

    public val sRGB: PngMetadataValue<PngSrgbMetadata>?
        get() = metadata.sRGB

    public val gAMA: PngMetadataValue<PngGammaMetadata>?
        get() = metadata.gAMA

    public val cHRM: PngMetadataValue<PngChromaticitiesMetadata>?
        get() = metadata.cHRM

    public val cICP: PngMetadataValue<PngCicpMetadata>?
        get() = metadata.cICP

    public val mDCV: PngMetadataValue<PngMasteringDisplayColorVolumeMetadata>?
        get() = metadata.mDCV

    public val cLLI: PngMetadataValue<PngContentLightLevelMetadata>?
        get() = metadata.cLLI

    public val eXIf: PngMetadataValue<PngExifMetadata>?
        get() = metadata.eXIf

    public val pHYs: PngMetadataValue<PngPhysicalPixelDimensions>?
        get() = metadata.pHYs

    public val tIME: PngMetadataValue<PngModificationTime>?
        get() = metadata.tIME

    public val tEXt: List<PngMetadataValue<PngTextMetadata>>
        get() = metadata.tEXt

    public val zTXt: List<PngMetadataValue<PngTextMetadata>>
        get() = metadata.zTXt

    public val iTXt: List<PngMetadataValue<PngInternationalTextMetadata>>
        get() = metadata.iTXt

    public val sBIT: PngMetadataValue<PngSignificantBitsMetadata>?
        get() = metadata.sBIT

    public val bKGD: PngMetadataValue<PngBackgroundColorMetadata>?
        get() = metadata.bKGD

    public val hIST: PngMetadataValue<PngHistogramMetadata>?
        get() = metadata.hIST

    public val sPLT: List<PngMetadataValue<PngSuggestedPaletteMetadata>>
        get() = metadata.sPLT

    public val tRNS: PngMetadataValue<PngTransparencyMetadata>?
        get() = metadata.tRNS

    public fun withAncillaryChunk(type: String, payload: ByteArray): PngDocument {
        validateAncillaryType(type)
        if (payload.size.toLong() > limits.maxAncillaryChunkBytes) {
            throw PngDocumentEditException(
                "png.ancillary.limit",
                "PNG ancillary payload exceeds the document limit",
            )
        }
        val records = chunks.filter { it.type == type }
        if (records.size > 1) {
            throw PngDocumentEditException(
                "png.ancillary.replacement.ambiguous",
                "Raw replacement requires zero or one matching PNG chunk",
            )
        }
        if (records.isEmpty() && type[3] !in 'a'..'z' && !PngAncillaryCriticalPolicy.isKnown(type)) {
            throw PngDocumentEditException(
                "png.ancillary.unsafe.anchor.required",
                "An absent unknown unsafe-to-copy PNG chunk requires an explicit insertion anchor",
            )
        }
        val anchor = insertionAnchor(type, records)
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
        PngWriteImpact.NONE -> PngDocumentSaveResult.saved(
            bytes = sourceBytes,
            report = PngSaveReport(),
        )

        PngWriteImpact.ANCILLARY -> saveAncillaryEdits()
        PngWriteImpact.CRITICAL -> refuseCriticalSave()
    }

    private fun saveAncillaryEdits(): PngDocumentSaveResult {
        val preflight = preflightAncillaryOutput()
        if (preflight is PngOutputPreflight.Failure) {
            return refuseSave(preflight.diagnostic)
        }
        preflight as PngOutputPreflight.Success
        val output = ByteArrayOutputStream(preflight.outputSize)
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
        return PngDocumentSaveResult.saved(
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
        val reportEntries = criticalAncillaryReportEntries()
        reportEntries += PngSaveReportEntry(
            chunkType = null,
            ordinal = null,
            status = PngSaveEntryStatus.REFUSED,
            reasonCode = PngSaveReason.PIXEL_REENCODE_UNSUPPORTED,
        )
        return refuseSave(
            diagnostic = PngDiagnostic(
                code = PngSaveReason.PIXEL_REENCODE_UNSUPPORTED,
                offset = 0L,
                message = "PngDocument cannot re-encode pixel or critical chunk edits",
            ),
            reportEntries = reportEntries,
        )
    }

    private fun preflightAncillaryOutput(): PngOutputPreflight {
        val recordsByType = chunks.groupBy(PngChunkRecord::type)
        var outputSize = sourceBytes.size.toLong()
        var outputChunkCount = chunks.size.toLong()
        for ((type, edit) in writePlan.edits) {
            val records = recordsByType[type].orEmpty()
            val originalBytes = records.sumOf { it.rawRange.size }
            outputSize -= originalBytes
            outputChunkCount -= records.size.toLong()
            if (edit is PngChunkEdit.Replacement) {
                outputSize += PNG_CHUNK_OVERHEAD_BYTES + edit.payloadSize.toLong()
                outputChunkCount += 1L
            }
        }
        if (outputChunkCount > limits.maxChunkCount.toLong()) {
            return PngOutputPreflight.Failure(
                PngDiagnostic(
                    code = PngSaveReason.OUTPUT_CHUNK_COUNT_LIMIT,
                    offset = 0L,
                    message = "Edited PNG chunk count exceeds the configured limit",
                ),
            )
        }
        if (outputSize > limits.maxInputBytes || outputSize > Int.MAX_VALUE.toLong()) {
            return PngOutputPreflight.Failure(
                PngDiagnostic(
                    code = PngSaveReason.OUTPUT_LIMIT,
                    offset = 0L,
                    message = "Edited PNG output exceeds the configured byte limit",
                ),
            )
        }
        return PngOutputPreflight.Success(outputSize.toInt())
    }

    private fun refuseSave(
        diagnostic: PngDiagnostic,
        reportEntries: List<PngSaveReportEntry> = listOf(
            PngSaveReportEntry(
                chunkType = null,
                ordinal = null,
                status = PngSaveEntryStatus.REFUSED,
                reasonCode = diagnostic.code,
            ),
        ),
    ): PngDocumentSaveResult = PngDocumentSaveResult.refused(
        ownedSourceSnapshot = sourceBytes,
        report = PngSaveReport(reportEntries),
        diagnostic = diagnostic,
    )

    private fun criticalAncillaryReportEntries(): MutableList<PngSaveReportEntry> {
        val reportEntries = ArrayList<PngSaveReportEntry>()
        val emitted = HashSet<String>()
        for (record in chunks) {
            when (val edit = writePlan.edits[record.type]) {
                is PngChunkEdit.Replacement -> if (emitted.add(record.type)) {
                    reportEntries += criticalPolicyEntry(record.type, record.ordinal)
                }

                PngChunkEdit.Removed -> reportEntries += PngSaveReportEntry(
                    chunkType = record.type,
                    ordinal = record.ordinal,
                    status = PngSaveEntryStatus.PLANNED_DROP,
                    reasonCode = PngSaveReason.ANCILLARY_REMOVED,
                )

                null -> if (record.isAncillary) {
                    reportEntries += criticalPolicyEntry(record.type, record.ordinal)
                }
            }
        }
        for ((type, edit) in writePlan.edits) {
            if (edit is PngChunkEdit.Replacement && emitted.add(type)) {
                reportEntries += criticalPolicyEntry(type, null)
            }
        }
        return reportEntries
    }

    private fun criticalPolicyEntry(type: String, ordinal: Int?): PngSaveReportEntry {
        val (status, reason) = PngAncillaryCriticalPolicy.decision(type)
        return PngSaveReportEntry(type, ordinal, status, reason)
    }

    private fun insertionAnchor(
        type: String,
        records: List<PngChunkRecord>,
    ): PngChunkInsertionAnchor {
        val paletteOrdinal = chunks.firstOrNull { it.type == "PLTE" }?.ordinal
        if (type == "hIST" && paletteOrdinal == null) {
            throw PngDocumentEditException(
                "png.ancillary.anchor.requires-plte",
                "PNG hIST requires a preceding PLTE chunk",
            )
        }
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
                (paletteOrdinal != null && type in POST_PALETTE_TYPES && anchor != PngChunkInsertionAnchor.BEFORE_IDAT) ||
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
        metadata = metadataFor(plan),
        writePlan = plan,
    )

    private fun metadataFor(plan: PngWritePlan): PngMetadata {
        if (plan.impact != PngWriteImpact.ANCILLARY) return metadata
        val projected = PngDocument(
            sourceBytes = sourceBytes,
            container = container,
            limits = limits,
            metadata = metadata,
            writePlan = plan,
        ).save()
        if (projected.status != PngDocumentSaveStatus.SAVED) return metadata
        val outputBytes = projected.bytes
        return when (val parsed = PngContainerParser.parse(outputBytes, limits)) {
            is PngContainerParseResult.Success -> PngMetadataParser.parse(outputBytes, parsed.container, limits.metadata)
            is PngContainerParseResult.Failure -> metadata
        }
    }

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
                    PngDocument(
                        sourceBytes = sourceBytes,
                        container = result.container,
                        limits = limits,
                        metadata = PngMetadataParser.parse(sourceBytes, result.container, limits.metadata),
                        writePlan = PngWritePlan.None,
                    ),
                )

                is PngContainerParseResult.Failure -> PngDocumentOpenResult.Failure(result.diagnostic)
            }
        }

        private const val PNG_SIGNATURE_BYTES: Int = 8
        private const val PNG_CHUNK_OVERHEAD_BYTES: Long = 12L
        private val APNG_CHUNK_TYPES: Set<String> = setOf("acTL", "fcTL", "fdAT")
        private val INSERTION_BOUNDARY_TYPES: Set<String> = setOf("PLTE", "IDAT", "IEND")
        private val PRE_PALETTE_TYPES: Set<String> = setOf(
            "cHRM",
            "cICP",
            "cLLI",
            "gAMA",
            "iCCP",
            "mDCV",
            "sBIT",
            "sRGB",
        )
        private val POST_PALETTE_TYPES: Set<String> = setOf("bKGD", "hIST", "tRNS")
        private val PRE_IDAT_TYPES: Set<String> = setOf(
            "bKGD",
            "eXIf",
            "hIST",
            "pHYs",
            "sPLT",
            "tRNS",
        )
    }

    private sealed interface PngOutputPreflight {
        data class Success(val outputSize: Int) : PngOutputPreflight

        data class Failure(val diagnostic: PngDiagnostic) : PngOutputPreflight
    }
}
