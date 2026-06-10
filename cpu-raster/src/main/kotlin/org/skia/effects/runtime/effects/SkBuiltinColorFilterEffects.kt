package org.skia.effects.runtime.effects

import org.skia.effects.runtime.ChildResolver
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.SkRuntimeEffectDescriptor
import org.skia.effects.runtime.SkRuntimeEffectDescriptorRegistry
import org.skia.effects.runtime.SkRuntimeEffectDispatch
import org.skia.effects.runtime.SkRuntimeImpl
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkPoint
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * Hand-ported color-filter runtime effects from Phase D2.4.a. Each
 * [SkRuntimeImpl] is registered against the canonical SkSL source
 * of the upstream effect ; the registration happens automatically
 * when this `object` is loaded (idempotent via the builtin-if-absent
 * registry helper).
 *
 * **Why not parse SkSL** : the project's strategy is hand-port-per-
 * shader-type (Kotlin for raster, WGSL for GPU — see
 * [`MIGRATION_PLAN_GPU_WEBGPU.md`](../../../../../../../../../MIGRATION_PLAN_GPU_WEBGPU.md)).
 * Each `Impl` class in this file reproduces the math an upstream
 * SkSL function would have computed ; the SkSL string is the
 * dispatch key.
 *
 * **GMs unblocked** :
 *  - [`RuntimeColorFilterGM`](../../../../tests/RuntimeColorFilterGM.kt) :
 *    the 5 `gNoop` / `gLumaSrc` / `gTernary` / `gIfs` / `gEarlyReturn`
 *    SkSL programs from upstream's `gm/runtimecolorfilter.cpp`.
 *  - [`ComposeColorFilterGM`](../../../../tests/ComposeColorFilterGM.kt) SkSL
 *    column : the 2-child `outer.eval(inner.eval(c))` from
 *    `gm/composecolorfilter.cpp`.
 *
 * **Auto-registration** : touching this `object` (e.g. via the
 * `SkRuntimeEffect.MakeForColorFilter` companion's `init { … }` or
 * directly from a GM's `onOnceBeforeDraw`) triggers the `init`
 * block which registers all impls in [SkRuntimeEffectDispatch].
 */
public object SkBuiltinColorFilterEffects {

    init { registerAll() }

    /**
     * Idempotent registry population. Called automatically on
     * first reference to this `object` (via the `init {}` block),
     * and re-called by [SkRuntimeEffect]'s `ensureBuiltinsLoaded`
     * helper after a [SkRuntimeEffectDispatch.clearForTest] —
     * that test hook empties the dispatch table, so the next
     * `MakeForXxx` call must repopulate before lookup.
     *
     * Each builtin registration is skipped when the same hash is already
     * present, so calling this twice in a row is safe.
     */
    public fun registerAll() {
        // Identity color filter (Noop) — gNoop in upstream.
        SkRuntimeEffectDispatch.registerBuiltinIfAbsent(NOOP_SKSL) { IdentityImpl }

        // Luma → alpha (gLumaSrc from runtimecolorfilter.cpp).
        SkRuntimeEffectDispatch.registerBuiltinIfAbsent(LUMA_SRC_SKSL) { LumaToAlphaImpl }
        SkRuntimeEffectDescriptorRegistry.registerBuiltinIfAbsent(
            LUMA_SRC_SKSL,
            SkRuntimeEffectDescriptor(
                stableId = "runtime.color_filter_luma_to_alpha",
                kind = SkRuntimeEffect.Kind.kColorFilter,
                uniforms = LumaToAlphaImpl.uniforms,
                children = LumaToAlphaImpl.children,
                flags = LumaToAlphaImpl.flags,
                cpuImplementationId = "kotlin/color_filter_luma_to_alpha",
                wgslImplementationId = "wgsl/runtime_color_filter_luma_to_alpha",
            ),
        )

        // G-channel splat (AlternateLuma's `inColor.ggga` from
        // lumafilter.cpp). Replicates the G channel into R, G, B
        // and preserves A — models a linear-light Y-channel readout
        // when the working colour space is CIE XYZ (G = Y).
        SkRuntimeEffectDispatch.registerBuiltinIfAbsent(G_CHANNEL_SPLAT_SKSL) { GChannelSplatImpl }

        // Tone-map (gTernary / gIfs / gEarlyReturn — all
        // semantically equivalent ; register all 3 hashes against
        // the same Kotlin impl).
        SkRuntimeEffectDispatch.registerBuiltinIfAbsent(TERNARY_SKSL) { ToneMapImpl }
        SkRuntimeEffectDispatch.registerBuiltinIfAbsent(IFS_SKSL) { ToneMapImpl }
        SkRuntimeEffectDispatch.registerBuiltinIfAbsent(EARLY_RETURN_SKSL) { ToneMapImpl }

        // Compose two color-filter children (gComposeCF from
        // composecolorfilter.cpp).
        SkRuntimeEffectDispatch.registerBuiltinIfAbsent(COMPOSE_CF_SKSL) { ComposeChildrenImpl }
    }

    // ─── SkSL sources (verbatim copies of upstream) ──────────────────

    /** `gNoop` from `gm/runtimecolorfilter.cpp`. */
    public const val NOOP_SKSL: String = """
    half4 main(half4 color) {
        return color;
    }
"""

    /** `gLumaSrc` from `gm/runtimecolorfilter.cpp`. Outputs luma into
     *  the alpha channel ; RGB = 0. */
    public const val LUMA_SRC_SKSL: String = """
    half4 main(half4 color) {
        return dot(color.rgb, half3(0.3, 0.6, 0.1)).000r;
    }
"""

    /** `gTernary` from `gm/runtimecolorfilter.cpp`. Piecewise tone
     *  map via ternary expression. */
    public const val TERNARY_SKSL: String = """
    half4 main(half4 color) {
        half luma = dot(color.rgb, half3(0.3, 0.6, 0.1));

        half scale = luma < 0.33333 ? 0.5
                   : luma < 0.66666 ? (0.166666 + 2.0 * (luma - 0.33333)) / luma
                   :   /* else */     (0.833333 + 0.5 * (luma - 0.66666)) / luma;
        return half4(color.rgb * scale, color.a);
    }
"""

    /** `gIfs` from `gm/runtimecolorfilter.cpp`. Same as ternary, but
     *  written with `if` blocks and no early return. */
    public const val IFS_SKSL: String = """
    half4 main(half4 color) {
        half luma = dot(color.rgb, half3(0.3, 0.6, 0.1));

        half scale = 0;
        if (luma < 0.33333) {
            scale = 0.5;
        } else if (luma < 0.66666) {
            scale = (0.166666 + 2.0 * (luma - 0.33333)) / luma;
        } else {
            scale = (0.833333 + 0.5 * (luma - 0.66666)) / luma;
        }
        return half4(color.rgb * scale, color.a);
    }
"""

    /** `gEarlyReturn` from `gm/runtimecolorfilter.cpp`. Same as ifs,
     *  but with an early `return` from inside the first branch. */
    public const val EARLY_RETURN_SKSL: String = """
    half4 main(half4 color) {
        half luma = dot(color.rgb, half3(0.3, 0.6, 0.1));

        half scale = 0;
        if (luma < 0.33333) {
            return half4(color.rgb * 0.5, color.a);
        } else if (luma < 0.66666) {
            scale = 0.166666 + 2.0 * (luma - 0.33333);
        } else {
            scale = 0.833333 + 0.5 * (luma - 0.66666);
        }
        return half4(color.rgb * (scale/luma), color.a);
    }
"""

    /** G-channel splat from `gm/lumafilter.cpp` (`AlternateLuma`).
     *  Replicates the green channel into red, green, and blue while
     *  preserving alpha. When the working colour space is CIE XYZ D50
     *  (R→X, G→Y, B→Z) this has the effect of reading back the
     *  luminance (Y) channel as greyscale. */
    public const val G_CHANNEL_SPLAT_SKSL: String =
        "half4 main(half4 inColor) { return inColor.ggga; }"

    /** Compose-CF SkSL from `gm/composecolorfilter.cpp`. Two color-
     *  filter children ; outer applied to inner's output. */
    public const val COMPOSE_CF_SKSL: String = """
            uniform colorFilter inner;
            uniform colorFilter outer;
            half4 main(half4 c) { return outer.eval(inner.eval(c)); }
        """

    // ─── Concrete impls (one Kotlin object per upstream SkSL) ────────

    /** `gNoop` — identity. Returns the input colour verbatim. */
    public object IdentityImpl : SkRuntimeImpl {
        override val uniforms: List<SkRuntimeEffect.Uniform> = emptyList()
        override val children: List<SkRuntimeEffect.Child> = emptyList()
        override val flags: Int = SkRuntimeEffect.kAllowColorFilter_Flag or
            SkRuntimeEffect.kAlphaUnchanged_Flag

        override fun shade(
            coords: SkPoint?,
            srcColor: SkColor4f?,
            dstColor: SkColor4f?,
            uniforms: ByteBuffer,
            children: Array<ChildResolver>,
        ): SkColor4f = srcColor ?: SkColor4f.kBlack
    }

    /** `gLumaSrc` — emit luma in the alpha channel, zero RGB.
     *
     * `dot(color.rgb, half3(0.3, 0.6, 0.1)).000r` produces a
     * 4-vector where channels (x, y, z) are zero (the `.000`
     * suffix) and channel `w` (alpha) is the luma scalar (the
     * `r` swizzle of the resulting scalar). */
    public object LumaToAlphaImpl : SkRuntimeImpl {
        override val uniforms: List<SkRuntimeEffect.Uniform> = emptyList()
        override val children: List<SkRuntimeEffect.Child> = emptyList()
        override val flags: Int = SkRuntimeEffect.kAllowColorFilter_Flag

        override fun shade(
            coords: SkPoint?,
            srcColor: SkColor4f?,
            dstColor: SkColor4f?,
            uniforms: ByteBuffer,
            children: Array<ChildResolver>,
        ): SkColor4f {
            val c = srcColor ?: return SkColor4f.kBlack
            val luma = c.fR * 0.3f + c.fG * 0.6f + c.fB * 0.1f
            return SkColor4f(0f, 0f, 0f, luma)
        }
    }

    /**
     * `gTernary` / `gIfs` / `gEarlyReturn` — piecewise tone map :
     * compute luma, then bucket into 3 ranges with different RGB
     * scale factors. Alpha is preserved verbatim. The 3 SkSL
     * variants are semantically identical — they just differ in
     * how the conditional logic is expressed. We provide one
     * Kotlin impl and register it under all 3 hash keys.
     *
     * The math :
     * ```
     *   luma = dot(rgb, (0.3, 0.6, 0.1))
     *   if luma < 0.33333  → rgb' = rgb * 0.5
     *   if luma < 0.66666  → rgb' = rgb * (0.166666 + 2 * (luma - 0.33333)) / luma
     *   else               → rgb' = rgb * (0.833333 + 0.5 * (luma - 0.66666)) / luma
     * ```
     * The `gEarlyReturn` variant has a slightly different `else`
     * branch composition (factors `scale/luma`), but produces the
     * same numbers as the ternary / ifs variants.
     */
    public object ToneMapImpl : SkRuntimeImpl {
        override val uniforms: List<SkRuntimeEffect.Uniform> = emptyList()
        override val children: List<SkRuntimeEffect.Child> = emptyList()
        override val flags: Int = SkRuntimeEffect.kAllowColorFilter_Flag or
            SkRuntimeEffect.kAlphaUnchanged_Flag

        override fun shade(
            coords: SkPoint?,
            srcColor: SkColor4f?,
            dstColor: SkColor4f?,
            uniforms: ByteBuffer,
            children: Array<ChildResolver>,
        ): SkColor4f {
            val c = srcColor ?: return SkColor4f.kBlack
            val luma = c.fR * 0.3f + c.fG * 0.6f + c.fB * 0.1f
            val scale = when {
                luma < 0.33333f -> 0.5f
                luma < 0.66666f -> (0.166666f + 2f * (luma - 0.33333f)) / luma
                else -> (0.833333f + 0.5f * (luma - 0.66666f)) / luma
            }
            return SkColor4f(c.fR * scale, c.fG * scale, c.fB * scale, c.fA)
        }
    }

    /**
     * Compose two color-filter children :
     * `outer.eval(inner.eval(input))`. Mirrors the upstream
     * `composecolorfilter.cpp` runtime effect that proves
     * `outer.makeComposed(inner)` and the SkSL form produce the
     * same pixels.
     */
    public object ComposeChildrenImpl : SkRuntimeImpl {
        override val uniforms: List<SkRuntimeEffect.Uniform> = emptyList()
        override val children: List<SkRuntimeEffect.Child> = listOf(
            SkRuntimeEffect.Child("inner", SkRuntimeEffect.ChildType.kColorFilter, 0),
            SkRuntimeEffect.Child("outer", SkRuntimeEffect.ChildType.kColorFilter, 1),
        )
        override val flags: Int = SkRuntimeEffect.kAllowColorFilter_Flag

        override fun shade(
            coords: SkPoint?,
            srcColor: SkColor4f?,
            dstColor: SkColor4f?,
            uniforms: ByteBuffer,
            children: Array<ChildResolver>,
        ): SkColor4f {
            val c = srcColor ?: return SkColor4f.kBlack
            val inner = children[0] as ChildResolver.ColorFilter
            val outer = children[1] as ChildResolver.ColorFilter
            return outer.apply(inner.apply(c))
        }
    }

    /**
     * G-channel splat (`AlternateLuma` from `gm/lumafilter.cpp`).
     *
     * `inColor.ggga` — spreads the green channel across R, G, B
     * while preserving A. When the upstream working colour space is
     * CIE XYZ D50 (RGB → XYZ, so G = Y = luminance), this produces
     * a near-greyscale result. In kanvas-skia the working-format
     * wrapper ([org.skia.effects.SkColorFilterPriv.withWorkingFormat])
     * is not yet implemented, so this impl runs in the device colour
     * space ; the visual result will differ from upstream but the
     * math is correct within that constraint.
     */
    public object GChannelSplatImpl : SkRuntimeImpl {
        override val uniforms: List<SkRuntimeEffect.Uniform> = emptyList()
        override val children: List<SkRuntimeEffect.Child> = emptyList()
        override val flags: Int = SkRuntimeEffect.kAllowColorFilter_Flag

        override fun shade(
            coords: SkPoint?,
            srcColor: SkColor4f?,
            dstColor: SkColor4f?,
            uniforms: ByteBuffer,
            children: Array<ChildResolver>,
        ): SkColor4f {
            val c = srcColor ?: return SkColor4f.kBlack
            // inColor.ggga : replicate G into R, G, B ; keep A.
            return SkColor4f(c.fG, c.fG, c.fG, c.fA)
        }
    }
}
