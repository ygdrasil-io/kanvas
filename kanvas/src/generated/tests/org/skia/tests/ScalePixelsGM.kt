package org.skia.tests

import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ScalePixelsGM : public skiagm::GM {
 * public:
 *     ScalePixelsGM(bool useImageScaling) : fUseImageScaling(useImageScaling) {}
 *
 * protected:
 *     SkString getName() const override {
 *         auto str = SkString("scale-pixels");
 *         if (fUseImageScaling) {
 *             str += "-via-image";
 *         }
 *         return str;
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(960, 1200); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         const SkImageInfo info = SkImageInfo::MakeN32Premul(100, 100);
 *
 *         const ImageMakerProc procs[] = {
 *             make_codec, make_raster, make_picture, make_codec, make_gpu,
 *         };
 *         for (auto& proc : procs) {
 *             sk_sp<SkImage> image(
 *                     proc(info, canvas->recordingContext(), canvas->recorder(), draw_contents));
 *             if (image) {
 *                 show_scaled_pixels(canvas, image.get(), fUseImageScaling);
 *             }
 *             canvas->translate(0, 120);
 *         }
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 *     bool fUseImageScaling;
 * }
 * ```
 */
public open class ScalePixelsGM public constructor(
  useImageScaling: Boolean,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * bool fUseImageScaling
   * ```
   */
  private var fUseImageScaling: Boolean = TODO("Initialize fUseImageScaling")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         auto str = SkString("scale-pixels");
   *         if (fUseImageScaling) {
   *             str += "-via-image";
   *         }
   *         return str;
   *     }
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
   *         const SkImageInfo info = SkImageInfo::MakeN32Premul(100, 100);
   *
   *         const ImageMakerProc procs[] = {
   *             make_codec, make_raster, make_picture, make_codec, make_gpu,
   *         };
   *         for (auto& proc : procs) {
   *             sk_sp<SkImage> image(
   *                     proc(info, canvas->recordingContext(), canvas->recorder(), draw_contents));
   *             if (image) {
   *                 show_scaled_pixels(canvas, image.get(), fUseImageScaling);
   *             }
   *             canvas->translate(0, 120);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
