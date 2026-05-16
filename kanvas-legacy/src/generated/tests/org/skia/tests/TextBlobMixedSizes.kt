package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.core.SkTextBlob
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class TextBlobMixedSizes : public GM {
 * public:
 *     // This gm tests that textblobs of mixed sizes with a large glyph will render properly
 *     TextBlobMixedSizes(bool useDFT) : fUseDFT(useDFT) {}
 *
 * protected:
 *     void onOnceBeforeDraw() override {
 *         SkTextBlobBuilder builder;
 *
 *         // make textblob.  To stress distance fields, we choose sizes appropriately
 *         sk_sp<SkTypeface> tf = ToolUtils::CreateTypefaceFromResource("fonts/HangingS.ttf");
 *         if (!tf) {
 *             tf = ToolUtils::DefaultPortableTypeface();
 *         }
 *         SkFont font(tf, 262);
 *         font.setSubpixel(true);
 *         font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
 *
 *         const char* text = "Skia";
 *
 *         ToolUtils::add_to_text_blob(&builder, text, font, 0, 0);
 *
 *         // large
 *         SkRect bounds;
 *         font.measureText(text, strlen(text), SkTextEncoding::kUTF8, &bounds);
 *         SkScalar yOffset = bounds.height();
 *         font.setSize(162);
 *
 *         ToolUtils::add_to_text_blob(&builder, text, font, 0, yOffset);
 *
 *         // Medium
 *         font.measureText(text, strlen(text), SkTextEncoding::kUTF8, &bounds);
 *         yOffset += bounds.height();
 *         font.setSize(72);
 *
 *         ToolUtils::add_to_text_blob(&builder, text, font, 0, yOffset);
 *
 *         // Small
 *         font.measureText(text, strlen(text), SkTextEncoding::kUTF8, &bounds);
 *         yOffset += bounds.height();
 *         font.setSize(32);
 *
 *         ToolUtils::add_to_text_blob(&builder, text, font, 0, yOffset);
 *
 *         // micro (will fall out of distance field text even if distance field text is enabled)
 *         font.measureText(text, strlen(text), SkTextEncoding::kUTF8, &bounds);
 *         yOffset += bounds.height();
 *         font.setSize(14);
 *
 *         ToolUtils::add_to_text_blob(&builder, text, font, 0, yOffset);
 *
 *         // Zero size.
 *         font.measureText(text, strlen(text), SkTextEncoding::kUTF8, &bounds);
 *         yOffset += bounds.height();
 *         font.setSize(0);
 *
 *         ToolUtils::add_to_text_blob(&builder, text, font, 0, yOffset);
 *
 *         // build
 *         fBlob = builder.make();
 *     }
 *
 *     SkString getName() const override {
 *         return SkStringPrintf("textblobmixedsizes%s",
 *                               fUseDFT ? "_df" : "");
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(kWidth, kHeight); }
 *
 *     void onDraw(SkCanvas* inputCanvas) override {
 *         SkCanvas* canvas = inputCanvas;
 *         sk_sp<SkSurface> surface;
 *         if (fUseDFT) {
 *             // Create a new Canvas to enable DFT
 *             SkISize size = this->getISize();
 *             if (!inputCanvas->getBaseLayerSize().isEmpty()) {
 *                 size = inputCanvas->getBaseLayerSize();
 *             }
 *             sk_sp<SkColorSpace> colorSpace = inputCanvas->imageInfo().refColorSpace();
 *             SkImageInfo info = SkImageInfo::MakeN32(size.width(), size.height(),
 *                                                     kPremul_SkAlphaType, colorSpace);
 *             SkSurfaceProps inputProps;
 *             inputCanvas->getProps(&inputProps);
 *             SkSurfaceProps props(
 *                     SkSurfaceProps::kUseDeviceIndependentFonts_Flag | inputProps.flags(),
 *                     inputProps.pixelGeometry());
 * #if defined(SK_GRAPHITE)
 *             if (auto recorder = inputCanvas->recorder()) {
 *                 surface = SkSurfaces::RenderTarget(recorder, info, skgpu::Mipmapped::kNo, &props);
 *             } else
 * #endif
 * #if defined(SK_GANESH)
 *             if (auto ctx = inputCanvas->recordingContext()) {
 *                 surface = SkSurfaces::RenderTarget(ctx, skgpu::Budgeted::kNo, info, 0, &props);
 *             }
 * #endif
 *             canvas = surface ? surface->getCanvas() : inputCanvas;
 *             // init our new canvas with the old canvas's matrix
 *             canvas->setMatrix(inputCanvas->getTotalMatrix());
 *         }
 *         canvas->drawColor(SK_ColorWHITE);
 *
 *         SkRect bounds = fBlob->bounds();
 *
 *         const int kPadX = SkScalarFloorToInt(bounds.width() / 3);
 *         const int kPadY = SkScalarFloorToInt(bounds.height() / 3);
 *
 *         int rowCount = 0;
 *         canvas->translate(SkIntToScalar(kPadX), SkIntToScalar(kPadY));
 *         canvas->save();
 *         SkRandom random;
 *
 *         SkPaint paint;
 *         if (!fUseDFT) {
 *             paint.setColor(SK_ColorWHITE);
 *         }
 *         paint.setAntiAlias(false);
 *
 *         const SkScalar kSigma = SkBlurMask::ConvertRadiusToSigma(SkIntToScalar(8));
 *
 *         // setup blur paint
 *         SkPaint blurPaint(paint);
 *         blurPaint.setColor(SK_ColorBLACK);
 *         blurPaint.setMaskFilter(SkMaskFilter::MakeBlur(kNormal_SkBlurStyle, kSigma));
 *
 *         for (int i = 0; i < 4; i++) {
 *             canvas->save();
 *             switch (i % 2) {
 *                 case 0:
 *                     canvas->rotate(random.nextF() * 45.f);
 *                     break;
 *                 case 1:
 *                     canvas->rotate(-random.nextF() * 45.f);
 *                     break;
 *             }
 *             if (!fUseDFT) {
 *                 canvas->drawTextBlob(fBlob, 0, 0, blurPaint);
 *             }
 *             canvas->drawTextBlob(fBlob, 0, 0, paint);
 *             canvas->restore();
 *             canvas->translate(bounds.width() + SK_Scalar1 * kPadX, 0);
 *             ++rowCount;
 *             if ((bounds.width() + 2 * kPadX) * rowCount > kWidth) {
 *                 canvas->restore();
 *                 canvas->translate(0, bounds.height() + SK_Scalar1 * kPadY);
 *                 canvas->save();
 *                 rowCount = 0;
 *             }
 *         }
 *         canvas->restore();
 *
 *         // render offscreen buffer
 *         if (surface) {
 *             SkAutoCanvasRestore acr(inputCanvas, true);
 *             // since we prepended this matrix already, we blit using identity
 *             inputCanvas->resetMatrix();
 *             inputCanvas->drawImage(surface->makeImageSnapshot().get(), 0, 0);
 *         }
 *     }
 *
 * private:
 *     sk_sp<SkTextBlob> fBlob;
 *
 *     static constexpr int kWidth = 2100;
 *     static constexpr int kHeight = 1900;
 *
 *     bool fUseDFT;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class TextBlobMixedSizes public constructor(
  useDFT: Boolean,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTextBlob> fBlob
   * ```
   */
  private var fBlob: SkSp<SkTextBlob> = TODO("Initialize fBlob")

  /**
   * C++ original:
   * ```cpp
   * static constexpr int kWidth = 2100
   * ```
   */
  private var fUseDFT: Boolean = TODO("Initialize fUseDFT")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         SkTextBlobBuilder builder;
   *
   *         // make textblob.  To stress distance fields, we choose sizes appropriately
   *         sk_sp<SkTypeface> tf = ToolUtils::CreateTypefaceFromResource("fonts/HangingS.ttf");
   *         if (!tf) {
   *             tf = ToolUtils::DefaultPortableTypeface();
   *         }
   *         SkFont font(tf, 262);
   *         font.setSubpixel(true);
   *         font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
   *
   *         const char* text = "Skia";
   *
   *         ToolUtils::add_to_text_blob(&builder, text, font, 0, 0);
   *
   *         // large
   *         SkRect bounds;
   *         font.measureText(text, strlen(text), SkTextEncoding::kUTF8, &bounds);
   *         SkScalar yOffset = bounds.height();
   *         font.setSize(162);
   *
   *         ToolUtils::add_to_text_blob(&builder, text, font, 0, yOffset);
   *
   *         // Medium
   *         font.measureText(text, strlen(text), SkTextEncoding::kUTF8, &bounds);
   *         yOffset += bounds.height();
   *         font.setSize(72);
   *
   *         ToolUtils::add_to_text_blob(&builder, text, font, 0, yOffset);
   *
   *         // Small
   *         font.measureText(text, strlen(text), SkTextEncoding::kUTF8, &bounds);
   *         yOffset += bounds.height();
   *         font.setSize(32);
   *
   *         ToolUtils::add_to_text_blob(&builder, text, font, 0, yOffset);
   *
   *         // micro (will fall out of distance field text even if distance field text is enabled)
   *         font.measureText(text, strlen(text), SkTextEncoding::kUTF8, &bounds);
   *         yOffset += bounds.height();
   *         font.setSize(14);
   *
   *         ToolUtils::add_to_text_blob(&builder, text, font, 0, yOffset);
   *
   *         // Zero size.
   *         font.measureText(text, strlen(text), SkTextEncoding::kUTF8, &bounds);
   *         yOffset += bounds.height();
   *         font.setSize(0);
   *
   *         ToolUtils::add_to_text_blob(&builder, text, font, 0, yOffset);
   *
   *         // build
   *         fBlob = builder.make();
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         return SkStringPrintf("textblobmixedsizes%s",
   *                               fUseDFT ? "_df" : "");
   *     }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(kWidth, kHeight); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* inputCanvas) override {
   *         SkCanvas* canvas = inputCanvas;
   *         sk_sp<SkSurface> surface;
   *         if (fUseDFT) {
   *             // Create a new Canvas to enable DFT
   *             SkISize size = this->getISize();
   *             if (!inputCanvas->getBaseLayerSize().isEmpty()) {
   *                 size = inputCanvas->getBaseLayerSize();
   *             }
   *             sk_sp<SkColorSpace> colorSpace = inputCanvas->imageInfo().refColorSpace();
   *             SkImageInfo info = SkImageInfo::MakeN32(size.width(), size.height(),
   *                                                     kPremul_SkAlphaType, colorSpace);
   *             SkSurfaceProps inputProps;
   *             inputCanvas->getProps(&inputProps);
   *             SkSurfaceProps props(
   *                     SkSurfaceProps::kUseDeviceIndependentFonts_Flag | inputProps.flags(),
   *                     inputProps.pixelGeometry());
   * #if defined(SK_GRAPHITE)
   *             if (auto recorder = inputCanvas->recorder()) {
   *                 surface = SkSurfaces::RenderTarget(recorder, info, skgpu::Mipmapped::kNo, &props);
   *             } else
   * #endif
   * #if defined(SK_GANESH)
   *             if (auto ctx = inputCanvas->recordingContext()) {
   *                 surface = SkSurfaces::RenderTarget(ctx, skgpu::Budgeted::kNo, info, 0, &props);
   *             }
   * #endif
   *             canvas = surface ? surface->getCanvas() : inputCanvas;
   *             // init our new canvas with the old canvas's matrix
   *             canvas->setMatrix(inputCanvas->getTotalMatrix());
   *         }
   *         canvas->drawColor(SK_ColorWHITE);
   *
   *         SkRect bounds = fBlob->bounds();
   *
   *         const int kPadX = SkScalarFloorToInt(bounds.width() / 3);
   *         const int kPadY = SkScalarFloorToInt(bounds.height() / 3);
   *
   *         int rowCount = 0;
   *         canvas->translate(SkIntToScalar(kPadX), SkIntToScalar(kPadY));
   *         canvas->save();
   *         SkRandom random;
   *
   *         SkPaint paint;
   *         if (!fUseDFT) {
   *             paint.setColor(SK_ColorWHITE);
   *         }
   *         paint.setAntiAlias(false);
   *
   *         const SkScalar kSigma = SkBlurMask::ConvertRadiusToSigma(SkIntToScalar(8));
   *
   *         // setup blur paint
   *         SkPaint blurPaint(paint);
   *         blurPaint.setColor(SK_ColorBLACK);
   *         blurPaint.setMaskFilter(SkMaskFilter::MakeBlur(kNormal_SkBlurStyle, kSigma));
   *
   *         for (int i = 0; i < 4; i++) {
   *             canvas->save();
   *             switch (i % 2) {
   *                 case 0:
   *                     canvas->rotate(random.nextF() * 45.f);
   *                     break;
   *                 case 1:
   *                     canvas->rotate(-random.nextF() * 45.f);
   *                     break;
   *             }
   *             if (!fUseDFT) {
   *                 canvas->drawTextBlob(fBlob, 0, 0, blurPaint);
   *             }
   *             canvas->drawTextBlob(fBlob, 0, 0, paint);
   *             canvas->restore();
   *             canvas->translate(bounds.width() + SK_Scalar1 * kPadX, 0);
   *             ++rowCount;
   *             if ((bounds.width() + 2 * kPadX) * rowCount > kWidth) {
   *                 canvas->restore();
   *                 canvas->translate(0, bounds.height() + SK_Scalar1 * kPadY);
   *                 canvas->save();
   *                 rowCount = 0;
   *             }
   *         }
   *         canvas->restore();
   *
   *         // render offscreen buffer
   *         if (surface) {
   *             SkAutoCanvasRestore acr(inputCanvas, true);
   *             // since we prepended this matrix already, we blit using identity
   *             inputCanvas->resetMatrix();
   *             inputCanvas->drawImage(surface->makeImageSnapshot().get(), 0, 0);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(inputCanvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    private val kWidth: Int = TODO("Initialize kWidth")

    private val kHeight: Int = TODO("Initialize kHeight")
  }
}
