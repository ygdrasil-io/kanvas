package org.skia.tests

import kotlin.Boolean
import kotlin.Double
import kotlin.Float
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class ImageMagnifierBounds : public skiagm::GM {
 * public:
 *     ImageMagnifierBounds() : fX(0.f), fY(0.f) {}
 *
 * protected:
 *     SkString getName() const override { return SkString("imagemagnifier_bounds"); }
 *     SkISize getISize() override { return SkISize::Make(768, 512); }
 *
 *     bool onAnimate(double nanos) override {
 *         fX = TimeUtils::SineWave(nanos, 10.f, 0.f, -200.f, 200.f);
 *         fY = TimeUtils::SineWave(nanos, 10.f, 3.f, -200.f, 200.f);
 *         return true;
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         this->drawRow(canvas, 16.f); // fish eye distortion
 *         canvas->translate(0.f, 256.f);
 *         this->drawRow(canvas, 0.f);  // no distortion, just zoom
 *     }
 *
 * private:
 *
 *     void drawRow(SkCanvas* canvas, float inset) {
 *         // Draw the magnifier two ways: backdrop filtered and then through a saveLayer with a
 *         // regular filter. Lastly draw the un-filtered input. Relevant bounds are displayed on
 *         // top of the rendering:
 *         //  - black = the lens bounding box
 *         //  - red   = the clipped inset lens bounds
 *         //  - blue  = the source of the undistorted magnified content
 *         auto drawBorder = [canvas](SkRect rect, SkColor color,
 *                                    float width, float borderInset = 0.f) {
 *             SkPaint paint;
 *             paint.setStyle(SkPaint::kStroke_Style);
 *             paint.setStrokeWidth(width);
 *             paint.setColor(color);
 *             paint.setAntiAlias(true);
 *
 *             // This draws the original rect (unrounded) when borderInset = 0
 *             rect.inset(borderInset, borderInset);
 *             canvas->drawRRect(SkRRect::MakeRectXY(rect, borderInset, borderInset), paint);
 *         };
 *
 *         // Logically there is a 'widgetBounds' that is the region of pixels to
 *         // be filled with magnified content. Pixels inside widgetBounds are
 *         // scaled up by a factor of 'zoomAmount', with a non linear distortion
 *         // applied to pixels up to 'inset' inside 'widgetBounds'. The specific
 *         // linearly scaled region is termed the 'srcRect' and is adjusted
 *         // dynamically if parts of 'widgetBounds' are offscreen.
 *         SkRect widgetBounds = {16.f, 24.f, 220.f, 248.f};
 *         widgetBounds.offset(fX, fY); // animating helps highlight magnifier behavior
 *
 *         constexpr float kZoomAmount = 2.5f;
 *
 *         // The available content for backdrops, which clips the widgetBounds as it animates.
 *         constexpr SkRect kOutBounds = {0.f, 0.f, 256.f, 256.f};
 *
 *         // The filter responds to any crop (explicit or from missing backdrop content). Compute
 *         // the corresponding clipped bounds and source bounds for visualization purposes.
 *         SkPoint zoomCenter = widgetBounds.center();
 *         SkRect clippedWidget = widgetBounds;
 *         SkAssertResult(clippedWidget.intersect(kOutBounds));
 *         zoomCenter = {SkTPin(zoomCenter.fX, clippedWidget.fLeft, clippedWidget.fRight),
 *                       SkTPin(zoomCenter.fY, clippedWidget.fTop, clippedWidget.fBottom)};
 *         zoomCenter = zoomCenter * (1.f - 1.f / kZoomAmount);
 *         SkRect srcRect = {clippedWidget.fLeft   / kZoomAmount + zoomCenter.fX,
 *                           clippedWidget.fTop    / kZoomAmount + zoomCenter.fY,
 *                           clippedWidget.fRight  / kZoomAmount + zoomCenter.fX,
 *                           clippedWidget.fBottom / kZoomAmount + zoomCenter.fY};
 *
 *         // Internally, the magnifier filter performs equivalent calculations but responds to the
 *         // canvas matrix and available input automatically.
 *         sk_sp<SkImageFilter> magnifier =
 *                 SkImageFilters::Magnifier(widgetBounds, kZoomAmount, inset,
 *                                           SkFilterMode::kLinear, nullptr, kOutBounds);
 *
 *         // Draw once as a backdrop filter
 *         canvas->save();
 *             canvas->clipRect(kOutBounds);
 *             draw_content(canvas, 32.f, 350);
 *             canvas->saveLayer({nullptr, nullptr, magnifier.get(), 0});
 *             canvas->restore();
 *
 *             drawBorder(widgetBounds, SK_ColorBLACK, 2.f);
 *             if (inset > 0.f) {
 *                 drawBorder(clippedWidget, SK_ColorRED, 2.f, inset);
 *             }
 *         canvas->restore();
 *
 *         // Draw once as a regular filter
 *         canvas->save();
 *             canvas->translate(256.f, 0.f);
 *             canvas->clipRect(kOutBounds);
 *
 *             SkPaint paint;
 *             paint.setImageFilter(magnifier);
 *             canvas->saveLayer(nullptr, &paint);
 *                 draw_content(canvas, 32.f, 350);
 *             canvas->restore();
 *
 *             drawBorder(widgetBounds, SK_ColorBLACK, 2.f);
 *             if (inset > 0.f) {
 *                 drawBorder(clippedWidget, SK_ColorRED, 2.f, inset);
 *             }
 *         canvas->restore();
 *
 *         // Draw once unfiltered
 *         canvas->save();
 *             canvas->translate(512.f, 0.f);
 *             canvas->clipRect(kOutBounds);
 *             draw_content(canvas, 32.f, 350);
 *
 *             drawBorder(widgetBounds, SK_ColorBLACK, 2.f);
 *             drawBorder(srcRect, SK_ColorBLUE, 2.f, inset / kZoomAmount);
 *         canvas->restore();
 *     }
 *
 * private:
 *     SkScalar fX;
 *     SkScalar fY;
 * }
 * ```
 */
public open class ImageMagnifierBounds public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkScalar fX
   * ```
   */
  private var fX: SkScalar = TODO("Initialize fX")

  /**
   * C++ original:
   * ```cpp
   * SkScalar fY
   * ```
   */
  private var fY: SkScalar = TODO("Initialize fY")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("imagemagnifier_bounds"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(768, 512); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onAnimate(double nanos) override {
   *         fX = TimeUtils::SineWave(nanos, 10.f, 0.f, -200.f, 200.f);
   *         fY = TimeUtils::SineWave(nanos, 10.f, 3.f, -200.f, 200.f);
   *         return true;
   *     }
   * ```
   */
  protected override fun onAnimate(nanos: Double): Boolean {
    TODO("Implement onAnimate")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         this->drawRow(canvas, 16.f); // fish eye distortion
   *         canvas->translate(0.f, 256.f);
   *         this->drawRow(canvas, 0.f);  // no distortion, just zoom
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawRow(SkCanvas* canvas, float inset) {
   *         // Draw the magnifier two ways: backdrop filtered and then through a saveLayer with a
   *         // regular filter. Lastly draw the un-filtered input. Relevant bounds are displayed on
   *         // top of the rendering:
   *         //  - black = the lens bounding box
   *         //  - red   = the clipped inset lens bounds
   *         //  - blue  = the source of the undistorted magnified content
   *         auto drawBorder = [canvas](SkRect rect, SkColor color,
   *                                    float width, float borderInset = 0.f) {
   *             SkPaint paint;
   *             paint.setStyle(SkPaint::kStroke_Style);
   *             paint.setStrokeWidth(width);
   *             paint.setColor(color);
   *             paint.setAntiAlias(true);
   *
   *             // This draws the original rect (unrounded) when borderInset = 0
   *             rect.inset(borderInset, borderInset);
   *             canvas->drawRRect(SkRRect::MakeRectXY(rect, borderInset, borderInset), paint);
   *         };
   *
   *         // Logically there is a 'widgetBounds' that is the region of pixels to
   *         // be filled with magnified content. Pixels inside widgetBounds are
   *         // scaled up by a factor of 'zoomAmount', with a non linear distortion
   *         // applied to pixels up to 'inset' inside 'widgetBounds'. The specific
   *         // linearly scaled region is termed the 'srcRect' and is adjusted
   *         // dynamically if parts of 'widgetBounds' are offscreen.
   *         SkRect widgetBounds = {16.f, 24.f, 220.f, 248.f};
   *         widgetBounds.offset(fX, fY); // animating helps highlight magnifier behavior
   *
   *         constexpr float kZoomAmount = 2.5f;
   *
   *         // The available content for backdrops, which clips the widgetBounds as it animates.
   *         constexpr SkRect kOutBounds = {0.f, 0.f, 256.f, 256.f};
   *
   *         // The filter responds to any crop (explicit or from missing backdrop content). Compute
   *         // the corresponding clipped bounds and source bounds for visualization purposes.
   *         SkPoint zoomCenter = widgetBounds.center();
   *         SkRect clippedWidget = widgetBounds;
   *         SkAssertResult(clippedWidget.intersect(kOutBounds));
   *         zoomCenter = {SkTPin(zoomCenter.fX, clippedWidget.fLeft, clippedWidget.fRight),
   *                       SkTPin(zoomCenter.fY, clippedWidget.fTop, clippedWidget.fBottom)};
   *         zoomCenter = zoomCenter * (1.f - 1.f / kZoomAmount);
   *         SkRect srcRect = {clippedWidget.fLeft   / kZoomAmount + zoomCenter.fX,
   *                           clippedWidget.fTop    / kZoomAmount + zoomCenter.fY,
   *                           clippedWidget.fRight  / kZoomAmount + zoomCenter.fX,
   *                           clippedWidget.fBottom / kZoomAmount + zoomCenter.fY};
   *
   *         // Internally, the magnifier filter performs equivalent calculations but responds to the
   *         // canvas matrix and available input automatically.
   *         sk_sp<SkImageFilter> magnifier =
   *                 SkImageFilters::Magnifier(widgetBounds, kZoomAmount, inset,
   *                                           SkFilterMode::kLinear, nullptr, kOutBounds);
   *
   *         // Draw once as a backdrop filter
   *         canvas->save();
   *             canvas->clipRect(kOutBounds);
   *             draw_content(canvas, 32.f, 350);
   *             canvas->saveLayer({nullptr, nullptr, magnifier.get(), 0});
   *             canvas->restore();
   *
   *             drawBorder(widgetBounds, SK_ColorBLACK, 2.f);
   *             if (inset > 0.f) {
   *                 drawBorder(clippedWidget, SK_ColorRED, 2.f, inset);
   *             }
   *         canvas->restore();
   *
   *         // Draw once as a regular filter
   *         canvas->save();
   *             canvas->translate(256.f, 0.f);
   *             canvas->clipRect(kOutBounds);
   *
   *             SkPaint paint;
   *             paint.setImageFilter(magnifier);
   *             canvas->saveLayer(nullptr, &paint);
   *                 draw_content(canvas, 32.f, 350);
   *             canvas->restore();
   *
   *             drawBorder(widgetBounds, SK_ColorBLACK, 2.f);
   *             if (inset > 0.f) {
   *                 drawBorder(clippedWidget, SK_ColorRED, 2.f, inset);
   *             }
   *         canvas->restore();
   *
   *         // Draw once unfiltered
   *         canvas->save();
   *             canvas->translate(512.f, 0.f);
   *             canvas->clipRect(kOutBounds);
   *             draw_content(canvas, 32.f, 350);
   *
   *             drawBorder(widgetBounds, SK_ColorBLACK, 2.f);
   *             drawBorder(srcRect, SK_ColorBLUE, 2.f, inset / kZoomAmount);
   *         canvas->restore();
   *     }
   * ```
   */
  private fun drawRow(canvas: SkCanvas?, inset: Float) {
    TODO("Implement drawRow")
  }
}
