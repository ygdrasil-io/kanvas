package org.skia.tests

import kotlin.Array
import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class LinearGradientGM : public skiagm::GM {
 * public:
 *     LinearGradientGM(bool dither) : fDither(dither) { }
 *
 * private:
 *     SkString getName() const override {
 *         return SkString(fDither ? "linear_gradient" : "linear_gradient_nodither");
 *     }
 *
 *     const SkScalar kWidthBump = 30.f;
 *     const SkScalar kHeight = 5.f;
 *     const SkScalar kMinWidth = 540.f;
 *
 *     SkISize getISize() override { return {500, 500}; }
 *
 *     void onOnceBeforeDraw() override {
 *         SkPoint pts[2] = { {0, 0}, {0, 0} };
 *         const SkColor4f colors[] = {
 *             SkColors::kWhite, SkColors::kWhite,
 *             SkColor4f::FromColor(0xFF008200), SkColor4f::FromColor(0xFF008200),
 *             SkColors::kWhite, SkColors::kWhite,
 *         };
 *         const SkScalar unitPos[] = { 0, 50, 70, 500, 540 };
 *         SkScalar pos[6];
 *         pos[5] = 1;
 *         for (int index = 0; index < (int) std::size(fShader); ++index) {
 *             pts[1].fX = 500.f + index * kWidthBump;
 *             for (int inner = 0; inner < (int) std::size(unitPos); ++inner) {
 *                 pos[inner] = unitPos[inner] / (kMinWidth + index * kWidthBump);
 *             }
 *             fShader[index] = SkShaders::LinearGradient(pts,
 *                                                        {{colors, pos, SkTileMode::kClamp}, {}});
 *         }
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *         paint.setDither(fDither);
 *         for (int index = 0; index < (int) std::size(fShader); ++index) {
 *             paint.setShader(fShader[index]);
 *             canvas->drawRect(SkRect::MakeLTRB(0, index * kHeight, kMinWidth + index * kWidthBump,
 *                     (index + 1) * kHeight), paint);
 *         }
 *     }
 *
 * private:
 *     sk_sp<SkShader> fShader[100];
 *     bool fDither;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class LinearGradientGM public constructor(
  dither: Boolean,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * const SkScalar kWidthBump = 30.f
   * ```
   */
  private val kWidthBump: SkScalar = TODO("Initialize kWidthBump")

  /**
   * C++ original:
   * ```cpp
   * const SkScalar kHeight = 5.f
   * ```
   */
  private val kHeight: SkScalar = TODO("Initialize kHeight")

  /**
   * C++ original:
   * ```cpp
   * const SkScalar kMinWidth = 540.f
   * ```
   */
  private val kMinWidth: SkScalar = TODO("Initialize kMinWidth")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> fShader[100]
   * ```
   */
  private var fShader: Array<SkSp<SkShader>> = TODO("Initialize fShader")

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
   *         return SkString(fDither ? "linear_gradient" : "linear_gradient_nodither");
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
   * void onOnceBeforeDraw() override {
   *         SkPoint pts[2] = { {0, 0}, {0, 0} };
   *         const SkColor4f colors[] = {
   *             SkColors::kWhite, SkColors::kWhite,
   *             SkColor4f::FromColor(0xFF008200), SkColor4f::FromColor(0xFF008200),
   *             SkColors::kWhite, SkColors::kWhite,
   *         };
   *         const SkScalar unitPos[] = { 0, 50, 70, 500, 540 };
   *         SkScalar pos[6];
   *         pos[5] = 1;
   *         for (int index = 0; index < (int) std::size(fShader); ++index) {
   *             pts[1].fX = 500.f + index * kWidthBump;
   *             for (int inner = 0; inner < (int) std::size(unitPos); ++inner) {
   *                 pos[inner] = unitPos[inner] / (kMinWidth + index * kWidthBump);
   *             }
   *             fShader[index] = SkShaders::LinearGradient(pts,
   *                                                        {{colors, pos, SkTileMode::kClamp}, {}});
   *         }
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
   *         paint.setAntiAlias(true);
   *         paint.setDither(fDither);
   *         for (int index = 0; index < (int) std::size(fShader); ++index) {
   *             paint.setShader(fShader[index]);
   *             canvas->drawRect(SkRect::MakeLTRB(0, index * kHeight, kMinWidth + index * kWidthBump,
   *                     (index + 1) * kHeight), paint);
   *         }
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
