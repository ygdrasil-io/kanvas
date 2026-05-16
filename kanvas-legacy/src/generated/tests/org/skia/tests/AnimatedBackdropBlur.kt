package org.skia.tests

import kotlin.Boolean
import kotlin.Double
import kotlin.Float
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkFont
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class AnimatedBackdropBlur final : public skiagm::GM {
 * public:
 *     SkString getName() const override { return SkString("animated-backdrop-blur"); }
 *
 *     SkISize getISize() override { return {512, 1024}; }
 *
 *     void onOnceBeforeDraw() override {
 *         fFont = SkFont(ToolUtils::DefaultPortableTypeface(), 20);
 *         fImage = ToolUtils::GetResourceAsImage("images/color_wheel.png");
 *         const SkRect crop{0, 100, 512, 400};
 *         fFilter = SkImageFilters::Crop(crop, SkTileMode::kDecal,
 *                         SkImageFilters::Blur(30, 30,
 *                                 SkImageFilters::Crop(crop, SkTileMode::kMirror, nullptr)));
 *
 *         fLayerRec = SkCanvas::SaveLayerRec(nullptr, nullptr, fFilter.get(), 0);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         static constexpr const char* txts[] = {
 *             "Lorem ipsum dolor sit amet,",
 *             "consectetur adipiscing elit,",
 *             "sed do eiusmod tempor incididunt",
 *             "ut labore et dolore magna aliqua.",
 *             "",
 *             "",
 *             "Ut enim ad minim veniam,",
 *             "quis nostrud exercitation ullamco laboris",
 *             "nisi ut aliquip ex ea commodo consequat.",
 *             "",
 *             "",
 *             "Duis aute irure dolor in reprehenderit",
 *             "in voluptate velit esse cillum dolore",
 *             "eu fugiat nulla pariatur."
 *         };
 *
 *         SkPaint paint;
 *         float voffset = fVOffset;
 *         for (const auto& txt : txts) {
 *             canvas->drawSimpleText(
 *                 txt, strlen(txt), SkTextEncoding::kUTF8, 0, voffset, fFont, paint);
 *             voffset += fFont.getSize();
 *         }
 *
 *         float dstHeight = fImage->height() * 128.f / fImage->width();
 *         canvas->drawImageRect(fImage.get(), SkRect::MakeXYWH(16.f, fVOffset, 128.f, dstHeight),
 *                               SkFilterMode::kLinear);
 *
 *         canvas->saveLayer(fLayerRec);
 *         canvas->restore();
 *     }
 *
 *     bool onAnimate(double nanos) override {
 *         fVOffset = TimeUtils::PingPong(nanos * 1e-9, 6, 0, 350, 0);
 *
 *         return true;
 *     }
 *
 * private:
 *     sk_sp<SkImageFilter>   fFilter;
 *     sk_sp<SkImage>         fImage;
 *     SkCanvas::SaveLayerRec fLayerRec;
 *     SkFont                 fFont;
 *     float                  fVOffset = 0;
 * }
 * ```
 */
public class AnimatedBackdropBlur : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter>   fFilter
   * ```
   */
  private var fFilter: SkSp<SkImageFilter> = TODO("Initialize fFilter")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage>         fImage
   * ```
   */
  private var fImage: SkSp<SkImage> = TODO("Initialize fImage")

  /**
   * C++ original:
   * ```cpp
   * SkCanvas::SaveLayerRec fLayerRec
   * ```
   */
  private var fLayerRec: SkCanvas.SaveLayerRec = TODO("Initialize fLayerRec")

  /**
   * C++ original:
   * ```cpp
   * SkFont                 fFont
   * ```
   */
  private var fFont: SkFont = TODO("Initialize fFont")

  /**
   * C++ original:
   * ```cpp
   * float                  fVOffset = 0
   * ```
   */
  private var fVOffset: Float = TODO("Initialize fVOffset")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("animated-backdrop-blur"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {512, 1024}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         fFont = SkFont(ToolUtils::DefaultPortableTypeface(), 20);
   *         fImage = ToolUtils::GetResourceAsImage("images/color_wheel.png");
   *         const SkRect crop{0, 100, 512, 400};
   *         fFilter = SkImageFilters::Crop(crop, SkTileMode::kDecal,
   *                         SkImageFilters::Blur(30, 30,
   *                                 SkImageFilters::Crop(crop, SkTileMode::kMirror, nullptr)));
   *
   *         fLayerRec = SkCanvas::SaveLayerRec(nullptr, nullptr, fFilter.get(), 0);
   *     }
   * ```
   */
  public override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         static constexpr const char* txts[] = {
   *             "Lorem ipsum dolor sit amet,",
   *             "consectetur adipiscing elit,",
   *             "sed do eiusmod tempor incididunt",
   *             "ut labore et dolore magna aliqua.",
   *             "",
   *             "",
   *             "Ut enim ad minim veniam,",
   *             "quis nostrud exercitation ullamco laboris",
   *             "nisi ut aliquip ex ea commodo consequat.",
   *             "",
   *             "",
   *             "Duis aute irure dolor in reprehenderit",
   *             "in voluptate velit esse cillum dolore",
   *             "eu fugiat nulla pariatur."
   *         };
   *
   *         SkPaint paint;
   *         float voffset = fVOffset;
   *         for (const auto& txt : txts) {
   *             canvas->drawSimpleText(
   *                 txt, strlen(txt), SkTextEncoding::kUTF8, 0, voffset, fFont, paint);
   *             voffset += fFont.getSize();
   *         }
   *
   *         float dstHeight = fImage->height() * 128.f / fImage->width();
   *         canvas->drawImageRect(fImage.get(), SkRect::MakeXYWH(16.f, fVOffset, 128.f, dstHeight),
   *                               SkFilterMode::kLinear);
   *
   *         canvas->saveLayer(fLayerRec);
   *         canvas->restore();
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onAnimate(double nanos) override {
   *         fVOffset = TimeUtils::PingPong(nanos * 1e-9, 6, 0, 350, 0);
   *
   *         return true;
   *     }
   * ```
   */
  public override fun onAnimate(nanos: Double): Boolean {
    TODO("Implement onAnimate")
  }
}
