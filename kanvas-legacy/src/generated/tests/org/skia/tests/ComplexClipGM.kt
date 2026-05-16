package org.skia.tests

import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkPath
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ComplexClipGM : public GM {
 * public:
 *     ComplexClipGM(bool aaclip, bool saveLayer, bool invertDraw)
 *     : fDoAAClip(aaclip)
 *     , fDoSaveLayer(saveLayer)
 *     , fInvertDraw(invertDraw) {
 *         this->setBGColor(0xFFDEDFDE);
 *     }
 *
 * protected:
 *     SkString getName() const override {
 *         SkString str;
 *         str.printf("complexclip_%s%s%s",
 *                    fDoAAClip ? "aa" : "bw",
 *                    fDoSaveLayer ? "_layer" : "",
 *                    fInvertDraw ? "_invert" : "");
 *         return str;
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(388, 780); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPath path = SkPathBuilder()
 *                         .moveTo(0,   50)
 *                         .quadTo(0,   0,   50,  0)
 *                         .lineTo(175, 0)
 *                         .quadTo(200, 0,   200, 25)
 *                         .lineTo(200, 150)
 *                         .quadTo(200, 200, 150, 200)
 *                         .lineTo(0,   200)
 *                         .close()
 *                         .moveTo(50,  50)
 *                         .lineTo(150, 50)
 *                         .lineTo(150, 125)
 *                         .quadTo(150, 150, 125, 150)
 *                         .lineTo(50,  150)
 *                         .close()
 *                         .detach();
 *         if (fInvertDraw) {
 *             path.setFillType(SkPathFillType::kInverseEvenOdd);
 *         } else {
 *             path.setFillType(SkPathFillType::kEvenOdd);
 *         }
 *         SkPaint pathPaint;
 *         pathPaint.setAntiAlias(true);
 *         pathPaint.setColor(gPathColor);
 *
 *         SkPath clipA = SkPath::Polygon({{{10,  20}, {165, 22}, {70,  105}, {165, 177}, {-5,  180}}},
 *                                        true);
 *
 *         SkPath clipB = SkPath::Polygon({{{40,  10}, {190, 15}, {195, 190}, {40,  185}, {155, 100}}},
 *                                        true);
 *
 *         SkFont font(ToolUtils::DefaultPortableTypeface(), 20);
 *
 *         constexpr struct {
 *             SkClipOp fOp;
 *             const char*      fName;
 *         } gOps[] = { //extra spaces in names for measureText
 *             {SkClipOp::kIntersect,         "Isect "},
 *             {SkClipOp::kDifference,        "Diff " },
 *         };
 *
 *         canvas->translate(20, 20);
 *         canvas->scale(3 * SK_Scalar1 / 4, 3 * SK_Scalar1 / 4);
 *
 *         if (fDoSaveLayer) {
 *             // We want the layer to appear symmetric relative to actual
 *             // device boundaries so we need to "undo" the effect of the
 *             // scale and translate
 *             SkRect bounds = SkRect::MakeLTRB(
 *               4.0f/3.0f * -20,
 *               4.0f/3.0f * -20,
 *               4.0f/3.0f * (this->getISize().fWidth - 20),
 *               4.0f/3.0f * (this->getISize().fHeight - 20));
 *
 *             bounds.inset(100, 100);
 *             SkPaint boundPaint;
 *             boundPaint.setColor(SK_ColorRED);
 *             boundPaint.setStyle(SkPaint::kStroke_Style);
 *             canvas->drawRect(bounds, boundPaint);
 *             canvas->clipRect(bounds);
 *             canvas->saveLayer(&bounds, nullptr);
 *         }
 *
 *         for (int invBits = 0; invBits < 4; ++invBits) {
 *             canvas->save();
 *             for (size_t op = 0; op < std::size(gOps); ++op) {
 *                 this->drawHairlines(canvas, path, clipA, clipB);
 *
 *                 bool doInvA = SkToBool(invBits & 1);
 *                 bool doInvB = SkToBool(invBits & 2);
 *                 canvas->save();
 *                     // set clip
 *                     clipA.setFillType(doInvA ? SkPathFillType::kInverseEvenOdd :
 *                                       SkPathFillType::kEvenOdd);
 *                     clipB.setFillType(doInvB ? SkPathFillType::kInverseEvenOdd :
 *                                       SkPathFillType::kEvenOdd);
 *                     canvas->clipPath(clipA, fDoAAClip);
 *                     canvas->clipPath(clipB, gOps[op].fOp, fDoAAClip);
 *
 *                     // In the inverse case we need to prevent the draw from covering the whole
 *                     // canvas.
 *                     if (fInvertDraw) {
 *                         SkRect rectClip = clipA.getBounds();
 *                         rectClip.join(path.getBounds());
 *                         rectClip.join(path.getBounds());
 *                         rectClip.outset(5, 5);
 *                         canvas->clipRect(rectClip);
 *                     }
 *
 *                     // draw path clipped
 *                     canvas->drawPath(path, pathPaint);
 *                 canvas->restore();
 *
 *
 *                 SkPaint paint;
 *                 SkScalar txtX = 45;
 *                 paint.setColor(gClipAColor);
 *                 const char* aTxt = doInvA ? "InvA " : "A ";
 *                 canvas->drawSimpleText(aTxt, strlen(aTxt), SkTextEncoding::kUTF8, txtX, 220, font, paint);
 *                 txtX += font.measureText(aTxt, strlen(aTxt), SkTextEncoding::kUTF8);
 *                 paint.setColor(SK_ColorBLACK);
 *                 canvas->drawSimpleText(gOps[op].fName, strlen(gOps[op].fName), SkTextEncoding::kUTF8, txtX, 220,
 *                                        font, paint);
 *                 txtX += font.measureText(gOps[op].fName, strlen(gOps[op].fName), SkTextEncoding::kUTF8);
 *                 paint.setColor(gClipBColor);
 *                 const char* bTxt = doInvB ? "InvB " : "B ";
 *                 canvas->drawSimpleText(bTxt, strlen(bTxt), SkTextEncoding::kUTF8, txtX, 220, font, paint);
 *
 *                 canvas->translate(250,0);
 *             }
 *             canvas->restore();
 *             canvas->translate(0, 250);
 *         }
 *
 *         if (fDoSaveLayer) {
 *             canvas->restore();
 *         }
 *     }
 * private:
 *     void drawHairlines(SkCanvas* canvas, const SkPath& path,
 *                        const SkPath& clipA, const SkPath& clipB) {
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *         paint.setStyle(SkPaint::kStroke_Style);
 *         const SkAlpha fade = 0x33;
 *
 *         // draw path in hairline
 *         paint.setColor(gPathColor); paint.setAlpha(fade);
 *         canvas->drawPath(path, paint);
 *
 *         // draw clips in hair line
 *         paint.setColor(gClipAColor); paint.setAlpha(fade);
 *         canvas->drawPath(clipA, paint);
 *         paint.setColor(gClipBColor); paint.setAlpha(fade);
 *         canvas->drawPath(clipB, paint);
 *     }
 *
 *     bool fDoAAClip;
 *     bool fDoSaveLayer;
 *     bool fInvertDraw;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class ComplexClipGM public constructor(
  aaclip: Boolean,
  saveLayer: Boolean,
  invertDraw: Boolean,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * bool fDoAAClip
   * ```
   */
  private var fDoAAClip: Boolean = TODO("Initialize fDoAAClip")

  /**
   * C++ original:
   * ```cpp
   * bool fDoSaveLayer
   * ```
   */
  private var fDoSaveLayer: Boolean = TODO("Initialize fDoSaveLayer")

  /**
   * C++ original:
   * ```cpp
   * bool fInvertDraw
   * ```
   */
  private var fInvertDraw: Boolean = TODO("Initialize fInvertDraw")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         SkString str;
   *         str.printf("complexclip_%s%s%s",
   *                    fDoAAClip ? "aa" : "bw",
   *                    fDoSaveLayer ? "_layer" : "",
   *                    fInvertDraw ? "_invert" : "");
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
   * SkISize getISize() override { return SkISize::Make(388, 780); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPath path = SkPathBuilder()
   *                         .moveTo(0,   50)
   *                         .quadTo(0,   0,   50,  0)
   *                         .lineTo(175, 0)
   *                         .quadTo(200, 0,   200, 25)
   *                         .lineTo(200, 150)
   *                         .quadTo(200, 200, 150, 200)
   *                         .lineTo(0,   200)
   *                         .close()
   *                         .moveTo(50,  50)
   *                         .lineTo(150, 50)
   *                         .lineTo(150, 125)
   *                         .quadTo(150, 150, 125, 150)
   *                         .lineTo(50,  150)
   *                         .close()
   *                         .detach();
   *         if (fInvertDraw) {
   *             path.setFillType(SkPathFillType::kInverseEvenOdd);
   *         } else {
   *             path.setFillType(SkPathFillType::kEvenOdd);
   *         }
   *         SkPaint pathPaint;
   *         pathPaint.setAntiAlias(true);
   *         pathPaint.setColor(gPathColor);
   *
   *         SkPath clipA = SkPath::Polygon({{{10,  20}, {165, 22}, {70,  105}, {165, 177}, {-5,  180}}},
   *                                        true);
   *
   *         SkPath clipB = SkPath::Polygon({{{40,  10}, {190, 15}, {195, 190}, {40,  185}, {155, 100}}},
   *                                        true);
   *
   *         SkFont font(ToolUtils::DefaultPortableTypeface(), 20);
   *
   *         constexpr struct {
   *             SkClipOp fOp;
   *             const char*      fName;
   *         } gOps[] = { //extra spaces in names for measureText
   *             {SkClipOp::kIntersect,         "Isect "},
   *             {SkClipOp::kDifference,        "Diff " },
   *         };
   *
   *         canvas->translate(20, 20);
   *         canvas->scale(3 * SK_Scalar1 / 4, 3 * SK_Scalar1 / 4);
   *
   *         if (fDoSaveLayer) {
   *             // We want the layer to appear symmetric relative to actual
   *             // device boundaries so we need to "undo" the effect of the
   *             // scale and translate
   *             SkRect bounds = SkRect::MakeLTRB(
   *               4.0f/3.0f * -20,
   *               4.0f/3.0f * -20,
   *               4.0f/3.0f * (this->getISize().fWidth - 20),
   *               4.0f/3.0f * (this->getISize().fHeight - 20));
   *
   *             bounds.inset(100, 100);
   *             SkPaint boundPaint;
   *             boundPaint.setColor(SK_ColorRED);
   *             boundPaint.setStyle(SkPaint::kStroke_Style);
   *             canvas->drawRect(bounds, boundPaint);
   *             canvas->clipRect(bounds);
   *             canvas->saveLayer(&bounds, nullptr);
   *         }
   *
   *         for (int invBits = 0; invBits < 4; ++invBits) {
   *             canvas->save();
   *             for (size_t op = 0; op < std::size(gOps); ++op) {
   *                 this->drawHairlines(canvas, path, clipA, clipB);
   *
   *                 bool doInvA = SkToBool(invBits & 1);
   *                 bool doInvB = SkToBool(invBits & 2);
   *                 canvas->save();
   *                     // set clip
   *                     clipA.setFillType(doInvA ? SkPathFillType::kInverseEvenOdd :
   *                                       SkPathFillType::kEvenOdd);
   *                     clipB.setFillType(doInvB ? SkPathFillType::kInverseEvenOdd :
   *                                       SkPathFillType::kEvenOdd);
   *                     canvas->clipPath(clipA, fDoAAClip);
   *                     canvas->clipPath(clipB, gOps[op].fOp, fDoAAClip);
   *
   *                     // In the inverse case we need to prevent the draw from covering the whole
   *                     // canvas.
   *                     if (fInvertDraw) {
   *                         SkRect rectClip = clipA.getBounds();
   *                         rectClip.join(path.getBounds());
   *                         rectClip.join(path.getBounds());
   *                         rectClip.outset(5, 5);
   *                         canvas->clipRect(rectClip);
   *                     }
   *
   *                     // draw path clipped
   *                     canvas->drawPath(path, pathPaint);
   *                 canvas->restore();
   *
   *
   *                 SkPaint paint;
   *                 SkScalar txtX = 45;
   *                 paint.setColor(gClipAColor);
   *                 const char* aTxt = doInvA ? "InvA " : "A ";
   *                 canvas->drawSimpleText(aTxt, strlen(aTxt), SkTextEncoding::kUTF8, txtX, 220, font, paint);
   *                 txtX += font.measureText(aTxt, strlen(aTxt), SkTextEncoding::kUTF8);
   *                 paint.setColor(SK_ColorBLACK);
   *                 canvas->drawSimpleText(gOps[op].fName, strlen(gOps[op].fName), SkTextEncoding::kUTF8, txtX, 220,
   *                                        font, paint);
   *                 txtX += font.measureText(gOps[op].fName, strlen(gOps[op].fName), SkTextEncoding::kUTF8);
   *                 paint.setColor(gClipBColor);
   *                 const char* bTxt = doInvB ? "InvB " : "B ";
   *                 canvas->drawSimpleText(bTxt, strlen(bTxt), SkTextEncoding::kUTF8, txtX, 220, font, paint);
   *
   *                 canvas->translate(250,0);
   *             }
   *             canvas->restore();
   *             canvas->translate(0, 250);
   *         }
   *
   *         if (fDoSaveLayer) {
   *             canvas->restore();
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawHairlines(SkCanvas* canvas, const SkPath& path,
   *                        const SkPath& clipA, const SkPath& clipB) {
   *         SkPaint paint;
   *         paint.setAntiAlias(true);
   *         paint.setStyle(SkPaint::kStroke_Style);
   *         const SkAlpha fade = 0x33;
   *
   *         // draw path in hairline
   *         paint.setColor(gPathColor); paint.setAlpha(fade);
   *         canvas->drawPath(path, paint);
   *
   *         // draw clips in hair line
   *         paint.setColor(gClipAColor); paint.setAlpha(fade);
   *         canvas->drawPath(clipA, paint);
   *         paint.setColor(gClipBColor); paint.setAlpha(fade);
   *         canvas->drawPath(clipB, paint);
   *     }
   * ```
   */
  private fun drawHairlines(
    canvas: SkCanvas?,
    path: SkPath,
    clipA: SkPath,
    clipB: SkPath,
  ) {
    TODO("Implement drawHairlines")
  }
}
