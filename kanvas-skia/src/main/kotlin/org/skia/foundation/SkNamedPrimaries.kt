package org.skia.foundation

/**
 * Bit-compatible port of `SkNamedPrimaries`
 * ([include/core/SkColorSpace.h:42-119](file:///Users/chaos/workspace/kanvas-forge/skia-main/include/core/SkColorSpace.h)).
 *
 * Defines color primaries from ITU-T H.273 Table 2. Used by
 * `SkColorSpace.MakeCICP` (Phase E) to look up gamut/primaries pairs by
 * CICP id.
 */
public object SkNamedPrimaries {

    public val kRec709: SkColorSpacePrimaries = SkColorSpacePrimaries(
        0.64f, 0.33f, 0.3f, 0.6f, 0.15f, 0.06f, 0.3127f, 0.329f,
    )

    public val kRec470SystemM: SkColorSpacePrimaries = SkColorSpacePrimaries(
        0.67f, 0.33f, 0.21f, 0.71f, 0.14f, 0.08f, 0.31f, 0.316f,
    )

    public val kRec470SystemBG: SkColorSpacePrimaries = SkColorSpacePrimaries(
        0.64f, 0.33f, 0.29f, 0.60f, 0.15f, 0.06f, 0.3127f, 0.3290f,
    )

    public val kRec601: SkColorSpacePrimaries = SkColorSpacePrimaries(
        0.630f, 0.340f, 0.310f, 0.595f, 0.155f, 0.070f, 0.3127f, 0.3290f,
    )

    /** Functionally identical to kRec601. */
    public val kSMPTE_ST_240: SkColorSpacePrimaries = kRec601

    public val kGenericFilm: SkColorSpacePrimaries = SkColorSpacePrimaries(
        0.681f, 0.319f, 0.243f, 0.692f, 0.145f, 0.049f, 0.310f, 0.316f,
    )

    public val kRec2020: SkColorSpacePrimaries = SkColorSpacePrimaries(
        0.708f, 0.292f, 0.170f, 0.797f, 0.131f, 0.046f, 0.3127f, 0.3290f,
    )

    public val kSMPTE_ST_428_1: SkColorSpacePrimaries = SkColorSpacePrimaries(
        1f, 0f, 0f, 1f, 0f, 0f, 1f / 3f, 1f / 3f,
    )

    public val kSMPTE_RP_431_2: SkColorSpacePrimaries = SkColorSpacePrimaries(
        0.680f, 0.320f, 0.265f, 0.690f, 0.150f, 0.060f, 0.314f, 0.351f,
    )

    public val kSMPTE_EG_432_1: SkColorSpacePrimaries = SkColorSpacePrimaries(
        0.680f, 0.320f, 0.265f, 0.690f, 0.150f, 0.060f, 0.3127f, 0.3290f,
    )

    /** No corresponding industry specification. Sometimes referred to as
     *  EBU 3213-E, but that document doesn't specify these values. */
    public val kITU_T_H273_Value22: SkColorSpacePrimaries = SkColorSpacePrimaries(
        0.630f, 0.340f, 0.295f, 0.605f, 0.155f, 0.077f, 0.3127f, 0.3290f,
    )

    /** [css-color-4](https://www.w3.org/TR/css-color-4/#predefined-prophoto-rgb). */
    public val kProPhotoRGB: SkColorSpacePrimaries = SkColorSpacePrimaries(
        0.7347f, 0.2653f, 0.1596f, 0.8404f, 0.0366f, 0.0001f, 0.34567f, 0.35850f,
    )

    /** Mapping between primary names and ITU-T H.273 Table 2 row numbers. */
    public enum class CicpId(public val value: Int) {
        kRec709(1),
        kRec470SystemM(4),
        kRec470SystemBG(5),
        kRec601(6),
        kSMPTE_ST_240(7),
        kGenericFilm(8),
        kRec2020(9),
        kSMPTE_ST_428_1(10),
        kSMPTE_RP_431_2(11),
        kSMPTE_EG_432_1(12),
        kITU_T_H273_Value22(22);

        public companion object {
            /** Value 2 = "characteristics are unknown or determined by the
             *  application" (delegate to toXYZD50 tags). */
            public const val kCicpIdApplicationDefined: Int = 2
        }
    }

    /**
     * Lookup table for CICP id → primaries. Mirrors the upstream
     * `cicp_table` in `SkColorSpace.cpp:38-50`. Entries with a fast-path
     * matrix (kRec709/kSRGB-gamut, kRec2020/kRec2020-gamut,
     * kSMPTE_EG_432_1/kDisplayP3) bypass `skcms_PrimariesToXYZD50`.
     */
    private data class TableEntry(
        val cicpId: CicpId,
        val primaries: SkColorSpacePrimaries,
        val toXYZD50: org.skia.math.SkcmsMatrix3x3? = null,
    )

    private val cicpTable: List<TableEntry> = listOf(
        TableEntry(CicpId.kRec709, kRec709, org.skia.foundation.skcms.SkNamedGamut.kSRGB),
        TableEntry(CicpId.kRec470SystemM, kRec470SystemM),
        TableEntry(CicpId.kRec470SystemBG, kRec470SystemBG),
        TableEntry(CicpId.kRec601, kRec601),
        TableEntry(CicpId.kSMPTE_ST_240, kSMPTE_ST_240),
        TableEntry(CicpId.kGenericFilm, kGenericFilm),
        TableEntry(CicpId.kRec2020, kRec2020, org.skia.foundation.skcms.SkNamedGamut.kRec2020),
        TableEntry(CicpId.kSMPTE_ST_428_1, kSMPTE_ST_428_1),
        TableEntry(CicpId.kSMPTE_RP_431_2, kSMPTE_RP_431_2),
        TableEntry(CicpId.kSMPTE_EG_432_1, kSMPTE_EG_432_1, org.skia.foundation.skcms.SkNamedGamut.kDisplayP3),
        TableEntry(CicpId.kITU_T_H273_Value22, kITU_T_H273_Value22),
    )

    /**
     * `CicpId → toXYZD50` matrix. Returns `null` if the id is not in the
     * table or the primaries don't yield a valid matrix.
     */
    public fun getCicp(primaries: CicpId): org.skia.math.SkcmsMatrix3x3? {
        for (entry in cicpTable) {
            if (entry.cicpId != primaries) continue
            entry.toXYZD50?.let { return it }
            entry.primaries.toXYZD50()?.let { return it }
        }
        return null
    }

    /**
     * Reverse lookup: a `toXYZD50` matrix → its CICP id, or `null` if no
     * standard primary matches within `xyzAlmostEqual` tolerance.
     */
    public fun getCicpFromMatrix(m: org.skia.math.SkcmsMatrix3x3): CicpId? {
        for (entry in cicpTable) {
            val cand = entry.primaries.toXYZD50() ?: continue
            if (xyzAlmostEqual(m, cand)) return entry.cicpId
        }
        return null
    }
}
