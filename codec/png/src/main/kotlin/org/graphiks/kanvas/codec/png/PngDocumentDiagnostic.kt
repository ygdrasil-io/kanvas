package org.graphiks.kanvas.codec.png

import java.util.Collections

public sealed interface PngDocumentOpenResult {
    public data class Success(public val document: PngDocument) : PngDocumentOpenResult

    public data class Failure(public val diagnostic: PngDiagnostic) : PngDocumentOpenResult
}

public class PngDocumentSaveResult internal constructor(
    bytes: ByteArray?,
    public val report: PngSaveReport,
    public val status: PngDocumentSaveStatus = PngDocumentSaveStatus.SAVED,
    public val diagnostic: PngDiagnostic? = null,
    sourceRecovery: ByteArray? = null,
) {
    private val savedOutputBytes: ByteArray? = bytes?.copyOf()
    private val savedSourceRecovery: ByteArray? = sourceRecovery?.copyOf()

    init {
        require(
            when (status) {
                PngDocumentSaveStatus.SAVED ->
                    savedOutputBytes != null && savedSourceRecovery == null && diagnostic == null

                PngDocumentSaveStatus.REFUSED -> savedOutputBytes == null && diagnostic != null
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
