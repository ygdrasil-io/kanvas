package org.skia.skcms

/**
 * Bit-compatible port of `skcms_TFType`. Phase 1 only handles `sRGBish` and
 * `Invalid`; PQ/HLG variants are stubbed for completeness and will be filled
 * in if a future GM needs HDR transfer functions.
 */
public enum class SkcmsTFType {
    Invalid,
    sRGBish,
    PQish,
    HLGish,
    PQ,
    HLG,
    HLGinvish,
}
