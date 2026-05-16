package org.skia.tests

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkMaskFilter
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class BlurCirclesGM : public skiagm::GM {
 * public:
 *     BlurCirclesGM() { }
 *
 * protected:
 *     bool runAsBench() const override { return true; }
 *
 *     SkString getName() const override { return SkString("blurcircles"); }
 *
 *     SkISize getISize() override { return SkISize::Make(950, 950); }
 *
 *     void onOnceBeforeDraw() override {
 *         const float blurRadii[kNumBlurs] = {1.f, 5.f, 10.f, 20.f};
 *
 *         for (int i = 0; i < kNumBlurs; ++i) {
 *             fBlurFilters[i] = SkMaskFilter::MakeBlur(
 *                                     kNormal_SkBlurStyle,
 *                                     SkBlurMask::ConvertRadiusToSigma(blurRadii[i]));
 *         }
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->scale(1.5f, 1.5f);
 *         canvas->translate(50,50);
 *
 *         const float circleRadii[] = {5.f, 10.f, 25.f, 50.f};
 *
 *         for (size_t i = 0; i < kNumBlurs; ++i) {
 *             SkAutoCanvasRestore autoRestore(canvas, true);
 *             canvas->translate(0, 150.f*i);
 *             for (size_t j = 0; j < std::size(circleRadii); ++j) {
 *                 SkPaint paint;
 *                 paint.setColor(SK_ColorBLACK);
 *                 paint.setMaskFilter(fBlurFilters[i]);
 *
 *                 static constexpr SkPoint kCenter = {50.f, 50.f};
 *                 // Throw a rotation in the mix to make sure GPU fast path handles it correctly.
 *                 canvas->save();
 *                 canvas->rotate(j*22.f, kCenter.fX, kCenter.fY);
 *                 canvas->drawCircle(kCenter, circleRadii[j], paint);
 *                 canvas->restore();
 *                 canvas->translate(150.f, 0.f);
 *             }
 *         }
 *     }
 * private:
 *     inline static constexpr int kNumBlurs = 4;
 *
 *     sk_sp<SkMaskFilter> fBlurFilters[kNumBlurs];
 *
 *     using INHERITED =         skiagm::GM;
 * }
 * ```
 */
public open class BlurCirclesGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kNumBlurs = 4
   * ```
   */
  private var fBlurFilters: Array<SkSp<SkMaskFilter>> = TODO("Initialize fBlurFilters")

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
   * SkString getName() const override { return SkString("blurcircles"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(950, 950); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         const float blurRadii[kNumBlurs] = {1.f, 5.f, 10.f, 20.f};
   *
   *         for (int i = 0; i < kNumBlurs; ++i) {
   *             fBlurFilters[i] = SkMaskFilter::MakeBlur(
   *                                     kNormal_SkBlurStyle,
   *                                     SkBlurMask::ConvertRadiusToSigma(blurRadii[i]));
   *         }
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
   *         canvas->scale(1.5f, 1.5f);
   *         canvas->translate(50,50);
   *
   *         const float circleRadii[] = {5.f, 10.f, 25.f, 50.f};
   *
   *         for (size_t i = 0; i < kNumBlurs; ++i) {
   *             SkAutoCanvasRestore autoRestore(canvas, true);
   *             canvas->translate(0, 150.f*i);
   *             for (size_t j = 0; j < std::size(circleRadii); ++j) {
   *                 SkPaint paint;
   *                 paint.setColor(SK_ColorBLACK);
   *                 paint.setMaskFilter(fBlurFilters[i]);
   *
   *                 static constexpr SkPoint kCenter = {50.f, 50.f};
   *                 // Throw a rotation in the mix to make sure GPU fast path handles it correctly.
   *                 canvas->save();
   *                 canvas->rotate(j*22.f, kCenter.fX, kCenter.fY);
   *                 canvas->drawCircle(kCenter, circleRadii[j], paint);
   *                 canvas->restore();
   *                 canvas->translate(150.f, 0.f);
   *             }
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    private val kNumBlurs: Int = TODO("Initialize kNumBlurs")
  }
}
