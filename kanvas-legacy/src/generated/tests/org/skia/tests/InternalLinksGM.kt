package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class InternalLinksGM : public skiagm::GM {
 *     void onOnceBeforeDraw() override { this->setBGColor(0xFFDDDDDD); }
 *
 *     SkString getName() const override { return SkString("internal_links"); }
 *
 *     SkISize getISize() override { return {700, 500}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         sk_sp<SkData> name(SkData::MakeWithCString("target-a"));
 *
 *         canvas->save();
 *         canvas->translate(SkIntToScalar(100), SkIntToScalar(100));
 *         drawLabeledRect(canvas, "Link to A", 0, 0);
 *         SkRect rect = SkRect::MakeXYWH(0, 0, SkIntToScalar(50), SkIntToScalar(20));
 *         SkAnnotateLinkToDestination(canvas, rect, name.get());
 *         canvas->restore();
 *
 *         canvas->save();
 *         canvas->translate(SkIntToScalar(200), SkIntToScalar(200));
 *         SkPoint point = SkPoint::Make(SkIntToScalar(100), SkIntToScalar(50));
 *         drawLabeledRect(canvas, "Target A", point.x(), point.y());
 *         SkAnnotateNamedDestination(canvas, point, name.get());
 *         canvas->restore();
 *     }
 *
 *     /** Draw an arbitrary rectangle at a given location and label it with some
 *      *  text. */
 *     void drawLabeledRect(SkCanvas* canvas, const char* text, SkScalar x, SkScalar y) {
 *         SkPaint paint;
 *         paint.setColor(SK_ColorBLUE);
 *         SkRect rect = SkRect::MakeXYWH(x, y,
 *                                        SkIntToScalar(50), SkIntToScalar(20));
 *         canvas->drawRect(rect, paint);
 *
 *         SkFont font(ToolUtils::DefaultPortableTypeface(), 25);
 *         paint.setColor(SK_ColorBLACK);
 *         canvas->drawString(text, x, y, font, paint);
 *     }
 * }
 * ```
 */
public open class InternalLinksGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override { this->setBGColor(0xFFDDDDDD); }
   * ```
   */
  public override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("internal_links"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {700, 500}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         sk_sp<SkData> name(SkData::MakeWithCString("target-a"));
   *
   *         canvas->save();
   *         canvas->translate(SkIntToScalar(100), SkIntToScalar(100));
   *         drawLabeledRect(canvas, "Link to A", 0, 0);
   *         SkRect rect = SkRect::MakeXYWH(0, 0, SkIntToScalar(50), SkIntToScalar(20));
   *         SkAnnotateLinkToDestination(canvas, rect, name.get());
   *         canvas->restore();
   *
   *         canvas->save();
   *         canvas->translate(SkIntToScalar(200), SkIntToScalar(200));
   *         SkPoint point = SkPoint::Make(SkIntToScalar(100), SkIntToScalar(50));
   *         drawLabeledRect(canvas, "Target A", point.x(), point.y());
   *         SkAnnotateNamedDestination(canvas, point, name.get());
   *         canvas->restore();
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawLabeledRect(SkCanvas* canvas, const char* text, SkScalar x, SkScalar y) {
   *         SkPaint paint;
   *         paint.setColor(SK_ColorBLUE);
   *         SkRect rect = SkRect::MakeXYWH(x, y,
   *                                        SkIntToScalar(50), SkIntToScalar(20));
   *         canvas->drawRect(rect, paint);
   *
   *         SkFont font(ToolUtils::DefaultPortableTypeface(), 25);
   *         paint.setColor(SK_ColorBLACK);
   *         canvas->drawString(text, x, y, font, paint);
   *     }
   * ```
   */
  private fun drawLabeledRect(
    canvas: SkCanvas?,
    text: String?,
    x: SkScalar,
    y: SkScalar,
  ) {
    TODO("Implement drawLabeledRect")
  }
}
