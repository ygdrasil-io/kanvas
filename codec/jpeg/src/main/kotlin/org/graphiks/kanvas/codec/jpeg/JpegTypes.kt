package org.graphiks.kanvas.codec.jpeg

import org.graphiks.kanvas.codec.Codec

internal enum class JpegEntropyCoding {
    HUFFMAN,
    ARITHMETIC,
}

internal enum class JpegSampleCoding {
    DCT_SEQUENTIAL,
    DCT_PROGRESSIVE,
    LOSSLESS,
}

internal enum class JpegColorModel {
    GRAYSCALE,
    YCBCR,
    RGB,
    CMYK,
    YCCK,
}

internal data class JpegFrameSpec(
    val marker: Int,
    val entropyCoding: JpegEntropyCoding,
    val sampleCoding: JpegSampleCoding,
    val differential: Boolean,
) {
    internal companion object {
        fun fromSof(marker: Int): JpegFrameSpec? = mapOf(
            0xC0 to JpegFrameSpec(0xC0, JpegEntropyCoding.HUFFMAN, JpegSampleCoding.DCT_SEQUENTIAL, false),
            0xC1 to JpegFrameSpec(0xC1, JpegEntropyCoding.HUFFMAN, JpegSampleCoding.DCT_SEQUENTIAL, false),
            0xC2 to JpegFrameSpec(0xC2, JpegEntropyCoding.HUFFMAN, JpegSampleCoding.DCT_PROGRESSIVE, false),
            0xC3 to JpegFrameSpec(0xC3, JpegEntropyCoding.HUFFMAN, JpegSampleCoding.LOSSLESS, false),
            0xC5 to JpegFrameSpec(0xC5, JpegEntropyCoding.HUFFMAN, JpegSampleCoding.DCT_SEQUENTIAL, true),
            0xC6 to JpegFrameSpec(0xC6, JpegEntropyCoding.HUFFMAN, JpegSampleCoding.DCT_PROGRESSIVE, true),
            0xC7 to JpegFrameSpec(0xC7, JpegEntropyCoding.HUFFMAN, JpegSampleCoding.LOSSLESS, true),
            0xC9 to JpegFrameSpec(0xC9, JpegEntropyCoding.ARITHMETIC, JpegSampleCoding.DCT_SEQUENTIAL, false),
            0xCA to JpegFrameSpec(0xCA, JpegEntropyCoding.ARITHMETIC, JpegSampleCoding.DCT_PROGRESSIVE, false),
            0xCB to JpegFrameSpec(0xCB, JpegEntropyCoding.ARITHMETIC, JpegSampleCoding.LOSSLESS, false),
            0xCD to JpegFrameSpec(0xCD, JpegEntropyCoding.ARITHMETIC, JpegSampleCoding.DCT_SEQUENTIAL, true),
            0xCE to JpegFrameSpec(0xCE, JpegEntropyCoding.ARITHMETIC, JpegSampleCoding.DCT_PROGRESSIVE, true),
            0xCF to JpegFrameSpec(0xCF, JpegEntropyCoding.ARITHMETIC, JpegSampleCoding.LOSSLESS, true),
        )[marker]
    }
}

public data class JpegLimits(
    val maxEncodedBytes: Long,
    val maxPixels: Long,
    val maxScans: Int,
    val maxSegments: Int,
) {
    public companion object {
        public val DEFAULT: JpegLimits = JpegLimits(64L * 1024 * 1024, 268_435_456, 1_024, 16_384)
    }
}

public data class JpegDiagnostic(
    val code: String,
    val offset: Long,
    val result: Codec.Result,
)
