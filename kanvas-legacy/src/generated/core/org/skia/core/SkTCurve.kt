package org.skia.core

import kotlin.Boolean
import kotlin.Double
import kotlin.Int
import org.skia.memory.SkArenaAlloc

/**
 * C++ original:
 * ```cpp
 * class SkTCurve {
 * public:
 *     virtual ~SkTCurve() {}
 *     virtual const SkDPoint& operator[](int n) const = 0;
 *     virtual SkDPoint& operator[](int n) = 0;
 *
 *     virtual bool collapsed() const = 0;
 *     virtual bool controlsInside() const = 0;
 *     virtual void debugInit() = 0;
 * #if DEBUG_T_SECT
 *     virtual void dumpID(int id) const = 0;
 * #endif
 *     virtual SkDVector dxdyAtT(double t) const = 0;
 *     virtual bool hullIntersects(const SkDQuad& , bool* isLinear) const = 0;
 *     virtual bool hullIntersects(const SkDConic& , bool* isLinear) const = 0;
 *     virtual bool hullIntersects(const SkDCubic& , bool* isLinear) const = 0;
 *     virtual bool hullIntersects(const SkTCurve& , bool* isLinear) const = 0;
 *     virtual int intersectRay(SkIntersections* i, const SkDLine& line) const = 0;
 *     virtual bool IsConic() const = 0;
 *     virtual SkTCurve* make(SkArenaAlloc& ) const = 0;
 *     virtual int maxIntersections() const = 0;
 *     virtual void otherPts(int oddMan, const SkDPoint* endPt[2]) const = 0;
 *     virtual int pointCount() const = 0;
 *     virtual int pointLast() const = 0;
 *     virtual SkDPoint ptAtT(double t) const = 0;
 *     virtual void setBounds(SkDRect* ) const = 0;
 *     virtual void subDivide(double t1, double t2, SkTCurve* curve) const = 0;
 * #ifdef SK_DEBUG
 *     virtual SkOpGlobalState* globalState() const = 0;
 * #endif
 * }
 * ```
 */
public abstract class SkTCurve {
  /**
   * C++ original:
   * ```cpp
   * virtual const SkDPoint& operator[](int n) const = 0
   * ```
   */
  public abstract operator fun `get`(n: Int): SkDPoint

  /**
   * C++ original:
   * ```cpp
   * virtual SkDPoint& operator[](int n) = 0
   * ```
   */
  public abstract fun collapsed(): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool collapsed() const = 0
   * ```
   */
  public abstract fun controlsInside(): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool controlsInside() const = 0
   * ```
   */
  public abstract fun debugInit()

  /**
   * C++ original:
   * ```cpp
   * virtual void debugInit() = 0
   * ```
   */
  public abstract fun dxdyAtT(t: Double): SkDVector

  /**
   * C++ original:
   * ```cpp
   * virtual SkDVector dxdyAtT(double t) const = 0
   * ```
   */
  public abstract fun hullIntersects(param0: SkDQuad, isLinear: Boolean?): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool hullIntersects(const SkDQuad& , bool* isLinear) const = 0
   * ```
   */
  public abstract fun hullIntersects(param0: SkDConic, isLinear: Boolean?): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool hullIntersects(const SkDConic& , bool* isLinear) const = 0
   * ```
   */
  public abstract fun hullIntersects(param0: SkDCubic, isLinear: Boolean?): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool hullIntersects(const SkDCubic& , bool* isLinear) const = 0
   * ```
   */
  public abstract fun hullIntersects(param0: SkTCurve, isLinear: Boolean?): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool hullIntersects(const SkTCurve& , bool* isLinear) const = 0
   * ```
   */
  public abstract fun intersectRay(i: SkIntersections?, line: SkDLine): Int

  /**
   * C++ original:
   * ```cpp
   * virtual int intersectRay(SkIntersections* i, const SkDLine& line) const = 0
   * ```
   */
  public abstract fun isConic(): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool IsConic() const = 0
   * ```
   */
  public abstract fun make(param0: SkArenaAlloc): SkTCurve

  /**
   * C++ original:
   * ```cpp
   * virtual SkTCurve* make(SkArenaAlloc& ) const = 0
   * ```
   */
  public abstract fun maxIntersections(): Int

  /**
   * C++ original:
   * ```cpp
   * virtual int maxIntersections() const = 0
   * ```
   */
  public abstract fun otherPts(oddMan: Int, endPt: Int)

  /**
   * C++ original:
   * ```cpp
   * virtual void otherPts(int oddMan, const SkDPoint* endPt[2]) const = 0
   * ```
   */
  public abstract fun pointCount(): Int

  /**
   * C++ original:
   * ```cpp
   * virtual int pointCount() const = 0
   * ```
   */
  public abstract fun pointLast(): Int

  /**
   * C++ original:
   * ```cpp
   * virtual int pointLast() const = 0
   * ```
   */
  public abstract fun ptAtT(t: Double): SkDPoint

  /**
   * C++ original:
   * ```cpp
   * virtual SkDPoint ptAtT(double t) const = 0
   * ```
   */
  public abstract fun setBounds(param0: SkDRect?)

  /**
   * C++ original:
   * ```cpp
   * virtual void setBounds(SkDRect* ) const = 0
   * ```
   */
  public abstract fun subDivide(
    t1: Double,
    t2: Double,
    curve: SkTCurve?,
  )

  /**
   * C++ original:
   * ```cpp
   * virtual void subDivide(double t1, double t2, SkTCurve* curve) const = 0
   * ```
   */
  public abstract fun globalState(): SkOpGlobalState
}
