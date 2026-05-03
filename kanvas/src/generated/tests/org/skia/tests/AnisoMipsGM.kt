package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SkColor
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class AnisoMipsGM : public GM {
 * public:
 *     AnisoMipsGM() = default;
 *
 * protected:
 *     SkString getName() const override { return SkString("anisomips"); }
 *
 *     SkISize getISize() override { return SkISize::Make(520, 260); }
 *
 *     sk_sp<SkImage> updateImage(SkSurface* surf, SkColor color) {
 *         surf->getCanvas()->clear(color);
 *         SkPaint paint;
 *         paint.setColor(~color | 0xFF000000);
 *         surf->getCanvas()->drawRect(SkRect::MakeLTRB(surf->width() *2/5.f,
 *                                                      surf->height()*2/5.f,
 *                                                      surf->width() *3/5.f,
 *                                                      surf->height()*3/5.f),
 *                                     paint);
 *         return surf->makeImageSnapshot()->withDefaultMipmaps();
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         auto ct = canvas->imageInfo().colorType() == kUnknown_SkColorType
 *                           ? kRGBA_8888_SkColorType
 *                           : canvas->imageInfo().colorType();
 *         auto ii = SkImageInfo::Make(kImageSize,
 *                                     kImageSize,
 *                                     ct,
 *                                     kPremul_SkAlphaType,
 *                                     canvas->imageInfo().refColorSpace());
 *         // In GPU mode we want a surface that is created with mipmaps to ensure that we exercise the
 *         // case where the SkSurface and SkImage share a texture. If the surface texture isn't
 *         // created with MIPs then asking for a mipmapped image will cause a copy to a mipped
 *         // texture.
 *         sk_sp<SkSurface> surface;
 * #if defined(SK_GANESH)
 *         if (auto rc = canvas->recordingContext()) {
 *             surface = SkSurfaces::RenderTarget(rc,
 *                                                skgpu::Budgeted::kYes,
 *                                                ii,
 *                                                /* sampleCount= */ 1,
 *                                                kTopLeft_GrSurfaceOrigin,
 *                                                /*surfaceProps=*/nullptr,
 *                                                /*shouldCreateWithMips=*/true);
 *             if (!surface) {
 *                 // We could be in an abandoned context situation.
 *                 return;
 *             }
 *         } else
 * #endif
 *         {
 *             surface = canvas->makeSurface(ii);
 *             if (!surface) {  // could be a recording canvas.
 *                 surface = SkSurfaces::Raster(ii);
 *             }
 *         }
 *
 *         static constexpr float kScales[] = {1.f, 0.5f, 0.25f, 0.125f};
 *         SkColor kColors[] = {0xFFF0F0F0, SK_ColorBLUE, SK_ColorGREEN, SK_ColorRED};
 *         static const SkSamplingOptions kSampling = SkSamplingOptions::Aniso(16);
 *
 *         for (bool shader : {false, true}) {
 *             int c = 0;
 *             canvas->save();
 *             for (float sy : kScales) {
 *                 canvas->save();
 *                 for (float sx : kScales) {
 *                     canvas->save();
 *                     canvas->scale(sx, sy);
 *                     auto image = this->updateImage(surface.get(), kColors[c]);
 *                     if (shader) {
 *                         SkPaint paint;
 *                         paint.setShader(image->makeShader(kSampling));
 *                         canvas->drawRect(SkRect::Make(image->dimensions()), paint);
 *                     } else {
 *                         canvas->drawImage(image, 0, 0, kSampling);
 *                     }
 *                     canvas->restore();
 *                     canvas->translate(ii.width() * sx + kPad, 0);
 *                     c = (c + 1) % std::size(kColors);
 *                 }
 *                 canvas->restore();
 *                 canvas->translate(0, ii.width() * sy + kPad);
 *             }
 *             canvas->restore();
 *             for (float sx : kScales) {
 *                 canvas->translate(ii.width() * sx + kPad, 0);
 *             }
 *         }
 *     }
 *
 * private:
 *     inline static constexpr int kImageSize = 128;
 *     inline static constexpr int kPad = 5;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class AnisoMipsGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("anisomips"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(520, 260); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> updateImage(SkSurface* surf, SkColor color) {
   *         surf->getCanvas()->clear(color);
   *         SkPaint paint;
   *         paint.setColor(~color | 0xFF000000);
   *         surf->getCanvas()->drawRect(SkRect::MakeLTRB(surf->width() *2/5.f,
   *                                                      surf->height()*2/5.f,
   *                                                      surf->width() *3/5.f,
   *                                                      surf->height()*3/5.f),
   *                                     paint);
   *         return surf->makeImageSnapshot()->withDefaultMipmaps();
   *     }
   * ```
   */
  protected fun updateImage(surf: SkSurface?, color: SkColor): SkSp<SkImage> {
    TODO("Implement updateImage")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         auto ct = canvas->imageInfo().colorType() == kUnknown_SkColorType
   *                           ? kRGBA_8888_SkColorType
   *                           : canvas->imageInfo().colorType();
   *         auto ii = SkImageInfo::Make(kImageSize,
   *                                     kImageSize,
   *                                     ct,
   *                                     kPremul_SkAlphaType,
   *                                     canvas->imageInfo().refColorSpace());
   *         // In GPU mode we want a surface that is created with mipmaps to ensure that we exercise the
   *         // case where the SkSurface and SkImage share a texture. If the surface texture isn't
   *         // created with MIPs then asking for a mipmapped image will cause a copy to a mipped
   *         // texture.
   *         sk_sp<SkSurface> surface;
   * #if defined(SK_GANESH)
   *         if (auto rc = canvas->recordingContext()) {
   *             surface = SkSurfaces::RenderTarget(rc,
   *                                                skgpu::Budgeted::kYes,
   *                                                ii,
   *                                                /* sampleCount= */ 1,
   *                                                kTopLeft_GrSurfaceOrigin,
   *                                                /*surfaceProps=*/nullptr,
   *                                                /*shouldCreateWithMips=*/true);
   *             if (!surface) {
   *                 // We could be in an abandoned context situation.
   *                 return;
   *             }
   *         } else
   * #endif
   *         {
   *             surface = canvas->makeSurface(ii);
   *             if (!surface) {  // could be a recording canvas.
   *                 surface = SkSurfaces::Raster(ii);
   *             }
   *         }
   *
   *         static constexpr float kScales[] = {1.f, 0.5f, 0.25f, 0.125f};
   *         SkColor kColors[] = {0xFFF0F0F0, SK_ColorBLUE, SK_ColorGREEN, SK_ColorRED};
   *         static const SkSamplingOptions kSampling = SkSamplingOptions::Aniso(16);
   *
   *         for (bool shader : {false, true}) {
   *             int c = 0;
   *             canvas->save();
   *             for (float sy : kScales) {
   *                 canvas->save();
   *                 for (float sx : kScales) {
   *                     canvas->save();
   *                     canvas->scale(sx, sy);
   *                     auto image = this->updateImage(surface.get(), kColors[c]);
   *                     if (shader) {
   *                         SkPaint paint;
   *                         paint.setShader(image->makeShader(kSampling));
   *                         canvas->drawRect(SkRect::Make(image->dimensions()), paint);
   *                     } else {
   *                         canvas->drawImage(image, 0, 0, kSampling);
   *                     }
   *                     canvas->restore();
   *                     canvas->translate(ii.width() * sx + kPad, 0);
   *                     c = (c + 1) % std::size(kColors);
   *                 }
   *                 canvas->restore();
   *                 canvas->translate(0, ii.width() * sy + kPad);
   *             }
   *             canvas->restore();
   *             for (float sx : kScales) {
   *                 canvas->translate(ii.width() * sx + kPad, 0);
   *             }
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    private val kImageSize: Int = TODO("Initialize kImageSize")

    private val kPad: Int = TODO("Initialize kPad")
  }
}
