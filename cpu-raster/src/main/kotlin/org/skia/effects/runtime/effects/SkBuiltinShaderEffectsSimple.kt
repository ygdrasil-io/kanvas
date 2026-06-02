package org.skia.effects.runtime.effects

import org.skia.effects.runtime.ChildResolver
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.SkRuntimeEffectDescriptor
import org.skia.effects.runtime.SkRuntimeEffectDescriptorRegistry
import org.skia.effects.runtime.SkRuntimeEffectDispatch
import org.skia.effects.runtime.SkRuntimeImpl
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkPoint
import org.skia.foundation.skcms.SkNamedTransferFn
import org.skia.foundation.skcms.skcmsTransferFunctionEval
import org.skia.foundation.skcms.skcmsTransferFunctionInvert
import java.nio.ByteBuffer
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Hand-ported "simple" shader runtime effects from Phase D2.4.b
 * (cluster A) — the 3 entry-level SkSL programs in upstream's
 * `gm/runtimeshader.cpp` that exercise the basic shader path
 * (uniforms only, no children, no `sample(...)` calls). Each
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
 *  - `RuntimeShaderGM` / `RuntimeShaderColorFilterGM` — the
 *    `gProg` "SimpleRT" smoke-test from `gm/runtimeshader.cpp`.
 *  - `SpiralRTGM` — the conic-spiral test from the same file
 *    (`gSpiralSkSL`), demonstrating two `layout(color)` uniforms.
 *  - `LinearGradientRTGM` — the encoded-vs-linear-sRGB compare
 *    from `gLinearGradientSkSL` (lines 592-644 upstream), which
 *    introduces `toLinearSrgb` / `fromLinearSrgb` intrinsics.
 *
 * **Auto-registration** : touching this `object` triggers the
 * `init` block which registers all impls in
 * [SkRuntimeEffectDispatch]. Phase D2.4.a wires the parent
 * `SkRuntimeEffect.makeFor(...)` to call
 * [SkBuiltinShaderEffectsSimple.registerAll] from
 * `ensureBuiltinsLoaded()` ; the hook lives one level up so this
 * file stays a leaf in the dependency graph.
 */
