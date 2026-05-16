package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.core.SkPicture
import org.skia.foundation.SkSp
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class PictureGeneratorGM : public skiagm::GM {
 * protected:
 *     SkString getName() const override { return SkString("pictureimagegenerator"); }
 *
 *     SkISize getISize() override { return SkISize::Make(1160, 860); }
 *
 *     void onOnceBeforeDraw() override {
 *         const SkRect rect = SkRect::MakeWH(kPictureWidth, kPictureHeight);
 *         SkPictureRecorder recorder;
 *         SkCanvas* canvas = recorder.beginRecording(rect);
 *         draw_vector_logo(canvas, rect);
 *         fPicture = recorder.finishRecordingAsPicture();
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         const struct {
 *             SkISize  size;
 *             SkScalar scaleX, scaleY;
 *             SkScalar opacity;
 *         } configs[] = {
 *             { SkISize::Make(200, 100), 1, 1, 1 },
 *             { SkISize::Make(200, 200), 1, 1, 1 },
 *             { SkISize::Make(200, 200), 1, 2, 1 },
 *             { SkISize::Make(400, 200), 2, 2, 1 },
 *
 *             { SkISize::Make(200, 100), 1, 1, 0.9f  },
 *             { SkISize::Make(200, 200), 1, 1, 0.75f },
 *             { SkISize::Make(200, 200), 1, 2, 0.5f  },
 *             { SkISize::Make(400, 200), 2, 2, 0.25f },
 *
 *             { SkISize::Make(200, 200), 0.5f, 1,    1 },
 *             { SkISize::Make(200, 200), 1,    0.5f, 1 },
 *             { SkISize::Make(200, 200), 0.5f, 0.5f, 1 },
 *             { SkISize::Make(200, 200), 2,    2,    1 },
 *
 *             { SkISize::Make(200, 100), -1,  1, 1    },
 *             { SkISize::Make(200, 100),  1, -1, 1    },
 *             { SkISize::Make(200, 100), -1, -1, 1    },
 *             { SkISize::Make(200, 100), -1, -1, 0.5f },
 *         };
 *
 *         auto srgbColorSpace = SkColorSpace::MakeSRGB();
 *         const unsigned kDrawsPerRow = 4;
 *         const SkScalar kDrawSize = 250;
 *
 *         for (size_t i = 0; i < std::size(configs); ++i) {
 *             SkPaint p;
 *             p.setAlphaf(configs[i].opacity);
 *
 *             SkMatrix m = SkMatrix::Scale(configs[i].scaleX, configs[i].scaleY);
 *             if (configs[i].scaleX < 0) {
 *                 m.postTranslate(SkIntToScalar(configs[i].size.width()), 0);
 *             }
 *             if (configs[i].scaleY < 0) {
 *                 m.postTranslate(0, SkIntToScalar(configs[i].size.height()));
 *             }
 *             std::unique_ptr<SkImageGenerator> gen =
 *                     SkImageGenerators::MakeFromPicture(configs[i].size,
 *                                                        fPicture,
 *                                                        &m,
 *                                                        p.getAlpha() != 255 ? &p : nullptr,
 *                                                        SkImages::BitDepth::kU8,
 *                                                        srgbColorSpace);
 *
 *             SkImageInfo bmInfo = gen->getInfo().makeColorSpace(canvas->imageInfo().refColorSpace());
 *
 *             SkBitmap bm;
 *             bm.allocPixels(bmInfo);
 *             SkAssertResult(gen->getPixels(bm.info(), bm.getPixels(), bm.rowBytes()));
 *
 *             const SkScalar x = kDrawSize * (i % kDrawsPerRow);
 *             const SkScalar y = kDrawSize * (i / kDrawsPerRow);
 *
 *             p.setColor(0xfff0f0f0);
 *             p.setAlphaf(1.0f);
 *             canvas->drawRect(SkRect::MakeXYWH(x, y,
 *                                               SkIntToScalar(bm.width()),
 *                                               SkIntToScalar(bm.height())), p);
 *             canvas->drawImage(bm.asImage(), x, y);
 *         }
 *     }
 *
 * private:
 *     sk_sp<SkPicture> fPicture;
 *
 *     const SkScalar kPictureWidth = 200;
 *     const SkScalar kPictureHeight = 100;
 *
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class PictureGeneratorGM : GM() {
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
   * const SkScalar kPictureWidth = 200
   * ```
   */
  private val kPictureWidth: SkScalar = TODO("Initialize kPictureWidth")

  /**
   * C++ original:
   * ```cpp
   * const SkScalar kPictureHeight = 100
   * ```
   */
  private val kPictureHeight: SkScalar = TODO("Initialize kPictureHeight")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("pictureimagegenerator"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(1160, 860); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         const SkRect rect = SkRect::MakeWH(kPictureWidth, kPictureHeight);
   *         SkPictureRecorder recorder;
   *         SkCanvas* canvas = recorder.beginRecording(rect);
   *         draw_vector_logo(canvas, rect);
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
   * void onDraw(SkCanvas* canvas) override {
   *         const struct {
   *             SkISize  size;
   *             SkScalar scaleX, scaleY;
   *             SkScalar opacity;
   *         } configs[] = {
   *             { SkISize::Make(200, 100), 1, 1, 1 },
   *             { SkISize::Make(200, 200), 1, 1, 1 },
   *             { SkISize::Make(200, 200), 1, 2, 1 },
   *             { SkISize::Make(400, 200), 2, 2, 1 },
   *
   *             { SkISize::Make(200, 100), 1, 1, 0.9f  },
   *             { SkISize::Make(200, 200), 1, 1, 0.75f },
   *             { SkISize::Make(200, 200), 1, 2, 0.5f  },
   *             { SkISize::Make(400, 200), 2, 2, 0.25f },
   *
   *             { SkISize::Make(200, 200), 0.5f, 1,    1 },
   *             { SkISize::Make(200, 200), 1,    0.5f, 1 },
   *             { SkISize::Make(200, 200), 0.5f, 0.5f, 1 },
   *             { SkISize::Make(200, 200), 2,    2,    1 },
   *
   *             { SkISize::Make(200, 100), -1,  1, 1    },
   *             { SkISize::Make(200, 100),  1, -1, 1    },
   *             { SkISize::Make(200, 100), -1, -1, 1    },
   *             { SkISize::Make(200, 100), -1, -1, 0.5f },
   *         };
   *
   *         auto srgbColorSpace = SkColorSpace::MakeSRGB();
   *         const unsigned kDrawsPerRow = 4;
   *         const SkScalar kDrawSize = 250;
   *
   *         for (size_t i = 0; i < std::size(configs); ++i) {
   *             SkPaint p;
   *             p.setAlphaf(configs[i].opacity);
   *
   *             SkMatrix m = SkMatrix::Scale(configs[i].scaleX, configs[i].scaleY);
   *             if (configs[i].scaleX < 0) {
   *                 m.postTranslate(SkIntToScalar(configs[i].size.width()), 0);
   *             }
   *             if (configs[i].scaleY < 0) {
   *                 m.postTranslate(0, SkIntToScalar(configs[i].size.height()));
   *             }
   *             std::unique_ptr<SkImageGenerator> gen =
   *                     SkImageGenerators::MakeFromPicture(configs[i].size,
   *                                                        fPicture,
   *                                                        &m,
   *                                                        p.getAlpha() != 255 ? &p : nullptr,
   *                                                        SkImages::BitDepth::kU8,
   *                                                        srgbColorSpace);
   *
   *             SkImageInfo bmInfo = gen->getInfo().makeColorSpace(canvas->imageInfo().refColorSpace());
   *
   *             SkBitmap bm;
   *             bm.allocPixels(bmInfo);
   *             SkAssertResult(gen->getPixels(bm.info(), bm.getPixels(), bm.rowBytes()));
   *
   *             const SkScalar x = kDrawSize * (i % kDrawsPerRow);
   *             const SkScalar y = kDrawSize * (i / kDrawsPerRow);
   *
   *             p.setColor(0xfff0f0f0);
   *             p.setAlphaf(1.0f);
   *             canvas->drawRect(SkRect::MakeXYWH(x, y,
   *                                               SkIntToScalar(bm.width()),
   *                                               SkIntToScalar(bm.height())), p);
   *             canvas->drawImage(bm.asImage(), x, y);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
