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
 * class ScaledTilingGM : public skiagm::GM {
 * public:
 *     ScaledTilingGM(bool powerOfTwoSize)
 *             : fPowerOfTwoSize(powerOfTwoSize) {
 *     }
 *
 *     SkBitmap    fTexture[std::size(gColorTypes)];
 *
 * protected:
 *     static constexpr int kPOTSize = 4;
 *     static constexpr int kNPOTSize = 3;
 *
 *     SkString getName() const override {
 *         SkString name("scaled_tilemodes");
 *         if (!fPowerOfTwoSize) {
 *             name.append("_npot");
 *         }
 *         return name;
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(880, 880); }
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
 *         SkFont  font(ToolUtils::DefaultPortableTypeface(), 12);
 *
 *         float scale = 32.f/kPOTSize;
 *
 *         int size = fPowerOfTwoSize ? kPOTSize : kNPOTSize;
 *
 *         SkRect r = { 0, 0, SkIntToScalar(size*2), SkIntToScalar(size*2) };
 *
 *         const char* gColorTypeNames[] = { "8888", "565" };
 *
 *         const char* gFilterNames[] = { "Nearest", "Linear", "Trilinear", "Mitchell", "Aniso" };
 *
 *         constexpr SkTileMode gModes[] = {
 *             SkTileMode::kClamp, SkTileMode::kRepeat, SkTileMode::kMirror };
 *         const char* gModeNames[] = { "C", "R", "M" };
 *
 *         SkScalar y = SkIntToScalar(24);
 *         SkScalar x = SkIntToScalar(10)/scale;
 *
 *         for (size_t kx = 0; kx < std::size(gModes); kx++) {
 *             for (size_t ky = 0; ky < std::size(gModes); ky++) {
 *                 SkString str;
 *                 str.printf("[%s,%s]", gModeNames[kx], gModeNames[ky]);
 *
 *                 SkTextUtils::DrawString(canvas, str.c_str(), scale*(x + r.width()/2), y, font, SkPaint(),
 *                                         SkTextUtils::kCenter_Align);
 *
 *                 x += r.width() * 4 / 3;
 *             }
 *         }
 *
 *         y = SkIntToScalar(40) / scale;
 *
 *         for (size_t i = 0; i < std::size(gColorTypes); i++) {
 *             for (size_t j = 0; j < std::size(gSamplings); j++) {
 *                 x = SkIntToScalar(10)/scale;
 *                 for (size_t kx = 0; kx < std::size(gModes); kx++) {
 *                     for (size_t ky = 0; ky < std::size(gModes); ky++) {
 *                         SkPaint paint;
 * #if 1 // Temporary change to regen bitmap before each draw. This may help tracking down an issue
 *       // on SGX where resizing NPOT textures to POT textures exhibits a driver bug.
 *                         if (!fPowerOfTwoSize) {
 *                             makebm(&fTexture[i], gColorTypes[i], size, size);
 *                         }
 * #endif
 *                         setup(&paint, fTexture[i], gSamplings[j], gModes[kx], gModes[ky]);
 *                         paint.setDither(true);
 *
 *                         canvas->save();
 *                         canvas->scale(scale,scale);
 *                         canvas->translate(x, y);
 *                         canvas->drawRect(r, paint);
 *                         canvas->restore();
 *
 *                         x += r.width() * 4 / 3;
 *                     }
 *                 }
 *                 canvas->drawString(SkStringPrintf("%s, %s", gColorTypeNames[i], gFilterNames[j]),
 *                                    scale * x, scale * (y + r.height() * 2 / 3), font, textPaint);
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
public open class ScaledTilingGM public constructor(
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
   * static constexpr int kPOTSize = 4
   * ```
   */
  private var fPowerOfTwoSize: Boolean = TODO("Initialize fPowerOfTwoSize")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         SkString name("scaled_tilemodes");
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
   * SkISize getISize() override { return SkISize::Make(880, 880); }
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
   *         SkFont  font(ToolUtils::DefaultPortableTypeface(), 12);
   *
   *         float scale = 32.f/kPOTSize;
   *
   *         int size = fPowerOfTwoSize ? kPOTSize : kNPOTSize;
   *
   *         SkRect r = { 0, 0, SkIntToScalar(size*2), SkIntToScalar(size*2) };
   *
   *         const char* gColorTypeNames[] = { "8888", "565" };
   *
   *         const char* gFilterNames[] = { "Nearest", "Linear", "Trilinear", "Mitchell", "Aniso" };
   *
   *         constexpr SkTileMode gModes[] = {
   *             SkTileMode::kClamp, SkTileMode::kRepeat, SkTileMode::kMirror };
   *         const char* gModeNames[] = { "C", "R", "M" };
   *
   *         SkScalar y = SkIntToScalar(24);
   *         SkScalar x = SkIntToScalar(10)/scale;
   *
   *         for (size_t kx = 0; kx < std::size(gModes); kx++) {
   *             for (size_t ky = 0; ky < std::size(gModes); ky++) {
   *                 SkString str;
   *                 str.printf("[%s,%s]", gModeNames[kx], gModeNames[ky]);
   *
   *                 SkTextUtils::DrawString(canvas, str.c_str(), scale*(x + r.width()/2), y, font, SkPaint(),
   *                                         SkTextUtils::kCenter_Align);
   *
   *                 x += r.width() * 4 / 3;
   *             }
   *         }
   *
   *         y = SkIntToScalar(40) / scale;
   *
   *         for (size_t i = 0; i < std::size(gColorTypes); i++) {
   *             for (size_t j = 0; j < std::size(gSamplings); j++) {
   *                 x = SkIntToScalar(10)/scale;
   *                 for (size_t kx = 0; kx < std::size(gModes); kx++) {
   *                     for (size_t ky = 0; ky < std::size(gModes); ky++) {
   *                         SkPaint paint;
   * #if 1 // Temporary change to regen bitmap before each draw. This may help tracking down an issue
   *       // on SGX where resizing NPOT textures to POT textures exhibits a driver bug.
   *                         if (!fPowerOfTwoSize) {
   *                             makebm(&fTexture[i], gColorTypes[i], size, size);
   *                         }
   * #endif
   *                         setup(&paint, fTexture[i], gSamplings[j], gModes[kx], gModes[ky]);
   *                         paint.setDither(true);
   *
   *                         canvas->save();
   *                         canvas->scale(scale,scale);
   *                         canvas->translate(x, y);
   *                         canvas->drawRect(r, paint);
   *                         canvas->restore();
   *
   *                         x += r.width() * 4 / 3;
   *                     }
   *                 }
   *                 canvas->drawString(SkStringPrintf("%s, %s", gColorTypeNames[i], gFilterNames[j]),
   *                                    scale * x, scale * (y + r.height() * 2 / 3), font, textPaint);
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
    protected val kPOTSize: Int = TODO("Initialize kPOTSize")

    protected val kNPOTSize: Int = TODO("Initialize kNPOTSize")
  }
}
