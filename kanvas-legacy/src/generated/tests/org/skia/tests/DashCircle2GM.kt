package org.skia.tests

import kotlin.Boolean
import kotlin.Double
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class DashCircle2GM : public skiagm::GM {
 * public:
 *     DashCircle2GM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("dashcircle2"); }
 *
 *     SkISize getISize() override { return SkISize::Make(635, 900); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         // These intervals are defined relative to tau.
 *         static constexpr SkScalar kIntervals[][2]{
 *                 {0.333f, 0.333f},
 *                 {0.015f, 0.015f},
 *                 {0.01f , 0.09f },
 *                 {0.097f, 0.003f},
 *                 {0.02f , 0.04f },
 *                 {0.1f  , 0.2f  },
 *                 {0.25f , 0.25f },
 *                 {0.6f  , 0.7f  }, // adds to > 1
 *                 {1.2f  , 0.8f  }, // on is > 1
 *                 {0.1f  , 1.1f  }, // off is > 1*/
 *         };
 *
 *         static constexpr int kN = std::size(kIntervals);
 *         static constexpr SkScalar kRadius = 20.f;
 *         static constexpr SkScalar kStrokeWidth = 15.f;
 *         static constexpr SkScalar kPad = 5.f;
 *         static constexpr SkRect kCircle = {-kRadius, -kRadius, kRadius, kRadius};
 *
 *         static constexpr SkScalar kThinRadius = kRadius * 1.5;
 *         static constexpr SkRect kThinCircle = {-kThinRadius, -kThinRadius,
 *                                                 kThinRadius,  kThinRadius};
 *         static constexpr SkScalar kThinStrokeWidth = 0.4f;
 *
 *         sk_sp<SkPathEffect> deffects[std::size(kIntervals)];
 *         sk_sp<SkPathEffect> thinDEffects[std::size(kIntervals)];
 *         for (int i = 0; i < kN; ++i) {
 *             static constexpr SkScalar kTau = 2 * SK_ScalarPI;
 *             static constexpr SkScalar kCircumference = kRadius * kTau;
 *             SkScalar scaledIntervals[2] = {kCircumference * kIntervals[i][0],
 *                                            kCircumference * kIntervals[i][1]};
 *             deffects[i] = SkDashPathEffect::Make(
 *                     scaledIntervals, kCircumference * fPhaseDegrees * kTau / 360.f);
 *             static constexpr SkScalar kThinCircumference = kThinRadius * kTau;
 *             scaledIntervals[0] = kThinCircumference * kIntervals[i][0];
 *             scaledIntervals[1] = kThinCircumference * kIntervals[i][1];
 *             thinDEffects[i] = SkDashPathEffect::Make(
 *                     scaledIntervals, kThinCircumference * fPhaseDegrees * kTau / 360.f);
 *         }
 *
 *         SkMatrix rotate;
 *         rotate.setRotate(25.f);
 *         const SkMatrix kMatrices[]{
 *                 SkMatrix::I(),
 *             SkMatrix::Scale(1.2f, 1.2f),
 *                 SkMatrix::MakeAll(1, 0, 0, 0, -1, 0, 0, 0, 1),  // y flipper
 *                 SkMatrix::MakeAll(-1, 0, 0, 0, 1, 0, 0, 0, 1),  // x flipper
 *             SkMatrix::Scale(0.7f, 0.7f),
 *                 rotate,
 *                 SkMatrix::Concat(
 *                         SkMatrix::Concat(SkMatrix::MakeAll(-1, 0, 0, 0, 1, 0, 0, 0, 1), rotate),
 *                         rotate)
 *         };
 *
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *         paint.setStrokeWidth(kStrokeWidth);
 *         paint.setStroke(true);
 *
 *         // Compute the union of bounds of all of our test cases.
 *         SkRect bounds = SkRect::MakeEmpty();
 *         const SkRect kBounds = kThinCircle.makeOutset(kThinStrokeWidth / 2.f,
 *                                                       kThinStrokeWidth / 2.f);
 *         for (const auto& m : kMatrices) {
 *             SkRect devBounds;
 *             m.mapRect(&devBounds, kBounds);
 *             bounds.join(devBounds);
 *         }
 *
 *         canvas->save();
 *         canvas->translate(-bounds.fLeft + kPad, -bounds.fTop + kPad);
 *         for (size_t i = 0; i < std::size(deffects); ++i) {
 *             canvas->save();
 *             for (const auto& m : kMatrices) {
 *                 canvas->save();
 *                 canvas->concat(m);
 *
 *                 paint.setPathEffect(deffects[i]);
 *                 paint.setStrokeWidth(kStrokeWidth);
 *                 canvas->drawOval(kCircle, paint);
 *
 *                 paint.setPathEffect(thinDEffects[i]);
 *                 paint.setStrokeWidth(kThinStrokeWidth);
 *                 canvas->drawOval(kThinCircle, paint);
 *
 *                 canvas->restore();
 *                 canvas->translate(bounds.width() + kPad, 0);
 *             }
 *             canvas->restore();
 *             canvas->translate(0, bounds.height() + kPad);
 *         }
 *         canvas->restore();
 *     }
 *
 * protected:
 *     bool onAnimate(double nanos) override {
 *         fPhaseDegrees = 1e-9 * nanos;
 *         return true;
 *     }
 *
 *     // Init with a non-zero phase for when run as a non-animating GM.
 *     SkScalar fPhaseDegrees = 12.f;
 * }
 * ```
 */
public open class DashCircle2GM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkScalar fPhaseDegrees = 12.f
   * ```
   */
  protected var fPhaseDegrees: SkScalar = TODO("Initialize fPhaseDegrees")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("dashcircle2"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(635, 900); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         // These intervals are defined relative to tau.
   *         static constexpr SkScalar kIntervals[][2]{
   *                 {0.333f, 0.333f},
   *                 {0.015f, 0.015f},
   *                 {0.01f , 0.09f },
   *                 {0.097f, 0.003f},
   *                 {0.02f , 0.04f },
   *                 {0.1f  , 0.2f  },
   *                 {0.25f , 0.25f },
   *                 {0.6f  , 0.7f  }, // adds to > 1
   *                 {1.2f  , 0.8f  }, // on is > 1
   *                 {0.1f  , 1.1f  }, // off is > 1*/
   *         };
   *
   *         static constexpr int kN = std::size(kIntervals);
   *         static constexpr SkScalar kRadius = 20.f;
   *         static constexpr SkScalar kStrokeWidth = 15.f;
   *         static constexpr SkScalar kPad = 5.f;
   *         static constexpr SkRect kCircle = {-kRadius, -kRadius, kRadius, kRadius};
   *
   *         static constexpr SkScalar kThinRadius = kRadius * 1.5;
   *         static constexpr SkRect kThinCircle = {-kThinRadius, -kThinRadius,
   *                                                 kThinRadius,  kThinRadius};
   *         static constexpr SkScalar kThinStrokeWidth = 0.4f;
   *
   *         sk_sp<SkPathEffect> deffects[std::size(kIntervals)];
   *         sk_sp<SkPathEffect> thinDEffects[std::size(kIntervals)];
   *         for (int i = 0; i < kN; ++i) {
   *             static constexpr SkScalar kTau = 2 * SK_ScalarPI;
   *             static constexpr SkScalar kCircumference = kRadius * kTau;
   *             SkScalar scaledIntervals[2] = {kCircumference * kIntervals[i][0],
   *                                            kCircumference * kIntervals[i][1]};
   *             deffects[i] = SkDashPathEffect::Make(
   *                     scaledIntervals, kCircumference * fPhaseDegrees * kTau / 360.f);
   *             static constexpr SkScalar kThinCircumference = kThinRadius * kTau;
   *             scaledIntervals[0] = kThinCircumference * kIntervals[i][0];
   *             scaledIntervals[1] = kThinCircumference * kIntervals[i][1];
   *             thinDEffects[i] = SkDashPathEffect::Make(
   *                     scaledIntervals, kThinCircumference * fPhaseDegrees * kTau / 360.f);
   *         }
   *
   *         SkMatrix rotate;
   *         rotate.setRotate(25.f);
   *         const SkMatrix kMatrices[]{
   *                 SkMatrix::I(),
   *             SkMatrix::Scale(1.2f, 1.2f),
   *                 SkMatrix::MakeAll(1, 0, 0, 0, -1, 0, 0, 0, 1),  // y flipper
   *                 SkMatrix::MakeAll(-1, 0, 0, 0, 1, 0, 0, 0, 1),  // x flipper
   *             SkMatrix::Scale(0.7f, 0.7f),
   *                 rotate,
   *                 SkMatrix::Concat(
   *                         SkMatrix::Concat(SkMatrix::MakeAll(-1, 0, 0, 0, 1, 0, 0, 0, 1), rotate),
   *                         rotate)
   *         };
   *
   *         SkPaint paint;
   *         paint.setAntiAlias(true);
   *         paint.setStrokeWidth(kStrokeWidth);
   *         paint.setStroke(true);
   *
   *         // Compute the union of bounds of all of our test cases.
   *         SkRect bounds = SkRect::MakeEmpty();
   *         const SkRect kBounds = kThinCircle.makeOutset(kThinStrokeWidth / 2.f,
   *                                                       kThinStrokeWidth / 2.f);
   *         for (const auto& m : kMatrices) {
   *             SkRect devBounds;
   *             m.mapRect(&devBounds, kBounds);
   *             bounds.join(devBounds);
   *         }
   *
   *         canvas->save();
   *         canvas->translate(-bounds.fLeft + kPad, -bounds.fTop + kPad);
   *         for (size_t i = 0; i < std::size(deffects); ++i) {
   *             canvas->save();
   *             for (const auto& m : kMatrices) {
   *                 canvas->save();
   *                 canvas->concat(m);
   *
   *                 paint.setPathEffect(deffects[i]);
   *                 paint.setStrokeWidth(kStrokeWidth);
   *                 canvas->drawOval(kCircle, paint);
   *
   *                 paint.setPathEffect(thinDEffects[i]);
   *                 paint.setStrokeWidth(kThinStrokeWidth);
   *                 canvas->drawOval(kThinCircle, paint);
   *
   *                 canvas->restore();
   *                 canvas->translate(bounds.width() + kPad, 0);
   *             }
   *             canvas->restore();
   *             canvas->translate(0, bounds.height() + kPad);
   *         }
   *         canvas->restore();
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
   *         fPhaseDegrees = 1e-9 * nanos;
   *         return true;
   *     }
   * ```
   */
  protected override fun onAnimate(nanos: Double): Boolean {
    TODO("Implement onAnimate")
  }
}
