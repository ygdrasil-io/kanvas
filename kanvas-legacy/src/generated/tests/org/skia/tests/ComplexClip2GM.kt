package org.skia.tests

import kotlin.Array
import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.core.SkClipOp
import org.skia.foundation.SkColor
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class ComplexClip2GM : public GM {
 * public:
 *     enum Clip {
 *         kRect_Clip,
 *         kRRect_Clip,
 *         kPath_Clip
 *     };
 *
 *     ComplexClip2GM(Clip clip, bool antiAlias)
 *     : fClip(clip)
 *     , fAntiAlias(antiAlias) {
 *         SkScalar xA = 0.65f;
 *         SkScalar xF = 50.65f;
 *
 *         SkScalar yA = 0.65f;
 *         SkScalar yF = 50.65f;
 *
 *         fWidth = xF - xA;
 *         fHeight = yF - yA;
 *
 *         fTotalWidth = kCols * fWidth + SK_Scalar1 * (kCols + 1) * kPadX;
 *         fTotalHeight = kRows * fHeight + SK_Scalar1 * (kRows + 1) * kPadY;
 *     }
 *
 * protected:
 *     void onOnceBeforeDraw() override {
 *         this->setBGColor(SkColorSetRGB(0xDD,0xA0,0xDD));
 *
 *         // offset the rects a bit so we get antialiasing even in the rect case
 *         SkScalar xA = 0.65f;
 *         SkScalar xB = 10.65f;
 *         SkScalar xC = 20.65f;
 *         SkScalar xD = 30.65f;
 *         SkScalar xE = 40.65f;
 *         SkScalar xF = 50.65f;
 *
 *         SkScalar yA = 0.65f;
 *         SkScalar yB = 10.65f;
 *         SkScalar yC = 20.65f;
 *         SkScalar yD = 30.65f;
 *         SkScalar yE = 40.65f;
 *         SkScalar yF = 50.65f;
 *
 *         fRects[0].setLTRB(xB, yB, xE, yE);
 *         fRRects[0].setRectXY(fRects[0], 7, 7);
 *         fPaths[0] = SkPath::RRect(fRects[0], 5, 5);
 *         fRectColors[0] = SK_ColorRED;
 *
 *         fRects[1].setLTRB(xA, yA, xD, yD);
 *         fRRects[1].setRectXY(fRects[1], 7, 7);
 *         fPaths[1] = SkPath::RRect(fRects[1], 5, 5);
 *         fRectColors[1] = SK_ColorGREEN;
 *
 *         fRects[2].setLTRB(xC, yA, xF, yD);
 *         fRRects[2].setRectXY(fRects[2], 7, 7);
 *         fPaths[2] = SkPath::RRect(fRects[2], 5, 5);
 *         fRectColors[2] = SK_ColorBLUE;
 *
 *         fRects[3].setLTRB(xA, yC, xD, yF);
 *         fRRects[3].setRectXY(fRects[3], 7, 7);
 *         fPaths[3] = SkPath::RRect(fRects[3], 5, 5);
 *         fRectColors[3] = SK_ColorYELLOW;
 *
 *         fRects[4].setLTRB(xC, yC, xF, yF);
 *         fRRects[4].setRectXY(fRects[4], 7, 7);
 *         fPaths[4] = SkPath::RRect(fRects[4], 5, 5);
 *         fRectColors[4] = SK_ColorCYAN;
 *
 *         const SkClipOp ops[] = {
 *             SkClipOp::kDifference,
 *             SkClipOp::kIntersect,
 *         };
 *
 *         SkRandom r;
 *         for (int i = 0; i < kRows; ++i) {
 *             for (int j = 0; j < kCols; ++j) {
 *                 for (int k = 0; k < 5; ++k) {
 *                     fOps[j*kRows+i][k] = ops[r.nextU() % std::size(ops)];
 *                 }
 *             }
 *         }
 *     }
 *
 *     inline static constexpr int kRows = 5;
 *     inline static constexpr int kCols = 5;
 *     inline static constexpr int kPadX = 20;
 *     inline static constexpr int kPadY = 20;
 *
 *     static const char* ClipStr(Clip clip) {
 *         switch (clip) {
 *         case kRect_Clip:
 *             return "rect";
 *         case kRRect_Clip:
 *             return "rrect";
 *         case kPath_Clip:
 *             return "path";
 *         }
 *         SkDEBUGFAIL("Unknown clip type.");
 *         return "";
 *     }
 *
 *     SkString getName() const override {
 *         if (kRect_Clip == fClip && !fAntiAlias) {
 *             return SkString("complexclip2");
 *         }
 *
 *         SkString str;
 *         str.printf("complexclip2_%s_%s",
 *                     ClipStr(fClip),
 *                     fAntiAlias ? "aa" : "bw");
 *         return str;
 *     }
 *
 *     SkISize getISize() override {
 *         return SkISize::Make(SkScalarRoundToInt(fTotalWidth),
 *                              SkScalarRoundToInt(fTotalHeight));
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint rectPaint;
 *         rectPaint.setStyle(SkPaint::kStroke_Style);
 *         rectPaint.setStrokeWidth(-1);
 *
 *         SkPaint fillPaint;
 *         fillPaint.setColor(SkColorSetRGB(0xA0,0xDD,0xA0));
 *
 *         for (int i = 0; i < kRows; ++i) {
 *             for (int j = 0; j < kCols; ++j) {
 *                 canvas->save();
 *
 *                 canvas->translate(kPadX * SK_Scalar1 + (fWidth + kPadX * SK_Scalar1)*j,
 *                                   kPadY * SK_Scalar1 + (fHeight + kPadY * SK_Scalar1)*i);
 *
 *                 // draw the original shapes first so we can see the
 *                 // antialiasing on the clipped draw
 *                 for (int k = 0; k < 5; ++k) {
 *                     rectPaint.setColor(fRectColors[k]);
 *                     switch (fClip) {
 *                         case kRect_Clip:
 *                             canvas->drawRect(fRects[k], rectPaint);
 *                             break;
 *                         case kRRect_Clip:
 *                             canvas->drawRRect(fRRects[k], rectPaint);
 *                             break;
 *                         case kPath_Clip:
 *                             canvas->drawPath(fPaths[k], rectPaint);
 *                             break;
 *                     }
 *                 }
 *
 *                 for (int k = 0; k < 5; ++k) {
 *                     switch (fClip) {
 *                         case kRect_Clip:
 *                             canvas->clipRect(fRects[k],
 *                                              fOps[j*kRows+i][k],
 *                                              fAntiAlias);
 *                             break;
 *                         case kRRect_Clip:
 *                             canvas->clipRRect(fRRects[k],
 *                                               fOps[j*kRows+i][k],
 *                                               fAntiAlias);
 *                             break;
 *                         case kPath_Clip:
 *                             canvas->clipPath(fPaths[k],
 *                                              fOps[j*kRows+i][k],
 *                                              fAntiAlias);
 *                             break;
 *                     }
 *                 }
 *                 canvas->drawRect(SkRect::MakeWH(fWidth, fHeight), fillPaint);
 *                 canvas->restore();
 *             }
 *         }
 *     }
 * private:
 *     Clip fClip;
 *     bool fAntiAlias;
 *     SkRect fRects[5];
 *     SkRRect fRRects[5];
 *     SkPath fPaths[5];
 *     SkColor fRectColors[5];
 *     SkClipOp fOps[kRows * kCols][5];
 *     SkScalar fWidth;
 *     SkScalar fHeight;
 *     SkScalar fTotalWidth;
 *     SkScalar fTotalHeight;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class ComplexClip2GM public constructor(
  clip: Clip,
  antiAlias: Boolean,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kRows = 5
   * ```
   */
  private var fClip: Clip = TODO("Initialize fClip")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kCols = 5
   * ```
   */
  private var fAntiAlias: Boolean = TODO("Initialize fAntiAlias")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kPadX = 20
   * ```
   */
  private var fRects: Array<SkRect> = TODO("Initialize fRects")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kPadY = 20
   * ```
   */
  private var fRRects: Array<SkRRect> = TODO("Initialize fRRects")

  /**
   * C++ original:
   * ```cpp
   * Clip fClip
   * ```
   */
  private var fPaths: Array<SkPath> = TODO("Initialize fPaths")

  /**
   * C++ original:
   * ```cpp
   * bool fAntiAlias
   * ```
   */
  private var fRectColors: Array<SkColor> = TODO("Initialize fRectColors")

  /**
   * C++ original:
   * ```cpp
   * SkRect fRects[5]
   * ```
   */
  private var fOps: Array<SkClipOp> = TODO("Initialize fOps")

  /**
   * C++ original:
   * ```cpp
   * SkRRect fRRects[5]
   * ```
   */
  private var fWidth: SkScalar = TODO("Initialize fWidth")

  /**
   * C++ original:
   * ```cpp
   * SkPath fPaths[5]
   * ```
   */
  private var fHeight: SkScalar = TODO("Initialize fHeight")

  /**
   * C++ original:
   * ```cpp
   * SkColor fRectColors[5]
   * ```
   */
  private var fTotalWidth: SkScalar = TODO("Initialize fTotalWidth")

  /**
   * C++ original:
   * ```cpp
   * SkClipOp fOps[kRows * kCols][5]
   * ```
   */
  private var fTotalHeight: SkScalar = TODO("Initialize fTotalHeight")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         this->setBGColor(SkColorSetRGB(0xDD,0xA0,0xDD));
   *
   *         // offset the rects a bit so we get antialiasing even in the rect case
   *         SkScalar xA = 0.65f;
   *         SkScalar xB = 10.65f;
   *         SkScalar xC = 20.65f;
   *         SkScalar xD = 30.65f;
   *         SkScalar xE = 40.65f;
   *         SkScalar xF = 50.65f;
   *
   *         SkScalar yA = 0.65f;
   *         SkScalar yB = 10.65f;
   *         SkScalar yC = 20.65f;
   *         SkScalar yD = 30.65f;
   *         SkScalar yE = 40.65f;
   *         SkScalar yF = 50.65f;
   *
   *         fRects[0].setLTRB(xB, yB, xE, yE);
   *         fRRects[0].setRectXY(fRects[0], 7, 7);
   *         fPaths[0] = SkPath::RRect(fRects[0], 5, 5);
   *         fRectColors[0] = SK_ColorRED;
   *
   *         fRects[1].setLTRB(xA, yA, xD, yD);
   *         fRRects[1].setRectXY(fRects[1], 7, 7);
   *         fPaths[1] = SkPath::RRect(fRects[1], 5, 5);
   *         fRectColors[1] = SK_ColorGREEN;
   *
   *         fRects[2].setLTRB(xC, yA, xF, yD);
   *         fRRects[2].setRectXY(fRects[2], 7, 7);
   *         fPaths[2] = SkPath::RRect(fRects[2], 5, 5);
   *         fRectColors[2] = SK_ColorBLUE;
   *
   *         fRects[3].setLTRB(xA, yC, xD, yF);
   *         fRRects[3].setRectXY(fRects[3], 7, 7);
   *         fPaths[3] = SkPath::RRect(fRects[3], 5, 5);
   *         fRectColors[3] = SK_ColorYELLOW;
   *
   *         fRects[4].setLTRB(xC, yC, xF, yF);
   *         fRRects[4].setRectXY(fRects[4], 7, 7);
   *         fPaths[4] = SkPath::RRect(fRects[4], 5, 5);
   *         fRectColors[4] = SK_ColorCYAN;
   *
   *         const SkClipOp ops[] = {
   *             SkClipOp::kDifference,
   *             SkClipOp::kIntersect,
   *         };
   *
   *         SkRandom r;
   *         for (int i = 0; i < kRows; ++i) {
   *             for (int j = 0; j < kCols; ++j) {
   *                 for (int k = 0; k < 5; ++k) {
   *                     fOps[j*kRows+i][k] = ops[r.nextU() % std::size(ops)];
   *                 }
   *             }
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
   * SkString getName() const override {
   *         if (kRect_Clip == fClip && !fAntiAlias) {
   *             return SkString("complexclip2");
   *         }
   *
   *         SkString str;
   *         str.printf("complexclip2_%s_%s",
   *                     ClipStr(fClip),
   *                     fAntiAlias ? "aa" : "bw");
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
   * SkISize getISize() override {
   *         return SkISize::Make(SkScalarRoundToInt(fTotalWidth),
   *                              SkScalarRoundToInt(fTotalHeight));
   *     }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPaint rectPaint;
   *         rectPaint.setStyle(SkPaint::kStroke_Style);
   *         rectPaint.setStrokeWidth(-1);
   *
   *         SkPaint fillPaint;
   *         fillPaint.setColor(SkColorSetRGB(0xA0,0xDD,0xA0));
   *
   *         for (int i = 0; i < kRows; ++i) {
   *             for (int j = 0; j < kCols; ++j) {
   *                 canvas->save();
   *
   *                 canvas->translate(kPadX * SK_Scalar1 + (fWidth + kPadX * SK_Scalar1)*j,
   *                                   kPadY * SK_Scalar1 + (fHeight + kPadY * SK_Scalar1)*i);
   *
   *                 // draw the original shapes first so we can see the
   *                 // antialiasing on the clipped draw
   *                 for (int k = 0; k < 5; ++k) {
   *                     rectPaint.setColor(fRectColors[k]);
   *                     switch (fClip) {
   *                         case kRect_Clip:
   *                             canvas->drawRect(fRects[k], rectPaint);
   *                             break;
   *                         case kRRect_Clip:
   *                             canvas->drawRRect(fRRects[k], rectPaint);
   *                             break;
   *                         case kPath_Clip:
   *                             canvas->drawPath(fPaths[k], rectPaint);
   *                             break;
   *                     }
   *                 }
   *
   *                 for (int k = 0; k < 5; ++k) {
   *                     switch (fClip) {
   *                         case kRect_Clip:
   *                             canvas->clipRect(fRects[k],
   *                                              fOps[j*kRows+i][k],
   *                                              fAntiAlias);
   *                             break;
   *                         case kRRect_Clip:
   *                             canvas->clipRRect(fRRects[k],
   *                                               fOps[j*kRows+i][k],
   *                                               fAntiAlias);
   *                             break;
   *                         case kPath_Clip:
   *                             canvas->clipPath(fPaths[k],
   *                                              fOps[j*kRows+i][k],
   *                                              fAntiAlias);
   *                             break;
   *                     }
   *                 }
   *                 canvas->drawRect(SkRect::MakeWH(fWidth, fHeight), fillPaint);
   *                 canvas->restore();
   *             }
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public enum class Clip {
    kRect_Clip,
    kRRect_Clip,
    kPath_Clip,
  }

  public companion object {
    protected val kRows: Int = TODO("Initialize kRows")

    protected val kCols: Int = TODO("Initialize kCols")

    protected val kPadX: Int = TODO("Initialize kPadX")

    protected val kPadY: Int = TODO("Initialize kPadY")

    /**
     * C++ original:
     * ```cpp
     * static const char* ClipStr(Clip clip) {
     *         switch (clip) {
     *         case kRect_Clip:
     *             return "rect";
     *         case kRRect_Clip:
     *             return "rrect";
     *         case kPath_Clip:
     *             return "path";
     *         }
     *         SkDEBUGFAIL("Unknown clip type.");
     *         return "";
     *     }
     * ```
     */
    protected fun clipStr(clip: Clip): Char {
      TODO("Implement clipStr")
    }
  }
}
