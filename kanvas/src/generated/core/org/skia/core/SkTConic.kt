package org.skia.core

import kotlin.Boolean
import kotlin.Double
import kotlin.Int
import org.skia.memory.SkArenaAlloc

/**
 * C++ original:
 * ```cpp
 * class SkTConic : public SkTCurve {
 * public:
 *     SkDConic fConic;
 *
 *     SkTConic() {}
 *
 *     SkTConic(const SkDConic& c)
 *         : fConic(c) {
 *     }
 *
 *     ~SkTConic() override {}
 *
 *     const SkDPoint& operator[](int n) const override { return fConic[n]; }
 *     SkDPoint& operator[](int n) override { return fConic[n]; }
 *
 *     bool collapsed() const override { return fConic.collapsed(); }
 *     bool controlsInside() const override { return fConic.controlsInside(); }
 *     void debugInit() override { return fConic.debugInit(); }
 * #if DEBUG_T_SECT
 *     void dumpID(int id) const override { return fConic.dumpID(id); }
 * #endif
 *     SkDVector dxdyAtT(double t) const override { return fConic.dxdyAtT(t); }
 * #ifdef SK_DEBUG
 *     SkOpGlobalState* globalState() const override { return fConic.globalState(); }
 * #endif
 *     bool hullIntersects(const SkDQuad& quad, bool* isLinear) const override;
 *
 *     bool hullIntersects(const SkDConic& conic, bool* isLinear) const override {
 *         return conic.hullIntersects(fConic, isLinear);
 *     }
 *
 *     bool hullIntersects(const SkDCubic& cubic, bool* isLinear) const override;
 *
 *     bool hullIntersects(const SkTCurve& curve, bool* isLinear) const override {
 *         return curve.hullIntersects(fConic, isLinear);
 *     }
 *
 *     int intersectRay(SkIntersections* i, const SkDLine& line) const override;
 *     bool IsConic() const override { return true; }
 *     SkTCurve* make(SkArenaAlloc& heap) const override { return heap.make<SkTConic>(); }
 *
 *     int maxIntersections() const override { return SkDConic::kMaxIntersections; }
 *
 *     void otherPts(int oddMan, const SkDPoint* endPt[2]) const override {
 *         fConic.otherPts(oddMan, endPt);
 *     }
 *
 *     int pointCount() const override { return SkDConic::kPointCount; }
 *     int pointLast() const override { return SkDConic::kPointLast; }
 *     SkDPoint ptAtT(double t) const override { return fConic.ptAtT(t); }
 *     void setBounds(SkDRect* ) const override;
 *
 *     void subDivide(double t1, double t2, SkTCurve* curve) const override {
 *         ((SkTConic*) curve)->fConic = fConic.subDivide(t1, t2);
 *     }
 * }
 * ```
 */
public open class SkTConic public constructor() : SkTCurve() {
  /**
   * C++ original:
   * ```cpp
   * SkDConic fConic
   * ```
   */
  public var fConic: SkDConic = TODO("Initialize fConic")

  /**
   * C++ original:
   * ```cpp
   * SkTConic() {}
   * ```
   */
  public constructor(c: SkDConic) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkDPoint& operator[](int n) const override { return fConic[n]; }
   * ```
   */
  public override operator fun `get`(n: Int): SkDPoint {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDPoint& operator[](int n) override { return fConic[n]; }
   * ```
   */
  public override fun collapsed(): Boolean {
    TODO("Implement collapsed")
  }

  /**
   * C++ original:
   * ```cpp
   * bool collapsed() const override { return fConic.collapsed(); }
   * ```
   */
  public override fun controlsInside(): Boolean {
    TODO("Implement controlsInside")
  }

  /**
   * C++ original:
   * ```cpp
   * bool controlsInside() const override { return fConic.controlsInside(); }
   * ```
   */
  public override fun debugInit() {
    TODO("Implement debugInit")
  }

