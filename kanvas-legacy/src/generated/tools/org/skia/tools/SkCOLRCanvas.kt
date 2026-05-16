package org.skia.tools

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkOpBuilder
import org.skia.core.THashMap
import org.skia.foundation.SkColor
import org.skia.foundation.SkGlyphID
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.skia.foundation.SkWStream
import org.skia.math.SkIRect
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.utils.SkNoDrawCanvas

/**
 * C++ original:
 * ```cpp
 * class SkCOLRCanvas : public SkNoDrawCanvas {
 * public:
 *     SkCOLRCanvas(SkRect                     glyphBounds,
 *                  const TestSVGTypeface&     typeface,
 *                  SkGlyphID                  glyphId,
 *                  TestSVGTypeface::GlyfInfo* glyf,
 *                  THashMap<SkColor, int>*    colors,
 *                  SkWStream*                 out)
 *             : SkNoDrawCanvas(glyphBounds.roundOut().width(), glyphBounds.roundOut().height())
 *             , fBaselineOffset(glyphBounds.top())
 *             , fTypeface(typeface)
 *             , fGlyphId(glyphId)
 *             , fGlyf(glyf)
 *             , fColors(colors)
 *             , fOut(out)
 *             , fLayerId(0) {}
 *
 *     void writePoint(SkScalar x, SkScalar y, bool on) {
 *         fOut->writeText("        <pt x=\"");
 *         fOut->writeDecAsText(SkScalarRoundToInt(x));
 *         fOut->writeText("\" y=\"");
 *         fOut->writeDecAsText(SkScalarRoundToInt(y));
 *         fOut->writeText("\" on=\"");
 *         fOut->write8(on ? '1' : '0');
 *         fOut->writeText("\"/>\n");
 *     }
 *     SkIRect writePath(const SkPath& path, bool layer) {
 *         // Convert to quads.
 *         SkPath quads = path_to_quads(path);
 *
 *         SkRect  bounds  = quads.computeTightBounds();
 *         SkIRect ibounds = bounds.roundOut();
 *         // The bounds will be re-calculated anyway.
 *         fOut->writeText("    <TTGlyph name=\"glyf");
 *         fOut->writeHexAsText(fGlyphId, 4);
 *         if (layer) {
 *             fOut->writeText("l");
 *             fOut->writeHexAsText(fLayerId, 4);
 *         }
 *         fOut->writeText("\" xMin=\"");
 *         fOut->writeDecAsText(ibounds.fLeft);
 *         fOut->writeText("\" yMin=\"");
 *         fOut->writeDecAsText(ibounds.fTop);
 *         fOut->writeText("\" xMax=\"");
 *         fOut->writeDecAsText(ibounds.fRight);
 *         fOut->writeText("\" yMax=\"");
 *         fOut->writeDecAsText(ibounds.fBottom);
 *         fOut->writeText("\">\n");
 *
 *         bool contourOpen = false;
 *         for (auto [verb, pts, w] : SkPathPriv::Iterate(quads)) {
 *             switch (verb) {
 *                 case SkPathVerb::kMove:
 *                     if (contourOpen) {
 *                         fOut->writeText("      </contour>\n");
 *                         contourOpen = false;
 *                     }
 *                     break;
 *                 case SkPathVerb::kLine:
 *                     if (!contourOpen) {
 *                         fOut->writeText("      <contour>\n");
 *                         this->writePoint(pts[0].fX, pts[0].fY, true);
 *                         contourOpen = true;
 *                     }
 *                     this->writePoint(pts[1].fX, pts[1].fY, true);
 *                     break;
 *                 case SkPathVerb::kQuad:
 *                     if (!contourOpen) {
 *                         fOut->writeText("      <contour>\n");
 *                         this->writePoint(pts[0].fX, pts[0].fY, true);
 *                         contourOpen = true;
 *                     }
 *                     this->writePoint(pts[1].fX, pts[1].fY, false);
 *                     this->writePoint(pts[2].fX, pts[2].fY, true);
 *                     break;
 *                 case SkPathVerb::kClose:
 *                     if (contourOpen) {
 *                         fOut->writeText("      </contour>\n");
 *                         contourOpen = false;
 *                     }
 *                     break;
 *                 default: SkDEBUGFAIL("bad verb"); return ibounds;
 *             }
 *         }
 *         if (contourOpen) {
 *             fOut->writeText("      </contour>\n");
 *         }
 *
 *         // Required to write out an instructions tag.
 *         fOut->writeText("      <instructions/>\n");
 *         fOut->writeText("    </TTGlyph>\n");
 *         return ibounds;
 *     }
 *
 *     void onDrawRect(const SkRect& rect, const SkPaint& paint) override {
 *         this->drawPath(SkPath::Rect(rect), paint);
 *     }
 *
 *     void onDrawOval(const SkRect& oval, const SkPaint& paint) override {
 *         this->drawPath(SkPath::Oval(oval), paint);
 *     }
 *
 *     void onDrawArc(const SkRect&  oval,
 *                    SkScalar       startAngle,
 *                    SkScalar       sweepAngle,
 *                    bool           useCenter,
 *                    const SkPaint& paint) override {
 *         bool fillNoPathEffect = SkPaint::kFill_Style == paint.getStyle() && !paint.getPathEffect();
 *         SkPath path = SkPathPriv::CreateDrawArcPath(
 *                 SkArc::Make(oval, startAngle, sweepAngle, useCenter), fillNoPathEffect);
 *         this->drawPath(path, paint);
 *     }
 *
 *     void onDrawRRect(const SkRRect& rrect, const SkPaint& paint) override {
 *         this->drawPath(SkPath::RRect(rrect), paint);
 *     }
 *
 *     void onDrawPath(const SkPath& platonicPath, const SkPaint& originalPaint) override {
 *         SkPaint paint = originalPaint;
 *         SkPath  path  = platonicPath;
 *
 *         // Apply the path effect.
 *         if (paint.getPathEffect() || paint.getStyle() != SkPaint::kFill_Style) {
 *             SkPathBuilder builder;
 *             bool fill = skpathutils::FillPathWithPaint(path, paint, &builder);
 *             path = builder.detach();
 *
 *             paint.setPathEffect(nullptr);
 *             if (fill) {
 *                 paint.setStyle(SkPaint::kFill_Style);
 *             } else {
 *                 paint.setStyle(SkPaint::kStroke_Style);
 *                 paint.setStrokeWidth(0);
 *             }
 *         }
 *
 *         // Apply the matrix.
 *         SkMatrix m = this->getTotalMatrix();
 *         // If done to the canvas then everything would get clipped out.
 *         m.postTranslate(0, fBaselineOffset);  // put the baseline at 0
 *         m.postScale(1, -1);                   // and flip it since OpenType is y-up.
 *         path = path.makeTransform(m);
 *
 *         // While creating the default glyf, union with dark colors and intersect with bright colors.
 *         SkColor  color = paint.getColor();
 *         SkPathOp op;
 *         if (fTypeface.getPathOp(color, &op)) {
 *             fBasePath.add(path, op);
 *         }
 *         SkIRect bounds = this->writePath(path, true);
 *
 *         // The CPAL table has the concept of a 'current color' which is index 0xFFFF.
 *         // Mark any layer drawn in 'currentColor' as having this special index.
 *         // The value of 'currentColor' here should a color which causes this layer to union into the
 *         // default glyf.
 *         constexpr SkColor currentColor = 0xFF2B0000;
 *
 *         int colorIndex;
 *         if (color == currentColor) {
 *             colorIndex = 0xFFFF;
 *         } else {
 *             int* colorIndexPtr = fColors->find(color);
 *             if (colorIndexPtr) {
 *                 colorIndex = *colorIndexPtr;
 *             } else {
 *                 colorIndex = fColors->count();
 *                 fColors->set(color, colorIndex);
 *             }
 *         }
 *         fGlyf->fLayers.emplace_back(colorIndex, bounds);
 *
 *         ++fLayerId;
 *     }
 *
 *     void finishGlyph() {
 *         SkPath baseGlyph = fBasePath.resolve().value_or(SkPath());
 *         fGlyf->fBounds = this->writePath(baseGlyph, false);
 *     }
 *
 * private:
 *     SkScalar                   fBaselineOffset;
 *     const TestSVGTypeface&     fTypeface;
 *     SkGlyphID                  fGlyphId;
 *     TestSVGTypeface::GlyfInfo* fGlyf;
 *     THashMap<SkColor, int>*    fColors;
 *     SkWStream* const           fOut;
 *     SkOpBuilder                fBasePath;
 *     int                        fLayerId;
 * }
 * ```
 */
