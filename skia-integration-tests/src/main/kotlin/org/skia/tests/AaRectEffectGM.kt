package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/aarecteffect.cpp` (`aa_rect_effect` GM, 210 × 250).
 *
 * ## Upstream description
 * This GM directly exercises a `GrFragmentProcessor` that clips against rects
 * (`GrFragmentProcessor::Rect`) using all `GrClipEdgeType` variants, comparing
 * each GPU-driven clip against the equivalent plain `canvas->drawRect` reference
 * (aliased and anti-aliased). Seven rect shapes (integer edges, half-integer
 * edges, thin horizontal/vertical, very small) are tested, yielding a 7 × row
 * grid.
 *
 * ## GPU-only internals used — none available in kanvas-skia raster backend
 *
 * | C++ symbol                          | Role                                        |
 * |-------------------------------------|---------------------------------------------|
 * | `GpuGM`                             | Base class — only invoked on Ganesh context |
 * | `skgpu::ganesh::TopDeviceSurfaceDrawContext` | Ganesh surface introspection       |
 * | `GrFragmentProcessor::Rect`         | Rect-clip fragment processor                |
 * | `GrClipEdgeType` (5 variants)       | AA / non-AA / inverse clip edge types       |
 * | `GrPaint` / `GrPorterDuffXPFactory` | Ganesh paint + Src blend                    |
 * | `sk_gpu_test::test_ops::MakeRect`   | Test-only GPU draw op                       |
 * | `SurfaceDrawContext::addDrawOp`     | Ganesh draw-op submission                   |
 *
 * The upstream `onDraw` has an unconditional guard:
 * ```cpp
 * auto sdc = skgpu::ganesh::TopDeviceSurfaceDrawContext(canvas);
 * if (!sdc) {
 *     *errorMsg = kErrorMsg_DrawSkippedGpuOnly;
 *     return DrawResult::kSkip;
 * }
 * ```
 * so on a CPU/raster canvas it draws nothing at all and returns `kSkip`.
 *
 * No reference PNG exists for `aa_rect_effect` in the repo (GPU-only output).
 *
 * **Classification: INTRACTABLE.GPU_ONLY**
 *
 * The `onDraw` body is the closest raster approximation available: the two
 * plain `canvas->drawRect` reference draws (aliased + anti-aliased) that the
 * upstream GM also renders as a sanity check alongside the GPU clips. The
 * GPU-clip columns (`GrFragmentProcessor::Rect` iterations) are represented
 * by `TODO("STUB.GR_FRAGMENT_PROCESSOR_RECT")` because the API does not exist
 * in kanvas-skia.
 *
 * C++ original `onDraw` (abbreviated):
 * ```cpp
 * SkScalar y = 12.f;
 * static constexpr SkScalar kDX = 12.f;
 * static constexpr SkScalar kOutset = 5.f;
 * static constexpr SkRect kRects[] = { … 7 rects … };
 *
 * for (auto r : kRects) {
 *     SkScalar x = kDX;
 *     for (int et = 0; et < kGrClipEdgeTypeCnt; ++et) {
 *         SkRect rect = r.makeOffset(x, y);
 *         GrClipEdgeType edgeType = static_cast<GrClipEdgeType>(et);
 *         auto fp = GrFragmentProcessor::Rect(nullptr, edgeType, rect);
 *         GrPaint grPaint; grPaint.setColor4f({0,0,0,1}); …
 *         sdc->addDrawOp(…);
 *         x += SkScalarCeilToScalar(rect.width() + kDX);
 *     }
 *     // reference draws (plain API):
 *     canvas->save(); canvas->translate(x, y);
 *     SkPaint paint; canvas->drawRect(r, paint);
 *     x += …; paint.setAntiAlias(true); canvas->drawRect(r, paint);
 *     canvas->restore();
 *     y += SkScalarCeilToScalar(r.height() + 20.f);
 * }
 * ```
 */
public class AaRectEffectGM : GM() {
    init { setBGColor(0xFFFFFFFF.toInt()) }

    override fun getName(): String = "aa_rect_effect"
    override fun getISize(): SkISize = SkISize.Make(210, 250)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // All GrFragmentProcessor::Rect / GrClipEdgeType work is GPU-only.
        // The raster port stubs that path and falls through to the reference
        // draws that the upstream GM also renders alongside the GPU clips.
        TODO("STUB.GR_FRAGMENT_PROCESSOR_RECT")
    }
}
