package org.skia.tests

import kotlin.Array
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.core.SkTextBlob
import org.skia.foundation.SkSp
import org.skia.gpu.ContextOptions
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class FontRegenGM : public skiagm::GM {
 * #if defined(SK_GANESH)
 *     void modifyGrContextOptions(GrContextOptions* options) override {
 *         options->fGlyphCacheTextureMaximumBytes = 0;
 *         options->fAllowMultipleGlyphCacheTextures = GrContextOptions::Enable::kNo;
 *     }
 * #endif
 *
 * #if defined(SK_GRAPHITE)
 *     void modifyGraphiteContextOptions(skgpu::graphite::ContextOptions* options) const override {
 *         options->fGlyphCacheTextureMaximumBytes = 0;
 *         options->fAllowMultipleAtlasTextures = false;
 *     }
 * #endif
 *
 *     SkString getName() const override { return SkString("fontregen"); }
 *
 *     SkISize getISize() override { return {kSize, kSize}; }
 *
 *     void onOnceBeforeDraw() override {
 *         this->setBGColor(SK_ColorLTGRAY);
 *
 *         auto tf = ToolUtils::CreatePortableTypeface("sans-serif", SkFontStyle::Normal());
 *
 *         static const SkString kTexts[] = {
 *             SkString("abcdefghijklmnopqrstuvwxyz"),
 *             SkString("ABCDEFGHI"),
 *             SkString("NOPQRSTUV")
 *         };
 *
 *         SkFont font;
 *         font.setEdging(SkFont::Edging::kAntiAlias);
 *         font.setSubpixel(false);
 *         font.setSize(80);
 *         font.setTypeface(tf);
 *
 *         fBlobs[0] = make_blob(kTexts[0], font);
 *         font.setSize(162);
 *         fBlobs[1] = make_blob(kTexts[1], font);
 *         fBlobs[2] = make_blob(kTexts[2], font);
 *     }
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *
 *
 *         SkPaint paint;
 *         paint.setColor(SK_ColorBLACK);
 *         canvas->drawTextBlob(fBlobs[0], 10, 80, paint);
 *         canvas->drawTextBlob(fBlobs[1], 10, 225, paint);
 *
 * #if defined(SK_GANESH)
 *         auto dContext = GrAsDirectContext(canvas->recordingContext());
 *         if (dContext) {
 *             dContext->flushAndSubmit();
 *         }
 * #endif
 *
 *         paint.setColor(0xFF010101);
 *         canvas->drawTextBlob(fBlobs[0], 10, 305, paint);
 *         canvas->drawTextBlob(fBlobs[2], 10, 465, paint);
 *
 * #if defined(SK_GANESH)
 *         //  Debugging tool for Ganesh.
 *         static const bool kShowAtlas = false;
 *         if (kShowAtlas && dContext) {
 *             auto img = dContext->priv().testingOnly_getFontAtlasImage(MaskFormat::kA8);
 *             canvas->drawImage(img, 200, 0);
 *         }
 * #endif
 *
 *         return DrawResult::kOk;
 *     }
 *
 * private:
 *     inline static constexpr int kSize = 512;
 *
 *     sk_sp<SkTextBlob> fBlobs[3];
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class FontRegenGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kSize = 512
   * ```
   */
  private var fBlobs: Array<SkSp<SkTextBlob>> = TODO("Initialize fBlobs")

  /**
   * C++ original:
   * ```cpp
   * void modifyGraphiteContextOptions(skgpu::graphite::ContextOptions* options) const override {
   *         options->fGlyphCacheTextureMaximumBytes = 0;
   *         options->fAllowMultipleAtlasTextures = false;
   *     }
   * ```
   */
  public override fun modifyGraphiteContextOptions(options: ContextOptions?) {
    TODO("Implement modifyGraphiteContextOptions")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("fontregen"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {kSize, kSize}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         this->setBGColor(SK_ColorLTGRAY);
   *
   *         auto tf = ToolUtils::CreatePortableTypeface("sans-serif", SkFontStyle::Normal());
   *
   *         static const SkString kTexts[] = {
   *             SkString("abcdefghijklmnopqrstuvwxyz"),
   *             SkString("ABCDEFGHI"),
   *             SkString("NOPQRSTUV")
   *         };
   *
   *         SkFont font;
   *         font.setEdging(SkFont::Edging::kAntiAlias);
   *         font.setSubpixel(false);
   *         font.setSize(80);
   *         font.setTypeface(tf);
   *
   *         fBlobs[0] = make_blob(kTexts[0], font);
   *         font.setSize(162);
   *         fBlobs[1] = make_blob(kTexts[1], font);
   *         fBlobs[2] = make_blob(kTexts[2], font);
   *     }
   * ```
   */
  public override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
   *
   *
   *         SkPaint paint;
   *         paint.setColor(SK_ColorBLACK);
   *         canvas->drawTextBlob(fBlobs[0], 10, 80, paint);
   *         canvas->drawTextBlob(fBlobs[1], 10, 225, paint);
   *
   * #if defined(SK_GANESH)
   *         auto dContext = GrAsDirectContext(canvas->recordingContext());
   *         if (dContext) {
   *             dContext->flushAndSubmit();
   *         }
   * #endif
   *
   *         paint.setColor(0xFF010101);
   *         canvas->drawTextBlob(fBlobs[0], 10, 305, paint);
   *         canvas->drawTextBlob(fBlobs[2], 10, 465, paint);
   *
   * #if defined(SK_GANESH)
   *         //  Debugging tool for Ganesh.
   *         static const bool kShowAtlas = false;
   *         if (kShowAtlas && dContext) {
   *             auto img = dContext->priv().testingOnly_getFontAtlasImage(MaskFormat::kA8);
   *             canvas->drawImage(img, 200, 0);
   *         }
   * #endif
   *
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement onDraw")
  }

  public companion object {
    private val kSize: Int = TODO("Initialize kSize")
  }
}
