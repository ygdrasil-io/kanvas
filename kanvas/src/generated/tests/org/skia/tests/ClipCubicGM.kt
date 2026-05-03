package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkPath
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class ClipCubicGM : public skiagm::GM {
 *     const SkScalar W = 100;
 *     const SkScalar H = 240;
 *
 *     SkPath fVPath, fHPath;
 * public:
 *     ClipCubicGM() {
 *         fVPath = SkPathBuilder().moveTo(W, 0)
 *                                 .cubicTo(W, H-10, 0, 10, 0, H)
 *                                 .detach();
 *
 *         SkMatrix pivot;
 *         pivot.setRotate(90, W/2, H/2);
 *         fHPath = fVPath.makeTransform(pivot);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("clipcubic"); }
 *
 *     SkISize getISize() override { return SkISize::Make(400, 410); }
 *
 *     void doDraw(SkCanvas* canvas, const SkPath& path) {
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *
 *         paint.setColor(0xFFCCCCCC);
 *         canvas->drawPath(path, paint);
 *
 *         paint.setColor(SK_ColorRED);
 *         paint.setStyle(SkPaint::kStroke_Style);
 *         canvas->drawPath(path, paint);
 *     }
 *
 *     void drawAndClip(SkCanvas* canvas, const SkPath& path, SkScalar dx, SkScalar dy) {
 *         SkAutoCanvasRestore acr(canvas, true);
 *
 *         SkRect r = SkRect::MakeXYWH(0, H/4, W, H/2);
 *         SkPaint paint;
 *         paint.setColor(ToolUtils::color_to_565(0xFF8888FF));
 *
 *         canvas->drawRect(r, paint);
 *         this->doDraw(canvas, path);
 *
 *         canvas->translate(dx, dy);
 *
 *         canvas->drawRect(r, paint);
 *         canvas->clipRect(r);
 *         this->doDraw(canvas, path);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->translate(80, 10);
 *         this->drawAndClip(canvas, fVPath, 200, 0);
 *         canvas->translate(0, 200);
 *         this->drawAndClip(canvas, fHPath, 200, 0);
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class ClipCubicGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * const SkScalar W = 100
   * ```
   */
  private val w: SkScalar = TODO("Initialize w")

  /**
   * C++ original:
   * ```cpp
   * const SkScalar H = 240
   * ```
   */
  private val h: SkScalar = TODO("Initialize h")

  /**
   * C++ original:
   * ```cpp
   * SkPath fVPath
   * ```
   */
  private var fVPath: SkPath = TODO("Initialize fVPath")

  /**
   * C++ original:
   * ```cpp
   * SkPath fVPath, fHPath
   * ```
   */
  private var fHPath: SkPath = TODO("Initialize fHPath")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("clipcubic"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(400, 410); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void doDraw(SkCanvas* canvas, const SkPath& path) {
   *         SkPaint paint;
   *         paint.setAntiAlias(true);
   *
   *         paint.setColor(0xFFCCCCCC);
   *         canvas->drawPath(path, paint);
   *
   *         paint.setColor(SK_ColorRED);
   *         paint.setStyle(SkPaint::kStroke_Style);
   *         canvas->drawPath(path, paint);
   *     }
   * ```
   */
  protected fun doDraw(canvas: SkCanvas?, path: SkPath) {
    TODO("Implement doDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawAndClip(SkCanvas* canvas, const SkPath& path, SkScalar dx, SkScalar dy) {
   *         SkAutoCanvasRestore acr(canvas, true);
   *
   *         SkRect r = SkRect::MakeXYWH(0, H/4, W, H/2);
   *         SkPaint paint;
   *         paint.setColor(ToolUtils::color_to_565(0xFF8888FF));
   *
   *         canvas->drawRect(r, paint);
   *         this->doDraw(canvas, path);
   *
   *         canvas->translate(dx, dy);
   *
   *         canvas->drawRect(r, paint);
   *         canvas->clipRect(r);
   *         this->doDraw(canvas, path);
   *     }
   * ```
   */
  protected fun drawAndClip(
    canvas: SkCanvas?,
    path: SkPath,
    dx: SkScalar,
    dy: SkScalar,
  ) {
    TODO("Implement drawAndClip")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         canvas->translate(80, 10);
   *         this->drawAndClip(canvas, fVPath, 200, 0);
   *         canvas->translate(0, 200);
   *         this->drawAndClip(canvas, fHPath, 200, 0);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
