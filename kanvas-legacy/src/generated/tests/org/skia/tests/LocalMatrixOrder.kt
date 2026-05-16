package org.skia.tests

import kotlin.Boolean
import kotlin.Double
import kotlin.Float
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class LocalMatrixOrder : public GM {
 * public:
 *     LocalMatrixOrder() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("localmatrix_order"); }
 *
 *     SkISize getISize() override { return SkISize::Make(500, 500); }
 *
 *     void onOnceBeforeDraw() override {
 *         auto mandrill = ToolUtils::GetResourceAsImage("images/mandrill_256.png");  // 256x256
 *         auto example5 = ToolUtils::GetResourceAsImage("images/example_5.png");     // 128x128
 *
 *         auto mshader = mandrill->makeShader(
 *                 SkTileMode::kRepeat,
 *                 SkTileMode::kRepeat,
 *                 SkFilterMode::kNearest,
 *                 SkMatrix::RotateDeg(45, {128, 128})); // rotate about center
 *         auto eshader = example5->makeShader(
 *                 SkTileMode::kRepeat,
 *                 SkTileMode::kRepeat,
 *                 SkFilterMode::kNearest,
 *                 SkMatrix::Scale(2, 2)); // make same size as mandrill and...
 *         // ... rotate about center
 *         eshader = eshader->makeWithLocalMatrix(SkMatrix::RotateDeg(45, {128, 128}));
 *
 *         // blend the two rotated and aligned images.
 *         fShader = SkShaders::Blend(SkBlendMode::kModulate, mshader, eshader);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         // Rotate fShader about the canvas center
 *         auto center = SkRect::Make(canvas->imageInfo().bounds()).center();
 *
 *         // viewer can insert a dpi scaling matrix. Make the animation always rotate about the device
 *         // center.
 *         if (auto ictm = canvas->getTotalMatrix(); ictm.invert(&ictm)) {
 *             center = ictm.mapPoint(center);
 *         }
 *
 *         auto shader = fShader->makeWithLocalMatrix(SkMatrix::RotateDeg(fAngle, center));
 *
 *         SkPaint paint;
 *         paint.setShader(shader);
 *         canvas->drawPaint(paint);
 *     }
 *
 *     bool onAnimate(double nanos) override {
 *         fAngle = TimeUtils::NanosToSeconds(nanos) * 5.f;
 *         return true;
 *     }
 *
 *     sk_sp<SkShader> fShader;
 *     float fAngle = 0.f;
 * }
 * ```
 */
public open class LocalMatrixOrder public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> fShader
   * ```
   */
  protected var fShader: SkSp<SkShader> = TODO("Initialize fShader")

  /**
   * C++ original:
   * ```cpp
   * float fAngle = 0.f
   * ```
   */
  protected var fAngle: Float = TODO("Initialize fAngle")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("localmatrix_order"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(500, 500); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         auto mandrill = ToolUtils::GetResourceAsImage("images/mandrill_256.png");  // 256x256
   *         auto example5 = ToolUtils::GetResourceAsImage("images/example_5.png");     // 128x128
   *
   *         auto mshader = mandrill->makeShader(
   *                 SkTileMode::kRepeat,
   *                 SkTileMode::kRepeat,
   *                 SkFilterMode::kNearest,
   *                 SkMatrix::RotateDeg(45, {128, 128})); // rotate about center
   *         auto eshader = example5->makeShader(
   *                 SkTileMode::kRepeat,
   *                 SkTileMode::kRepeat,
   *                 SkFilterMode::kNearest,
   *                 SkMatrix::Scale(2, 2)); // make same size as mandrill and...
   *         // ... rotate about center
   *         eshader = eshader->makeWithLocalMatrix(SkMatrix::RotateDeg(45, {128, 128}));
   *
   *         // blend the two rotated and aligned images.
   *         fShader = SkShaders::Blend(SkBlendMode::kModulate, mshader, eshader);
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         // Rotate fShader about the canvas center
   *         auto center = SkRect::Make(canvas->imageInfo().bounds()).center();
   *
   *         // viewer can insert a dpi scaling matrix. Make the animation always rotate about the device
   *         // center.
   *         if (auto ictm = canvas->getTotalMatrix(); ictm.invert(&ictm)) {
   *             center = ictm.mapPoint(center);
   *         }
   *
   *         auto shader = fShader->makeWithLocalMatrix(SkMatrix::RotateDeg(fAngle, center));
   *
   *         SkPaint paint;
   *         paint.setShader(shader);
   *         canvas->drawPaint(paint);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onAnimate(double nanos) override {
   *         fAngle = TimeUtils::NanosToSeconds(nanos) * 5.f;
   *         return true;
   *     }
   * ```
   */
  protected override fun onAnimate(nanos: Double): Boolean {
    TODO("Implement onAnimate")
  }
}
