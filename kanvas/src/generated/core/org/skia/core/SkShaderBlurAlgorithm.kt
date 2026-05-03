package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import org.skia.effects.SkRuntimeEffect
import org.skia.effects.SkRuntimeEffectBuilder
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.foundation.SkTileMode
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkSize
import org.skia.math.SkV2
import org.skia.math.SkV4

/**
 * C++ original:
 * ```cpp
 * class SkShaderBlurAlgorithm : public SkBlurEngine::Algorithm {
 * public:
 *     float maxSigma() const override { return kMaxLinearSigma; }
 *     bool supportsOnlyDecalTiling() const override { return false; }
 *
 *     sk_sp<SkSpecialImage> blur(SkSize sigma,
 *                                sk_sp<SkSpecialImage> src,
 *                                const SkIRect& srcRect,
 *                                SkTileMode tileMode,
 *                                const SkIRect& dstRect) const override;
 *
 * private:
 *     // Create a new surface, which can be approx-fit and have undefined contents.
 *     virtual sk_sp<SkDevice> makeDevice(const SkImageInfo&) const = 0;
 *
 *     sk_sp<SkSpecialImage> renderBlur(SkRuntimeEffectBuilder* blurEffectBuilder,
 *                                      SkFilterMode filter,
 *                                      SkISize radii,
 *                                      sk_sp<SkSpecialImage> input,
 *                                      const SkIRect& srcRect,
 *                                      SkTileMode tileMode,
 *                                      const SkIRect& dstRect) const;
 *     sk_sp<SkSpecialImage> evalBlur2D(SkSize sigma,
 *                                      SkISize radii,
 *                                      sk_sp<SkSpecialImage> input,
 *                                      const SkIRect& srcRect,
 *                                      SkTileMode tileMode,
 *                                      const SkIRect& dstRect) const;
 *     sk_sp<SkSpecialImage> evalBlur1D(float sigma,
 *                                      int radius,
 *                                      SkV2 dir,
 *                                      sk_sp<SkSpecialImage> input,
 *                                      SkIRect srcRect,
 *                                      SkTileMode tileMode,
 *                                      SkIRect dstRect) const;
 *
 * // TODO: These are internal details of the blur shaders, but are public for now because multiple
 * // backends invoke the blur shaders directly. Once everything just goes through this class, these
 * // can be hidden.
 * public:
 *
 *     // The kernel width of a Gaussian blur of the given pixel radius, when all pixels are sampled.
 *     static constexpr int KernelWidth(int radius) { return 2 * radius + 1; }
 *
 *     // The kernel width of a Gaussian blur of the given pixel radius, that relies on HW bilinear
 *     // filtering to combine adjacent pixels.
 *     static constexpr int LinearKernelWidth(int radius) { return radius + 1; }
 *
 *     // The maximum sigma that can be computed without downscaling is based on the number of uniforms
 *     // and texture samples the effects will make in a single pass. For 1D passes, the number of
 *     // samples is equal to `LinearKernelWidth`; for 2D passes, it is equal to
 *     // `KernelWidth(radiusX)*KernelWidth(radiusY)`. This maps back to different maximum sigmas
 *     // depending on the approach used, as well as the ratio between the sigmas for the X and Y axes
 *     // if a 2D blur is performed.
 *     static constexpr int kMaxSamples = 28;
 *
 *     // TODO(b/297393474): Update max linear sigma to 9; it had been 4 when a full 1D kernel was
 *     // used, but never updated after the linear filtering optimization reduced the number of
 *     // sample() calls required. Keep it at 4 for now to better isolate performance changes due to
 *     // switching to a runtime effect and constant loop structure.
 *     static constexpr float kMaxLinearSigma = 4.f; // -> radius = 27 -> linear kernel width = 28
 *     // NOTE: There is no defined kMaxBlurSigma for direct 2D blurs since it is entirely dependent on
 *     // the ratio between the two axes' sigmas, but generally it will be small on the order of a
 *     // 5x5 kernel.
 *
 *     // Return a runtime effect that applies a 2D Gaussian blur in a single pass. The returned effect
 *     // can perform arbitrarily sized blur kernels so long as the kernel area is less than
 *     // kMaxSamples. An SkRuntimeEffect is returned to give flexibility for callers to convert it to
 *     // an SkShader or a GrFragmentProcessor. Callers are responsible for providing the uniform
 *     // values (using the appropriate API of the target effect type). The effect declares the
 *     // following uniforms:
 *     //
 *     //    uniform half4  kernel[7];
 *     //    uniform half4  offsets[14];
 *     //    uniform shader child;
 *     //
 *     // 'kernel' should be set to the output of Compute2DBlurKernel(). 'offsets' should be set to the
 *     // output of Compute2DBlurOffsets() with the same 'radii' passed to this function. 'child'
 *     // should be bound to whatever input is intended to be blurred, and can use nearest-neighbor
 *     // sampling (assuming it's an image).
 *     static const SkRuntimeEffect* GetBlur2DEffect(const SkISize& radii);
 *
 *     // Return a runtime effect that applies a 1D Gaussian blur, taking advantage of HW linear
 *     // interpolation to accumulate adjacent pixels with fewer samples. The returned effect can be
 *     // used for both X and Y axes by changing the 'dir' uniform value (see below). It can be used
 *     // for all 1D blurs such that BlurLinearKernelWidth(radius) is less than or equal to
 *     // kMaxSamples. Like GetBlur2DEffect(), the caller is free to convert this to an SkShader or a
 *     // GrFragmentProcessor and is responsible for assigning uniforms with the appropriate API. Its
 *     // uniforms are declared as:
 *     //
 *     //     uniform half4  offsetsAndKernel[14];
 *     //     uniform half2  dir;
 *     //     uniform int    radius;
 *     //     uniform shader child;
 *     //
 *     // 'offsetsAndKernel' should be set to the output of Compute1DBlurLinearKernel(). 'radius'
 *     // should match the radius passed to that function. 'dir' should either be the vector {1,0} or
 *     // {0,1} for X and Y axis passes, respectively. 'child' should be bound to whatever input is
 *     // intended to be blurred and must use linear sampling in order for the outer blur effect to
 *     // function correctly.
 *     static const SkRuntimeEffect* GetLinearBlur1DEffect(int radius);
 *
 *     // Calculates a set of weights for a 2D Gaussian blur of the given sigma and radius. It is
 *     // assumed that the radius was from prior calls to BlurSigmaRadius(sigma.width()|height()) and
 *     // is passed in to avoid redundant calculations.
 *     //
 *     // The provided span is fully written. The kernel is stored in row-major order based on the
 *     // provided radius. Any remaining indices in the span are zero initialized. The span must have
 *     // at least KernelWidth(radius.width())*KernelWidth(radius.height()) elements.
 *     //
 *     // NOTE: These take spans because it can be useful to compute full kernels that are larger than
 *     // what is supported in the GPU effects.
 *     static void Compute2DBlurKernel(SkSize sigma,
 *                                     SkISize radius,
 *                                     SkSpan<float> kernel);
 *
 *     // A convenience function that packs the kMaxBlurSample scalars into SkV4's to match the
 *     // required type of the uniforms in GetBlur2DEffect().
 *     static void Compute2DBlurKernel(SkSize sigma,
 *                                     SkISize radius,
 *                                     std::array<SkV4, kMaxSamples/4>& kernel);
 *
 *     // A convenience for the 2D case where one dimension has a sigma of 0.
 *     static  void Compute1DBlurKernel(float sigma, int radius, SkSpan<float> kernel) {
 *         Compute2DBlurKernel(SkSize{sigma, 0.f}, SkISize{radius, 0}, kernel);
 *     }
 *
 *     // Utility function to fill in 'offsets' for the effect returned by GetBlur2DEffect(). It
 *     // automatically fills in the elements beyond the kernel size with the last real offset to
 *     // maximize texture cache hits. Each offset is really an SkV2 but are packed into SkV4's to
 *     // match the uniform declaration, and are otherwise ordered row-major.
 *     static void Compute2DBlurOffsets(SkISize radius, std::array<SkV4, kMaxSamples/2>& offsets);
 *
 *     // Calculates a set of weights and sampling offsets for a 1D blur that uses GPU hardware to
 *     // linearly combine two logical source pixel values. This assumes that 'radius' was from a prior
 *     // call to BlurSigmaRadius() and is passed in to avoid redundant calculations. To match std140
 *     // uniform packing, the offset and kernel weight for adjacent samples are packed into a single
 *     // SkV4 as {offset[2*i], kernel[2*i], offset[2*i+1], kernel[2*i+1]}
 *     //
 *     // The provided array is fully written to. The calculated values are written to indices 0
 *     // through LinearKernelWidth(radius), with any remaining indices zero initialized.
 *     //
 *     // NOTE: This takes an array of a constrained size because its main use is calculating uniforms
 *     // for an effect with a matching constraint. Knowing the size of the linear kernel means the
 *     // full kernel can be stored on the stack internally.
 *     static void Compute1DBlurLinearKernel(float sigma,
 *                                           int radius,
 *                                           std::array<SkV4, kMaxSamples/2>& offsetsAndKernel);
 *
 * }
 * ```
 */
