package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class PathInverseFillGM : public skiagm::GM {
 *     SkPath  fPath[N];
 *     SkScalar fDY[N];
 * protected:
 *     void onOnceBeforeDraw() override {
 *         for (size_t i = 0; i < N; i++) {
 *             auto [path, dy] = gProcs[i]();
 *             fPath[i] = path;
 *             fDY[i] = dy;
 *         }
 *     }
 *
 *     SkString getName() const override { return SkString("pathinvfill"); }
 *
 *     SkISize getISize() override { return SkISize::Make(450, 220); }
 *
 *     static void show(SkCanvas* canvas, const SkPath& path, const SkPaint& paint,
 *                      const SkRect* clip, SkScalar top, const SkScalar bottom) {
 *         canvas->save();
 *         if (clip) {
 *             SkRect r = *clip;
 *             r.fTop = top;
 *             r.fBottom = bottom;
 *             canvas->clipRect(r);
 *         }
 *         canvas->drawPath(path, paint);
 *         canvas->restore();
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPath path = SkPathBuilder().addCircle(50, 50, 40)
 *                                      .toggleInverseFillType()
 *                                      .detach();
 *
 *         SkRect clipR = {0, 0, 100, 200};
 *
 *         canvas->translate(10, 10);
 *
 *         for (int doclip = 0; doclip <= 1; ++doclip) {
 *             for (int aa = 0; aa <= 1; ++aa) {
 *                 SkPaint paint;
 *                 paint.setAntiAlias(SkToBool(aa));
 *
 *                 canvas->save();
 *                 canvas->clipRect(clipR);
 *
 *                 const SkRect* clipPtr = doclip ? &clipR : nullptr;
 *
 *                 show(canvas, path, paint, clipPtr, clipR.fTop, clipR.centerY());
 *                 show(canvas, path, paint, clipPtr, clipR.centerY(), clipR.fBottom);
 *
 *                 canvas->restore();
 *                 canvas->translate(SkIntToScalar(110), 0);
 *             }
 *         }
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class PathInverseFillGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkPath  fPath
   * ```
   */
  private var fPath: SkPath = TODO("Initialize fPath")

  /**
   * C++ original:
   * ```cpp
   * SkScalar fDY
   * ```
   */
  private var fDY: SkScalar = TODO("Initialize fDY")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         for (size_t i = 0; i < N; i++) {
   *             auto [path, dy] = gProcs[i]();
   *             fPath[i] = path;
   *             fDY[i] = dy;
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
   * SkString getName() const override { return SkString("pathinvfill"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(450, 220); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPath path = SkPathBuilder().addCircle(50, 50, 40)
   *                                      .toggleInverseFillType()
   *                                      .detach();
   *
   *         SkRect clipR = {0, 0, 100, 200};
   *
   *         canvas->translate(10, 10);
   *
   *         for (int doclip = 0; doclip <= 1; ++doclip) {
   *             for (int aa = 0; aa <= 1; ++aa) {
   *                 SkPaint paint;
   *                 paint.setAntiAlias(SkToBool(aa));
   *
   *                 canvas->save();
   *                 canvas->clipRect(clipR);
   *
   *                 const SkRect* clipPtr = doclip ? &clipR : nullptr;
   *
   *                 show(canvas, path, paint, clipPtr, clipR.fTop, clipR.centerY());
   *                 show(canvas, path, paint, clipPtr, clipR.centerY(), clipR.fBottom);
   *
   *                 canvas->restore();
   *                 canvas->translate(SkIntToScalar(110), 0);
   *             }
   *         }
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
     * static void show(SkCanvas* canvas, const SkPath& path, const SkPaint& paint,
     *                      const SkRect* clip, SkScalar top, const SkScalar bottom) {
     *         canvas->save();
     *         if (clip) {
     *             SkRect r = *clip;
     *             r.fTop = top;
     *             r.fBottom = bottom;
     *             canvas->clipRect(r);
     *         }
     *         canvas->drawPath(path, paint);
     *         canvas->restore();
     *     }
     * ```
     */
    protected fun show(
      canvas: SkCanvas?,
      path: SkPath,
      paint: SkPaint,
      clip: SkRect?,
      top: SkScalar,
      bottom: SkScalar,
    ) {
      TODO("Implement show")
    }
  }
}
