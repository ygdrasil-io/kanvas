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

/**
 * Hand-ported runtime shader effects from Phase D2.4.b — **cluster B**,
 * the two `runtimeshader.cpp` programs that consume **shader children**
 * (vs cluster A which only deals with color filters / no children).
 *
 * **Why a separate file from [SkBuiltinColorFilterEffects]** : the
 * dispatch table is the same global registry, but the auto-registration
 * objects are split by feature cluster so a partial port doesn't drag
 * unused impls into class load. This file owns the `ThresholdRT` and
 * `UnsharpRT` programs from upstream
 * [`gm/runtimeshader.cpp`](https://github.com/google/skia/blob/main/gm/runtimeshader.cpp) ;
 * the rest of that GM (SimpleRT, SpiralRT, ColorCubeRT, …) lives in
 * a sibling object as the cluster expands.
 *
 * **Why not parse SkSL** : the project's strategy is hand-port-per-
 * shader-type (Kotlin for raster, WGSL for GPU — see
 * [`MIGRATION_PLAN_GPU_WEBGPU.md`](../../../../../../../../../MIGRATION_PLAN_GPU_WEBGPU.md)).
 * Each `Impl` class in this file reproduces the math an upstream SkSL
 * function would have computed ; the SkSL string is the dispatch key.
 *
 * **GMs unblocked** :
 *  - [`RuntimeShaderGM`](../../../../tests/RuntimeShaderGM.kt) — the
 *    `ThresholdRT` and `UnsharpRT` cells from upstream's
 *    `gm/runtimeshader.cpp`.
 *
 * **Auto-registration** : touching this `object` (e.g. via the
 * `SkRuntimeEffect.MakeForShader` companion's `init { … }` or directly
 * from a GM's `onOnceBeforeDraw`) triggers the `init` block which
 * registers all impls in [SkRuntimeEffectDispatch].
 */
public object SkBuiltinShaderEffectsChildren {

    init { registerAll() }

    /**
     * Idempotent registry population. Called automatically on first
     * reference to this `object` (via the `init {}` block), and re-
     * called by [SkRuntimeEffect]'s `ensureBuiltinsLoaded` helper after
     * a [SkRuntimeEffectDispatch.clearForTest] — that test hook empties
     * the dispatch table, so the next `MakeForXxx` call must repopulate
     * before lookup.
     *
     * Each builtin registration is skipped when the same hash is already
     * present, so calling this twice in a row is safe.
     */
    public fun registerAll() {
        SkRuntimeEffectDispatch.registerBuiltinIfAbsent(THRESHOLD_RT_SKSL) { ThresholdRTImpl }
        SkRuntimeEffectDispatch.registerBuiltinIfAbsent(UNSHARP_RT_SKSL) { UnsharpRTImpl }
        SkRuntimeEffectDescriptorRegistry.registerBuiltinIfAbsent(
            UNSHARP_RT_SKSL,
            SkRuntimeEffectDescriptor(
                stableId = "runtime.unsharp_rt",
                kind = SkRuntimeEffect.Kind.kShader,
                uniforms = UnsharpRTImpl.uniforms,
                children = UnsharpRTImpl.children,
                flags = UnsharpRTImpl.flags,
                cpuImplementationId = "kotlin/unsharp_rt",
                wgslImplementationId = null,
            ),
        )
    }

    // ─── SkSL sources (verbatim copies of upstream) ──────────────────

    /** `ThresholdRT` from `gm/runtimeshader.cpp` (lines 129-190). Picks
     *  between `before_map` and `after_map` per pixel based on a
     *  smooth-stepped threshold sampled from `threshold_map.a`. */
    public const val THRESHOLD_RT_SKSL: String = """
        uniform shader before_map;
        uniform shader after_map;
        uniform shader threshold_map;

        uniform float cutoff;
        uniform float slope;

        float smooth_cutoff(float x) {
            x = x * slope + (0.5 - slope * cutoff);
            return clamp(x, 0, 1);
        }

        half4 main(float2 xy) {
            half4 before = before_map.eval(xy);
            half4 after = after_map.eval(xy);

            float m = smooth_cutoff(threshold_map.eval(xy).a);
            return mix(before, after, m);
        }
    """

    /** `UnsharpRT` from `gm/runtimeshader.cpp` (lines 231-267). 5-tap
     *  unsharp-mask kernel : center × 5 minus the four 4-connected
     *  neighbours. */
    public const val UNSHARP_RT_SKSL: String = """
        uniform shader child;
        half4 main(float2 xy) {
            half4 c = child.eval(xy) * 5;
            c -= child.eval(xy + float2( 1,  0));
            c -= child.eval(xy + float2(-1,  0));
            c -= child.eval(xy + float2( 0,  1));
            c -= child.eval(xy + float2( 0, -1));
            return c;
        }
    """

    // ─── Concrete impls (one Kotlin object per upstream SkSL) ────────

