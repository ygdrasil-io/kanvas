package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class DrawAtlasGM : public skiagm::GM {
 *     static sk_sp<SkImage> MakeAtlas(SkCanvas* caller, const SkRect& target) {
 *         SkImageInfo info = SkImageInfo::MakeN32Premul(100, 100);
 *         auto        surface(ToolUtils::makeSurface(caller, info));
 *         SkCanvas* canvas = surface->getCanvas();
 *         // draw red everywhere, but we don't expect to see it in the draw, testing the notion
 *         // that drawAtlas draws a subset-region of the atlas.
 *         canvas->clear(SK_ColorRED);
 *
 *         SkPaint paint;
 *         paint.setBlendMode(SkBlendMode::kClear);
 *         SkRect r(target);
 *         r.inset(-1, -1);
 *         // zero out a place (with a 1-pixel border) to land our drawing.
 *         canvas->drawRect(r, paint);
 *         paint.setBlendMode(SkBlendMode::kSrcOver);
 *         paint.setColor(SK_ColorBLUE);
 *         paint.setAntiAlias(true);
 *         canvas->drawOval(target, paint);
 *         return surface->makeImageSnapshot();
 *     }
 *
 * public:
 *     DrawAtlasGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("draw-atlas"); }
 *
 *     SkISize getISize() override { return SkISize::Make(640, 480); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         const SkRect target = { 50, 50, 80, 90 };
 *         auto atlas = MakeAtlas(canvas, target);
 *
 *         const struct {
 *             SkScalar fScale;
 *             SkScalar fDegrees;
 *             SkScalar fTx;
 *             SkScalar fTy;
 *
 *             void apply(SkRSXform* xform) const {
 *                 const SkScalar rad = SkDegreesToRadians(fDegrees);
 *                 xform->fSCos = fScale * SkScalarCos(rad);
 *                 xform->fSSin = fScale * SkScalarSin(rad);
 *                 xform->fTx   = fTx;
 *                 xform->fTy   = fTy;
 *             }
 *         } rec[] = {
 *             { 1, 0, 10, 10 },       // just translate
 *             { 2, 0, 110, 10 },      // scale + translate
 *             { 1, 30, 210, 10 },     // rotate + translate
 *             { 2, -30, 310, 30 },    // scale + rotate + translate
 *         };
 *
 *         const int N = std::size(rec);
 *         SkRSXform xform[N];
 *         SkRect tex[N];
 *         SkColor colors[N];
 *
 *         for (int i = 0; i < N; ++i) {
 *             rec[i].apply(&xform[i]);
 *             tex[i] = target;
 *             colors[i] = 0x80FF0000 + (i * 40 * 256);
 *         }
 *
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *         SkSamplingOptions sampling(SkFilterMode::kLinear);
 *
 *         canvas->drawAtlas(atlas.get(), xform, tex, {}, SkBlendMode::kDst,
 *                           sampling, nullptr, &paint);
 *         canvas->translate(0, 100);
 *         canvas->drawAtlas(atlas.get(), xform, tex, colors, SkBlendMode::kSrcIn,
 *                           sampling, nullptr, &paint);
 *     }
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class DrawAtlasGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("draw-atlas"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(640, 480); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         const SkRect target = { 50, 50, 80, 90 };
   *         auto atlas = MakeAtlas(canvas, target);
   *
   *         const struct {
   *             SkScalar fScale;
   *             SkScalar fDegrees;
   *             SkScalar fTx;
   *             SkScalar fTy;
   *
   *             void apply(SkRSXform* xform) const {
   *                 const SkScalar rad = SkDegreesToRadians(fDegrees);
   *                 xform->fSCos = fScale * SkScalarCos(rad);
   *                 xform->fSSin = fScale * SkScalarSin(rad);
   *                 xform->fTx   = fTx;
   *                 xform->fTy   = fTy;
   *             }
   *         } rec[] = {
   *             { 1, 0, 10, 10 },       // just translate
   *             { 2, 0, 110, 10 },      // scale + translate
   *             { 1, 30, 210, 10 },     // rotate + translate
   *             { 2, -30, 310, 30 },    // scale + rotate + translate
   *         };
   *
   *         const int N = std::size(rec);
   *         SkRSXform xform[N];
   *         SkRect tex[N];
   *         SkColor colors[N];
   *
   *         for (int i = 0; i < N; ++i) {
   *             rec[i].apply(&xform[i]);
   *             tex[i] = target;
   *             colors[i] = 0x80FF0000 + (i * 40 * 256);
   *         }
   *
   *         SkPaint paint;
   *         paint.setAntiAlias(true);
   *         SkSamplingOptions sampling(SkFilterMode::kLinear);
   *
   *         canvas->drawAtlas(atlas.get(), xform, tex, {}, SkBlendMode::kDst,
   *                           sampling, nullptr, &paint);
   *         canvas->translate(0, 100);
   *         canvas->drawAtlas(atlas.get(), xform, tex, colors, SkBlendMode::kSrcIn,
   *                           sampling, nullptr, &paint);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkImage> MakeAtlas(SkCanvas* caller, const SkRect& target) {
     *         SkImageInfo info = SkImageInfo::MakeN32Premul(100, 100);
     *         auto        surface(ToolUtils::makeSurface(caller, info));
     *         SkCanvas* canvas = surface->getCanvas();
     *         // draw red everywhere, but we don't expect to see it in the draw, testing the notion
     *         // that drawAtlas draws a subset-region of the atlas.
     *         canvas->clear(SK_ColorRED);
     *
     *         SkPaint paint;
     *         paint.setBlendMode(SkBlendMode::kClear);
     *         SkRect r(target);
     *         r.inset(-1, -1);
     *         // zero out a place (with a 1-pixel border) to land our drawing.
     *         canvas->drawRect(r, paint);
     *         paint.setBlendMode(SkBlendMode::kSrcOver);
     *         paint.setColor(SK_ColorBLUE);
     *         paint.setAntiAlias(true);
     *         canvas->drawOval(target, paint);
     *         return surface->makeImageSnapshot();
     *     }
     * ```
     */
    private fun makeAtlas(caller: SkCanvas?, target: SkRect): SkSp<SkImage> {
      TODO("Implement makeAtlas")
    }
  }
}
