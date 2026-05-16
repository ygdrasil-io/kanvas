package org.skia.gpu

import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class PathMiddleOutFanIter {
 * public:
 *     PathMiddleOutFanIter(const SkPath& path) : fMiddleOut(path.countVerbs()) {
 *         SkPathPriv::Iterate it(path);
 *         fPathIter = it.begin();
 *         fPathEnd = it.end();
 *     }
 *
 *     bool done() const { return fDone; }
 *
 *     MiddleOutPolygonTriangulator::PoppedTriangleStack nextStack() {
 *         SkASSERT(!fDone);
 *         if (fPathIter == fPathEnd) {
 *             fDone = true;
 *             return fMiddleOut.close();
 *         }
 *         switch (auto [verb, pts, w] = *fPathIter++; verb) {
 *             SkPoint pt;
 *             case SkPathVerb::kMove:
 *                 return fMiddleOut.closeAndMove(pts[0]);
 *             case SkPathVerb::kLine:
 *             case SkPathVerb::kQuad:
 *             case SkPathVerb::kConic:
 *             case SkPathVerb::kCubic:
 *                 pt = pts[SkPathPriv::PtsInIter((unsigned)verb) - 1];
 *                 return fMiddleOut.pushVertex(pt);
 *             case SkPathVerb::kClose:
 *                 return fMiddleOut.close();
 *         }
 *         SkUNREACHABLE;
 *     }
 *
 * private:
 *     MiddleOutPolygonTriangulator fMiddleOut;
 *     SkPathPriv::RangeIter fPathIter;
 *     SkPathPriv::RangeIter fPathEnd;
 *     bool fDone = false;
 * }
 * ```
 */
public data class PathMiddleOutFanIter public constructor(
  /**
   * C++ original:
   * ```cpp
   * MiddleOutPolygonTriangulator fMiddleOut
   * ```
   */
  private var fMiddleOut: MiddleOutPolygonTriangulator,
  /**
   * C++ original:
   * ```cpp
   * SkPathPriv::RangeIter fPathIter
   * ```
   */
  private var fPathIter: Int,
  /**
   * C++ original:
   * ```cpp
   * SkPathPriv::RangeIter fPathEnd
   * ```
   */
  private var fPathEnd: Int,
  /**
   * C++ original:
   * ```cpp
   * bool fDone = false
   * ```
   */
  private var fDone: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * bool done() const { return fDone; }
   * ```
   */
  public fun done(): Boolean {
    TODO("Implement done")
  }

  /**
   * C++ original:
   * ```cpp
   * MiddleOutPolygonTriangulator::PoppedTriangleStack nextStack() {
   *         SkASSERT(!fDone);
   *         if (fPathIter == fPathEnd) {
   *             fDone = true;
   *             return fMiddleOut.close();
   *         }
   *         switch (auto [verb, pts, w] = *fPathIter++; verb) {
   *             SkPoint pt;
   *             case SkPathVerb::kMove:
   *                 return fMiddleOut.closeAndMove(pts[0]);
   *             case SkPathVerb::kLine:
   *             case SkPathVerb::kQuad:
   *             case SkPathVerb::kConic:
   *             case SkPathVerb::kCubic:
   *                 pt = pts[SkPathPriv::PtsInIter((unsigned)verb) - 1];
   *                 return fMiddleOut.pushVertex(pt);
   *             case SkPathVerb::kClose:
   *                 return fMiddleOut.close();
   *         }
   *         SkUNREACHABLE;
   *     }
   * ```
   */
  public fun nextStack(): MiddleOutPolygonTriangulator.PoppedTriangleStack {
    TODO("Implement nextStack")
  }
}
