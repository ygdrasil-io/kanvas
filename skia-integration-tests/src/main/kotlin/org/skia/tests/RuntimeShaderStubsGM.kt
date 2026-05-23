package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

// ─── Stubs for runtimeshader.cpp GMs that require missing API ────────────────
//
// Each class below stubs out one GM from `gm/runtimeshader.cpp` that cannot
// yet be fully ported due to unimplemented kanvas-skia infrastructure.
// The TODO tags name the exact missing capability.
//
// | GM name                   | Missing API           | Tag                        |
// |:--------------------------|:----------------------|:---------------------------|
// | child_sampling_rt         | passthrough child impl| STUB.RUNTIME_SHADER_CHILD  |
// | clip_super_rrect_pow2     | SkRuntimeShaderBuilder| STUB.CLIP_SUPER_RRECT      |
// | clip_super_rrect_pow3.5   | SkRuntimeShaderBuilder| STUB.CLIP_SUPER_RRECT      |
// | paint_alpha_normals_rt    | normal_map_shader impl| STUB.NORMAL_MAP_SHADER     |
// | raw_image_shader_normals_rt| makeRawShader + CS   | STUB.RAW_IMAGE_SHADER      |
// | lit_shader_linear_rt      | lit_shader impl       | STUB.LIT_SHADER_LINEAR     |
// | local_matrix_shader_rt    | passthrough impl      | STUB.LOCAL_MATRIX_RT       |
// | null_child_rt             | null-child dispatch   | STUB.NULL_CHILD_RT         |
// | deferred_shader_rt        | SkRuntimeEffectPriv   | STUB.DEFERRED_SHADER       |
// | alpha_image_shader_rt     | alpha-image paint opt | STUB.ALPHA_IMAGE_SHADER    |

/**
 * Stub for `child_sampling_rt` (256 × 256, `gm/runtimeshader.cpp:646`).
 *
 * Requires a "passthrough child" runtime shader — `uniform shader child; half4
 * main(float2 xy) { return child.eval(xy*0.1); }` — that samples a raster
 * surface with a linear child sampler and no-op uniforms.  The SkRuntimeImpl
 * for this program has not been registered in SkRuntimeEffectDispatch.
 */
public class ChildSamplingRtGM : GM() {
    override fun getName(): String = "child_sampling_rt"
    override fun getISize(): SkISize = SkISize.Make(256, 256)
    override fun onDraw(canvas: SkCanvas?) { TODO("STUB.RUNTIME_SHADER_CHILD: passthrough child shader not registered — see SkBuiltinShaderEffectsChildren") }
}

/**
 * Stub for `clip_super_rrect_pow2` (500 × 500, `gm/runtimeshader.cpp:585`).
 *
 * Uses `SkCanvas::clipShader` with a `ClipSuperRRect` runtime shader that
 * implements a superellipse (x^2 + y^2 = 1, i.e. normal round rect).  The
 * 2×2 `derivatives` uniform requires knowledge of the canvas CTM at draw
 * time and is computed from `canvas->getTotalMatrix()`.  This combined with
 * the missing `ClipSuperRRectImpl` makes a full port infeasible until the
 * impl is registered.
 */
public class ClipSuperRRectPow2GM : GM() {
    override fun getName(): String = "clip_super_rrect_pow2"
    override fun getISize(): SkISize = SkISize.Make(500, 500)
    override fun onDraw(canvas: SkCanvas?) { TODO("STUB.CLIP_SUPER_RRECT: ClipSuperRRect shader impl not registered — power=2") }
}

/**
 * Stub for `clip_super_rrect_pow3.5` (500 × 500, `gm/runtimeshader.cpp:587`).
 *
 * Same as [ClipSuperRRectPow2GM] but with power = 3.5. The superellipse
 * computation differs from a standard round rect.
 */
public class ClipSuperRRectPow3p5GM : GM() {
    override fun getName(): String = "clip_super_rrect_pow3.5"
    override fun getISize(): SkISize = SkISize.Make(500, 500)
    override fun onDraw(canvas: SkCanvas?) { TODO("STUB.CLIP_SUPER_RRECT: ClipSuperRRect shader impl not registered — power=3.5") }
}

/**
 * Stub for `paint_alpha_normals_rt` (512 × 512, `gm/runtimeshader.cpp:753`).
 *
 * Requires `normal_map_shader()` (a procedural hemispherical-normal runtime
 * shader) and `lit_shader()` (N-dot-L lighting), neither of which has an
 * SkRuntimeImpl in the dispatch table.  The GM demonstrates paint-alpha
 * inconsistency between CPU and GPU.
 */
public class PaintAlphaNormalsRtGM : GM() {
    override fun getName(): String = "paint_alpha_normals_rt"
    override fun getISize(): SkISize = SkISize.Make(512, 512)
    override fun onDraw(canvas: SkCanvas?) { TODO("STUB.NORMAL_MAP_SHADER: normal_map_shader + lit_shader impls not registered") }
}

