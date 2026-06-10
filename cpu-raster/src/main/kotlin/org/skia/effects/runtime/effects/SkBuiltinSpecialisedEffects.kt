package org.skia.effects.runtime.effects

import org.skia.effects.runtime.ChildResolver
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.SkRuntimeEffectDescriptor
import org.skia.effects.runtime.SkRuntimeEffectDescriptorRegistry
import org.skia.effects.runtime.SkRuntimeEffectDispatch
import org.skia.effects.runtime.SkRuntimeEffectDispatchMetadata
import org.skia.effects.runtime.SkRuntimeImpl
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkPoint
import java.nio.ByteBuffer

/**
 * D2.4.d — Hand-ported "specialised one-off" runtime effects.
 *
 * Each entry in this cluster is a self-contained SkSL program
 * extracted from a single upstream GM. Unlike c.1-c.6 (which all
 * exercise the same template skeleton with a swappable expression),
 * each one-off here has its own bespoke shape, uniform layout, and
 * GM consumer.
 *
 * **Effects shipped** :
 *
 *  - **`InvertBlender`** — `gm/destcolor.cpp`. A
 *    [SkRuntimeEffect.Kind.kBlender] that ignores `src` and
 *    returns `(half4(1) - dst).rgb1` (RGB inversion, alpha = 1).
 *    Used by `DestColorGM` to invert the lower-right oval over
 *    the source image.
 *
 *  - **`StretchColorsBlender`** — `gm/imagedither.cpp`'s
 *    `stretch_colors_blender()`. Maps `dst.rgb` from `[0.25, 0.3125]`
 *    to `[0, 1]` (multiply by 16 after subtracting 0.25), letting
 *    the test reveal whether dithering was applied to the source
 *    image. Used by `ImageDitherGM`.
 *
 *  - **`KawaseBlurShader`** + **`KawaseMixShader`** —
 *    `gm/kawase_blur_rt.cpp`. Two `SkRuntimeEffect.Kind.kShader`
 *    programs that implement an iterative box-blur (Kawase) :
 *    the blur shader samples 5 points (centre + 4 diagonal
 *    offsets) and averages ; the mix shader cross-fades the
 *    blurred result with the original. Used by `KawaseBlurRtGM`.
 *
 * **GMs unblocked** :
 *  - [`DestColorGM`](../../../../tests/DestColorGM.kt)
 *  - [`ImageDitherGM`](../../../../tests/ImageDitherGM.kt)
 *  - [`KawaseBlurRtGM`](../../../../tests/KawaseBlurRtGM.kt)
 *
 * **Deferred to follow-up D2.4.d.2** :
 *  - `rippleshadergm.cpp` — uses `RippleShader.rts` (~100 LOC of
 *    bespoke SkSL with 9 helper functions : `triangleNoise`,
 *    `sparkles`, `softCircle`, `softRing`, `subProgress`, …).
 *    Substantial port effort.
 *  - `fp_sample_chaining.cpp` — multi-effect chain test, ~244 LOC
 *    upstream.
 */
public object SkBuiltinSpecialisedEffects {

    public fun registerAll() {
        SkRuntimeEffectDispatch.registerBuiltinIfAbsent(
            INVERT_BLENDER_SKSL,
            InvertBlenderImpl.dispatchMetadata("runtime.invert_blender", "kotlin/invert_blender"),
        ) { InvertBlenderImpl }
        SkRuntimeEffectDescriptorRegistry.registerBuiltinIfAbsent(
            INVERT_BLENDER_SKSL,
            SkRuntimeEffectDescriptor(
                stableId = "runtime.invert_blender",
                kind = SkRuntimeEffect.Kind.kBlender,
                uniforms = InvertBlenderImpl.uniforms,
                children = InvertBlenderImpl.children,
                flags = InvertBlenderImpl.flags,
                cpuImplementationId = "kotlin/invert_blender",
                wgslImplementationId = null,
            ),
        )
        SkRuntimeEffectDispatch.registerBuiltinIfAbsent(STRETCH_COLORS_BLENDER_SKSL) { StretchColorsBlenderImpl }
        SkRuntimeEffectDispatch.registerBuiltinIfAbsent(KAWASE_BLUR_SHADER_SKSL) { KawaseBlurShaderImpl }
        SkRuntimeEffectDispatch.registerBuiltinIfAbsent(KAWASE_MIX_SHADER_SKSL) { KawaseMixShaderImpl }
        SkRuntimeEffectDispatch.registerBuiltinIfAbsent(RUNTIME_FUNCTIONS_SHADER_SKSL) { RuntimeFunctionsShaderImpl }
    }

    // ─── destcolor.cpp invert blender ────────────────────────────────

