package org.skia.skcms

/**
 * Bit-compatible port of `skcms_A2B`
 * ([modules/skcms/src/skcms_public.h:179-199](file:///Users/chaos/workspace/kanvas-forge/skia-main/modules/skcms/src/skcms_public.h)).
 *
 * Phase F1: data shape only. The N-dimensional CLUT and matrix evaluation
 * arrive in Phase F4 (multi-dimensional LUT support — typically only
 * needed for CMYK and exotic RGB profiles, not the SDR DM references we
 * target today).
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
    override fun equals(other: Any?): Boolean = throw UnsupportedOperationException(
        "SkcmsA2B equality is not implemented (Phase F4)"
    )
    override fun hashCode(): Int = throw UnsupportedOperationException(
        "SkcmsA2B hashing is not implemented (Phase F4)"
    )

    public companion object {
        public val EMPTY: SkcmsA2B = SkcmsA2B()
    }
}
