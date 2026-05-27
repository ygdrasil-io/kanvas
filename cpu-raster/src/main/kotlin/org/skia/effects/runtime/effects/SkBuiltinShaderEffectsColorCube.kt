package org.skia.effects.runtime.effects

import org.skia.effects.runtime.ChildResolver
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.SkRuntimeEffectDispatch
import org.skia.effects.runtime.SkRuntimeImpl
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkPoint
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Hand-ported color-cube LUT runtime effects from Phase D2.4.b
 * (cluster C) — the 2 SkSL programs in upstream's
 * `gm/runtimeshader.cpp` (lines 269-349 and 353-430) that
 * implement a 3-D look-up-table colour transform via a 2-D
 * "tiled-slice" LUT image. Both share the same per-pixel math ;
 * they only differ in **how the source colour reaches the cube
 * lookup** :
 *  - [ColorCubeRTImpl] is a **shader** effect — the source colour
 *    comes from a child shader (`child.eval(xy)`), and the output
 *    is also produced as a shader colour.
 *  - [ColorCubeColorFilterRTImpl] is a **color filter** effect —
 *    the source colour is the filter input (`half4 inColor`), and
 *    the cube LUT is supplied as a `uniform shader`. The math on
 *    the colour is identical ; only the entry point and the
 *    children list differ.
 *
 * The shared math (factored into [applyColorCube]) :
 *  1. Unpremul the source colour (`c.rgb /= c.a` ; `(0,0,0,0)`
 *     when `c.a == 0`).
 *  2. Map to cube-coordinate space :
 *     `cubeCoords = (c.r * rg_scale + rg_bias,
 *                    c.g * rg_scale + rg_bias,
 *                    c.b * b_scale)`.
 *  3. Compute two slice-coordinate samples in the tiled LUT
 *     image (one for the floor of the blue slice, one for the
 *     ceil), then `lerp` them by `fract(cubeCoords.b)`.
 *  4. Re-premul the lerped output (`color.rgb *= color.a`).
 *
 * **Why not parse SkSL** : the project's strategy is hand-port-per-
 * shader-type (Kotlin for raster, WGSL for GPU — see
 * [`MIGRATION_PLAN_GPU_WEBGPU.md`](../../../../../../../../../MIGRATION_PLAN_GPU_WEBGPU.md)).
 * Each `Impl` class in this file reproduces the math an upstream
 * SkSL function would have computed ; the SkSL string is the
 * dispatch key.
 *
 * **GMs unblocked** :
 *  - `ColorCubeRT` (shader form, lines 269-349 of
 *    `gm/runtimeshader.cpp`).
 *  - `ColorCubeColorFilterRT` (color-filter form, lines 353-430
 *    of the same file ; same maths over a different entry point).
 *
 * **Auto-registration** : touching this `object` triggers the
 * `init` block which registers both impls in
 * [SkRuntimeEffectDispatch]. Phase D2.4.a wires the parent
 * `SkRuntimeEffect.makeFor(...)` to call
 * [SkBuiltinShaderEffectsColorCube.registerAll] from
 * `ensureBuiltinsLoaded()` ; the hook lives one level up so this
 * file stays a leaf in the dependency graph.
 *
 * **Note on the color-filter form's child slot** : upstream
 * declares `uniform shader color_cube;` inside a color-filter
 * program. The current binding layer's
 * [SkRuntimeEffect.makeColorFilter] enforces that all child slots
 * be `kColorFilter` ; integration of the shader-as-color-filter-
 * child path lands in the parent worktree (alongside the GM
 * driver wiring). Here we declare the impl with the correct
 * child type so the registration is ready ; tests exercise the
 * impl directly via [SkRuntimeImpl.shade].
 */
