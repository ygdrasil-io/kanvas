package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class CroppedRectsGM : public GM {
 * private:
 *     SkString getName() const override { return SkString("croppedrects"); }
 *     SkISize getISize() override { return SkISize::Make(500, 500); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         if (!fSrcImage) {
 *             fSrcImage = create_image(canvas);
 *             if (fSrcImage) {
 *                 fSrcImageShader = fSrcImage->makeShader(SkSamplingOptions());
 *             }
 *         }
 *
 *         canvas->clear(SK_ColorWHITE);
 *
 *         {
 *             // skgpu::ganesh::SurfaceDrawContext::drawFilledRect.
 *             SkAutoCanvasRestore acr(canvas, true);
 *             SkPaint paint;
 *             paint.setShader(fSrcImageShader);
 *             canvas->clipRect(kSrcImageClip);
 *             canvas->drawPaint(paint);
 *         }
 *
 *         {
 *             // skgpu::ganesh::SurfaceDrawContext::fillRectToRect.
 *             SkAutoCanvasRestore acr(canvas, true);
 *             SkRect drawRect = SkRect::MakeXYWH(350, 100, 100, 300);
 *             canvas->clipRect(drawRect);
 *             canvas->drawImageRect(fSrcImage.get(),
 *                                   kSrcImageClip.makeOutset(0.5f * kSrcImageClip.width(),
 *                                                            kSrcImageClip.height()),
 *                                   drawRect.makeOutset(0.5f * drawRect.width(), drawRect.height()),
 *                                   SkSamplingOptions(), nullptr,
 *                                   SkCanvas::kStrict_SrcRectConstraint);
 *         }
 *
 *         {
 *             // skgpu::ganesh::SurfaceDrawContext::fillRectWithLocalMatrix.
 *             SkAutoCanvasRestore acr(canvas, true);
 *             SkPath path = SkPath::Line(
 *                    {kSrcImageClip.fLeft - kSrcImageClip.width(), kSrcImageClip.centerY()},
 *                    {kSrcImageClip.fRight + 3 * kSrcImageClip.width(), kSrcImageClip.centerY()});
 *             SkPaint paint;
 *             paint.setStyle(SkPaint::kStroke_Style);
 *             paint.setStrokeWidth(2 * kSrcImageClip.height());
 *             paint.setShader(fSrcImageShader);
 *             canvas->translate(23, 301);
 *             canvas->scale(300 / kSrcImageClip.width(), 100 / kSrcImageClip.height());
 *             canvas->translate(-kSrcImageClip.left(), -kSrcImageClip.top());
 *             canvas->clipRect(kSrcImageClip);
 *             canvas->drawPath(path, paint);
 *         }
 *
 *         // TODO: assert the draw target only has one op in the post-MDB world.
 *     }
 *
 *     sk_sp<SkImage> fSrcImage;
 *     sk_sp<SkShader> fSrcImageShader;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class CroppedRectsGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fSrcImage
   * ```
   */
  private var fSrcImage: SkSp<SkImage> = TODO("Initialize fSrcImage")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> fSrcImageShader
   * ```
   */
  private var fSrcImageShader: SkSp<SkShader> = TODO("Initialize fSrcImageShader")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("croppedrects"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(500, 500); }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         if (!fSrcImage) {
   *             fSrcImage = create_image(canvas);
   *             if (fSrcImage) {
   *                 fSrcImageShader = fSrcImage->makeShader(SkSamplingOptions());
   *             }
   *         }
   *
   *         canvas->clear(SK_ColorWHITE);
   *
   *         {
   *             // skgpu::ganesh::SurfaceDrawContext::drawFilledRect.
   *             SkAutoCanvasRestore acr(canvas, true);
   *             SkPaint paint;
   *             paint.setShader(fSrcImageShader);
   *             canvas->clipRect(kSrcImageClip);
   *             canvas->drawPaint(paint);
   *         }
   *
   *         {
   *             // skgpu::ganesh::SurfaceDrawContext::fillRectToRect.
   *             SkAutoCanvasRestore acr(canvas, true);
   *             SkRect drawRect = SkRect::MakeXYWH(350, 100, 100, 300);
   *             canvas->clipRect(drawRect);
   *             canvas->drawImageRect(fSrcImage.get(),
   *                                   kSrcImageClip.makeOutset(0.5f * kSrcImageClip.width(),
   *                                                            kSrcImageClip.height()),
   *                                   drawRect.makeOutset(0.5f * drawRect.width(), drawRect.height()),
   *                                   SkSamplingOptions(), nullptr,
   *                                   SkCanvas::kStrict_SrcRectConstraint);
   *         }
   *
   *         {
   *             // skgpu::ganesh::SurfaceDrawContext::fillRectWithLocalMatrix.
   *             SkAutoCanvasRestore acr(canvas, true);
   *             SkPath path = SkPath::Line(
   *                    {kSrcImageClip.fLeft - kSrcImageClip.width(), kSrcImageClip.centerY()},
   *                    {kSrcImageClip.fRight + 3 * kSrcImageClip.width(), kSrcImageClip.centerY()});
   *             SkPaint paint;
   *             paint.setStyle(SkPaint::kStroke_Style);
   *             paint.setStrokeWidth(2 * kSrcImageClip.height());
   *             paint.setShader(fSrcImageShader);
   *             canvas->translate(23, 301);
   *             canvas->scale(300 / kSrcImageClip.width(), 100 / kSrcImageClip.height());
   *             canvas->translate(-kSrcImageClip.left(), -kSrcImageClip.top());
   *             canvas->clipRect(kSrcImageClip);
   *             canvas->drawPath(path, paint);
   *         }
   *
   *         // TODO: assert the draw target only has one op in the post-MDB world.
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
