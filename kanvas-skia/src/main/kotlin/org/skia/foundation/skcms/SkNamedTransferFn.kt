package org.skia.foundation.skcms
import org.skia.math.SkcmsTransferFunction

/**
 * Bit-compatible port of `SkNamedTransferFn`
 * ([include/core/SkColorSpace.h:121-221](file:///Users/chaos/workspace/kanvas-forge/skia-main/include/core/SkColorSpace.h)).
 *
 * Most TFs are sRGBish (positive `g`). PQ and HLG use a sentinel-encoding
 * (negative `g`) that drives the HDR branches in `classify` / `eval`; until
 * Phase I of MIGRATION_PLAN_COLORSPACE_PORT.md they classify as `Invalid`
 * and don't traverse the eval/invert paths.
 */
public object SkNamedTransferFn {

    // -----------------------------------------------------------------------
    // sRGBish family
    // -----------------------------------------------------------------------

    /** Standard sRGB OETF. */
    public val kSRGB: SkcmsTransferFunction = SkcmsTransferFunction(
        g = 2.4f,
        a = 1.0f / 1.055f,
        b = 0.055f / 1.055f,
        c = 1.0f / 12.92f,
        d = 0.04045f,
        e = 0.0f,
        f = 0.0f,
    )

    /** Identity TF: `eval(x) == x` for x in `[0, 1]`. */
    public val kLinear: SkcmsTransferFunction = SkcmsTransferFunction(
        g = 1f, a = 1f, b = 0f, c = 0f, d = 0f, e = 0f, f = 0f,
    )

    /** Pure 2.2-power. */
    public val k2Dot2: SkcmsTransferFunction = SkcmsTransferFunction(
        g = 2.2f, a = 1f, b = 0f, c = 0f, d = 0f, e = 0f, f = 0f,
    )

    /** BT.2020 OETF. Aligned with upstream Skia
     *  [SkColorSpace.h:130-131](file:///Users/chaos/workspace/kanvas-forge/skia-main/include/core/SkColorSpace.h)
     *  (6 decimal places). The Phase B snap absorbs the ~5e-7 divergence
     *  with the s15Fixed16-decoded values from the reference PNG ICC
     *  profile, so `makeRGB(parsed-from-PNG, kRec2020-gamut)` still snaps
     *  to a single canonical singleton. */
    public val kRec2020: SkcmsTransferFunction = SkcmsTransferFunction(
        g = 2.22222f,
        a = 0.909672f,
        b = 0.0903276f,
        c = 0.222222f,
        d = 0.0812429f,
        e = 0f, f = 0f,
    )

    // -----------------------------------------------------------------------
    // ITU-T H.273, Table 3 — entries beyond the four above.
    //
    // BT.1886 / BT.709 / BT.601 / IEC 61966-2-4 all share the same {g=2.4,
    // a=1, b..f=0} encoding upstream, so kRec601 / kIEC61966_2_4 / kRec2020_*
    // are aliases of kRec709.
    // -----------------------------------------------------------------------

    public val kRec709: SkcmsTransferFunction = SkcmsTransferFunction(
        g = 2.4f, a = 1f, b = 0f, c = 0f, d = 0f, e = 0f, f = 0f,
    )

    public val kRec470SystemM: SkcmsTransferFunction = SkcmsTransferFunction(
        g = 2.2f, a = 1f, b = 0f, c = 0f, d = 0f, e = 0f, f = 0f,
    )

    public val kRec470SystemBG: SkcmsTransferFunction = SkcmsTransferFunction(
        g = 2.8f, a = 1f, b = 0f, c = 0f, d = 0f, e = 0f, f = 0f,
    )

    public val kRec601: SkcmsTransferFunction = kRec709

    public val kSMPTE_ST_240: SkcmsTransferFunction = SkcmsTransferFunction(
        g = 2.222222222222f,
        a = 0.899626676224f,
        b = 0.100373323776f,
        c = 0.25f,
        d = 0.091286342118f,
        e = 0f, f = 0f,
    )

    public val kIEC61966_2_4: SkcmsTransferFunction = kRec709
    public val kIEC61966_2_1: SkcmsTransferFunction = kSRGB
    public val kRec2020_10bit: SkcmsTransferFunction = kRec709
    public val kRec2020_12bit: SkcmsTransferFunction = kRec709

