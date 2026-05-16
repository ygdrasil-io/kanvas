package org.skia.tests

import kotlin.Array
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class DrawImageSetGM : public GM {
 * private:
 *     SkString getName() const override { return SkString("draw_image_set"); }
 *     SkISize getISize() override { return {1000, 725}; }
 *     void onOnceBeforeDraw() override {
 *         static constexpr SkColor4f kColors[] = {SkColors::kCyan, SkColors::kBlack,
 *                                                 SkColors::kMagenta, SkColors::kBlack};
 *         make_image_tiles(kTileW, kTileH, kM, kN, kColors, fSet);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkScalar d = SkVector{kM * kTileW, kN * kTileH}.length();
 *         SkMatrix matrices[4];
 *         // rotation
 *         matrices[0].setRotate(30);
 *         matrices[0].postTranslate(d / 3, 0);
 *         // perespective
 *         const std::array<SkPoint, 4> src = SkRect::MakeWH(kM * kTileW, kN * kTileH).toQuad();
 *         SkPoint dst[4] = {{0, 0},
 *                           {kM * kTileW + 10.f, -5.f},
 *                           {kM * kTileW - 28.f, kN * kTileH + 40.f},
 *                           {45.f, kN * kTileH - 25.f}};
 *         SkAssertResult(matrices[1].setPolyToPoly(src, dst));
 *         matrices[1].postTranslate(d, 50.f);
 *
 *         // skew
 *         matrices[2].setRotate(-60.f);
 *         matrices[2].postSkew(0.5f, -1.15f);
 *         matrices[2].postScale(0.6f, 1.05f);
 *         matrices[2].postTranslate(d, 2.6f * d);
 *         // perspective + mirror in x.
 *         dst[1] = {-.25 * kM * kTileW, 0};
 *         dst[0] = {5.f / 4.f * kM * kTileW, 0};
 *         dst[3] = {2.f / 3.f * kM * kTileW, 1 / 2.f * kN * kTileH};
 *         dst[2] = {1.f / 3.f * kM * kTileW, 1 / 2.f * kN * kTileH - 0.1f * kTileH};
 *         SkAssertResult(matrices[3].setPolyToPoly(src, dst));
 *         matrices[3].postTranslate(100.f, d);
 *         for (auto fm : {SkFilterMode::kNearest, SkFilterMode::kLinear}) {
 *             SkPaint setPaint;
 *             setPaint.setBlendMode(SkBlendMode::kSrcOver);
 *             SkSamplingOptions sampling(fm);
 *
 *             for (size_t m = 0; m < std::size(matrices); ++m) {
 *                 // Draw grid of red lines at interior tile boundaries.
 *                 static constexpr SkScalar kLineOutset = 10.f;
 *                 SkPaint paint;
 *                 paint.setAntiAlias(true);
 *                 paint.setColor(SK_ColorRED);
 *                 paint.setStyle(SkPaint::kStroke_Style);
 *                 paint.setStrokeWidth(0.f);
 *                 for (int x = 1; x < kM; ++x) {
 *                     SkPoint pts[] = {{x * kTileW, 0}, {x * kTileW, kN * kTileH}};
 *                     matrices[m].mapPoints(pts);
 *                     SkVector v = pts[1] - pts[0];
 *                     v.setLength(v.length() + kLineOutset);
 *                     canvas->drawLine(pts[1] - v, pts[0] + v, paint);
 *                 }
 *                 for (int y = 1; y < kN; ++y) {
 *                     SkPoint pts[] = {{0, y * kTileH}, {kTileW * kM, y * kTileH}};
 *                     matrices[m].mapPoints(pts);
 *                     SkVector v = pts[1] - pts[0];
 *                     v.setLength(v.length() + kLineOutset);
 *                     canvas->drawLine(pts[1] - v, pts[0] + v, paint);
 *                 }
 *                 canvas->save();
 *                 canvas->concat(matrices[m]);
 *                 canvas->experimental_DrawEdgeAAImageSet(fSet, kM * kN, nullptr, nullptr, sampling,
 *                                                         &setPaint,
 *                                                         SkCanvas::kFast_SrcRectConstraint);
 *                 canvas->restore();
 *             }
 *             // A more exotic case with an unusual blend mode, mixed aa flags set, and alpha,
 *             // subsets the image. And another with all the above plus a color filter.
 *             SkCanvas::ImageSetEntry entry;
 *             entry.fSrcRect = SkRect::MakeWH(kTileW, kTileH).makeInset(kTileW / 4.f, kTileH / 4.f);
 *             entry.fDstRect = SkRect::MakeWH(1.5 * kTileW, 1.5 * kTileH).makeOffset(d / 4, 2 * d);
 *             entry.fImage = fSet[0].fImage;
 *             entry.fAlpha = 0.7f;
 *             entry.fAAFlags = SkCanvas::kLeft_QuadAAFlag | SkCanvas::kTop_QuadAAFlag;
 *             canvas->save();
 *             canvas->rotate(3.f);
 *
 *             setPaint.setBlendMode(SkBlendMode::kExclusion);
 *             canvas->experimental_DrawEdgeAAImageSet(&entry, 1, nullptr, nullptr, sampling,
 *                                                     &setPaint, SkCanvas::kFast_SrcRectConstraint);
 *             canvas->translate(entry.fDstRect.width() + 8.f, 0);
 *             SkPaint cfPaint = setPaint;
 *             cfPaint.setColorFilter(SkColorFilters::LinearToSRGBGamma());
 *             canvas->experimental_DrawEdgeAAImageSet(&entry, 1, nullptr, nullptr, sampling,
 *                                                     &cfPaint, SkCanvas::kFast_SrcRectConstraint);
 *             canvas->restore();
 *             canvas->translate(2 * d, 0);
 *         }
 *     }
 *     inline static constexpr int kM = 4;
 *     inline static constexpr int kN = 3;
 *     inline static constexpr SkScalar kTileW = 30;
 *     inline static constexpr SkScalar kTileH = 60;
 *     SkCanvas::ImageSetEntry fSet[kM * kN];
 * }
 * ```
 */
public open class DrawImageSetGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kM = 4
   * ```
   */
  private var fSet: Array<SkCanvas.ImageSetEntry> = TODO("Initialize fSet")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("draw_image_set"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {1000, 725}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         static constexpr SkColor4f kColors[] = {SkColors::kCyan, SkColors::kBlack,
   *                                                 SkColors::kMagenta, SkColors::kBlack};
   *         make_image_tiles(kTileW, kTileH, kM, kN, kColors, fSet);
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
   *         SkScalar d = SkVector{kM * kTileW, kN * kTileH}.length();
   *         SkMatrix matrices[4];
   *         // rotation
   *         matrices[0].setRotate(30);
   *         matrices[0].postTranslate(d / 3, 0);
   *         // perespective
   *         const std::array<SkPoint, 4> src = SkRect::MakeWH(kM * kTileW, kN * kTileH).toQuad();
   *         SkPoint dst[4] = {{0, 0},
   *                           {kM * kTileW + 10.f, -5.f},
   *                           {kM * kTileW - 28.f, kN * kTileH + 40.f},
   *                           {45.f, kN * kTileH - 25.f}};
   *         SkAssertResult(matrices[1].setPolyToPoly(src, dst));
   *         matrices[1].postTranslate(d, 50.f);
   *
   *         // skew
   *         matrices[2].setRotate(-60.f);
   *         matrices[2].postSkew(0.5f, -1.15f);
   *         matrices[2].postScale(0.6f, 1.05f);
   *         matrices[2].postTranslate(d, 2.6f * d);
   *         // perspective + mirror in x.
   *         dst[1] = {-.25 * kM * kTileW, 0};
   *         dst[0] = {5.f / 4.f * kM * kTileW, 0};
   *         dst[3] = {2.f / 3.f * kM * kTileW, 1 / 2.f * kN * kTileH};
   *         dst[2] = {1.f / 3.f * kM * kTileW, 1 / 2.f * kN * kTileH - 0.1f * kTileH};
   *         SkAssertResult(matrices[3].setPolyToPoly(src, dst));
   *         matrices[3].postTranslate(100.f, d);
   *         for (auto fm : {SkFilterMode::kNearest, SkFilterMode::kLinear}) {
   *             SkPaint setPaint;
   *             setPaint.setBlendMode(SkBlendMode::kSrcOver);
   *             SkSamplingOptions sampling(fm);
   *
   *             for (size_t m = 0; m < std::size(matrices); ++m) {
   *                 // Draw grid of red lines at interior tile boundaries.
   *                 static constexpr SkScalar kLineOutset = 10.f;
   *                 SkPaint paint;
   *                 paint.setAntiAlias(true);
   *                 paint.setColor(SK_ColorRED);
   *                 paint.setStyle(SkPaint::kStroke_Style);
   *                 paint.setStrokeWidth(0.f);
   *                 for (int x = 1; x < kM; ++x) {
   *                     SkPoint pts[] = {{x * kTileW, 0}, {x * kTileW, kN * kTileH}};
   *                     matrices[m].mapPoints(pts);
   *                     SkVector v = pts[1] - pts[0];
   *                     v.setLength(v.length() + kLineOutset);
   *                     canvas->drawLine(pts[1] - v, pts[0] + v, paint);
   *                 }
   *                 for (int y = 1; y < kN; ++y) {
   *                     SkPoint pts[] = {{0, y * kTileH}, {kTileW * kM, y * kTileH}};
   *                     matrices[m].mapPoints(pts);
   *                     SkVector v = pts[1] - pts[0];
   *                     v.setLength(v.length() + kLineOutset);
   *                     canvas->drawLine(pts[1] - v, pts[0] + v, paint);
   *                 }
   *                 canvas->save();
   *                 canvas->concat(matrices[m]);
   *                 canvas->experimental_DrawEdgeAAImageSet(fSet, kM * kN, nullptr, nullptr, sampling,
   *                                                         &setPaint,
   *                                                         SkCanvas::kFast_SrcRectConstraint);
   *                 canvas->restore();
   *             }
   *             // A more exotic case with an unusual blend mode, mixed aa flags set, and alpha,
   *             // subsets the image. And another with all the above plus a color filter.
   *             SkCanvas::ImageSetEntry entry;
   *             entry.fSrcRect = SkRect::MakeWH(kTileW, kTileH).makeInset(kTileW / 4.f, kTileH / 4.f);
   *             entry.fDstRect = SkRect::MakeWH(1.5 * kTileW, 1.5 * kTileH).makeOffset(d / 4, 2 * d);
   *             entry.fImage = fSet[0].fImage;
   *             entry.fAlpha = 0.7f;
   *             entry.fAAFlags = SkCanvas::kLeft_QuadAAFlag | SkCanvas::kTop_QuadAAFlag;
   *             canvas->save();
   *             canvas->rotate(3.f);
   *
   *             setPaint.setBlendMode(SkBlendMode::kExclusion);
   *             canvas->experimental_DrawEdgeAAImageSet(&entry, 1, nullptr, nullptr, sampling,
   *                                                     &setPaint, SkCanvas::kFast_SrcRectConstraint);
   *             canvas->translate(entry.fDstRect.width() + 8.f, 0);
   *             SkPaint cfPaint = setPaint;
   *             cfPaint.setColorFilter(SkColorFilters::LinearToSRGBGamma());
   *             canvas->experimental_DrawEdgeAAImageSet(&entry, 1, nullptr, nullptr, sampling,
   *                                                     &cfPaint, SkCanvas::kFast_SrcRectConstraint);
   *             canvas->restore();
   *             canvas->translate(2 * d, 0);
   *         }
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    private val kM: Int = TODO("Initialize kM")

    private val kN: Int = TODO("Initialize kN")

    private val kTileW: SkScalar = TODO("Initialize kTileW")

    private val kTileH: SkScalar = TODO("Initialize kTileH")
  }
}
