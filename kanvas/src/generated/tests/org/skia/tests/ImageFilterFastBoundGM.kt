package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.math.SkIPoint
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ImageFilterFastBoundGM : public GM {
 * public:
 *     ImageFilterFastBoundGM() {
 *         this->setBGColor(0xFFCCCCCC);
 *     }
 *
 * protected:
 *     inline static constexpr int kTileWidth = 100;
 *     inline static constexpr int kTileHeight = 100;
 *     inline static constexpr int kNumVertTiles = 7;
 *     inline static constexpr int kNumXtraCols = 2;
 *
 *     SkString getName() const override { return SkString("filterfastbounds"); }
 *
 *     SkISize getISize() override {
 *         return SkISize::Make((std::size(gDrawMthds) + kNumXtraCols) * kTileWidth,
 *                              kNumVertTiles * kTileHeight);
 *     }
 *
 *     static void draw_geom_with_paint(drawMth draw, const SkIPoint& off,
 *                                      SkCanvas* canvas, const SkPaint& p) {
 *         SkPaint redStroked;
 *         redStroked.setColor(SK_ColorRED);
 *         redStroked.setStyle(SkPaint::kStroke_Style);
 *
 *         SkPaint blueStroked;
 *         blueStroked.setColor(SK_ColorBLUE);
 *         blueStroked.setStyle(SkPaint::kStroke_Style);
 *
 *         const SkRect r = SkRect::MakeLTRB(20, 20, 30, 30);
 *         SkRect storage;
 *
 *         canvas->save();
 *             canvas->translate(SkIntToScalar(off.fX), SkIntToScalar(off.fY));
 *             canvas->scale(1.5f, 1.5f);
 *
 *             const SkRect& fastBound = p.computeFastBounds(r, &storage);
 *
 *             canvas->save();
 *                 canvas->clipRect(fastBound);
 *                 (*draw)(canvas, r, p);
 *             canvas->restore();
 *
 *             canvas->drawRect(r, redStroked);
 *             canvas->drawRect(fastBound, blueStroked);
 *         canvas->restore();
 *     }
 *
 *     static void draw_savelayer_with_paint(const SkIPoint& off,
 *                                           SkCanvas* canvas,
 *                                           const SkPaint& p) {
 *         SkPaint redStroked;
 *         redStroked.setColor(SK_ColorRED);
 *         redStroked.setStyle(SkPaint::kStroke_Style);
 *
 *         SkPaint blueStroked;
 *         blueStroked.setColor(SK_ColorBLUE);
 *         blueStroked.setStyle(SkPaint::kStroke_Style);
 *
 *         const SkRect bounds = SkRect::MakeWH(10, 10);
 *         SkRect storage;
 *
 *         canvas->save();
 *             canvas->translate(30, 30);
 *             canvas->translate(SkIntToScalar(off.fX), SkIntToScalar(off.fY));
 *             canvas->scale(1.5f, 1.5f);
 *
 *             const SkRect& fastBound = p.computeFastBounds(bounds, &storage);
 *
 *             canvas->saveLayer(&fastBound, &p);
 *             canvas->restore();
 *
 *             canvas->drawRect(bounds, redStroked);
 *             canvas->drawRect(fastBound, blueStroked);
 *         canvas->restore();
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *
 *         SkPaint blackFill;
 *
 *         //-----------
 *         // Normal paints (no source)
 *         TArray<SkPaint> paints;
 *         create_paints(&paints, nullptr);
 *
 *         //-----------
 *         // Paints with a PictureImageFilter as a source
 *         sk_sp<SkPicture> pic;
 *
 *         {
 *             SkPictureRecorder rec;
 *
 *             SkCanvas* c = rec.beginRecording(10, 10);
 *             c->drawRect(SkRect::MakeWH(10, 10), blackFill);
 *             pic = rec.finishRecordingAsPicture();
 *         }
 *
 *         TArray<SkPaint> pifPaints;
 *         create_paints(&pifPaints, SkImageFilters::Picture(pic));
 *
 *         //-----------
 *         // Paints with a SkImageSource as a source
 *
 *         auto surface(SkSurfaces::Raster(SkImageInfo::MakeN32Premul(10, 10)));
 *         {
 *             SkPaint p;
 *             SkCanvas* temp = surface->getCanvas();
 *             temp->clear(SK_ColorYELLOW);
 *             p.setColor(SK_ColorBLUE);
 *             temp->drawRect(SkRect::MakeLTRB(5, 5, 10, 10), p);
 *             p.setColor(SK_ColorGREEN);
 *             temp->drawRect(SkRect::MakeLTRB(5, 0, 10, 5), p);
 *         }
 *
 *         sk_sp<SkImage> image(surface->makeImageSnapshot());
 *         sk_sp<SkImageFilter> imageSource(SkImageFilters::Image(std::move(image),
 *                                                                SkFilterMode::kLinear));
 *         TArray<SkPaint> bmsPaints;
 *         create_paints(&bmsPaints, std::move(imageSource));
 *
 *         //-----------
 *         SkASSERT(paints.size() == kNumVertTiles);
 *         SkASSERT(paints.size() == pifPaints.size());
 *         SkASSERT(paints.size() == bmsPaints.size());
 *
 *         // horizontal separators
 *         for (int i = 1; i < paints.size(); ++i) {
 *             canvas->drawLine(0,
 *                              i*SkIntToScalar(kTileHeight),
 *                              SkIntToScalar((std::size(gDrawMthds) + kNumXtraCols)*kTileWidth),
 *                              i*SkIntToScalar(kTileHeight),
 *                              blackFill);
 *         }
 *         // vertical separators
 *         for (int i = 0; i < (int)std::size(gDrawMthds) + kNumXtraCols; ++i) {
 *             canvas->drawLine(SkIntToScalar(i * kTileWidth),
 *                              0,
 *                              SkIntToScalar(i * kTileWidth),
 *                              SkIntToScalar(paints.size() * kTileWidth),
 *                              blackFill);
 *         }
 *
 *         // A column of saveLayers with PictureImageFilters
 *         for (int i = 0; i < pifPaints.size(); ++i) {
 *             draw_savelayer_with_paint(SkIPoint::Make(0, i*kTileHeight),
 *                                       canvas, pifPaints[i]);
 *         }
 *
 *         // A column of saveLayers with BitmapSources
 *         for (int i = 0; i < pifPaints.size(); ++i) {
 *             draw_savelayer_with_paint(SkIPoint::Make(kTileWidth, i*kTileHeight),
 *                                       canvas, bmsPaints[i]);
 *         }
 *
 *         // Multiple columns with different geometry
 *         for (int i = 0; i < (int)std::size(gDrawMthds); ++i) {
 *             for (int j = 0; j < paints.size(); ++j) {
 *                 draw_geom_with_paint(*gDrawMthds[i],
 *                                      SkIPoint::Make((i+kNumXtraCols) * kTileWidth, j*kTileHeight),
 *                                      canvas, paints[j]);
 *             }
 *         }
 *
 *     }
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class ImageFilterFastBoundGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("filterfastbounds"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override {
   *         return SkISize::Make((std::size(gDrawMthds) + kNumXtraCols) * kTileWidth,
   *                              kNumVertTiles * kTileHeight);
   *     }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *
   *         SkPaint blackFill;
   *
   *         //-----------
   *         // Normal paints (no source)
   *         TArray<SkPaint> paints;
   *         create_paints(&paints, nullptr);
   *
   *         //-----------
   *         // Paints with a PictureImageFilter as a source
   *         sk_sp<SkPicture> pic;
   *
   *         {
   *             SkPictureRecorder rec;
   *
   *             SkCanvas* c = rec.beginRecording(10, 10);
   *             c->drawRect(SkRect::MakeWH(10, 10), blackFill);
   *             pic = rec.finishRecordingAsPicture();
   *         }
   *
   *         TArray<SkPaint> pifPaints;
   *         create_paints(&pifPaints, SkImageFilters::Picture(pic));
   *
   *         //-----------
   *         // Paints with a SkImageSource as a source
   *
   *         auto surface(SkSurfaces::Raster(SkImageInfo::MakeN32Premul(10, 10)));
   *         {
   *             SkPaint p;
   *             SkCanvas* temp = surface->getCanvas();
   *             temp->clear(SK_ColorYELLOW);
   *             p.setColor(SK_ColorBLUE);
   *             temp->drawRect(SkRect::MakeLTRB(5, 5, 10, 10), p);
   *             p.setColor(SK_ColorGREEN);
   *             temp->drawRect(SkRect::MakeLTRB(5, 0, 10, 5), p);
   *         }
   *
   *         sk_sp<SkImage> image(surface->makeImageSnapshot());
   *         sk_sp<SkImageFilter> imageSource(SkImageFilters::Image(std::move(image),
   *                                                                SkFilterMode::kLinear));
   *         TArray<SkPaint> bmsPaints;
   *         create_paints(&bmsPaints, std::move(imageSource));
   *
   *         //-----------
   *         SkASSERT(paints.size() == kNumVertTiles);
   *         SkASSERT(paints.size() == pifPaints.size());
   *         SkASSERT(paints.size() == bmsPaints.size());
   *
   *         // horizontal separators
   *         for (int i = 1; i < paints.size(); ++i) {
   *             canvas->drawLine(0,
   *                              i*SkIntToScalar(kTileHeight),
   *                              SkIntToScalar((std::size(gDrawMthds) + kNumXtraCols)*kTileWidth),
   *                              i*SkIntToScalar(kTileHeight),
   *                              blackFill);
   *         }
   *         // vertical separators
   *         for (int i = 0; i < (int)std::size(gDrawMthds) + kNumXtraCols; ++i) {
   *             canvas->drawLine(SkIntToScalar(i * kTileWidth),
   *                              0,
   *                              SkIntToScalar(i * kTileWidth),
   *                              SkIntToScalar(paints.size() * kTileWidth),
   *                              blackFill);
   *         }
   *
   *         // A column of saveLayers with PictureImageFilters
   *         for (int i = 0; i < pifPaints.size(); ++i) {
   *             draw_savelayer_with_paint(SkIPoint::Make(0, i*kTileHeight),
   *                                       canvas, pifPaints[i]);
   *         }
   *
   *         // A column of saveLayers with BitmapSources
   *         for (int i = 0; i < pifPaints.size(); ++i) {
   *             draw_savelayer_with_paint(SkIPoint::Make(kTileWidth, i*kTileHeight),
   *                                       canvas, bmsPaints[i]);
   *         }
   *
   *         // Multiple columns with different geometry
   *         for (int i = 0; i < (int)std::size(gDrawMthds); ++i) {
   *             for (int j = 0; j < paints.size(); ++j) {
   *                 draw_geom_with_paint(*gDrawMthds[i],
   *                                      SkIPoint::Make((i+kNumXtraCols) * kTileWidth, j*kTileHeight),
   *                                      canvas, paints[j]);
   *             }
   *         }
   *
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    protected val kTileWidth: Int = TODO("Initialize kTileWidth")

    protected val kTileHeight: Int = TODO("Initialize kTileHeight")

    protected val kNumVertTiles: Int = TODO("Initialize kNumVertTiles")

    protected val kNumXtraCols: Int = TODO("Initialize kNumXtraCols")

    /**
     * C++ original:
     * ```cpp
     * static void draw_geom_with_paint(drawMth draw, const SkIPoint& off,
     *                                      SkCanvas* canvas, const SkPaint& p) {
     *         SkPaint redStroked;
     *         redStroked.setColor(SK_ColorRED);
     *         redStroked.setStyle(SkPaint::kStroke_Style);
     *
     *         SkPaint blueStroked;
     *         blueStroked.setColor(SK_ColorBLUE);
     *         blueStroked.setStyle(SkPaint::kStroke_Style);
     *
     *         const SkRect r = SkRect::MakeLTRB(20, 20, 30, 30);
     *         SkRect storage;
     *
     *         canvas->save();
     *             canvas->translate(SkIntToScalar(off.fX), SkIntToScalar(off.fY));
     *             canvas->scale(1.5f, 1.5f);
     *
     *             const SkRect& fastBound = p.computeFastBounds(r, &storage);
     *
     *             canvas->save();
     *                 canvas->clipRect(fastBound);
     *                 (*draw)(canvas, r, p);
     *             canvas->restore();
     *
     *             canvas->drawRect(r, redStroked);
     *             canvas->drawRect(fastBound, blueStroked);
     *         canvas->restore();
     *     }
     * ```
     */
    protected fun drawGeomWithPaint(
      draw: DrawMth,
      off: SkIPoint,
      canvas: SkCanvas?,
      p: SkPaint,
    ) {
      TODO("Implement drawGeomWithPaint")
    }

    /**
     * C++ original:
     * ```cpp
     * static void draw_savelayer_with_paint(const SkIPoint& off,
     *                                           SkCanvas* canvas,
     *                                           const SkPaint& p) {
     *         SkPaint redStroked;
     *         redStroked.setColor(SK_ColorRED);
     *         redStroked.setStyle(SkPaint::kStroke_Style);
     *
     *         SkPaint blueStroked;
     *         blueStroked.setColor(SK_ColorBLUE);
     *         blueStroked.setStyle(SkPaint::kStroke_Style);
     *
     *         const SkRect bounds = SkRect::MakeWH(10, 10);
     *         SkRect storage;
     *
     *         canvas->save();
     *             canvas->translate(30, 30);
     *             canvas->translate(SkIntToScalar(off.fX), SkIntToScalar(off.fY));
     *             canvas->scale(1.5f, 1.5f);
     *
     *             const SkRect& fastBound = p.computeFastBounds(bounds, &storage);
     *
     *             canvas->saveLayer(&fastBound, &p);
     *             canvas->restore();
     *
     *             canvas->drawRect(bounds, redStroked);
     *             canvas->drawRect(fastBound, blueStroked);
     *         canvas->restore();
     *     }
     * ```
     */
    protected fun drawSavelayerWithPaint(
      off: SkIPoint,
      canvas: SkCanvas?,
      p: SkPaint,
    ) {
      TODO("Implement drawSavelayerWithPaint")
    }
  }
}
