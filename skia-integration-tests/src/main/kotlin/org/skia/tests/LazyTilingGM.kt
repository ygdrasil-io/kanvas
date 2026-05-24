package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/lazytiling.cpp` — two GMs registered via [DEF_GM]:
 * - `lazytiling_tl` ([kTopLeft_GrSurfaceOrigin])
 * - `lazytiling_bl` ([kBottomLeft_GrSurfaceOrigin])
 *
 * This GM exercises all four tile modes (clamp, repeat, mirror, decal) for a
 * texture that cannot be normalised early — i.e., a **fully-lazy proxy** backed
 * by a `GrProxyProvider::MakeFullyLazyProxy` callback. The body requires:
 *
 * - `GrSurfaceProxyView` / `GrTextureProxy` (Ganesh-internal proxy layer)
 * - `GrProxyProvider::MakeFullyLazyProxy` — GPU-only lazy proxy allocation
 * - `skgpu::ganesh::TopDeviceSurfaceDrawContext` — GPU draw-context introspection
 * - `GrTextureEffect::MakeSubset` — GPU fragment-processor factory
 * - `GrPaint` / `SurfaceDrawContext::drawRect` — Ganesh render path
 *
 * None of these APIs exist in the `:kanvas-skia` raster backend.
 *
 * The upstream class extends `GpuGM` (not plain `GM`) and returns
 * `DrawResult::kSkip` on every non-GPU context, so it produces **no visible
 * output** on a raster canvas. There are no reference PNGs in
 * `src/test/resources/original-888/` for `lazytiling_tl.png` or
 * `lazytiling_bl.png`.
 *
 * Tracked as **STUB.LAZY_TILING_GPU**.
 *
 * Upstream C++ sketch:
 * ```cpp
 * class LazyTilingGM : public GpuGM {
 *     SkString getName() const override {
 *         return SkStringPrintf("lazytiling_%s",
 *             fOrigin == kTopLeft_GrSurfaceOrigin ? "tl" : "bl");
 *     }
 *     SkISize getISize() override { return SkISize::Make(kTotalWidth, kTotalHeight); }
 *
 *     DrawResult onGpuSetup(SkCanvas*, SkString*, GraphiteTestContext*) override {
 *         auto bm = create_bitmap(fContentRect, {...}, fOrigin);
 *         fView = create_view(dContext, bm, fOrigin); // MakeFullyLazyProxy
 *         return DrawResult::kOk;
 *     }
 *
 *     DrawResult onDraw(GrRecordingContext*, SkCanvas*, SkString*) override {
 *         auto sdc = skgpu::ganesh::TopDeviceSurfaceDrawContext(canvas);
 *         for (auto yMode : {kClamp, kRepeat, kMirror, kDecal}) {
 *             for (auto xMode : {kClamp, kRepeat, kMirror, kDecal}) {
 *                 draw_texture(caps, sdc, fView, fContentRect, cellRect,
 *                              texMatrix,
 *                              SkTileModeToWrapMode(xMode),
 *                              SkTileModeToWrapMode(yMode));
 *             }
 *         }
 *         return DrawResult::kOk;
 *     }
 * };
 * DEF_GM(return new LazyTilingGM(kTopLeft_GrSurfaceOrigin);)
 * DEF_GM(return new LazyTilingGM(kBottomLeft_GrSurfaceOrigin);)
 * ```
 */
public class LazyTilingTlGM : GM() {
    override fun getName(): String = "lazytiling_tl"
    override fun getISize(): SkISize = SkISize.Make(kTotalWidth, kTotalHeight)

    override fun onDraw(canvas: SkCanvas?) {
        // GPU-only GM (GpuGM / MakeFullyLazyProxy / GrTextureEffect).
        // The upstream class unconditionally returns kSkip on raster.
        // No raster fallback is possible — all rendering paths are Ganesh-internal.
        TODO("STUB.LAZY_TILING_GPU")
    }

    private companion object {
        private const val kContentSize = 32
        private const val kPad = 4
        // kSkTileModeCount == 4
        private const val kTotalWidth  = (2 * kContentSize + kPad) * 4 + kPad
        private const val kTotalHeight = (2 * kContentSize + kPad) * 4 + kPad
    }
}

/**
 * Bottom-left origin variant of [LazyTilingTlGM].
 *
 * Produces `lazytiling_bl.png` upstream. Same STUB — see [LazyTilingTlGM] for details.
 */
public class LazyTilingBlGM : GM() {
    override fun getName(): String = "lazytiling_bl"
    override fun getISize(): SkISize = SkISize.Make(kTotalWidth, kTotalHeight)

    override fun onDraw(canvas: SkCanvas?) {
        // GPU-only GM (GpuGM / kBottomLeft_GrSurfaceOrigin / MakeFullyLazyProxy).
        // On raster the upstream GM returns kSkip unconditionally.
        TODO("STUB.LAZY_TILING_GPU")
    }

    private companion object {
        private const val kContentSize = 32
        private const val kPad = 4
        private const val kTotalWidth  = (2 * kContentSize + kPad) * 4 + kPad
        private const val kTotalHeight = (2 * kContentSize + kPad) * 4 + kPad
    }
}