    /** Verbatim from `gm/destcolor.cpp`. */
    public const val INVERT_BLENDER_SKSL: String = """
        half4 main(half4 src, half4 dst) {
            return (half4(1) - dst).rgb1;
        }
    """

    public object InvertBlenderImpl : SkRuntimeImpl {
        override val uniforms: List<SkRuntimeEffect.Uniform> = emptyList()
        override val children: List<SkRuntimeEffect.Child> = emptyList()
        override val flags: Int = SkRuntimeEffect.kAllowBlender_Flag

        override fun shade(
            coords: SkPoint?,
            srcColor: SkColor4f?,
            dstColor: SkColor4f?,
            uniforms: ByteBuffer,
            children: Array<ChildResolver>,
        ): SkColor4f {
            val d = dstColor ?: return SkColor4f.kBlack
            // (half4(1) - dst).rgb1 → invert RGB, alpha = 1.
            return SkColor4f(
                fR = 1f - d.fR,
                fG = 1f - d.fG,
                fB = 1f - d.fB,
                fA = 1f,
            )
        }
    }

    // ─── imagedither.cpp stretch_colors_blender ──────────────────────

    /** Verbatim from `gm/imagedither.cpp::stretch_colors_blender()`. */
    public const val STRETCH_COLORS_BLENDER_SKSL: String =
        "half4 main(half4 src, half4 dst) { return ((dst.rgb - 0.25) * 16).rgb1; }"

    public object StretchColorsBlenderImpl : SkRuntimeImpl {
        override val uniforms: List<SkRuntimeEffect.Uniform> = emptyList()
        override val children: List<SkRuntimeEffect.Child> = emptyList()
        override val flags: Int = SkRuntimeEffect.kAllowBlender_Flag

        override fun shade(
            coords: SkPoint?,
            srcColor: SkColor4f?,
            dstColor: SkColor4f?,
            uniforms: ByteBuffer,
            children: Array<ChildResolver>,
        ): SkColor4f {
            val d = dstColor ?: return SkColor4f.kBlack
            // ((dst.rgb - 0.25) * 16).rgb1 — stretches [0.25, 0.3125]
            // to [0, 1]. Values outside that range over/underflow ;
            // the GM uses a [0x44/255, 0x55/255] = [0.267, 0.333]
            // gradient, so the stretch lands within [-0.27, 1.32].
            // We clamp the output at write time per upstream Skia
            // behaviour (8-bit channel saturation).
            return SkColor4f(
                fR = (d.fR - 0.25f) * 16f,
                fG = (d.fG - 0.25f) * 16f,
                fB = (d.fB - 0.25f) * 16f,
                fA = 1f,
            )
        }
    }

    // ─── kawase_blur_rt.cpp shaders ─────────────────────────────────

    /** Verbatim from `gm/kawase_blur_rt.cpp`'s blur shader. */
    public const val KAWASE_BLUR_SHADER_SKSL: String = """
            uniform shader src;
            uniform float in_inverseScale;
            uniform float2 in_blurOffset;

            half4 main(float2 xy) {
                float2 scaled_xy = float2(xy.x * in_inverseScale, xy.y * in_inverseScale);

                half4 c = src.eval(scaled_xy);
                c += src.eval(scaled_xy + float2( in_blurOffset.x,  in_blurOffset.y));
                c += src.eval(scaled_xy + float2( in_blurOffset.x, -in_blurOffset.y));
                c += src.eval(scaled_xy + float2(-in_blurOffset.x,  in_blurOffset.y));
                c += src.eval(scaled_xy + float2(-in_blurOffset.x, -in_blurOffset.y));

                return half4(c.rgb * 0.2, 1.0);
            }
        """

    public object KawaseBlurShaderImpl : SkRuntimeImpl {
        override val uniforms: List<SkRuntimeEffect.Uniform> = listOf(
            SkRuntimeEffect.Uniform(
                name = "in_inverseScale", offset = 0,
                type = SkRuntimeEffect.Uniform.Type.kFloat, count = 1, flags = 0,
            ),
            // in_blurOffset is float2 ; offset 8 (alignment 8).
            SkRuntimeEffect.Uniform(
                name = "in_blurOffset", offset = 8,
                type = SkRuntimeEffect.Uniform.Type.kFloat2, count = 1, flags = 0,
            ),
        )
        override val children: List<SkRuntimeEffect.Child> = listOf(
            SkRuntimeEffect.Child("src", SkRuntimeEffect.ChildType.kShader, index = 0),
        )
        override val flags: Int = SkRuntimeEffect.kAllowShader_Flag

