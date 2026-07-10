package org.graphiks.kanvas.codec.png

import java.util.Collections

public enum class PngWriteImpact {
    NONE,
    ANCILLARY,
    CRITICAL,
}

public enum class PngSaveEntryStatus {
    PRESERVED,
    REPLACED,
    DROPPED,
    REFUSED,
}

public data class PngSaveReportEntry(
    public val chunkType: String?,
    public val ordinal: Int?,
    public val status: PngSaveEntryStatus,
    public val reasonCode: String,
)

public object PngSaveReason {
    public const val ANCILLARY_REPLACED: String = "png.ancillary.replaced"
    public const val ANCILLARY_REMOVED: String = "png.ancillary.removed"
    public const val METADATA_ONLY_PRESERVED: String = "png.ancillary.preserved.metadata-only"
    public const val SAFE_TO_COPY_PRESERVED: String = "png.ancillary.preserved.safe-to-copy"
    public const val KNOWN_INDEPENDENT_PRESERVED: String = "png.ancillary.preserved.known-independent"
    public const val UNSAFE_TO_COPY_DROPPED: String = "png.ancillary.dropped.unsafe-to-copy"
    public const val IMAGE_DEPENDENT_DROPPED: String = "png.ancillary.dropped.image-dependent"
    public const val STALE_DROPPED: String = "png.ancillary.dropped.stale"
    public const val PIXEL_REENCODE_UNSUPPORTED: String = "png.pixel-edit.reencode.unsupported"
}

public class PngDocumentEditException(
    public val code: String,
    message: String,
) : IllegalArgumentException(message)

public class PngWritePlan internal constructor(
    public val impact: PngWriteImpact,
    edits: Map<String, PngChunkEdit>,
) {
    internal val edits: Map<String, PngChunkEdit> = Collections.unmodifiableMap(LinkedHashMap(edits))

    public val editedChunkTypes: Set<String> = Collections.unmodifiableSet(LinkedHashSet(edits.keys))

    internal fun withEdit(type: String, edit: PngChunkEdit): PngWritePlan {
        val updated = LinkedHashMap(edits)
        updated[type] = edit
        val updatedImpact = if (impact == PngWriteImpact.CRITICAL) impact else PngWriteImpact.ANCILLARY
        return PngWritePlan(updatedImpact, updated)
    }

    internal fun withoutEdit(type: String): PngWritePlan {
        val updated = LinkedHashMap(edits)
        updated.remove(type)
        val updatedImpact = when {
            impact == PngWriteImpact.CRITICAL -> impact
            updated.isEmpty() -> PngWriteImpact.NONE
            else -> PngWriteImpact.ANCILLARY
        }
        return PngWritePlan(updatedImpact, updated)
    }

    internal fun withCriticalImpact(): PngWritePlan = PngWritePlan(PngWriteImpact.CRITICAL, edits)

    internal companion object {
        val None: PngWritePlan = PngWritePlan(PngWriteImpact.NONE, emptyMap())
    }
}

internal enum class PngChunkInsertionAnchor {
    BEFORE_PLTE,
    BEFORE_IDAT,
    AFTER_IDAT,
}

internal sealed interface PngChunkEdit {
    class Replacement(
        payload: ByteArray,
        val anchor: PngChunkInsertionAnchor,
    ) : PngChunkEdit {
        private val savedPayload: ByteArray = payload.copyOf()

        fun payloadCopy(): ByteArray = savedPayload.copyOf()
    }

    data object Removed : PngChunkEdit
}

internal object PngAncillaryCriticalPolicy {
    private val imageDependentTypes: Set<String> = setOf(
        "bKGD",
        "cHRM",
        "cICP",
        "cLLI",
        "gAMA",
        "hIST",
        "iCCP",
        "mDCV",
        "sBIT",
        "sRGB",
        "tRNS",
    )

    private val knownIndependentTypes: Set<String> = setOf(
        "eXIf",
        "iTXt",
        "pHYs",
        "sPLT",
        "tEXt",
        "zTXt",
    )

    fun decision(type: String): Pair<PngSaveEntryStatus, String> = when {
        type in imageDependentTypes ->
            PngSaveEntryStatus.DROPPED to PngSaveReason.IMAGE_DEPENDENT_DROPPED

        type == "tIME" -> PngSaveEntryStatus.DROPPED to PngSaveReason.STALE_DROPPED
        type in knownIndependentTypes ->
            PngSaveEntryStatus.PRESERVED to PngSaveReason.KNOWN_INDEPENDENT_PRESERVED

        type[3] in 'a'..'z' ->
            PngSaveEntryStatus.PRESERVED to PngSaveReason.SAFE_TO_COPY_PRESERVED

        else -> PngSaveEntryStatus.DROPPED to PngSaveReason.UNSAFE_TO_COPY_DROPPED
    }
}
