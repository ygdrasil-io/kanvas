package org.skia.foundation.skcms
import org.skia.math.SkcmsMatrix3x3

/**
 * Bit-compatible port of `skcms_ICCProfile`
 * ([modules/skcms/src/skcms_public.h:230-267](file:///Users/chaos/workspace/kanvas-forge/skia-main/modules/skcms/src/skcms_public.h)).
 *
 * Result of `skcmsParse` (Phase F2). The fields mirror upstream:
 *  - [buffer] / [size] : pointer + length to the raw ICC bytes (we own
 *    the slice; upstream just stores a non-owning pointer).
 *  - [dataColorSpace] / [pcs] : 4-byte signatures from the header.
 *  - [tagCount] : number of tags in the tag table.
 *  - [trc] : the rTRC, gTRC, bTRC curves (when [hasTrc] is true).
 *  - [toXYZD50] : R/G/B → XYZ matrix (when [hasToXYZD50]).
 *  - [a2b] / [b2a] : multi-dimensional CLUT tables (Phase F4).
 *  - [cicp] : 'cicp' tag values (when [hasCICP]).
 *  - `has*` flags say which optional sub-structures are populated.
 *
 * `SkColorSpace.Make(profile)` (Phase F5) consumes this and selects a
 * `(transferFunction, toXYZD50)` pair using the CICP / TRC / matrix
 * sources in priority order.
 */
public data class SkcmsICCProfile(
    public val buffer: ByteArray? = null,
    public val size: Int = 0,
    public val dataColorSpace: Int = 0,
    public val pcs: Int = 0,
    public val tagCount: Int = 0,

    public val trc: Array<SkcmsCurve?> = arrayOfNulls(3),
    public val toXYZD50: SkcmsMatrix3x3 = SkcmsMatrix3x3.IDENTITY,
    public val a2b: SkcmsA2B = SkcmsA2B.EMPTY,
    public val b2a: SkcmsB2A = SkcmsB2A.EMPTY,
    public val cicp: SkcmsCICP = SkcmsCICP(0, 0, 0, 0),

    public val hasTrc: Boolean = false,
    public val hasToXYZD50: Boolean = false,
    public val hasA2B: Boolean = false,
    public val hasB2A: Boolean = false,
    public val hasCICP: Boolean = false,
) {
    /** Phase F1 stub: equals delegates to identity. Field-aware comparison
     *  is added in Phase F when we need profile-equality semantics. */
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}
