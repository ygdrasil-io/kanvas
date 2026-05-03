package org.skia.core

import org.skia.foundation.SkBitmap
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSurfaceProps
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class TextDevice : public SkNoPixelsDevice, public skcpu::BitmapDevicePainter {
 * public:
 *     TextDevice(SkCanvas* overdrawCanvas, const SkSurfaceProps& props)
 *             : SkNoPixelsDevice{SkIRect::MakeWH(32767, 32767), props},
 *               fOverdrawCanvas{overdrawCanvas},
 *               fPainter{props, kN32_SkColorType, nullptr} {}
 *
 *     void paintMasks(SkZip<const SkGlyph*, SkPoint> accepted, const SkPaint& paint) const override {
 *         for (auto [glyph, pos] : accepted) {
 *             SkMask mask = glyph->mask(pos);
 *             // We need to ignore any matrix on the overdraw canvas (it's already been baked into
 *             // our glyph positions). Otherwise, the CTM is double-applied. (skbug.com/40044818)
 *             fOverdrawCanvas->save();
 *             fOverdrawCanvas->resetMatrix();
 *             fOverdrawCanvas->drawRect(SkRect::Make(mask.fBounds), SkPaint());
 *             fOverdrawCanvas->restore();
 *         }
 *     }
 *
 *     void drawBitmap(const SkBitmap&, const SkMatrix&, const SkRect* dstOrNull,
 *                     const SkSamplingOptions&, const SkPaint&) const override {}
 *
 *     void onDrawGlyphRunList(SkCanvas* canvas,
 *                             const sktext::GlyphRunList& glyphRunList,
 *                             const SkPaint& paint) override {
 *         SkASSERT(!glyphRunList.hasRSXForm());
 *         fPainter.drawForBitmapDevice(
 *                 canvas, this, glyphRunList, paint, fOverdrawCanvas->getTotalMatrix());
 *     }
 *
 * private:
 *     SkCanvas* const fOverdrawCanvas;
 *     skcpu::GlyphRunListPainter fPainter;
 * }
 * ```
 */
public open class TextDevice public constructor(
  overdrawCanvas: SkCanvas?,
  props: SkSurfaceProps,
) : SkNoPixelsDevice(TODO(), TODO()),
    BitmapDevicePainter {
  /**
   * C++ original:
   * ```cpp
   * SkCanvas* const fOverdrawCanvas
   * ```
   */
  private val fOverdrawCanvas: SkCanvas? = TODO("Initialize fOverdrawCanvas")

  /**
   * C++ original:
   * ```cpp
   * skcpu::GlyphRunListPainter fPainter
   * ```
   */
  private var fPainter: GlyphRunListPainter = TODO("Initialize fPainter")

  /**
   * C++ original:
   * ```cpp
   * void paintMasks(SkZip<const SkGlyph*, SkPoint> accepted, const SkPaint& paint) const override {
   *         for (auto [glyph, pos] : accepted) {
   *             SkMask mask = glyph->mask(pos);
   *             // We need to ignore any matrix on the overdraw canvas (it's already been baked into
   *             // our glyph positions). Otherwise, the CTM is double-applied. (skbug.com/40044818)
   *             fOverdrawCanvas->save();
   *             fOverdrawCanvas->resetMatrix();
   *             fOverdrawCanvas->drawRect(SkRect::Make(mask.fBounds), SkPaint());
   *             fOverdrawCanvas->restore();
   *         }
   *     }
   * ```
   */
  public override fun paintMasks(accepted: SkZip<SkGlyph?, SkPoint>, paint: SkPaint) {
    TODO("Implement paintMasks")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawBitmap(const SkBitmap&, const SkMatrix&, const SkRect* dstOrNull,
   *                     const SkSamplingOptions&, const SkPaint&) const override {}
   * ```
   */
  public override fun drawBitmap(
    param0: SkBitmap,
    param1: SkMatrix,
    dstOrNull: SkRect?,
    param3: SkSamplingOptions,
    param4: SkPaint,
  ) {
    TODO("Implement drawBitmap")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawGlyphRunList(SkCanvas* canvas,
   *                             const sktext::GlyphRunList& glyphRunList,
   *                             const SkPaint& paint) override {
   *         SkASSERT(!glyphRunList.hasRSXForm());
   *         fPainter.drawForBitmapDevice(
   *                 canvas, this, glyphRunList, paint, fOverdrawCanvas->getTotalMatrix());
   *     }
   * ```
   */
  public override fun onDrawGlyphRunList(
    canvas: SkCanvas?,
    glyphRunList: GlyphRunList,
    paint: SkPaint,
  ) {
    TODO("Implement onDrawGlyphRunList")
  }
}
