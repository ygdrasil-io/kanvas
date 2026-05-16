package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ColorMatrixGM : public skiagm::GM {
 * public:
 *     ColorMatrixGM() {
 *         this->setBGColor(0xFF808080);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("colormatrix"); }
 *
 *     SkISize getISize() override { return SkISize::Make(WIDTH, HEIGHT); }
 *
 *     void onOnceBeforeDraw() override {
 *         fSolidImg = CreateSolidBitmap(64, 64);
 *         fTransparentImg = CreateTransparentBitmap(64, 64);
 *     }
 *
 *     static sk_sp<SkImage> CreateSolidBitmap(int width, int height) {
 *         SkBitmap bm;
 *         bm.allocN32Pixels(width, height);
 *         SkCanvas canvas(bm);
 *         canvas.clear(0x0);
 *         for (int y = 0; y < height; ++y) {
 *             for (int x = 0; x < width; ++x) {
 *                 SkPaint paint;
 *                 paint.setColor(SkColorSetARGB(255, x * 255 / width, y * 255 / height, 0));
 *                 canvas.drawRect(SkRect::MakeXYWH(SkIntToScalar(x),
 *                     SkIntToScalar(y), SK_Scalar1, SK_Scalar1), paint);
 *             }
 *         }
 *         return bm.asImage();
 *     }
 *
 *     // creates a bitmap with shades of transparent gray.
 *     static sk_sp<SkImage> CreateTransparentBitmap(int width, int height) {
 *         SkBitmap bm;
 *         bm.allocN32Pixels(width, height);
 *         SkCanvas canvas(bm);
 *         canvas.clear(0x0);
 *
 *         SkPoint pts[] = {{0, 0}, {SkIntToScalar(width), SkIntToScalar(height)}};
 *         const SkColor4f colors[] = {{0,0,0,0}, {1,1,1,1}};
 *         SkPaint paint;
 *         paint.setShader(SkShaders::LinearGradient(pts, {{colors, {}, SkTileMode::kClamp}, {}}));
 *         canvas.drawRect(SkRect::MakeWH(SkIntToScalar(width), SkIntToScalar(height)), paint);
 *         return bm.asImage();
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint;
 *         SkColorMatrix matrix;
 *
 *         paint.setBlendMode(SkBlendMode::kSrc);
 *         const SkImage* bmps[] = { fSolidImg.get(), fTransparentImg.get() };
 *
 *         for (size_t i = 0; i < std::size(bmps); ++i) {
 *             matrix.setIdentity();
 *             set_color_matrix(&paint, matrix);
 *             canvas->drawImage(bmps[i], 0, 0, SkSamplingOptions(), &paint);
 *
 *             ///////////////////////////////////////////////
 *
 *             matrix.setSaturation(0.0f);
 *             set_color_matrix(&paint, matrix);
 *             canvas->drawImage(bmps[i], 80, 0, SkSamplingOptions(), &paint);
 *
 *             matrix.setSaturation(0.5f);
 *             set_color_matrix(&paint, matrix);
 *             canvas->drawImage(bmps[i], 160, 0, SkSamplingOptions(), &paint);
 *
 *             matrix.setSaturation(1.0f);
 *             set_color_matrix(&paint, matrix);
 *             canvas->drawImage(bmps[i], 240, 0, SkSamplingOptions(), &paint);
 *
 *             matrix.setSaturation(2.0f);
 *             set_color_matrix(&paint, matrix);
 *             canvas->drawImage(bmps[i], 320, 0, SkSamplingOptions(), &paint);
 *
 *             ///////////////////////////////////////////////
 *
 *             // Move red into alpha, set color to white
 *             float data[20] = {
 *                 0,  0, 0, 0, 1,
 *                 0,  0, 0, 0, 1,
 *                 0,  0, 0, 0, 1,
 *                 1, 0, 0, 0, 0,
 *             };
 *
 *             set_array(&paint, data);
 *             canvas->drawImage(bmps[i], 400, 0, SkSamplingOptions(), &paint);
 *             ///////////////////////////////////////////////
 *             canvas->translate(0, 80);
 *         }
 *     }
 *
 * private:
 *     sk_sp<SkImage>   fSolidImg;
 *     sk_sp<SkImage>   fTransparentImg;
 *
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class ColorMatrixGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage>   fSolidImg
   * ```
   */
  private var fSolidImg: SkSp<SkImage> = TODO("Initialize fSolidImg")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage>   fTransparentImg
   * ```
   */
  private var fTransparentImg: SkSp<SkImage> = TODO("Initialize fTransparentImg")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("colormatrix"); }
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
   * void onOnceBeforeDraw() override {
   *         fSolidImg = CreateSolidBitmap(64, 64);
   *         fTransparentImg = CreateTransparentBitmap(64, 64);
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPaint paint;
   *         SkColorMatrix matrix;
   *
   *         paint.setBlendMode(SkBlendMode::kSrc);
   *         const SkImage* bmps[] = { fSolidImg.get(), fTransparentImg.get() };
   *
   *         for (size_t i = 0; i < std::size(bmps); ++i) {
   *             matrix.setIdentity();
   *             set_color_matrix(&paint, matrix);
   *             canvas->drawImage(bmps[i], 0, 0, SkSamplingOptions(), &paint);
   *
   *             ///////////////////////////////////////////////
   *
   *             matrix.setSaturation(0.0f);
   *             set_color_matrix(&paint, matrix);
   *             canvas->drawImage(bmps[i], 80, 0, SkSamplingOptions(), &paint);
   *
   *             matrix.setSaturation(0.5f);
   *             set_color_matrix(&paint, matrix);
   *             canvas->drawImage(bmps[i], 160, 0, SkSamplingOptions(), &paint);
   *
   *             matrix.setSaturation(1.0f);
   *             set_color_matrix(&paint, matrix);
   *             canvas->drawImage(bmps[i], 240, 0, SkSamplingOptions(), &paint);
   *
   *             matrix.setSaturation(2.0f);
   *             set_color_matrix(&paint, matrix);
   *             canvas->drawImage(bmps[i], 320, 0, SkSamplingOptions(), &paint);
   *
   *             ///////////////////////////////////////////////
   *
   *             // Move red into alpha, set color to white
   *             float data[20] = {
   *                 0,  0, 0, 0, 1,
   *                 0,  0, 0, 0, 1,
   *                 0,  0, 0, 0, 1,
   *                 1, 0, 0, 0, 0,
   *             };
   *
   *             set_array(&paint, data);
   *             canvas->drawImage(bmps[i], 400, 0, SkSamplingOptions(), &paint);
   *             ///////////////////////////////////////////////
   *             canvas->translate(0, 80);
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
     * static sk_sp<SkImage> CreateSolidBitmap(int width, int height) {
     *         SkBitmap bm;
     *         bm.allocN32Pixels(width, height);
     *         SkCanvas canvas(bm);
     *         canvas.clear(0x0);
     *         for (int y = 0; y < height; ++y) {
     *             for (int x = 0; x < width; ++x) {
     *                 SkPaint paint;
     *                 paint.setColor(SkColorSetARGB(255, x * 255 / width, y * 255 / height, 0));
     *                 canvas.drawRect(SkRect::MakeXYWH(SkIntToScalar(x),
     *                     SkIntToScalar(y), SK_Scalar1, SK_Scalar1), paint);
     *             }
     *         }
     *         return bm.asImage();
     *     }
     * ```
     */
    protected fun createSolidBitmap(width: Int, height: Int): SkSp<SkImage> {
      TODO("Implement createSolidBitmap")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkImage> CreateTransparentBitmap(int width, int height) {
     *         SkBitmap bm;
     *         bm.allocN32Pixels(width, height);
     *         SkCanvas canvas(bm);
     *         canvas.clear(0x0);
     *
     *         SkPoint pts[] = {{0, 0}, {SkIntToScalar(width), SkIntToScalar(height)}};
     *         const SkColor4f colors[] = {{0,0,0,0}, {1,1,1,1}};
     *         SkPaint paint;
     *         paint.setShader(SkShaders::LinearGradient(pts, {{colors, {}, SkTileMode::kClamp}, {}}));
     *         canvas.drawRect(SkRect::MakeWH(SkIntToScalar(width), SkIntToScalar(height)), paint);
     *         return bm.asImage();
     *     }
     * ```
     */
    protected fun createTransparentBitmap(width: Int, height: Int): SkSp<SkImage> {
      TODO("Implement createTransparentBitmap")
    }
  }
}
