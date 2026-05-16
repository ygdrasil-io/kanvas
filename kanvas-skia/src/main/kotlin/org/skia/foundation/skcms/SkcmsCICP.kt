package org.skia.foundation.skcms

/**
 * Bit-compatible port of `skcms_CICP`
 * ([modules/skcms/src/skcms_public.h:223-228](file:///Users/chaos/workspace/kanvas-forge/skia-main/modules/skcms/src/skcms_public.h)).
 *
 * 4-byte ICC v4 'cicp' tag payload. Values are ITU-T H.273 code points:
 *  - `colorPrimaries` indexes Table 2 (cf. `SkNamedPrimaries::CicpId`).
 *  - `transferCharacteristics` indexes Table 3 (cf.
 *    `SkNamedTransferFn::CicpId`).
 *  - `matrixCoefficients` should be `0` for RGB profiles.
 *  - `videoFullRangeFlag` should be `1` for full-range images.
 */
public data class SkcmsCICP(
    public val colorPrimaries: Int,
    public val transferCharacteristics: Int,
    public val matrixCoefficients: Int,
    public val videoFullRangeFlag: Int,
)
