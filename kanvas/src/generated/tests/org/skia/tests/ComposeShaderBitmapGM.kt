package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ComposeShaderBitmapGM : public skiagm::GM {
 * public:
 *     ComposeShaderBitmapGM(bool use_lm) : fUseLocalMatrix(use_lm) {}
 *
 * protected:
 *     SkString getName() const override {
 *         return SkStringPrintf("composeshader_bitmap%s", fUseLocalMatrix ? "_lm" : "");
 *     }
 *
 *     SkISize getISize() override {
 *         return SkISize::Make(7 * (squareLength + 5), 2 * (squareLength + 5));
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         if (!fInitialized) {
 *             draw_color_bm(&fColorBitmap, squareLength);
 *             sk_sp<SkImage> img = SkImages::RasterFromBitmap(fColorBitmap);
 *             img = ToolUtils::MakeTextureImage(canvas, std::move(img));
 *             if (img) {
 *                 fColorBitmapShader = img->makeShader(SkTileMode::kRepeat, SkTileMode::kRepeat,
 *                                                      SkSamplingOptions(), SkMatrix::I());
 *             }
 *             draw_alpha8_bm(&fAlpha8Bitmap, squareLength);
 *             img = SkImages::RasterFromBitmap(fAlpha8Bitmap);
 *             img = ToolUtils::MakeTextureImage(canvas, std::move(img));
 *             if (img) {
 *                 fAlpha8BitmapShader = fAlpha8Bitmap.makeShader(SkTileMode::kRepeat,
 *                                                                SkTileMode::kRepeat,
 *                                                                SkSamplingOptions(),
 *                                                                SkMatrix::I());
 *             }
 *             fLinearGradientShader = make_linear_gradient_shader(squareLength);
 *             fInitialized = true;
 *         }
 *
 *         SkBlendMode mode = SkBlendMode::kDstOver;
 *
 *         SkMatrix lm = SkMatrix::Translate(0, squareLength * 0.5f);
 *
 *         sk_sp<SkShader> shaders[] = {
 *             // gradient should appear over color bitmap
 *             SkShaders::Blend(mode, fLinearGradientShader, fColorBitmapShader),
 *             // gradient should appear over alpha8 bitmap colorized by the paint color
 *             SkShaders::Blend(mode, fLinearGradientShader, fAlpha8BitmapShader),
 *         };
 *         if (fUseLocalMatrix) {
 *             for (unsigned i = 0; i < std::size(shaders); ++i) {
 *                 shaders[i] = shaders[i] ? shaders[i]->makeWithLocalMatrix(lm) : nullptr;
 *             }
 *         }
 *
 *         SkPaint paint;
 *         paint.setColor(SK_ColorYELLOW);
 *
 *         const SkRect r = SkRect::MakeIWH(squareLength, squareLength);
 *
 *         for (size_t y = 0; y < std::size(shaders); ++y) {
 *             canvas->save();
 *             for (int alpha = 0xFF; alpha > 0; alpha -= 0x28) {
 *                 paint.setAlpha(alpha);
 *                 paint.setShader(shaders[y]);
 *                 canvas->drawRect(r, paint);
 *
 *                 canvas->translate(r.width() + 5, 0);
 *             }
 *             canvas->restore();
 *             canvas->translate(0, r.height() + 5);
 *         }
 *     }
 *
 * private:
 *     /** This determines the length and width of the bitmaps used in the ComposeShaders.  Values
 *      *  above 20 may cause an SkASSERT to fail in SkSmallAllocator. However, larger values will
 *      *  work in a release build.  You can change this parameter and then compile a release build
 *      *  to have this GM draw larger bitmaps for easier visual inspection.
 *      */
 *     inline static constexpr int squareLength = 20;
 *
 *     const bool fUseLocalMatrix;
 *
 *     bool fInitialized = false;
 *     SkBitmap fColorBitmap;
 *     SkBitmap fAlpha8Bitmap;
 *     sk_sp<SkShader> fColorBitmapShader;
 *     sk_sp<SkShader> fAlpha8BitmapShader;
 *     sk_sp<SkShader> fLinearGradientShader;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class ComposeShaderBitmapGM public constructor(
  useLm: Boolean,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int squareLength = 20
   * ```
   */
  private val fUseLocalMatrix: Boolean = TODO("Initialize fUseLocalMatrix")

  /**
   * C++ original:
   * ```cpp
   * const bool fUseLocalMatrix
   * ```
   */
  private var fInitialized: Boolean = TODO("Initialize fInitialized")

  /**
   * C++ original:
   * ```cpp
   * bool fInitialized = false
   * ```
   */
  private var fColorBitmap: SkBitmap = TODO("Initialize fColorBitmap")

  /**
   * C++ original:
   * ```cpp
   * SkBitmap fColorBitmap
   * ```
   */
  private var fAlpha8Bitmap: SkBitmap = TODO("Initialize fAlpha8Bitmap")

  /**
   * C++ original:
   * ```cpp
   * SkBitmap fAlpha8Bitmap
   * ```
   */
  private var fColorBitmapShader: SkSp<SkShader> = TODO("Initialize fColorBitmapShader")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> fColorBitmapShader
   * ```
   */
  private var fAlpha8BitmapShader: SkSp<SkShader> = TODO("Initialize fAlpha8BitmapShader")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> fAlpha8BitmapShader
   * ```
   */
  private var fLinearGradientShader: SkSp<SkShader> = TODO("Initialize fLinearGradientShader")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         return SkStringPrintf("composeshader_bitmap%s", fUseLocalMatrix ? "_lm" : "");
   *     }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override {
   *         return SkISize::Make(7 * (squareLength + 5), 2 * (squareLength + 5));
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
   *         if (!fInitialized) {
   *             draw_color_bm(&fColorBitmap, squareLength);
   *             sk_sp<SkImage> img = SkImages::RasterFromBitmap(fColorBitmap);
   *             img = ToolUtils::MakeTextureImage(canvas, std::move(img));
   *             if (img) {
   *                 fColorBitmapShader = img->makeShader(SkTileMode::kRepeat, SkTileMode::kRepeat,
   *                                                      SkSamplingOptions(), SkMatrix::I());
   *             }
   *             draw_alpha8_bm(&fAlpha8Bitmap, squareLength);
   *             img = SkImages::RasterFromBitmap(fAlpha8Bitmap);
   *             img = ToolUtils::MakeTextureImage(canvas, std::move(img));
   *             if (img) {
   *                 fAlpha8BitmapShader = fAlpha8Bitmap.makeShader(SkTileMode::kRepeat,
   *                                                                SkTileMode::kRepeat,
   *                                                                SkSamplingOptions(),
   *                                                                SkMatrix::I());
   *             }
   *             fLinearGradientShader = make_linear_gradient_shader(squareLength);
   *             fInitialized = true;
   *         }
   *
   *         SkBlendMode mode = SkBlendMode::kDstOver;
   *
   *         SkMatrix lm = SkMatrix::Translate(0, squareLength * 0.5f);
   *
   *         sk_sp<SkShader> shaders[] = {
   *             // gradient should appear over color bitmap
   *             SkShaders::Blend(mode, fLinearGradientShader, fColorBitmapShader),
   *             // gradient should appear over alpha8 bitmap colorized by the paint color
   *             SkShaders::Blend(mode, fLinearGradientShader, fAlpha8BitmapShader),
   *         };
   *         if (fUseLocalMatrix) {
   *             for (unsigned i = 0; i < std::size(shaders); ++i) {
   *                 shaders[i] = shaders[i] ? shaders[i]->makeWithLocalMatrix(lm) : nullptr;
   *             }
   *         }
   *
   *         SkPaint paint;
   *         paint.setColor(SK_ColorYELLOW);
   *
   *         const SkRect r = SkRect::MakeIWH(squareLength, squareLength);
   *
   *         for (size_t y = 0; y < std::size(shaders); ++y) {
   *             canvas->save();
   *             for (int alpha = 0xFF; alpha > 0; alpha -= 0x28) {
   *                 paint.setAlpha(alpha);
   *                 paint.setShader(shaders[y]);
   *                 canvas->drawRect(r, paint);
   *
   *                 canvas->translate(r.width() + 5, 0);
   *             }
   *             canvas->restore();
   *             canvas->translate(0, r.height() + 5);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    private val squareLength: Int = TODO("Initialize squareLength")
  }
}
