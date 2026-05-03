package org.skia.tests

import kotlin.Array
import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkSp
import org.skia.foundation.SkTypeface
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class FontCacheGM : public skiagm::GM {
 * public:
 *     FontCacheGM(bool allowMultipleTextures) : fAllowMultipleTextures(allowMultipleTextures) {
 *         this->setBGColor(SK_ColorLTGRAY);
 *     }
 *
 * #if defined(SK_GANESH)
 *     void modifyGrContextOptions(GrContextOptions* options) override {
 *         options->fGlyphCacheTextureMaximumBytes = 0;
 *         using Enable = GrContextOptions::Enable;
 *         options->fAllowMultipleGlyphCacheTextures = fAllowMultipleTextures ? Enable::kYes
 *                                                                            : Enable::kNo;
 *     }
 * #endif
 *
 * protected:
 *     SkString getName() const override {
 *         SkString name("fontcache");
 *         if (fAllowMultipleTextures) {
 *             name.append("-mt");
 *         }
 *         return name;
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(kSize, kSize); }
 *
 *     void onOnceBeforeDraw() override {
 *         fTypefaces[0] = ToolUtils::CreatePortableTypeface("serif", SkFontStyle::Italic());
 *         fTypefaces[1] = ToolUtils::CreatePortableTypeface("sans-serif", SkFontStyle::Italic());
 *         fTypefaces[2] = ToolUtils::CreatePortableTypeface("serif", SkFontStyle::Normal());
 *         fTypefaces[3] = ToolUtils::CreatePortableTypeface("sans-serif", SkFontStyle::Normal());
 *         fTypefaces[4] = ToolUtils::CreatePortableTypeface("serif", SkFontStyle::Bold());
 *         fTypefaces[5] = ToolUtils::CreatePortableTypeface("sans-serif", SkFontStyle::Bold());
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         this->drawText(canvas);
 * #if defined(SK_GANESH)
 *         //  Debugging tool for Ganesh.
 *         static const bool kShowAtlas = false;
 *         if (kShowAtlas) {
 *             if (auto dContext = GrAsDirectContext(canvas->recordingContext())) {
 *                 auto img = dContext->priv().testingOnly_getFontAtlasImage(MaskFormat::kA8);
 *                 canvas->drawImage(img, 0, 0);
 *             }
 *         }
 * #endif
 *     }
 *
 * private:
 *     void drawText(SkCanvas* canvas) {
 *         static const int kSizes[] = {8, 9, 10, 11, 12, 13, 18, 20, 25};
 *
 *         static const SkString kTexts[] = {SkString("ABCDEFGHIJKLMNOPQRSTUVWXYZ"),
 *                                           SkString("abcdefghijklmnopqrstuvwxyz"),
 *                                           SkString("0123456789"),
 *                                           SkString("!@#$%^&*()<>[]{}")};
 *         SkFont font = ToolUtils::DefaultPortableFont();
 *         font.setEdging(SkFont::Edging::kAntiAlias);
 *         font.setSubpixel(true);
 *
 *         static const SkScalar kSubPixelInc = 1 / 2.f;
 *         SkScalar x = 0;
 *         SkScalar y = 10;
 *         SkScalar subpixelX = 0;
 *         SkScalar subpixelY = 0;
 *         bool offsetX = true;
 *
 *         if (fAllowMultipleTextures) {
 *             canvas->scale(10, 10);
 *         }
 *
 *         do {
 *             for (auto s : kSizes) {
 *                 auto size = 2 * s;
 *                 font.setSize(size);
 *                 for (const auto& typeface : fTypefaces) {
 *                     font.setTypeface(typeface);
 *                     for (const auto& text : kTexts) {
 *                         x = size + draw_string(canvas, text, x + subpixelX, y + subpixelY, font);
 *                         x = SkScalarCeilToScalar(x);
 *                         if (x + 100 > kSize) {
 *                             x = 0;
 *                             y += SkScalarCeilToScalar(size + 3);
 *                             if (y > kSize) {
 *                                 return;
 *                             }
 *                         }
 *                     }
 *                 }
 *                 (offsetX ? subpixelX : subpixelY) += kSubPixelInc;
 *                 offsetX = !offsetX;
 *             }
 *         } while (true);
 *     }
 *
 *     inline static constexpr SkScalar kSize = 1280;
 *
 *     bool fAllowMultipleTextures;
 *     sk_sp<SkTypeface> fTypefaces[6];
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class FontCacheGM public constructor(
  allowMultipleTextures: Boolean,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr SkScalar kSize = 1280
   * ```
   */
  private var fAllowMultipleTextures: Boolean = TODO("Initialize fAllowMultipleTextures")

  /**
   * C++ original:
   * ```cpp
   * bool fAllowMultipleTextures
   * ```
   */
  private var fTypefaces: Array<SkSp<SkTypeface>> = TODO("Initialize fTypefaces")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         SkString name("fontcache");
   *         if (fAllowMultipleTextures) {
   *             name.append("-mt");
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
   * SkISize getISize() override { return SkISize::Make(kSize, kSize); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         fTypefaces[0] = ToolUtils::CreatePortableTypeface("serif", SkFontStyle::Italic());
   *         fTypefaces[1] = ToolUtils::CreatePortableTypeface("sans-serif", SkFontStyle::Italic());
   *         fTypefaces[2] = ToolUtils::CreatePortableTypeface("serif", SkFontStyle::Normal());
   *         fTypefaces[3] = ToolUtils::CreatePortableTypeface("sans-serif", SkFontStyle::Normal());
   *         fTypefaces[4] = ToolUtils::CreatePortableTypeface("serif", SkFontStyle::Bold());
   *         fTypefaces[5] = ToolUtils::CreatePortableTypeface("sans-serif", SkFontStyle::Bold());
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
   *         this->drawText(canvas);
   * #if defined(SK_GANESH)
   *         //  Debugging tool for Ganesh.
   *         static const bool kShowAtlas = false;
   *         if (kShowAtlas) {
   *             if (auto dContext = GrAsDirectContext(canvas->recordingContext())) {
   *                 auto img = dContext->priv().testingOnly_getFontAtlasImage(MaskFormat::kA8);
   *                 canvas->drawImage(img, 0, 0);
   *             }
   *         }
   * #endif
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawText(SkCanvas* canvas) {
   *         static const int kSizes[] = {8, 9, 10, 11, 12, 13, 18, 20, 25};
   *
   *         static const SkString kTexts[] = {SkString("ABCDEFGHIJKLMNOPQRSTUVWXYZ"),
   *                                           SkString("abcdefghijklmnopqrstuvwxyz"),
   *                                           SkString("0123456789"),
   *                                           SkString("!@#$%^&*()<>[]{}")};
   *         SkFont font = ToolUtils::DefaultPortableFont();
   *         font.setEdging(SkFont::Edging::kAntiAlias);
   *         font.setSubpixel(true);
   *
   *         static const SkScalar kSubPixelInc = 1 / 2.f;
   *         SkScalar x = 0;
   *         SkScalar y = 10;
   *         SkScalar subpixelX = 0;
   *         SkScalar subpixelY = 0;
   *         bool offsetX = true;
   *
   *         if (fAllowMultipleTextures) {
   *             canvas->scale(10, 10);
   *         }
   *
   *         do {
   *             for (auto s : kSizes) {
   *                 auto size = 2 * s;
   *                 font.setSize(size);
   *                 for (const auto& typeface : fTypefaces) {
   *                     font.setTypeface(typeface);
   *                     for (const auto& text : kTexts) {
   *                         x = size + draw_string(canvas, text, x + subpixelX, y + subpixelY, font);
   *                         x = SkScalarCeilToScalar(x);
   *                         if (x + 100 > kSize) {
   *                             x = 0;
   *                             y += SkScalarCeilToScalar(size + 3);
   *                             if (y > kSize) {
   *                                 return;
   *                             }
   *                         }
   *                     }
   *                 }
   *                 (offsetX ? subpixelX : subpixelY) += kSubPixelInc;
   *                 offsetX = !offsetX;
   *             }
   *         } while (true);
   *     }
   * ```
   */
  private fun drawText(canvas: SkCanvas?) {
    TODO("Implement drawText")
  }

  public companion object {
    private val kSize: SkScalar = TODO("Initialize kSize")
  }
}
