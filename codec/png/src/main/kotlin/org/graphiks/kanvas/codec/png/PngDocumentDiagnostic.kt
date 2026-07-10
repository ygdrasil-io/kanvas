package org.graphiks.kanvas.codec.png

import java.util.Collections

public sealed interface PngDocumentOpenResult {
    public data class Success(public val document: PngDocument) : PngDocumentOpenResult

    public data class Failure(public val diagnostic: PngDiagnostic) : PngDocumentOpenResult
}

public class PngDocumentSaveResult internal constructor(
    bytes: ByteArray,
    public val report: PngSaveReport,
    public val status: PngDocumentSaveStatus = PngDocumentSaveStatus.SAVED,
    public val diagnostic: PngDiagnostic? = null,
) {
    private val savedBytes: ByteArray = bytes.copyOf()

    init {
        require((status == PngDocumentSaveStatus.SAVED) == (diagnostic == null)) {
            "A saved PNG must not have a diagnostic and a refused PNG must have one"
        }
    }

    public val bytes: ByteArray
        get() = savedBytes.copyOf()

    public val isSuccess: Boolean
        get() = status == PngDocumentSaveStatus.SAVED
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
