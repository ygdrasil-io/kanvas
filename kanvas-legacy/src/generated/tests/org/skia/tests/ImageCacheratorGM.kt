package org.skia.tests

import kotlin.Boolean
import kotlin.CharArray
import kotlin.Float
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.core.SkPicture
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.gpu.ganesh.GrDirectContext
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class ImageCacheratorGM : public skiagm::GM {
 *     typedef std::unique_ptr<SkImageGenerator> (*FactoryFunc)(SkCanvas*, sk_sp<SkPicture>);
 *
 *     SkString         fName;
 *     FactoryFunc      fFactory;
 *     sk_sp<SkPicture> fPicture;
 *     sk_sp<SkImage>   fImage;
 *     sk_sp<SkImage>   fImageSubset;
 *     bool             fUseTexture;
 *
 * public:
 *     ImageCacheratorGM(const char suffix[], FactoryFunc factory, bool useTexture) :
 *                     fFactory(factory), fUseTexture(useTexture) {
 *         fName.printf("image-cacherator-from-%s", suffix);
 *     }
 *
 * protected:
 *     SkString getName() const override { return fName; }
 *
 *     SkISize getISize() override { return SkISize::Make(960, 450); }
 *
 *     void onOnceBeforeDraw() override {
 *         const SkRect bounds = SkRect::MakeXYWH(100, 100, 100, 100);
 *         SkPictureRecorder recorder;
 *         draw_something(recorder.beginRecording(bounds), bounds);
 *         fPicture = recorder.finishRecordingAsPicture();
 *     }
 *
 *     bool makeCaches(SkCanvas* canvas) {
 *         {
 *             auto gen = fFactory(canvas, fPicture);
 *             if (!gen) {
 *                 return false;
 *             }
 * #if defined(SK_GANESH)
 *             if (fUseTexture) {
 *                 auto textureGen = std::unique_ptr<GrTextureGenerator>(
 *                         static_cast<GrTextureGenerator*>(gen.release()));
 *                 fImage = SkImages::DeferredFromTextureGenerator(std::move(textureGen));
 *             } else
 * #endif
 *             {
 *                 SkASSERT(!fUseTexture);
 *                 fImage = SkImages::DeferredFromGenerator(std::move(gen));
 *             }
 *             if (!fImage) {
 *                 return false;
 *             }
 *             SkASSERT(fImage->dimensions() == SkISize::Make(100, 100));
 *         }
 *
 *         {
 *             const SkIRect subset = SkIRect::MakeLTRB(50, 50, 100, 100);
 *
 *             // We re-create the generator here on the off chance that making a subset from
 *             // 'fImage' might perturb its state.
 *             auto gen = fFactory(canvas, fPicture);
 *             if (!gen) {
 *                 return false;
 *             }
 *
 *             auto recorder = canvas->baseRecorder();
 * #if defined(SK_GANESH)
 *             if (fUseTexture) {
 *                 auto textureGen = std::unique_ptr<GrTextureGenerator>(
 *                         static_cast<GrTextureGenerator*>(gen.release()));
 *                 fImageSubset = SkImages::DeferredFromTextureGenerator(std::move(textureGen))
 *                                        ->makeSubset(recorder, subset, {});
 *             } else
 * #endif
 *             {
 *                 SkASSERT(!fUseTexture);
 *                 fImageSubset = SkImages::DeferredFromGenerator(std::move(gen))
 *                                        ->makeSubset(recorder, subset, {});
 *             }
 *             if (!fImageSubset) {
 *                 return false;
 *             }
 *             SkASSERT(fImageSubset->dimensions() == SkISize::Make(50, 50));
 *         }
 *
 *         return true;
 *     }
 *
 *     static void draw_placeholder(SkCanvas* canvas, SkScalar x, SkScalar y, int w, int h) {
 *         SkPaint paint;
 *         paint.setStyle(SkPaint::kStroke_Style);
 *         SkRect r = SkRect::MakeXYWH(x, y, SkIntToScalar(w), SkIntToScalar(h));
 *         canvas->drawRect(r, paint);
 *         canvas->drawLine(r.left(), r.top(), r.right(), r.bottom(), paint);
 *         canvas->drawLine(r.left(), r.bottom(), r.right(), r.top(), paint);
 *     }
 *
 *     static void draw_as_bitmap(GrDirectContext* dContext, SkCanvas* canvas, SkImage* image,
 *                                SkScalar x, SkScalar y) {
 *         SkBitmap bitmap;
 *         if (as_IB(image)->getROPixels(dContext, &bitmap)) {
 *             canvas->drawImage(bitmap.asImage(), x, y);
 *         } else {
 *             draw_placeholder(canvas, x, y, image->width(), image->height());
 *         }
 *     }
 *
 *     static void draw_as_tex(SkCanvas* canvas, SkImage* image, SkScalar x, SkScalar y) {
 * #if defined(SK_GANESH)
 *         if (as_IB(image)->isGaneshBacked()) {
 *             // The gpu-backed images are drawn in this manner bc the generator backed images
 *             // aren't considered texture-backed
 *             // We know for this test the targetSurface proxy is different from the image so we can
 *             // just pass in nullptr.
 *             auto [view, ct] =
 *                     skgpu::ganesh::AsView(canvas->recordingContext(), image, skgpu::Mipmapped::kNo,
 *                                           /*targetSurface=*/nullptr);
 *             if (!view) {
 *                 // show placeholder if we have no texture
 *                 draw_placeholder(canvas, x, y, image->width(), image->height());
 *                 return;
 *             }
 *             SkColorInfo colorInfo(GrColorTypeToSkColorType(ct),
 *                                   image->alphaType(),
 *                                   image->refColorSpace());
 *             // No API to draw a GrTexture directly, so we cheat and create a private image subclass
 *             sk_sp<SkImage> texImage(new SkImage_Ganesh(sk_ref_sp(canvas->recordingContext()),
 *                                                        image->uniqueID(),
 *                                                        std::move(view),
 *                                                        std::move(colorInfo)));
 *             canvas->drawImage(texImage.get(), x, y);
 *         } else
 * #endif
 *         {
 *             canvas->drawImage(image, x, y);
 *         }
 *     }
 *
 *     void drawRow(GrDirectContext* dContext, SkCanvas* canvas, float scale) const {
 *         canvas->scale(scale, scale);
 *
 *         SkMatrix matrix = SkMatrix::Translate(-100, -100);
 *         canvas->drawPicture(fPicture, &matrix, nullptr);
 *
 *         // Draw the tex first, so it doesn't hit a lucky cache from the raster version. This
 *         // way we also can force the generateTexture call.
 *
 *         draw_as_tex(canvas, fImage.get(), 150, 0);
 *         draw_as_tex(canvas, fImageSubset.get(), 150+101, 0);
 *
 *         draw_as_bitmap(dContext, canvas, fImage.get(), 310, 0);
 *         draw_as_bitmap(dContext, canvas, fImageSubset.get(), 310+101, 0);
 *     }
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 * #if defined(SK_GANESH)
 *         auto dContext = GrAsDirectContext(canvas->recordingContext());
 * #else
 *         constexpr GrDirectContext* dContext = nullptr;
 * #endif
 *         if (!this->makeCaches(canvas)) {
 *             errorMsg->printf("Could not create cached images");
 *             return DrawResult::kSkip;
 *         }
 *
 *         canvas->save();
 *             canvas->translate(20, 20);
 *             this->drawRow(dContext, canvas, 1.0);
 *         canvas->restore();
 *
 *         canvas->save();
 *             canvas->translate(20, 150);
 *             this->drawRow(dContext, canvas, 0.25f);
 *         canvas->restore();
 *
 *         canvas->save();
 *             canvas->translate(20, 220);
 *             this->drawRow(dContext, canvas, 2.0f);
 *         canvas->restore();
 *
 *         return DrawResult::kOk;
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class ImageCacheratorGM public constructor(
  suffix: CharArray,
  factory: ImageCacheratorGMFactoryFunc,
  useTexture: Boolean,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString         fName
   * ```
   */
  private var fName: String = TODO("Initialize fName")

  /**
   * C++ original:
   * ```cpp
   * FactoryFunc      fFactory
   * ```
   */
  private var fFactory: ImageCacheratorGMFactoryFunc = TODO("Initialize fFactory")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkPicture> fPicture
   * ```
   */
  private var fPicture: SkSp<SkPicture> = TODO("Initialize fPicture")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage>   fImage
   * ```
   */
  private var fImage: SkSp<SkImage> = TODO("Initialize fImage")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage>   fImageSubset
   * ```
   */
  private var fImageSubset: SkSp<SkImage> = TODO("Initialize fImageSubset")

  /**
   * C++ original:
   * ```cpp
   * bool             fUseTexture
   * ```
   */
  private var fUseTexture: Boolean = TODO("Initialize fUseTexture")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return fName; }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(960, 450); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         const SkRect bounds = SkRect::MakeXYWH(100, 100, 100, 100);
   *         SkPictureRecorder recorder;
   *         draw_something(recorder.beginRecording(bounds), bounds);
   *         fPicture = recorder.finishRecordingAsPicture();
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * bool makeCaches(SkCanvas* canvas) {
   *         {
   *             auto gen = fFactory(canvas, fPicture);
   *             if (!gen) {
   *                 return false;
   *             }
   * #if defined(SK_GANESH)
   *             if (fUseTexture) {
   *                 auto textureGen = std::unique_ptr<GrTextureGenerator>(
   *                         static_cast<GrTextureGenerator*>(gen.release()));
   *                 fImage = SkImages::DeferredFromTextureGenerator(std::move(textureGen));
   *             } else
   * #endif
   *             {
   *                 SkASSERT(!fUseTexture);
   *                 fImage = SkImages::DeferredFromGenerator(std::move(gen));
   *             }
   *             if (!fImage) {
   *                 return false;
   *             }
   *             SkASSERT(fImage->dimensions() == SkISize::Make(100, 100));
   *         }
   *
   *         {
   *             const SkIRect subset = SkIRect::MakeLTRB(50, 50, 100, 100);
   *
   *             // We re-create the generator here on the off chance that making a subset from
   *             // 'fImage' might perturb its state.
   *             auto gen = fFactory(canvas, fPicture);
   *             if (!gen) {
   *                 return false;
   *             }
   *
   *             auto recorder = canvas->baseRecorder();
   * #if defined(SK_GANESH)
   *             if (fUseTexture) {
   *                 auto textureGen = std::unique_ptr<GrTextureGenerator>(
   *                         static_cast<GrTextureGenerator*>(gen.release()));
   *                 fImageSubset = SkImages::DeferredFromTextureGenerator(std::move(textureGen))
   *                                        ->makeSubset(recorder, subset, {});
   *             } else
   * #endif
   *             {
   *                 SkASSERT(!fUseTexture);
   *                 fImageSubset = SkImages::DeferredFromGenerator(std::move(gen))
   *                                        ->makeSubset(recorder, subset, {});
   *             }
   *             if (!fImageSubset) {
   *                 return false;
   *             }
   *             SkASSERT(fImageSubset->dimensions() == SkISize::Make(50, 50));
   *         }
   *
   *         return true;
   *     }
   * ```
   */
  protected fun makeCaches(canvas: SkCanvas?): Boolean {
    TODO("Implement makeCaches")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawRow(GrDirectContext* dContext, SkCanvas* canvas, float scale) const {
   *         canvas->scale(scale, scale);
   *
   *         SkMatrix matrix = SkMatrix::Translate(-100, -100);
   *         canvas->drawPicture(fPicture, &matrix, nullptr);
   *
   *         // Draw the tex first, so it doesn't hit a lucky cache from the raster version. This
   *         // way we also can force the generateTexture call.
   *
   *         draw_as_tex(canvas, fImage.get(), 150, 0);
   *         draw_as_tex(canvas, fImageSubset.get(), 150+101, 0);
   *
   *         draw_as_bitmap(dContext, canvas, fImage.get(), 310, 0);
   *         draw_as_bitmap(dContext, canvas, fImageSubset.get(), 310+101, 0);
   *     }
   * ```
   */
  protected fun drawRow(
    dContext: GrDirectContext?,
    canvas: SkCanvas?,
    scale: Float,
  ) {
    TODO("Implement drawRow")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
   * #if defined(SK_GANESH)
   *         auto dContext = GrAsDirectContext(canvas->recordingContext());
   * #else
   *         constexpr GrDirectContext* dContext = nullptr;
   * #endif
   *         if (!this->makeCaches(canvas)) {
   *             errorMsg->printf("Could not create cached images");
   *             return DrawResult::kSkip;
   *         }
   *
   *         canvas->save();
   *             canvas->translate(20, 20);
   *             this->drawRow(dContext, canvas, 1.0);
   *         canvas->restore();
   *
   *         canvas->save();
   *             canvas->translate(20, 150);
   *             this->drawRow(dContext, canvas, 0.25f);
   *         canvas->restore();
   *
   *         canvas->save();
   *             canvas->translate(20, 220);
   *             this->drawRow(dContext, canvas, 2.0f);
   *         canvas->restore();
   *
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement onDraw")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static void draw_placeholder(SkCanvas* canvas, SkScalar x, SkScalar y, int w, int h) {
     *         SkPaint paint;
     *         paint.setStyle(SkPaint::kStroke_Style);
     *         SkRect r = SkRect::MakeXYWH(x, y, SkIntToScalar(w), SkIntToScalar(h));
     *         canvas->drawRect(r, paint);
     *         canvas->drawLine(r.left(), r.top(), r.right(), r.bottom(), paint);
     *         canvas->drawLine(r.left(), r.bottom(), r.right(), r.top(), paint);
     *     }
     * ```
     */
    protected fun drawPlaceholder(
      canvas: SkCanvas?,
      x: SkScalar,
      y: SkScalar,
      w: Int,
      h: Int,
    ) {
      TODO("Implement drawPlaceholder")
    }

    /**
     * C++ original:
     * ```cpp
     * static void draw_as_bitmap(GrDirectContext* dContext, SkCanvas* canvas, SkImage* image,
     *                                SkScalar x, SkScalar y) {
     *         SkBitmap bitmap;
     *         if (as_IB(image)->getROPixels(dContext, &bitmap)) {
     *             canvas->drawImage(bitmap.asImage(), x, y);
     *         } else {
     *             draw_placeholder(canvas, x, y, image->width(), image->height());
     *         }
     *     }
     * ```
     */
    protected fun drawAsBitmap(
      dContext: GrDirectContext?,
      canvas: SkCanvas?,
      image: SkImage?,
      x: SkScalar,
      y: SkScalar,
    ) {
      TODO("Implement drawAsBitmap")
    }

    /**
     * C++ original:
     * ```cpp
     * static void draw_as_tex(SkCanvas* canvas, SkImage* image, SkScalar x, SkScalar y) {
     * #if defined(SK_GANESH)
     *         if (as_IB(image)->isGaneshBacked()) {
     *             // The gpu-backed images are drawn in this manner bc the generator backed images
     *             // aren't considered texture-backed
     *             // We know for this test the targetSurface proxy is different from the image so we can
     *             // just pass in nullptr.
     *             auto [view, ct] =
     *                     skgpu::ganesh::AsView(canvas->recordingContext(), image, skgpu::Mipmapped::kNo,
     *                                           /*targetSurface=*/nullptr);
     *             if (!view) {
     *                 // show placeholder if we have no texture
     *                 draw_placeholder(canvas, x, y, image->width(), image->height());
     *                 return;
     *             }
     *             SkColorInfo colorInfo(GrColorTypeToSkColorType(ct),
     *                                   image->alphaType(),
     *                                   image->refColorSpace());
     *             // No API to draw a GrTexture directly, so we cheat and create a private image subclass
     *             sk_sp<SkImage> texImage(new SkImage_Ganesh(sk_ref_sp(canvas->recordingContext()),
     *                                                        image->uniqueID(),
     *                                                        std::move(view),
     *                                                        std::move(colorInfo)));
     *             canvas->drawImage(texImage.get(), x, y);
     *         } else
     * #endif
     *         {
     *             canvas->drawImage(image, x, y);
     *         }
     *     }
     * ```
     */
    protected fun drawAsTex(
      canvas: SkCanvas?,
      image: SkImage?,
      x: SkScalar,
      y: SkScalar,
    ) {
      TODO("Implement drawAsTex")
    }
  }
}
