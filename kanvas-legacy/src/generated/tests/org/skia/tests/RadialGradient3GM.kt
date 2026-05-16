package org.skia.tests

import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class RadialGradient3GM : public skiagm::GM {
 * public:
 *     RadialGradient3GM(bool dither) : fDither(dither) { }
 *
 * private:
 *     SkString getName() const override {
 *         return SkString(fDither ? "radial_gradient3" : "radial_gradient3_nodither");
 *     }
 *
 *     SkISize getISize() override { return {500, 500}; }
 *
 *     bool runAsBench() const override { return true; }
 *
 *     void onOnceBeforeDraw() override {
 *         const SkPoint center = { 0, 0 };
 *         const SkScalar kRadius = 3000;
 *         const SkColor4f kColors[] = { {1,1,1,1}, {0,0,0,1} };
 *         fShader = SkShaders::RadialGradient(center, kRadius,
 *                                             {{kColors, {}, SkTileMode::kClamp}, {}});
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint;
 *         paint.setShader(fShader);
 *         paint.setDither(fDither);
 *         canvas->drawRect(SkRect::MakeWH(500, 500), paint);
 *     }
 *
 * private:
 *     sk_sp<SkShader> fShader;
 *     bool fDither;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class RadialGradient3GM public constructor(
  dither: Boolean,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> fShader
   * ```
   */
  private var fShader: SkSp<SkShader> = TODO("Initialize fShader")

  /**
   * C++ original:
   * ```cpp
   * bool fDither
   * ```
   */
  private var fDither: Boolean = TODO("Initialize fDither")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         return SkString(fDither ? "radial_gradient3" : "radial_gradient3_nodither");
   *     }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {500, 500}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

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
   * void onOnceBeforeDraw() override {
   *         const SkPoint center = { 0, 0 };
   *         const SkScalar kRadius = 3000;
   *         const SkColor4f kColors[] = { {1,1,1,1}, {0,0,0,1} };
   *         fShader = SkShaders::RadialGradient(center, kRadius,
   *                                             {{kColors, {}, SkTileMode::kClamp}, {}});
   *     }
   * ```
   */
  public override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPaint paint;
   *         paint.setShader(fShader);
   *         paint.setDither(fDither);
   *         canvas->drawRect(SkRect::MakeWH(500, 500), paint);
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
