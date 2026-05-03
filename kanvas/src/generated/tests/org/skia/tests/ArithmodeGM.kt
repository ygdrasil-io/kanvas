package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ArithmodeGM : public skiagm::GM {
 *     SkString getName() const override { return SkString("arithmode"); }
 *
 *     SkISize getISize() override { return {640, 572}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         constexpr int WW = 100,
 *                       HH = 32;
 *
 *         sk_sp<SkImage> src = make_src(WW, HH);
 *         sk_sp<SkImage> dst = make_dst(WW, HH);
 *         sk_sp<SkImageFilter> srcFilter = SkImageFilters::Image(src, {SkFilterMode::kLinear});
 *         sk_sp<SkImageFilter> dstFilter = SkImageFilters::Image(dst, {SkFilterMode::kLinear});
 *
 *         constexpr SkScalar one = SK_Scalar1;
 *         constexpr SkScalar K[] = {
 *             0, 0, 0, 0,
 *             0, 0, 0, one,
 *             0, one, 0, 0,
 *             0, 0, one, 0,
 *             0, one, one, 0,
 *             0, one, -one, 0,
 *             0, one/2, one/2, 0,
 *             0, one/2, one/2, one/4,
 *             0, one/2, one/2, -one/4,
 *             one/4, one/2, one/2, 0,
 *             -one/4, one/2, one/2, 0,
 *         };
 *
 *         const SkScalar* k = K;
 *         const SkScalar* stop = k + std::size(K);
 *         // Many of the Arithmetic filters have a 4th coefficient that's not zero, which means they
 *         // affect transparent black. 'rect' is used as a crop filter to make sure they don't
 *         // overwrite each other.
 *         const SkRect rect = SkRect::MakeWH(WW, HH);
 *         SkScalar gap = SkIntToScalar(WW + 20);
 *         while (k < stop) {
 *             {
 *                 SkAutoCanvasRestore acr(canvas, true);
 *                 canvas->drawImage(src, 0, 0);
 *                 canvas->translate(gap, 0);
 *                 canvas->drawImage(dst, 0, 0);
 *                 canvas->translate(gap, 0);
 *                 SkPaint paint;
 *                 paint.setImageFilter(SkImageFilters::Arithmetic(k[0], k[1], k[2], k[3], true,
 *                                                                 dstFilter, srcFilter, rect));
 *                 canvas->saveLayer(nullptr, &paint);
 *                 canvas->restore();
 *
 *                 canvas->translate(gap, 0);
 *                 show_k_text(canvas, 0, 0, k);
 *             }
 *
 *             k += 4;
 *             canvas->translate(0, HH + 12);
 *         }
 *
 *         // Draw two special cases to test enforcePMColor. In these cases, we
 *         // draw the dst bitmap twice, the first time it is halved and inverted,
 *         // leading to invalid premultiplied colors. If we enforcePMColor, these
 *         // invalid values should be clamped, and will not contribute to the
 *         // second draw.
 *         for (int i = 0; i < 2; i++) {
 *             const bool enforcePMColor = (i == 0);
 *
 *             {
 *                 SkAutoCanvasRestore acr(canvas, true);
 *                 canvas->translate(gap, 0);
 *                 canvas->drawImage(dst, 0, 0);
 *                 canvas->translate(gap, 0);
 *
 *                 sk_sp<SkImageFilter> bg =
 *                         SkImageFilters::Arithmetic(0, 0, -one / 2, 1, enforcePMColor, dstFilter,
 *                                                    nullptr, nullptr);
 *                 SkPaint p;
 *                 p.setImageFilter(SkImageFilters::Arithmetic(0, one / 2, -one, 1, true,
 *                                                             std::move(bg), dstFilter, rect));
 *                 canvas->saveLayer(nullptr, &p);
 *                 canvas->restore();
 *                 canvas->translate(gap, 0);
 *
 *                 // Label
 *                 SkFont   font(ToolUtils::DefaultPortableTypeface(), 24);
 *                 SkString str(enforcePMColor ? "enforcePM" : "no enforcePM");
 *                 canvas->drawString(str, 0, font.getSize(), font, SkPaint());
 *             }
 *             canvas->translate(0, HH + 12);
 *         }
 *     }
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class ArithmodeGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("arithmode"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {640, 572}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         constexpr int WW = 100,
   *                       HH = 32;
   *
   *         sk_sp<SkImage> src = make_src(WW, HH);
   *         sk_sp<SkImage> dst = make_dst(WW, HH);
   *         sk_sp<SkImageFilter> srcFilter = SkImageFilters::Image(src, {SkFilterMode::kLinear});
   *         sk_sp<SkImageFilter> dstFilter = SkImageFilters::Image(dst, {SkFilterMode::kLinear});
   *
   *         constexpr SkScalar one = SK_Scalar1;
   *         constexpr SkScalar K[] = {
   *             0, 0, 0, 0,
   *             0, 0, 0, one,
   *             0, one, 0, 0,
   *             0, 0, one, 0,
   *             0, one, one, 0,
   *             0, one, -one, 0,
   *             0, one/2, one/2, 0,
   *             0, one/2, one/2, one/4,
   *             0, one/2, one/2, -one/4,
   *             one/4, one/2, one/2, 0,
   *             -one/4, one/2, one/2, 0,
   *         };
   *
   *         const SkScalar* k = K;
   *         const SkScalar* stop = k + std::size(K);
   *         // Many of the Arithmetic filters have a 4th coefficient that's not zero, which means they
   *         // affect transparent black. 'rect' is used as a crop filter to make sure they don't
   *         // overwrite each other.
   *         const SkRect rect = SkRect::MakeWH(WW, HH);
   *         SkScalar gap = SkIntToScalar(WW + 20);
   *         while (k < stop) {
   *             {
   *                 SkAutoCanvasRestore acr(canvas, true);
   *                 canvas->drawImage(src, 0, 0);
   *                 canvas->translate(gap, 0);
   *                 canvas->drawImage(dst, 0, 0);
   *                 canvas->translate(gap, 0);
   *                 SkPaint paint;
   *                 paint.setImageFilter(SkImageFilters::Arithmetic(k[0], k[1], k[2], k[3], true,
   *                                                                 dstFilter, srcFilter, rect));
   *                 canvas->saveLayer(nullptr, &paint);
   *                 canvas->restore();
   *
   *                 canvas->translate(gap, 0);
   *                 show_k_text(canvas, 0, 0, k);
   *             }
   *
   *             k += 4;
   *             canvas->translate(0, HH + 12);
   *         }
   *
   *         // Draw two special cases to test enforcePMColor. In these cases, we
   *         // draw the dst bitmap twice, the first time it is halved and inverted,
   *         // leading to invalid premultiplied colors. If we enforcePMColor, these
   *         // invalid values should be clamped, and will not contribute to the
   *         // second draw.
   *         for (int i = 0; i < 2; i++) {
   *             const bool enforcePMColor = (i == 0);
   *
   *             {
   *                 SkAutoCanvasRestore acr(canvas, true);
   *                 canvas->translate(gap, 0);
   *                 canvas->drawImage(dst, 0, 0);
   *                 canvas->translate(gap, 0);
   *
   *                 sk_sp<SkImageFilter> bg =
   *                         SkImageFilters::Arithmetic(0, 0, -one / 2, 1, enforcePMColor, dstFilter,
   *                                                    nullptr, nullptr);
   *                 SkPaint p;
   *                 p.setImageFilter(SkImageFilters::Arithmetic(0, one / 2, -one, 1, true,
   *                                                             std::move(bg), dstFilter, rect));
   *                 canvas->saveLayer(nullptr, &p);
   *                 canvas->restore();
   *                 canvas->translate(gap, 0);
   *
   *                 // Label
   *                 SkFont   font(ToolUtils::DefaultPortableTypeface(), 24);
   *                 SkString str(enforcePMColor ? "enforcePM" : "no enforcePM");
   *                 canvas->drawString(str, 0, font.getSize(), font, SkPaint());
   *             }
   *             canvas->translate(0, HH + 12);
   *         }
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
