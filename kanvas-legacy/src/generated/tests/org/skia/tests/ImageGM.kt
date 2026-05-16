package org.skia.tests

import kotlin.Int
import kotlin.String
import kotlin.ULong
import kotlin.Unit
import org.skia.core.SkCanvas
import org.skia.math.SkISize
import org.skia.math.SkSize

/**
 * C++ original:
 * ```cpp
 * class ImageGM : public skiagm::GM {
 *     void*   fBuffer;
 *     size_t  fBufferSize;
 *     SkSize  fSize;
 *     enum {
 *         W = 64,
 *         H = 64,
 *         RB = W * 4 + 8,
 *     };
 * public:
 *     ImageGM() {
 *         fBufferSize = RB * H;
 *         fBuffer = sk_malloc_throw(fBufferSize);
 *         fSize.set(SkIntToScalar(W), SkIntToScalar(H));
 *     }
 *
 *     ~ImageGM() override {
 *         sk_free(fBuffer);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("image-surface"); }
 *
 *     SkISize getISize() override { return SkISize::Make(960, 1200); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->scale(2, 2);
 *
 *         SkFont font(ToolUtils::DefaultPortableTypeface(), 8);
 *
 *         canvas->drawString("Original Img",  10,  60, font, SkPaint());
 *         canvas->drawString("Modified Img",  10, 140, font, SkPaint());
 *         canvas->drawString("Cur Surface",   10, 220, font, SkPaint());
 *         canvas->drawString("Full Crop",     10, 300, font, SkPaint());
 *         canvas->drawString("Over-crop",     10, 380, font, SkPaint());
 *         canvas->drawString("Upper-left",    10, 460, font, SkPaint());
 *         canvas->drawString("No Crop",       10, 540, font, SkPaint());
 *
 *         canvas->drawString("Pre-Alloc Img", 80,  10, font, SkPaint());
 *         canvas->drawString("New Alloc Img", 160, 10, font, SkPaint());
 *         canvas->drawString( "GPU",          265, 10, font, SkPaint());
 *
 *         canvas->translate(80, 20);
 *
 *         // since we draw into this directly, we need to start fresh
 *         sk_bzero(fBuffer, fBufferSize);
 *
 *         SkImageInfo info = SkImageInfo::MakeN32Premul(W, H);
 *         sk_sp<SkSurface> surf0(SkSurfaces::WrapPixels(info, fBuffer, RB));
 *         sk_sp<SkSurface> surf1(SkSurfaces::Raster(info));
 *         sk_sp<SkSurface> surf2;
 * #if defined(SK_GANESH)
 *         surf2 = SkSurfaces::RenderTarget(canvas->recordingContext(), skgpu::Budgeted::kNo, info);
 * #endif
 *
 *         test_surface(canvas, surf0.get(), true);
 *         canvas->translate(80, 0);
 *         test_surface(canvas, surf1.get(), true);
 *         if (surf2) {
 *             canvas->translate(80, 0);
 *             test_surface(canvas, surf2.get(), true);
 *         }
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class ImageGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * void*   fBuffer
   * ```
   */
  private var fBuffer: Unit? = TODO("Initialize fBuffer")

  /**
   * C++ original:
   * ```cpp
   * size_t  fBufferSize
   * ```
   */
  private var fBufferSize: ULong = TODO("Initialize fBufferSize")

  /**
   * C++ original:
   * ```cpp
   * SkSize  fSize
   * ```
   */
  private var fSize: SkSize = TODO("Initialize fSize")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("image-surface"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(960, 1200); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         canvas->scale(2, 2);
   *
   *         SkFont font(ToolUtils::DefaultPortableTypeface(), 8);
   *
   *         canvas->drawString("Original Img",  10,  60, font, SkPaint());
   *         canvas->drawString("Modified Img",  10, 140, font, SkPaint());
   *         canvas->drawString("Cur Surface",   10, 220, font, SkPaint());
   *         canvas->drawString("Full Crop",     10, 300, font, SkPaint());
   *         canvas->drawString("Over-crop",     10, 380, font, SkPaint());
   *         canvas->drawString("Upper-left",    10, 460, font, SkPaint());
   *         canvas->drawString("No Crop",       10, 540, font, SkPaint());
   *
   *         canvas->drawString("Pre-Alloc Img", 80,  10, font, SkPaint());
   *         canvas->drawString("New Alloc Img", 160, 10, font, SkPaint());
   *         canvas->drawString( "GPU",          265, 10, font, SkPaint());
   *
   *         canvas->translate(80, 20);
   *
   *         // since we draw into this directly, we need to start fresh
   *         sk_bzero(fBuffer, fBufferSize);
   *
   *         SkImageInfo info = SkImageInfo::MakeN32Premul(W, H);
   *         sk_sp<SkSurface> surf0(SkSurfaces::WrapPixels(info, fBuffer, RB));
   *         sk_sp<SkSurface> surf1(SkSurfaces::Raster(info));
   *         sk_sp<SkSurface> surf2;
   * #if defined(SK_GANESH)
   *         surf2 = SkSurfaces::RenderTarget(canvas->recordingContext(), skgpu::Budgeted::kNo, info);
   * #endif
   *
   *         test_surface(canvas, surf0.get(), true);
   *         canvas->translate(80, 0);
   *         test_surface(canvas, surf1.get(), true);
   *         if (surf2) {
   *             canvas->translate(80, 0);
   *             test_surface(canvas, surf2.get(), true);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    public val w: Int = TODO("Initialize w")

    public val h: Int = TODO("Initialize h")

    public val rb: Int = TODO("Initialize rb")
  }
}