        override fun shade(
            coords: SkPoint?,
            srcColor: SkColor4f?,
            dstColor: SkColor4f?,
            uniforms: ByteBuffer,
            children: Array<ChildResolver>,
        ): SkColor4f {
            val xy = coords ?: return SkColor4f.kBlack

            uniforms.position(0)
            val invScale = uniforms.float
            uniforms.position(8)
            val blurOffsetX = uniforms.float
            val blurOffsetY = uniforms.float

            val srcChild = children[0] as ChildResolver.Shader

            val sx = xy.fX * invScale
            val sy = xy.fY * invScale

            // 5-tap Kawase : centre + 4 diagonal offsets, equal weight 1/5.
            val taps = listOf(
                SkPoint(sx, sy),
                SkPoint(sx + blurOffsetX, sy + blurOffsetY),
                SkPoint(sx + blurOffsetX, sy - blurOffsetY),
                SkPoint(sx - blurOffsetX, sy + blurOffsetY),
                SkPoint(sx - blurOffsetX, sy - blurOffsetY),
            )

            var r = 0f
            var g = 0f
            var b = 0f
            for (tap in taps) {
                val s = srcChild.sample(tap)
                r += s.fR
                g += s.fG
                b += s.fB
            }
            // half4(c.rgb * 0.2, 1.0) — sum × 1/5, alpha forced to 1.
            return SkColor4f(r * 0.2f, g * 0.2f, b * 0.2f, 1f)
        }
    }

    /** Verbatim from `gm/kawase_blur_rt.cpp`'s mix shader. */
    public const val KAWASE_MIX_SHADER_SKSL: String = """
            uniform shader in_blur;
            uniform shader in_original;
            uniform float in_inverseScale;
            uniform float in_mix;

            half4 main(float2 xy) {
                float2 scaled_xy = float2(xy.x * in_inverseScale, xy.y * in_inverseScale);

                half4 blurred = in_blur.eval(scaled_xy);
                half4 composition = in_original.eval(xy);
                return mix(composition, blurred, in_mix);
            }
        """

    public object KawaseMixShaderImpl : SkRuntimeImpl {
        override val uniforms: List<SkRuntimeEffect.Uniform> = listOf(
            SkRuntimeEffect.Uniform(
                name = "in_inverseScale", offset = 0,
                type = SkRuntimeEffect.Uniform.Type.kFloat, count = 1, flags = 0,
            ),
            SkRuntimeEffect.Uniform(
                name = "in_mix", offset = 4,
                type = SkRuntimeEffect.Uniform.Type.kFloat, count = 1, flags = 0,
            ),
        )
        override val children: List<SkRuntimeEffect.Child> = listOf(
            SkRuntimeEffect.Child("in_blur", SkRuntimeEffect.ChildType.kShader, index = 0),
            SkRuntimeEffect.Child("in_original", SkRuntimeEffect.ChildType.kShader, index = 1),
        )
        override val flags: Int = SkRuntimeEffect.kAllowShader_Flag

        override fun shade(
            coords: SkPoint?,
            srcColor: SkColor4f?,
            dstColor: SkColor4f?,
            uniforms: ByteBuffer,
            children: Array<ChildResolver>,
        ): SkColor4f {
            val xy = coords ?: return SkColor4f.kBlack

            uniforms.position(0)
            val invScale = uniforms.float
            val mix = uniforms.float

            val blurChild = children[0] as ChildResolver.Shader
            val origChild = children[1] as ChildResolver.Shader

            val blurred = blurChild.sample(SkPoint(xy.fX * invScale, xy.fY * invScale))
            val original = origChild.sample(xy)

            val one = 1f - mix
            return SkColor4f(
                fR = original.fR * one + blurred.fR * mix,
                fG = original.fG * one + blurred.fG * mix,
                fB = original.fB * one + blurred.fB * mix,
                fA = original.fA * one + blurred.fA * mix,
            )
        }
    }

    // ─── runtimefunctions.cpp procedural shader ──────────────────────

    /**
     * Verbatim from `gm/runtimefunctions.cpp::RUNTIME_FUNCTIONS_SRC`
     * (`@notargs`' Twitter procedural shader). Single half4 uniform
     * (`iResolution`), no children, no childcalls. The inner helper
     * `f(vec3)` does the heavy lifting per-pixel ; `main(...)` iterates
     * it 32 times to march a 3D point along a viewing direction.
     */
    public const val RUNTIME_FUNCTIONS_SHADER_SKSL: String = """
        // Source: @notargs https://twitter.com/notargs/status/1250468645030858753
        uniform half4 iResolution;
        const float iTime = 0;

        float f(vec3 p) {
            p.z -= iTime * 10.;
            float a = p.z * .1;
            p.xy *= mat2(cos(a), sin(a), -sin(a), cos(a));
            return .1 - length(cos(p.xy) + sin(p.yz));
        }

        half4 main(vec2 fragcoord) {
            vec3 d = .5 - fragcoord.xy1 / iResolution.y;
            vec3 p=vec3(0);
            for (int i = 0; i < 32; i++) {
              p += f(p) * d;
            }
            return ((sin(p) + vec3(2, 5, 9)) / length(p)).xyz1;
        }
    """

