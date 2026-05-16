package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class DiscardGM : public GM {
 *
 * public:
 *     DiscardGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("discard"); }
 *
 *     SkISize getISize() override { return SkISize::Make(100, 100); }
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *
 *         SkISize size = this->getISize();
 *         size.fWidth /= 10;
 *         size.fHeight /= 10;
 *         SkImageInfo info = SkImageInfo::MakeN32Premul(size);
 *         sk_sp<SkSurface> surface;
 *
 * #if defined(SK_GANESH)
 *         if (auto dContext = GrAsDirectContext(canvas->recordingContext());
 *             dContext && !dContext->abandoned()) {
 *             surface = SkSurfaces::RenderTarget(dContext, skgpu::Budgeted::kNo, info);
 *         }
 * #endif
 *
 * #if defined(SK_GRAPHITE)
 *         if (auto recorder = canvas->recorder()) {
 *             surface = SkSurfaces::RenderTarget(recorder, info);
 *         }
 * #endif
 *
 *         if (!surface) {
 *             surface = SkSurfaces::Raster(info);
 *         }
 *         if (!surface) {
 *             *errorMsg = "Could not create surface.";
 *             return DrawResult::kFail;
 *         }
 *
 *         canvas->clear(SK_ColorBLACK);
 *
 *         SkRandom rand;
 *         for (int x = 0; x < 10; ++x) {
 *             for (int y = 0; y < 10; ++y) {
 *               surface->getCanvas()->discard();
 *               // Make something that isn't too close to the background color, black.
 *               SkColor color = ToolUtils::color_to_565(rand.nextU() | 0xFF404040);
 *               switch (rand.nextULessThan(3)) {
 *                   case 0:
 *                       surface->getCanvas()->drawColor(color);
 *                       break;
 *                   case 1:
 *                       surface->getCanvas()->clear(color);
 *                       break;
 *                   case 2:
 *                       SkPaint paint;
 *                       paint.setShader(SkShaders::Color(color));
 *                       surface->getCanvas()->drawPaint(paint);
 *                       break;
 *               }
 *               surface->draw(canvas, 10.f*x, 10.f*y);
 *             }
 *         }
 *
 *         surface->getCanvas()->discard();
 *         return DrawResult::kOk;
 *     }
 * }
 * ```
 */
public open class DiscardGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("discard"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(100, 100); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
   *
   *         SkISize size = this->getISize();
   *         size.fWidth /= 10;
   *         size.fHeight /= 10;
   *         SkImageInfo info = SkImageInfo::MakeN32Premul(size);
   *         sk_sp<SkSurface> surface;
   *
   * #if defined(SK_GANESH)
   *         if (auto dContext = GrAsDirectContext(canvas->recordingContext());
   *             dContext && !dContext->abandoned()) {
   *             surface = SkSurfaces::RenderTarget(dContext, skgpu::Budgeted::kNo, info);
   *         }
   * #endif
   *
   * #if defined(SK_GRAPHITE)
   *         if (auto recorder = canvas->recorder()) {
   *             surface = SkSurfaces::RenderTarget(recorder, info);
   *         }
   * #endif
   *
   *         if (!surface) {
   *             surface = SkSurfaces::Raster(info);
   *         }
   *         if (!surface) {
   *             *errorMsg = "Could not create surface.";
   *             return DrawResult::kFail;
   *         }
   *
   *         canvas->clear(SK_ColorBLACK);
   *
   *         SkRandom rand;
   *         for (int x = 0; x < 10; ++x) {
   *             for (int y = 0; y < 10; ++y) {
   *               surface->getCanvas()->discard();
   *               // Make something that isn't too close to the background color, black.
   *               SkColor color = ToolUtils::color_to_565(rand.nextU() | 0xFF404040);
   *               switch (rand.nextULessThan(3)) {
   *                   case 0:
   *                       surface->getCanvas()->drawColor(color);
   *                       break;
   *                   case 1:
   *                       surface->getCanvas()->clear(color);
   *                       break;
   *                   case 2:
   *                       SkPaint paint;
   *                       paint.setShader(SkShaders::Color(color));
   *                       surface->getCanvas()->drawPaint(paint);
   *                       break;
   *               }
   *               surface->draw(canvas, 10.f*x, 10.f*y);
   *             }
   *         }
   *
   *         surface->getCanvas()->discard();
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement onDraw")
  }
}
