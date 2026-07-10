package org.graphiks.kanvas.codec.png

import java.util.Collections

public sealed interface PngDocumentOpenResult {
    public data class Success(public val document: PngDocument) : PngDocumentOpenResult

    public data class Failure(public val diagnostic: PngDiagnostic) : PngDocumentOpenResult
}

public class PngDocumentSaveResult private constructor(
    private val savedOutputBytes: ByteArray?,
    private val savedSourceRecovery: ByteArray?,
    public val report: PngSaveReport,
    public val status: PngDocumentSaveStatus,
    public val diagnostic: PngDiagnostic?,
) {
    init {
        require(
            when (status) {
                PngDocumentSaveStatus.SAVED ->
                    savedOutputBytes != null && savedSourceRecovery == null && diagnostic == null

                PngDocumentSaveStatus.REFUSED ->
                    savedOutputBytes == null && savedSourceRecovery != null && diagnostic != null
            },
        ) {
            "Saved PNG results own output bytes; refused results own no output and require a diagnostic"
        }
    }

    public val bytes: ByteArray
        get() = outputBytes ?: throw PngSaveOutputUnavailableException()

    public val outputBytes: ByteArray?
        get() = savedOutputBytes?.copyOf()

    public val sourceRecovery: ByteArray?
        get() = savedSourceRecovery?.copyOf()

    public val isSuccess: Boolean
        get() = status == PngDocumentSaveStatus.SAVED

    internal fun referencesSourceRecoverySnapshot(snapshot: ByteArray): Boolean =
        savedSourceRecovery === snapshot

    internal companion object {
        fun saved(bytes: ByteArray, report: PngSaveReport): PngDocumentSaveResult = PngDocumentSaveResult(
            savedOutputBytes = bytes.copyOf(),
            savedSourceRecovery = null,
            report = report,
            status = PngDocumentSaveStatus.SAVED,
            diagnostic = null,
        )

        fun refused(
            ownedSourceSnapshot: ByteArray,
            report: PngSaveReport,
            diagnostic: PngDiagnostic,
        ): PngDocumentSaveResult = PngDocumentSaveResult(
            savedOutputBytes = null,
            savedSourceRecovery = ownedSourceSnapshot,
            report = report,
            status = PngDocumentSaveStatus.REFUSED,
            diagnostic = diagnostic,
        )
    }
}

public class PngSaveOutputUnavailableException internal constructor() : IllegalStateException(
    "A refused PNG save has no publishable output bytes; inspect sourceRecovery explicitly",
) {
    public val code: String = PngSaveReason.OUTPUT_UNAVAILABLE
}

public enum class PngDocumentSaveStatus {
    SAVED,
    REFUSED,
}

public class PngSaveReport internal constructor(entries: List<PngSaveReportEntry> = emptyList()) {
    public val entries: List<PngSaveReportEntry> = Collections.unmodifiableList(ArrayList(entries))

    public val isEmpty: Boolean
        get() = entries.isEmpty()
}
