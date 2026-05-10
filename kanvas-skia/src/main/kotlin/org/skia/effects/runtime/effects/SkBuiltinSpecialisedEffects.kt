package org.skia.effects.runtime.effects

import org.skia.effects.runtime.ChildResolver
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.SkRuntimeEffectDispatch
import org.skia.effects.runtime.SkRuntimeImpl
import org.skia.foundation.SkColor4f
import org.skia.math.SkPoint
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
        SkRuntimeEffectDispatch.register(INVERT_BLENDER_SKSL) { InvertBlenderImpl }
        SkRuntimeEffectDispatch.register(STRETCH_COLORS_BLENDER_SKSL) { StretchColorsBlenderImpl }
        SkRuntimeEffectDispatch.register(KAWASE_BLUR_SHADER_SKSL) { KawaseBlurShaderImpl }
        SkRuntimeEffectDispatch.register(KAWASE_MIX_SHADER_SKSL) { KawaseMixShaderImpl }
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

    init { registerAll() }
}
