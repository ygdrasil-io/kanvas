package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.core.SkPicture
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class PictureCullRectGM : public skiagm::GM {
 * public:
 *     PictureCullRectGM()
 *         : fPicture(nullptr)
 *     {}
 *
 * protected:
 *     void onOnceBeforeDraw() override {
 *         SkPictureRecorder rec;
 *         SkRTreeFactory rtreeFactory;
 *         SkCanvas* canvas = rec.beginRecording(100, 100, &rtreeFactory);
 *
 *         SkPaint paint;
 *         paint.setAntiAlias(false);
 *
 *         SkRect rect = SkRect::MakeLTRB(0, 80, 100, 100);
 *
 *         // Make picture complex enough to trigger the cull rect and bbh (RTree) computations.
 *         // (A single drawRect won't trigger it.)
 *         paint.setColor(0x800000FF);
 *         canvas->drawRect(rect, paint);
 *         canvas->drawOval(rect, paint);
 *
 *         fPicture = rec.finishRecordingAsPicture();
 *
 *         SkASSERT(fPicture->cullRect().top() == 80);
 *     }
 *
 *     SkString getName() const override { return SkString("picture_cull_rect"); }
 *
 *     SkISize getISize() override { return SkISize::Make(120, 120); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->clipRect(SkRect::MakeLTRB(0, 60, 120, 120));
 *         canvas->translate(10, 10);
 *         canvas->drawPicture(fPicture);
 *     }
 *
 * private:
 *     sk_sp<SkPicture> fPicture;
 *
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class PictureCullRectGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkPicture> fPicture
   * ```
   */
  private var fPicture: SkSp<SkPicture> = TODO("Initialize fPicture")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         SkPictureRecorder rec;
   *         SkRTreeFactory rtreeFactory;
   *         SkCanvas* canvas = rec.beginRecording(100, 100, &rtreeFactory);
   *
   *         SkPaint paint;
   *         paint.setAntiAlias(false);
   *
   *         SkRect rect = SkRect::MakeLTRB(0, 80, 100, 100);
   *
   *         // Make picture complex enough to trigger the cull rect and bbh (RTree) computations.
   *         // (A single drawRect won't trigger it.)
   *         paint.setColor(0x800000FF);
   *         canvas->drawRect(rect, paint);
   *         canvas->drawOval(rect, paint);
   *
   *         fPicture = rec.finishRecordingAsPicture();
   *
   *         SkASSERT(fPicture->cullRect().top() == 80);
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("picture_cull_rect"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(120, 120); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         canvas->clipRect(SkRect::MakeLTRB(0, 60, 120, 120));
   *         canvas->translate(10, 10);
   *         canvas->drawPicture(fPicture);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
