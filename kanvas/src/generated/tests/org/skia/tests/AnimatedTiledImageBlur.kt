package org.skia.tests

import kotlin.Boolean
import kotlin.Double
import kotlin.Float
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class AnimatedTiledImageBlur : public skiagm::GM {
 * static constexpr float kMaxBlurSigma = 250.f;
 * static constexpr float kAnimationDuration = 12.f; // seconds
 * public:
 *     AnimatedTiledImageBlur() : fBlurSigma(0.3f * kMaxBlurSigma) {
 *         this->setBGColor(0xFFCCCCCC);
 *     }
 *
 * protected:
 *     bool runAsBench() const override { return true; }
 *
 *     SkString getName() const override { return SkString("animated-tiled-image-blur"); }
 *
 *     SkISize getISize() override { return SkISize::Make(530, 530); }
 *
 *     void onOnceBeforeDraw() override {
 *         fImage = ToolUtils::GetResourceAsImage("images/mandrill_512.png");
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         auto drawBlurredImage = [&](float tx, float ty, SkTileMode tileMode) {
 *             SkPaint paint;
 *             SkRect rect = SkRect::MakeIWH(250, 250);
 *             canvas->save();
 *             canvas->translate(tx, ty);
 *             paint.setImageFilter(SkImageFilters::Blur(fBlurSigma, fBlurSigma, tileMode,
 *                                                       nullptr, rect));
 *             canvas->drawImageRect(fImage, rect, SkFilterMode::kLinear, &paint);
 *             canvas->restore();
 *         };
 *
 *         drawBlurredImage(10.f,  10.f,  SkTileMode::kDecal);
 *         drawBlurredImage(270.f, 10.f,  SkTileMode::kClamp);
 *         drawBlurredImage(10.f,  270.f, SkTileMode::kRepeat);
 *         drawBlurredImage(270.f, 270.f, SkTileMode::kMirror);
 *     }
 *
 *     bool onAnimate(double nanos) override {
 *         fBlurSigma = TimeUtils::PingPong(1e-9 * nanos, kAnimationDuration,
 *                                          0.f, 0.0f, kMaxBlurSigma);
 *         return true;
 *     }
 *
 * private:
 *     sk_sp<SkImage> fImage;
 *     SkScalar fBlurSigma;
 * }
 * ```
 */
public open class AnimatedTiledImageBlur public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * static constexpr float kMaxBlurSigma = 250.f
   * ```
   */
  private var fImage: SkSp<SkImage> = TODO("Initialize fImage")

  /**
   * C++ original:
   * ```cpp
   * static constexpr float kAnimationDuration = 12.f
   * ```
   */
  private var fBlurSigma: SkScalar = TODO("Initialize fBlurSigma")

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
   * SkString getName() const override { return SkString("animated-tiled-image-blur"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(530, 530); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         fImage = ToolUtils::GetResourceAsImage("images/mandrill_512.png");
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
   *         auto drawBlurredImage = [&](float tx, float ty, SkTileMode tileMode) {
   *             SkPaint paint;
   *             SkRect rect = SkRect::MakeIWH(250, 250);
   *             canvas->save();
   *             canvas->translate(tx, ty);
   *             paint.setImageFilter(SkImageFilters::Blur(fBlurSigma, fBlurSigma, tileMode,
   *                                                       nullptr, rect));
   *             canvas->drawImageRect(fImage, rect, SkFilterMode::kLinear, &paint);
   *             canvas->restore();
   *         };
   *
   *         drawBlurredImage(10.f,  10.f,  SkTileMode::kDecal);
   *         drawBlurredImage(270.f, 10.f,  SkTileMode::kClamp);
   *         drawBlurredImage(10.f,  270.f, SkTileMode::kRepeat);
   *         drawBlurredImage(270.f, 270.f, SkTileMode::kMirror);
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
   *         fBlurSigma = TimeUtils::PingPong(1e-9 * nanos, kAnimationDuration,
   *                                          0.f, 0.0f, kMaxBlurSigma);
   *         return true;
   *     }
   * ```
   */
  protected override fun onAnimate(nanos: Double): Boolean {
    TODO("Implement onAnimate")
  }

  public companion object {
    private val kMaxBlurSigma: Float = TODO("Initialize kMaxBlurSigma")

    private val kAnimationDuration: Float = TODO("Initialize kAnimationDuration")
  }
}
