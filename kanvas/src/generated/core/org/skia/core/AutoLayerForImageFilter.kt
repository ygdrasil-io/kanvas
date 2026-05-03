package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class AutoLayerForImageFilter {
 * public:
 *     // `rawBounds` is the original bounds of the primitive about to be drawn, unmodified by the
 *     // paint. It's used to determine the size of the offscreen layer for filters.
 *     // If null, the clip will be used instead.
 *     //
 *     // If `skipMaskFilterLayer` is true, any SkMaskFilter on `paint` will be left as-is and is
 *     // assumed to be handled by the SkDevice that handles the draw.
 *     //
 *     // Draw functions should use layer->paint() instead of the passed-in paint.
 *     AutoLayerForImageFilter(SkCanvas* canvas,
 *                             const SkPaint& paint,
 *                             const SkRect* rawBounds,
 *                             bool skipMaskFilterLayer);
 *
 *     AutoLayerForImageFilter(const AutoLayerForImageFilter&) = delete;
 *     AutoLayerForImageFilter& operator=(const AutoLayerForImageFilter&) = delete;
 *     AutoLayerForImageFilter(AutoLayerForImageFilter&&);
 *     AutoLayerForImageFilter& operator=(AutoLayerForImageFilter&&);
 *
 *     ~AutoLayerForImageFilter();
 *
 *     const SkPaint& paint() const { return fPaint; }
 *
 *     // This is public so that a canvas can attempt to specially handle mask filters, specifically
 *     // for blurs, and then if the attempt fails fall back on a regular draw with the same autolayer.
 *     void addMaskFilterLayer(const SkRect* drawBounds);
 *
 * private:
 *     void addImageFilterLayer(const SkRect* drawBounds);
 *
 *     void addLayer(const SkPaint& restorePaint, const SkRect* drawBounds, bool coverageOnly);
 *
 *     SkPaint         fPaint;
 *     SkCanvas*       fCanvas;
 *     int             fTempLayersForFilters;
 *
 *     SkDEBUGCODE(int fSaveCount;)
 * }
 * ```
 */
public data class AutoLayerForImageFilter public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkPaint         fPaint
   * ```
   */
  private var fPaint: SkPaint,
  /**
   * C++ original:
   * ```cpp
   * SkCanvas*       fCanvas
   * ```
   */
  private var fCanvas: SkCanvas?,
  /**
   * C++ original:
   * ```cpp
   * int             fTempLayersForFilters
   * ```
   */
  private var fTempLayersForFilters: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * AutoLayerForImageFilter& operator=(const AutoLayerForImageFilter&) = delete
   * ```
   */
  public fun assign(param0: AutoLayerForImageFilter) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * AutoLayerForImageFilter& AutoLayerForImageFilter::operator=(AutoLayerForImageFilter&& other) {
   *     fPaint = std::move(other.fPaint);
   *     fCanvas = other.fCanvas;
   *     fTempLayersForFilters = other.fTempLayersForFilters;
   *     SkDEBUGCODE(fSaveCount = other.fSaveCount;)
   *
   *     other.fTempLayersForFilters = 0;
   *     SkDEBUGCODE(other.fSaveCount = -1;)
   *
   *     return *this;
   * }
   * ```
   */
  public fun paint(): SkPaint {
    TODO("Implement paint")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkPaint& paint() const { return fPaint; }
   * ```
   */
  public fun addMaskFilterLayer(drawBounds: SkRect?) {
    TODO("Implement addMaskFilterLayer")
  }

  /**
   * C++ original:
   * ```cpp
   * void AutoLayerForImageFilter::addMaskFilterLayer(const SkRect* drawBounds) {
   *     // Shouldn't be adding a layer if there was no mask filter to begin with.
   *     SkASSERT(fPaint.getMaskFilter());
   *
   *     // Image filters are evaluated after mask filters so any filter should have been converted to
   *     // a layer and removed from fPaint already.
   *     SkASSERT(!fPaint.getImageFilter());
   *
   *     // TODO: Eventually all SkMaskFilters will implement this method so this can switch to an assert
   *     auto [maskFilterAsImageFilter, appliesShading] = as_MFB(
   *         fPaint.getMaskFilter())->asImageFilter(fCanvas->getTotalMatrix(), fPaint);
   *     if (!maskFilterAsImageFilter) {
   *         // This is a legacy mask filter that can be handled by raster and Ganesh directly, but will
   *         // be ignored by Graphite. Return now, leaving the paint with the mask filter so that the
   *         // underlying SkDevice can handle it if it will.
   *         return;
   *     }
   *
   *     // The restore paint for the coverage layer takes over all shading effects that had been on the
   *     // original paint, which will be applied to the alpha-only output image from the mask filter
   *     // converted to an image filter.
   *     // If we know our mask filter will affect shading, we don't want to add the original shading
   *     // into the restore paint.
   *     SkPaint restorePaint;
   *     if (!appliesShading) {
   *         restorePaint.setColor4f(fPaint.getColor4f());
   *         restorePaint.setShader(fPaint.refShader());
   *         restorePaint.setColorFilter(fPaint.refColorFilter());
   *         restorePaint.setDither(fPaint.isDither());
   *     }
   *     restorePaint.setBlender(fPaint.refBlender());
   *     restorePaint.setImageFilter(maskFilterAsImageFilter);
   *
   *     // Remove all shading effects from the "working" paint so that the layer's alpha channel
   *     // will correspond to the coverage. This leaves the original style and AA settings that
   *     // contribute to coverage (including any path effect).
   *     fPaint.setColor4f(SkColors::kWhite);
   *     fPaint.setShader(nullptr);
   *     fPaint.setColorFilter(nullptr);
   *     fPaint.setMaskFilter(nullptr);
   *     fPaint.setDither(false);
   *     fPaint.setBlendMode(SkBlendMode::kSrcOver);
   *
   *     this->addLayer(restorePaint, drawBounds, /*coverageOnly=*/!appliesShading);
   * }
   * ```
   */
  private fun addImageFilterLayer(drawBounds: SkRect?) {
    TODO("Implement addImageFilterLayer")
  }

  /**
   * C++ original:
   * ```cpp
   * void AutoLayerForImageFilter::addImageFilterLayer(const SkRect* drawBounds) {
   *     // Shouldn't be adding a layer if there was no image filter to begin with.
   *     SkASSERT(fPaint.getImageFilter());
   *
   *     // The restore paint for an image filter layer simply takes the image filter and blending off
   *     // the original paint. The blending is applied post image filter because otherwise it'd be
   *     // applied with the new layer's transparent dst and not be very interesting.
   *     SkPaint restorePaint;
   *     restorePaint.setImageFilter(fPaint.refImageFilter());
   *     restorePaint.setBlender(fPaint.refBlender());
   *
   *     // Remove the restorePaint fields from our "working" paint, leaving all other shading and
   *     // geometry effects to be rendered into the layer. If there happens to be a mask filter, this
   *     // paint will still trigger a second layer for that filter.
   *     fPaint.setImageFilter(nullptr);
   *     fPaint.setBlendMode(SkBlendMode::kSrcOver);
   *
   *     this->addLayer(restorePaint, drawBounds, /*coverageOnly=*/false);
   * }
   * ```
   */
  private fun addLayer(
    restorePaint: SkPaint,
    drawBounds: SkRect?,
    coverageOnly: Boolean,
  ) {
    TODO("Implement addLayer")
  }
}
