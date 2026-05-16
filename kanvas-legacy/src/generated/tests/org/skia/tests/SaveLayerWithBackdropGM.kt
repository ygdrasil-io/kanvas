package org.skia.tests

import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class SaveLayerWithBackdropGM : public skiagm::GM {
 * protected:
 *     bool runAsBench() const override { return true; }
 *     SkString getName() const override { return SkString("savelayer_with_backdrop"); }
 *     SkISize getISize() override { return SkISize::Make(830, 550); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkColorMatrix cm;
 *         cm.setSaturation(10);
 *         sk_sp<SkColorFilter> cf(SkColorFilters::Matrix(cm));
 *         const SkScalar kernel[] = { 4, 0, 4, 0, -15, 0, 4, 0, 4 };
 *         sk_sp<SkImageFilter> filters[] = {
 *             SkImageFilters::Blur(10, 10, nullptr),
 *             SkImageFilters::Dilate(8, 8, nullptr),
 *             SkImageFilters::MatrixConvolution({ 3, 3 }, kernel, 1, 0, { 0, 0 },
 *                                               SkTileMode::kDecal, true, nullptr),
 *             SkImageFilters::ColorFilter(std::move(cf), nullptr),
 *         };
 *
 *         const struct {
 *             SkScalar    fSx, fSy, fTx, fTy;
 *         } xforms[] = {
 *             { 1, 1, 0, 0 },
 *             { 0.5f, 0.5f, 530, 0 },
 *             { 0.25f, 0.25f, 530, 275 },
 *             { 0.125f, 0.125f, 530, 420 },
 *         };
 *
 *         SkSamplingOptions sampling(SkFilterMode::kLinear,
 *                                    SkMipmapMode::kLinear);
 *         sk_sp<SkImage> image(ToolUtils::GetResourceAsImage("images/mandrill_512.png"));
 *
 *         canvas->translate(20, 20);
 *         for (const auto& xform : xforms) {
 *             canvas->save();
 *             canvas->translate(xform.fTx, xform.fTy);
 *             canvas->scale(xform.fSx, xform.fSy);
 *             canvas->drawImage(image, 0, 0, sampling, nullptr);
 *             draw_set(canvas, filters, std::size(filters));
 *             canvas->restore();
 *         }
 *     }
 * }
 * ```
 */
public open class SaveLayerWithBackdropGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * bool runAsBench() const override { return true; }
   * ```
   */
  protected override fun runAsBench(): Boolean {
    TODO("Implement runAsBench")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("savelayer_with_backdrop"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(830, 550); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkColorMatrix cm;
   *         cm.setSaturation(10);
   *         sk_sp<SkColorFilter> cf(SkColorFilters::Matrix(cm));
   *         const SkScalar kernel[] = { 4, 0, 4, 0, -15, 0, 4, 0, 4 };
   *         sk_sp<SkImageFilter> filters[] = {
   *             SkImageFilters::Blur(10, 10, nullptr),
   *             SkImageFilters::Dilate(8, 8, nullptr),
   *             SkImageFilters::MatrixConvolution({ 3, 3 }, kernel, 1, 0, { 0, 0 },
   *                                               SkTileMode::kDecal, true, nullptr),
   *             SkImageFilters::ColorFilter(std::move(cf), nullptr),
   *         };
   *
   *         const struct {
   *             SkScalar    fSx, fSy, fTx, fTy;
   *         } xforms[] = {
   *             { 1, 1, 0, 0 },
   *             { 0.5f, 0.5f, 530, 0 },
   *             { 0.25f, 0.25f, 530, 275 },
   *             { 0.125f, 0.125f, 530, 420 },
   *         };
   *
   *         SkSamplingOptions sampling(SkFilterMode::kLinear,
   *                                    SkMipmapMode::kLinear);
   *         sk_sp<SkImage> image(ToolUtils::GetResourceAsImage("images/mandrill_512.png"));
   *
   *         canvas->translate(20, 20);
   *         for (const auto& xform : xforms) {
   *             canvas->save();
   *             canvas->translate(xform.fTx, xform.fTy);
   *             canvas->scale(xform.fSx, xform.fSy);
   *             canvas->drawImage(image, 0, 0, sampling, nullptr);
   *             draw_set(canvas, filters, std::size(filters));
   *             canvas->restore();
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
