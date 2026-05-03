package org.skia.tests

import kotlin.CharArray
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class Tiling2GM : public skiagm::GM {
 *     ShaderProc fProc;
 *     const char* fName;
 *
 * public:
 *     Tiling2GM(ShaderProc proc, const char name[]) : fProc(proc), fName(name) {}
 *
 * private:
 *     SkString getName() const override { return SkString(fName); }
 *
 *     SkISize getISize() override { return SkISize::Make(650, 610); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->scale(SkIntToScalar(3)/2, SkIntToScalar(3)/2);
 *
 *         const SkScalar w = SkIntToScalar(gWidth);
 *         const SkScalar h = SkIntToScalar(gHeight);
 *         SkRect r = { -w, -h, w*2, h*2 };
 *
 *         constexpr SkTileMode gModes[] = {
 *             SkTileMode::kClamp, SkTileMode::kRepeat, SkTileMode::kMirror
 *         };
 *         const char* gModeNames[] = {
 *             "Clamp", "Repeat", "Mirror"
 *         };
 *
 *         SkScalar y = SkIntToScalar(24);
 *         SkScalar x = SkIntToScalar(66);
 *
 *         SkFont font = ToolUtils::DefaultPortableFont();
 *
 *         for (size_t kx = 0; kx < std::size(gModes); kx++) {
 *             SkString str(gModeNames[kx]);
 *             SkTextUtils::DrawString(canvas, str.c_str(), x + r.width()/2, y, font, SkPaint(),
 *                                     SkTextUtils::kCenter_Align);
 *             x += r.width() * 4 / 3;
 *         }
 *
 *         y += SkIntToScalar(16) + h;
 *
 *         for (size_t ky = 0; ky < std::size(gModes); ky++) {
 *             x = SkIntToScalar(16) + w;
 *
 *             SkString str(gModeNames[ky]);
 *             SkTextUtils::DrawString(canvas, str.c_str(), x, y + h/2, font, SkPaint(),
 *                                     SkTextUtils::kRight_Align);
 *
 *             x += SkIntToScalar(50);
 *             for (size_t kx = 0; kx < std::size(gModes); kx++) {
 *                 SkPaint paint;
 *                 paint.setShader(fProc(gModes[kx], gModes[ky]));
 *
 *                 canvas->save();
 *                 canvas->translate(x, y);
 *                 canvas->drawRect(r, paint);
 *                 canvas->restore();
 *
 *                 x += r.width() * 4 / 3;
 *             }
 *             y += r.height() * 4 / 3;
 *         }
 *     }
 * }
 * ```
 */
public open class Tiling2GM public constructor(
  proc: ShaderProc,
  name: CharArray,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * ShaderProc fProc
   * ```
   */
  private var fProc: ShaderProc = TODO("Initialize fProc")

  /**
   * C++ original:
   * ```cpp
   * const char* fName
   * ```
   */
  private val fName: String? = TODO("Initialize fName")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString(fName); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(650, 610); }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         canvas->scale(SkIntToScalar(3)/2, SkIntToScalar(3)/2);
   *
   *         const SkScalar w = SkIntToScalar(gWidth);
   *         const SkScalar h = SkIntToScalar(gHeight);
   *         SkRect r = { -w, -h, w*2, h*2 };
   *
   *         constexpr SkTileMode gModes[] = {
   *             SkTileMode::kClamp, SkTileMode::kRepeat, SkTileMode::kMirror
   *         };
   *         const char* gModeNames[] = {
   *             "Clamp", "Repeat", "Mirror"
   *         };
   *
   *         SkScalar y = SkIntToScalar(24);
   *         SkScalar x = SkIntToScalar(66);
   *
   *         SkFont font = ToolUtils::DefaultPortableFont();
   *
   *         for (size_t kx = 0; kx < std::size(gModes); kx++) {
   *             SkString str(gModeNames[kx]);
   *             SkTextUtils::DrawString(canvas, str.c_str(), x + r.width()/2, y, font, SkPaint(),
   *                                     SkTextUtils::kCenter_Align);
   *             x += r.width() * 4 / 3;
   *         }
   *
   *         y += SkIntToScalar(16) + h;
   *
   *         for (size_t ky = 0; ky < std::size(gModes); ky++) {
   *             x = SkIntToScalar(16) + w;
   *
   *             SkString str(gModeNames[ky]);
   *             SkTextUtils::DrawString(canvas, str.c_str(), x, y + h/2, font, SkPaint(),
   *                                     SkTextUtils::kRight_Align);
   *
   *             x += SkIntToScalar(50);
   *             for (size_t kx = 0; kx < std::size(gModes); kx++) {
   *                 SkPaint paint;
   *                 paint.setShader(fProc(gModes[kx], gModes[ky]));
   *
   *                 canvas->save();
   *                 canvas->translate(x, y);
   *                 canvas->drawRect(r, paint);
   *                 canvas->restore();
   *
   *                 x += r.width() * 4 / 3;
   *             }
   *             y += r.height() * 4 / 3;
   *         }
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