    /**
     * `ThresholdRT` — three shader children + two float uniforms.
     *
     * **Children** : `before_map` (index 0), `after_map` (index 1),
     * `threshold_map` (index 2). All sampled at the same local-space
     * `xy` once per pixel.
     *
     * **Uniforms** : `cutoff` at offset 0, `slope` at offset 4 (each a
     * 4-byte float ; total uniform block is 8 bytes per the std140-ish
     * layout of [SkRuntimeEffectSignatureParser]).
     *
     * **Math** :
     * ```
     *   m = clamp(threshold_map(xy).a * slope + (0.5 - slope * cutoff),
     *             0, 1)
     *   out = lerp(before_map(xy), after_map(xy), m)        // per channel
     * ```
     * For `slope` large (≈ 10) this approximates a step function at
     * `threshold == cutoff` ; for `slope == 1` it becomes a linear
     * blend over the whole `[0, 1]` range.
     */
    public object ThresholdRTImpl : SkRuntimeImpl {
        override val uniforms: List<SkRuntimeEffect.Uniform> = listOf(
            SkRuntimeEffect.Uniform(
                name = "cutoff", offset = 0, type = SkRuntimeEffect.Uniform.Type.kFloat,
                count = 1, flags = 0,
            ),
            SkRuntimeEffect.Uniform(
                name = "slope", offset = 4, type = SkRuntimeEffect.Uniform.Type.kFloat,
                count = 1, flags = 0,
            ),
        )
        override val children: List<SkRuntimeEffect.Child> = listOf(
            SkRuntimeEffect.Child("before_map", SkRuntimeEffect.ChildType.kShader, 0),
            SkRuntimeEffect.Child("after_map", SkRuntimeEffect.ChildType.kShader, 1),
            SkRuntimeEffect.Child("threshold_map", SkRuntimeEffect.ChildType.kShader, 2),
        )
        override val flags: Int = SkRuntimeEffect.kAllowShader_Flag or
            SkRuntimeEffect.kUsesSampleCoords_Flag

        override fun shade(
            coords: SkPoint?,
            srcColor: SkColor4f?,
            dstColor: SkColor4f?,
            uniforms: ByteBuffer,
            children: Array<ChildResolver>,
        ): SkColor4f {
            val xy = coords ?: SkPoint(0f, 0f)
            // Read uniforms in declaration order : cutoff @0, slope @4.
            val cutoff = uniforms.getFloat(0)
            val slope = uniforms.getFloat(4)

            val before = (children[0] as ChildResolver.Shader).sample(xy)
            val after = (children[1] as ChildResolver.Shader).sample(xy)
            val threshold = (children[2] as ChildResolver.Shader).sample(xy)

            // smooth_cutoff(threshold.a)
            val raw = threshold.fA * slope + (0.5f - slope * cutoff)
            val m = raw.coerceIn(0f, 1f)

            // mix(before, after, m) — per channel.
            val invM = 1f - m
            return SkColor4f(
                fR = before.fR * invM + after.fR * m,
                fG = before.fG * invM + after.fG * m,
                fB = before.fB * invM + after.fB * m,
                fA = before.fA * invM + after.fA * m,
            )
        }
    }

    /**
     * `UnsharpRT` — single shader child, no uniforms. Sharpens the
     * child's image by subtracting a 4-connected neighbourhood from
     * a centre × 5 sample :
     *
     * ```
     *   out = 5 · child(xy)
     *       − child(xy + ( 1,  0)) − child(xy + (−1,  0))
     *       − child(xy + ( 0,  1)) − child(xy + ( 0, −1))
     * ```
     *
     * **Saturation** : SkSL's `half4 -= half4` allows the intermediate
     * result to swing negative or above 1.0. We mirror that behaviour
     * here — the impl returns the unclamped float values ; the caller
     * (typically [org.skia.effects.runtime.SkRuntimeShader.pack4fToColor])
     * clamps to `[0, 1]` when packing to 8-bit. Holding the unclamped
     * float across the impl call lets a downstream colour-filter or F16
     * destination see the full HDR range upstream's SkSL would have
     * produced.
     */
    public object UnsharpRTImpl : SkRuntimeImpl {
        override val uniforms: List<SkRuntimeEffect.Uniform> = emptyList()
        override val children: List<SkRuntimeEffect.Child> = listOf(
            SkRuntimeEffect.Child("child", SkRuntimeEffect.ChildType.kShader, 0),
        )
        override val flags: Int = SkRuntimeEffect.kAllowShader_Flag or
            SkRuntimeEffect.kUsesSampleCoords_Flag

        override fun shade(
            coords: SkPoint?,
            srcColor: SkColor4f?,
            dstColor: SkColor4f?,
            uniforms: ByteBuffer,
            children: Array<ChildResolver>,
        ): SkColor4f {
            val xy = coords ?: SkPoint(0f, 0f)
            val child = children[0] as ChildResolver.Shader

            val centre = child.sample(xy)
            val east = child.sample(SkPoint(xy.fX + 1f, xy.fY))
            val west = child.sample(SkPoint(xy.fX - 1f, xy.fY))
            val south = child.sample(SkPoint(xy.fX, xy.fY + 1f))
            val north = child.sample(SkPoint(xy.fX, xy.fY - 1f))

            return SkColor4f(
                fR = 5f * centre.fR - east.fR - west.fR - south.fR - north.fR,
                fG = 5f * centre.fG - east.fG - west.fG - south.fG - north.fG,
                fB = 5f * centre.fB - east.fB - west.fB - south.fB - north.fB,
                fA = 5f * centre.fA - east.fA - west.fA - south.fA - north.fA,
            )
        }
    }
}
