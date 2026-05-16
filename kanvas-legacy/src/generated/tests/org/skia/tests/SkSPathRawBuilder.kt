package org.skia.tests

import kotlin.Int
import kotlin.ULong
import org.skia.core.SkPathConvexity
import org.skia.core.SkPathRaw
import org.skia.foundation.SkSpan
import org.skia.math.SkPathFillType
import org.skia.math.SkPathVerb
import org.skia.math.SkPoint
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SkSPathRawBuilder {
 * public:
 *     SkSPathRawBuilder(SkSpan<SkPoint> ptStore, SkSpan<SkPathVerb> vbStore, SkSpan<float> cnStore)
 *     : fPtStorage(ptStore)
 *     , fVbStorage(vbStore)
 *     , fCnStorage(cnStore)
 *     , fPts(0), fCns(0), fVbs(0)
 *     {}
 *
 *     void moveTo(SkPoint);
 *     void lineTo(SkPoint);
 *     void quadTo(SkPoint, SkPoint);
 *     void conicTo(SkPoint, SkPoint, SkScalar w);
 *     void cubicTo(SkPoint, SkPoint, SkPoint);
 *     void close();
 *
 *     SkPathRaw raw(SkPathFillType, SkPathConvexity) const;
 *
 * private:
 *     SkSpan<SkPoint>    fPtStorage;
 *     SkSpan<SkPathVerb> fVbStorage;
 *     SkSpan<SkScalar>   fCnStorage;
 *     size_t fPts, fCns, fVbs;
 *
 *     void check_extend_pts(size_t n) const {
 *         SkASSERT(fPts + n <= fPtStorage.size());
 *     }
 *     void check_extend_vbs(size_t n) const {
 *         SkASSERT(fVbs + n <= fVbStorage.size());
 *     }
 *     void check_extend_cns(size_t n) const {
 *         SkASSERT(fCns + n <= fCnStorage.size());
 *     }
 * }
 * ```
 */
public data class SkSPathRawBuilder public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkSpan<SkPoint>    fPtStorage
   * ```
   */
  private var fPtStorage: SkSpan<SkPoint>,
  /**
   * C++ original:
   * ```cpp
   * SkSpan<SkPathVerb> fVbStorage
   * ```
   */
  private var fVbStorage: SkSpan<SkPathVerb>,
  /**
   * C++ original:
   * ```cpp
   * SkSpan<SkScalar>   fCnStorage
   * ```
   */
  private var fCnStorage: SkSpan<SkScalar>,
  /**
   * C++ original:
   * ```cpp
   * size_t fPts
   * ```
   */
  private var fPts: Int,
  /**
   * C++ original:
   * ```cpp
   * size_t fPts, fCns
   * ```
   */
  private var fCns: Int,
  /**
   * C++ original:
   * ```cpp
   * size_t fPts, fCns, fVbs
   * ```
   */
  private var fVbs: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * void SkSPathRawBuilder::moveTo(SkPoint p) {
   *     check_extend_pts(1);
   *     check_extend_vbs(1);
   *     fPtStorage[fPts++] = p;
   *     fVbStorage[fVbs++] = SkPathVerb::kMove;
   * }
   * ```
   */
  public fun moveTo(p: SkPoint) {
    TODO("Implement moveTo")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSPathRawBuilder::lineTo(SkPoint p) {
   *     check_extend_pts(1);
   *     check_extend_vbs(1);
   *     fPtStorage[fPts++] = p;
   *     fVbStorage[fVbs++] = SkPathVerb::kLine;
   * }
   * ```
   */
  public fun lineTo(p: SkPoint) {
    TODO("Implement lineTo")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSPathRawBuilder::quadTo(SkPoint p1, SkPoint p2) {
   *     check_extend_pts(2);
   *     check_extend_vbs(1);
   *     fPtStorage[fPts++] = p1;
   *     fPtStorage[fPts++] = p2;
   *     fVbStorage[fVbs++] = SkPathVerb::kQuad;
   * }
   * ```
   */
  public fun quadTo(p1: SkPoint, p2: SkPoint) {
    TODO("Implement quadTo")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSPathRawBuilder::conicTo(SkPoint p1, SkPoint p2, SkScalar w) {
   *     check_extend_pts(2);
   *     check_extend_cns(1);
   *     check_extend_vbs(1);
   *     fPtStorage[fPts++] = p1;
   *     fPtStorage[fPts++] = p2;
   *     fCnStorage[fCns++] = w;
   *     fVbStorage[fVbs++] = SkPathVerb::kConic;
   * }
   * ```
   */
  public fun conicTo(
    p1: SkPoint,
    p2: SkPoint,
    w: SkScalar,
  ) {
    TODO("Implement conicTo")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSPathRawBuilder::cubicTo(SkPoint p1, SkPoint p2, SkPoint p3) {
   *     check_extend_pts(3);
   *     check_extend_vbs(1);
   *     fPtStorage[fPts++] = p1;
   *     fPtStorage[fPts++] = p2;
   *     fPtStorage[fPts++] = p3;
   *     fVbStorage[fVbs++] = SkPathVerb::kCubic;
   * }
   * ```
   */
  public fun cubicTo(
    p1: SkPoint,
    p2: SkPoint,
    p3: SkPoint,
  ) {
    TODO("Implement cubicTo")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSPathRawBuilder::close() {
   *     check_extend_vbs(1);
   *     fVbStorage[fVbs++] = SkPathVerb::kClose;
   * }
   * ```
   */
  public fun close() {
    TODO("Implement close")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathRaw SkSPathRawBuilder::raw(SkPathFillType ft, SkPathConvexity convexity) const {
   *     const auto ptSpan = fPtStorage.first(fPts);
   *     return {
   *             ptSpan,
   *             fVbStorage.first(fVbs),
   *             fCnStorage.first(fCns),
   *             SkRect::BoundsOrEmpty(ptSpan),
   *             ft,
   *             convexity,
   *             SkPathPriv::ComputeSegmentMask(fVbStorage.first(fVbs)),
   *     };
   * }
   * ```
   */
  public fun raw(ft: SkPathFillType, convexity: SkPathConvexity): SkPathRaw {
    TODO("Implement raw")
  }

  /**
   * C++ original:
   * ```cpp
   * void check_extend_pts(size_t n) const {
   *         SkASSERT(fPts + n <= fPtStorage.size());
   *     }
   * ```
   */
  private fun checkExtendPts(n: ULong) {
    TODO("Implement checkExtendPts")
  }

  /**
   * C++ original:
   * ```cpp
   * void check_extend_vbs(size_t n) const {
   *         SkASSERT(fVbs + n <= fVbStorage.size());
   *     }
   * ```
   */
  private fun checkExtendVbs(n: ULong) {
    TODO("Implement checkExtendVbs")
  }

  /**
   * C++ original:
   * ```cpp
   * void check_extend_cns(size_t n) const {
   *         SkASSERT(fCns + n <= fCnStorage.size());
   *     }
   * ```
   */
  private fun checkExtendCns(n: ULong) {
    TODO("Implement checkExtendCns")
  }
}
