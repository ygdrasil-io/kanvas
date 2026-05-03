package org.skia.skcms

/**
 * Bit-compatible port of `skcms_B2A`
 * ([modules/skcms/src/skcms_public.h:201-221](file:///Users/chaos/workspace/kanvas-forge/skia-main/modules/skcms/src/skcms_public.h)).
 *
 * Phase F1: data shape only. CLUT-based device → PCS conversions are
 * deferred to Phase F4.
 */
public data class SkcmsB2A(
    public val inputCurves: Array<SkcmsCurve?> = arrayOfNulls(3),
    public val inputChannels: Int = 0,

    public val matrixChannels: Int = 0,
    public val matrixCurves: Array<SkcmsCurve?> = arrayOfNulls(3),
    public val matrix: SkcmsMatrix3x4? = null,

    public val outputCurves: Array<SkcmsCurve?> = arrayOfNulls(4),
    public val grid8: ByteArray? = null,
    public val grid16: ByteArray? = null,
    public val gridPoints: IntArray = IntArray(4),
    public val outputChannels: Int = 0,
) {
    override fun equals(other: Any?): Boolean = throw UnsupportedOperationException(
        "SkcmsB2A equality is not implemented (Phase F4)"
    )
    override fun hashCode(): Int = throw UnsupportedOperationException(
        "SkcmsB2A hashing is not implemented (Phase F4)"
    )

    public companion object {
        public val EMPTY: SkcmsB2A = SkcmsB2A()
    }
}
