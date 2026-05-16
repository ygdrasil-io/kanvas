package org.skia.tests

import kotlin.Float
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.core.SkTextBlob
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * class RSXShaderGM : public skiagm::GM {
 * public:
 * private:
 *     SkString getName() const override { return SkString("rsx_blob_shader"); }
 *
 *     SkISize getISize() override { return SkISize::Make(kSZ * kScale * 2.1f, kSZ * kScale * 2.1f); }
 *
 *     void onOnceBeforeDraw() override {
 *         const SkFontStyle style(SkFontStyle::kExtraBlack_Weight,
 *                                 SkFontStyle::kNormal_Width,
 *                                 SkFontStyle::kUpright_Slant);
 *         SkFont font(ToolUtils::CreatePortableTypeface("Sans", style), kFontSZ);
 *         font.setEdging(SkFont::Edging::kAntiAlias);
 *
 *         static constexpr char txt[] = "TEST";
 *         SkGlyphID glyphs[16];
 *         float     widths[16];
 *         const auto glyph_count = font.textToGlyphs(txt, strlen(txt), SkTextEncoding::kUTF8, glyphs);
 *         font.getWidths({glyphs, glyph_count}, {widths, glyph_count});
 *
 *         SkTextBlobBuilder builder;
 *         const auto& buf = builder.allocRunRSXform(font, glyph_count);
 *         std::copy(glyphs, glyphs + glyph_count, buf.glyphs);
 *
 *         float x = 0;
 *         for (size_t i = 0; i < glyph_count; ++i) {
 *             buf.xforms()[i] = {
 *                 1, 0,
 *                 x, 0,
 *             };
 *             x += widths[i];
 *         }
 *
 *         fBlob = builder.make();
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->scale(kScale, kScale);
 *         this->draw_one(canvas,
 *             {0, 0}, SkMatrix::I(), SkMatrix::I());
 *         this->draw_one(canvas,
 *             {kSZ*1.1f, 0}, SkMatrix::Scale(2, 2), SkMatrix::I());
 *         this->draw_one(canvas,
 *             {0, kSZ*1.1f}, SkMatrix::I(), SkMatrix::RotateDeg(45));
 *         this->draw_one(canvas,
 *             {kSZ*1.1f, kSZ*1.1f}, SkMatrix::Scale(2, 2), SkMatrix::RotateDeg(45));
 *     }
 *
 *     void draw_one(SkCanvas* canvas, SkPoint pos, const SkMatrix& lm,
 *                   const SkMatrix& outer_lm) const {
 *         SkAutoCanvasRestore acr(canvas, true);
 *         canvas->translate(pos.fX, pos.fY);
 *
 *         SkPaint p;
 *         p.setShader(make_shader(lm, outer_lm));
 *         p.setAlphaf(0.75f);
 *         canvas->drawRect(SkRect::MakeWH(kSZ, kSZ), p);
 *
 *         p.setAlphaf(1);
 *         canvas->drawTextBlob(fBlob, 0, kFontSZ*1, p);
 *         canvas->drawTextBlob(fBlob, 0, kFontSZ*2, p);
 *     }
 *
 *     static sk_sp<SkShader> make_shader(const SkMatrix& lm, const SkMatrix& outer_lm) {
 *         static constexpr SkISize kTileSize = { 30, 30 };
 *         auto surface = SkSurfaces::Raster(
 *                 SkImageInfo::MakeN32Premul(kTileSize.width(), kTileSize.height()));
 *
 *         SkPaint p;
 *         p.setColor(0xffffff00);
 *         surface->getCanvas()->drawPaint(p);
 *         p.setColor(0xff008000);
 *         surface->getCanvas()
 *                ->drawRect({0, 0, kTileSize.width()*0.9f, kTileSize.height()*0.9f}, p);
 *
 *         return surface->makeImageSnapshot()
 *                 ->makeShader(SkTileMode::kRepeat, SkTileMode::kRepeat,
 *                              SkSamplingOptions(SkFilterMode::kLinear), &lm)
 *                 ->makeWithLocalMatrix(outer_lm);
 *     }
 *
 *     inline static constexpr float kSZ     = 300,
 *                                   kFontSZ = kSZ * 0.38,
 *                                   kScale  = 1.4f;
 *
 *     sk_sp<SkTextBlob> fBlob;
 * }
 * ```
 */
public open class RSXShaderGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr float kSZ     = 300
   * ```
   */
  private var fBlob: SkSp<SkTextBlob> = TODO("Initialize fBlob")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("rsx_blob_shader"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(kSZ * kScale * 2.1f, kSZ * kScale * 2.1f); }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         const SkFontStyle style(SkFontStyle::kExtraBlack_Weight,
   *                                 SkFontStyle::kNormal_Width,
   *                                 SkFontStyle::kUpright_Slant);
   *         SkFont font(ToolUtils::CreatePortableTypeface("Sans", style), kFontSZ);
   *         font.setEdging(SkFont::Edging::kAntiAlias);
   *
   *         static constexpr char txt[] = "TEST";
   *         SkGlyphID glyphs[16];
   *         float     widths[16];
   *         const auto glyph_count = font.textToGlyphs(txt, strlen(txt), SkTextEncoding::kUTF8, glyphs);
   *         font.getWidths({glyphs, glyph_count}, {widths, glyph_count});
   *
   *         SkTextBlobBuilder builder;
   *         const auto& buf = builder.allocRunRSXform(font, glyph_count);
   *         std::copy(glyphs, glyphs + glyph_count, buf.glyphs);
   *
   *         float x = 0;
   *         for (size_t i = 0; i < glyph_count; ++i) {
   *             buf.xforms()[i] = {
   *                 1, 0,
   *                 x, 0,
   *             };
   *             x += widths[i];
   *         }
   *
   *         fBlob = builder.make();
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
   *         canvas->scale(kScale, kScale);
   *         this->draw_one(canvas,
   *             {0, 0}, SkMatrix::I(), SkMatrix::I());
   *         this->draw_one(canvas,
   *             {kSZ*1.1f, 0}, SkMatrix::Scale(2, 2), SkMatrix::I());
   *         this->draw_one(canvas,
   *             {0, kSZ*1.1f}, SkMatrix::I(), SkMatrix::RotateDeg(45));
   *         this->draw_one(canvas,
   *             {kSZ*1.1f, kSZ*1.1f}, SkMatrix::Scale(2, 2), SkMatrix::RotateDeg(45));
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void draw_one(SkCanvas* canvas, SkPoint pos, const SkMatrix& lm,
   *                   const SkMatrix& outer_lm) const {
   *         SkAutoCanvasRestore acr(canvas, true);
   *         canvas->translate(pos.fX, pos.fY);
   *
   *         SkPaint p;
   *         p.setShader(make_shader(lm, outer_lm));
   *         p.setAlphaf(0.75f);
   *         canvas->drawRect(SkRect::MakeWH(kSZ, kSZ), p);
   *
   *         p.setAlphaf(1);
   *         canvas->drawTextBlob(fBlob, 0, kFontSZ*1, p);
   *         canvas->drawTextBlob(fBlob, 0, kFontSZ*2, p);
   *     }
   * ```
   */
  private fun drawOne(
    canvas: SkCanvas?,
    pos: SkPoint,
    lm: SkMatrix,
    outerLm: SkMatrix,
  ) {
    TODO("Implement drawOne")
  }

  public companion object {
    private val kSZ: Float = TODO("Initialize kSZ")

    private val kFontSZ: Float = TODO("Initialize kFontSZ")

    private val kScale: Float = TODO("Initialize kScale")

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkShader> make_shader(const SkMatrix& lm, const SkMatrix& outer_lm) {
     *         static constexpr SkISize kTileSize = { 30, 30 };
     *         auto surface = SkSurfaces::Raster(
     *                 SkImageInfo::MakeN32Premul(kTileSize.width(), kTileSize.height()));
     *
     *         SkPaint p;
     *         p.setColor(0xffffff00);
     *         surface->getCanvas()->drawPaint(p);
     *         p.setColor(0xff008000);
     *         surface->getCanvas()
     *                ->drawRect({0, 0, kTileSize.width()*0.9f, kTileSize.height()*0.9f}, p);
     *
     *         return surface->makeImageSnapshot()
     *                 ->makeShader(SkTileMode::kRepeat, SkTileMode::kRepeat,
     *                              SkSamplingOptions(SkFilterMode::kLinear), &lm)
     *                 ->makeWithLocalMatrix(outer_lm);
     *     }
     * ```
     */
    private fun makeShader(lm: SkMatrix, outerLm: SkMatrix): SkSp<SkShader> {
      TODO("Implement makeShader")
    }
  }
}
