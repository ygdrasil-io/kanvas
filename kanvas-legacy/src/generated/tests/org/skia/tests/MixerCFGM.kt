package org.skia.tests

import kotlin.String
import kotlin.ULong
import org.skia.core.SkCanvas
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSp
import org.skia.math.SkISize
import org.skia.math.SkSize

/**
 * C++ original:
 * ```cpp
 * class MixerCFGM final : public skiagm::GM {
 * public:
 *     MixerCFGM(const SkSize& tileSize, size_t tileCount)
 *         : fTileSize(tileSize)
 *         , fTileCount(tileCount) {}
 *
 * protected:
 *     SkString getName() const override { return SkString("mixerCF"); }
 *
 *     SkISize getISize() override {
 *         return SkISize::Make(fTileSize.width()  * 1.2f * fTileCount,
 *                              fTileSize.height() * 1.2f * 3);         // 3 rows
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint;
 *
 *         const SkColor4f gradient_colors[] = {
 *             SkColors::kRed, SkColors::kGreen, SkColors::kBlue, SkColors::kRed };
 *         paint.setShader(SkShaders::SweepGradient({fTileSize.width() / 2, fTileSize.height() / 2},
 *                                                  {{gradient_colors, {}, SkTileMode::kClamp}, {}}));
 *
 *         auto cf0 = MakeTintColorFilter(0xff300000, 0xffa00000);  // red tint
 *         auto cf1 = MakeTintColorFilter(0xff003000, 0xff00a000);  // green tint
 *
 *         this->mixRow(canvas, paint, nullptr,     cf1);
 *         this->mixRow(canvas, paint,     cf0, nullptr);
 *         this->mixRow(canvas, paint,     cf0,     cf1);
 *     }
 *
 * private:
 *     const SkSize fTileSize;
 *     const size_t fTileCount;
 *
 *     void mixRow(SkCanvas* canvas, SkPaint& paint,
 *                 sk_sp<SkColorFilter> cf0, sk_sp<SkColorFilter> cf1) {
 *         // We cycle through paint colors on each row, to test how the paint color flows through
 *         // the color-filter network
 *         const SkColor4f paintColors[] = {
 *             { 1.0f, 1.0f, 1.0f, 1.0f },  // Opaque white
 *             { 1.0f, 1.0f, 1.0f, 0.5f },  // Translucent white
 *             { 0.5f, 0.5f, 1.0f, 1.0f },  // Opaque pale blue
 *             { 0.5f, 0.5f, 1.0f, 0.5f },  // Translucent pale blue
 *         };
 *
 *         canvas->translate(0, fTileSize.height() * 0.1f);
 *         {
 *             SkAutoCanvasRestore arc(canvas, true);
 *             for (size_t i = 0; i < fTileCount; ++i) {
 *                 paint.setColor4f(paintColors[i % std::size(paintColors)]);
 *                 float t = static_cast<float>(i) / (fTileCount - 1);
 *                 paint.setColorFilter(SkColorFilters::Lerp(t, cf0, cf1));
 *                 canvas->translate(fTileSize.width() * 0.1f, 0);
 *                 canvas->drawRect(SkRect::MakeWH(fTileSize.width(), fTileSize.height()), paint);
 *                 canvas->translate(fTileSize.width() * 1.1f, 0);
 *             }
 *         }
 *         canvas->translate(0, fTileSize.height() * 1.1f);
 *     }
 *
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public class MixerCFGM public constructor(
  tileSize: SkSize,
  tileCount: ULong,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * const SkSize fTileSize
   * ```
   */
  private val fTileSize: SkSize = TODO("Initialize fTileSize")

  /**
   * C++ original:
   * ```cpp
   * const size_t fTileCount
   * ```
   */
  private val fTileCount: ULong = TODO("Initialize fTileCount")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("mixerCF"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override {
   *         return SkISize::Make(fTileSize.width()  * 1.2f * fTileCount,
   *                              fTileSize.height() * 1.2f * 3);         // 3 rows
   *     }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPaint paint;
   *
   *         const SkColor4f gradient_colors[] = {
   *             SkColors::kRed, SkColors::kGreen, SkColors::kBlue, SkColors::kRed };
   *         paint.setShader(SkShaders::SweepGradient({fTileSize.width() / 2, fTileSize.height() / 2},
   *                                                  {{gradient_colors, {}, SkTileMode::kClamp}, {}}));
   *
   *         auto cf0 = MakeTintColorFilter(0xff300000, 0xffa00000);  // red tint
   *         auto cf1 = MakeTintColorFilter(0xff003000, 0xff00a000);  // green tint
   *
   *         this->mixRow(canvas, paint, nullptr,     cf1);
   *         this->mixRow(canvas, paint,     cf0, nullptr);
   *         this->mixRow(canvas, paint,     cf0,     cf1);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void mixRow(SkCanvas* canvas, SkPaint& paint,
   *                 sk_sp<SkColorFilter> cf0, sk_sp<SkColorFilter> cf1) {
   *         // We cycle through paint colors on each row, to test how the paint color flows through
   *         // the color-filter network
   *         const SkColor4f paintColors[] = {
   *             { 1.0f, 1.0f, 1.0f, 1.0f },  // Opaque white
   *             { 1.0f, 1.0f, 1.0f, 0.5f },  // Translucent white
   *             { 0.5f, 0.5f, 1.0f, 1.0f },  // Opaque pale blue
   *             { 0.5f, 0.5f, 1.0f, 0.5f },  // Translucent pale blue
   *         };
   *
   *         canvas->translate(0, fTileSize.height() * 0.1f);
   *         {
   *             SkAutoCanvasRestore arc(canvas, true);
   *             for (size_t i = 0; i < fTileCount; ++i) {
   *                 paint.setColor4f(paintColors[i % std::size(paintColors)]);
   *                 float t = static_cast<float>(i) / (fTileCount - 1);
   *                 paint.setColorFilter(SkColorFilters::Lerp(t, cf0, cf1));
   *                 canvas->translate(fTileSize.width() * 0.1f, 0);
   *                 canvas->drawRect(SkRect::MakeWH(fTileSize.width(), fTileSize.height()), paint);
   *                 canvas->translate(fTileSize.width() * 1.1f, 0);
   *             }
   *         }
   *         canvas->translate(0, fTileSize.height() * 1.1f);
   *     }
   * ```
   */
  private fun mixRow(
    canvas: SkCanvas?,
    paint: SkPaint,
    cf0: SkSp<SkColorFilter>,
    cf1: SkSp<SkColorFilter>,
  ) {
    TODO("Implement mixRow")
  }
}
