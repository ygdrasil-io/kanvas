package org.skia.tests

import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class RuntimeFunctions : public skiagm::GM {
 *     bool runAsBench() const override { return true; }
 *
 *     SkString getName() const override { return SkString("runtimefunctions"); }
 *
 *     SkISize getISize() override { return {256, 256}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkRuntimeEffect::Result result =
 *                 SkRuntimeEffect::MakeForShader(SkString(RUNTIME_FUNCTIONS_SRC));
 *         SkASSERTF(result.effect, "%s", result.errorText.c_str());
 *
 *         SkMatrix localM;
 *         localM.setRotate(90, 128, 128);
 *
 *         SkV4 iResolution = { 255, 255, 0, 0 };
 *         auto shader = result.effect->makeShader(
 *                 SkData::MakeWithCopy(&iResolution, sizeof(iResolution)), nullptr, 0, &localM);
 *         SkPaint p;
 *         p.setShader(std::move(shader));
 *         canvas->drawRect({0, 0, 256, 256}, p);
 *     }
 * }
 * ```
 */
public open class RuntimeFunctions : GM() {
  /**
   * C++ original:
   * ```cpp
   * bool runAsBench() const override { return true; }
   * ```
   */
  public override fun runAsBench(): Boolean {
    TODO("Implement runAsBench")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("runtimefunctions"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {256, 256}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkRuntimeEffect::Result result =
   *                 SkRuntimeEffect::MakeForShader(SkString(RUNTIME_FUNCTIONS_SRC));
   *         SkASSERTF(result.effect, "%s", result.errorText.c_str());
   *
   *         SkMatrix localM;
   *         localM.setRotate(90, 128, 128);
   *
   *         SkV4 iResolution = { 255, 255, 0, 0 };
   *         auto shader = result.effect->makeShader(
   *                 SkData::MakeWithCopy(&iResolution, sizeof(iResolution)), nullptr, 0, &localM);
   *         SkPaint p;
   *         p.setShader(std::move(shader));
   *         canvas->drawRect({0, 0, 256, 256}, p);
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
