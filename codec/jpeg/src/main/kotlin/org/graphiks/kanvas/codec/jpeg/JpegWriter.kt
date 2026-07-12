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

/** Component representation materialized from the RGBA bitmap before JPEG coding. */
public enum class JpegEncodeColorModel {
    /** A luminance-only JPEG frame. */
    Grayscale,
    /** Three Y, Cb, Cr components; this is the historical sequential default. */
    YCbCr,
    /** Three untransformed R, G, B components; supported by the lossless writer only. */
    Rgb,
}

/**
 * One progressive Huffman scan.  The current static writer accepts initial
 * scans only: `Ah = Al = 0`; a DC scan has `Ss = Se = 0` and an AC scan has
 * exactly one component.  Refinement remains an explicit refusal rather than
 * silently producing a sequential image.
 */
public data class JpegProgressiveScan(
    val componentIds: List<Int>,
    val spectralStart: Int,
    val spectralEnd: Int,
    val successiveHigh: Int = 0,
    val successiveLow: Int = 0,
) {
    init {
        require(componentIds.isNotEmpty()) { "a progressive scan needs at least one component" }
        require(componentIds.distinct().size == componentIds.size) { "a progressive scan cannot repeat a component" }
        require(componentIds.all { it in 1..255 }) { "JPEG component ids must be in [1, 255]" }
        require(spectralStart in 0..63 && spectralEnd in 0..63 && spectralStart <= spectralEnd) {
            "progressive spectral selection must be within [0, 63]"
        }
        require(successiveHigh in 0..13 && successiveLow in 0..13) {
            "progressive successive approximation must be within [0, 13]"
        }
    }
}

/** Predictor and point transform for a SOF3 Huffman lossless scan. */
public data class JpegLosslessParameters(
    val predictor: Int,
    val pointTransform: Int,
) {
    init {
        require(predictor in 1..7) { "lossless predictor must be in [1, 7]" }
        require(pointTransform in 0..15) { "lossless point transform must be in [0, 15]" }
    }
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

/**
 * Declares one differential hierarchy level relative to the final image size.
 *
 * The current writer accepts exactly one `1/2`
 * [JpegEncodeProcess.DifferentialSequentialHuffman] level after a grayscale
 * sequential-Huffman reference; other ratios and process intersections remain
 * explicit refusals until they have independent encoder evidence.
 */
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
