package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class LatticeGM2 : public skiagm::GM {
 * public:
 *     LatticeGM2() {}
 *     SkString getName() const override { return SkString("lattice2"); }
 *
 *     SkISize getISize() override { return SkISize::Make(800, 800); }
 *
 *     sk_sp<SkImage> makeImage(SkCanvas* root, int padLeft, int padTop, int padRight, int padBottom) {
 *         const int kSize = 80;
 *         auto surface(make_surface(root, kSize, padLeft, padTop, padRight, padBottom));
 *         SkCanvas* canvas = surface->getCanvas();
 *         SkPaint paint;
 *         paint.setAntiAlias(false);
 *         SkRect r;
 *
 *         //first line
 *         r.setXYWH(0, 0, 4, 1);  //4x1 green rect
 *         paint.setColor(0xFF00FF00);
 *         canvas->drawRect(r, paint);
 *
 *         r.setXYWH(4, 0, 1, 1); //1x1 blue pixel -> draws as rectangle
 *         paint.setColor(0xFF0000FF);
 *         canvas->drawRect(r, paint);
 *
 *         r.setXYWH(5, 0, kSize-5, 1); //the rest of the line is red
 *         paint.setColor(0xFFFF0000);
 *         canvas->drawRect(r, paint);
 *
 *
 *         //second line -> draws as fixed color rectangles
 *         r.setXYWH(0, 1, 4, 1);  //4x1 red rect
 *         paint.setColor(0xFFFF0000);
 *         canvas->drawRect(r, paint);
 *
 *         r.setXYWH(4, 1, 1, 1); //1x1 blue pixel with alpha
 *         paint.setColor(0x880000FF);
 *         canvas->drawRect(r, paint);
 *
 *         r.setXYWH(5, 1, kSize-5, 1); //the rest of the line is green
 *         paint.setColor(0xFF00FF00);
 *         canvas->drawRect(r, paint);
 *
 *
 *         //third line - does not draw, because it is transparent
 *         r.setXYWH(0, 2, 4, kSize-2);  //4x78 green rect
 *         paint.setColor(0xFF00FF00);
 *         canvas->drawRect(r, paint);
 *
 *         r.setXYWH(4, 2, 1, kSize-2); //1x78 red pixel with alpha
 *         paint.setColor(0x88FF0000);
 *         canvas->drawRect(r, paint);
 *
 *         r.setXYWH(5, 2, kSize-5, kSize-2); //the rest of the image is blue
 *         paint.setColor(0xFF0000FF);
 *         canvas->drawRect(r, paint);
 *
 *         return surface->makeImageSnapshot();
 *     }
 *
 *     void onDrawHelper(SkCanvas* canvas, int padLeft, int padTop, int padRight, int padBottom,
 *                       SkPaint& paint) {
 *         int xDivs[2] = {4, 5};
 *         int yDivs[2] = {1, 2};
 *
 *         canvas->save();
 *
 *         sk_sp<SkImage> image = makeImage(canvas, padLeft, padTop, padRight, padBottom);
 *
 *         canvas->drawImage(image, 10, 10);
 *
 *         SkCanvas::Lattice lattice;
 *         lattice.fXCount = 2;
 *         lattice.fXDivs = xDivs;
 *         lattice.fYCount = 2;
 *         lattice.fYDivs = yDivs;
 *         lattice.fBounds = nullptr;
 *
 *         SkCanvas::Lattice::RectType flags[9];
 *         sk_bzero(flags, 9 * sizeof(SkCanvas::Lattice::RectType));
 *         flags[3] = SkCanvas::Lattice::kFixedColor;
 *         flags[4] = SkCanvas::Lattice::kFixedColor;
 *         flags[5] = SkCanvas::Lattice::kFixedColor;
 *
 *         flags[6] = SkCanvas::Lattice::kTransparent;
 *         flags[7] = SkCanvas::Lattice::kTransparent;
 *         flags[8] = SkCanvas::Lattice::kTransparent;
 *         lattice.fRectTypes = flags;
 *
 *         SkColor colors[9] = {SK_ColorBLACK, SK_ColorBLACK, SK_ColorBLACK,
 *                              0xFFFF0000, 0x880000FF, 0xFF00FF00,
 *                              SK_ColorBLACK, SK_ColorBLACK, SK_ColorBLACK};
 *         lattice.fColors = colors;
 *         paint.setColor(0xFFFFFFFF);
 *         canvas->drawImageLattice(image.get(), lattice,
 *                                  SkRect::MakeXYWH(100, 100, 200, 200),
 *                                  SkFilterMode::kNearest, &paint);
 *
 *         //draw the same content with alpha
 *         canvas->translate(400, 0);
 *         paint.setColor(0x80000FFF);
 *         canvas->drawImageLattice(image.get(), lattice,
 *                                  SkRect::MakeXYWH(100, 100, 200, 200),
 *                                  SkFilterMode::kNearest, &paint);
 *
 *         canvas->restore();
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *
 *         //draw a rectangle in the background with transparent pixels
 *         SkPaint paint;
 *         paint.setColor(0x7F123456);
 *         paint.setBlendMode(SkBlendMode::kSrc);
 *         canvas->drawRect( SkRect::MakeXYWH(300, 0, 300, 800), paint);
 *
 *         //draw image lattice with kSrcOver blending
 *         paint.setBlendMode(SkBlendMode::kSrcOver);
 *         this->onDrawHelper(canvas, 0, 0, 0, 0, paint);
 *
 *         //draw image lattice with kSrcATop blending
 *         canvas->translate(0.0f, 400.0f);
 *         paint.setBlendMode(SkBlendMode::kSrcATop);
 *         this->onDrawHelper(canvas, 0, 0, 0, 0, paint);
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class LatticeGM2 public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("lattice2"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(800, 800); }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> makeImage(SkCanvas* root, int padLeft, int padTop, int padRight, int padBottom) {
   *         const int kSize = 80;
   *         auto surface(make_surface(root, kSize, padLeft, padTop, padRight, padBottom));
   *         SkCanvas* canvas = surface->getCanvas();
   *         SkPaint paint;
   *         paint.setAntiAlias(false);
   *         SkRect r;
   *
   *         //first line
   *         r.setXYWH(0, 0, 4, 1);  //4x1 green rect
   *         paint.setColor(0xFF00FF00);
   *         canvas->drawRect(r, paint);
   *
   *         r.setXYWH(4, 0, 1, 1); //1x1 blue pixel -> draws as rectangle
   *         paint.setColor(0xFF0000FF);
   *         canvas->drawRect(r, paint);
   *
   *         r.setXYWH(5, 0, kSize-5, 1); //the rest of the line is red
   *         paint.setColor(0xFFFF0000);
   *         canvas->drawRect(r, paint);
   *
   *
   *         //second line -> draws as fixed color rectangles
   *         r.setXYWH(0, 1, 4, 1);  //4x1 red rect
   *         paint.setColor(0xFFFF0000);
   *         canvas->drawRect(r, paint);
   *
   *         r.setXYWH(4, 1, 1, 1); //1x1 blue pixel with alpha
   *         paint.setColor(0x880000FF);
   *         canvas->drawRect(r, paint);
   *
   *         r.setXYWH(5, 1, kSize-5, 1); //the rest of the line is green
   *         paint.setColor(0xFF00FF00);
   *         canvas->drawRect(r, paint);
   *
   *
   *         //third line - does not draw, because it is transparent
   *         r.setXYWH(0, 2, 4, kSize-2);  //4x78 green rect
   *         paint.setColor(0xFF00FF00);
   *         canvas->drawRect(r, paint);
   *
   *         r.setXYWH(4, 2, 1, kSize-2); //1x78 red pixel with alpha
   *         paint.setColor(0x88FF0000);
   *         canvas->drawRect(r, paint);
   *
   *         r.setXYWH(5, 2, kSize-5, kSize-2); //the rest of the image is blue
   *         paint.setColor(0xFF0000FF);
   *         canvas->drawRect(r, paint);
   *
   *         return surface->makeImageSnapshot();
   *     }
   * ```
   */
  public fun makeImage(
    root: SkCanvas?,
    padLeft: Int,
    padTop: Int,
    padRight: Int,
    padBottom: Int,
  ): SkSp<SkImage> {
    TODO("Implement makeImage")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawHelper(SkCanvas* canvas, int padLeft, int padTop, int padRight, int padBottom,
   *                       SkPaint& paint) {
   *         int xDivs[2] = {4, 5};
   *         int yDivs[2] = {1, 2};
   *
   *         canvas->save();
   *
   *         sk_sp<SkImage> image = makeImage(canvas, padLeft, padTop, padRight, padBottom);
   *
   *         canvas->drawImage(image, 10, 10);
   *
   *         SkCanvas::Lattice lattice;
   *         lattice.fXCount = 2;
   *         lattice.fXDivs = xDivs;
   *         lattice.fYCount = 2;
   *         lattice.fYDivs = yDivs;
   *         lattice.fBounds = nullptr;
   *
   *         SkCanvas::Lattice::RectType flags[9];
   *         sk_bzero(flags, 9 * sizeof(SkCanvas::Lattice::RectType));
   *         flags[3] = SkCanvas::Lattice::kFixedColor;
   *         flags[4] = SkCanvas::Lattice::kFixedColor;
   *         flags[5] = SkCanvas::Lattice::kFixedColor;
   *
   *         flags[6] = SkCanvas::Lattice::kTransparent;
   *         flags[7] = SkCanvas::Lattice::kTransparent;
   *         flags[8] = SkCanvas::Lattice::kTransparent;
   *         lattice.fRectTypes = flags;
   *
   *         SkColor colors[9] = {SK_ColorBLACK, SK_ColorBLACK, SK_ColorBLACK,
   *                              0xFFFF0000, 0x880000FF, 0xFF00FF00,
   *                              SK_ColorBLACK, SK_ColorBLACK, SK_ColorBLACK};
   *         lattice.fColors = colors;
   *         paint.setColor(0xFFFFFFFF);
   *         canvas->drawImageLattice(image.get(), lattice,
   *                                  SkRect::MakeXYWH(100, 100, 200, 200),
   *                                  SkFilterMode::kNearest, &paint);
   *
   *         //draw the same content with alpha
   *         canvas->translate(400, 0);
   *         paint.setColor(0x80000FFF);
   *         canvas->drawImageLattice(image.get(), lattice,
   *                                  SkRect::MakeXYWH(100, 100, 200, 200),
   *                                  SkFilterMode::kNearest, &paint);
   *
   *         canvas->restore();
   *     }
   * ```
   */
  public fun onDrawHelper(
    canvas: SkCanvas?,
    padLeft: Int,
    padTop: Int,
    padRight: Int,
    padBottom: Int,
    paint: SkPaint,
  ) {
    TODO("Implement onDrawHelper")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *
   *         //draw a rectangle in the background with transparent pixels
   *         SkPaint paint;
   *         paint.setColor(0x7F123456);
   *         paint.setBlendMode(SkBlendMode::kSrc);
   *         canvas->drawRect( SkRect::MakeXYWH(300, 0, 300, 800), paint);
   *
   *         //draw image lattice with kSrcOver blending
   *         paint.setBlendMode(SkBlendMode::kSrcOver);
   *         this->onDrawHelper(canvas, 0, 0, 0, 0, paint);
   *
   *         //draw image lattice with kSrcATop blending
   *         canvas->translate(0.0f, 400.0f);
   *         paint.setBlendMode(SkBlendMode::kSrcATop);
   *         this->onDrawHelper(canvas, 0, 0, 0, 0, paint);
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
