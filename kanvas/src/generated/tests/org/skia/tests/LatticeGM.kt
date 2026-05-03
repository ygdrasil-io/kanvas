package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.gpu.ganesh.GrDirectContext
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class LatticeGM : public skiagm::GM {
 * public:
 *     LatticeGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("lattice"); }
 *
 *     SkISize getISize() override { return SkISize::Make(800, 800); }
 *
 *     void onDrawHelper(GrDirectContext* dContext, SkCanvas* canvas, int padLeft, int padTop,
 *                       int padRight, int padBottom) {
 *         canvas->save();
 *
 *         int xDivs[5];
 *         int yDivs[5];
 *         xDivs[0] = padLeft;
 *         yDivs[0] = padTop;
 *
 *         SkBitmap bitmap;
 *         sk_sp<SkImage> image = make_image(canvas, xDivs + 1, yDivs + 1, padLeft, padTop,
 *                                           padRight, padBottom);
 *         image_to_bitmap(dContext, image.get(), &bitmap);
 *
 *         const SkSize size[] = {
 *             {  50,  50, }, // shrink in both axes
 *             {  50, 200, }, // shrink in X
 *             { 200,  50, }, // shrink in Y
 *             { 200, 200, },
 *         };
 *
 *         canvas->drawImage(image, 10, 10);
 *
 *         SkScalar x = SkIntToScalar(100);
 *         SkScalar y = SkIntToScalar(100);
 *
 *         SkCanvas::Lattice lattice;
 *         lattice.fXCount = 4;
 *         lattice.fXDivs = xDivs + 1;
 *         lattice.fYCount = 4;
 *         lattice.fYDivs = yDivs + 1;
 *         lattice.fRectTypes = nullptr;
 *         lattice.fColors = nullptr;
 *
 *         SkIRect bounds = SkIRect::MakeLTRB(padLeft, padTop,
 *                                            image->width() - padRight, image->height() - padBottom);
 *         lattice.fBounds = (bounds == SkIRect::MakeWH(image->width(), image->height())) ?
 *                 nullptr : &bounds;
 *
 *         for (int iy = 0; iy < 2; ++iy) {
 *             for (int ix = 0; ix < 2; ++ix) {
 *                 int i = ix * 2 + iy;
 *                 SkRect r = SkRect::MakeXYWH(x + ix * 60, y + iy * 60,
 *                                             size[i].width(), size[i].height());
 *                 canvas->drawImageLattice(image.get(), lattice, r);
 *             }
 *         }
 *
 *         // Provide hints about 3 solid color rects. These colors match
 *         // what was already in the bitmap.
 *         int fixedColorX[3] = {2, 4, 1};
 *         int fixedColorY[3] = {1, 1, 2};
 *         SkColor fixedColor[3] = {SK_ColorBLACK, SK_ColorBLACK, SK_ColorBLACK};
 *         const SkImageInfo info = SkImageInfo::Make(1, 1, kBGRA_8888_SkColorType,
 *                                                    kUnpremul_SkAlphaType);
 *         for (int rectNum = 0; rectNum < 3; rectNum++) {
 *             int srcX = xDivs[fixedColorX[rectNum]-1];
 *             int srcY = yDivs[fixedColorY[rectNum]-1];
 *             image->readPixels(dContext, info, &fixedColor[rectNum], 4, srcX, srcY);
 *         }
 *
 *         // Include the degenerate first div.  While normally the first patch is "scalable",
 *         // this will mean that the first non-degenerate patch is "fixed".
 *         lattice.fXCount = 5;
 *         lattice.fXDivs = xDivs;
 *         lattice.fYCount = 5;
 *         lattice.fYDivs = yDivs;
 *
 *         // Let's skip a few rects.
 *         SkCanvas::Lattice::RectType flags[36];
 *         sk_bzero(flags, 36 * sizeof(SkCanvas::Lattice::RectType));
 *         flags[4] = SkCanvas::Lattice::kTransparent;
 *         flags[9] = SkCanvas::Lattice::kTransparent;
 *         flags[12] = SkCanvas::Lattice::kTransparent;
 *         flags[19] = SkCanvas::Lattice::kTransparent;
 *         for (int rectNum = 0; rectNum < 3; rectNum++) {
 *             flags[fixedColorY[rectNum]*6 + fixedColorX[rectNum]]
 *                    = SkCanvas::Lattice::kFixedColor;
 *         }
 *         lattice.fRectTypes = flags;
 *
 *         SkColor colors[36];
 *         sk_bzero(colors, 36 * sizeof(SkColor));
 *         for (int rectNum = 0; rectNum < 3; rectNum++) {
 *             colors[fixedColorY[rectNum]*6 + fixedColorX[rectNum]]
 *                    = fixedColor[rectNum];
 *         }
 *
 *         lattice.fColors = colors;
 *
 *         canvas->translate(400, 0);
 *         for (int iy = 0; iy < 2; ++iy) {
 *             for (int ix = 0; ix < 2; ++ix) {
 *                 int i = ix * 2 + iy;
 *                 SkRect r = SkRect::MakeXYWH(x + ix * 60, y + iy * 60,
 *                                             size[i].width(), size[i].height());
 *                 canvas->drawImageLattice(image.get(), lattice, r);
 *             }
 *         }
 *
 *         canvas->restore();
 *     }
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 * #if defined(SK_GANESH)
 *         auto rContext = canvas->recordingContext();
 *         auto dContext = GrAsDirectContext(rContext);
 *         if (rContext && !dContext) {
 *             *errorMsg = "not supported in ddl";
 *             return DrawResult::kSkip;
 *         }
 * #else
 *         constexpr GrDirectContext* dContext = nullptr;
 * #endif
 *         this->onDrawHelper(dContext, canvas, 0, 0, 0, 0);
 *         canvas->translate(0.0f, 400.0f);
 *         this->onDrawHelper(dContext, canvas, 3, 7, 4, 11);
 *         return DrawResult::kOk;
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class LatticeGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("lattice"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(800, 800); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawHelper(GrDirectContext* dContext, SkCanvas* canvas, int padLeft, int padTop,
   *                       int padRight, int padBottom) {
   *         canvas->save();
   *
   *         int xDivs[5];
   *         int yDivs[5];
   *         xDivs[0] = padLeft;
   *         yDivs[0] = padTop;
   *
   *         SkBitmap bitmap;
   *         sk_sp<SkImage> image = make_image(canvas, xDivs + 1, yDivs + 1, padLeft, padTop,
   *                                           padRight, padBottom);
   *         image_to_bitmap(dContext, image.get(), &bitmap);
   *
   *         const SkSize size[] = {
   *             {  50,  50, }, // shrink in both axes
   *             {  50, 200, }, // shrink in X
   *             { 200,  50, }, // shrink in Y
   *             { 200, 200, },
   *         };
   *
   *         canvas->drawImage(image, 10, 10);
   *
   *         SkScalar x = SkIntToScalar(100);
   *         SkScalar y = SkIntToScalar(100);
   *
   *         SkCanvas::Lattice lattice;
   *         lattice.fXCount = 4;
   *         lattice.fXDivs = xDivs + 1;
   *         lattice.fYCount = 4;
   *         lattice.fYDivs = yDivs + 1;
   *         lattice.fRectTypes = nullptr;
   *         lattice.fColors = nullptr;
   *
   *         SkIRect bounds = SkIRect::MakeLTRB(padLeft, padTop,
   *                                            image->width() - padRight, image->height() - padBottom);
   *         lattice.fBounds = (bounds == SkIRect::MakeWH(image->width(), image->height())) ?
   *                 nullptr : &bounds;
   *
   *         for (int iy = 0; iy < 2; ++iy) {
   *             for (int ix = 0; ix < 2; ++ix) {
   *                 int i = ix * 2 + iy;
   *                 SkRect r = SkRect::MakeXYWH(x + ix * 60, y + iy * 60,
   *                                             size[i].width(), size[i].height());
   *                 canvas->drawImageLattice(image.get(), lattice, r);
   *             }
   *         }
   *
   *         // Provide hints about 3 solid color rects. These colors match
   *         // what was already in the bitmap.
   *         int fixedColorX[3] = {2, 4, 1};
   *         int fixedColorY[3] = {1, 1, 2};
   *         SkColor fixedColor[3] = {SK_ColorBLACK, SK_ColorBLACK, SK_ColorBLACK};
   *         const SkImageInfo info = SkImageInfo::Make(1, 1, kBGRA_8888_SkColorType,
   *                                                    kUnpremul_SkAlphaType);
   *         for (int rectNum = 0; rectNum < 3; rectNum++) {
   *             int srcX = xDivs[fixedColorX[rectNum]-1];
   *             int srcY = yDivs[fixedColorY[rectNum]-1];
   *             image->readPixels(dContext, info, &fixedColor[rectNum], 4, srcX, srcY);
   *         }
   *
   *         // Include the degenerate first div.  While normally the first patch is "scalable",
   *         // this will mean that the first non-degenerate patch is "fixed".
   *         lattice.fXCount = 5;
   *         lattice.fXDivs = xDivs;
   *         lattice.fYCount = 5;
   *         lattice.fYDivs = yDivs;
   *
   *         // Let's skip a few rects.
   *         SkCanvas::Lattice::RectType flags[36];
   *         sk_bzero(flags, 36 * sizeof(SkCanvas::Lattice::RectType));
   *         flags[4] = SkCanvas::Lattice::kTransparent;
   *         flags[9] = SkCanvas::Lattice::kTransparent;
   *         flags[12] = SkCanvas::Lattice::kTransparent;
   *         flags[19] = SkCanvas::Lattice::kTransparent;
   *         for (int rectNum = 0; rectNum < 3; rectNum++) {
   *             flags[fixedColorY[rectNum]*6 + fixedColorX[rectNum]]
   *                    = SkCanvas::Lattice::kFixedColor;
   *         }
   *         lattice.fRectTypes = flags;
   *
   *         SkColor colors[36];
   *         sk_bzero(colors, 36 * sizeof(SkColor));
   *         for (int rectNum = 0; rectNum < 3; rectNum++) {
   *             colors[fixedColorY[rectNum]*6 + fixedColorX[rectNum]]
   *                    = fixedColor[rectNum];
   *         }
   *
   *         lattice.fColors = colors;
   *
   *         canvas->translate(400, 0);
   *         for (int iy = 0; iy < 2; ++iy) {
   *             for (int ix = 0; ix < 2; ++ix) {
   *                 int i = ix * 2 + iy;
   *                 SkRect r = SkRect::MakeXYWH(x + ix * 60, y + iy * 60,
   *                                             size[i].width(), size[i].height());
   *                 canvas->drawImageLattice(image.get(), lattice, r);
   *             }
   *         }
   *
   *         canvas->restore();
   *     }
   * ```
   */
  protected fun onDrawHelper(
    dContext: GrDirectContext?,
    canvas: SkCanvas?,
    padLeft: Int,
    padTop: Int,
    padRight: Int,
    padBottom: Int,
  ) {
    TODO("Implement onDrawHelper")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
   * #if defined(SK_GANESH)
   *         auto rContext = canvas->recordingContext();
   *         auto dContext = GrAsDirectContext(rContext);
   *         if (rContext && !dContext) {
   *             *errorMsg = "not supported in ddl";
   *             return DrawResult::kSkip;
   *         }
   * #else
   *         constexpr GrDirectContext* dContext = nullptr;
   * #endif
   *         this->onDrawHelper(dContext, canvas, 0, 0, 0, 0);
   *         canvas->translate(0.0f, 400.0f);
   *         this->onDrawHelper(dContext, canvas, 3, 7, 4, 11);
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement onDraw")
  }
}