public open class SkCOLRCanvas public constructor(
  glyphBounds: SkRect,
  typeface: TestSVGTypeface,
  glyphId: SkGlyphID,
  glyf: TestSVGTypeface.GlyfInfo?,
  colors: THashMap<SkColor, Int>?,
  `out`: SkWStream?,
) : SkNoDrawCanvas(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkScalar                   fBaselineOffset
   * ```
   */
  private var fBaselineOffset: SkScalar = TODO("Initialize fBaselineOffset")

  /**
   * C++ original:
   * ```cpp
   * const TestSVGTypeface&     fTypeface
   * ```
   */
  private val fTypeface: TestSVGTypeface = TODO("Initialize fTypeface")

  /**
   * C++ original:
   * ```cpp
   * SkGlyphID                  fGlyphId
   * ```
   */
  private var fGlyphId: SkGlyphID = TODO("Initialize fGlyphId")

  /**
   * C++ original:
   * ```cpp
   * TestSVGTypeface::GlyfInfo* fGlyf
   * ```
   */
  private var fGlyf: TestSVGTypeface.GlyfInfo? = TODO("Initialize fGlyf")

  /**
   * C++ original:
   * ```cpp
   * THashMap<SkColor, int>*    fColors
   * ```
   */
  private var fColors: Int? = TODO("Initialize fColors")

  /**
   * C++ original:
   * ```cpp
   * SkWStream* const           fOut
   * ```
   */
  private val fOut: SkWStream? = TODO("Initialize fOut")

  /**
   * C++ original:
   * ```cpp
   * SkOpBuilder                fBasePath
   * ```
   */
  private var fBasePath: SkOpBuilder = TODO("Initialize fBasePath")

  /**
   * C++ original:
   * ```cpp
   * int                        fLayerId
   * ```
   */
  private var fLayerId: Int = TODO("Initialize fLayerId")

  /**
   * C++ original:
   * ```cpp
   * void writePoint(SkScalar x, SkScalar y, bool on) {
   *         fOut->writeText("        <pt x=\"");
   *         fOut->writeDecAsText(SkScalarRoundToInt(x));
   *         fOut->writeText("\" y=\"");
   *         fOut->writeDecAsText(SkScalarRoundToInt(y));
   *         fOut->writeText("\" on=\"");
   *         fOut->write8(on ? '1' : '0');
   *         fOut->writeText("\"/>\n");
   *     }
   * ```
   */
  public fun writePoint(
    x: SkScalar,
    y: SkScalar,
    on: Boolean,
  ) {
    TODO("Implement writePoint")
  }

  /**
   * C++ original:
   * ```cpp
   * SkIRect writePath(const SkPath& path, bool layer) {
   *         // Convert to quads.
   *         SkPath quads = path_to_quads(path);
   *
   *         SkRect  bounds  = quads.computeTightBounds();
   *         SkIRect ibounds = bounds.roundOut();
   *         // The bounds will be re-calculated anyway.
   *         fOut->writeText("    <TTGlyph name=\"glyf");
   *         fOut->writeHexAsText(fGlyphId, 4);
   *         if (layer) {
   *             fOut->writeText("l");
   *             fOut->writeHexAsText(fLayerId, 4);
   *         }
   *         fOut->writeText("\" xMin=\"");
   *         fOut->writeDecAsText(ibounds.fLeft);
   *         fOut->writeText("\" yMin=\"");
   *         fOut->writeDecAsText(ibounds.fTop);
   *         fOut->writeText("\" xMax=\"");
   *         fOut->writeDecAsText(ibounds.fRight);
   *         fOut->writeText("\" yMax=\"");
   *         fOut->writeDecAsText(ibounds.fBottom);
   *         fOut->writeText("\">\n");
   *
   *         bool contourOpen = false;
   *         for (auto [verb, pts, w] : SkPathPriv::Iterate(quads)) {
   *             switch (verb) {
   *                 case SkPathVerb::kMove:
   *                     if (contourOpen) {
   *                         fOut->writeText("      </contour>\n");
   *                         contourOpen = false;
   *                     }
   *                     break;
   *                 case SkPathVerb::kLine:
   *                     if (!contourOpen) {
   *                         fOut->writeText("      <contour>\n");
   *                         this->writePoint(pts[0].fX, pts[0].fY, true);
   *                         contourOpen = true;
   *                     }
   *                     this->writePoint(pts[1].fX, pts[1].fY, true);
   *                     break;
   *                 case SkPathVerb::kQuad:
   *                     if (!contourOpen) {
   *                         fOut->writeText("      <contour>\n");
   *                         this->writePoint(pts[0].fX, pts[0].fY, true);
   *                         contourOpen = true;
   *                     }
   *                     this->writePoint(pts[1].fX, pts[1].fY, false);
   *                     this->writePoint(pts[2].fX, pts[2].fY, true);
   *                     break;
   *                 case SkPathVerb::kClose:
   *                     if (contourOpen) {
   *                         fOut->writeText("      </contour>\n");
   *                         contourOpen = false;
   *                     }
   *                     break;
   *                 default: SkDEBUGFAIL("bad verb"); return ibounds;
   *             }
   *         }
   *         if (contourOpen) {
   *             fOut->writeText("      </contour>\n");
   *         }
   *
   *         // Required to write out an instructions tag.
   *         fOut->writeText("      <instructions/>\n");
   *         fOut->writeText("    </TTGlyph>\n");
   *         return ibounds;
   *     }
   * ```
   */
  public fun writePath(path: SkPath, layer: Boolean): SkIRect {
    TODO("Implement writePath")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawRect(const SkRect& rect, const SkPaint& paint) override {
   *         this->drawPath(SkPath::Rect(rect), paint);
   *     }
   * ```
   */
  public override fun onDrawRect(rect: SkRect, paint: SkPaint) {
    TODO("Implement onDrawRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawOval(const SkRect& oval, const SkPaint& paint) override {
   *         this->drawPath(SkPath::Oval(oval), paint);
   *     }
   * ```
   */
  public override fun onDrawOval(oval: SkRect, paint: SkPaint) {
    TODO("Implement onDrawOval")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawArc(const SkRect&  oval,
   *                    SkScalar       startAngle,
   *                    SkScalar       sweepAngle,
   *                    bool           useCenter,
   *                    const SkPaint& paint) override {
   *         bool fillNoPathEffect = SkPaint::kFill_Style == paint.getStyle() && !paint.getPathEffect();
   *         SkPath path = SkPathPriv::CreateDrawArcPath(
   *                 SkArc::Make(oval, startAngle, sweepAngle, useCenter), fillNoPathEffect);
   *         this->drawPath(path, paint);
   *     }
   * ```
   */
  public override fun onDrawArc(
    oval: SkRect,
    startAngle: SkScalar,
    sweepAngle: SkScalar,
    useCenter: Boolean,
    paint: SkPaint,
  ) {
    TODO("Implement onDrawArc")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawRRect(const SkRRect& rrect, const SkPaint& paint) override {
   *         this->drawPath(SkPath::RRect(rrect), paint);
   *     }
   * ```
   */
  public override fun onDrawRRect(rrect: SkRRect, paint: SkPaint) {
    TODO("Implement onDrawRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawPath(const SkPath& platonicPath, const SkPaint& originalPaint) override {
   *         SkPaint paint = originalPaint;
   *         SkPath  path  = platonicPath;
   *
   *         // Apply the path effect.
   *         if (paint.getPathEffect() || paint.getStyle() != SkPaint::kFill_Style) {
   *             SkPathBuilder builder;
   *             bool fill = skpathutils::FillPathWithPaint(path, paint, &builder);
   *             path = builder.detach();
   *
   *             paint.setPathEffect(nullptr);
   *             if (fill) {
   *                 paint.setStyle(SkPaint::kFill_Style);
   *             } else {
   *                 paint.setStyle(SkPaint::kStroke_Style);
   *                 paint.setStrokeWidth(0);
   *             }
   *         }
   *
   *         // Apply the matrix.
   *         SkMatrix m = this->getTotalMatrix();
   *         // If done to the canvas then everything would get clipped out.
   *         m.postTranslate(0, fBaselineOffset);  // put the baseline at 0
   *         m.postScale(1, -1);                   // and flip it since OpenType is y-up.
   *         path = path.makeTransform(m);
   *
   *         // While creating the default glyf, union with dark colors and intersect with bright colors.
   *         SkColor  color = paint.getColor();
   *         SkPathOp op;
   *         if (fTypeface.getPathOp(color, &op)) {
   *             fBasePath.add(path, op);
   *         }
   *         SkIRect bounds = this->writePath(path, true);
   *
   *         // The CPAL table has the concept of a 'current color' which is index 0xFFFF.
   *         // Mark any layer drawn in 'currentColor' as having this special index.
   *         // The value of 'currentColor' here should a color which causes this layer to union into the
   *         // default glyf.
   *         constexpr SkColor currentColor = 0xFF2B0000;
   *
   *         int colorIndex;
   *         if (color == currentColor) {
   *             colorIndex = 0xFFFF;
   *         } else {
   *             int* colorIndexPtr = fColors->find(color);
   *             if (colorIndexPtr) {
   *                 colorIndex = *colorIndexPtr;
   *             } else {
   *                 colorIndex = fColors->count();
   *                 fColors->set(color, colorIndex);
   *             }
   *         }
   *         fGlyf->fLayers.emplace_back(colorIndex, bounds);
   *
   *         ++fLayerId;
   *     }
   * ```
   */
  public override fun onDrawPath(platonicPath: SkPath, originalPaint: SkPaint) {
    TODO("Implement onDrawPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void finishGlyph() {
   *         SkPath baseGlyph = fBasePath.resolve().value_or(SkPath());
   *         fGlyf->fBounds = this->writePath(baseGlyph, false);
   *     }
   * ```
   */
  public fun finishGlyph() {
    TODO("Implement finishGlyph")
  }
}
