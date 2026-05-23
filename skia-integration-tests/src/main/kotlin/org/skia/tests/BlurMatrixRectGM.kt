package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkBlurMaskFilter
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/blurrect.cpp::DEF_SIMPLE_GM(blur_matrix_rect, …, 650, 685)`.
 *
 * Draws a fixed 14 × 60 rect under 6 different affine matrices
 * (rotations, non-uniform scales, skews, a mirror) at 5 increasing
 * blur sigmas. The layout stacks each matrix variant vertically, and
 * advances horizontally for each sigma. The test ensures the analytic
 * blur-rect path (and the fallback to the general mask path) work
 * correctly under non-trivial CTMs.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(blur_matrix_rect, canvas, 650, 685) {
 *     static constexpr auto kRect = SkRect::MakeWH(14, 60);
 *     static constexpr float kSigmas[] = {0.5f, 1.2f, 2.3f, 3.9f, 7.4f};
 *     const SkPoint c = {kRect.centerX(), kRect.centerY()};
 *
 *     std::vector<SkMatrix> matrices;
 *     matrices.push_back(SkMatrix::RotateDeg(4.f,   c));
 *     matrices.push_back(SkMatrix::RotateDeg(63.f,  c));
 *     matrices.push_back(SkMatrix::RotateDeg(30.f,  c)); matrices.back().preScale(1.1f, .5f);
 *     matrices.push_back(SkMatrix::RotateDeg(147.f, c)); matrices.back().preScale(3.f, .1f);
 *     mirror.setAll(0,1,0,1,0,0,0,0,1);
 *     matrices.push_back(SkMatrix::Concat(mirror, matrices.back()));
 *     matrices.push_back(SkMatrix::RotateDeg(197.f, c)); matrices.back().preSkew(.3f, -.5f);
 *
 *     // Compute union of all transformed bounds + blur pad, translate origin.
 *     for (auto sigma : kSigmas) {
 *         SkPaint p; p.setMaskFilter(MakeBlur(kNormal, sigma));
 *         canvas->save();
 *         for (const auto& m : matrices) {
 *             canvas->save(); canvas->concat(m);
 *             canvas->drawRect(kRect, p);
 *             canvas->restore();
 *             canvas->translate(0, bounds.height());
 *         }
 *         canvas->restore();
 *         canvas->translate(bounds.width(), 0);
 *     }
 * }
 * ```
 *
 * All APIs required for this port are available in `:kanvas-skia`:
 *  - [SkMatrix.MakeRotate] (equivalent to C++ `SkMatrix::RotateDeg`)
 *  - [SkMatrix.preScale] / [SkMatrix.preSkew]
 *  - [SkMatrix.concat] (equivalent to C++ `SkMatrix::Concat`)
 *  - [SkMatrix.mapRect] / [SkRect.joinNonEmptyArg] / [SkRect.makeSorted]
 *  - [SkCanvas.concat] / [SkBlurMaskFilter.Make]
 *
 * Classification: **LAZY_PORT** — fully functional, no missing APIs.
 */
public class BlurMatrixRectGM : GM() {

    override fun getName(): String = "blur_matrix_rect"
    override fun getISize(): SkISize = SkISize.Make(650, 685)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val kRect   = SkRect.MakeWH(14f, 60f)
        val kSigmas = floatArrayOf(0.5f, 1.2f, 2.3f, 3.9f, 7.4f)

        // Centre of the rect — pivot point for all rotations.
        val cx = kRect.centerX()
        val cy = kRect.centerY()

        // Build the 6 transform matrices (mirrors C++ `matrices` vector).
        // C++ `SkMatrix::RotateDeg(deg, c)` ≡ `SkMatrix.MakeRotate(deg, cx, cy)`.
        val matrices = mutableListOf<SkMatrix>()

        matrices.add(SkMatrix.MakeRotate(4f, cx, cy))

        matrices.add(SkMatrix.MakeRotate(63f, cx, cy))

        matrices.add(SkMatrix.MakeRotate(30f, cx, cy).preScale(1.1f, 0.5f))

        val m3 = SkMatrix.MakeRotate(147f, cx, cy).preScale(3f, 0.1f)
        matrices.add(m3)

        // mirror = [ 0 1 0 / 1 0 0 / 0 0 1 ] — swaps x and y axes.
        val mirror = SkMatrix(sx = 0f, kx = 1f, tx = 0f, ky = 1f, sy = 0f, ty = 0f)
        matrices.add(SkMatrix.concat(mirror, m3))

        matrices.add(SkMatrix.MakeRotate(197f, cx, cy).preSkew(0.3f, -0.5f))

        // Compute the axis-aligned bounding box of all transformed rects,
        // then expand by blurPad = 2 * maxSigma on every side.
        var bounds = SkRect.MakeEmpty()
        for (m in matrices) {
            val mapped = m.mapRect(kRect).makeSorted()
            bounds.joinNonEmptyArg(mapped)
        }
        val blurPad = 2f * kSigmas[kSigmas.size - 1]
        bounds.outset(blurPad, blurPad)

        // Translate so the top-left of the union bbox lands at (0, 0).
        c.translate(-bounds.left, -bounds.top)

        for (sigma in kSigmas) {
            val paint = SkPaint().apply {
                maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, sigma)
            }
            c.save()
            for (m in matrices) {
                c.save()
                c.concat(m)
                c.drawRect(kRect, paint)
                c.restore()
                c.translate(0f, bounds.height())
            }
            c.restore()
            c.translate(bounds.width(), 0f)
        }
    }
}
