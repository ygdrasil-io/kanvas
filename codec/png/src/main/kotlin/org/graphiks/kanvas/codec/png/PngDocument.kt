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
                    PngDocument(sourceBytes, result.container),
                )

                is PngContainerParseResult.Failure -> PngDocumentOpenResult.Failure(result.diagnostic)
            }
        }
    }
}
