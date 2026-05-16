package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SkPath
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class SrcModeGM : public skiagm::GM {
 *     SkPath fPath;
 *
 *     void onOnceBeforeDraw() override { this->setBGColor(SK_ColorBLACK); }
 *
 *     SkString getName() const override { return SkString("srcmode"); }
 *
 *     SkISize getISize() override { return {640, 760}; }
 *
 *     void drawContent(SkCanvas* canvas) {
 *         canvas->translate(SkIntToScalar(20), SkIntToScalar(20));
 *
 *         SkPaint paint;
 *         SkFont  font(ToolUtils::DefaultPortableTypeface(), H / 4);
 *         paint.setColor(0x80F60000);
 *
 *         const Proc procs[] = {
 *             draw_hair, draw_thick, draw_rect, draw_oval, draw_text
 *         };
 *
 *         const SkBlendMode modes[] = {
 *             SkBlendMode::kSrcOver, SkBlendMode::kSrc, SkBlendMode::kClear
 *         };
 *
 *         const PaintProc paintProcs[] = {
 *             identity_paintproc, gradient_paintproc
 *         };
 *
 *         for (int aa = 0; aa <= 1; ++aa) {
 *             paint.setAntiAlias(SkToBool(aa));
 *             font.setEdging(SkToBool(aa) ? SkFont::Edging::kAntiAlias : SkFont::Edging::kAlias);
 *             canvas->save();
 *             for (size_t i = 0; i < std::size(paintProcs); ++i) {
 *                 paintProcs[i](&paint);
 *                 for (size_t x = 0; x < std::size(modes); ++x) {
 *                     paint.setBlendMode(modes[x]);
 *                     canvas->save();
 *                     for (size_t y = 0; y < std::size(procs); ++y) {
 *                         procs[y](canvas, paint, font);
 *                         canvas->translate(0, H * 5 / 4);
 *                     }
 *                     canvas->restore();
 *                     canvas->translate(W * 5 / 4, 0);
 *                 }
 *             }
 *             canvas->restore();
 *             canvas->translate(0, (H * 5 / 4) * std::size(procs));
 *         }
 *     }
 *
 *     static sk_sp<SkSurface> compat_surface(SkCanvas* canvas, const SkISize& size) {
 *         SkImageInfo info = SkImageInfo::MakeN32Premul(size);
 *         sk_sp<SkSurface> surface = canvas->makeSurface(info);
 *         if (nullptr == surface) {
 *             // picture canvas will return null, so fall-back to raster
 *             surface = SkSurfaces::Raster(info);
 *         }
 *         return surface;
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         auto surf(compat_surface(canvas, this->getISize()));
 *         surf->getCanvas()->drawColor(SK_ColorWHITE);
 *         this->drawContent(surf->getCanvas());
 *         surf->draw(canvas, 0, 0);
 *     }
 * }
 * ```
 */
public open class SrcModeGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkPath fPath
   * ```
   */
  private var fPath: SkPath = TODO("Initialize fPath")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override { this->setBGColor(SK_ColorBLACK); }
   * ```
   */
  public override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("srcmode"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {640, 760}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawContent(SkCanvas* canvas) {
   *         canvas->translate(SkIntToScalar(20), SkIntToScalar(20));
   *
   *         SkPaint paint;
   *         SkFont  font(ToolUtils::DefaultPortableTypeface(), H / 4);
   *         paint.setColor(0x80F60000);
   *
   *         const Proc procs[] = {
   *             draw_hair, draw_thick, draw_rect, draw_oval, draw_text
   *         };
   *
   *         const SkBlendMode modes[] = {
   *             SkBlendMode::kSrcOver, SkBlendMode::kSrc, SkBlendMode::kClear
   *         };
   *
   *         const PaintProc paintProcs[] = {
   *             identity_paintproc, gradient_paintproc
   *         };
   *
   *         for (int aa = 0; aa <= 1; ++aa) {
   *             paint.setAntiAlias(SkToBool(aa));
   *             font.setEdging(SkToBool(aa) ? SkFont::Edging::kAntiAlias : SkFont::Edging::kAlias);
   *             canvas->save();
   *             for (size_t i = 0; i < std::size(paintProcs); ++i) {
   *                 paintProcs[i](&paint);
   *                 for (size_t x = 0; x < std::size(modes); ++x) {
   *                     paint.setBlendMode(modes[x]);
   *                     canvas->save();
   *                     for (size_t y = 0; y < std::size(procs); ++y) {
   *                         procs[y](canvas, paint, font);
   *                         canvas->translate(0, H * 5 / 4);
   *                     }
   *                     canvas->restore();
   *                     canvas->translate(W * 5 / 4, 0);
   *                 }
   *             }
   *             canvas->restore();
   *             canvas->translate(0, (H * 5 / 4) * std::size(procs));
   *         }
   *     }
   * ```
   */
  public override fun drawContent(canvas: SkCanvas?) {
    TODO("Implement drawContent")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         auto surf(compat_surface(canvas, this->getISize()));
   *         surf->getCanvas()->drawColor(SK_ColorWHITE);
   *         this->drawContent(surf->getCanvas());
   *         surf->draw(canvas, 0, 0);
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkSurface> compat_surface(SkCanvas* canvas, const SkISize& size) {
     *         SkImageInfo info = SkImageInfo::MakeN32Premul(size);
     *         sk_sp<SkSurface> surface = canvas->makeSurface(info);
     *         if (nullptr == surface) {
     *             // picture canvas will return null, so fall-back to raster
     *             surface = SkSurfaces::Raster(info);
     *         }
     *         return surface;
     *     }
     * ```
     */
    private fun compatSurface(canvas: SkCanvas?, size: SkISize): SkSp<SkSurface> {
      TODO("Implement compatSurface")
    }
  }
}
