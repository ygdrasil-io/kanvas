package org.skia.tests

import kotlin.String
import org.skia.core.SkAAClip
import org.skia.core.SkCanvas
import org.skia.core.SkClipOp
import org.skia.foundation.SkColor
import org.skia.foundation.SkPath
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SimpleClipGM : public GM {
 * public:
 *     enum SkGeomTypes {
 *         kRect_GeomType,
 *         kPath_GeomType,
 *         kAAClip_GeomType
 *     };
 *
 *     SimpleClipGM(SkGeomTypes geomType)
 *     : fGeomType(geomType) {
 *     }
 *
 * protected:
 *     void onOnceBeforeDraw() override {
 *         // offset the rects a bit so we get anti-aliasing in the rect case
 *         fBase.setLTRB(100.65f,
 *                       100.65f,
 *                       150.65f,
 *                       150.65f);
 *         fRect = fBase;
 *         fRect.inset(5, 5);
 *         fRect.offset(25, 25);
 *
 *         fBasePath = SkPath::RRect(fBase, 5, 5);
 *         fRectPath = SkPath::RRect(fRect, 5, 5);
 *         INHERITED::setBGColor(0xFFDDDDDD);
 *     }
 *
 *     void buildRgn(SkAAClip* clip, SkClipOp op) {
 *         clip->setPath(fBasePath, fBasePath.getBounds().roundOut(), true);
 *
 *         SkAAClip clip2;
 *         clip2.setPath(fRectPath, fRectPath.getBounds().roundOut(), true);
 *         clip->op(clip2, op);
 *     }
 *
 *     void drawOrig(SkCanvas* canvas) {
 *         SkPaint     paint;
 *
 *         paint.setStyle(SkPaint::kStroke_Style);
 *         paint.setColor(SK_ColorBLACK);
 *
 *         canvas->drawRect(fBase, paint);
 *         canvas->drawRect(fRect, paint);
 *     }
 *
 *     void drawRgnOped(SkCanvas* canvas, SkClipOp op, SkColor color) {
 *
 *         SkAAClip clip;
 *
 *         this->buildRgn(&clip, op);
 *         this->drawOrig(canvas);
 *
 *         SkPaint paint;
 *         paint.setColor(color);
 *         paint_rgn(canvas, clip, paint);
 *     }
 *
 *     void drawPathsOped(SkCanvas* canvas, SkClipOp op, SkColor color) {
 *
 *         this->drawOrig(canvas);
 *
 *         canvas->save();
 *
 *         // create the clip mask with the supplied boolean op
 *         if (kPath_GeomType == fGeomType) {
 *             // path-based case
 *             canvas->clipPath(fBasePath, true);
 *             canvas->clipPath(fRectPath, op, true);
 *         } else {
 *             // rect-based case
 *             canvas->clipRect(fBase, true);
 *             canvas->clipRect(fRect, op, true);
 *         }
 *
 *         // draw a rect that will entirely cover the clip mask area
 *         SkPaint paint;
 *         paint.setColor(color);
 *
 *         SkRect r = SkRect::MakeLTRB(SkIntToScalar(90),  SkIntToScalar(90),
 *                                     SkIntToScalar(180), SkIntToScalar(180));
 *
 *         canvas->drawRect(r, paint);
 *
 *         canvas->restore();
 *     }
 *
 *     SkString getName() const override {
 *         SkString str;
 *         str.printf("simpleaaclip_%s",
 *                     kRect_GeomType == fGeomType ? "rect" :
 *                     (kPath_GeomType == fGeomType ? "path" :
 *                     "aaclip"));
 *         return str;
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(500, 240); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *
 *         const struct {
 *             SkColor         fColor;
 *             const char*     fName;
 *             SkClipOp        fOp;
 *         } gOps[] = {
 *                 {SK_ColorBLACK, "Difference", SkClipOp::kDifference},
 *                 {SK_ColorRED, "Intersect", SkClipOp::kIntersect},
 *         };
 *
 *         SkPaint textPaint;
 *         SkFont  font(ToolUtils::DefaultPortableTypeface(), 24);
 *         int xOff = 0;
 *
 *         for (size_t op = 0; op < std::size(gOps); op++) {
 *             canvas->drawString(gOps[op].fName, 75.0f, 50.0f, font, textPaint);
 *
 *             if (kAAClip_GeomType == fGeomType) {
 *                 this->drawRgnOped(canvas, gOps[op].fOp, gOps[op].fColor);
 *             } else {
 *                 this->drawPathsOped(canvas, gOps[op].fOp, gOps[op].fColor);
 *             }
 *
 *             if (xOff >= 400) {
 *                 canvas->translate(SkIntToScalar(-400), SkIntToScalar(250));
 *                 xOff = 0;
 *             } else {
 *                 canvas->translate(SkIntToScalar(200), 0);
 *                 xOff += 200;
 *             }
 *         }
 *     }
 * private:
 *
 *     SkGeomTypes fGeomType;
 *
 *     SkRect fBase;
 *     SkRect fRect;
 *
 *     SkPath fBasePath;       // fBase as a round rect
 *     SkPath fRectPath;       // fRect as a round rect
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class SimpleClipGM public constructor(
  geomType: SkGeomTypes,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkGeomTypes fGeomType
   * ```
   */
  private var fGeomType: SkGeomTypes = TODO("Initialize fGeomType")

  /**
   * C++ original:
   * ```cpp
   * SkRect fBase
   * ```
   */
  private var fBase: SkRect = TODO("Initialize fBase")

  /**
   * C++ original:
   * ```cpp
   * SkRect fRect
   * ```
   */
  private var fRect: SkRect = TODO("Initialize fRect")

  /**
   * C++ original:
   * ```cpp
   * SkPath fBasePath
   * ```
   */
  private var fBasePath: SkPath = TODO("Initialize fBasePath")

  /**
   * C++ original:
   * ```cpp
   * SkPath fRectPath
   * ```
   */
  private var fRectPath: SkPath = TODO("Initialize fRectPath")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         // offset the rects a bit so we get anti-aliasing in the rect case
   *         fBase.setLTRB(100.65f,
   *                       100.65f,
   *                       150.65f,
   *                       150.65f);
   *         fRect = fBase;
   *         fRect.inset(5, 5);
   *         fRect.offset(25, 25);
   *
   *         fBasePath = SkPath::RRect(fBase, 5, 5);
   *         fRectPath = SkPath::RRect(fRect, 5, 5);
   *         INHERITED::setBGColor(0xFFDDDDDD);
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void buildRgn(SkAAClip* clip, SkClipOp op) {
   *         clip->setPath(fBasePath, fBasePath.getBounds().roundOut(), true);
   *
   *         SkAAClip clip2;
   *         clip2.setPath(fRectPath, fRectPath.getBounds().roundOut(), true);
   *         clip->op(clip2, op);
   *     }
   * ```
   */
  protected fun buildRgn(clip: SkAAClip?, op: SkClipOp) {
    TODO("Implement buildRgn")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawOrig(SkCanvas* canvas) {
   *         SkPaint     paint;
   *
   *         paint.setStyle(SkPaint::kStroke_Style);
   *         paint.setColor(SK_ColorBLACK);
   *
   *         canvas->drawRect(fBase, paint);
   *         canvas->drawRect(fRect, paint);
   *     }
   * ```
   */
  protected fun drawOrig(canvas: SkCanvas?) {
    TODO("Implement drawOrig")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawRgnOped(SkCanvas* canvas, SkClipOp op, SkColor color) {
   *
   *         SkAAClip clip;
   *
   *         this->buildRgn(&clip, op);
   *         this->drawOrig(canvas);
   *
   *         SkPaint paint;
   *         paint.setColor(color);
   *         paint_rgn(canvas, clip, paint);
   *     }
   * ```
   */
  protected fun drawRgnOped(
    canvas: SkCanvas?,
    op: SkClipOp,
    color: SkColor,
  ) {
    TODO("Implement drawRgnOped")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawPathsOped(SkCanvas* canvas, SkClipOp op, SkColor color) {
   *
   *         this->drawOrig(canvas);
   *
   *         canvas->save();
   *
   *         // create the clip mask with the supplied boolean op
   *         if (kPath_GeomType == fGeomType) {
   *             // path-based case
   *             canvas->clipPath(fBasePath, true);
   *             canvas->clipPath(fRectPath, op, true);
   *         } else {
   *             // rect-based case
   *             canvas->clipRect(fBase, true);
   *             canvas->clipRect(fRect, op, true);
   *         }
   *
   *         // draw a rect that will entirely cover the clip mask area
   *         SkPaint paint;
   *         paint.setColor(color);
   *
   *         SkRect r = SkRect::MakeLTRB(SkIntToScalar(90),  SkIntToScalar(90),
   *                                     SkIntToScalar(180), SkIntToScalar(180));
   *
   *         canvas->drawRect(r, paint);
   *
   *         canvas->restore();
   *     }
   * ```
   */
  protected fun drawPathsOped(
    canvas: SkCanvas?,
    op: SkClipOp,
    color: SkColor,
  ) {
    TODO("Implement drawPathsOped")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         SkString str;
   *         str.printf("simpleaaclip_%s",
   *                     kRect_GeomType == fGeomType ? "rect" :
   *                     (kPath_GeomType == fGeomType ? "path" :
   *                     "aaclip"));
   *         return str;
   *     }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(500, 240); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *
   *         const struct {
   *             SkColor         fColor;
   *             const char*     fName;
   *             SkClipOp        fOp;
   *         } gOps[] = {
   *                 {SK_ColorBLACK, "Difference", SkClipOp::kDifference},
   *                 {SK_ColorRED, "Intersect", SkClipOp::kIntersect},
   *         };
   *
   *         SkPaint textPaint;
   *         SkFont  font(ToolUtils::DefaultPortableTypeface(), 24);
   *         int xOff = 0;
   *
   *         for (size_t op = 0; op < std::size(gOps); op++) {
   *             canvas->drawString(gOps[op].fName, 75.0f, 50.0f, font, textPaint);
   *
   *             if (kAAClip_GeomType == fGeomType) {
   *                 this->drawRgnOped(canvas, gOps[op].fOp, gOps[op].fColor);
   *             } else {
   *                 this->drawPathsOped(canvas, gOps[op].fOp, gOps[op].fColor);
   *             }
   *
   *             if (xOff >= 400) {
   *                 canvas->translate(SkIntToScalar(-400), SkIntToScalar(250));
   *                 xOff = 0;
   *             } else {
   *                 canvas->translate(SkIntToScalar(200), 0);
   *                 xOff += 200;
   *             }
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public enum class SkGeomTypes {
    kRect_GeomType,
    kPath_GeomType,
    kAAClip_GeomType,
  }
}
