package org.graphiks.kanvas.codec.png

public sealed interface PngDocumentOpenResult {
    public data class Success(public val document: PngDocument) : PngDocumentOpenResult

    public data class Failure(public val diagnostic: PngDiagnostic) : PngDocumentOpenResult
}

public class PngDocumentSaveResult internal constructor(
    bytes: ByteArray,
    public val report: PngSaveReport,
) {
    private val savedBytes: ByteArray = bytes.copyOf()

    public val bytes: ByteArray
        get() = savedBytes.copyOf()
}

public class PngSaveReport internal constructor() {
    public val isEmpty: Boolean
        get() = true
}
