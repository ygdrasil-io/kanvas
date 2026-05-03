package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class AnisotropicGM : public GM {
 * public:
 *     enum class Mode { kLinear, kMip, kAniso };
 *
 *     AnisotropicGM(Mode mode) : fMode(mode) {
 *         switch (fMode) {
 *             case Mode::kLinear:
 *                 fSampling = SkSamplingOptions(SkFilterMode::kLinear);
 *                 break;
 *             case Mode::kMip:
 *                 fSampling = SkSamplingOptions(SkFilterMode::kLinear, SkMipmapMode::kLinear);
 *                 break;
 *             case Mode::kAniso:
 *                 fSampling = SkSamplingOptions::Aniso(16);
 *                 break;
 *         }
 *         this->setBGColor(0xFFCCCCCC);
 *     }
 *
 * protected:
 *     SkString getName() const override {
 *         SkString name("anisotropic_image_scale_");
 *         switch (fMode) {
 *             case Mode::kLinear:
 *                 name += "linear";
 *                 break;
 *             case Mode::kMip:
 *                 name += "mip";
 *                 break;
 *             case Mode::kAniso:
 *                 name += "aniso";
 *                 break;
 *         }
 *         return name;
 *     }
 *
 *     SkISize getISize() override {
 *         return SkISize::Make(2*kImageSize + 3*kSpacer,
 *                              kNumVertImages*kImageSize + (kNumVertImages+1)*kSpacer);
 *     }
 *
 *     // Create an image consisting of lines radiating from its center
 *     void onOnceBeforeDraw() override {
 *         constexpr int kNumLines = 100;
 *         constexpr SkScalar kAngleStep = 360.0f / kNumLines;
 *         constexpr int kInnerOffset = 10;
 *
 *         auto info = SkImageInfo::MakeN32(kImageSize, kImageSize, kOpaque_SkAlphaType);
 *         auto surf = SkSurfaces::Raster(info);
 *         auto canvas = surf->getCanvas();
 *
 *         canvas->clear(SK_ColorWHITE);
 *
 *         SkPaint p;
 *         p.setAntiAlias(true);
 *
 *         SkScalar angle = 0.0f, sin, cos;
 *
 *         canvas->translate(kImageSize/2.0f, kImageSize/2.0f);
 *         for (int i = 0; i < kNumLines; ++i, angle += kAngleStep) {
 *             sin = SkScalarSin(angle);
 *             cos = SkScalarCos(angle);
 *             canvas->drawLine(cos * kInnerOffset, sin * kInnerOffset,
 *                              cos * kImageSize/2, sin * kImageSize/2, p);
 *         }
 *         fImage = surf->makeImageSnapshot();
 *     }
 *
 *     void draw(SkCanvas* canvas, int x, int y, int xSize, int ySize) {
 *         SkRect r = SkRect::MakeXYWH(SkIntToScalar(x), SkIntToScalar(y),
 *                                     SkIntToScalar(xSize), SkIntToScalar(ySize));
 *         canvas->drawImageRect(fImage, r, fSampling);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkScalar gScales[] = { 0.9f, 0.8f, 0.75f, 0.6f, 0.5f, 0.4f, 0.25f, 0.2f, 0.1f };
 *
 *         SkASSERT(kNumVertImages-1 == (int)std::size(gScales)/2);
 *
 *         // Minimize vertically
 *         for (int i = 0; i < (int)std::size(gScales); ++i) {
 *             int height = SkScalarFloorToInt(fImage->height() * gScales[i]);
 *
 *             int yOff;
 *             if (i <= (int)std::size(gScales)/2) {
 *                 yOff = kSpacer + i * (fImage->height() + kSpacer);
 *             } else {
 *                 // Position the more highly squashed images with their less squashed counterparts
 *                 yOff = (std::size(gScales) - i) * (fImage->height() + kSpacer) - height;
 *             }
 *
 *             this->draw(canvas, kSpacer, yOff, fImage->width(), height);
 *         }
 *
 *         // Minimize horizontally
 *         for (int i = 0; i < (int)std::size(gScales); ++i) {
 *             int width = SkScalarFloorToInt(fImage->width() * gScales[i]);
 *
 *             int xOff, yOff;
 *             if (i <= (int)std::size(gScales)/2) {
 *                 xOff = fImage->width() + 2*kSpacer;
 *                 yOff = kSpacer + i * (fImage->height() + kSpacer);
 *             } else {
 *                 // Position the more highly squashed images with their less squashed counterparts
 *                 xOff = fImage->width() + 2*kSpacer + fImage->width() - width;
 *                 yOff = kSpacer + (std::size(gScales) - i - 1) * (fImage->height() + kSpacer);
 *             }
 *
 *             this->draw(canvas, xOff, yOff, width, fImage->height());
 *         }
 *     }
 *
 * private:
 *     inline static constexpr int kImageSize     = 256;
 *     inline static constexpr int kSpacer        = 10;
 *     inline static constexpr int kNumVertImages = 5;
 *
 *     sk_sp<SkImage>    fImage;
 *     SkSamplingOptions fSampling;
 *     Mode              fMode;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class AnisotropicGM public constructor(
  mode: Mode,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kImageSize     = 256
   * ```
   */
  private var fImage: SkSp<SkImage> = TODO("Initialize fImage")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kSpacer        = 10
   * ```
   */
  private var fSampling: SkSamplingOptions = TODO("Initialize fSampling")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kNumVertImages = 5
   * ```
   */
  private var fMode: Mode = TODO("Initialize fMode")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         SkString name("anisotropic_image_scale_");
   *         switch (fMode) {
   *             case Mode::kLinear:
   *                 name += "linear";
   *                 break;
   *             case Mode::kMip:
   *                 name += "mip";
   *                 break;
   *             case Mode::kAniso:
   *                 name += "aniso";
   *                 break;
   *         }
   *         return name;
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
   *         return SkISize::Make(2*kImageSize + 3*kSpacer,
   *                              kNumVertImages*kImageSize + (kNumVertImages+1)*kSpacer);
   *     }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         constexpr int kNumLines = 100;
   *         constexpr SkScalar kAngleStep = 360.0f / kNumLines;
   *         constexpr int kInnerOffset = 10;
   *
   *         auto info = SkImageInfo::MakeN32(kImageSize, kImageSize, kOpaque_SkAlphaType);
   *         auto surf = SkSurfaces::Raster(info);
   *         auto canvas = surf->getCanvas();
   *
   *         canvas->clear(SK_ColorWHITE);
   *
   *         SkPaint p;
   *         p.setAntiAlias(true);
   *
   *         SkScalar angle = 0.0f, sin, cos;
   *
   *         canvas->translate(kImageSize/2.0f, kImageSize/2.0f);
   *         for (int i = 0; i < kNumLines; ++i, angle += kAngleStep) {
   *             sin = SkScalarSin(angle);
   *             cos = SkScalarCos(angle);
   *             canvas->drawLine(cos * kInnerOffset, sin * kInnerOffset,
   *                              cos * kImageSize/2, sin * kImageSize/2, p);
   *         }
   *         fImage = surf->makeImageSnapshot();
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void draw(SkCanvas* canvas, int x, int y, int xSize, int ySize) {
   *         SkRect r = SkRect::MakeXYWH(SkIntToScalar(x), SkIntToScalar(y),
   *                                     SkIntToScalar(xSize), SkIntToScalar(ySize));
   *         canvas->drawImageRect(fImage, r, fSampling);
   *     }
   * ```
   */
  protected fun draw(
    canvas: SkCanvas?,
    x: Int,
    y: Int,
    xSize: Int,
    ySize: Int,
  ) {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkScalar gScales[] = { 0.9f, 0.8f, 0.75f, 0.6f, 0.5f, 0.4f, 0.25f, 0.2f, 0.1f };
   *
   *         SkASSERT(kNumVertImages-1 == (int)std::size(gScales)/2);
   *
   *         // Minimize vertically
   *         for (int i = 0; i < (int)std::size(gScales); ++i) {
   *             int height = SkScalarFloorToInt(fImage->height() * gScales[i]);
   *
   *             int yOff;
   *             if (i <= (int)std::size(gScales)/2) {
   *                 yOff = kSpacer + i * (fImage->height() + kSpacer);
   *             } else {
   *                 // Position the more highly squashed images with their less squashed counterparts
   *                 yOff = (std::size(gScales) - i) * (fImage->height() + kSpacer) - height;
   *             }
   *
   *             this->draw(canvas, kSpacer, yOff, fImage->width(), height);
   *         }
   *
   *         // Minimize horizontally
   *         for (int i = 0; i < (int)std::size(gScales); ++i) {
   *             int width = SkScalarFloorToInt(fImage->width() * gScales[i]);
   *
   *             int xOff, yOff;
   *             if (i <= (int)std::size(gScales)/2) {
   *                 xOff = fImage->width() + 2*kSpacer;
   *                 yOff = kSpacer + i * (fImage->height() + kSpacer);
   *             } else {
   *                 // Position the more highly squashed images with their less squashed counterparts
   *                 xOff = fImage->width() + 2*kSpacer + fImage->width() - width;
   *                 yOff = kSpacer + (std::size(gScales) - i - 1) * (fImage->height() + kSpacer);
   *             }
   *
   *             this->draw(canvas, xOff, yOff, width, fImage->height());
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public enum class Mode {
    kLinear,
    kMip,
    kAniso,
  }

  public companion object {
    private val kImageSize: Int = TODO("Initialize kImageSize")

    private val kSpacer: Int = TODO("Initialize kSpacer")

    private val kNumVertImages: Int = TODO("Initialize kNumVertImages")
  }
}
