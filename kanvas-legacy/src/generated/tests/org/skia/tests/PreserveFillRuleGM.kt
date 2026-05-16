package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class PreserveFillRuleGM : public GM {
 * public:
 *     PreserveFillRuleGM(bool big) : fBig(big) , fStarSize((big) ? 200 : 20) {}
 *
 * private:
 *     SkString getName() const override {
 *         SkString name("preservefillrule");
 *         name += (fBig) ? "_big" : "_little";
 *         return name;
 *     }
 *     SkISize getISize() override { return SkISize::Make(fStarSize * 2, fStarSize * 2); }
 *
 * #if defined(SK_GANESH)
 *     void modifyGrContextOptions(GrContextOptions* ctxOptions) override {
 *         ctxOptions->fAllowPathMaskCaching = true;
 *     }
 * #endif
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         auto starRect = SkRect::MakeWH(fStarSize, fStarSize);
 *         SkPath star7_winding = ToolUtils::make_star(starRect, 7);
 *         star7_winding.setFillType(SkPathFillType::kWinding);
 *
 *         SkPath star7_evenOdd = star7_winding.makeTransform(SkMatrix::Translate(0, fStarSize))
 *                                             .makeFillType(SkPathFillType::kEvenOdd);
 *
 *         SkPath star5_winding = ToolUtils::make_star(starRect, 5)
 *                                .makeTransform(SkMatrix::Translate(fStarSize, 0))
 *                                .makeFillType(SkPathFillType::kWinding);
 *
 *         SkPath star5_evenOdd = star5_winding.makeTransform(SkMatrix::Translate(0, fStarSize))
 *                                             .makeFillType(SkPathFillType::kEvenOdd);
 *
 *         SkPaint paint;
 *         paint.setColor(SK_ColorGREEN);
 *         paint.setAntiAlias(true);
 *
 *         canvas->clear(SK_ColorWHITE);
 *         canvas->drawPath(star7_winding, paint);
 *         canvas->drawPath(star7_evenOdd, paint);
 *         canvas->drawPath(star5_winding, paint);
 *         canvas->drawPath(star5_evenOdd, paint);
 *
 * #if defined(SK_GANESH)
 *         if (auto dContext = GrAsDirectContext(canvas->recordingContext())) {
 *             dContext->flush();
 *         }
 * #endif
 *     }
 *
 * private:
 *     const bool fBig;
 *     const int fStarSize;
 * }
 * ```
 */
public open class PreserveFillRuleGM public constructor(
  big: Boolean,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * const bool fBig
   * ```
   */
  private val fBig: Boolean = TODO("Initialize fBig")

  /**
   * C++ original:
   * ```cpp
   * const int fStarSize
   * ```
   */
  private val fStarSize: Int = TODO("Initialize fStarSize")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         SkString name("preservefillrule");
   *         name += (fBig) ? "_big" : "_little";
   *         return name;
   *     }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(fStarSize * 2, fStarSize * 2); }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         auto starRect = SkRect::MakeWH(fStarSize, fStarSize);
   *         SkPath star7_winding = ToolUtils::make_star(starRect, 7);
   *         star7_winding.setFillType(SkPathFillType::kWinding);
   *
   *         SkPath star7_evenOdd = star7_winding.makeTransform(SkMatrix::Translate(0, fStarSize))
   *                                             .makeFillType(SkPathFillType::kEvenOdd);
   *
   *         SkPath star5_winding = ToolUtils::make_star(starRect, 5)
   *                                .makeTransform(SkMatrix::Translate(fStarSize, 0))
   *                                .makeFillType(SkPathFillType::kWinding);
   *
   *         SkPath star5_evenOdd = star5_winding.makeTransform(SkMatrix::Translate(0, fStarSize))
   *                                             .makeFillType(SkPathFillType::kEvenOdd);
   *
   *         SkPaint paint;
   *         paint.setColor(SK_ColorGREEN);
   *         paint.setAntiAlias(true);
   *
   *         canvas->clear(SK_ColorWHITE);
   *         canvas->drawPath(star7_winding, paint);
   *         canvas->drawPath(star7_evenOdd, paint);
   *         canvas->drawPath(star5_winding, paint);
   *         canvas->drawPath(star5_evenOdd, paint);
   *
   * #if defined(SK_GANESH)
   *         if (auto dContext = GrAsDirectContext(canvas->recordingContext())) {
   *             dContext->flush();
   *         }
   * #endif
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