/**
 * Stub for `raw_image_shader_normals_rt` (768 × 512, `gm/runtimeshader.cpp:778`).
 *
 * Requires `SkImage::makeRawShader` (bypasses colour-space conversion) and an
 * offscreen surface with a colour-spin colour space (`makeColorSpin`).
 * Neither raw-shader mode nor the colour-spin CS factory is implemented in
 * kanvas-skia.
 */
public class RawImageShaderNormalsRtGM : GM() {
    override fun getName(): String = "raw_image_shader_normals_rt"
    override fun getISize(): SkISize = SkISize.Make(768, 512)
    override fun onDraw(canvas: SkCanvas?) { TODO("STUB.RAW_IMAGE_SHADER: SkImage.makeRawShader + SkColorSpace.makeColorSpin not implemented") }
}

/**
 * Stub for `lit_shader_linear_rt` (512 × 256, `gm/runtimeshader.cpp:830`).
 *
 * Requires `lit_shader_linear()` — a lighting shader that explicitly converts
 * to linear sRGB via `fromLinearSrgb` intrinsic within the impl.  Neither the
 * N-dot-L impl nor the lit-linear variant are registered.
 */
public class LitShaderLinearRtGM : GM() {
    override fun getName(): String = "lit_shader_linear_rt"
    override fun getISize(): SkISize = SkISize.Make(512, 256)
    override fun onDraw(canvas: SkCanvas?) { TODO("STUB.LIT_SHADER_LINEAR: lit_shader_linear impl not registered") }
}

/**
 * Stub for `local_matrix_shader_rt` (256 × 256, `gm/runtimeshader.cpp:862`).
 *
 * Tests `SkShader::makeWithLocalMatrix` on a runtime shader wrapping an image
 * child.  The passthrough SkSL (`uniform shader s; half4 main(float2 p) {
 * return s.eval(p); }`) has no registered impl.  Once registered, this GM
 * would also verify the skbug.com/40044685 double-local-matrix fix.
 */
public class LocalMatrixShaderRtGM : GM() {
    override fun getName(): String = "local_matrix_shader_rt"
    override fun getISize(): SkISize = SkISize.Make(256, 256)
    override fun onDraw(canvas: SkCanvas?) { TODO("STUB.LOCAL_MATRIX_RT: passthrough shader impl not registered") }
}

/**
 * Stub for `null_child_rt` (150 × 100, `gm/runtimeshader.cpp:909`).
 *
 * Tests that a null `ChildPtr` (null shader / null color-filter / null blender)
 * passed to `makeShader` / `makeColorFilter` behaves correctly (transparent-
 * black / identity / src-over respectively).  The SkRuntimeEffect `ChildPtr`
 * null-dispatch path is not yet wired in kanvas-skia's `ChildResolver`.
 */
public class NullChildRtGM : GM() {
    override fun getName(): String = "null_child_rt"
    override fun getISize(): SkISize = SkISize.Make(150, 100)
    override fun onDraw(canvas: SkCanvas?) { TODO("STUB.NULL_CHILD_RT: ChildPtr null-dispatch (null shader/colorFilter/blender) not implemented in ChildResolver") }
}

/**
 * Stub for `deferred_shader_rt` (150 × 50, `gm/runtimeshader.cpp:1020`).
 *
 * Uses `SkRuntimeEffectPriv::MakeDeferredShader` — a private Skia API that
 * re-evaluates the uniform block on every draw via a callback.  This private
 * API is intentionally not exposed in kanvas-skia's public SkRuntimeEffect.
 */
public class DeferredShaderRtGM : GM() {
    override fun getName(): String = "deferred_shader_rt"
    override fun getISize(): SkISize = SkISize.Make(150, 50)
    override fun onDraw(canvas: SkCanvas?) { TODO("STUB.DEFERRED_SHADER: SkRuntimeEffectPriv.MakeDeferredShader is a private Skia API not exposed in kanvas-skia") }
}

/**
 * Stub for `alpha_image_shader_rt` (350 × 50, `gm/runtimeshader.cpp:1064`).
 *
 * Tests that alpha-only images (1×1 alpha-8 bitmap, "paint-color shader") used
 * as runtime-shader children do not get paint-color tinted — only `{0,0,0,a}`
 * should come out regardless of paint colour.  This requires the alpha-image
 * paint-optimization suppression in the SkRuntimeShader child-resolve path,
 * which is not yet implemented.
 */
public class AlphaImageShaderRtGM : GM() {
    override fun getName(): String = "alpha_image_shader_rt"
    override fun getISize(): SkISize = SkISize.Make(350, 50)
    override fun onDraw(canvas: SkCanvas?) { TODO("STUB.ALPHA_IMAGE_SHADER: alpha-image paint-colour suppression in ChildResolver not implemented") }
}
