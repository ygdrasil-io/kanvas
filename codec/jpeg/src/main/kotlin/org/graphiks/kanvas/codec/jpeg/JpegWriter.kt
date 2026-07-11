package org.graphiks.kanvas.codec.jpeg

/** JPEG processes accepted by the static encoder API. Task 8 writes only [SequentialHuffman]. */
public enum class JpegEncodeProcess {
    SequentialHuffman,
    SequentialArithmetic,
    ProgressiveHuffman,
    ProgressiveArithmetic,
    LosslessHuffman,
    LosslessArithmetic,
    DifferentialSequentialHuffman,
    DifferentialSequentialArithmetic,
    DifferentialProgressiveHuffman,
    DifferentialProgressiveArithmetic,
    DifferentialLosslessHuffman,
    DifferentialLosslessArithmetic,
}

/** Defines how a source alpha channel is projected onto opaque JPEG samples. */
public enum class JpegAlphaPolicy {
    Ignore,
    BlendOnBlack,
}

/** JPEG component sampling factors, constrained by the SOF 4-bit fields. */
public data class JpegSamplingFactor(
    val horizontal: Int,
    val vertical: Int,
) {
    init {
        require(horizontal in 1..4) { "horizontal sampling factor must be in [1, 4], got $horizontal" }
        require(vertical in 1..4) { "vertical sampling factor must be in [1, 4], got $vertical" }
    }
}

/** Sampling factors for the Y, Cb and Cr components emitted by this RGB encoder. */
public class JpegSampling(components: List<JpegSamplingFactor>) {
    /** Immutable snapshot of factors supplied when this configuration was built. */
    private val componentsStorage: List<JpegSamplingFactor> = components.toList()
    public val components: List<JpegSamplingFactor> get() = componentsStorage.toList()

    init {
        require(componentsStorage.size == 3) { "JPEG RGB encoding requires exactly three sampling factors" }
    }

    public companion object {
        public val S444: JpegSampling = JpegSampling(List(3) { JpegSamplingFactor(1, 1) })
        public val S422: JpegSampling = JpegSampling(
            listOf(JpegSamplingFactor(2, 1), JpegSamplingFactor(1, 1), JpegSamplingFactor(1, 1)),
        )
        public val S420: JpegSampling = JpegSampling(
            listOf(JpegSamplingFactor(2, 2), JpegSamplingFactor(1, 1), JpegSamplingFactor(1, 1)),
        )
    }
}

/**
 * Raw metadata payloads written by the encoder.
 *
 * [icc] is a complete ICC profile, [exif] is TIFF data without the `Exif\\0\\0`
 * identifier, [xmp] is packet data without the XMP identifier, and [comment]
 * is the COM payload. The optional Adobe transform is emitted as APP14. This
 * three-component YCbCr writer accepts only Adobe transform 1.
 */
public class JpegEncodeMetadata(
    icc: ByteArray? = null,
    exif: ByteArray? = null,
    xmp: ByteArray? = null,
    comment: ByteArray? = null,
    public val adobeTransform: Int? = null,
) {
    /** Immutable snapshots; callers cannot mutate bytes after configuring an encode. */
    private val iccStorage: ByteArray? = icc?.copyOf()
    private val exifStorage: ByteArray? = exif?.copyOf()
    private val xmpStorage: ByteArray? = xmp?.copyOf()
    private val commentStorage: ByteArray? = comment?.copyOf()
    public val icc: ByteArray? get() = iccStorage?.copyOf()
    public val exif: ByteArray? get() = exifStorage?.copyOf()
    public val xmp: ByteArray? get() = xmpStorage?.copyOf()
    public val comment: ByteArray? get() = commentStorage?.copyOf()

    init {
        require(exifStorage == null || exifStorage.size <= JpegWriterLimits.MAX_EXIF_BYTES) { "EXIF payload is too large" }
        require(xmpStorage == null || xmpStorage.size <= JpegWriterLimits.MAX_XMP_BYTES) { "XMP payload is too large" }
        require(commentStorage == null || commentStorage.size <= JpegWriterLimits.MAX_SEGMENT_PAYLOAD) { "COM payload is too large" }
        require(iccStorage == null || iccStorage.size <= JpegWriterLimits.MAX_ICC_BYTES) { "ICC profile is too large" }
        require(iccStorage == null || iccStorage.isNotEmpty()) { "ICC profile must not be empty" }
        require(adobeTransform == null || adobeTransform == 1) {
            "this YCbCr encoder supports only Adobe transform 1"
        }
    }
}

/** Reserved Task 9 hierarchy declaration. Task 8 accepts it only to refuse encoding explicitly. */
public data class JpegHierarchyLevel(
    val scaleNumerator: Int,
    val scaleDenominator: Int,
    val process: JpegEncodeProcess,
) {
    init {
        require(scaleNumerator > 0) { "hierarchy scale numerator must be positive" }
        require(scaleDenominator > 0) { "hierarchy scale denominator must be positive" }
    }
}

internal object JpegWriterLimits {
    const val MAX_SEGMENT_PAYLOAD: Int = 65_533
    const val ICC_SIGNATURE_BYTES: Int = 14
    const val MAX_ICC_CHUNK_BYTES: Int = MAX_SEGMENT_PAYLOAD - ICC_SIGNATURE_BYTES
    const val MAX_ICC_BYTES: Int = MAX_ICC_CHUNK_BYTES * 255
    const val EXIF_SIGNATURE_BYTES: Int = 6
    const val XMP_SIGNATURE_BYTES: Int = 29
    const val MAX_EXIF_BYTES: Int = MAX_SEGMENT_PAYLOAD - EXIF_SIGNATURE_BYTES
    const val MAX_XMP_BYTES: Int = MAX_SEGMENT_PAYLOAD - XMP_SIGNATURE_BYTES
}
