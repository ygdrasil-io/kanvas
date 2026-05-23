package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/rectangletexture.cpp` (`RectangleTexture`,
 * GM name `rectangle_texture`, 1180 × 710).
 *
 * ## What the upstream GM does
 *
 * The GM is gated behind `#ifdef SK_GL` — it is meaningful **only** on
 * the Ganesh/OpenGL backend. In `onGpuSetup` it:
 *  1. Early-returns `kSkip` if the backend is not `kOpenGL_GrBackend` or
 *     if the GL caps do not advertise `rectangleTextureSupport()`.
 *  2. Creates backend textures backed by `GL_TEXTURE_RECTANGLE` (target
 *     `GR_GL_TEXTURE_RECTANGLE` via `GrBackendFormats::MakeGL(GR_GL_RGBA8,
 *     GR_GL_TEXTURE_RECTANGLE)`) using `GrDirectContext::createBackendTexture`
 *     / `GrDirectContext::updateBackendTexture`.
 *  3. Wraps each handle with `SkImages::AdoptTextureFrom` (Ganesh flavour),
 *     producing two 50 × 50 gradient-circle images (kTopLeft and
 *     kBottomLeft origins) and one 2 × 2 four-corner colour image.
 *  4. In `onDraw`, exercises four [SkSamplingOptions] (nearest / linear /
 *     mip-linear / Mitchell cubic) at three scales (1.0 / 1.2 / 0.75),
 *     drawing each gradient image via three paths:
 *     - `drawImage`
 *     - clamp/clamp shader (`makeShader()`)
 *     - repeat/mirror shader (`makeShader(kRepeat, kMirror, …)`)
 *     - `drawImageRect` with `kStrict_SrcRectConstraint`
 *  5. Then tiles the 2 × 2 image across every [SkTileMode] × [SkTileMode]
 *     combination (4 × 4 grid) for each viable sampling option, with a
 *     45 ° rotated / 6.5 × scaled local matrix.
 *
 * ## Why this is INTRACTABLE in kanvas-skia
 *
 * The entire GM depends on the following Ganesh/GL APIs that have no
 * raster equivalent:
 *  - `GrDirectContext::createBackendTexture` — allocates a GL texture
 *    object on the GPU.
 *  - `GrDirectContext::updateBackendTexture` — uploads pixel data to the
 *    GL texture.
 *  - `GrBackendFormats::MakeGL(GR_GL_RGBA8, GR_GL_TEXTURE_RECTANGLE)` —
 *    names the `GL_TEXTURE_RECTANGLE` target; no equivalent exists in a
 *    raster pipeline.
 *  - `SkImages::AdoptTextureFrom(GrDirectContext*, …)` — the Ganesh
 *    texture-adoption factory.
 *  - `GrGLCaps::rectangleTextureSupport()` — queries a GL capability.
 *
 * All of these live in `include/gpu/ganesh/` and `src/gpu/ganesh/gl/`,
 * which are entirely out of scope for `:kanvas-skia` (raster-only facade).
 * Even if the sampling / tiling logic were ported, it would require an
 * actual OpenGL context and GPU-backed texture objects to produce any
 * pixels, making a CPU-raster reference match impossible.
 *
 * Tracked as **STUB.GL_TEXTURE_RECTANGLE**.
 */
public class RectangleTextureGM : GM() {
    init {
        setBGColor(0xFFFFFFFF.toInt())
    }

    override fun getName(): String = "rectangle_texture"
    override fun getISize(): SkISize = SkISize.Make(1180, 710)

    override fun onDraw(canvas: SkCanvas?) {
        // Ganesh-only GM — requires GL_TEXTURE_RECTANGLE backend textures
        // created via GrDirectContext::createBackendTexture /
        // GrBackendFormats::MakeGL(GR_GL_RGBA8, GR_GL_TEXTURE_RECTANGLE) /
        // SkImages::AdoptTextureFrom (Ganesh). None of these exist in the
        // kanvas-skia raster backend.
        TODO("STUB.GL_TEXTURE_RECTANGLE: rectangle_texture GM requires GrDirectContext::createBackendTexture + GrBackendFormats::MakeGL(GR_GL_RGBA8, GR_GL_TEXTURE_RECTANGLE) + SkImages::AdoptTextureFrom — Ganesh/GL-only, no raster equivalent in kanvas-skia")
    }
}
