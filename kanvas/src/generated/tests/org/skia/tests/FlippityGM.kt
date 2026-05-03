package org.skia.tests

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.math.SkISize
import org.skia.tools.GraphiteTestContext

/**
 * C++ original:
 * ```cpp
 * class FlippityGM : public skiagm::GM {
 * public:
 *     FlippityGM() {
 *         this->setBGColor(0xFFCCCCCC);
 *     }
 *
 * private:
 *     SkString getName() const override { return SkString("flippity"); }
 *
 *     SkISize getISize() override { return SkISize::Make(kGMWidth, kGMHeight); }
 *
 *     // Draw the reference image and the four corner labels in the matrix's coordinate space
 *     void drawImageWithMatrixAndLabels(SkCanvas* canvas, SkImage* image, int matIndex,
 *                                       bool drawSubset, bool drawScaled) {
 *         static const SkRect kSubsets[kNumMatrices] = {
 *             SkRect::MakeXYWH(kInset, 0, kImageSize-kInset, kImageSize),
 *             SkRect::MakeXYWH(0, kInset, kImageSize, kImageSize-kInset),
 *             SkRect::MakeXYWH(0, 0, kImageSize-kInset, kImageSize),
 *             SkRect::MakeXYWH(0, 0, kImageSize, kImageSize-kInset),
 *             SkRect::MakeXYWH(kInset/2, kInset/2, kImageSize-kInset, kImageSize-kInset),
 *             SkRect::MakeXYWH(kInset, kInset, kImageSize-2*kInset, kImageSize-2*kInset),
 *         };
 *
 *         SkMatrix imageGeomMat;
 *         SkAssertResult(UVMatToGeomMatForImage(&imageGeomMat, kUVMatrices[matIndex]));
 *
 *         canvas->save();
 *
 *             // draw the reference image
 *             canvas->concat(imageGeomMat);
 *             if (drawSubset) {
 *                 canvas->drawImageRect(image, kSubsets[matIndex],
 *                                       drawScaled ? SkRect::MakeWH(kImageSize, kImageSize)
 *                                                  : kSubsets[matIndex],
 *                                       SkSamplingOptions(), nullptr,
 *                                       SkCanvas::kFast_SrcRectConstraint);
 *             } else {
 *                 canvas->drawImage(image, 0, 0);
 *             }
 *
 *             // draw the labels
 *             for (int i = 0; i < kNumLabels; ++i) {
 *                 canvas->drawImage(fLabels[i],
 *                                     0.0f == kPoints[i].fX ? -kLabelSize : kPoints[i].fX,
 *                                     0.0f == kPoints[i].fY ? -kLabelSize : kPoints[i].fY);
 *             }
 *         canvas->restore();
 *     }
 *
 *     void drawRow(SkCanvas* canvas, bool bottomLeftImage, bool drawSubset, bool drawScaled) {
 *
 *         canvas->save();
 *             canvas->translate(kLabelSize, kLabelSize);
 *
 *             for (int i = 0; i < kNumMatrices; ++i) {
 *                 this->drawImageWithMatrixAndLabels(canvas, fReferenceImages[bottomLeftImage].get(),
 *                                                    i, drawSubset, drawScaled);
 *                 canvas->translate(kCellSize, 0);
 *             }
 *         canvas->restore();
 *     }
 *
 *     void makeLabels() {
 *         if (fLabels.size()) {
 *             return;
 *         }
 *
 *         static const char* kLabelText[kNumLabels] = { "LL", "LR", "UL", "UR" };
 *
 *         static const SkColor kLabelColors[kNumLabels] = {
 *             SK_ColorRED,
 *             SK_ColorGREEN,
 *             SK_ColorBLUE,
 *             SK_ColorCYAN
 *         };
 *
 *         for (int i = 0; i < kNumLabels; ++i) {
 *             fLabels.push_back(make_text_image(kLabelText[i], kLabelColors[i]));
 *         }
 *         SkASSERT(kNumLabels == fLabels.size());
 *     }
 *
 *     DrawResult onGpuSetup(SkCanvas* canvas, SkString* errorMsg, GraphiteTestContext*) override {
 *         this->makeLabels();
 *         fReferenceImages[0] = make_reference_image(canvas, fLabels, false);
 *         fReferenceImages[1] = make_reference_image(canvas, fLabels, true);
 *         if (!fReferenceImages[0] || !fReferenceImages[1]) {
 *             *errorMsg = "Failed to create reference images.";
 *             return DrawResult::kFail;
 *         }
 *
 *         return DrawResult::kOk;
 *     }
 *
 *     void onGpuTeardown() override {
 *         fLabels.clear();
 *         fReferenceImages[0] = fReferenceImages[1] = nullptr;
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkASSERT(fReferenceImages[0] && fReferenceImages[1]);
 *
 *         canvas->save();
 *
 *         // Top row gets TL image
 *         this->drawRow(canvas, false, false, false);
 *
 *         canvas->translate(0, kCellSize);
 *
 *         // Bottom row gets BL image
 *         this->drawRow(canvas, true, false, false);
 *
 *         canvas->translate(0, kCellSize);
 *
 *         // Third row gets subsets of BL images
 *         this->drawRow(canvas, true, true, false);
 *
 *         canvas->translate(0, kCellSize);
 *
 *         // Fourth row gets scaled subsets of BL images
 *         this->drawRow(canvas, true, true, true);
 *
 *         canvas->restore();
 *
 *         // separator grid
 *         for (int i = 0; i < 4; ++i) {
 *             canvas->drawLine(0, i * kCellSize, kGMWidth, i * kCellSize, SkPaint());
 *         }
 *         for (int i = 0; i < kNumMatrices; ++i) {
 *             canvas->drawLine(i * kCellSize, 0, i * kCellSize, kGMHeight, SkPaint());
 *         }
 *     }
 *
 * private:
 *     TArray<sk_sp<SkImage>> fLabels;
 *     sk_sp<SkImage> fReferenceImages[2];
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class FlippityGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * TArray<sk_sp<SkImage>> fLabels
   * ```
   */
  private var fLabels: Int = TODO("Initialize fLabels")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fReferenceImages[2]
   * ```
   */
  private var fReferenceImages: Array<SkSp<SkImage>> = TODO("Initialize fReferenceImages")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("flippity"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(kGMWidth, kGMHeight); }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawImageWithMatrixAndLabels(SkCanvas* canvas, SkImage* image, int matIndex,
   *                                       bool drawSubset, bool drawScaled) {
   *         static const SkRect kSubsets[kNumMatrices] = {
   *             SkRect::MakeXYWH(kInset, 0, kImageSize-kInset, kImageSize),
   *             SkRect::MakeXYWH(0, kInset, kImageSize, kImageSize-kInset),
   *             SkRect::MakeXYWH(0, 0, kImageSize-kInset, kImageSize),
   *             SkRect::MakeXYWH(0, 0, kImageSize, kImageSize-kInset),
   *             SkRect::MakeXYWH(kInset/2, kInset/2, kImageSize-kInset, kImageSize-kInset),
   *             SkRect::MakeXYWH(kInset, kInset, kImageSize-2*kInset, kImageSize-2*kInset),
   *         };
   *
   *         SkMatrix imageGeomMat;
   *         SkAssertResult(UVMatToGeomMatForImage(&imageGeomMat, kUVMatrices[matIndex]));
   *
   *         canvas->save();
   *
   *             // draw the reference image
   *             canvas->concat(imageGeomMat);
   *             if (drawSubset) {
   *                 canvas->drawImageRect(image, kSubsets[matIndex],
   *                                       drawScaled ? SkRect::MakeWH(kImageSize, kImageSize)
   *                                                  : kSubsets[matIndex],
   *                                       SkSamplingOptions(), nullptr,
   *                                       SkCanvas::kFast_SrcRectConstraint);
   *             } else {
   *                 canvas->drawImage(image, 0, 0);
   *             }
   *
   *             // draw the labels
   *             for (int i = 0; i < kNumLabels; ++i) {
   *                 canvas->drawImage(fLabels[i],
   *                                     0.0f == kPoints[i].fX ? -kLabelSize : kPoints[i].fX,
   *                                     0.0f == kPoints[i].fY ? -kLabelSize : kPoints[i].fY);
   *             }
   *         canvas->restore();
   *     }
   * ```
   */
  private fun drawImageWithMatrixAndLabels(
    canvas: SkCanvas?,
    image: SkImage?,
    matIndex: Int,
    drawSubset: Boolean,
    drawScaled: Boolean,
  ) {
    TODO("Implement drawImageWithMatrixAndLabels")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawRow(SkCanvas* canvas, bool bottomLeftImage, bool drawSubset, bool drawScaled) {
   *
   *         canvas->save();
   *             canvas->translate(kLabelSize, kLabelSize);
   *
   *             for (int i = 0; i < kNumMatrices; ++i) {
   *                 this->drawImageWithMatrixAndLabels(canvas, fReferenceImages[bottomLeftImage].get(),
   *                                                    i, drawSubset, drawScaled);
   *                 canvas->translate(kCellSize, 0);
   *             }
   *         canvas->restore();
   *     }
   * ```
   */
  private fun drawRow(
    canvas: SkCanvas?,
    bottomLeftImage: Boolean,
    drawSubset: Boolean,
    drawScaled: Boolean,
  ) {
    TODO("Implement drawRow")
  }

  /**
   * C++ original:
   * ```cpp
   * void makeLabels() {
   *         if (fLabels.size()) {
   *             return;
   *         }
   *
   *         static const char* kLabelText[kNumLabels] = { "LL", "LR", "UL", "UR" };
   *
   *         static const SkColor kLabelColors[kNumLabels] = {
   *             SK_ColorRED,
   *             SK_ColorGREEN,
   *             SK_ColorBLUE,
   *             SK_ColorCYAN
   *         };
   *
   *         for (int i = 0; i < kNumLabels; ++i) {
   *             fLabels.push_back(make_text_image(kLabelText[i], kLabelColors[i]));
   *         }
   *         SkASSERT(kNumLabels == fLabels.size());
   *     }
   * ```
   */
  private fun makeLabels() {
    TODO("Implement makeLabels")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onGpuSetup(SkCanvas* canvas, SkString* errorMsg, GraphiteTestContext*) override {
   *         this->makeLabels();
   *         fReferenceImages[0] = make_reference_image(canvas, fLabels, false);
   *         fReferenceImages[1] = make_reference_image(canvas, fLabels, true);
   *         if (!fReferenceImages[0] || !fReferenceImages[1]) {
   *             *errorMsg = "Failed to create reference images.";
   *             return DrawResult::kFail;
   *         }
   *
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  public override fun onGpuSetup(
    canvas: SkCanvas?,
    errorMsg: String?,
    param2: GraphiteTestContext?,
  ): DrawResult {
    TODO("Implement onGpuSetup")
  }

  /**
   * C++ original:
   * ```cpp
   * void onGpuTeardown() override {
   *         fLabels.clear();
   *         fReferenceImages[0] = fReferenceImages[1] = nullptr;
   *     }
   * ```
   */
  public override fun onGpuTeardown() {
    TODO("Implement onGpuTeardown")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkASSERT(fReferenceImages[0] && fReferenceImages[1]);
   *
   *         canvas->save();
   *
   *         // Top row gets TL image
   *         this->drawRow(canvas, false, false, false);
   *
   *         canvas->translate(0, kCellSize);
   *
   *         // Bottom row gets BL image
   *         this->drawRow(canvas, true, false, false);
   *
   *         canvas->translate(0, kCellSize);
   *
   *         // Third row gets subsets of BL images
   *         this->drawRow(canvas, true, true, false);
   *
   *         canvas->translate(0, kCellSize);
   *
   *         // Fourth row gets scaled subsets of BL images
   *         this->drawRow(canvas, true, true, true);
   *
   *         canvas->restore();
   *
   *         // separator grid
   *         for (int i = 0; i < 4; ++i) {
   *             canvas->drawLine(0, i * kCellSize, kGMWidth, i * kCellSize, SkPaint());
   *         }
   *         for (int i = 0; i < kNumMatrices; ++i) {
   *             canvas->drawLine(i * kCellSize, 0, i * kCellSize, kGMHeight, SkPaint());
   *         }
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