    public val kSMPTE_ST_428_1: SkcmsTransferFunction = SkcmsTransferFunction(
        g = 2.6f,
        a = 1.034080527699f,
        b = 0f, c = 0f, d = 0f, e = 0f, f = 0f,
    )

    // -----------------------------------------------------------------------
    // PQ / HLG — sentinel-encoded for `classify`. Currently classified as
    // Invalid (HDR support arrives in MIGRATION_PLAN_COLORSPACE_PORT.md
    // Phase I).
    // -----------------------------------------------------------------------

    /** SMPTE ST 2084 PQ EOTF. `g=-5` is the sentinel; `a` carries the HDR
     *  reference white luminance (default 203 nits). */
    public val kPQ: SkcmsTransferFunction = SkcmsTransferFunction(
        g = -5.0f, a = 203f, b = 0f, c = 0f, d = 0f, e = 0f, f = 0f,
    )

    /** BT.2100 HLG. `g=-6`, `a`=HDR ref white, `b`=peak luminance,
     *  `c`=system gamma. */
    public val kHLG: SkcmsTransferFunction = SkcmsTransferFunction(
        g = -6.0f, a = 203f, b = 1000.0f, c = 1.2f, d = 0f, e = 0f, f = 0f,
    )

    // -----------------------------------------------------------------------
    // Display profiles
    // -----------------------------------------------------------------------

    /** ProPhoto RGB — gamma 1/1.8 = 1.8 power. */
    public val kProPhotoRGB: SkcmsTransferFunction = SkcmsTransferFunction(
        g = 1.8f, a = 1f, b = 0f, c = 0f, d = 0f, e = 0f, f = 0f,
    )

    /** Adobe RGB (1998) — alias of k2Dot2. */
    public val kA98RGB: SkcmsTransferFunction = k2Dot2

    // -----------------------------------------------------------------------
    // CICP id mapping (ITU-T H.273, Table 3)
    // -----------------------------------------------------------------------

    /** Mapping between TF names and ITU-T H.273 Table 3 row numbers. */
    public enum class CicpId(public val value: Int) {
        kRec709(1),
        kRec470SystemM(4),
        kRec470SystemBG(5),
        kRec601(6),
        kSMPTE_ST_240(7),
        kLinear(8),
        kIEC61966_2_4(11),
        kIEC61966_2_1(13),
        kRec2020_10bit(14),
        kRec2020_12bit(15),
        kPQ(16),
        kSMPTE_ST_428_1(17),
        kHLG(18);

        public companion object {
            /** Alias of [kIEC61966_2_1] (CICP value 13). */
            public val kSRGB: CicpId = kIEC61966_2_1

            /** Value 2 = "characteristics are unknown or determined by the
             *  application" (delegate to TRC tags). */
            public const val kCicpIdApplicationDefined: Int = 2
        }
    }

    /**
     * Lookup table for CICP id → transfer function. Mirrors upstream
     * `cicp_table` in `SkColorSpace.cpp:93-107`.
     */
    private val cicpTable: List<Pair<CicpId, SkcmsTransferFunction>> = listOf(
        CicpId.kRec709 to kRec709,
        CicpId.kRec470SystemM to kRec470SystemM,
        CicpId.kRec470SystemBG to kRec470SystemBG,
        CicpId.kRec601 to kRec601,
        CicpId.kSMPTE_ST_240 to kSMPTE_ST_240,
        CicpId.kLinear to kLinear,
        CicpId.kIEC61966_2_4 to kIEC61966_2_4,
        CicpId.kIEC61966_2_1 to kIEC61966_2_1,
        CicpId.kRec2020_10bit to kRec2020_10bit,
        CicpId.kRec2020_12bit to kRec2020_12bit,
        CicpId.kPQ to kPQ,
        CicpId.kSMPTE_ST_428_1 to kSMPTE_ST_428_1,
        CicpId.kHLG to kHLG,
    )

    /** `CicpId → TF`. Returns `null` if the id is not in the table. */
    public fun getCicp(id: CicpId): SkcmsTransferFunction? =
        cicpTable.firstOrNull { it.first == id }?.second
}
