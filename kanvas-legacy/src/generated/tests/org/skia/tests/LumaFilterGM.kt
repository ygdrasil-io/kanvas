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
 * class LumaFilterGM : public skiagm::GM {
 * protected:
 *     void onOnceBeforeDraw() override {
 *         SkColor4f g1Colors[] = { kColor1, kColor1.withAlphaByte(0x20) };
 *         SkColor4f g2Colors[] = { kColor2, kColor2.withAlphaByte(0x20) };
 *         SkPoint  g1Points[] = { { 0, 0 }, { 0,     100 } };
 *         SkPoint  g2Points[] = { { 0, 0 }, { kSize, 0   } };
 *         SkScalar pos[] = { 0.2f, 1.0f };
 *
 *         fFilter = SkLumaColorFilter::Make();
 *         fGr1 = SkShaders::LinearGradient(g1Points, {{g1Colors, pos, SkTileMode::kClamp}, {}});
 *         fGr2 = SkShaders::LinearGradient(g2Points, {{g2Colors, pos, SkTileMode::kClamp}, {}});
 *     }
 *
 *     SkString getName() const override { return SkString("lumafilter"); }
 *
 *     SkISize getISize() override { return SkISize::Make(600, 420); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkBlendMode modes[] = {
 *             SkBlendMode::kSrcOver,
 *             SkBlendMode::kDstOver,
 *             SkBlendMode::kSrcATop,
 *             SkBlendMode::kDstATop,
 *             SkBlendMode::kSrcIn,
 *             SkBlendMode::kDstIn,
 *         };
 *         struct {
 *             const sk_sp<SkShader>& fShader1;
 *             const sk_sp<SkShader>& fShader2;
 *         } shaders[] = {
 *             { nullptr, nullptr },
 *             { nullptr, fGr2 },
 *             { fGr1, nullptr },
 *             { fGr1, fGr2 },
 *         };
 *
 *         SkScalar gridStep = kSize + 2 * kInset;
 *         for (size_t i = 0; i < std::size(modes); ++i) {
 *             draw_label(canvas, SkBlendMode_Name(modes[i]),
 *                        SkPoint::Make(gridStep * (0.5f + i), 20));
 *         }
 *
 *         for (size_t i = 0; i < std::size(shaders); ++i) {
 *             canvas->save();
 *             canvas->translate(kInset, gridStep * i + 30);
 *             for (size_t m = 0; m < std::size(modes); ++m) {
 *                 draw_scene(canvas, fFilter, modes[m], shaders[i].fShader1, shaders[i].fShader2);
 *                 canvas->translate(gridStep, 0);
 *             }
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
public open class LumaFilterGM : GM() {
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
   *         SkPoint  g1Points[] = { { 0, 0 }, { 0,     100 } };
   *         SkPoint  g2Points[] = { { 0, 0 }, { kSize, 0   } };
   *         SkScalar pos[] = { 0.2f, 1.0f };
   *
   *         fFilter = SkLumaColorFilter::Make();
   *         fGr1 = SkShaders::LinearGradient(g1Points, {{g1Colors, pos, SkTileMode::kClamp}, {}});
   *         fGr2 = SkShaders::LinearGradient(g2Points, {{g2Colors, pos, SkTileMode::kClamp}, {}});
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("lumafilter"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(600, 420); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkBlendMode modes[] = {
   *             SkBlendMode::kSrcOver,
   *             SkBlendMode::kDstOver,
   *             SkBlendMode::kSrcATop,
   *             SkBlendMode::kDstATop,
   *             SkBlendMode::kSrcIn,
   *             SkBlendMode::kDstIn,
   *         };
   *         struct {
   *             const sk_sp<SkShader>& fShader1;
   *             const sk_sp<SkShader>& fShader2;
   *         } shaders[] = {
   *             { nullptr, nullptr },
   *             { nullptr, fGr2 },
   *             { fGr1, nullptr },
   *             { fGr1, fGr2 },
   *         };
   *
   *         SkScalar gridStep = kSize + 2 * kInset;
   *         for (size_t i = 0; i < std::size(modes); ++i) {
   *             draw_label(canvas, SkBlendMode_Name(modes[i]),
   *                        SkPoint::Make(gridStep * (0.5f + i), 20));
   *         }
   *
   *         for (size_t i = 0; i < std::size(shaders); ++i) {
   *             canvas->save();
   *             canvas->translate(kInset, gridStep * i + 30);
   *             for (size_t m = 0; m < std::size(modes); ++m) {
   *                 draw_scene(canvas, fFilter, modes[m], shaders[i].fShader1, shaders[i].fShader2);
   *                 canvas->translate(gridStep, 0);
   *             }
   *             canvas->restore();
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
