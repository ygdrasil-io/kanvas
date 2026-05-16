package org.skia.tools

import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint

/**
 * C++ original:
 * ```cpp
 * class HilbertGenerator {
 * public:
 *     HilbertGenerator(float desiredSize, float desiredLineWidth, int desiredDepth);
 *
 *     // Draw a Hilbert curve into the canvas w/ a gradient along its length
 *     void draw(SkCanvas* canvas);
 *
 * private:
 *     void turn90(bool turnLeft);
 *     void line(SkCanvas* canvas);
 *     void recursiveDraw(SkCanvas* canvas, int curDepth, bool turnLeft);
 *     SkColor4f getColor(float curLen);
 *
 *     const float fDesiredSize;
 *     const int fDesiredDepth;
 *     const float fSegmentLength;            // length of a line segment
 *     const float fDesiredLineWidth;
 *
 *     SkRect fActualBounds;
 *
 *     // The "turtle" state
 *     SkPoint fCurPos;
 *     int fCurDir;
 *
 *     const float fExpectedLen;
 *     float fCurLen;
 * }
 * ```
 */
public data class HilbertGenerator public constructor(
  /**
   * C++ original:
   * ```cpp
   * const float fDesiredSize
   * ```
   */
  private val fDesiredSize: Float,
  /**
   * C++ original:
   * ```cpp
   * const int fDesiredDepth
   * ```
   */
  private val fDesiredDepth: Int,
  /**
   * C++ original:
   * ```cpp
   * const float fSegmentLength
   * ```
   */
  private val fSegmentLength: Float,
  /**
   * C++ original:
   * ```cpp
   * const float fDesiredLineWidth
   * ```
   */
  private val fDesiredLineWidth: Float,
  /**
   * C++ original:
   * ```cpp
   * SkRect fActualBounds
   * ```
   */
  private var fActualBounds: Int,
  /**
   * C++ original:
   * ```cpp
   * SkPoint fCurPos
   * ```
   */
  private var fCurPos: SkPaint,
  /**
   * C++ original:
   * ```cpp
   * int fCurDir
   * ```
   */
  private var fCurDir: Int,
  /**
   * C++ original:
   * ```cpp
   * const float fExpectedLen
   * ```
   */
  private val fExpectedLen: Float,
  /**
   * C++ original:
   * ```cpp
   * float fCurLen
   * ```
   */
  private var fCurLen: Float,
) {
  /**
   * C++ original:
   * ```cpp
   * void HilbertGenerator::draw(SkCanvas* canvas) {
   *     this->recursiveDraw(canvas, /* curDepth= */ 0, /* turnLeft= */ true);
   *
   *     SkScalarNearlyEqual(fExpectedLen, fCurLen, 0.01f);
   *     SkScalarNearlyEqual(fDesiredSize, fActualBounds.width(), 0.01f);
   *     SkScalarNearlyEqual(fDesiredSize, fActualBounds.height(), 0.01f);
   * }
   * ```
   */
  public fun draw(canvas: SkCanvas?) {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * void HilbertGenerator::turn90(bool turnLeft) {
   *     fCurDir += turnLeft ? 90 : -90;
   *     if (fCurDir >= 360) {
   *         fCurDir = 0;
   *     } else if (fCurDir < 0) {
   *         fCurDir = 270;
   *     }
   *
   *     SkASSERT(fCurDir == 0 || fCurDir == 90 || fCurDir == 180 || fCurDir == 270);
   * }
   * ```
   */
  private fun turn90(turnLeft: Boolean) {
    TODO("Implement turn90")
  }

  /**
   * C++ original:
   * ```cpp
   * void HilbertGenerator::line(SkCanvas* canvas) {
   *
   *     SkPoint before = fCurPos;
   *
   *     SkRect r;
   *     switch (fCurDir) {
   *         case 0:
   *             r.fLeft = fCurPos.fX;
   *             r.fTop = fCurPos.fY - fDesiredLineWidth / 2.0f;
   *             r.fRight = fCurPos.fX + fSegmentLength;
   *             r.fBottom = fCurPos.fY + fDesiredLineWidth / 2.0f;
   *             fCurPos.fX += fSegmentLength;
   *             break;
   *         case 90:
   *             r.fLeft = fCurPos.fX - fDesiredLineWidth / 2.0f;
   *             r.fTop = fCurPos.fY - fSegmentLength;
   *             r.fRight = fCurPos.fX + fDesiredLineWidth / 2.0f;
   *             r.fBottom = fCurPos.fY;
   *             fCurPos.fY -= fSegmentLength;
   *             break;
   *         case 180:
   *             r.fLeft = fCurPos.fX - fSegmentLength;
   *             r.fTop = fCurPos.fY - fDesiredLineWidth / 2.0f;
   *             r.fRight = fCurPos.fX;
   *             r.fBottom = fCurPos.fY + fDesiredLineWidth / 2.0f;
   *             fCurPos.fX -= fSegmentLength;
   *             break;
   *         case 270:
   *             r.fLeft = fCurPos.fX - fDesiredLineWidth / 2.0f;
   *             r.fTop = fCurPos.fY;
   *             r.fRight = fCurPos.fX + fDesiredLineWidth / 2.0f;
   *             r.fBottom = fCurPos.fY + fSegmentLength;
   *             fCurPos.fY += fSegmentLength;
   *             break;
   *         default:
   *             return;
   *     }
   *
   *     SkPoint pts[2] = { before, fCurPos };
   *
   *     SkColor4f colors[2] = {
   *             this->getColor(fCurLen),
   *             this->getColor(fCurLen + fSegmentLength),
   *     };
   *
   *     fCurLen += fSegmentLength;
   *     if (fActualBounds.isEmpty()) {
   *         fActualBounds = r;
   *     } else {
   *         fActualBounds.join(r);
   *     }
   *
   *     SkPaint paint;
   *     paint.setShader(SkShaders::LinearGradient(pts, {{colors, {}, SkTileMode::kClamp}, {}}));
   *     canvas->drawRect(r, paint);
   * }
   * ```
   */
  private fun line(canvas: SkCanvas?) {
    TODO("Implement line")
  }

  /**
   * C++ original:
   * ```cpp
   * void HilbertGenerator::recursiveDraw(SkCanvas* canvas, int curDepth, bool turnLeft) {
   *     if (curDepth >= fDesiredDepth) {
   *         return;
   *     }
   *
   *     this->turn90(turnLeft);
   *     this->recursiveDraw(canvas, curDepth + 1, !turnLeft);
   *     this->line(canvas);
   *     this->turn90(!turnLeft);
   *     this->recursiveDraw(canvas, curDepth + 1, turnLeft);
   *     this->line(canvas);
   *     this->recursiveDraw(canvas, curDepth + 1, turnLeft);
   *     this->turn90(!turnLeft);
   *     this->line(canvas);
   *     this->recursiveDraw(canvas, curDepth + 1, !turnLeft);
   *     this->turn90(turnLeft);
   * }
   * ```
   */
  private fun recursiveDraw(
    canvas: SkCanvas?,
    curDepth: Int,
    turnLeft: Boolean,
  ) {
    TODO("Implement recursiveDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColor4f HilbertGenerator::getColor(float curLen) {
   *     static const SkColor4f kColors[] = {
   *             SkColors::kBlack,
   *             SkColors::kBlue,
   *             SkColors::kCyan,
   *             SkColors::kGreen,
   *             SkColors::kYellow,
   *             SkColors::kRed,
   *             SkColors::kWhite,
   *     };
   *
   *     static const float kStops[] = {
   *             0.0f,
   *             1.0f/6.0f,
   *             2.0f/6.0f,
   *             0.5f,
   *             4.0f/6.0f,
   *             5.0f/6.0f,
   *             1.0f,
   *     };
   *     static_assert(std::size(kColors) == std::size(kStops));
   *
   *     float t = curLen / fExpectedLen;
   *     if (t <= 0.0f) {
   *         return kColors[0];
   *     } else if (t >= 1.0f) {
   *         return kColors[std::size(kColors)-1];
   *     }
   *
   *     for (unsigned int i = 0; i < std::size(kColors)-1; ++i) {
   *         if (kStops[i] <= t && t <= kStops[i+1]) {
   *             t = (t - kStops[i]) / (kStops[i+1] - kStops[i]);
   *             SkASSERT(0.0f <= t && t <= 1.0f);
   *             return { kColors[i].fR * (1 - t) + kColors[i+1].fR * t,
   *                      kColors[i].fG * (1 - t) + kColors[i+1].fG * t,
   *                      kColors[i].fB * (1 - t) + kColors[i+1].fB * t,
   *                      kColors[i].fA * (1 - t) + kColors[i+1].fA * t };
   *
   *         }
   *     }
   *
   *     return SkColors::kBlack;
   * }
   * ```
   */
  private fun getColor(curLen: Float): Int {
    TODO("Implement getColor")
  }
}
