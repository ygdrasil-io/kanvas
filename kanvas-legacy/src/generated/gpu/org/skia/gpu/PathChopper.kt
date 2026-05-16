package org.skia.gpu

import kotlin.Array
import kotlin.Float
import kotlin.Int
import org.skia.core.SkPathBuilder
import org.skia.foundation.SkPath
import org.skia.math.SkPathFillType
import org.skia.math.SkPoint
import wangs_formula.VectorXform

/**
 * C++ original:
 * ```cpp
 * class PathChopper {
 * public:
 *     PathChopper(float tessellationPrecision, const SkMatrix& matrix, const SkRect& viewport)
 *             : fTessellationPrecision(tessellationPrecision)
 *             , fCullTest(viewport, matrix)
 *             , fVectorXform(matrix) {
 *         fBuilder.setIsVolatile(true);
 *     }
 *
 *     SkPath detachPath(SkPathFillType ft) {
 *         fBuilder.setFillType(ft);
 *         return fBuilder.detach();
 *     }
 *
 *     void moveTo(SkPoint p) { fBuilder.moveTo(p); }
 *     void lineTo(const SkPoint p[2]) { fBuilder.lineTo(p[1]); }
 *     void close() { fBuilder.close(); }
 *
 *     void quadTo(const SkPoint quad[3]) {
 *         SkASSERT(fPointStack.empty());
 *         // Use a heap stack to recursively chop the quad into manageable, on-screen segments.
 *         fPointStack.push_back_n(3, quad);
 *         int numChops = 0;
 *         while (!fPointStack.empty()) {
 *             const SkPoint* p = fPointStack.end() - 3;
 *             if (!fCullTest.areVisible3(p)) {
 *                 fBuilder.lineTo(p[2]);
 *             } else {
 *                 float n4 = wangs_formula::quadratic_p4(fTessellationPrecision, p, fVectorXform);
 *                 if (n4 > kMaxSegmentsPerCurve_p4 && numChops < kMaxChopsPerCurve) {
 *                     SkPoint chops[5];
 *                     SkChopQuadAtHalf(p, chops);
 *                     fPointStack.pop_back_n(3);
 *                     fPointStack.push_back_n(3, chops+2);
 *                     fPointStack.push_back_n(3, chops);
 *                     ++numChops;
 *                     continue;
 *                 }
 *                 fBuilder.quadTo(p[1], p[2]);
 *             }
 *             fPointStack.pop_back_n(3);
 *         }
 *     }
 *
 *     void conicTo(const SkPoint conic[3], float weight) {
 *         SkASSERT(fPointStack.empty());
 *         SkASSERT(fWeightStack.empty());
 *         // Use a heap stack to recursively chop the conic into manageable, on-screen segments.
 *         fPointStack.push_back_n(3, conic);
 *         fWeightStack.push_back(weight);
 *         int numChops = 0;
 *         while (!fPointStack.empty()) {
 *             const SkPoint* p = fPointStack.end() - 3;
 *             float w = fWeightStack.back();
 *             if (!fCullTest.areVisible3(p)) {
 *                 fBuilder.lineTo(p[2]);
 *             } else {
 *                 float n2 = wangs_formula::conic_p2(fTessellationPrecision, p, w, fVectorXform);
 *                 if (n2 > kMaxSegmentsPerCurve_p2 && numChops < kMaxChopsPerCurve) {
 *                     SkConic chops[2];
 *                     if (!SkConic(p,w).chopAt(.5, chops)) {
 *                         SkPoint line[2] = {p[0], p[2]};
 *                         this->lineTo(line);
 *                         continue;
 *                     }
 *                     fPointStack.pop_back_n(3);
 *                     fWeightStack.pop_back();
 *                     fPointStack.push_back_n(3, chops[1].fPts);
 *                     fWeightStack.push_back(chops[1].fW);
 *                     fPointStack.push_back_n(3, chops[0].fPts);
 *                     fWeightStack.push_back(chops[0].fW);
 *                     ++numChops;
 *                     continue;
 *                 }
 *                 fBuilder.conicTo(p[1], p[2], w);
 *             }
 *             fPointStack.pop_back_n(3);
 *             fWeightStack.pop_back();
 *         }
 *         SkASSERT(fWeightStack.empty());
 *     }
 *
 *     void cubicTo(const SkPoint cubic[4]) {
 *         SkASSERT(fPointStack.empty());
 *         // Use a heap stack to recursively chop the cubic into manageable, on-screen segments.
 *         fPointStack.push_back_n(4, cubic);
 *         int numChops = 0;
 *         while (!fPointStack.empty()) {
 *             SkPoint* p = fPointStack.end() - 4;
 *             if (!fCullTest.areVisible4(p)) {
 *                 fBuilder.lineTo(p[3]);
 *             } else {
 *                 float n4 = wangs_formula::cubic_p4(fTessellationPrecision, p, fVectorXform);
 *                 if (n4 > kMaxSegmentsPerCurve_p4 && numChops < kMaxChopsPerCurve) {
 *                     SkPoint chops[7];
 *                     SkChopCubicAtHalf(p, chops);
 *                     fPointStack.pop_back_n(4);
 *                     fPointStack.push_back_n(4, chops+3);
 *                     fPointStack.push_back_n(4, chops);
 *                     ++numChops;
 *                     continue;
 *                 }
 *                 fBuilder.cubicTo(p[1], p[2], p[3]);
 *             }
 *             fPointStack.pop_back_n(4);
 *         }
 *     }
 *
 * private:
 *     const float fTessellationPrecision;
 *     const CullTest fCullTest;
 *     const wangs_formula::VectorXform fVectorXform;
 *     SkPathBuilder fBuilder;
 *
 *     // Used for stack-based recursion (instead of using the runtime stack).
 *     STArray<8, SkPoint> fPointStack;
 *     STArray<2, float> fWeightStack;
 * }
 * ```
 */