public object SkBuiltinShaderEffectsSimple {

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
        SkRuntimeEffectDispatch.registerBuiltinIfAbsent(
            SIMPLE_RT_SKSL,
            SimpleRTImpl.dispatchMetadata("runtime.simple_rt", "kotlin/simple_rt"),
        ) { SimpleRTImpl }
        SkRuntimeEffectDescriptorRegistry.registerBuiltinIfAbsent(
            SIMPLE_RT_SKSL,
            SkRuntimeEffectDescriptor(
                stableId = "runtime.simple_rt",
                kind = SkRuntimeEffect.Kind.kShader,
                uniforms = SimpleRTImpl.uniforms,
                children = SimpleRTImpl.children,
                flags = SimpleRTImpl.flags,
                cpuImplementationId = "kotlin/simple_rt",
                wgslImplementationId = "wgsl/runtime_simple_rt",
            ),
        )
        SkRuntimeEffectDispatch.registerBuiltinIfAbsent(
            SPIRAL_RT_SKSL,
            SpiralRTImpl.dispatchMetadata("runtime.spiral_rt", "kotlin/spiral_rt"),
        ) { SpiralRTImpl }
        SkRuntimeEffectDescriptorRegistry.registerBuiltinIfAbsent(
            SPIRAL_RT_SKSL,
            SkRuntimeEffectDescriptor(
                stableId = "runtime.spiral_rt",
                kind = SkRuntimeEffect.Kind.kShader,
                uniforms = SpiralRTImpl.uniforms,
                children = SpiralRTImpl.children,
                flags = SpiralRTImpl.flags,
                cpuImplementationId = "kotlin/spiral_rt",
                wgslImplementationId = "wgsl/runtime_spiral_rt",
            ),
        )
        SkRuntimeEffectDispatch.registerBuiltinIfAbsent(
            LINEAR_GRADIENT_RT_SKSL,
            LinearGradientRTImpl.dispatchMetadata("runtime.linear_gradient_rt", "kotlin/linear_gradient_rt"),
        ) { LinearGradientRTImpl }
        SkRuntimeEffectDescriptorRegistry.registerBuiltinIfAbsent(
            LINEAR_GRADIENT_RT_SKSL,
            SkRuntimeEffectDescriptor(
                stableId = "runtime.linear_gradient_rt",
                kind = SkRuntimeEffect.Kind.kShader,
                uniforms = LinearGradientRTImpl.uniforms,
                children = LinearGradientRTImpl.children,
                flags = LinearGradientRTImpl.flags,
                cpuImplementationId = "kotlin/linear_gradient_rt",
                wgslImplementationId = "wgsl/runtime_linear_gradient_rt",
            ),
        )
    }

    // ─── SkSL sources (verbatim copies of upstream) ──────────────────

    /** `gProg` (the "SimpleRT" smoke-test) from
     *  `gm/runtimeshader.cpp` lines 67-89. Outputs `(p.x/255,
     *  p.y/255, gColor.b, 1)` — only the blue channel of the
     *  uniform colour is read. */
    public const val SIMPLE_RT_SKSL: String = """
        uniform half4 gColor;

        half4 main(float2 p) {
            return half4(p*(1.0/255), gColor.b, 1);
        }
    """

    /** `gSpiralSkSL` from `gm/runtimeshader.cpp` lines 192-225.
     *  Polar-coordinate spiral lerp between two `layout(color)`
     *  uniforms. Notable : uses one-arg `atan(y/x)` — *not*
     *  `atan2` — so the angle range is `[-π/2, π/2]`. */
    public const val SPIRAL_RT_SKSL: String = """
        uniform float rad_scale;
        uniform float2 in_center;
        layout(color) uniform float4 in_colors0;
        layout(color) uniform float4 in_colors1;

        half4 main(float2 p) {
            float2 pp = p - in_center;
            float radius = length(pp);
            radius = sqrt(radius);
            float angle = atan(pp.y / pp.x);
            float t = (angle + 3.1415926/2) / (3.1415926);
            t += radius * rad_scale;
            t = fract(t);
            return in_colors0 * (1-t) + in_colors1 * t;
        }
    """

    /** `gLinearGradientSkSL` from `gm/runtimeshader.cpp` lines
     *  592-644. Top half (`p.y < 32`) lerps in encoded sRGB ;
     *  bottom half lerps in linear sRGB via the
     *  `toLinearSrgb` / `fromLinearSrgb` intrinsics. */
    public const val LINEAR_GRADIENT_RT_SKSL: String = """
        layout(color) uniform vec4 in_colors0;
        layout(color) uniform vec4 in_colors1;

        vec4 main(vec2 p) {
            float t = p.x / 256;
            if (p.y < 32) {
                return mix(in_colors0, in_colors1, t);
            } else {
                vec3 linColor0 = toLinearSrgb(in_colors0.rgb);
                vec3 linColor1 = toLinearSrgb(in_colors1.rgb);
                vec3 linColor = mix(linColor0, linColor1, t);
                return fromLinearSrgb(linColor).rgb1;
            }
        }
    """

    // ─── Concrete impls (one Kotlin object per upstream SkSL) ────────

    /**
     * `gProg` — the SimpleRT smoke-test : output the encoded
     * coordinate in `(R, G)` and the uniform's blue channel in
     * `B`. Useful as a wiring test : if the rasterizer feeds the
     * impl bad coords, the gradient-style render plot makes the
     * defect obvious.
     *
     * Uniforms layout :
     *  - `gColor` : `half4` at offset 0, 8 bytes (half-precision
     *    capped to `kFloat4`'s 16 byte alignment but only 8
     *    bytes wide — the parser tags it `kHalfPrecision_Flag`
     *    yet the byte buffer carries 16 bytes per the alignment
     *    rule). The impl reads only `gColor.b` (offset 8 → blue
     *    when `half4 = (r,g,b,a)`, but we use `kFloat4` storage
     *    so blue is at offset 8 = 2 floats in).
     *
     * **Half precision quirk** : the parser maps `half4` to
     * `kFloat4` (16 bytes) and just sets the half-precision flag
     * for reflection ; the on-the-wire layout is full float4. So
     * `gColor.b` lives at byte offset 8 (third float).
     */
    public object SimpleRTImpl : SkRuntimeImpl {
        override val uniforms: List<SkRuntimeEffect.Uniform> = listOf(
            SkRuntimeEffect.Uniform(
                name = "gColor",
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
            val p = coords ?: return SkColor4f.kBlack
            // gColor.b — third float of the half4 uniform at offset 0.
            uniforms.position(8)
            val gColorB = uniforms.float
            return SkColor4f(p.fX * INV_255, p.fY * INV_255, gColorB, 1f)
        }
    }

    /**
     * `gSpiralSkSL` — polar lerp with a `sqrt(radius)` warp.
     * Inputs are unpremul `layout(color)` colours ; the math
     * just blends between them based on the fractional part of
     * `(angle/π + 0.5) + sqrt(radius) * rad_scale`.
     *
     * Uniforms layout (computed by [SkRuntimeEffectSignatureParser]) :
     *  - `rad_scale`   : `float`  at offset  0 — 4 bytes.
     *  - `in_center`   : `float2` at offset  8 — aligned to 8, 8 bytes.
     *  - `in_colors0`  : `float4` at offset 16 — aligned to 16, 16 bytes.
     *  - `in_colors1`  : `float4` at offset 32 — 16 bytes.
     *  - Total : 48 bytes.
     */
    public object SpiralRTImpl : SkRuntimeImpl {
        override val uniforms: List<SkRuntimeEffect.Uniform> = listOf(
            SkRuntimeEffect.Uniform(
                name = "rad_scale",
                offset = 0,
                type = SkRuntimeEffect.Uniform.Type.kFloat,
                count = 1,
                flags = 0,
            ),
            SkRuntimeEffect.Uniform(
                name = "in_center",
                offset = 8,
                type = SkRuntimeEffect.Uniform.Type.kFloat2,
                count = 1,
                flags = 0,
            ),
            SkRuntimeEffect.Uniform(
                name = "in_colors0",
                offset = 16,
                type = SkRuntimeEffect.Uniform.Type.kFloat4,
                count = 1,
                flags = SkRuntimeEffect.Uniform.kColor_Flag,
            ),
            SkRuntimeEffect.Uniform(
                name = "in_colors1",
                offset = 32,
                type = SkRuntimeEffect.Uniform.Type.kFloat4,
                count = 1,
                flags = SkRuntimeEffect.Uniform.kColor_Flag,
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
            val p = coords ?: return SkColor4f.kBlack

            uniforms.position(0)
            val radScale = uniforms.float
            uniforms.position(8)
            val centerX = uniforms.float
            val centerY = uniforms.float
            uniforms.position(16)
            val c0R = uniforms.float
            val c0G = uniforms.float
            val c0B = uniforms.float
            val c0A = uniforms.float
            val c1R = uniforms.float
            val c1G = uniforms.float
            val c1B = uniforms.float
            val c1A = uniforms.float

            val ppX = p.fX - centerX
            val ppY = p.fY - centerY
            // length(pp) → sqrt(x^2 + y^2), then sqrt'd again.
            val rOnce = sqrt(ppX * ppX + ppY * ppY)
            val radius = sqrt(rOnce)
            // SkSL one-arg atan(y/x) ≡ Kotlin atan(y/x) — range [-π/2, π/2].
            // pp.x = 0 → divide-by-zero ; SkSL returns ±π/2 ; Kotlin's
            // atan(±Inf) does the same. NaN at (0, 0) only — match SkSL.
            val angle = atan(ppY / ppX)
            var t = (angle + (PI / 2f)) / PI
            t += radius * radScale
            // fract(x) = x - floor(x) ; matches SkSL definition for
            // both positive and negative inputs.
            t -= floor(t)

            val one = 1f - t
            return SkColor4f(
                fR = c0R * one + c1R * t,
                fG = c0G * one + c1G * t,
                fB = c0B * one + c1B * t,
                fA = c0A * one + c1A * t,
            )
        }

        private const val PI: Float = 3.1415926f
    }

    /**
     * `gLinearGradientSkSL` — illustrates the difference between
     * mixing colours in encoded sRGB vs linear sRGB. The top
     * half of the GM (when `p.y < 32`) lerps the two uniforms
     * directly ; the bottom half lifts them through the sRGB
     * transfer function, lerps in linear space, and folds them
     * back through the inverse transfer.
     *
     * Uniforms layout :
     *  - `in_colors0` : `float4` at offset  0 — 16 bytes.
     *  - `in_colors1` : `float4` at offset 16 — 16 bytes.
     *  - Total : 32 bytes.
     *
     * `toLinearSrgb` / `fromLinearSrgb` intrinsics : we use the
     * existing [SkNamedTransferFn.kSRGB] (encoded → linear) and
     * its inverse via [skcmsTransferFunctionInvert]. Skia's
     * SkSL intrinsic uses an additive 1f4 alpha for
     * `fromLinearSrgb(...).rgb1` — we mirror that with the
     * `.rgb1` swizzle (alpha = 1, not the lerp's alpha).
     */
    public object LinearGradientRTImpl : SkRuntimeImpl {
        override val uniforms: List<SkRuntimeEffect.Uniform> = listOf(
            SkRuntimeEffect.Uniform(
                name = "in_colors0",
                offset = 0,
                type = SkRuntimeEffect.Uniform.Type.kFloat4,
                count = 1,
                flags = SkRuntimeEffect.Uniform.kColor_Flag,
            ),
            SkRuntimeEffect.Uniform(
                name = "in_colors1",
                offset = 16,
                type = SkRuntimeEffect.Uniform.Type.kFloat4,
                count = 1,
                flags = SkRuntimeEffect.Uniform.kColor_Flag,
            ),
        )
        override val children: List<SkRuntimeEffect.Child> = emptyList()
        override val flags: Int = SkRuntimeEffect.kAllowShader_Flag or
            SkRuntimeEffect.kUsesColorTransform_Flag

        override fun shade(
            coords: SkPoint?,
            srcColor: SkColor4f?,
            dstColor: SkColor4f?,
            uniforms: ByteBuffer,
            children: Array<ChildResolver>,
        ): SkColor4f {
            val p = coords ?: return SkColor4f.kBlack

            uniforms.position(0)
            val c0R = uniforms.float
            val c0G = uniforms.float
            val c0B = uniforms.float
            val c0A = uniforms.float
            val c1R = uniforms.float
            val c1G = uniforms.float
            val c1B = uniforms.float
            val c1A = uniforms.float

            val t = p.fX / 256f
            return if (p.fY < 32f) {
                // mix(a, b, t) = a*(1-t) + b*t per channel — encoded sRGB.
                val one = 1f - t
                SkColor4f(
                    fR = c0R * one + c1R * t,
                    fG = c0G * one + c1G * t,
                    fB = c0B * one + c1B * t,
                    fA = c0A * one + c1A * t,
                )
            } else {
                // Lift the RGB triple through the sRGB transfer fn.
                val l0R = srgbToLinear(c0R)
                val l0G = srgbToLinear(c0G)
                val l0B = srgbToLinear(c0B)
                val l1R = srgbToLinear(c1R)
                val l1G = srgbToLinear(c1G)
                val l1B = srgbToLinear(c1B)
                val one = 1f - t
                val mR = l0R * one + l1R * t
                val mG = l0G * one + l1G * t
                val mB = l0B * one + l1B * t
                // .rgb1 swizzle : alpha replaced by 1, not lerp's α.
                SkColor4f(
                    fR = linearToSrgb(mR),
                    fG = linearToSrgb(mG),
                    fB = linearToSrgb(mB),
                    fA = 1f,
                )
            }
        }

        // toLinearSrgb intrinsic — sRGB encoded value → linear scalar.
        private fun srgbToLinear(c: Float): Float =
            skcmsTransferFunctionEval(SkNamedTransferFn.kSRGB, c)

        // fromLinearSrgb intrinsic — linear scalar → sRGB encoded value.
        // Compute the inverse TF once at class init.
        private val kSRGBInv = skcmsTransferFunctionInvert(SkNamedTransferFn.kSRGB)
            ?: error("kSRGB transfer function is not invertible — should never happen")

        private fun linearToSrgb(l: Float): Float =
            skcmsTransferFunctionEval(kSRGBInv, l)
    }

    private const val INV_255: Float = 1f / 255f

    private fun SkRuntimeImpl.dispatchMetadata(
        stableId: String,
        cpuImplementationId: String,
    ): org.skia.effects.runtime.SkRuntimeEffectDispatchMetadata =
        org.skia.effects.runtime.SkRuntimeEffectDispatchMetadata(
            stableId = stableId,
            kind = SkRuntimeEffect.Kind.kShader,
            uniforms = uniforms,
            children = children,
            flags = flags,
            cpuImplementationId = cpuImplementationId,
        )
}
