package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ColorCubeGM : public GM {
 * public:
 *     ColorCubeGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("jpg-color-cube"); }
 *
 *     SkISize getISize() override { return SkISize::Make(512, 512); }
 *
 *     void onOnceBeforeDraw() override {
 *         SkBitmap bmp;
 *         bmp.allocN32Pixels(512, 512, true);
 *         int bX = 0, bY = 0;
 *         for (int b = 0; b < 64; ++b) {
 *             for (int r = 0; r < 64; ++r) {
 *                 for (int g = 0; g < 64; ++g) {
 *                     *bmp.getAddr32(bX + r, bY + g) = SkPackARGB32(255,
 *                                                                   SkTPin(r * 4, 0, 255),
 *                                                                   SkTPin(g * 4, 0, 255),
 *                                                                   SkTPin(b * 4, 0, 255));
 *                 }
 *             }
 *             bX += 64;
 *             if (bX >= 512) {
 *                 bX = 0;
 *                 bY += 64;
 *             }
 *         }
 *         sk_sp<SkData> data = SkJpegEncoder::Encode(bmp.pixmap(), {});
 *         SkASSERT_RELEASE(data);
 *         fImage = SkImages::DeferredFromEncodedData(data);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->drawImage(fImage, 0, 0);
 *     }
 *
 * private:
 *     sk_sp<SkImage> fImage;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class ColorCubeGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fImage
   * ```
   */
  private var fImage: SkSp<SkImage> = TODO("Initialize fImage")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("jpg-color-cube"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(512, 512); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         SkBitmap bmp;
   *         bmp.allocN32Pixels(512, 512, true);
   *         int bX = 0, bY = 0;
   *         for (int b = 0; b < 64; ++b) {
   *             for (int r = 0; r < 64; ++r) {
   *                 for (int g = 0; g < 64; ++g) {
   *                     *bmp.getAddr32(bX + r, bY + g) = SkPackARGB32(255,
   *                                                                   SkTPin(r * 4, 0, 255),
   *                                                                   SkTPin(g * 4, 0, 255),
   *                                                                   SkTPin(b * 4, 0, 255));
   *                 }
   *             }
   *             bX += 64;
   *             if (bX >= 512) {
   *                 bX = 0;
   *                 bY += 64;
   *             }
   *         }
   *         sk_sp<SkData> data = SkJpegEncoder::Encode(bmp.pixmap(), {});
   *         SkASSERT_RELEASE(data);
   *         fImage = SkImages::DeferredFromEncodedData(data);
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
   *         canvas->drawImage(fImage, 0, 0);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
