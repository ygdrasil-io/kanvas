package org.skia.tests

import kotlin.Boolean
import kotlin.Double
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize
import org.skia.math.SkRandom
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class BlurCircles2GM : public skiagm::GM {
 * public:
 *     BlurCircles2GM() {
 *         fAnimRadius = TimeUtils::PingPong(
 *                 0, kRadiusPingPoingPeriod, kRadiusPingPoingShift, kMinRadius, kMaxRadius);
 *         fAnimBlurRadius = TimeUtils::PingPong(0,
 *                                               kBlurRadiusPingPoingPeriod,
 *                                               kBlurRadiusPingPoingShift,
 *                                               kMinBlurRadius,
 *                                               kMaxBlurRadius);
 *     }
 *
 * protected:
 *     bool runAsBench() const override { return true; }
 *
 *     SkString getName() const override { return SkString("blurcircles2"); }
 *
 *     SkISize getISize() override { return SkISize::Make(730, 1350); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         constexpr SkScalar kMaxR = kMaxRadius + kMaxBlurRadius;
 *
 *         auto almostCircleMaker = [] (SkScalar radius) {
 *             return SkPathBuilder().addArc(SkRect::MakeXYWH(-radius, -radius, 2 * radius, 2 * radius), 0, 355)
 *                                   .setIsVolatile(true)
 *                                   .close()
 *                                   .detach();
 *         };
 *
 *         auto blurMaker = [] (SkScalar radius) ->sk_sp<SkMaskFilter> {
 *             return SkMaskFilter::MakeBlur(kNormal_SkBlurStyle,
 *                                           SkBlurMask::ConvertRadiusToSigma(radius));
 *         };
 *
 *         SkPaint paint;
 *         paint.setColor(SK_ColorBLACK);
 *
 *         if (this->getMode() == kSample_Mode) {
 *             paint.setMaskFilter(blurMaker(fAnimBlurRadius));
 *             SkISize size = canvas->getBaseLayerSize();
 *             SkPath almostCircle = almostCircleMaker(fAnimRadius);
 *             canvas->save();
 *                 canvas->translate(size.fWidth / 2.f, size.fHeight / 4.f);
 *                 canvas->drawCircle(0, 0, fAnimRadius, paint);
 *                 canvas->translate(0, 2 * kMaxR);
 *                 canvas->drawPath(almostCircle, paint);
 *             canvas->restore();
 *         } else {
 *             bool benchMode = this->getMode() == kBench_Mode;
 *             canvas->save();
 *             constexpr SkScalar kPad = 5;
 *             constexpr SkScalar kRadiusSteps = 5;
 *             constexpr SkScalar kBlurRadiusSteps = 5;
 *             canvas->translate(kPad + kMinRadius + kMaxBlurRadius,
 *                               kPad + kMinRadius + kMaxBlurRadius);
 *             constexpr SkScalar kDeltaRadius = (kMaxRadius - kMinRadius) / kRadiusSteps;
 *             constexpr SkScalar kDeltaBlurRadius = (kMaxBlurRadius - kMinBlurRadius) /
 *                                                          kBlurRadiusSteps;
 *             SkScalar lineWidth = 0;
 *             if (!benchMode) {
 *                 for (int r = 0; r < kRadiusSteps - 1; ++r) {
 *                     const SkScalar radius = r * kDeltaRadius + kMinRadius;
 *                     lineWidth += 2 * (radius + kMaxBlurRadius) + kPad;
 *                 }
 *             }
 *             for (int br = 0; br < kBlurRadiusSteps; ++br) {
 *                 SkScalar blurRadius = br * kDeltaBlurRadius + kMinBlurRadius;
 *                 if (benchMode) {
 *                     blurRadius += fRandom.nextSScalar1() * kDeltaBlurRadius;
 *                 }
 *                 const SkScalar maxRowR = blurRadius + kMaxRadius;
 *                 paint.setMaskFilter(blurMaker(blurRadius));
 *                 canvas->save();
 *                 for (int r = 0; r < kRadiusSteps; ++r) {
 *                     SkScalar radius = r * kDeltaRadius + kMinRadius;
 *                     if (benchMode) {
 *                         radius += fRandom.nextSScalar1() * kDeltaRadius;
 *                     }
 *                     SkPath almostCircle;
 *                     if (!benchMode) {
 *                         almostCircle = almostCircleMaker(radius);
 *                     }
 *                     canvas->save();
 *                         canvas->drawCircle(0, 0, radius, paint);
 *                         canvas->translate(0, 2 * maxRowR + kPad);
 *                     if (!benchMode) {
 *                         canvas->drawPath(almostCircle, paint);
 *                     }
 *                     canvas->restore();
 *                     const SkScalar maxColR = radius + kMaxBlurRadius;
 *                     canvas->translate(maxColR * 2 + kPad, 0);
 *                 }
 *                 canvas->restore();
 *                 if (!benchMode) {
 *                     SkPaint blackPaint;
 *                     blackPaint.setColor(SK_ColorBLACK);
 *                     const SkScalar lineY = 3 * maxRowR + 1.5f * kPad;
 *                     if (br != kBlurRadiusSteps - 1) {
 *                         canvas->drawLine(0, lineY, lineWidth, lineY, blackPaint);
 *                     }
 *                 }
 *                 canvas->translate(0, maxRowR * 4 + 2 * kPad);
 *             }
 *             canvas->restore();
 *         }
 *     }
 *
 *     bool onAnimate(double nanos) override {
 *         fAnimRadius = TimeUtils::PingPong(1e-9 * nanos, kRadiusPingPoingPeriod, kRadiusPingPoingShift, kMinRadius,
 *                                      kMaxRadius);
 *         fAnimBlurRadius = TimeUtils::PingPong(1e-9 * nanos, kBlurRadiusPingPoingPeriod, kBlurRadiusPingPoingShift,
 *                                          kMinBlurRadius, kMaxBlurRadius);
 *         return true;
 *     }
 *
 * private:
 *     inline static constexpr SkScalar kMinRadius = 15;
 *     inline static constexpr SkScalar kMaxRadius = 45;
 *     inline static constexpr SkScalar kRadiusPingPoingPeriod = 8;
 *     inline static constexpr SkScalar kRadiusPingPoingShift = 3;
 *
 *     inline static constexpr SkScalar kMinBlurRadius = 5;
 *     inline static constexpr SkScalar kMaxBlurRadius = 45;
 *     inline static constexpr SkScalar kBlurRadiusPingPoingPeriod = 3;
 *     inline static constexpr SkScalar kBlurRadiusPingPoingShift = 1.5;
 *
 *     SkScalar    fAnimRadius;
 *     SkScalar    fAnimBlurRadius;
 *
 *     SkRandom    fRandom;
 *
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class BlurCircles2GM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr SkScalar kMinRadius = 15
   * ```
   */
  private var fAnimRadius: SkScalar = TODO("Initialize fAnimRadius")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr SkScalar kMaxRadius = 45
   * ```
   */
  private var fAnimBlurRadius: SkScalar = TODO("Initialize fAnimBlurRadius")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr SkScalar kRadiusPingPoingPeriod = 8
   * ```
   */
  private var fRandom: SkRandom = TODO("Initialize fRandom")

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
   * SkString getName() const override { return SkString("blurcircles2"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(730, 1350); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         constexpr SkScalar kMaxR = kMaxRadius + kMaxBlurRadius;
   *
   *         auto almostCircleMaker = [] (SkScalar radius) {
   *             return SkPathBuilder().addArc(SkRect::MakeXYWH(-radius, -radius, 2 * radius, 2 * radius), 0, 355)
   *                                   .setIsVolatile(true)
   *                                   .close()
   *                                   .detach();
   *         };
   *
   *         auto blurMaker = [] (SkScalar radius) ->sk_sp<SkMaskFilter> {
   *             return SkMaskFilter::MakeBlur(kNormal_SkBlurStyle,
   *                                           SkBlurMask::ConvertRadiusToSigma(radius));
   *         };
   *
   *         SkPaint paint;
   *         paint.setColor(SK_ColorBLACK);
   *
   *         if (this->getMode() == kSample_Mode) {
   *             paint.setMaskFilter(blurMaker(fAnimBlurRadius));
   *             SkISize size = canvas->getBaseLayerSize();
   *             SkPath almostCircle = almostCircleMaker(fAnimRadius);
   *             canvas->save();
   *                 canvas->translate(size.fWidth / 2.f, size.fHeight / 4.f);
   *                 canvas->drawCircle(0, 0, fAnimRadius, paint);
   *                 canvas->translate(0, 2 * kMaxR);
   *                 canvas->drawPath(almostCircle, paint);
   *             canvas->restore();
   *         } else {
   *             bool benchMode = this->getMode() == kBench_Mode;
   *             canvas->save();
   *             constexpr SkScalar kPad = 5;
   *             constexpr SkScalar kRadiusSteps = 5;
   *             constexpr SkScalar kBlurRadiusSteps = 5;
   *             canvas->translate(kPad + kMinRadius + kMaxBlurRadius,
   *                               kPad + kMinRadius + kMaxBlurRadius);
   *             constexpr SkScalar kDeltaRadius = (kMaxRadius - kMinRadius) / kRadiusSteps;
   *             constexpr SkScalar kDeltaBlurRadius = (kMaxBlurRadius - kMinBlurRadius) /
   *                                                          kBlurRadiusSteps;
   *             SkScalar lineWidth = 0;
   *             if (!benchMode) {
   *                 for (int r = 0; r < kRadiusSteps - 1; ++r) {
   *                     const SkScalar radius = r * kDeltaRadius + kMinRadius;
   *                     lineWidth += 2 * (radius + kMaxBlurRadius) + kPad;
   *                 }
   *             }
   *             for (int br = 0; br < kBlurRadiusSteps; ++br) {
   *                 SkScalar blurRadius = br * kDeltaBlurRadius + kMinBlurRadius;
   *                 if (benchMode) {
   *                     blurRadius += fRandom.nextSScalar1() * kDeltaBlurRadius;
   *                 }
   *                 const SkScalar maxRowR = blurRadius + kMaxRadius;
   *                 paint.setMaskFilter(blurMaker(blurRadius));
   *                 canvas->save();
   *                 for (int r = 0; r < kRadiusSteps; ++r) {
   *                     SkScalar radius = r * kDeltaRadius + kMinRadius;
   *                     if (benchMode) {
   *                         radius += fRandom.nextSScalar1() * kDeltaRadius;
   *                     }
   *                     SkPath almostCircle;
   *                     if (!benchMode) {
   *                         almostCircle = almostCircleMaker(radius);
   *                     }
   *                     canvas->save();
   *                         canvas->drawCircle(0, 0, radius, paint);
   *                         canvas->translate(0, 2 * maxRowR + kPad);
   *                     if (!benchMode) {
   *                         canvas->drawPath(almostCircle, paint);
   *                     }
   *                     canvas->restore();
   *                     const SkScalar maxColR = radius + kMaxBlurRadius;
   *                     canvas->translate(maxColR * 2 + kPad, 0);
   *                 }
   *                 canvas->restore();
   *                 if (!benchMode) {
   *                     SkPaint blackPaint;
   *                     blackPaint.setColor(SK_ColorBLACK);
   *                     const SkScalar lineY = 3 * maxRowR + 1.5f * kPad;
   *                     if (br != kBlurRadiusSteps - 1) {
   *                         canvas->drawLine(0, lineY, lineWidth, lineY, blackPaint);
   *                     }
   *                 }
   *                 canvas->translate(0, maxRowR * 4 + 2 * kPad);
   *             }
   *             canvas->restore();
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onAnimate(double nanos) override {
   *         fAnimRadius = TimeUtils::PingPong(1e-9 * nanos, kRadiusPingPoingPeriod, kRadiusPingPoingShift, kMinRadius,
   *                                      kMaxRadius);
   *         fAnimBlurRadius = TimeUtils::PingPong(1e-9 * nanos, kBlurRadiusPingPoingPeriod, kBlurRadiusPingPoingShift,
   *                                          kMinBlurRadius, kMaxBlurRadius);
   *         return true;
   *     }
   * ```
   */
  protected override fun onAnimate(nanos: Double): Boolean {
    TODO("Implement onAnimate")
  }

  public companion object {
    private val kMinRadius: SkScalar = TODO("Initialize kMinRadius")

    private val kMaxRadius: SkScalar = TODO("Initialize kMaxRadius")

    private val kRadiusPingPoingPeriod: SkScalar = TODO("Initialize kRadiusPingPoingPeriod")

    private val kRadiusPingPoingShift: SkScalar = TODO("Initialize kRadiusPingPoingShift")

    private val kMinBlurRadius: SkScalar = TODO("Initialize kMinBlurRadius")

    private val kMaxBlurRadius: SkScalar = TODO("Initialize kMaxBlurRadius")

    private val kBlurRadiusPingPoingPeriod: SkScalar = TODO("Initialize kBlurRadiusPingPoingPeriod")

    private val kBlurRadiusPingPoingShift: SkScalar = TODO("Initialize kBlurRadiusPingPoingShift")
  }
}