public abstract class SkShaderBlurAlgorithm : Algorithm() {
  /**
   * C++ original:
   * ```cpp
   * float maxSigma() const override { return kMaxLinearSigma; }
   * ```
   */
  public override fun maxSigma(): Float {
    TODO("Implement maxSigma")
  }

  /**
   * C++ original:
   * ```cpp
   * bool supportsOnlyDecalTiling() const override { return false; }
   * ```
   */
  public override fun supportsOnlyDecalTiling(): Boolean {
    TODO("Implement supportsOnlyDecalTiling")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSpecialImage> SkShaderBlurAlgorithm::blur(SkSize sigma,
   *                                                   sk_sp<SkSpecialImage> src,
   *                                                   const SkIRect& srcRect,
   *                                                   SkTileMode tileMode,
   *                                                   const SkIRect& dstRect) const {
   *     SkASSERT(sigma.width() <= kMaxLinearSigma &&  sigma.height() <= kMaxLinearSigma);
   *
   *     int radiusX = SkBlurEngine::SigmaToRadius(sigma.width());
   *     int radiusY = SkBlurEngine::SigmaToRadius(sigma.height());
   *     const int kernelArea = KernelWidth(radiusX) * KernelWidth(radiusY);
   *     if (kernelArea <= kMaxSamples && radiusX > 0 && radiusY > 0) {
   *         // Use a single-pass 2D kernel if it fits and isn't just 1D already
   *         return this->evalBlur2D(sigma,
   *                                 {radiusX, radiusY},
   *                                 std::move(src),
   *                                 srcRect,
   *                                 tileMode,
   *                                 dstRect);
   *     } else {
   *         // Use two passes of a 1D kernel (one per axis).
   *         SkIRect intermediateSrcRect = srcRect;
   *         SkIRect intermediateDstRect = dstRect;
   *         if (radiusX > 0) {
   *             if (radiusY > 0) {
   *                 // May need to maintain extra rows above and below 'dstRect' for the follow-up pass.
   *                 if (tileMode == SkTileMode::kRepeat || tileMode == SkTileMode::kMirror) {
   *                     // If the srcRect and dstRect are aligned, then we don't need extra rows since
   *                     // the periodic tiling on srcRect is the same for the intermediate. If they
   *                     // are not aligned, then outset by the Y radius.
   *                     const int period = srcRect.height() * (tileMode == SkTileMode::kMirror ? 2 : 1);
   *                     if (std::abs(dstRect.fTop - srcRect.fTop) % period != 0 ||
   *                         dstRect.height() != srcRect.height()) {
   *                         intermediateDstRect.outset(0, radiusY);
   *                     }
   *                 } else {
   *                     // For clamp and decal tiling, we outset by the Y radius up to what's available
   *                     // from the srcRect. Anything beyond that is identical to tiling the
   *                     // intermediate dst image directly.
   *                     intermediateDstRect.outset(0, radiusY);
   *                     intermediateDstRect.fTop = std::max(intermediateDstRect.fTop, srcRect.fTop);
   *                     intermediateDstRect.fBottom =
   *                             std::min(intermediateDstRect.fBottom, srcRect.fBottom);
   *                     if (intermediateDstRect.fTop >= intermediateDstRect.fBottom) {
   *                         return nullptr;
   *                     }
   *                 }
   *             }
   *
   *             src = this->evalBlur1D(sigma.width(),
   *                                    radiusX,
   *                                    /*dir=*/{1.f, 0.f},
   *                                    std::move(src),
   *                                    srcRect,
   *                                    tileMode,
   *                                    intermediateDstRect);
   *             if (!src) {
   *                 return nullptr;
   *             }
   *             intermediateSrcRect = SkIRect::MakeWH(src->width(), src->height());
   *             intermediateDstRect = dstRect.makeOffset(-intermediateDstRect.left(),
   *                                                      -intermediateDstRect.top());
   *         }
   *
   *         if (radiusY > 0) {
   *             src = this->evalBlur1D(sigma.height(),
   *                                    radiusY,
   *                                    /*dir=*/{0.f, 1.f},
   *                                    std::move(src),
   *                                    intermediateSrcRect,
   *                                    tileMode,
   *                                    intermediateDstRect);
   *         }
   *
   *         return src;
   *     }
   * }
   * ```
   */
  public override fun blur(
    sigma: SkSize,
    src: SkSp<SkSpecialImage>,
    srcRect: SkIRect,
    tileMode: SkTileMode,
    dstRect: SkIRect,
  ): SkSp<SkSpecialImage> {
    TODO("Implement blur")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkDevice> makeDevice(const SkImageInfo&) const = 0
   * ```
   */
  private abstract fun makeDevice(param0: SkImageInfo): SkSp<SkDevice>

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSpecialImage> SkShaderBlurAlgorithm::renderBlur(SkRuntimeShaderBuilder* blurEffectBuilder,
   *                                                         SkFilterMode filter,
   *                                                         SkISize radii,
   *                                                         sk_sp<SkSpecialImage> input,
   *                                                         const SkIRect& srcRect,
   *                                                         SkTileMode tileMode,
   *                                                         const SkIRect& dstRect) const {
   *     SkImageInfo outII = SkImageInfo::Make({dstRect.width(), dstRect.height()},
   *                                           input->colorType(),
   *                                           kPremul_SkAlphaType,
   *                                           input->colorInfo().refColorSpace());
   *     sk_sp<SkDevice> device = this->makeDevice(outII);
   *     if (!device) {
   *         return nullptr;
   *     }
   *
   *     SkIRect subset = SkIRect::MakeSize(dstRect.size());
   *     device->clipRect(SkRect::Make(subset), SkClipOp::kIntersect, /*aa=*/false);
   *     device->setLocalToDevice(SkM44::Translate(-dstRect.left(), -dstRect.top()));
   *
   *     // renderBlur() will either mix multiple fast and strict draws to cover dstRect, or will issue
   *     // a single strict draw. While the SkShader object changes (really just strict mode), the rest
   *     // of the SkPaint remains the same.
   *     SkPaint paint;
   *     paint.setBlendMode(SkBlendMode::kSrc);
   *
   *     SkIRect safeSrcRect = srcRect.makeInset(radii.width(), radii.height());
   *     SkIRect fastDstRect = dstRect;
   *
   *     // Only consider the safeSrcRect for shader-based tiling if the original srcRect is different
   *     // from the backing store dimensions; when they match the full image we can use HW tiling.
   *     if (srcRect != SkIRect::MakeSize(input->backingStoreDimensions())) {
   *         if (fastDstRect.intersect(safeSrcRect)) {
   *             // If the area of the non-clamping shader is small, it's better to just issue a single
   *             // draw that performs shader tiling over the whole dst.
   *             if (fastDstRect != dstRect && fastDstRect.width() * fastDstRect.height() < 128 * 128) {
   *                 fastDstRect.setEmpty();
   *             }
   *         } else {
   *             fastDstRect.setEmpty();
   *         }
   *     }
   *
   *     if (!fastDstRect.isEmpty()) {
   *         // Fill as much as possible without adding shader tiling logic to each blur sample,
   *         // switching to clamp tiling if we aren't in this block due to HW tiling.
   *         SkIRect untiledSrcRect = srcRect.makeInset(1, 1);
   *         SkTileMode fastTileMode = untiledSrcRect.contains(fastDstRect) ? SkTileMode::kClamp
   *                                                                        : tileMode;
   *         blurEffectBuilder->child("child") = input->asShader(
   *                 fastTileMode, filter, SkMatrix::I(), /*strict=*/false);
   *         paint.setShader(blurEffectBuilder->makeShader());
   *         device->drawRect(SkRect::Make(fastDstRect), paint);
   *     }
   *
   *     // Switch to a strict shader if there are remaining pixels to fill
   *     if (fastDstRect != dstRect) {
   *         blurEffectBuilder->child("child") = input->makeSubset(srcRect)->asShader(
   *                 tileMode, filter, SkMatrix::Translate(srcRect.left(), srcRect.top()));
   *         paint.setShader(blurEffectBuilder->makeShader());
   *     }
   *
   *     if (fastDstRect.isEmpty()) {
   *         // Fill the entire dst with the strict shader
   *         device->drawRect(SkRect::Make(dstRect), paint);
   *     } else if (fastDstRect != dstRect) {
   *         // There will be up to four additional strict draws to fill in the border. The left and
   *         // right sides will span the full height of the dst rect. The top and bottom will span
   *         // the just the width of the fast interior. Strict border draws with zero width/height
   *         // are skipped.
   *         auto drawBorder = [&](const SkIRect& r) {
   *             if (!r.isEmpty()) {
   *                 device->drawRect(SkRect::Make(r), paint);
   *             }
   *         };
   *
   *         drawBorder({dstRect.left(),      dstRect.top(),
   *                     fastDstRect.left(),  dstRect.bottom()});   // Left, spanning full height
   *         drawBorder({fastDstRect.right(), dstRect.top(),
   *                     dstRect.right(),     dstRect.bottom()});   // Right, spanning full height
   *         drawBorder({fastDstRect.left(),  dstRect.top(),
   *                     fastDstRect.right(), fastDstRect.top()});  // Top, spanning inner width
   *         drawBorder({fastDstRect.left(),  fastDstRect.bottom(),
   *                     fastDstRect.right(), dstRect.bottom()});   // Bottom, spanning inner width
   *     }
   *
   *     return device->snapSpecial(subset);
   * }
   * ```
   */
  private fun renderBlur(
    blurEffectBuilder: SkRuntimeEffectBuilder?,
    filter: SkFilterMode,
    radii: SkISize,
    input: SkSp<SkSpecialImage>,
    srcRect: SkIRect,
    tileMode: SkTileMode,
    dstRect: SkIRect,
  ): SkSp<SkSpecialImage> {
    TODO("Implement renderBlur")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSpecialImage> SkShaderBlurAlgorithm::evalBlur2D(SkSize sigma,
   *                                                         SkISize radii,
   *                                                         sk_sp<SkSpecialImage> input,
   *                                                         const SkIRect& srcRect,
   *                                                         SkTileMode tileMode,
   *                                                         const SkIRect& dstRect) const {
   *     std::array<SkV4, kMaxSamples/4> kernel;
   *     std::array<SkV4, kMaxSamples/2> offsets;
   *     Compute2DBlurKernel(sigma, radii, kernel);
   *     Compute2DBlurOffsets(radii, offsets);
   *
   *     SkRuntimeShaderBuilder builder{sk_ref_sp(GetBlur2DEffect(radii))};
   *     builder.uniform("kernel") = kernel;
   *     builder.uniform("offsets") = offsets;
   *     // NOTE: renderBlur() will configure the "child" shader as needed. The 2D blur effect only
   *     // requires nearest-neighbor filtering.
   *     return this->renderBlur(&builder, SkFilterMode::kNearest, radii,
   *                             std::move(input), srcRect, tileMode, dstRect);
   * }
   * ```
   */
  private fun evalBlur2D(
    sigma: SkSize,
    radii: SkISize,
    input: SkSp<SkSpecialImage>,
    srcRect: SkIRect,
    tileMode: SkTileMode,
    dstRect: SkIRect,
  ): SkSp<SkSpecialImage> {
    TODO("Implement evalBlur2D")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSpecialImage> SkShaderBlurAlgorithm::evalBlur1D(float sigma,
   *                                                         int radius,
   *                                                         SkV2 dir,
   *                                                         sk_sp<SkSpecialImage> input,
   *                                                         SkIRect srcRect,
   *                                                         SkTileMode tileMode,
   *                                                         SkIRect dstRect) const {
   *     std::array<SkV4, kMaxSamples/2> offsetsAndKernel;
   *     Compute1DBlurLinearKernel(sigma, radius, offsetsAndKernel);
   *
   *     SkRuntimeShaderBuilder builder{sk_ref_sp(GetLinearBlur1DEffect(radius))};
   *     builder.uniform("offsetsAndKernel") = offsetsAndKernel;
   *     builder.uniform("dir") = dir;
   *     // NOTE: renderBlur() will configure the "child" shader as needed. The 1D blur effect requires
   *     // linear filtering. Reconstruct the appropriate "2D" radii inset value from 'dir'.
   *     SkISize radii{dir.x ? radius : 0, dir.y ? radius : 0};
   *     return this->renderBlur(&builder, SkFilterMode::kLinear, radii,
   *                             std::move(input), srcRect, tileMode, dstRect);
   * }
   * ```
   */
  private fun evalBlur1D(
    sigma: Float,
    radius: Int,
    dir: SkV2,
    input: SkSp<SkSpecialImage>,
    srcRect: SkIRect,
    tileMode: SkTileMode,
    dstRect: SkIRect,
  ): SkSp<SkSpecialImage> {
    TODO("Implement evalBlur1D")
  }

  public companion object {
    public val kMaxSamples: Int = TODO("Initialize kMaxSamples")

    public val kMaxLinearSigma: Float = TODO("Initialize kMaxLinearSigma")

    /**
     * C++ original:
     * ```cpp
     * static constexpr int KernelWidth(int radius) { return 2 * radius + 1; }
     * ```
     */
    public fun kernelWidth(radius: Int): Int {
      TODO("Implement kernelWidth")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr int LinearKernelWidth(int radius) { return radius + 1; }
     * ```
     */
    public fun linearKernelWidth(radius: Int): Int {
      TODO("Implement linearKernelWidth")
    }

    /**
     * C++ original:
     * ```cpp
     * const SkRuntimeEffect* SkShaderBlurAlgorithm::GetBlur2DEffect(const SkISize& radii) {
     *     int kernelArea = KernelWidth(radii.width()) * KernelWidth(radii.height());
     *     return GetKnownRuntimeEffect(
     *             to_stablekey(kernelArea,
     *                          static_cast<uint32_t>(SkKnownRuntimeEffects::StableKey::k2DBlurBase)));
     * }
     * ```
     */
    public fun getBlur2DEffect(radii: SkISize): SkRuntimeEffect {
      TODO("Implement getBlur2DEffect")
    }

    /**
     * C++ original:
     * ```cpp
     * const SkRuntimeEffect* SkShaderBlurAlgorithm::GetLinearBlur1DEffect(int radius) {
     *     return GetKnownRuntimeEffect(
     *             to_stablekey(LinearKernelWidth(radius),
     *                          static_cast<uint32_t>(SkKnownRuntimeEffects::StableKey::k1DBlurBase)));
     * }
     * ```
     */
    public fun getLinearBlur1DEffect(radius: Int): SkRuntimeEffect {
      TODO("Implement getLinearBlur1DEffect")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkShaderBlurAlgorithm::Compute2DBlurKernel(SkSize sigma,
     *                                                 SkISize radius,
     *                                                 SkSpan<float> kernel) {
     *     // Callers likely had to calculate the radius prior to filling out the kernel value, which is
     *     // why it's provided; but make sure it's consistent with expectations.
     *     SkASSERT(SkBlurEngine::SigmaToRadius(sigma.width()) == radius.width() &&
     *              SkBlurEngine::SigmaToRadius(sigma.height()) == radius.height());
     *
     *     // Callers are responsible for downscaling large sigmas to values that can be processed by the
     *     // effects, so ensure the radius won't overflow 'kernel'
     *     const int width = KernelWidth(radius.width());
     *     const int height = KernelWidth(radius.height());
     *     const size_t kernelSize = SkTo<size_t>(sk_64_mul(width, height));
     *     SkASSERT(kernelSize <= kernel.size());
     *
     *     // And the definition of an identity blur should be sufficient that 2sigma^2 isn't near zero
     *     // when there's a non-trivial radius.
     *     const float twoSigmaSqrdX = 2.0f * sigma.width() * sigma.width();
     *     const float twoSigmaSqrdY = 2.0f * sigma.height() * sigma.height();
     *     SkASSERT((radius.width() == 0 || !SkScalarNearlyZero(twoSigmaSqrdX)) &&
     *              (radius.height() == 0 || !SkScalarNearlyZero(twoSigmaSqrdY)));
     *
     *     // Setting the denominator to 1 when the radius is 0 automatically converts the remaining math
     *     // to the 1D Gaussian distribution. When both radii are 0, it correctly computes a weight of 1.0
     *     const float sigmaXDenom = radius.width() > 0 ? 1.0f / twoSigmaSqrdX : 1.f;
     *     const float sigmaYDenom = radius.height() > 0 ? 1.0f / twoSigmaSqrdY : 1.f;
     *
     *     float sum = 0.0f;
     *     for (int x = 0; x < width; x++) {
     *         float xTerm = static_cast<float>(x - radius.width());
     *         xTerm = xTerm * xTerm * sigmaXDenom;
     *         for (int y = 0; y < height; y++) {
     *             float yTerm = static_cast<float>(y - radius.height());
     *             float xyTerm = std::exp(-(xTerm + yTerm * yTerm * sigmaYDenom));
     *             // Note that the constant term (1/(sqrt(2*pi*sigma^2)) of the Gaussian
     *             // is dropped here, since we renormalize the kernel below.
     *             kernel[y * width + x] = xyTerm;
     *             sum += xyTerm;
     *         }
     *     }
     *     // Normalize the kernel
     *     float scale = 1.0f / sum;
     *     for (size_t i = 0; i < kernelSize; ++i) {
     *         kernel[i] *= scale;
     *     }
     *     // Zero remainder of the array
     *     memset(kernel.data() + kernelSize, 0, sizeof(float)*(kernel.size() - kernelSize));
     * }
     * ```
     */
    public fun compute2DBlurKernel(
      sigma: SkSize,
      radius: SkISize,
      kernel: SkSpan<Float>,
    ) {
      TODO("Implement compute2DBlurKernel")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkShaderBlurAlgorithm::Compute2DBlurKernel(SkSize sigma,
     *                                                 SkISize radii,
     *                                                 std::array<SkV4, kMaxSamples/4>& kernel) {
     *     static_assert(sizeof(kernel) == sizeof(std::array<float, kMaxSamples>));
     *     static_assert(alignof(float) == alignof(SkV4));
     *     float* data = kernel[0].ptr();
     *     Compute2DBlurKernel(sigma, radii, SkSpan<float>(data, kMaxSamples));
     * }
     * ```
     */
    public fun compute2DBlurKernel(
      sigma: SkSize,
      radius: SkISize,
      kernel: Array<SkV4>,
    ) {
      TODO("Implement compute2DBlurKernel")
    }

    /**
     * C++ original:
     * ```cpp
     * static  void Compute1DBlurKernel(float sigma, int radius, SkSpan<float> kernel) {
     *         Compute2DBlurKernel(SkSize{sigma, 0.f}, SkISize{radius, 0}, kernel);
     *     }
     * ```
     */
    public fun compute1DBlurKernel(
      sigma: Float,
      radius: Int,
      kernel: SkSpan<Float>,
    ) {
      TODO("Implement compute1DBlurKernel")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkShaderBlurAlgorithm::Compute2DBlurOffsets(SkISize radius,
     *                                                  std::array<SkV4, kMaxSamples/2>& offsets) {
     *     const int kernelArea = KernelWidth(radius.width()) * KernelWidth(radius.height());
     *     SkASSERT(kernelArea <= kMaxSamples);
     *
     *     SkSpan<float> offsetView{offsets[0].ptr(), kMaxSamples*2};
     *
     *     int i = 0;
     *     for (int y = -radius.height(); y <= radius.height(); ++y) {
     *         for (int x = -radius.width(); x <= radius.width(); ++x) {
     *             offsetView[2*i]   = x;
     *             offsetView[2*i+1] = y;
     *             ++i;
     *         }
     *     }
     *     SkASSERT(i == kernelArea);
     *     const int lastValidOffset = 2*(kernelArea - 1);
     *     for (; i < kMaxSamples; ++i) {
     *         offsetView[2*i]   = offsetView[lastValidOffset];
     *         offsetView[2*i+1] = offsetView[lastValidOffset+1];
     *     }
     * }
     * ```
     */
    public fun compute2DBlurOffsets(radius: SkISize, offsets: Array<SkV4>) {
      TODO("Implement compute2DBlurOffsets")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkShaderBlurAlgorithm::Compute1DBlurLinearKernel(
     *         float sigma,
     *         int radius,
     *         std::array<SkV4, kMaxSamples/2>& offsetsAndKernel) {
     *     SkASSERT(sigma <= kMaxLinearSigma);
     *     SkASSERT(radius == SkBlurEngine::SigmaToRadius(sigma));
     *     SkASSERT(LinearKernelWidth(radius) <= kMaxSamples);
     *
     *     // Given 2 adjacent gaussian points, they are blended as: Wi * Ci + Wj * Cj.
     *     // The GPU will mix Ci and Cj as Ci * (1 - x) + Cj * x during sampling.
     *     // Compute W', x such that W' * (Ci * (1 - x) + Cj * x) = Wi * Ci + Wj * Cj.
     *     // Solving W' * x = Wj, W' * (1 - x) = Wi:
     *     // W' = Wi + Wj
     *     // x = Wj / (Wi + Wj)
     *     auto get_new_weight = [](float* new_w, float* offset, float wi, float wj) {
     *         *new_w = wi + wj;
     *         *offset = wj / (wi + wj);
     *     };
     *
     *     // Create a temporary standard kernel. The maximum blur radius that can be passed to this
     *     // function is (kMaxBlurSamples-1), so make an array large enough to hold the full kernel width.
     *     static constexpr int kMaxKernelWidth = KernelWidth(kMaxSamples - 1);
     *     SkASSERT(KernelWidth(radius) <= kMaxKernelWidth);
     *     std::array<float, kMaxKernelWidth> fullKernel;
     *     Compute1DBlurKernel(sigma, radius, SkSpan<float>{fullKernel.data(), (size_t)KernelWidth(radius)});
     *
     *     std::array<float, kMaxSamples> kernel;
     *     std::array<float, kMaxSamples> offsets;
     *     // Note that halfsize isn't just size / 2, but radius + 1. This is the size of the output array.
     *     int halfSize = LinearKernelWidth(radius);
     *     int halfRadius = halfSize / 2;
     *     int lowIndex = halfRadius - 1;
     *
     *     // Compute1DGaussianKernel produces a full 2N + 1 kernel. Since the kernel can be mirrored,
     *     // compute only the upper half and mirror to the lower half.
     *
     *     int index = radius;
     *     if (radius & 1) {
     *         // If N is odd, then use two samples.
     *         // The centre texel gets sampled twice, so halve its influence for each sample.
     *         // We essentially sample like this:
     *         // Texel edges
     *         // v    v    v    v
     *         // |    |    |    |
     *         // \-----^---/ Lower sample
     *         //      \---^-----/ Upper sample
     *         get_new_weight(&kernel[halfRadius],
     *                        &offsets[halfRadius],
     *                        fullKernel[index] * 0.5f,
     *                        fullKernel[index + 1]);
     *         kernel[lowIndex] = kernel[halfRadius];
     *         offsets[lowIndex] = -offsets[halfRadius];
     *         index++;
     *         lowIndex--;
     *     } else {
     *         // If N is even, then there are an even number of texels on either side of the centre texel.
     *         // Sample the centre texel directly.
     *         kernel[halfRadius] = fullKernel[index];
     *         offsets[halfRadius] = 0.0f;
     *     }
     *     index++;
     *
     *     // Every other pair gets one sample.
     *     for (int i = halfRadius + 1; i < halfSize; index += 2, i++, lowIndex--) {
     *         get_new_weight(&kernel[i], &offsets[i], fullKernel[index], fullKernel[index + 1]);
     *         offsets[i] += static_cast<float>(index - radius);
     *
     *         // Mirror to lower half.
     *         kernel[lowIndex] = kernel[i];
     *         offsets[lowIndex] = -offsets[i];
     *     }
     *
     *     // Zero out remaining values in the kernel
     *     memset(kernel.data() + halfSize, 0, sizeof(float)*(kMaxSamples - halfSize));
     *     // But copy the last valid offset into the remaining offsets, to increase the chance that
     *     // over-iteration in a fragment shader will have a cache hit.
     *     for (int i = halfSize; i < kMaxSamples; ++i) {
     *         offsets[i] = offsets[halfSize - 1];
     *     }
     *
     *     // Interleave into the output array to match the 1D SkSL effect
     *     for (int i = 0; i < kMaxSamples / 2; ++i) {
     *         offsetsAndKernel[i] = SkV4{offsets[2*i], kernel[2*i], offsets[2*i+1], kernel[2*i+1]};
     *     }
     * }
     * ```
     */
    public fun compute1DBlurLinearKernel(
      sigma: Float,
      radius: Int,
      offsetsAndKernel: Array<SkV4>,
    ) {
      TODO("Implement compute1DBlurLinearKernel")
    }
  }
}
