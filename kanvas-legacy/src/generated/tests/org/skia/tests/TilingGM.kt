package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class TilingGM : public skiagm::GM {
 * public:
 *     TilingGM(bool powerOfTwoSize)
 *             : fPowerOfTwoSize(powerOfTwoSize) {
 *     }
 *
 *     SkBitmap    fTexture[std::size(gColorTypes)];
 *
 * protected:
 *
 *     enum {
 *         kPOTSize = 32,
 *         kNPOTSize = 21,
 *     };
 *
 *     SkString getName() const override {
 *         SkString name("tilemodes");
 *         if (!fPowerOfTwoSize) {
 *             name.append("_npot");
 *         }
 *         return name;
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(880, 560); }
 *
 *     void onOnceBeforeDraw() override {
 *         int size = fPowerOfTwoSize ? kPOTSize : kNPOTSize;
 *         for (size_t i = 0; i < std::size(gColorTypes); i++) {
 *             makebm(&fTexture[i], gColorTypes[i], size, size);
 *         }
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint textPaint;
 *         SkFont  font = ToolUtils::DefaultPortableFont();
 *
 *         int size = fPowerOfTwoSize ? kPOTSize : kNPOTSize;
 *
 *         SkRect r = { 0, 0, SkIntToScalar(size*2), SkIntToScalar(size*2) };
 *
 *         const char* gConfigNames[] = { "8888", "565" };
 *
 *         constexpr SkFilterMode gFilters[] = { SkFilterMode::kNearest, SkFilterMode::kLinear };
 *         static const char* gFilterNames[] = { "point", "bilinear" };
 *
 *         constexpr SkTileMode gModes[] = {
 *             SkTileMode::kClamp, SkTileMode::kRepeat, SkTileMode::kMirror };
 *         static const char* gModeNames[] = { "C", "R", "M" };
 *
 *         SkScalar y = SkIntToScalar(24);
 *         SkScalar x = SkIntToScalar(10);
 *
 *         for (size_t kx = 0; kx < std::size(gModes); kx++) {
 *             for (size_t ky = 0; ky < std::size(gModes); ky++) {
 *                 SkPaint p;
 *                 p.setDither(true);
 *                 SkString str;
 *                 str.printf("[%s,%s]", gModeNames[kx], gModeNames[ky]);
 *
 *                 SkTextUtils::DrawString(canvas, str.c_str(), x + r.width()/2, y, font, p,
 *                                         SkTextUtils::kCenter_Align);
 *
 *                 x += r.width() * 4 / 3;
 *             }
 *         }
 *
 *         y += SkIntToScalar(16);
 *
 *         for (size_t i = 0; i < std::size(gColorTypes); i++) {
 *             for (size_t j = 0; j < std::size(gFilters); j++) {
 *                 x = SkIntToScalar(10);
 *                 for (size_t kx = 0; kx < std::size(gModes); kx++) {
 *                     for (size_t ky = 0; ky < std::size(gModes); ky++) {
 *                         SkPaint paint;
 * #if 1 // Temporary change to regen bitmap before each draw. This may help tracking down an issue
 *       // on SGX where resizing NPOT textures to POT textures exhibits a driver bug.
 *                         if (!fPowerOfTwoSize) {
 *                             makebm(&fTexture[i], gColorTypes[i], size, size);
 *                         }
 * #endif
 *                         setup(canvas, &paint, fTexture[i], gFilters[j], gModes[kx], gModes[ky]);
 *                         paint.setDither(true);
 *
 *                         canvas->save();
 *                         canvas->translate(x, y);
 *                         canvas->drawRect(r, paint);
 *                         canvas->restore();
 *
 *                         x += r.width() * 4 / 3;
 *                     }
 *                 }
 *                 canvas->drawString(SkStringPrintf("%s, %s", gConfigNames[i], gFilterNames[j]),
 *                                    x, y + r.height() * 2 / 3, font, textPaint);
 *
 *                 y += r.height() * 4 / 3;
 *             }
 *         }
 *     }
 *
 * private:
 *     bool fPowerOfTwoSize;
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class TilingGM public constructor(
  powerOfTwoSize: Boolean,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkBitmap    fTexture
   * ```
   */
  public var fTexture: SkBitmap = TODO("Initialize fTexture")

  /**
   * C++ original:
   * ```cpp
   * bool fPowerOfTwoSize
   * ```
   */
  private var fPowerOfTwoSize: Boolean = TODO("Initialize fPowerOfTwoSize")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         SkString name("tilemodes");
   *         if (!fPowerOfTwoSize) {
   *             name.append("_npot");
   *         }
   *         return name;
   *     }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(880, 560); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         int size = fPowerOfTwoSize ? kPOTSize : kNPOTSize;
   *         for (size_t i = 0; i < std::size(gColorTypes); i++) {
   *             makebm(&fTexture[i], gColorTypes[i], size, size);
   *         }
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
   *         SkPaint textPaint;
   *         SkFont  font = ToolUtils::DefaultPortableFont();
   *
   *         int size = fPowerOfTwoSize ? kPOTSize : kNPOTSize;
   *
   *         SkRect r = { 0, 0, SkIntToScalar(size*2), SkIntToScalar(size*2) };
   *
   *         const char* gConfigNames[] = { "8888", "565" };
   *
   *         constexpr SkFilterMode gFilters[] = { SkFilterMode::kNearest, SkFilterMode::kLinear };
   *         static const char* gFilterNames[] = { "point", "bilinear" };
   *
   *         constexpr SkTileMode gModes[] = {
   *             SkTileMode::kClamp, SkTileMode::kRepeat, SkTileMode::kMirror };
   *         static const char* gModeNames[] = { "C", "R", "M" };
   *
   *         SkScalar y = SkIntToScalar(24);
   *         SkScalar x = SkIntToScalar(10);
   *
   *         for (size_t kx = 0; kx < std::size(gModes); kx++) {
   *             for (size_t ky = 0; ky < std::size(gModes); ky++) {
   *                 SkPaint p;
   *                 p.setDither(true);
   *                 SkString str;
   *                 str.printf("[%s,%s]", gModeNames[kx], gModeNames[ky]);
   *
   *                 SkTextUtils::DrawString(canvas, str.c_str(), x + r.width()/2, y, font, p,
   *                                         SkTextUtils::kCenter_Align);
   *
   *                 x += r.width() * 4 / 3;
   *             }
   *         }
   *
   *         y += SkIntToScalar(16);
   *
   *         for (size_t i = 0; i < std::size(gColorTypes); i++) {
   *             for (size_t j = 0; j < std::size(gFilters); j++) {
   *                 x = SkIntToScalar(10);
   *                 for (size_t kx = 0; kx < std::size(gModes); kx++) {
   *                     for (size_t ky = 0; ky < std::size(gModes); ky++) {
   *                         SkPaint paint;
   * #if 1 // Temporary change to regen bitmap before each draw. This may help tracking down an issue
   *       // on SGX where resizing NPOT textures to POT textures exhibits a driver bug.
   *                         if (!fPowerOfTwoSize) {
   *                             makebm(&fTexture[i], gColorTypes[i], size, size);
   *                         }
   * #endif
   *                         setup(canvas, &paint, fTexture[i], gFilters[j], gModes[kx], gModes[ky]);
   *                         paint.setDither(true);
   *
   *                         canvas->save();
   *                         canvas->translate(x, y);
   *                         canvas->drawRect(r, paint);
   *                         canvas->restore();
   *
   *                         x += r.width() * 4 / 3;
   *                     }
   *                 }
   *                 canvas->drawString(SkStringPrintf("%s, %s", gConfigNames[i], gFilterNames[j]),
   *                                    x, y + r.height() * 2 / 3, font, textPaint);
   *
   *                 y += r.height() * 4 / 3;
   *             }
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    public val kPOTSize: Int = TODO("Initialize kPOTSize")

    public val kNPOTSize: Int = TODO("Initialize kNPOTSize")
  }
}
