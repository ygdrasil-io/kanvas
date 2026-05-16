package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class HighContrastFilterGM : public skiagm::GM {
 * protected:
 *     void onOnceBeforeDraw() override {
 *         SkColor4f g1Colors[] = { kColor1, kColor1.withAlphaByte(0x20) };
 *         SkColor4f g2Colors[] = { kColor2, kColor2.withAlphaByte(0x20) };
 *         SkPoint   g1Points[] = { { 0, 0 }, { 0,     100 } };
 *         SkPoint   g2Points[] = { { 0, 0 }, { kSize, 0   } };
 *         SkScalar pos[] = { 0.2f, 1.0f };
 *
 *         SkHighContrastConfig fConfig;
 *         fFilter = SkHighContrastFilter::Make(fConfig);
 *         fGr1 = SkShaders::LinearGradient(
 *             g1Points, {{g1Colors, pos, SkTileMode::kClamp}, {}});
 *         fGr2 = SkShaders::LinearGradient(
 *             g2Points, {{g2Colors, pos, SkTileMode::kClamp}, {}});
 *     }
 *
 *     SkString getName() const override { return SkString("highcontrastfilter"); }
 *
 *     SkISize getISize() override { return SkISize::Make(800, 420); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkHighContrastConfig configs[] = {
 *             { false, InvertStyle::kNoInvert, 0.0f },
 *             { false, InvertStyle::kInvertBrightness, 0.0f },
 *             { false, InvertStyle::kInvertLightness, 0.0f },
 *             { false, InvertStyle::kInvertLightness, 0.2f },
 *             { true, InvertStyle::kNoInvert, 0.0f },
 *             { true, InvertStyle::kInvertBrightness, 0.0f },
 *             { true, InvertStyle::kInvertLightness, 0.0f },
 *             { true, InvertStyle::kInvertLightness, 0.2f },
 *         };
 *
 *         for (size_t i = 0; i < std::size(configs); ++i) {
 *             SkScalar x = kSize * (i % 4);
 *             SkScalar y = kSize * (i / 4);
 *             canvas->save();
 *             canvas->translate(x, y);
 *             canvas->scale(kSize, kSize);
 *             draw_scene(canvas, configs[i]);
 *             draw_label(canvas, configs[i]);
 *             canvas->restore();
 *         }
 *     }
 *
 * private:
 *     sk_sp<SkColorFilter>    fFilter;
 *     sk_sp<SkShader>         fGr1, fGr2;
 *
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class HighContrastFilterGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorFilter>    fFilter
   * ```
   */
  private var fFilter: SkSp<SkColorFilter> = TODO("Initialize fFilter")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader>         fGr1
   * ```
   */
  private var fGr1: SkSp<SkShader> = TODO("Initialize fGr1")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader>         fGr1, fGr2
   * ```
   */
  private var fGr2: SkSp<SkShader> = TODO("Initialize fGr2")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         SkColor4f g1Colors[] = { kColor1, kColor1.withAlphaByte(0x20) };
   *         SkColor4f g2Colors[] = { kColor2, kColor2.withAlphaByte(0x20) };
   *         SkPoint   g1Points[] = { { 0, 0 }, { 0,     100 } };
   *         SkPoint   g2Points[] = { { 0, 0 }, { kSize, 0   } };
   *         SkScalar pos[] = { 0.2f, 1.0f };
   *
   *         SkHighContrastConfig fConfig;
   *         fFilter = SkHighContrastFilter::Make(fConfig);
   *         fGr1 = SkShaders::LinearGradient(
   *             g1Points, {{g1Colors, pos, SkTileMode::kClamp}, {}});
   *         fGr2 = SkShaders::LinearGradient(
   *             g2Points, {{g2Colors, pos, SkTileMode::kClamp}, {}});
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("highcontrastfilter"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(800, 420); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkHighContrastConfig configs[] = {
   *             { false, InvertStyle::kNoInvert, 0.0f },
   *             { false, InvertStyle::kInvertBrightness, 0.0f },
   *             { false, InvertStyle::kInvertLightness, 0.0f },
   *             { false, InvertStyle::kInvertLightness, 0.2f },
   *             { true, InvertStyle::kNoInvert, 0.0f },
   *             { true, InvertStyle::kInvertBrightness, 0.0f },
   *             { true, InvertStyle::kInvertLightness, 0.0f },
   *             { true, InvertStyle::kInvertLightness, 0.2f },
   *         };
   *
   *         for (size_t i = 0; i < std::size(configs); ++i) {
   *             SkScalar x = kSize * (i % 4);
   *             SkScalar y = kSize * (i / 4);
   *             canvas->save();
   *             canvas->translate(x, y);
   *             canvas->scale(kSize, kSize);
   *             draw_scene(canvas, configs[i]);
   *             draw_label(canvas, configs[i]);
   *             canvas->restore();
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
