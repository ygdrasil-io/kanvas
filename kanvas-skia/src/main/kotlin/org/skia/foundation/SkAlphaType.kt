package org.skia.foundation

/**
 * Describes how a pixel's alpha channel is interpreted relative to its
 * colour channels. Mirrors Skia's `SkAlphaType` (the upstream variants
 * carry a `_SkAlphaType` suffix that's redundant under Kotlin enum
 * scoping — kept here in the Javadoc for searchability when reading
 * upstream code).
 *
 * - [kUnknown] : `kUnknown_SkAlphaType` — uninitialised / unspecified.
 * - [kOpaque]  : `kOpaque_SkAlphaType`  — pixel is fully opaque, alpha
 *   channel can be ignored or treated as 1.
 * - [kPremul]  : `kPremul_SkAlphaType`  — colour components are
 *   pre-multiplied by alpha (RGB ∈ [0, A]).
 * - [kUnpremul]: `kUnpremul_SkAlphaType` — colour components are stored
 *   independently from alpha (RGB ∈ [0, 1]).
 *
 * `:kanvas-skia`'s `SkBitmap` currently stores `kUnpremul` ARGB8888 — a
 * future premul refactor will start tagging bitmaps with this enum so
 * conversions happen at well-defined boundaries.
 */
public enum class SkAlphaType {
    kUnknown,
    kOpaque,
    kPremul,
    kUnpremul,
    ;

    /** Mirrors Skia's `SkAlphaTypeIsOpaque(SkAlphaType)`. */
    public fun isOpaque(): Boolean = this == kOpaque

    /** True for [kPremul], [kOpaque], [kUnpremul] — i.e. anything other than [kUnknown]. */
    public fun isValid(): Boolean = this != kUnknown
}
