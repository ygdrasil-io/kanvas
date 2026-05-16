package org.skia.tests

import kotlin.Char
import kotlin.Int
import org.skia.core.FilterResult

/**
 * C++ original:
 * ```cpp
 * class FilterResultImageResolver {
 * public:
 *     enum class Method {
 *         kImageAndOffset,
 *         kDrawToCanvas,
 *         kShader,
 *         kClippedShader,
 *         kStrictShader // Only used to check image correctness when stats reported an optimization
 *     };
 *
 *     FilterResultImageResolver(Method method) : fMethod(method) {}
 *
 *     const char* methodName() const {
 *         switch (fMethod) {
 *             case Method::kImageAndOffset: return "imageAndOffset";
 *             case Method::kDrawToCanvas:   return "drawToCanvas";
 *             case Method::kShader:         return "asShader";
 *             case Method::kClippedShader:  return "asShaderClipped";
 *             case Method::kStrictShader:   return "strictShader";
 *         }
 *         SkUNREACHABLE;
 *     }
 *
 *     std::pair<sk_sp<SkSpecialImage>, SkIPoint> resolve(const Context& ctx,
 *                                                        const FilterResult& image) const {
 *         if (fMethod == Method::kImageAndOffset) {
 *             SkIPoint origin;
 *             sk_sp<SkSpecialImage> resolved = image.imageAndOffset(ctx, &origin);
 *             return {resolved, origin};
 *         } else {
 *             if (ctx.desiredOutput().isEmpty()) {
 *                 return {nullptr, {}};
 *             }
 *
 *             auto device = ctx.backend()->makeDevice(SkISize(ctx.desiredOutput().size()),
 *                                                     ctx.refColorSpace());
 *             SkASSERT(device);
 *
 *             SkCanvas canvas{device};
 *             canvas.clear(SK_ColorTRANSPARENT);
 *             canvas.translate(-ctx.desiredOutput().left(), -ctx.desiredOutput().top());
 *
 *             if (fMethod > Method::kDrawToCanvas) {
 *                 sk_sp<SkShader> shader;
 *                 if (fMethod == Method::kShader) {
 *                     // asShader() applies layer bounds by resolving automatically
 *                     // (e.g. kDrawToCanvas), if sampleBounds is larger than the layer bounds. Since
 *                     // we want to test the unclipped shader version, pass in layerBounds() for
 *                     // sampleBounds and add a clip to the canvas instead.
 *                     canvas.clipIRect(SkIRect(image.layerBounds()));
 *                     shader = FilterResultTestAccess::AsShader(ctx, image, image.layerBounds());
 *                 } else if (fMethod == Method::kClippedShader) {
 *                     shader = FilterResultTestAccess::AsShader(ctx, image, ctx.desiredOutput());
 *                 } else {
 *                     shader = FilterResultTestAccess::StrictShader(ctx, image);
 *                     if (!shader) {
 *                         auto [pixels, origin] = this->resolve(
 *                                 ctx.withNewDesiredOutput(image.layerBounds()), image);
 *                         shader = FilterResultTestAccess::StrictShader(
 *                                 ctx, FilterResult(std::move(pixels), LayerSpace<SkIPoint>(origin)));
 *                     }
 *                 }
 *
 *                 SkPaint paint;
 *                 paint.setShader(std::move(shader));
 *                 canvas.drawPaint(paint);
 *             } else {
 *                 SkASSERT(fMethod == Method::kDrawToCanvas);
 *                 FilterResultTestAccess::Draw(ctx, device.get(), image,
 *                                              /*preserveDeviceState=*/false);
 *             }
 *
 *             return {device->snapSpecial(SkIRect::MakeWH(ctx.desiredOutput().width(),
 *                                                         ctx.desiredOutput().height())),
 *                     SkIPoint(ctx.desiredOutput().topLeft())};
 *         }
 *     }
 *
 * private:
 *     Method fMethod;
 * }
 * ```
 */
public data class FilterResultImageResolver public constructor(
  /**
   * C++ original:
   * ```cpp
   * Method fMethod
   * ```
   */
  private var fMethod: Method,
) {
  /**
   * C++ original:
   * ```cpp
   * const char* methodName() const {
   *         switch (fMethod) {
   *             case Method::kImageAndOffset: return "imageAndOffset";
   *             case Method::kDrawToCanvas:   return "drawToCanvas";
   *             case Method::kShader:         return "asShader";
   *             case Method::kClippedShader:  return "asShaderClipped";
   *             case Method::kStrictShader:   return "strictShader";
   *         }
   *         SkUNREACHABLE;
   *     }
   * ```
   */
  public fun methodName(): Char {
    TODO("Implement methodName")
  }

  /**
   * C++ original:
   * ```cpp
   * std::pair<sk_sp<SkSpecialImage>, SkIPoint> resolve(const Context& ctx,
   *                                                        const FilterResult& image) const {
   *         if (fMethod == Method::kImageAndOffset) {
   *             SkIPoint origin;
   *             sk_sp<SkSpecialImage> resolved = image.imageAndOffset(ctx, &origin);
   *             return {resolved, origin};
   *         } else {
   *             if (ctx.desiredOutput().isEmpty()) {
   *                 return {nullptr, {}};
   *             }
   *
   *             auto device = ctx.backend()->makeDevice(SkISize(ctx.desiredOutput().size()),
   *                                                     ctx.refColorSpace());
   *             SkASSERT(device);
   *
   *             SkCanvas canvas{device};
   *             canvas.clear(SK_ColorTRANSPARENT);
   *             canvas.translate(-ctx.desiredOutput().left(), -ctx.desiredOutput().top());
   *
   *             if (fMethod > Method::kDrawToCanvas) {
   *                 sk_sp<SkShader> shader;
   *                 if (fMethod == Method::kShader) {
   *                     // asShader() applies layer bounds by resolving automatically
   *                     // (e.g. kDrawToCanvas), if sampleBounds is larger than the layer bounds. Since
   *                     // we want to test the unclipped shader version, pass in layerBounds() for
   *                     // sampleBounds and add a clip to the canvas instead.
   *                     canvas.clipIRect(SkIRect(image.layerBounds()));
   *                     shader = FilterResultTestAccess::AsShader(ctx, image, image.layerBounds());
   *                 } else if (fMethod == Method::kClippedShader) {
   *                     shader = FilterResultTestAccess::AsShader(ctx, image, ctx.desiredOutput());
   *                 } else {
   *                     shader = FilterResultTestAccess::StrictShader(ctx, image);
   *                     if (!shader) {
   *                         auto [pixels, origin] = this->resolve(
   *                                 ctx.withNewDesiredOutput(image.layerBounds()), image);
   *                         shader = FilterResultTestAccess::StrictShader(
   *                                 ctx, FilterResult(std::move(pixels), LayerSpace<SkIPoint>(origin)));
   *                     }
   *                 }
   *
   *                 SkPaint paint;
   *                 paint.setShader(std::move(shader));
   *                 canvas.drawPaint(paint);
   *             } else {
   *                 SkASSERT(fMethod == Method::kDrawToCanvas);
   *                 FilterResultTestAccess::Draw(ctx, device.get(), image,
   *                                              /*preserveDeviceState=*/false);
   *             }
   *
   *             return {device->snapSpecial(SkIRect::MakeWH(ctx.desiredOutput().width(),
   *                                                         ctx.desiredOutput().height())),
   *                     SkIPoint(ctx.desiredOutput().topLeft())};
   *         }
   *     }
   * ```
   */
  public fun resolve(ctx: Context, image: FilterResult): Int {
    TODO("Implement resolve")
  }

  public enum class Method {
    kImageAndOffset,
    kDrawToCanvas,
    kShader,
    kClippedShader,
    kStrictShader,
  }
}
