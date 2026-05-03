package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ModeColorFilterGM : public GM {
 * public:
 *     ModeColorFilterGM() {
 *         this->setBGColor(0xFF303030);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("modecolorfilters"); }
 *
 *     SkISize getISize() override { return SkISize::Make(WIDTH, HEIGHT); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         // size of rect for each test case
 *         constexpr int kRectWidth  = 20;
 *         constexpr int kRectHeight = 20;
 *
 *         constexpr int kCheckSize  = 10;
 *
 *         if (!fBmpShader) {
 *             fBmpShader = make_bg_shader(kCheckSize);
 *         }
 *         SkPaint bgPaint;
 *         bgPaint.setShader(fBmpShader);
 *         bgPaint.setBlendMode(SkBlendMode::kSrc);
 *
 *         sk_sp<SkShader> shaders[] = {
 *             nullptr,                                   // use a paint color instead of a shader
 *             make_solid_shader(),
 *             make_transparent_shader(),
 *             make_trans_black_shader(),
 *         };
 *
 *         // used without shader
 *         SkColor colors[] = {
 *             SkColorSetARGB(0xFF, 0xFF, 0xFF, 0xFF),
 *             SkColorSetARGB(0xFF, 0x00, 0x00, 0x00),
 *             SkColorSetARGB(0x00, 0x00, 0x00, 0x00),
 *             SkColorSetARGB(0xFF, 0x10, 0x20, 0x42),
 *             SkColorSetARGB(0xA0, 0x20, 0x30, 0x90),
 *         };
 *
 *         // used with shaders
 *         SkColor alphas[] = {0xFFFFFFFF, 0x80808080};
 *
 *         const SkBlendMode modes[]  = { // currently just doing the Modes expressible as Coeffs
 *             SkBlendMode::kClear,
 *             SkBlendMode::kSrc,
 *             SkBlendMode::kDst,
 *             SkBlendMode::kSrcOver,
 *             SkBlendMode::kDstOver,
 *             SkBlendMode::kSrcIn,
 *             SkBlendMode::kDstIn,
 *             SkBlendMode::kSrcOut,
 *             SkBlendMode::kDstOut,
 *             SkBlendMode::kSrcATop,
 *             SkBlendMode::kDstATop,
 *             SkBlendMode::kXor,
 *             SkBlendMode::kPlus,
 *             SkBlendMode::kModulate,
 *         };
 *
 *         SkPaint paint;
 *         int idx = 0;
 *         const int kRectsPerRow = std::max(this->getISize().fWidth / kRectWidth, 1);
 *         for (size_t cfm = 0; cfm < std::size(modes); ++cfm) {
 *             for (size_t cfc = 0; cfc < std::size(colors); ++cfc) {
 *                 paint.setColorFilter(SkColorFilters::Blend(colors[cfc], modes[cfm]));
 *                 for (size_t s = 0; s < std::size(shaders); ++s) {
 *                     paint.setShader(shaders[s]);
 *                     bool hasShader = nullptr == paint.getShader();
 *                     int paintColorCnt = hasShader ? std::size(alphas) : std::size(colors);
 *                     SkColor* paintColors = hasShader ? alphas : colors;
 *                     for (int pc = 0; pc < paintColorCnt; ++pc) {
 *                         paint.setColor(paintColors[pc]);
 *                         SkScalar x = SkIntToScalar(idx % kRectsPerRow);
 *                         SkScalar y = SkIntToScalar(idx / kRectsPerRow);
 *                         SkRect rect = SkRect::MakeXYWH(x * kRectWidth, y * kRectHeight,
 *                                                        SkIntToScalar(kRectWidth),
 *                                                        SkIntToScalar(kRectHeight));
 *                         canvas->saveLayer(&rect, nullptr);
 *                         canvas->drawRect(rect, bgPaint);
 *                         canvas->drawRect(rect, paint);
 *                         canvas->restore();
 *                         ++idx;
 *                     }
 *                 }
 *             }
 *         }
 *     }
 *
 * private:
 *     sk_sp<SkShader> fBmpShader;
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class ModeColorFilterGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> fBmpShader
   * ```
   */
  private var fBmpShader: SkSp<SkShader> = TODO("Initialize fBmpShader")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("modecolorfilters"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(WIDTH, HEIGHT); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         // size of rect for each test case
   *         constexpr int kRectWidth  = 20;
   *         constexpr int kRectHeight = 20;
   *
   *         constexpr int kCheckSize  = 10;
   *
   *         if (!fBmpShader) {
   *             fBmpShader = make_bg_shader(kCheckSize);
   *         }
   *         SkPaint bgPaint;
   *         bgPaint.setShader(fBmpShader);
   *         bgPaint.setBlendMode(SkBlendMode::kSrc);
   *
   *         sk_sp<SkShader> shaders[] = {
   *             nullptr,                                   // use a paint color instead of a shader
   *             make_solid_shader(),
   *             make_transparent_shader(),
   *             make_trans_black_shader(),
   *         };
   *
   *         // used without shader
   *         SkColor colors[] = {
   *             SkColorSetARGB(0xFF, 0xFF, 0xFF, 0xFF),
   *             SkColorSetARGB(0xFF, 0x00, 0x00, 0x00),
   *             SkColorSetARGB(0x00, 0x00, 0x00, 0x00),
   *             SkColorSetARGB(0xFF, 0x10, 0x20, 0x42),
   *             SkColorSetARGB(0xA0, 0x20, 0x30, 0x90),
   *         };
   *
   *         // used with shaders
   *         SkColor alphas[] = {0xFFFFFFFF, 0x80808080};
   *
   *         const SkBlendMode modes[]  = { // currently just doing the Modes expressible as Coeffs
   *             SkBlendMode::kClear,
   *             SkBlendMode::kSrc,
   *             SkBlendMode::kDst,
   *             SkBlendMode::kSrcOver,
   *             SkBlendMode::kDstOver,
   *             SkBlendMode::kSrcIn,
   *             SkBlendMode::kDstIn,
   *             SkBlendMode::kSrcOut,
   *             SkBlendMode::kDstOut,
   *             SkBlendMode::kSrcATop,
   *             SkBlendMode::kDstATop,
   *             SkBlendMode::kXor,
   *             SkBlendMode::kPlus,
   *             SkBlendMode::kModulate,
   *         };
   *
   *         SkPaint paint;
   *         int idx = 0;
   *         const int kRectsPerRow = std::max(this->getISize().fWidth / kRectWidth, 1);
   *         for (size_t cfm = 0; cfm < std::size(modes); ++cfm) {
   *             for (size_t cfc = 0; cfc < std::size(colors); ++cfc) {
   *                 paint.setColorFilter(SkColorFilters::Blend(colors[cfc], modes[cfm]));
   *                 for (size_t s = 0; s < std::size(shaders); ++s) {
   *                     paint.setShader(shaders[s]);
   *                     bool hasShader = nullptr == paint.getShader();
   *                     int paintColorCnt = hasShader ? std::size(alphas) : std::size(colors);
   *                     SkColor* paintColors = hasShader ? alphas : colors;
   *                     for (int pc = 0; pc < paintColorCnt; ++pc) {
   *                         paint.setColor(paintColors[pc]);
   *                         SkScalar x = SkIntToScalar(idx % kRectsPerRow);
   *                         SkScalar y = SkIntToScalar(idx / kRectsPerRow);
   *                         SkRect rect = SkRect::MakeXYWH(x * kRectWidth, y * kRectHeight,
   *                                                        SkIntToScalar(kRectWidth),
   *                                                        SkIntToScalar(kRectHeight));
   *                         canvas->saveLayer(&rect, nullptr);
   *                         canvas->drawRect(rect, bgPaint);
   *                         canvas->drawRect(rect, paint);
   *                         canvas->restore();
   *                         ++idx;
   *                     }
   *                 }
   *             }
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
