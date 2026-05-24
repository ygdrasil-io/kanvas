package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/swizzle.cpp` `DEF_SIMPLE_GPU_GM(swizzle, ...)` (512 × 512).
 *
 * Upstream draws `images/mandrill_512_q075.jpg` into a GPU
 * `SurfaceFillContext` via a `GrTextureEffect` wrapped in
 * `GrFragmentProcessor::SwizzleOutput(..., skgpu::Swizzle("grb1"))`.
 * The swizzle rearranges the RGBA channels of the source texture:
 * `r→g`, `g→r`, `b→b`, `a→1` (fully opaque), producing a green-channel
 * boosted image with red and green swapped on a white alpha background.
 *
 * **This is a pure Ganesh GPU GM** (`DEF_SIMPLE_GPU_GM`).
 * All the APIs it exercises (`SurfaceFillContext`, `GrFragmentProcessor`,
 * `GrTextureEffect`, `GrMakeCachedBitmapProxyView`, `skgpu::Swizzle`)
 * are Ganesh-internal and have no CPU-raster equivalent in `:kanvas-skia`.
 *
 * The body calls [TODO] with `STUB.GANESH` so the compile contract is
 * satisfied and the stub is traceable. [SwizzleTest] is `@Disabled`
 * because the Ganesh pipeline is not available in the JVM raster backend.
 *
 * See [`MIGRATION_PLAN_GPU_WEBGPU.md`](../../../../../../../../MIGRATION_PLAN_GPU_WEBGPU.md)
 * for the long-term GPU / WebGPU port plan.
 */
public class SwizzleGM : GM() {

    override fun getName(): String = "swizzle"
    override fun getISize(): SkISize = SkISize.Make(512, 512)

    override fun onDraw(canvas: SkCanvas?) {
        // Upstream body (GPU-only):
        //   auto sfc = skgpu::ganesh::TopDeviceSurfaceFillContext(canvas);
        //   SkBitmap bmp; GetResourceAsBitmap("images/mandrill_512_q075.jpg", &bmp);
        //   auto bitmap = GrMippedBitmap::Make(bmp.pixmap());
        //   auto view = GrMakeCachedBitmapProxyView(rContext, bitmap.value(), ...);
        //   auto imgFP = GrTextureEffect::Make(view, alphaType, SkMatrix());
        //   auto fp = GrFragmentProcessor::SwizzleOutput(imgFP, skgpu::Swizzle("grb1"));
        //   sfc->fillWithFP(std::move(fp));
        //
        // All of the above APIs are Ganesh-internal (SurfaceFillContext,
        // GrFragmentProcessor, GrTextureEffect). No CPU-raster equivalent exists.
        TODO("STUB.GANESH: swizzle GM requires GrFragmentProcessor::SwizzleOutput + SurfaceFillContext — Ganesh GPU pipeline not available in :kanvas-skia JVM raster backend")
    }
}
