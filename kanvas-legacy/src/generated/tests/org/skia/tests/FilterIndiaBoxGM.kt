package org.skia.tests

import kotlin.Array
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.math.SkISize
import org.skia.math.SkMatrix

/**
 * C++ original:
 * ```cpp
 * class FilterIndiaBoxGM : public skiagm::GM {
 *     SkBitmap    fBM;
 *     SkMatrix    fMatrix[2];
 *
 *     void onOnceBeforeDraw() override {
 *         constexpr char kResource[] = "images/box.gif";
 *         if (!ToolUtils::GetResourceAsBitmap(kResource, &fBM)) {
 *             fBM.allocN32Pixels(1, 1);
 *             fBM.eraseARGB(255, 255, 0 , 0); // red == bad
 *         }
 *         fBM.setImmutable();
 *
 *         SkScalar cx = SkScalarHalf(fBM.width());
 *         SkScalar cy = SkScalarHalf(fBM.height());
 *
 *         float vertScale = 30.0f/55.0f;
 *         float horizScale = 150.0f/200.0f;
 *
 *         fMatrix[0].setScale(horizScale, vertScale);
 *         fMatrix[1].setRotate(30, cx, cy); fMatrix[1].postScale(horizScale, vertScale);
 *     }
 *
 *     SkString getName() const override { return SkString("filterindiabox"); }
 *
 *     SkISize getISize() override { return {680, 130}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->translate(10, 10);
 *         for (size_t i = 0; i < std::size(fMatrix); ++i) {
 *             SkSize size = computeSize(fBM, fMatrix[i]);
 *             size.fWidth += 20;
 *             size.fHeight += 20;
 *
 *             draw_row(canvas, fBM, fMatrix[i], size.fWidth);
 *             canvas->translate(0, size.fHeight);
 *         }
 *     }
 * }
 * ```
 */
public open class FilterIndiaBoxGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkBitmap    fBM
   * ```
   */
  private var fBM: SkBitmap = TODO("Initialize fBM")

  /**
   * C++ original:
   * ```cpp
   * SkMatrix    fMatrix[2]
   * ```
   */
  private var fMatrix: Array<SkMatrix> = TODO("Initialize fMatrix")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         constexpr char kResource[] = "images/box.gif";
   *         if (!ToolUtils::GetResourceAsBitmap(kResource, &fBM)) {
   *             fBM.allocN32Pixels(1, 1);
   *             fBM.eraseARGB(255, 255, 0 , 0); // red == bad
   *         }
   *         fBM.setImmutable();
   *
   *         SkScalar cx = SkScalarHalf(fBM.width());
   *         SkScalar cy = SkScalarHalf(fBM.height());
   *
   *         float vertScale = 30.0f/55.0f;
   *         float horizScale = 150.0f/200.0f;
   *
   *         fMatrix[0].setScale(horizScale, vertScale);
   *         fMatrix[1].setRotate(30, cx, cy); fMatrix[1].postScale(horizScale, vertScale);
   *     }
   * ```
   */
  public override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("filterindiabox"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {680, 130}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         canvas->translate(10, 10);
   *         for (size_t i = 0; i < std::size(fMatrix); ++i) {
   *             SkSize size = computeSize(fBM, fMatrix[i]);
   *             size.fWidth += 20;
   *             size.fHeight += 20;
   *
   *             draw_row(canvas, fBM, fMatrix[i], size.fWidth);
   *             canvas->translate(0, size.fHeight);
   *         }
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
