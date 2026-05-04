package org.skia.skcms

/**
 * Bit-compatible port of `skcms_A2B`
 * ([modules/skcms/src/skcms_public.h:179-199](file:///Users/chaos/workspace/kanvas-forge/skia-main/modules/skcms/src/skcms_public.h)).
 *
 * Data shape from Phase F1 plus content-aware [equals]/[hashCode] activated
 * in Phase F4 alongside [evalA2b] (Kotlin port of the upstream A2B pipeline).
 */
public data class SkcmsA2B(
    public val inputCurves: Array<SkcmsCurve?> = arrayOfNulls(4),
    public val grid8: ByteArray? = null,
    public val grid16: ByteArray? = null,
    public val inputChannels: Int = 0,
    public val gridPoints: IntArray = IntArray(4),

    public val matrixCurves: Array<SkcmsCurve?> = arrayOfNulls(3),
    public val matrix: SkcmsMatrix3x4? = null,
    public val matrixChannels: Int = 0,

    public val outputChannels: Int = 0,
    public val outputCurves: Array<SkcmsCurve?> = arrayOfNulls(3),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SkcmsA2B) return false
        if (inputChannels != other.inputChannels) return false
        if (matrixChannels != other.matrixChannels) return false
        if (outputChannels != other.outputChannels) return false
        if (!gridPoints.contentEquals(other.gridPoints)) return false
        if (!grid8.contentNullableEquals(other.grid8)) return false
        if (!grid16.contentNullableEquals(other.grid16)) return false
        if (matrix != other.matrix) return false
        if (!inputCurves.contentEquals(other.inputCurves)) return false
        if (!matrixCurves.contentEquals(other.matrixCurves)) return false
        if (!outputCurves.contentEquals(other.outputCurves)) return false
        return true
    }

    override fun hashCode(): Int {
        var h = inputChannels
        h = 31 * h + matrixChannels
        h = 31 * h + outputChannels
        h = 31 * h + gridPoints.contentHashCode()
        h = 31 * h + (grid8?.contentHashCode() ?: 0)
        h = 31 * h + (grid16?.contentHashCode() ?: 0)
        h = 31 * h + (matrix?.hashCode() ?: 0)
        h = 31 * h + inputCurves.contentHashCode()
        h = 31 * h + matrixCurves.contentHashCode()
        h = 31 * h + outputCurves.contentHashCode()
        return h
    }

    public companion object {
        public val EMPTY: SkcmsA2B = SkcmsA2B()
    }
}

private fun ByteArray?.contentNullableEquals(other: ByteArray?): Boolean {
    if (this == null) return other == null
    if (other == null) return false
    return contentEquals(other)
}
