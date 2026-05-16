package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ComplexClipBlurTiledGM : public GM {
 * public:
 *     ComplexClipBlurTiledGM() {
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("complexclip_blur_tiled"); }
 *
 *     SkISize getISize() override { return SkISize::Make(WIDTH, HEIGHT); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint blurPaint;
 *         blurPaint.setImageFilter(SkImageFilters::Blur(5.0f, 5.0f, nullptr));
 *         const SkScalar tileSize = SkIntToScalar(128);
 *         SkRect bounds = canvas->getLocalClipBounds();
 *         int ts = SkScalarCeilToInt(tileSize);
 *         SkImageInfo info = SkImageInfo::MakeN32Premul(ts, ts);
 *         auto           tileSurface(ToolUtils::makeSurface(canvas, info));
 *         SkCanvas* tileCanvas = tileSurface->getCanvas();
 *         for (SkScalar y = bounds.top(); y < bounds.bottom(); y += tileSize) {
 *             for (SkScalar x = bounds.left(); x < bounds.right(); x += tileSize) {
 *                 tileCanvas->save();
 *                 tileCanvas->clear(0);
 *                 tileCanvas->translate(-x, -y);
 *                 SkRect rect = SkRect::MakeWH(WIDTH, HEIGHT);
 *                 tileCanvas->saveLayer(&rect, &blurPaint);
 *                 SkRRect rrect = SkRRect::MakeRectXY(rect.makeInset(20, 20), 25, 25);
 *                 tileCanvas->clipRRect(rrect, SkClipOp::kDifference, true);
 *                 SkPaint paint;
 *                 tileCanvas->drawRect(rect, paint);
 *                 tileCanvas->restore();
 *                 tileCanvas->restore();
 *                 canvas->drawImage(tileSurface->makeImageSnapshot().get(), x, y);
 *             }
 *         }
 *     }
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class ComplexClipBlurTiledGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("complexclip_blur_tiled"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(WIDTH, HEIGHT); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPaint blurPaint;
   *         blurPaint.setImageFilter(SkImageFilters::Blur(5.0f, 5.0f, nullptr));
   *         const SkScalar tileSize = SkIntToScalar(128);
   *         SkRect bounds = canvas->getLocalClipBounds();
   *         int ts = SkScalarCeilToInt(tileSize);
   *         SkImageInfo info = SkImageInfo::MakeN32Premul(ts, ts);
   *         auto           tileSurface(ToolUtils::makeSurface(canvas, info));
   *         SkCanvas* tileCanvas = tileSurface->getCanvas();
   *         for (SkScalar y = bounds.top(); y < bounds.bottom(); y += tileSize) {
   *             for (SkScalar x = bounds.left(); x < bounds.right(); x += tileSize) {
   *                 tileCanvas->save();
   *                 tileCanvas->clear(0);
   *                 tileCanvas->translate(-x, -y);
   *                 SkRect rect = SkRect::MakeWH(WIDTH, HEIGHT);
   *                 tileCanvas->saveLayer(&rect, &blurPaint);
   *                 SkRRect rrect = SkRRect::MakeRectXY(rect.makeInset(20, 20), 25, 25);
   *                 tileCanvas->clipRRect(rrect, SkClipOp::kDifference, true);
   *                 SkPaint paint;
   *                 tileCanvas->drawRect(rect, paint);
   *                 tileCanvas->restore();
   *                 tileCanvas->restore();
   *                 canvas->drawImage(tileSurface->makeImageSnapshot().get(), x, y);
   *             }
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
