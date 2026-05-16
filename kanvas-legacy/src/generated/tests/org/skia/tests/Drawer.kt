package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkSp
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class Drawer {
 *  public:
 *     explicit Drawer() : fImageInfo(SkImageInfo::MakeN32Premul(200, 100)) {
 *         auto surf = SkSurfaces::Raster(SkImageInfo::MakeN32Premul(100, 100));
 *         surf->getCanvas()->clear(0xffffffff);
 *         SkPaint circlePaint;
 *         circlePaint.setColor(0xff000000);
 *         surf->getCanvas()->drawCircle(50, 50, 50, circlePaint);
 *         fCircleImage = surf->makeImageSnapshot();
 *     }
 *
 *     const SkImageInfo& imageInfo() const { return fImageInfo; }
 *
 *     void draw(SkCanvas* canvas, const SkRect& clipRect, SkBlendMode mode) const {
 *         SkPaint greenPaint;
 *         greenPaint.setColor(0xff008000);
 *         SkPaint blackPaint;
 *         blackPaint.setColor(0xff000000);
 *         SkPaint whitePaint;
 *         whitePaint.setColor(0xffffffff);
 *         SkPaint layerPaint;
 *         layerPaint.setColor(0xff000000);
 *         layerPaint.setBlendMode(mode);
 *         SkRect canvasRect(SkRect::MakeWH(SkIntToScalar(fImageInfo.width()),
 *                                          SkIntToScalar(fImageInfo.height())));
 *
 *         canvas->clipRect(clipRect);
 *         canvas->clear(0xff000000);
 *
 *         canvas->saveLayer(nullptr, &blackPaint);
 *             canvas->drawRect(canvasRect, greenPaint);
 *             canvas->saveLayer(nullptr, &layerPaint);
 *                 canvas->drawImageRect(fCircleImage, SkRect::MakeXYWH(20,20,60,60),
 *                                       SkSamplingOptions(), &blackPaint);
 *             canvas->restore();
 *         canvas->restore();
 *     }
 *
 *  private:
 *     const SkImageInfo fImageInfo;
 *     sk_sp<SkImage> fCircleImage;
 * }
 * ```
 */
public data class Drawer public constructor(
  /**
   * C++ original:
   * ```cpp
   * const SkImageInfo fImageInfo
   * ```
   */
  private val fImageInfo: SkImageInfo,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fCircleImage
   * ```
   */
  private var fCircleImage: SkSp<SkImage>,
) {
  /**
   * C++ original:
   * ```cpp
   * const SkImageInfo& imageInfo() const { return fImageInfo; }
   * ```
   */
  public fun imageInfo(): SkImageInfo {
    TODO("Implement imageInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * void draw(SkCanvas* canvas, const SkRect& clipRect, SkBlendMode mode) const {
   *         SkPaint greenPaint;
   *         greenPaint.setColor(0xff008000);
   *         SkPaint blackPaint;
   *         blackPaint.setColor(0xff000000);
   *         SkPaint whitePaint;
   *         whitePaint.setColor(0xffffffff);
   *         SkPaint layerPaint;
   *         layerPaint.setColor(0xff000000);
   *         layerPaint.setBlendMode(mode);
   *         SkRect canvasRect(SkRect::MakeWH(SkIntToScalar(fImageInfo.width()),
   *                                          SkIntToScalar(fImageInfo.height())));
   *
   *         canvas->clipRect(clipRect);
   *         canvas->clear(0xff000000);
   *
   *         canvas->saveLayer(nullptr, &blackPaint);
   *             canvas->drawRect(canvasRect, greenPaint);
   *             canvas->saveLayer(nullptr, &layerPaint);
   *                 canvas->drawImageRect(fCircleImage, SkRect::MakeXYWH(20,20,60,60),
   *                                       SkSamplingOptions(), &blackPaint);
   *             canvas->restore();
   *         canvas->restore();
   *     }
   * ```
   */
  public fun draw(
    canvas: SkCanvas?,
    clipRect: SkRect,
    mode: SkBlendMode,
  ) {
    TODO("Implement draw")
  }
}