  /**
   * C++ original:
   * ```cpp
   * void debugInit() override { return fConic.debugInit(); }
   * ```
   */
  public override fun dxdyAtT(t: Double): SkDVector {
    TODO("Implement dxdyAtT")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDVector dxdyAtT(double t) const override { return fConic.dxdyAtT(t); }
   * ```
   */
  public override fun globalState(): SkOpGlobalState {
    TODO("Implement globalState")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpGlobalState* globalState() const override { return fConic.globalState(); }
   * ```
   */
  public override fun hullIntersects(quad: SkDQuad, isLinear: Boolean?): Boolean {
    TODO("Implement hullIntersects")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTConic::hullIntersects(const SkDQuad& quad, bool* isLinear) const  {
   *     return quad.hullIntersects(fConic, isLinear);
   * }
   * ```
   */
  public override fun hullIntersects(conic: SkDConic, isLinear: Boolean?): Boolean {
    TODO("Implement hullIntersects")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hullIntersects(const SkDConic& conic, bool* isLinear) const override {
   *         return conic.hullIntersects(fConic, isLinear);
   *     }
   * ```
   */
  public override fun hullIntersects(cubic: SkDCubic, isLinear: Boolean?): Boolean {
    TODO("Implement hullIntersects")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTConic::hullIntersects(const SkDCubic& cubic, bool* isLinear) const {
   *     return cubic.hullIntersects(fConic, isLinear);
   * }
   * ```
   */
  public override fun hullIntersects(curve: SkTCurve, isLinear: Boolean?): Boolean {
    TODO("Implement hullIntersects")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hullIntersects(const SkTCurve& curve, bool* isLinear) const override {
   *         return curve.hullIntersects(fConic, isLinear);
   *     }
   * ```
   */
  public override fun intersectRay(i: SkIntersections?, line: SkDLine): Int {
    TODO("Implement intersectRay")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkTConic::intersectRay(SkIntersections* i, const SkDLine& line) const {
   *     return i->intersectRay(fConic, line);
   * }
   * ```
   */
  public override fun isConic(): Boolean {
    TODO("Implement isConic")
  }

  /**
   * C++ original:
   * ```cpp
   * bool IsConic() const override { return true; }
   * ```
   */
  public override fun make(heap: SkArenaAlloc): SkTCurve {
    TODO("Implement make")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTCurve* make(SkArenaAlloc& heap) const override { return heap.make<SkTConic>(); }
   * ```
   */
  public override fun maxIntersections(): Int {
    TODO("Implement maxIntersections")
  }

  /**
   * C++ original:
   * ```cpp
   * int maxIntersections() const override { return SkDConic::kMaxIntersections; }
   * ```
   */
  public override fun otherPts(oddMan: Int, endPt: Int) {
    TODO("Implement otherPts")
  }

  /**
   * C++ original:
   * ```cpp
   * void otherPts(int oddMan, const SkDPoint* endPt[2]) const override {
   *         fConic.otherPts(oddMan, endPt);
   *     }
   * ```
   */
  public override fun pointCount(): Int {
    TODO("Implement pointCount")
  }

  /**
   * C++ original:
   * ```cpp
   * int pointCount() const override { return SkDConic::kPointCount; }
   * ```
   */
  public override fun pointLast(): Int {
    TODO("Implement pointLast")
  }

  /**
   * C++ original:
   * ```cpp
   * int pointLast() const override { return SkDConic::kPointLast; }
   * ```
   */
  public override fun ptAtT(t: Double): SkDPoint {
    TODO("Implement ptAtT")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDPoint ptAtT(double t) const override { return fConic.ptAtT(t); }
   * ```
   */
  public override fun setBounds(rect: SkDRect?) {
    TODO("Implement setBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTConic::setBounds(SkDRect* rect) const {
   *     rect->setBounds(fConic);
   * }
   * ```
   */
  public override fun subDivide(
    t1: Double,
    t2: Double,
    curve: SkTCurve?,
  ) {
    TODO("Implement subDivide")
  }
}
