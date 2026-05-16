package org.skia.tests

import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkMaskFilter
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class SamplerStressGM : public GM {
 * public:
 *     SamplerStressGM()
 *     : fTextureCreated(false)
 *     , fMaskFilter(nullptr) {
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("gpusamplerstress"); }
 *
 *     SkISize getISize() override { return SkISize::Make(640, 480); }
 *
 *     /**
 *      * Create a red & green stripes on black texture
 *      */
 *     void createTexture() {
 *         if (fTextureCreated) {
 *             return;
 *         }
 *
 *         constexpr int xSize = 16;
 *         constexpr int ySize = 16;
 *
 *         fTexture.allocN32Pixels(xSize, ySize);
 *         SkPMColor* addr = fTexture.getAddr32(0, 0);
 *
 *         for (int y = 0; y < ySize; ++y) {
 *             for (int x = 0; x < xSize; ++x) {
 *                 addr[y*xSize+x] = SkPreMultiplyColor(SK_ColorBLACK);
 *
 *                 if ((y % 5) == 0) {
 *                     addr[y*xSize+x] = SkPreMultiplyColor(SK_ColorRED);
 *                 }
 *                 if ((x % 7) == 0) {
 *                     addr[y*xSize+x] = SkPreMultiplyColor(SK_ColorGREEN);
 *                 }
 *             }
 *         }
 *
 *         fTextureCreated = true;
 *     }
 *
 *     void createShader() {
 *         if (fShader) {
 *             return;
 *         }
 *
 *         createTexture();
 *
 *         fShader = fTexture.makeShader(SkTileMode::kRepeat, SkTileMode::kRepeat,
 *                                       SkSamplingOptions());
 *     }
 *
 *     void createMaskFilter() {
 *         if (fMaskFilter) {
 *             return;
 *         }
 *
 *         const SkScalar sigma = 1;
 *         fMaskFilter = SkMaskFilter::MakeBlur(kNormal_SkBlurStyle, sigma);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         createShader();
 *         createMaskFilter();
 *
 *         canvas->save();
 *
 *         // draw a letter "M" with a green & red striped texture and a
 *         // stipple mask with a round rect soft clip
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *         paint.setShader(fShader);
 *         paint.setMaskFilter(fMaskFilter);
 *         SkFont font(ToolUtils::DefaultPortableTypeface(), 72);
 *
 *         SkRect temp;
 *         temp.setLTRB(115, 75, 144, 110);
 *
 *         SkPath path = SkPath::RRect(SkRRect::MakeRectXY(temp, 5, 5));
 *
 *         canvas->clipPath(path, true); // AA is on
 *
 *         canvas->drawString("M", 100.0f, 100.0f, font, paint);
 *
 *         canvas->restore();
 *
 *         // Now draw stroked versions of the "M" and the round rect so we can
 *         // see what is going on
 *         SkPaint paint2;
 *         paint2.setColor(SK_ColorBLACK);
 *         paint2.setAntiAlias(true);
 *         paint2.setStyle(SkPaint::kStroke_Style);
 *         paint2.setStrokeWidth(1);
 *         canvas->drawString("M", 100.0f, 100.0f, font, paint2);
 *
 *         paint2.setColor(SK_ColorGRAY);
 *
 *         canvas->drawPath(path, paint2);
 *     }
 *
 * private:
 *     SkBitmap        fTexture;
 *     bool            fTextureCreated;
 *     sk_sp<SkShader> fShader;
 *     sk_sp<SkMaskFilter> fMaskFilter;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class SamplerStressGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkBitmap        fTexture
   * ```
   */
  private var fTexture: SkBitmap = TODO("Initialize fTexture")

  /**
   * C++ original:
   * ```cpp
   * bool            fTextureCreated
   * ```
   */
  private var fTextureCreated: Boolean = TODO("Initialize fTextureCreated")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> fShader
   * ```
   */
  private var fShader: SkSp<SkShader> = TODO("Initialize fShader")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkMaskFilter> fMaskFilter
   * ```
   */
  private var fMaskFilter: SkSp<SkMaskFilter> = TODO("Initialize fMaskFilter")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("gpusamplerstress"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(640, 480); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void createTexture() {
   *         if (fTextureCreated) {
   *             return;
   *         }
   *
   *         constexpr int xSize = 16;
   *         constexpr int ySize = 16;
   *
   *         fTexture.allocN32Pixels(xSize, ySize);
   *         SkPMColor* addr = fTexture.getAddr32(0, 0);
   *
   *         for (int y = 0; y < ySize; ++y) {
   *             for (int x = 0; x < xSize; ++x) {
   *                 addr[y*xSize+x] = SkPreMultiplyColor(SK_ColorBLACK);
   *
   *                 if ((y % 5) == 0) {
   *                     addr[y*xSize+x] = SkPreMultiplyColor(SK_ColorRED);
   *                 }
   *                 if ((x % 7) == 0) {
   *                     addr[y*xSize+x] = SkPreMultiplyColor(SK_ColorGREEN);
   *                 }
   *             }
   *         }
   *
   *         fTextureCreated = true;
   *     }
   * ```
   */
  protected fun createTexture() {
    TODO("Implement createTexture")
  }

  /**
   * C++ original:
   * ```cpp
   * void createShader() {
   *         if (fShader) {
   *             return;
   *         }
   *
   *         createTexture();
   *
   *         fShader = fTexture.makeShader(SkTileMode::kRepeat, SkTileMode::kRepeat,
   *                                       SkSamplingOptions());
   *     }
   * ```
   */
  protected fun createShader() {
    TODO("Implement createShader")
  }

  /**
   * C++ original:
   * ```cpp
   * void createMaskFilter() {
   *         if (fMaskFilter) {
   *             return;
   *         }
   *
   *         const SkScalar sigma = 1;
   *         fMaskFilter = SkMaskFilter::MakeBlur(kNormal_SkBlurStyle, sigma);
   *     }
   * ```
   */
  protected fun createMaskFilter() {
    TODO("Implement createMaskFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         createShader();
   *         createMaskFilter();
   *
   *         canvas->save();
   *
   *         // draw a letter "M" with a green & red striped texture and a
   *         // stipple mask with a round rect soft clip
   *         SkPaint paint;
   *         paint.setAntiAlias(true);
   *         paint.setShader(fShader);
   *         paint.setMaskFilter(fMaskFilter);
   *         SkFont font(ToolUtils::DefaultPortableTypeface(), 72);
   *
   *         SkRect temp;
   *         temp.setLTRB(115, 75, 144, 110);
   *
   *         SkPath path = SkPath::RRect(SkRRect::MakeRectXY(temp, 5, 5));
   *
   *         canvas->clipPath(path, true); // AA is on
   *
   *         canvas->drawString("M", 100.0f, 100.0f, font, paint);
   *
   *         canvas->restore();
   *
   *         // Now draw stroked versions of the "M" and the round rect so we can
   *         // see what is going on
   *         SkPaint paint2;
   *         paint2.setColor(SK_ColorBLACK);
   *         paint2.setAntiAlias(true);
   *         paint2.setStyle(SkPaint::kStroke_Style);
   *         paint2.setStrokeWidth(1);
   *         canvas->drawString("M", 100.0f, 100.0f, font, paint2);
   *
   *         paint2.setColor(SK_ColorGRAY);
   *
   *         canvas->drawPath(path, paint2);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
