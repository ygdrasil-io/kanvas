package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Stub port of Skia's
 * [`gm/textblobrandomfont.cpp`](https://github.com/google/skia/blob/main/gm/textblobrandomfont.cpp)
 * — registered as `DEF_GM(return new TextBlobRandomFont;)`.
 *
 * ## What the GM does
 *
 * Tests that `SkTextBlob` can be translated and scaled with a font that
 * returns random but deterministic masks. The key object is
 * `SkRandomTypeface` (from `tools/fonts/RandomScalerContext.{h,cpp}`),
 * which wraps a real typeface and, for every glyph, generates a random
 * (but reproducible) mask. This exercises the Ganesh glyph-cache
 * invalidation path: after `dContext->freeGpuResources()` the same blob
 * must re-upload correctly.
 *
 * The GM draws the blob three times — two rotated by −0.05 rad and one
 * on an off-screen surface — at a 2000 × 1600 canvas. The upstream text
 * includes:
 *
 *  1. 32pt "The quick brown fox jumps over the lazy dog." with
 *     `SkFont::Edging::kSubpixelAntiAlias` through `SkRandomTypeface`.
 *  2. 160pt "The quick brown fox" and "jumps over the lazy dog."
 *     with `SkFont::Edging::kAntiAlias` (A8 path) through `SkRandomTypeface`.
 *  3. Optionally an emoji sample (from `ToolUtils::EmojiSample()`) also
 *     wrapped in `SkRandomTypeface`, if a color-emoji typeface is available.
 *
 * ## Why STUB.GR_RANDOM_TYPEFACE
 *
 * The entire point of the GM — and its only meaningful draw path — is
 * exercising the Ganesh glyph-atlas regeneration after
 * `GrDirectContext::freeGpuResources()`. Upstream explicitly skips the
 * GM when not on a GPU canvas:
 *
 * ```cpp
 * if (!isGPU) {
 *     *errorMsg = skiagm::GM::kErrorMsg_DrawSkippedGpuOnly;
 *     return skiagm::DrawResult::kSkip;
 * }
 * ```
 *
 * In addition, `SkRandomTypeface` is a test-only scaler context that lives
 * in `tools/fonts/RandomScalerContext.{h,cpp}`. It generates a custom
 * `SkScalerContext` whose `generateImage` fills each glyph cell with
 * deterministic pseudo-random pixels via Skia's private `SkRandom`.
 * Neither the type nor the underlying `SkScalerContext` API is exposed
 * in `:kanvas-skia`'s public surface.
 *
 * Furthermore, `ToolUtils::makeSurface(canvas, info, &props)` requires a
 * live GPU recording context to create a GPU-backed off-screen surface;
 * there is no CPU-raster equivalent.
 *
 * All three missing pieces (GPU canvas detection, `SkRandomTypeface`,
 * and GPU-backed off-screen surface) are Ganesh-only. There is no way to
 * meaningfully emulate this GM on the CPU raster backend.
 *
 * Calling [onDraw] throws `STUB.GR_RANDOM_TYPEFACE`. The matching
 * [TextBlobRandomFontTest] is `@Disabled`.
 *
 * See `MIGRATION_PLAN_GPU_WEBGPU.md` for the GPU-backend roadmap.
 */
public class TextBlobRandomFontGM : GM() {

    override fun getName(): String = "textblobrandomfont"
    override fun getISize(): SkISize = SkISize.Make(kWidth, kHeight)

    override fun onDraw(canvas: SkCanvas?) {
        TODO(
            "STUB.GR_RANDOM_TYPEFACE: textblobrandomfont is GPU-only (upstream returns kSkip on " +
                "raster) and requires SkRandomTypeface (tools/fonts/RandomScalerContext.h) + " +
                "ToolUtils::makeSurface (GPU-backed off-screen surface) + " +
                "GrDirectContext::freeGpuResources() — none available in the CPU raster backend",
        )
    }

    private companion object {
        const val kWidth: Int = 2000
        const val kHeight: Int = 1600
    }
}
