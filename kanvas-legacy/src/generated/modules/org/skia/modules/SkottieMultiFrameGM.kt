package org.skia.modules

import kotlin.Boolean
import kotlin.CharArray
import kotlin.Double
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkSp
import org.skia.math.SkISize
import org.skia.math.SkScalar
import org.skia.tests.DrawResult
import org.skia.tests.GM

/**
 * C++ original:
 * ```cpp
 * class SkottieMultiFrameGM : public skiagm::GM {
 * public:
 * protected:
 *     SkString getName() const override { return SkString("skottie_multiframe"); }
 *
 *     SkISize getISize() override { return SkISize::Make(kSize, kSize); }
 *
 *     void onOnceBeforeDraw() override {
 *         if (auto stream = GetResourceAsStream("skottie/skottie_sample_multiframe.json")) {
 *             fAnimation = skottie::Animation::Builder()
 *                             .setResourceProvider(sk_make_sp<MultiFrameResourceProvider>())
 *                             .make(stream.get());
 *             fAnimation->seek(0);
 *         }
 *     }
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *         if (!fAnimation) {
 *             *errorMsg = "No animation";
 *             return DrawResult::kFail;
 *         }
 *
 *         auto dest = SkRect::MakeWH(kSize, kSize);
 *         fAnimation->render(canvas, &dest);
 *         return DrawResult::kOk;
 *     }
 *
 *     bool onAnimate(double nanos) override {
 *         if (!fAnimation) {
 *             return false;
 *         }
 *
 *         const auto duration = fAnimation->duration();
 *         fAnimation->seek(std::fmod(1e-9 * nanos, duration) / duration);
 *         return true;
 *     }
 *
 * private:
 *     class MultiFrameResourceProvider final : public skresources::ResourceProvider {
 *     public:
 *         sk_sp<skresources::ImageAsset> loadImageAsset(const char[], const char[],
 *                                                       const char[]) const override {
 *             sk_sp<SkData> data = GetResourceAsData("images/flightAnim.gif");
 *             SkASSERT(data);
 *             std::unique_ptr<SkCodec> codec = SkGifDecoder::Decode(data, nullptr);
 *             SkASSERT(codec);
 *             return skresources::MultiFrameImageAsset::Make(std::move(codec));
 *         }
 *     };
 *
 *     inline static constexpr SkScalar kSize = 800;
 *
 *     sk_sp<skottie::Animation> fAnimation;
 *
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class SkottieMultiFrameGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr SkScalar kSize = 800
   * ```
   */
  public var fAnimation: SkSp<Animation> = TODO("Initialize fAnimation")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("skottie_multiframe"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(kSize, kSize); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         if (auto stream = GetResourceAsStream("skottie/skottie_sample_multiframe.json")) {
   *             fAnimation = skottie::Animation::Builder()
   *                             .setResourceProvider(sk_make_sp<MultiFrameResourceProvider>())
   *                             .make(stream.get());
   *             fAnimation->seek(0);
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
   * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
   *         if (!fAnimation) {
   *             *errorMsg = "No animation";
   *             return DrawResult::kFail;
   *         }
   *
   *         auto dest = SkRect::MakeWH(kSize, kSize);
   *         fAnimation->render(canvas, &dest);
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onAnimate(double nanos) override {
   *         if (!fAnimation) {
   *             return false;
   *         }
   *
   *         const auto duration = fAnimation->duration();
   *         fAnimation->seek(std::fmod(1e-9 * nanos, duration) / duration);
   *         return true;
   *     }
   * ```
   */
  protected override fun onAnimate(nanos: Double): Boolean {
    TODO("Implement onAnimate")
  }

  public class MultiFrameResourceProvider : ResourceProvider() {
    public override fun loadImageAsset(
      param0: CharArray,
      param1: CharArray,
      param2: CharArray,
    ): SkSp<ImageAsset> {
      TODO("Implement loadImageAsset")
    }
  }

  public companion object {
    public val kSize: SkScalar = TODO("Initialize kSize")
  }
}