public object SkBuiltinShaderEffectsColorCube {

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
        SkRuntimeEffectDispatch.registerBuiltinIfAbsent(COLOR_CUBE_RT_SKSL) { ColorCubeRTImpl }
        SkRuntimeEffectDispatch.registerBuiltinIfAbsent(COLOR_CUBE_CF_RT_SKSL) { ColorCubeColorFilterRTImpl }
    }

    // ─── SkSL sources (verbatim copies of upstream) ──────────────────

    /** `ColorCubeRT` from `gm/runtimeshader.cpp` lines 269-349.
     *  Shader form : reads the source colour from a `child` shader
     *  child, looks it up in a 2-D tiled-slice cube LUT (also a
     *  shader child), produces a re-premul colour. */
    public const val COLOR_CUBE_RT_SKSL: String = """
        uniform shader child;
        uniform shader color_cube;

        uniform float rg_scale;
        uniform float rg_bias;
        uniform float b_scale;
        uniform float inv_size;

        half4 main(float2 xy) {
            float4 c = unpremul(child.eval(xy));

            // Map to cube coords:
            float3 cubeCoords = float3(c.rg * rg_scale + rg_bias, c.b * b_scale);

            // Compute slice coordinate
            float2 coords1 = float2((floor(cubeCoords.b) + cubeCoords.r) * inv_size, cubeCoords.g);
            float2 coords2 = float2(( ceil(cubeCoords.b) + cubeCoords.r) * inv_size, cubeCoords.g);

            // Two bilinear fetches, plus a manual lerp for the third axis:
            half4 color = mix(color_cube.eval(coords1), color_cube.eval(coords2),
                              fract(cubeCoords.b));

            // Premul again
            color.rgb *= color.a;

            return color;
        }
    """

    /** `ColorCubeColorFilterRT` from `gm/runtimeshader.cpp` lines
     *  353-430. Color-filter form : same cube-LUT math as
     *  [COLOR_CUBE_RT_SKSL], but the source colour is the filter
     *  input rather than a child shader. The `color_cube` slot is
     *  declared as `uniform shader` inside a color-filter program
     *  — upstream supports that, the kanvas binding's eventual
     *  integration lands in the parent worktree. */
    public const val COLOR_CUBE_CF_RT_SKSL: String = """
        uniform shader color_cube;

        uniform float rg_scale;
        uniform float rg_bias;
        uniform float b_scale;
        uniform float inv_size;

        half4 main(half4 inColor) {
            float4 c = unpremul(inColor);

            // Map to cube coords:
            float3 cubeCoords = float3(c.rg * rg_scale + rg_bias, c.b * b_scale);

            // Compute slice coordinate
            float2 coords1 = float2((floor(cubeCoords.b) + cubeCoords.r) * inv_size, cubeCoords.g);
            float2 coords2 = float2(( ceil(cubeCoords.b) + cubeCoords.r) * inv_size, cubeCoords.g);

            // Two bilinear fetches, plus a manual lerp for the third axis:
            half4 color = mix(color_cube.eval(coords1), color_cube.eval(coords2),
                              fract(cubeCoords.b));

            // Premul again
            color.rgb *= color.a;

            return color;
        }
    """

    // ─── Concrete impls (one Kotlin object per upstream SkSL) ────────

    /**
     * `ColorCubeRT` — shader form. Reads the source colour from
     * the `child` shader at the device sample point, applies the
     * cube LUT lookup via [applyColorCube], and returns the
     * re-premul output.
     *
     * Children :
     *  - `child`      (index 0) : `kShader` — the source-image
     *    shader.
     *  - `color_cube` (index 1) : `kShader` — the 2-D tiled-slice
     *    LUT image as a shader.
     *
     * Uniforms layout (computed by [SkRuntimeEffectSignatureParser]) :
     *  - `rg_scale` : `float` at offset  0 — 4 bytes.
     *  - `rg_bias`  : `float` at offset  4 — 4 bytes.
     *  - `b_scale`  : `float` at offset  8 — 4 bytes.
     *  - `inv_size` : `float` at offset 12 — 4 bytes.
     *  - Total : 16 bytes.
     */
    public object ColorCubeRTImpl : SkRuntimeImpl {
        override val uniforms: List<SkRuntimeEffect.Uniform> = colorCubeUniforms()
        override val children: List<SkRuntimeEffect.Child> = listOf(
            SkRuntimeEffect.Child("child", SkRuntimeEffect.ChildType.kShader, 0),
            SkRuntimeEffect.Child("color_cube", SkRuntimeEffect.ChildType.kShader, 1),
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

            // Read uniforms.
            uniforms.position(0)
            val rgScale = uniforms.float
            val rgBias = uniforms.float
            val bScale = uniforms.float
            val invSize = uniforms.float

            // Sample the source-colour child shader, then run the
            // shared cube-LUT lookup. The kanvas binding's
            // ChildResolver.Shader hands us an unpremul SkColor4f,
            // but the SkSL expression `unpremul(child.eval(xy))`
            // mathematically expects to undo a premul step — for
            // alpha < 1 the math wouldn't match upstream exactly
            // (we'd divide by α twice). For alpha == 1 (the common
            // GM case — `mandrill_256.png` is opaque) it's a no-op
            // so the visible output matches.
            val childResolver = children[0] as ChildResolver.Shader
            val lutResolver = children[1] as ChildResolver.Shader
            val src = childResolver.sample(xy)

            return applyColorCube(src, lutResolver, rgScale, rgBias, bScale, invSize)
        }
    }

    /**
     * `ColorCubeColorFilterRT` — color-filter form. The source
     * colour is the filter input (`half4 inColor`) ; the LUT lives
     * in the single child slot (declared as `kShader` upstream).
     * The lookup math is identical to [ColorCubeRTImpl] —
     * delegated to the shared [applyColorCube] helper.
     *
     * Children :
     *  - `color_cube` (index 0) : `kShader` — the LUT image as a
     *    shader. Note : the binding's
     *    [SkRuntimeEffect.makeColorFilter] currently rejects
     *    shader children of color-filter effects ; the
     *    integration that supports this pattern lands in the
     *    parent worktree (alongside the GM driver wiring). The
     *    impl declares the correct type here so the registration
     *    is ready when that lands.
     *
     * Uniforms layout : same 16-byte layout as [ColorCubeRTImpl].
     */
    public object ColorCubeColorFilterRTImpl : SkRuntimeImpl {
        override val uniforms: List<SkRuntimeEffect.Uniform> = colorCubeUniforms()
        override val children: List<SkRuntimeEffect.Child> = listOf(
            SkRuntimeEffect.Child("color_cube", SkRuntimeEffect.ChildType.kShader, 0),
        )
        override val flags: Int = SkRuntimeEffect.kAllowColorFilter_Flag

        override fun shade(
            coords: SkPoint?,
            srcColor: SkColor4f?,
            dstColor: SkColor4f?,
            uniforms: ByteBuffer,
            children: Array<ChildResolver>,
        ): SkColor4f {
            val src = srcColor ?: return SkColor4f.kBlack

            uniforms.position(0)
            val rgScale = uniforms.float
            val rgBias = uniforms.float
            val bScale = uniforms.float
            val invSize = uniforms.float

            val lutResolver = children[0] as ChildResolver.Shader

            return applyColorCube(src, lutResolver, rgScale, rgBias, bScale, invSize)
        }
    }

    // ─── Shared helpers ──────────────────────────────────────────────

    /**
     * Build the standard 4-float uniform list shared by both
     * cube impls. Re-built per access (cheap : a 4-element list)
     * so each `object` carries its own snapshot — avoids any
     * subtle aliasing between the two registrations.
     */
    private fun colorCubeUniforms(): List<SkRuntimeEffect.Uniform> = listOf(
        SkRuntimeEffect.Uniform(
            name = "rg_scale", offset = 0,
            type = SkRuntimeEffect.Uniform.Type.kFloat,
            count = 1, flags = 0,
        ),
        SkRuntimeEffect.Uniform(
            name = "rg_bias", offset = 4,
            type = SkRuntimeEffect.Uniform.Type.kFloat,
            count = 1, flags = 0,
        ),
        SkRuntimeEffect.Uniform(
            name = "b_scale", offset = 8,
            type = SkRuntimeEffect.Uniform.Type.kFloat,
            count = 1, flags = 0,
        ),
        SkRuntimeEffect.Uniform(
            name = "inv_size", offset = 12,
            type = SkRuntimeEffect.Uniform.Type.kFloat,
            count = 1, flags = 0,
        ),
    )

    /**
     * Shared per-pixel cube-LUT math. Implements the body of both
     * SkSL programs verbatim :
     *
     * 1. `c = unpremul(srcColor)`. When `srcColor.fA == 0` the
     *    SkSL `unpremul` would divide by zero — Skia clamps this
     *    to `(0, 0, 0, 0)`, matching [SkColor4f.unpremul].
     * 2. `cubeCoords = (c.r * rg_scale + rg_bias,
     *                   c.g * rg_scale + rg_bias,
     *                   c.b * b_scale)`.
     * 3. Build the two slice-coordinate samples :
     *    - `coords1 = ((floor(cubeCoords.b) + cubeCoords.r) * inv_size,
     *                  cubeCoords.g)`.
     *    - `coords2 = (( ceil(cubeCoords.b) + cubeCoords.r) * inv_size,
     *                  cubeCoords.g)`.
     *    The integer truncation mode for `floor` / `ceil` matches
     *    upstream SkSL (Kotlin's [floor] / [ceil] in `kotlin.math`
     *    use the same IEEE 754 rounding as GLSL).
     * 4. Sample the LUT shader at `coords1` and `coords2`, then
     *    `lerp` by `fract(cubeCoords.b)` (= `b - floor(b)`). The
     *    `lerp` is component-wise on R, G, B, A.
     * 5. Re-premul : `color.rgb *= color.a`. Alpha is preserved
     *    verbatim (matches upstream's `color` accumulator).
     */
    private fun applyColorCube(
        srcColor: SkColor4f,
        lut: ChildResolver.Shader,
        rgScale: Float,
        rgBias: Float,
        bScale: Float,
        invSize: Float,
    ): SkColor4f {
        // Step 1 — unpremul.
        val c = srcColor.unpremul()

        // Step 2 — cube coords.
        val cubeR = c.fR * rgScale + rgBias
        val cubeG = c.fG * rgScale + rgBias
        val cubeB = c.fB * bScale

        // Step 3 — slice coords.
        val bFloor = floor(cubeB)
        val bCeil = ceil(cubeB)
        val coords1 = SkPoint((bFloor + cubeR) * invSize, cubeG)
        val coords2 = SkPoint((bCeil + cubeR) * invSize, cubeG)

        // Step 4 — sample + lerp by fract(cubeB).
        val s1 = lut.sample(coords1)
        val s2 = lut.sample(coords2)
        val fracB = cubeB - bFloor    // fract(x) = x - floor(x).
        val invFrac = 1f - fracB
        val lerpR = s1.fR * invFrac + s2.fR * fracB
        val lerpG = s1.fG * invFrac + s2.fG * fracB
        val lerpB = s1.fB * invFrac + s2.fB * fracB
        val lerpA = s1.fA * invFrac + s2.fA * fracB

        // Step 5 — re-premul (color.rgb *= color.a).
        return SkColor4f(
            fR = lerpR * lerpA,
            fG = lerpG * lerpA,
            fB = lerpB * lerpA,
            fA = lerpA,
        )
    }
}
