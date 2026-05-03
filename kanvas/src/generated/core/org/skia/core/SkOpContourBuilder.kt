package org.skia.core

import kotlin.Array
import kotlin.Boolean
import org.skia.math.SkPathVerb
import org.skia.math.SkPoint
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SkOpContourBuilder {
 * public:
 *     SkOpContourBuilder(SkOpContour* contour)
 *         : fContour(contour)
 *         , fLastIsLine(false) {
 *     }
 *
 *     void addConic(SkPoint pts[3], SkScalar weight);
 *     void addCubic(SkPoint pts[4]);
 *     void addCurve(SkPath::Verb verb, const SkPoint pts[4], SkScalar weight = 1);
 *     void addLine(const SkPoint pts[2]);
 *     void addQuad(SkPoint pts[3]);
 *     void flush();
 *     SkOpContour* contour() { return fContour; }
 *     void setContour(SkOpContour* contour) { flush(); fContour = contour; }
 * protected:
 *     SkOpContour* fContour;
 *     SkPoint fLastLine[2];
 *     bool fLastIsLine;
 * }
 * ```
 */
public data class SkOpContourBuilder public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkOpContour* fContour
   * ```
   */
  protected var fContour: SkOpContour?,
  /**
   * C++ original:
   * ```cpp
   * SkPoint fLastLine[2]
   * ```
   */
  protected var fLastLine: Array<SkPoint>,
  /**
   * C++ original:
   * ```cpp
   * bool fLastIsLine
   * ```
   */
  protected var fLastIsLine: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * void SkOpContourBuilder::addConic(SkPoint pts[3], SkScalar weight) {
   *     this->flush();
   *     fContour->addConic(pts, weight);
   * }
   * ```
   */
  public fun addConic(pts: Array<SkPoint>, weight: SkScalar) {
    TODO("Implement addConic")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpContourBuilder::addCubic(SkPoint pts[4]) {
   *     this->flush();
   *     fContour->addCubic(pts);
   * }
   * ```
   */
  public fun addCubic(pts: Array<SkPoint>) {
    TODO("Implement addCubic")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpContourBuilder::addCurve(SkPath::Verb verb, const SkPoint pts[4], SkScalar weight) {
   *     if (SkPath::kLine_Verb == verb) {
   *         this->addLine(pts);
   *         return;
   *     }
   *     SkArenaAlloc* allocator = fContour->globalState()->allocator();
   *     switch (verb) {
   *         case SkPath::kQuad_Verb: {
   *             SkPoint* ptStorage = allocator->makeArrayDefault<SkPoint>(3);
   *             memcpy(ptStorage, pts, sizeof(SkPoint) * 3);
   *             this->addQuad(ptStorage);
   *         } break;
   *         case SkPath::kConic_Verb: {
   *             SkPoint* ptStorage = allocator->makeArrayDefault<SkPoint>(3);
   *             memcpy(ptStorage, pts, sizeof(SkPoint) * 3);
   *             this->addConic(ptStorage, weight);
   *         } break;
   *         case SkPath::kCubic_Verb: {
   *             SkPoint* ptStorage = allocator->makeArrayDefault<SkPoint>(4);
   *             memcpy(ptStorage, pts, sizeof(SkPoint) * 4);
   *             this->addCubic(ptStorage);
   *         } break;
   *         default:
   *             SkASSERT(0);
   *     }
   * }
   * ```
   */
  public fun addCurve(
    verb: SkPathVerb,
    pts: Array<SkPoint>,
    weight: SkScalar = 1,
  ) {
    TODO("Implement addCurve")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpContourBuilder::addLine(const SkPoint pts[2]) {
   *     // if the previous line added is the exact opposite, eliminate both
   *     if (fLastIsLine) {
   *         if (fLastLine[0] == pts[1] && fLastLine[1] == pts[0]) {
   *             fLastIsLine = false;
   *             return;
   *         } else {
   *             flush();
   *         }
   *     }
   *     memcpy(fLastLine, pts, sizeof(fLastLine));
   *     fLastIsLine = true;
   * }
   * ```
   */
  public fun addLine(pts: Array<SkPoint>) {
    TODO("Implement addLine")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpContourBuilder::addQuad(SkPoint pts[3]) {
   *     this->flush();
   *     fContour->addQuad(pts);
   * }
   * ```
   */
  public fun addQuad(pts: Array<SkPoint>) {
    TODO("Implement addQuad")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpContourBuilder::flush() {
   *     if (!fLastIsLine)
   *         return;
   *     SkArenaAlloc* allocator = fContour->globalState()->allocator();
   *     SkPoint* ptStorage = allocator->makeArrayDefault<SkPoint>(2);
   *     memcpy(ptStorage, fLastLine, sizeof(fLastLine));
   *     (void) fContour->addLine(ptStorage);
   *     fLastIsLine = false;
   * }
   * ```
   */
  public fun flush() {
    TODO("Implement flush")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpContour* contour() { return fContour; }
   * ```
   */
  public fun contour(): SkOpContour {
    TODO("Implement contour")
  }

  /**
   * C++ original:
   * ```cpp
   * void setContour(SkOpContour* contour) { flush(); fContour = contour; }
   * ```
   */
  public fun setContour(contour: SkOpContour?) {
    TODO("Implement setContour")
  }
}
