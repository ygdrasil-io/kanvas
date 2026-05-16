package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkPath
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkScalar
import org.skia.math.SkVector

/**
 * C++ original:
 * ```cpp
 * class SK_API SkPathMeasure {
 * public:
 *     SkPathMeasure();
 *     /** Initialize the pathmeasure with the specified path. The parts of the path that are needed
 *      *  are copied, so the client is free to modify/delete the path after this call.
 *      *
 *      *  resScale controls the precision of the measure. values > 1 increase the
 *      *  precision (and possibly slow down the computation).
 *      */
 *     SkPathMeasure(const SkPath& path, bool forceClosed, SkScalar resScale = 1);
 *     ~SkPathMeasure();
 *
 *     SkPathMeasure(SkPathMeasure&&) = default;
 *     SkPathMeasure& operator=(SkPathMeasure&&) = default;
 *
 *     /** Reset the pathmeasure with the specified path. The parts of the path that are needed
 *      *  are copied, so the client is free to modify/delete the path after this call..
 *      */
 *     void setPath(const SkPath*, bool forceClosed);
 *
 *     /** Return the total length of the current contour, or 0 if no path
 *         is associated (e.g. resetPath(null))
 *     */
 *     SkScalar getLength();
 *
 *     /** Pins distance to 0 <= distance <= getLength(), and then computes
 *         the corresponding position and tangent.
 *         Returns false if there is no path, or a zero-length path was specified, in which case
 *         position and tangent are unchanged.
 *     */
 *     [[nodiscard]] bool getPosTan(SkScalar distance, SkPoint* position, SkVector* tangent);
 *
 *     enum MatrixFlags {
 *         kGetPosition_MatrixFlag     = 0x01,
 *         kGetTangent_MatrixFlag      = 0x02,
 *         kGetPosAndTan_MatrixFlag    = kGetPosition_MatrixFlag | kGetTangent_MatrixFlag
 *     };
 *
 *     /** Pins distance to 0 <= distance <= getLength(), and then computes
 *         the corresponding matrix (by calling getPosTan).
 *         Returns false if there is no path, or a zero-length path was specified, in which case
 *         matrix is unchanged.
 *     */
 *     [[nodiscard]] bool getMatrix(SkScalar distance, SkMatrix* matrix,
 *                                  MatrixFlags flags = kGetPosAndTan_MatrixFlag);
 *
 *     /** Given a start and stop distance, return in dst the intervening segment(s).
 *         If the segment is zero-length, return false, else return true.
 *         startD and stopD are pinned to legal values (0..getLength()). If startD > stopD
 *         then return false (and leave dst untouched).
 *         Begin the segment with a moveTo if startWithMoveTo is true
 *     */
 *     bool getSegment(SkScalar startD, SkScalar stopD, SkPathBuilder* dst, bool startWithMoveTo);
 * #ifdef SK_SUPPORT_MUTABLE_PATHEFFECT
 *     bool getSegment(SkScalar startD, SkScalar stopD, SkPath* dst, bool startWithMoveTo);
 * #endif
 *
 *     /** Return true if the current contour is closed()
 *     */
 *     bool isClosed();
 *
 *     /** Move to the next contour in the path. Return true if one exists, or false if
 *         we're done with the path.
 *     */
 *     bool nextContour();
 *
 * #ifdef SK_DEBUG
 *     void    dump();
 * #endif
 *
 *     const SkContourMeasure* currentMeasure() const { return fContour.get(); }
 *
 * private:
 *     SkContourMeasureIter    fIter;
 *     sk_sp<SkContourMeasure> fContour;
 * }
 * ```
 */
public data class SkPathMeasure public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkContourMeasureIter    fIter
   * ```
   */
  private var fIter: Int,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkContourMeasure> fContour
   * ```
   */
  private var fContour: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * SkPathMeasure& operator=(SkPathMeasure&&) = default
   * ```
   */
  public fun assign(param0: SkPathMeasure) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPathMeasure::setPath(const SkPath* path, bool forceClosed) {
   *     fIter.reset(path ? *path : SkPath(), forceClosed);
   *     fContour = fIter.next();
   * }
   * ```
   */
  public fun setPath(path: SkPath?, forceClosed: Boolean) {
    TODO("Implement setPath")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar SkPathMeasure::getLength() {
   *     return fContour ? fContour->length() : 0;
   * }
   * ```
   */
  public fun getLength(): Int {
    TODO("Implement getLength")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPathMeasure::getPosTan(SkScalar distance, SkPoint* position, SkVector* tangent) {
   *     return fContour && fContour->getPosTan(distance, position, tangent);
   * }
   * ```
   */
  public fun getPosTan(
    distance: SkScalar,
    position: SkPoint?,
    tangent: SkVector?,
  ): Boolean {
    TODO("Implement getPosTan")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPathMeasure::getMatrix(SkScalar distance, SkMatrix* matrix, MatrixFlags flags) {
   *     return fContour && fContour->getMatrix(distance, matrix, (SkContourMeasure::MatrixFlags)flags);
   * }
   * ```
   */
  public fun getMatrix(
    distance: SkScalar,
    matrix: SkMatrix?,
    flags: MatrixFlags = TODO(),
  ): Boolean {
    TODO("Implement getMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPathMeasure::getSegment(SkScalar startD, SkScalar stopD, SkPath* dst,
   *                                bool startWithMoveTo) {
   *     SkPathBuilder builder;
   *     if (this->getSegment(startD, stopD, &builder, startWithMoveTo)) {
   *         *dst = builder.detach();
   *         return true;
   *     }
   *     return false;
   * }
   * ```
   */
  public fun getSegment(
    startD: SkScalar,
    stopD: SkScalar,
    dst: SkPathBuilder?,
    startWithMoveTo: Boolean,
  ): Boolean {
    TODO("Implement getSegment")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPathMeasure::isClosed() {
   *     return fContour && fContour->isClosed();
   * }
   * ```
   */
  public fun isClosed(): Boolean {
    TODO("Implement isClosed")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPathMeasure::nextContour() {
   *     fContour = fIter.next();
   *     return !!fContour;
   * }
   * ```
   */
  public fun nextContour(): Boolean {
    TODO("Implement nextContour")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkContourMeasure* currentMeasure() const { return fContour.get(); }
   * ```
   */
  public fun currentMeasure(): Int {
    TODO("Implement currentMeasure")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPathMeasure::dump() {}
   * ```
   */
  public fun dump() {
    TODO("Implement dump")
  }

  public enum class MatrixFlags {
    kGetPosition_MatrixFlag,
    kGetTangent_MatrixFlag,
    kGetPosAndTan_MatrixFlag,
  }
}
