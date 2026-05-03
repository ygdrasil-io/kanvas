package org.skia.tests

import kotlin.Int
import org.skia.core.FilterResult
import org.skia.core.LayerSpace
import org.skia.core.SkSpecialImage
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSp
import org.skia.foundation.SkTileMode
import org.skia.math.SkIPoint
import org.skia.math.SkIRect
import org.skia.math.SkMatrix
import org.skia.math.SkSize

/**
 * C++ original:
 * ```cpp
 * class ApplyAction {
 *     struct TransformParams {
 *         LayerSpace<SkMatrix> fMatrix;
 *         SkSamplingOptions fSampling;
 *     };
 *     struct CropParams {
 *         LayerSpace<SkIRect> fRect;
 *         SkTileMode fTileMode;
 *         // Sometimes the expected bounds due to cropping and tiling are too hard to automate with
 *         // simple test code.
 *         std::optional<LayerSpace<SkIRect>> fExpectedBounds;
 *     };
 *     struct RescaleParams {
 *         LayerSpace<SkSize> fScale;
 *     };
 *
 * public:
 *     ApplyAction(const SkMatrix& transform,
 *                 const SkSamplingOptions& sampling,
 *                 Expect expectation,
 *                 const SkSamplingOptions& expectedSampling,
 *                 SkTileMode expectedTileMode,
 *                 sk_sp<SkColorFilter> expectedColorFilter)
 *             : fAction{TransformParams{LayerSpace<SkMatrix>(transform), sampling}}
 *             , fExpectation(expectation)
 *             , fExpectedSampling(expectedSampling)
 *             , fExpectedTileMode(expectedTileMode)
 *             , fExpectedColorFilter(std::move(expectedColorFilter)) {}
 *
 *     ApplyAction(const SkIRect& cropRect,
 *                 SkTileMode tileMode,
 *                 std::optional<LayerSpace<SkIRect>> expectedBounds,
 *                 Expect expectation,
 *                 const SkSamplingOptions& expectedSampling,
 *                 SkTileMode expectedTileMode,
 *                 sk_sp<SkColorFilter> expectedColorFilter)
 *             : fAction{CropParams{LayerSpace<SkIRect>(cropRect), tileMode, expectedBounds}}
 *             , fExpectation(expectation)
 *             , fExpectedSampling(expectedSampling)
 *             , fExpectedTileMode(expectedTileMode)
 *             , fExpectedColorFilter(std::move(expectedColorFilter)) {}
 *
 *     ApplyAction(sk_sp<SkColorFilter> colorFilter,
 *                 Expect expectation,
 *                 const SkSamplingOptions& expectedSampling,
 *                 SkTileMode expectedTileMode,
 *                 sk_sp<SkColorFilter> expectedColorFilter)
 *             : fAction(std::move(colorFilter))
 *             , fExpectation(expectation)
 *             , fExpectedSampling(expectedSampling)
 *             , fExpectedTileMode(expectedTileMode)
 *             , fExpectedColorFilter(std::move(expectedColorFilter)) {}
 *
 *     ApplyAction(LayerSpace<SkSize> scale,
 *                 Expect expectation,
 *                 const SkSamplingOptions& expectedSampling,
 *                 SkTileMode expectedTileMode,
 *                 sk_sp<SkColorFilter> expectedColorFilter)
 *             : fAction(RescaleParams{scale})
 *             , fExpectation(expectation)
 *             , fExpectedSampling(expectedSampling)
 *             , fExpectedTileMode(expectedTileMode)
 *             , fExpectedColorFilter(std::move(expectedColorFilter)) {}
 *
 *     // Test-simplified logic for bounds propagation similar to how image filters calculate bounds
 *     // while evaluating a filter DAG, which is outside of skif::FilterResult's responsibilities.
 *     LayerSpace<SkIRect> requiredInput(const LayerSpace<SkIRect>& desiredOutput) const {
 *         if (auto* t = std::get_if<TransformParams>(&fAction)) {
 *             LayerSpace<SkIRect> out;
 *             return t->fMatrix.inverseMapRect(desiredOutput, &out)
 *                     ? out : LayerSpace<SkIRect>::Empty();
 *         } else if (auto* c = std::get_if<CropParams>(&fAction)) {
 *             LayerSpace<SkIRect> intersection = c->fRect;
 *             if (c->fTileMode == SkTileMode::kDecal && !intersection.intersect(desiredOutput)) {
 *                 intersection = LayerSpace<SkIRect>::Empty();
 *             }
 *             return intersection;
 *         } else if (std::holds_alternative<sk_sp<SkColorFilter>>(fAction) ||
 *                    std::holds_alternative<RescaleParams>(fAction)) {
 *             return desiredOutput;
 *         }
 *         SkUNREACHABLE;
 *     }
 *
 *     // Performs the action to be tested
 *     FilterResult apply(const Context& ctx, const FilterResult& in) const {
 *         if (auto* t = std::get_if<TransformParams>(&fAction)) {
 *             return in.applyTransform(ctx, t->fMatrix, t->fSampling);
 *         } else if (auto* c = std::get_if<CropParams>(&fAction)) {
 *             return in.applyCrop(ctx, c->fRect, c->fTileMode);
 *         } else if (auto* cf = std::get_if<sk_sp<SkColorFilter>>(&fAction)) {
 *             return in.applyColorFilter(ctx, *cf);
 *         } else if (auto* s = std::get_if<RescaleParams>(&fAction)) {
 *             return FilterResultTestAccess::Rescale(ctx, in, s->fScale);
 *         }
 *         SkUNREACHABLE;
 *     }
 *
 *     Expect expectation() const { return fExpectation; }
 *     const SkSamplingOptions& expectedSampling() const { return fExpectedSampling; }
 *     SkTileMode expectedTileMode() const { return fExpectedTileMode; }
 *     const SkColorFilter* expectedColorFilter() const { return fExpectedColorFilter.get(); }
 *
 *     std::vector<int> expectedOffscreenSurfaces(const FilterResult& source) const {
 *         if (fExpectation != Expect::kNewImage) {
 *             return {0};
 *         }
 *         if (auto* s = std::get_if<RescaleParams>(&fAction)) {
 *             float minScale = std::min(s->fScale.width(), s->fScale.height());
 *             if (minScale >= 1.f - 0.001f) {
 *                 return {1};
 *             } else {
 *                 auto deferredScale = FilterResultTestAccess::DeferredScaleFactors(source);
 *                 int steps = 0;
 *                 if (deferredScale && std::get<0>(*deferredScale) <= 0.9f) {
 *                     steps++;
 *                 }
 *
 *                 do {
 *                     steps++;
 *                     minScale *= 2.f;
 *                 } while(minScale < 0.9f);
 *
 *
 *                 // Rescaling periodic tiling may require scaling further than the value stored in
 *                 // the action to hit pixel integer bounds, which may trigger one more pass.
 *                 SkTileMode srcTileMode = source.tileMode();
 *                 if (srcTileMode == SkTileMode::kRepeat || srcTileMode == SkTileMode::kMirror) {
 *                     return {steps, steps + 1};
 *                 } else {
 *                     return {steps};
 *                 }
 *             }
 *         } else {
 *             return {1};
 *         }
 *     }
 *
 *     FilterResultTestAccess::ShaderSampleMode expectedSampleMode(const Context& ctx,
 *                                                                 const FilterResult& source) const {
 *         bool actionSupportsDirectDrawing = true;
 *         if (std::holds_alternative<RescaleParams>(fAction)) {
 *             // rescale() normally does not draw directly; the exception is if the source image has
 *             // a scale factor that requires a pre-resolve. If that happens 'source' is not really
 *             // the source of the rescale steps, and `source` can be drawn directly by the resolve.
 *             auto scales = FilterResultTestAccess::DeferredScaleFactors(source);
 *             if (!scales || scales->first > 0.5f) {
 *                 actionSupportsDirectDrawing = false; // no pre-resolve
 *             }
 *         }
 *         return FilterResultTestAccess::GetExpectedShaderSampleMode(
 *                 ctx, source, actionSupportsDirectDrawing);
 *     }
 *
 *     LayerSpace<SkIRect> expectedBounds(const LayerSpace<SkIRect>& inputBounds) const {
 *         // This assumes anything outside 'inputBounds' is transparent black.
 *         if (auto* t = std::get_if<TransformParams>(&fAction)) {
 *             if (inputBounds.isEmpty()) {
 *                 return LayerSpace<SkIRect>::Empty();
 *             }
 *             return t->fMatrix.mapRect(inputBounds);
 *         } else if (auto* c = std::get_if<CropParams>(&fAction)) {
 *             if (c->fExpectedBounds) {
 *                 return *c->fExpectedBounds;
 *             }
 *
 *             LayerSpace<SkIRect> intersection = c->fRect;
 *             if (!intersection.intersect(inputBounds)) {
 *                 return LayerSpace<SkIRect>::Empty();
 *             }
 *             return c->fTileMode == SkTileMode::kDecal
 *                     ? intersection : LayerSpace<SkIRect>(SkRectPriv::MakeILarge());
 *         } else if (auto* cf = std::get_if<sk_sp<SkColorFilter>>(&fAction)) {
 *             if (as_CFB(*cf)->affectsTransparentBlack()) {
 *                 // Fills out infinitely
 *                 return LayerSpace<SkIRect>(SkRectPriv::MakeILarge());
 *             } else {
 *                 return inputBounds;
 *             }
 *         } else if (std::holds_alternative<RescaleParams>(fAction)) {
 *             return inputBounds;
 *         }
 *         SkUNREACHABLE;
 *     }
 *
 *     sk_sp<SkSpecialImage> renderExpectedImage(const Context& ctx,
 *                                               sk_sp<SkSpecialImage> source,
 *                                               LayerSpace<SkIPoint> origin,
 *                                               const LayerSpace<SkIRect>& desiredOutput) const {
 *         Expect effectiveExpectation = fExpectation;
 *         SkISize size(desiredOutput.size());
 *         if (desiredOutput.isEmpty()) {
 *             size = {1, 1};
 *             effectiveExpectation = Expect::kEmptyImage;
 *         }
 *
 *         auto device = ctx.backend()->makeDevice(size, ctx.refColorSpace());
 *         if (!device) {
 *             return nullptr;
 *         }
 *         SkCanvas canvas{device};
 *         canvas.clear(SK_ColorTRANSPARENT);
 *         canvas.translate(-desiredOutput.left(), -desiredOutput.top());
 *
 *         if (effectiveExpectation != Expect::kEmptyImage) {
 *             SkASSERT(source);
 *             LayerSpace<SkIRect> sourceBounds{
 *                     SkIRect::MakeXYWH(origin.x(), origin.y(), source->width(), source->height())};
 *             LayerSpace<SkIRect> expectedBounds = this->expectedBounds(sourceBounds);
 *
 *             canvas.clipIRect(SkIRect(expectedBounds), SkClipOp::kIntersect);
 *
 *             SkPaint paint;
 *             paint.setAntiAlias(true);
 *             paint.setBlendMode(SkBlendMode::kSrc);
 *             // Start with NN to match exact subsetting FilterResult does for deferred images
 *             SkSamplingOptions sampling = {};
 *             SkTileMode tileMode = SkTileMode::kDecal;
 *             if (auto* t = std::get_if<TransformParams>(&fAction)) {
 *                 SkMatrix m{t->fMatrix};
 *                 // FilterResult treats default/bilerp filtering as NN when it has an integer
 *                 // translation, so only change 'sampling' when that is not the case.
 *                 if (!m.isTranslate() ||
 *                     !SkScalarIsInt(m.getTranslateX()) ||
 *                     !SkScalarIsInt(m.getTranslateY())) {
 *                     sampling = t->fSampling;
 *                 }
 *                 canvas.concat(m);
 *             } else if (auto* c = std::get_if<CropParams>(&fAction)) {
 *                 LayerSpace<SkIRect> imageBounds(
 *                         SkIRect::MakeXYWH(origin.x(), origin.y(),
 *                                           source->width(), source->height()));
 *                 if (c->fTileMode == SkTileMode::kDecal || imageBounds.contains(c->fRect)) {
 *                     // Extract a subset of the image
 *                     SkAssertResult(imageBounds.intersect(c->fRect));
 *                     source = source->makeSubset({imageBounds.left() - origin.x(),
 *                                                  imageBounds.top() - origin.y(),
 *                                                  imageBounds.right() - origin.x(),
 *                                                  imageBounds.bottom() - origin.y()});
 *                     origin = imageBounds.topLeft();
 *                 } else {
 *                     // A non-decal tile mode where the image doesn't cover the crop requires the
 *                     // image to be padded out with transparency so the tiling matches 'fRect'.
 *                     SkISize paddedSize = SkISize(c->fRect.size());
 *                     auto paddedDevice = ctx.backend()->makeDevice(paddedSize, ctx.refColorSpace());
 *                     clear_device(paddedDevice.get());
 *                     paddedDevice->drawSpecial(source.get(),
 *                                               SkMatrix::Translate(origin.x() - c->fRect.left(),
 *                                                                   origin.y() - c->fRect.top()),
 *                                               /*sampling=*/{},
 *                                               /*paint=*/{});
 *                     source = paddedDevice->snapSpecial(SkIRect::MakeSize(paddedSize));
 *                     origin = c->fRect.topLeft();
 *                 }
 *                 tileMode = c->fTileMode;
 *             } else if (auto* cf = std::get_if<sk_sp<SkColorFilter>>(&fAction)) {
 *                 paint.setColorFilter(*cf);
 *             } else if (auto* s = std::get_if<RescaleParams>(&fAction)) {
 *                 // Don't redraw with an identity scale since sampling errors creep in on some GPUs
 *                 if (s->fScale.width() != 1.f || s->fScale.height() != 1.f) {
 *                     int origSrcWidth = source->width();
 *                     int origSrcHeight = source->height();
 *                     SkISize lowResSize = {sk_float_ceil2int(origSrcWidth * s->fScale.width()),
 *                                           sk_float_ceil2int(origSrcHeight * s->fScale.height())};
 *
 *                     while (source->width() != lowResSize.width() ||
 *                         source->height() != lowResSize.height()) {
 *                         float sx = std::max(0.5f, lowResSize.width() / (float) source->width());
 *                         float sy = std::max(0.5f, lowResSize.height() / (float) source->height());
 *                         SkISize stepSize = {sk_float_ceil2int(source->width() * sx),
 *                                             sk_float_ceil2int(source->height() * sy)};
 *                         auto stepDevice = ctx.backend()->makeDevice(stepSize, ctx.refColorSpace());
 *                         clear_device(stepDevice.get());
 *                         stepDevice->drawSpecial(source.get(),
 *                                                 SkMatrix::Scale(sx, sy),
 *                                                 SkFilterMode::kLinear,
 *                                                 /*paint=*/{});
 *                         source = stepDevice->snapSpecial(SkIRect::MakeSize(stepSize));
 *                     }
 *
 *                     // Adjust to draw the low-res image upscaled to fill the original image bounds
 *                     sampling = SkFilterMode::kLinear;
 *                     tileMode = SkTileMode::kClamp;
 *                     canvas.translate(origin.x(), origin.y());
 *                     canvas.scale(origSrcWidth / (float) source->width(),
 *                                  origSrcHeight / (float) source->height());
 *                     origin = LayerSpace<SkIPoint>({0, 0});
 *                 }
 *             }
 *             // else it's a rescale action, but for the expected image leave it unmodified.
 *             paint.setShader(source->asShader(tileMode,
 *                                              sampling,
 *                                              SkMatrix::Translate(origin.x(), origin.y())));
 *             canvas.drawPaint(paint);
 *         }
 *         return device->snapSpecial(SkIRect::MakeSize(size));
 *     }
 *
 * private:
 *     // Action
 *     std::variant<TransformParams,     // for applyTransform()
 *                  CropParams,          // for applyCrop()
 *                  sk_sp<SkColorFilter>,// for applyColorFilter()
 *                  RescaleParams        // for rescale()
 *                 > fAction;
 *
 *     // Expectation
 *     Expect fExpectation;
 *     SkSamplingOptions fExpectedSampling;
 *     SkTileMode fExpectedTileMode;
 *     sk_sp<SkColorFilter> fExpectedColorFilter;
 *     // The expected desired outputs and layer bounds are calculated automatically based on the
 *     // action type and parameters to simplify test case specification.
 * }
 * ```
 */
public data class ApplyAction public constructor(
  /**
   * C++ original:
   * ```cpp
   * std::variant<TransformParams,     // for applyTransform()
   *                  CropParams,          // for applyCrop()
   *                  sk_sp<SkColorFilter>,// for applyColorFilter()
   *                  RescaleParams        // for rescale()
   *                 > fAction
   * ```
   */
  private var fAction: Int,
  /**
   * C++ original:
   * ```cpp
   * Expect fExpectation
   * ```
   */
  private var fExpectation: Expect,
  /**
   * C++ original:
   * ```cpp
   * SkSamplingOptions fExpectedSampling
   * ```
   */
  private var fExpectedSampling: SkSamplingOptions,
  /**
   * C++ original:
   * ```cpp
   * SkTileMode fExpectedTileMode
   * ```
   */
  private var fExpectedTileMode: SkTileMode,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorFilter> fExpectedColorFilter
   * ```
   */
  private var fExpectedColorFilter: SkSp<SkColorFilter>,
) {
  /**
   * C++ original:
   * ```cpp
   * LayerSpace<SkIRect> requiredInput(const LayerSpace<SkIRect>& desiredOutput) const {
   *         if (auto* t = std::get_if<TransformParams>(&fAction)) {
   *             LayerSpace<SkIRect> out;
   *             return t->fMatrix.inverseMapRect(desiredOutput, &out)
   *                     ? out : LayerSpace<SkIRect>::Empty();
   *         } else if (auto* c = std::get_if<CropParams>(&fAction)) {
   *             LayerSpace<SkIRect> intersection = c->fRect;
   *             if (c->fTileMode == SkTileMode::kDecal && !intersection.intersect(desiredOutput)) {
   *                 intersection = LayerSpace<SkIRect>::Empty();
   *             }
   *             return intersection;
   *         } else if (std::holds_alternative<sk_sp<SkColorFilter>>(fAction) ||
   *                    std::holds_alternative<RescaleParams>(fAction)) {
   *             return desiredOutput;
   *         }
   *         SkUNREACHABLE;
   *     }
   * ```
   */
  public fun requiredInput(desiredOutput: LayerSpace<SkIRect>): LayerSpace<SkIRect> {
    TODO("Implement requiredInput")
  }

  /**
   * C++ original:
   * ```cpp
   * FilterResult apply(const Context& ctx, const FilterResult& in) const {
   *         if (auto* t = std::get_if<TransformParams>(&fAction)) {
   *             return in.applyTransform(ctx, t->fMatrix, t->fSampling);
   *         } else if (auto* c = std::get_if<CropParams>(&fAction)) {
   *             return in.applyCrop(ctx, c->fRect, c->fTileMode);
   *         } else if (auto* cf = std::get_if<sk_sp<SkColorFilter>>(&fAction)) {
   *             return in.applyColorFilter(ctx, *cf);
   *         } else if (auto* s = std::get_if<RescaleParams>(&fAction)) {
   *             return FilterResultTestAccess::Rescale(ctx, in, s->fScale);
   *         }
   *         SkUNREACHABLE;
   *     }
   * ```
   */
  public fun apply(ctx: Context, `in`: FilterResult): FilterResult {
    TODO("Implement apply")
  }

  /**
   * C++ original:
   * ```cpp
   * Expect expectation() const { return fExpectation; }
   * ```
   */
  public fun expectation(): Expect {
    TODO("Implement expectation")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkSamplingOptions& expectedSampling() const { return fExpectedSampling; }
   * ```
   */
  public fun expectedSampling(): SkSamplingOptions {
    TODO("Implement expectedSampling")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTileMode expectedTileMode() const { return fExpectedTileMode; }
   * ```
   */
  public fun expectedTileMode(): SkTileMode {
    TODO("Implement expectedTileMode")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkColorFilter* expectedColorFilter() const { return fExpectedColorFilter.get(); }
   * ```
   */
  public fun expectedColorFilter(): SkColorFilter {
    TODO("Implement expectedColorFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * std::vector<int> expectedOffscreenSurfaces(const FilterResult& source) const {
   *         if (fExpectation != Expect::kNewImage) {
   *             return {0};
   *         }
   *         if (auto* s = std::get_if<RescaleParams>(&fAction)) {
   *             float minScale = std::min(s->fScale.width(), s->fScale.height());
   *             if (minScale >= 1.f - 0.001f) {
   *                 return {1};
   *             } else {
   *                 auto deferredScale = FilterResultTestAccess::DeferredScaleFactors(source);
   *                 int steps = 0;
   *                 if (deferredScale && std::get<0>(*deferredScale) <= 0.9f) {
   *                     steps++;
   *                 }
   *
   *                 do {
   *                     steps++;
   *                     minScale *= 2.f;
   *                 } while(minScale < 0.9f);
   *
   *
   *                 // Rescaling periodic tiling may require scaling further than the value stored in
   *                 // the action to hit pixel integer bounds, which may trigger one more pass.
   *                 SkTileMode srcTileMode = source.tileMode();
   *                 if (srcTileMode == SkTileMode::kRepeat || srcTileMode == SkTileMode::kMirror) {
   *                     return {steps, steps + 1};
   *                 } else {
   *                     return {steps};
   *                 }
   *             }
   *         } else {
   *             return {1};
   *         }
   *     }
   * ```
   */
  public fun expectedOffscreenSurfaces(source: FilterResult): Int {
    TODO("Implement expectedOffscreenSurfaces")
  }

  /**
   * C++ original:
   * ```cpp
   * FilterResultTestAccess::ShaderSampleMode expectedSampleMode(const Context& ctx,
   *                                                                 const FilterResult& source) const {
   *         bool actionSupportsDirectDrawing = true;
   *         if (std::holds_alternative<RescaleParams>(fAction)) {
   *             // rescale() normally does not draw directly; the exception is if the source image has
   *             // a scale factor that requires a pre-resolve. If that happens 'source' is not really
   *             // the source of the rescale steps, and `source` can be drawn directly by the resolve.
   *             auto scales = FilterResultTestAccess::DeferredScaleFactors(source);
   *             if (!scales || scales->first > 0.5f) {
   *                 actionSupportsDirectDrawing = false; // no pre-resolve
   *             }
   *         }
   *         return FilterResultTestAccess::GetExpectedShaderSampleMode(
   *                 ctx, source, actionSupportsDirectDrawing);
   *     }
   * ```
   */
  public fun expectedSampleMode(ctx: Context, source: FilterResult): Int {
    TODO("Implement expectedSampleMode")
  }

  /**
   * C++ original:
   * ```cpp
   * LayerSpace<SkIRect> expectedBounds(const LayerSpace<SkIRect>& inputBounds) const {
   *         // This assumes anything outside 'inputBounds' is transparent black.
   *         if (auto* t = std::get_if<TransformParams>(&fAction)) {
   *             if (inputBounds.isEmpty()) {
   *                 return LayerSpace<SkIRect>::Empty();
   *             }
   *             return t->fMatrix.mapRect(inputBounds);
   *         } else if (auto* c = std::get_if<CropParams>(&fAction)) {
   *             if (c->fExpectedBounds) {
   *                 return *c->fExpectedBounds;
   *             }
   *
   *             LayerSpace<SkIRect> intersection = c->fRect;
   *             if (!intersection.intersect(inputBounds)) {
   *                 return LayerSpace<SkIRect>::Empty();
   *             }
   *             return c->fTileMode == SkTileMode::kDecal
   *                     ? intersection : LayerSpace<SkIRect>(SkRectPriv::MakeILarge());
   *         } else if (auto* cf = std::get_if<sk_sp<SkColorFilter>>(&fAction)) {
   *             if (as_CFB(*cf)->affectsTransparentBlack()) {
   *                 // Fills out infinitely
   *                 return LayerSpace<SkIRect>(SkRectPriv::MakeILarge());
   *             } else {
   *                 return inputBounds;
   *             }
   *         } else if (std::holds_alternative<RescaleParams>(fAction)) {
   *             return inputBounds;
   *         }
   *         SkUNREACHABLE;
   *     }
   * ```
   */
  public fun expectedBounds(inputBounds: LayerSpace<SkIRect>): LayerSpace<SkIRect> {
    TODO("Implement expectedBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSpecialImage> renderExpectedImage(const Context& ctx,
   *                                               sk_sp<SkSpecialImage> source,
   *                                               LayerSpace<SkIPoint> origin,
   *                                               const LayerSpace<SkIRect>& desiredOutput) const {
   *         Expect effectiveExpectation = fExpectation;
   *         SkISize size(desiredOutput.size());
   *         if (desiredOutput.isEmpty()) {
   *             size = {1, 1};
   *             effectiveExpectation = Expect::kEmptyImage;
   *         }
   *
   *         auto device = ctx.backend()->makeDevice(size, ctx.refColorSpace());
   *         if (!device) {
   *             return nullptr;
   *         }
   *         SkCanvas canvas{device};
   *         canvas.clear(SK_ColorTRANSPARENT);
   *         canvas.translate(-desiredOutput.left(), -desiredOutput.top());
   *
   *         if (effectiveExpectation != Expect::kEmptyImage) {
   *             SkASSERT(source);
   *             LayerSpace<SkIRect> sourceBounds{
   *                     SkIRect::MakeXYWH(origin.x(), origin.y(), source->width(), source->height())};
   *             LayerSpace<SkIRect> expectedBounds = this->expectedBounds(sourceBounds);
   *
   *             canvas.clipIRect(SkIRect(expectedBounds), SkClipOp::kIntersect);
   *
   *             SkPaint paint;
   *             paint.setAntiAlias(true);
   *             paint.setBlendMode(SkBlendMode::kSrc);
   *             // Start with NN to match exact subsetting FilterResult does for deferred images
   *             SkSamplingOptions sampling = {};
   *             SkTileMode tileMode = SkTileMode::kDecal;
   *             if (auto* t = std::get_if<TransformParams>(&fAction)) {
   *                 SkMatrix m{t->fMatrix};
   *                 // FilterResult treats default/bilerp filtering as NN when it has an integer
   *                 // translation, so only change 'sampling' when that is not the case.
   *                 if (!m.isTranslate() ||
   *                     !SkScalarIsInt(m.getTranslateX()) ||
   *                     !SkScalarIsInt(m.getTranslateY())) {
   *                     sampling = t->fSampling;
   *                 }
   *                 canvas.concat(m);
   *             } else if (auto* c = std::get_if<CropParams>(&fAction)) {
   *                 LayerSpace<SkIRect> imageBounds(
   *                         SkIRect::MakeXYWH(origin.x(), origin.y(),
   *                                           source->width(), source->height()));
   *                 if (c->fTileMode == SkTileMode::kDecal || imageBounds.contains(c->fRect)) {
   *                     // Extract a subset of the image
   *                     SkAssertResult(imageBounds.intersect(c->fRect));
   *                     source = source->makeSubset({imageBounds.left() - origin.x(),
   *                                                  imageBounds.top() - origin.y(),
   *                                                  imageBounds.right() - origin.x(),
   *                                                  imageBounds.bottom() - origin.y()});
   *                     origin = imageBounds.topLeft();
   *                 } else {
   *                     // A non-decal tile mode where the image doesn't cover the crop requires the
   *                     // image to be padded out with transparency so the tiling matches 'fRect'.
   *                     SkISize paddedSize = SkISize(c->fRect.size());
   *                     auto paddedDevice = ctx.backend()->makeDevice(paddedSize, ctx.refColorSpace());
   *                     clear_device(paddedDevice.get());
   *                     paddedDevice->drawSpecial(source.get(),
   *                                               SkMatrix::Translate(origin.x() - c->fRect.left(),
   *                                                                   origin.y() - c->fRect.top()),
   *                                               /*sampling=*/{},
   *                                               /*paint=*/{});
   *                     source = paddedDevice->snapSpecial(SkIRect::MakeSize(paddedSize));
   *                     origin = c->fRect.topLeft();
   *                 }
   *                 tileMode = c->fTileMode;
   *             } else if (auto* cf = std::get_if<sk_sp<SkColorFilter>>(&fAction)) {
   *                 paint.setColorFilter(*cf);
   *             } else if (auto* s = std::get_if<RescaleParams>(&fAction)) {
   *                 // Don't redraw with an identity scale since sampling errors creep in on some GPUs
   *                 if (s->fScale.width() != 1.f || s->fScale.height() != 1.f) {
   *                     int origSrcWidth = source->width();
   *                     int origSrcHeight = source->height();
   *                     SkISize lowResSize = {sk_float_ceil2int(origSrcWidth * s->fScale.width()),
   *                                           sk_float_ceil2int(origSrcHeight * s->fScale.height())};
   *
   *                     while (source->width() != lowResSize.width() ||
   *                         source->height() != lowResSize.height()) {
   *                         float sx = std::max(0.5f, lowResSize.width() / (float) source->width());
   *                         float sy = std::max(0.5f, lowResSize.height() / (float) source->height());
   *                         SkISize stepSize = {sk_float_ceil2int(source->width() * sx),
   *                                             sk_float_ceil2int(source->height() * sy)};
   *                         auto stepDevice = ctx.backend()->makeDevice(stepSize, ctx.refColorSpace());
   *                         clear_device(stepDevice.get());
   *                         stepDevice->drawSpecial(source.get(),
   *                                                 SkMatrix::Scale(sx, sy),
   *                                                 SkFilterMode::kLinear,
   *                                                 /*paint=*/{});
   *                         source = stepDevice->snapSpecial(SkIRect::MakeSize(stepSize));
   *                     }
   *
   *                     // Adjust to draw the low-res image upscaled to fill the original image bounds
   *                     sampling = SkFilterMode::kLinear;
   *                     tileMode = SkTileMode::kClamp;
   *                     canvas.translate(origin.x(), origin.y());
   *                     canvas.scale(origSrcWidth / (float) source->width(),
   *                                  origSrcHeight / (float) source->height());
   *                     origin = LayerSpace<SkIPoint>({0, 0});
   *                 }
   *             }
   *             // else it's a rescale action, but for the expected image leave it unmodified.
   *             paint.setShader(source->asShader(tileMode,
   *                                              sampling,
   *                                              SkMatrix::Translate(origin.x(), origin.y())));
   *             canvas.drawPaint(paint);
   *         }
   *         return device->snapSpecial(SkIRect::MakeSize(size));
   *     }
   * ```
   */
  public fun renderExpectedImage(
    ctx: Context,
    source: SkSp<SkSpecialImage>,
    origin: LayerSpace<SkIPoint>,
    desiredOutput: LayerSpace<SkIRect>,
  ): SkSp<SkSpecialImage> {
    TODO("Implement renderExpectedImage")
  }

  public data class TransformParams public constructor(
    public var fMatrix: LayerSpace<SkMatrix>,
    public var fSampling: SkSamplingOptions,
  )

  public data class CropParams public constructor(
    public var fRect: LayerSpace<SkIRect>,
    public var fTileMode: SkTileMode,
    public var fExpectedBounds: Int,
  )

  public data class RescaleParams public constructor(
    public var fScale: LayerSpace<SkSize>,
  )
}
