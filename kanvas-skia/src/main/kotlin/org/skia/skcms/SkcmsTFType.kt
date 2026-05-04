package org.skia.skcms

/**
 * Bit-compatible port of `skcms_TFType`. Order is load-bearing: ordinals
 * are used as the negated `g` sentinel for HDR transfer functions
 * (`tfKindMarker(kind)` = `-kind.ordinal.toFloat()`), so this must
 * match upstream
 * [skcms_public.h](file:///Users/chaos/workspace/kanvas-forge/skia-main/modules/skcms/src/skcms_public.h)
 * exactly.
 */
public enum class SkcmsTFType {
    Invalid,
    sRGBish,
    PQish,
    HLGish,
    HLGinvish,
    PQ,
    HLG,
}