    public object RuntimeFunctionsShaderImpl : SkRuntimeImpl {
        // Single uniform : half4 iResolution, offset 0, size 16, half-precision.
        override val uniforms: List<SkRuntimeEffect.Uniform> = listOf(
            SkRuntimeEffect.Uniform(
                name = "iResolution",
                offset = 0,
                type = SkRuntimeEffect.Uniform.Type.kFloat4,
                count = 1,
                flags = SkRuntimeEffect.Uniform.kHalfPrecision_Flag,
            ),
        )
        override val children: List<SkRuntimeEffect.Child> = emptyList()
        override val flags: Int = SkRuntimeEffect.kAllowShader_Flag

        override fun shade(
            coords: SkPoint?,
            srcColor: SkColor4f?,
            dstColor: SkColor4f?,
            uniforms: ByteBuffer,
            children: Array<ChildResolver>,
        ): SkColor4f {
            val xy = coords ?: return SkColor4f.kBlack

            // iResolution.y — the only component read.
            uniforms.position(4)
            val iResY = uniforms.float

            // vec3 d = .5 - fragcoord.xy1 / iResolution.y;
            // fragcoord.xy1 == vec3(fragcoord.x, fragcoord.y, 1.0).
            val invY = 1f / iResY
            var dx = 0.5f - xy.fX * invY
            var dy = 0.5f - xy.fY * invY
            var dz = 0.5f - invY

            // vec3 p = vec3(0).
            var px = 0f
            var py = 0f
            var pz = 0f

            // 32 marches. f(p) — itself mutates a local copy of p
            // (SkSL pass-by-value), so we don't bleed back into the
            // outer loop's p (matches upstream's `p.z -= iTime*10`
            // and `p.xy *= mat2(...)` being scoped to the helper).
            // iTime == 0 (const), so `p.z -= 0` is a no-op.
            for (i in 0 until 32) {
                // f(p)
                // p.z -= iTime * 10. ; iTime == 0 → unchanged.
                val fpz = pz
                val a = fpz * 0.1f
                val ca = kotlin.math.cos(a)
                val sa = kotlin.math.sin(a)
                // p.xy *= mat2(cos(a), sin(a), -sin(a), cos(a))
                // column-major SkSL : columns [ca, sa] and [-sa, ca]
                //   matrix = [[ca, -sa], [sa, ca]] (row form)
                // row-vector × mat : new.x = px*ca + py*sa
                //                    new.y = px*(-sa) + py*ca
                val rx = px * ca + py * sa
                val ry = px * (-sa) + py * ca

                // .1 - length(cos(p.xy) + sin(p.yz))
                // cos(p.xy) = (cos(rx), cos(ry))
                // sin(p.yz) = (sin(ry), sin(fpz))
                // (here `p.xy` and `p.yz` reference the *rotated*
                // local p — upstream mutates p in-place then sums
                // cos(p.xy) + sin(p.yz)).
                val ax = kotlin.math.cos(rx) + kotlin.math.sin(ry)
                val ay = kotlin.math.cos(ry) + kotlin.math.sin(fpz)
                val len = kotlin.math.sqrt(ax * ax + ay * ay)
                val fpv = 0.1f - len

                // p += f(p) * d
                px += fpv * dx
                py += fpv * dy
                pz += fpv * dz
            }

            // return ((sin(p) + vec3(2,5,9)) / length(p)).xyz1
            val sx = kotlin.math.sin(px) + 2f
            val sy = kotlin.math.sin(py) + 5f
            val sz = kotlin.math.sin(pz) + 9f
            val plen = kotlin.math.sqrt(px * px + py * py + pz * pz)
            val invLen = if (plen == 0f) 0f else 1f / plen
            return SkColor4f(
                fR = sx * invLen,
                fG = sy * invLen,
                fB = sz * invLen,
                fA = 1f,
            )
        }
    }

    private fun SkRuntimeImpl.dispatchMetadata(
        stableId: String,
        cpuImplementationId: String,
    ): SkRuntimeEffectDispatchMetadata =
        SkRuntimeEffectDispatchMetadata(
            stableId = stableId,
            kind = SkRuntimeEffect.Kind.kBlender,
            uniforms = uniforms,
            children = children,
            flags = flags,
            cpuImplementationId = cpuImplementationId,
        )

    init { registerAll() }
}
