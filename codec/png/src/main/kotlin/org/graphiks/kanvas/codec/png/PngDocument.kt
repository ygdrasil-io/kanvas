package org.graphiks.kanvas.codec.png

public class PngDocument private constructor(
    private val sourceBytes: ByteArray,
    container: PngContainer,
) {
    public val header: PngHeader = container.header

    public val chunks: List<PngChunkRecord> = container.chunks

    public val originalBytes: ByteArray
        get() = sourceBytes.copyOf()

    public fun save(): PngDocumentSaveResult = PngDocumentSaveResult(
        bytes = sourceBytes,
        report = PngSaveReport(),
    )

    public companion object {
        public fun open(
            bytes: ByteArray,
            limits: PngContainerLimits = PngContainerLimits.Default,
        ): PngDocumentOpenResult {
            val sourceBytes = bytes.copyOf()
            return when (val result = PngContainerParser.parse(sourceBytes, limits)) {
                is PngContainerParseResult.Success -> PngDocumentOpenResult.Success(
                    PngDocument(sourceBytes, result.container),
                )

                is PngContainerParseResult.Failure -> PngDocumentOpenResult.Failure(result.diagnostic)
            }
        }
    }
}