public data class PathChopper public constructor(
  /**
   * C++ original:
   * ```cpp
   * const float fTessellationPrecision
   * ```
   */
  private val fTessellationPrecision: Float,
  /**
   * C++ original:
   * ```cpp
   * const CullTest fCullTest
   * ```
   */
  private val fCullTest: CullTest,
  /**
   * C++ original:
   * ```cpp
   * const wangs_formula::VectorXform fVectorXform
   * ```
   */
  private val fVectorXform: VectorXform,
  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder fBuilder
   * ```
   */
  private var fBuilder: SkPathBuilder,
  /**
   * C++ original:
   * ```cpp
   * STArray<8, SkPoint> fPointStack
   * ```
   */
  private var fPointStack: Int,
  /**
   * C++ original:
   * ```cpp
   * STArray<2, float> fWeightStack
   * ```
   */
  private var fWeightStack: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * SkPath detachPath(SkPathFillType ft) {
   *         fBuilder.setFillType(ft);
   *         return fBuilder.detach();
   *     }
   * ```
   */
  public fun detachPath(ft: SkPathFillType): SkPath {
    TODO("Implement detachPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void moveTo(SkPoint p) { fBuilder.moveTo(p); }
   * ```
   */
  public fun moveTo(p: SkPoint) {
    TODO("Implement moveTo")
  }

  /**
   * C++ original:
   * ```cpp
   * void lineTo(const SkPoint p[2]) { fBuilder.lineTo(p[1]); }
   * ```
   */
  public fun lineTo(p: Array<SkPoint>) {
    TODO("Implement lineTo")
  }

  /**
   * C++ original:
   * ```cpp
   * void close() { fBuilder.close(); }
   * ```
   */
  public fun close() {
    TODO("Implement close")
  }

  /**
   * C++ original:
   * ```cpp
   * void quadTo(const SkPoint quad[3]) {
   *         SkASSERT(fPointStack.empty());
   *         // Use a heap stack to recursively chop the quad into manageable, on-screen segments.
   *         fPointStack.push_back_n(3, quad);
   *         int numChops = 0;
   *         while (!fPointStack.empty()) {
   *             const SkPoint* p = fPointStack.end() - 3;
   *             if (!fCullTest.areVisible3(p)) {
   *                 fBuilder.lineTo(p[2]);
   *             } else {
   *                 float n4 = wangs_formula::quadratic_p4(fTessellationPrecision, p, fVectorXform);
   *                 if (n4 > kMaxSegmentsPerCurve_p4 && numChops < kMaxChopsPerCurve) {
   *                     SkPoint chops[5];
   *                     SkChopQuadAtHalf(p, chops);
   *                     fPointStack.pop_back_n(3);
   *                     fPointStack.push_back_n(3, chops+2);
   *                     fPointStack.push_back_n(3, chops);
   *                     ++numChops;
   *                     continue;
   *                 }
   *                 fBuilder.quadTo(p[1], p[2]);
   *             }
   *             fPointStack.pop_back_n(3);
   *         }
   *     }
   * ```
   */
  public fun quadTo(quad: Array<SkPoint>) {
    TODO("Implement quadTo")
  }

  /**
   * C++ original:
   * ```cpp
   * void conicTo(const SkPoint conic[3], float weight) {
   *         SkASSERT(fPointStack.empty());
   *         SkASSERT(fWeightStack.empty());
   *         // Use a heap stack to recursively chop the conic into manageable, on-screen segments.
   *         fPointStack.push_back_n(3, conic);
   *         fWeightStack.push_back(weight);
   *         int numChops = 0;
   *         while (!fPointStack.empty()) {
   *             const SkPoint* p = fPointStack.end() - 3;
   *             float w = fWeightStack.back();
   *             if (!fCullTest.areVisible3(p)) {
   *                 fBuilder.lineTo(p[2]);
   *             } else {
   *                 float n2 = wangs_formula::conic_p2(fTessellationPrecision, p, w, fVectorXform);
   *                 if (n2 > kMaxSegmentsPerCurve_p2 && numChops < kMaxChopsPerCurve) {
   *                     SkConic chops[2];
   *                     if (!SkConic(p,w).chopAt(.5, chops)) {
   *                         SkPoint line[2] = {p[0], p[2]};
   *                         this->lineTo(line);
   *                         continue;
   *                     }
   *                     fPointStack.pop_back_n(3);
   *                     fWeightStack.pop_back();
   *                     fPointStack.push_back_n(3, chops[1].fPts);
   *                     fWeightStack.push_back(chops[1].fW);
   *                     fPointStack.push_back_n(3, chops[0].fPts);
   *                     fWeightStack.push_back(chops[0].fW);
   *                     ++numChops;
   *                     continue;
   *                 }
   *                 fBuilder.conicTo(p[1], p[2], w);
   *             }
   *             fPointStack.pop_back_n(3);
   *             fWeightStack.pop_back();
   *         }
   *         SkASSERT(fWeightStack.empty());
   *     }
   * ```
   */
  public fun conicTo(conic: Array<SkPoint>, weight: Float) {
    TODO("Implement conicTo")
  }

  /**
   * C++ original:
   * ```cpp
   * void cubicTo(const SkPoint cubic[4]) {
   *         SkASSERT(fPointStack.empty());
   *         // Use a heap stack to recursively chop the cubic into manageable, on-screen segments.
   *         fPointStack.push_back_n(4, cubic);
   *         int numChops = 0;
   *         while (!fPointStack.empty()) {
   *             SkPoint* p = fPointStack.end() - 4;
   *             if (!fCullTest.areVisible4(p)) {
   *                 fBuilder.lineTo(p[3]);
   *             } else {
   *                 float n4 = wangs_formula::cubic_p4(fTessellationPrecision, p, fVectorXform);
   *                 if (n4 > kMaxSegmentsPerCurve_p4 && numChops < kMaxChopsPerCurve) {
   *                     SkPoint chops[7];
   *                     SkChopCubicAtHalf(p, chops);
   *                     fPointStack.pop_back_n(4);
   *                     fPointStack.push_back_n(4, chops+3);
   *                     fPointStack.push_back_n(4, chops);
   *                     ++numChops;
   *                     continue;
   *                 }
   *                 fBuilder.cubicTo(p[1], p[2], p[3]);
   *             }
   *             fPointStack.pop_back_n(4);
   *         }
   *     }
   * ```
   */
  public fun cubicTo(cubic: Array<SkPoint>) {
    TODO("Implement cubicTo")
  }
}
