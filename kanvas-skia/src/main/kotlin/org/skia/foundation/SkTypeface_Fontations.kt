package org.skia.foundation

import org.skia.foundation.stream.SkStream

/**
 * R-final.S **STUB.FONTATIONS** — surface stub for upstream's
 * `SkTypeface_Fontations` (`src/ports/fonts/SkTypeface_fontations.h`).
 *
 * Upstream Skia ships an alternative typeface backend powered by
 * Google's Rust [`fontations`](https://github.com/googlefonts/fontations)
 * crate (skrifa / read-fonts / write-fonts) instead of FreeType. The
 * port lives in `modules/skshaper` and is wired through
 * `SkTypeface_Fontations::MakeFromStream(stream, args)` to mint a
 * typeface that uses the Rust scaler.
 *
 * `:kanvas-skia` is **pure-JVM** — pulling in fontations would mean
 * either UniFFI bindings (Rust → Kotlin) or a JNI shim around a
 * cdylib. Both are out of scope for this module ; consumers that
 * need fontations parity should bind it themselves and supply a
 * concrete [SkTypeface] subclass through their own font manager.
 *
 * The factory exists so direct ports of `gm/fontations.cpp` and
 * `gm/fontations_ft_compare.cpp` (see
 * [`API_FINALIZATION_PLAN.md`](../../../../../../../../API_FINALIZATION_PLAN.md))
 * compile and reference the documented API surface.
 */
@Suppress("UNUSED_PARAMETER")
public object SkTypeface_Fontations {

    /**
     * Mirrors `sk_sp<SkTypeface> SkTypeface_Fontations::MakeFromStream(
     *     std::unique_ptr<SkStreamAsset>, const SkFontArguments&)`.
     *
     * Always throws [NotImplementedError] with the `STUB.FONTATIONS`
     * tag.
     */
    @Suppress("FunctionName")
    public fun MakeFromStream(
        stream: SkStream,
        args: SkFontArguments = SkFontArguments(),
    ): SkTypeface = throw NotImplementedError(
        "STUB.FONTATIONS: requires Fontations Rust crate via UniFFI or JNI — " +
            "see API_FINALIZATION_PLAN.md.",
    )

    /**
     * Mirrors `sk_sp<SkTypeface> SkTypeface_Fontations::MakeFromData(
     *     sk_sp<SkData>, const SkFontArguments&)` — same fate as
     * [MakeFromStream].
     */
    @Suppress("FunctionName")
    public fun MakeFromData(
        bytes: ByteArray,
        args: SkFontArguments = SkFontArguments(),
    ): SkTypeface = throw NotImplementedError(
        "STUB.FONTATIONS: requires Fontations Rust crate via UniFFI or JNI — " +
            "see API_FINALIZATION_PLAN.md.",
    )
}
