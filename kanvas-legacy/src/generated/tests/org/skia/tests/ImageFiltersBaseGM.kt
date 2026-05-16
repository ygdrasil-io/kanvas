package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class ImageFiltersBaseGM : public skiagm::GM {
 * public:
 *     ImageFiltersBaseGM () {}
 *
 * protected:
 *     SkString getName() const override { return SkString("imagefiltersbase"); }
 *
 *     SkISize getISize() override { return SkISize::Make(700, 500); }
 *
 *     void draw_frame(SkCanvas* canvas, const SkRect& r) {
 *         SkPaint paint;
 *         paint.setStyle(SkPaint::kStroke_Style);
 *         paint.setColor(SK_ColorRED);
 *         canvas->drawRect(r, paint);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         if (fAtlas == nullptr) {
 *             fAtlas = create_atlas_image(canvas);
 *         }
 *
 *         void (*drawProc[])(SkCanvas*, SkImage*, const SkRect&, sk_sp<SkImageFilter>) = {
 *             draw_paint,
 *             draw_line, draw_rect, draw_path, draw_text,
 *             draw_bitmap, draw_patch, draw_atlas
 *         };
 *
 *         auto cf = SkColorFilters::Blend(SK_ColorRED, SkBlendMode::kSrcIn);
 *         sk_sp<SkImageFilter> filters[] = {
 *             nullptr,
 *             SkImageFilters::Offset(0.f, 0.f, nullptr), // "identity"
 *             SkImageFilters::Empty(),
 *             SkImageFilters::ColorFilter(std::move(cf), nullptr),
 *             // The strange 0.29 value tickles an edge case where crop rect calculates
 *             // a small border, but the blur really needs no border. This tickles
 *             // an msan uninitialized value bug.
 *             SkImageFilters::Blur(12.0f, 0.29f, nullptr),
 *             SkImageFilters::DropShadow(10.0f, 5.0f, 3.0f, 3.0f, SK_ColorBLUE, nullptr),
 *         };
 *
 *         SkRect r = SkRect::MakeWH(SkIntToScalar(64), SkIntToScalar(64));
 *         SkScalar MARGIN = SkIntToScalar(16);
 *         SkScalar DX = r.width() + MARGIN;
 *         SkScalar DY = r.height() + MARGIN;
 *
 *         canvas->translate(MARGIN, MARGIN);
 *         for (size_t i = 0; i < std::size(drawProc); ++i) {
 *             canvas->save();
 *             for (size_t j = 0; j < std::size(filters); ++j) {
 *                 drawProc[i](canvas, fAtlas.get(), r, filters[j]);
 *
 *                 draw_frame(canvas, r);
 *                 canvas->translate(0, DY);
 *             }
 *             canvas->restore();
 *             canvas->translate(DX, 0);
 *         }
 *     }
 *
 * private:
 *     static sk_sp<SkImage> create_atlas_image(SkCanvas* canvas) {
 *         static constexpr SkSize kSize = {64, 64};
 *         SkImageInfo atlasInfo = SkImageInfo::MakeN32Premul(kSize.fWidth, kSize.fHeight);
 *         sk_sp<SkSurface> atlasSurface(ToolUtils::makeSurface(canvas, atlasInfo));
 *         SkCanvas* atlasCanvas = atlasSurface->getCanvas();
 *
 *         SkPaint atlasPaint;
 *         atlasPaint.setColor(SK_ColorGRAY);
 *         SkFont font(ToolUtils::DefaultPortableTypeface(), kSize.fHeight * 0.4f);
 *         SkTextUtils::DrawString(atlasCanvas, "Atlas", kSize.fWidth * 0.5f, kSize.fHeight * 0.5f,
 *                                 font, atlasPaint, SkTextUtils::kCenter_Align);
 *         return atlasSurface->makeImageSnapshot();
 *     }
 *
 *     sk_sp<SkImage> fAtlas;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class ImageFiltersBaseGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fAtlas
   * ```
   */
  private var fAtlas: SkSp<SkImage> = TODO("Initialize fAtlas")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("imagefiltersbase"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(700, 500); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void draw_frame(SkCanvas* canvas, const SkRect& r) {
   *         SkPaint paint;
   *         paint.setStyle(SkPaint::kStroke_Style);
   *         paint.setColor(SK_ColorRED);
   *         canvas->drawRect(r, paint);
   *     }
   * ```
   */
  protected fun drawFrame(canvas: SkCanvas?, r: SkRect) {
    TODO("Implement drawFrame")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         if (fAtlas == nullptr) {
   *             fAtlas = create_atlas_image(canvas);
   *         }
   *
   *         void (*drawProc[])(SkCanvas*, SkImage*, const SkRect&, sk_sp<SkImageFilter>) = {
   *             draw_paint,
   *             draw_line, draw_rect, draw_path, draw_text,
   *             draw_bitmap, draw_patch, draw_atlas
   *         };
   *
   *         auto cf = SkColorFilters::Blend(SK_ColorRED, SkBlendMode::kSrcIn);
   *         sk_sp<SkImageFilter> filters[] = {
   *             nullptr,
   *             SkImageFilters::Offset(0.f, 0.f, nullptr), // "identity"
   *             SkImageFilters::Empty(),
   *             SkImageFilters::ColorFilter(std::move(cf), nullptr),
   *             // The strange 0.29 value tickles an edge case where crop rect calculates
   *             // a small border, but the blur really needs no border. This tickles
   *             // an msan uninitialized value bug.
   *             SkImageFilters::Blur(12.0f, 0.29f, nullptr),
   *             SkImageFilters::DropShadow(10.0f, 5.0f, 3.0f, 3.0f, SK_ColorBLUE, nullptr),
   *         };
   *
   *         SkRect r = SkRect::MakeWH(SkIntToScalar(64), SkIntToScalar(64));
   *         SkScalar MARGIN = SkIntToScalar(16);
   *         SkScalar DX = r.width() + MARGIN;
   *         SkScalar DY = r.height() + MARGIN;
   *
   *         canvas->translate(MARGIN, MARGIN);
   *         for (size_t i = 0; i < std::size(drawProc); ++i) {
   *             canvas->save();
   *             for (size_t j = 0; j < std::size(filters); ++j) {
   *                 drawProc[i](canvas, fAtlas.get(), r, filters[j]);
   *
   *                 draw_frame(canvas, r);
   *                 canvas->translate(0, DY);
   *             }
   *             canvas->restore();
   *             canvas->translate(DX, 0);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkImage> create_atlas_image(SkCanvas* canvas) {
     *         static constexpr SkSize kSize = {64, 64};
     *         SkImageInfo atlasInfo = SkImageInfo::MakeN32Premul(kSize.fWidth, kSize.fHeight);
     *         sk_sp<SkSurface> atlasSurface(ToolUtils::makeSurface(canvas, atlasInfo));
     *         SkCanvas* atlasCanvas = atlasSurface->getCanvas();
     *
     *         SkPaint atlasPaint;
     *         atlasPaint.setColor(SK_ColorGRAY);
     *         SkFont font(ToolUtils::DefaultPortableTypeface(), kSize.fHeight * 0.4f);
     *         SkTextUtils::DrawString(atlasCanvas, "Atlas", kSize.fWidth * 0.5f, kSize.fHeight * 0.5f,
     *                                 font, atlasPaint, SkTextUtils::kCenter_Align);
     *         return atlasSurface->makeImageSnapshot();
     *     }
     * ```
     */
    private fun createAtlasImage(canvas: SkCanvas?): SkSp<SkImage> {
      TODO("Implement createAtlasImage")
    }
  }
}
