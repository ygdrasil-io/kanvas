package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class ImageBlurTiledGM : public GM {
 * public:
 *     ImageBlurTiledGM(SkScalar sigmaX, SkScalar sigmaY)
 *         : fSigmaX(sigmaX), fSigmaY(sigmaY) {
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("imageblurtiled"); }
 *
 *     SkISize getISize() override { return SkISize::Make(WIDTH, HEIGHT); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint;
 *         paint.setImageFilter(SkImageFilters::Blur(fSigmaX, fSigmaY, nullptr));
 *         const SkScalar tileSize = SkIntToScalar(128);
 *         SkRect bounds = canvas->getLocalClipBounds();
 *         for (SkScalar y = bounds.top(); y < bounds.bottom(); y += tileSize) {
 *             for (SkScalar x = bounds.left(); x < bounds.right(); x += tileSize) {
 *                 canvas->save();
 *                 canvas->clipRect(SkRect::MakeXYWH(x, y, tileSize, tileSize));
 *                 canvas->saveLayer(nullptr, &paint);
 *                 const char* str[] = {
 *                     "The quick",
 *                     "brown fox",
 *                     "jumped over",
 *                     "the lazy dog.",
 *                 };
 *                 SkFont font(ToolUtils::DefaultPortableTypeface(), 100);
 *                 int posY = 0;
 *                 for (unsigned i = 0; i < std::size(str); i++) {
 *                     posY += 100;
 *                     canvas->drawString(str[i], 0, SkIntToScalar(posY), font, SkPaint());
 *                 }
 *                 canvas->restore();
 *                 canvas->restore();
 *             }
 *         }
 *     }
 *
 * private:
 *     SkScalar fSigmaX;
 *     SkScalar fSigmaY;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class ImageBlurTiledGM public constructor(
  sigmaX: SkScalar,
  sigmaY: SkScalar,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkScalar fSigmaX
   * ```
   */
  private var fSigmaX: SkScalar = TODO("Initialize fSigmaX")

  /**
   * C++ original:
   * ```cpp
   * SkScalar fSigmaY
   * ```
   */
  private var fSigmaY: SkScalar = TODO("Initialize fSigmaY")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("imageblurtiled"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(WIDTH, HEIGHT); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPaint paint;
   *         paint.setImageFilter(SkImageFilters::Blur(fSigmaX, fSigmaY, nullptr));
   *         const SkScalar tileSize = SkIntToScalar(128);
   *         SkRect bounds = canvas->getLocalClipBounds();
   *         for (SkScalar y = bounds.top(); y < bounds.bottom(); y += tileSize) {
   *             for (SkScalar x = bounds.left(); x < bounds.right(); x += tileSize) {
   *                 canvas->save();
   *                 canvas->clipRect(SkRect::MakeXYWH(x, y, tileSize, tileSize));
   *                 canvas->saveLayer(nullptr, &paint);
   *                 const char* str[] = {
   *                     "The quick",
   *                     "brown fox",
   *                     "jumped over",
   *                     "the lazy dog.",
   *                 };
   *                 SkFont font(ToolUtils::DefaultPortableTypeface(), 100);
   *                 int posY = 0;
   *                 for (unsigned i = 0; i < std::size(str); i++) {
   *                     posY += 100;
   *                     canvas->drawString(str[i], 0, SkIntToScalar(posY), font, SkPaint());
   *                 }
   *                 canvas->restore();
   *                 canvas->restore();
   *             }
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
