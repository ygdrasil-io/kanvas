package org.skia.core

import kotlin.Boolean
import kotlin.Double
import kotlin.Int
import org.skia.memory.SkArenaAlloc

/**
 * C++ original:
 * ```cpp
 * class SkTCubic : public SkTCurve {
 * public:
 *     SkDCubic fCubic;
 *
 *     SkTCubic() {}
 *
 *     SkTCubic(const SkDCubic& c)
 *         : fCubic(c) {
 *     }
 *
 *     ~SkTCubic() override {}
 *
 *     const SkDPoint& operator[](int n) const override { return fCubic[n]; }
 *     SkDPoint& operator[](int n) override { return fCubic[n]; }
 *
 *     bool collapsed() const override { return fCubic.collapsed(); }
 *     bool controlsInside() const override { return fCubic.controlsInside(); }
 *     void debugInit() override { return fCubic.debugInit(); }
 * #if DEBUG_T_SECT
 *     void dumpID(int id) const override { return fCubic.dumpID(id); }
 * #endif
 *     SkDVector dxdyAtT(double t) const override { return fCubic.dxdyAtT(t); }
 * #ifdef SK_DEBUG
 *     SkOpGlobalState* globalState() const override { return fCubic.globalState(); }
 * #endif
 *     bool hullIntersects(const SkDQuad& quad, bool* isLinear) const override;
 *     bool hullIntersects(const SkDConic& conic, bool* isLinear) const override;
 *
 *     bool hullIntersects(const SkDCubic& cubic, bool* isLinear) const override {
 *         return cubic.hullIntersects(fCubic, isLinear);
 *     }
 *
 *     bool hullIntersects(const SkTCurve& curve, bool* isLinear) const override {
 *         return curve.hullIntersects(fCubic, isLinear);
 *     }
 *
 *     int intersectRay(SkIntersections* i, const SkDLine& line) const override;
 *     bool IsConic() const override { return false; }
 *     SkTCurve* make(SkArenaAlloc& heap) const override { return heap.make<SkTCubic>(); }
 *
 *     int maxIntersections() const override { return SkDCubic::kMaxIntersections; }
 *
 *     void otherPts(int oddMan, const SkDPoint* endPt[2]) const override {
 *         fCubic.otherPts(oddMan, endPt);
 *     }
 *
 *     int pointCount() const override { return SkDCubic::kPointCount; }
 *     int pointLast() const override { return SkDCubic::kPointLast; }
 *     SkDPoint ptAtT(double t) const override { return fCubic.ptAtT(t); }
 *     void setBounds(SkDRect* ) const override;
 *
 *     void subDivide(double t1, double t2, SkTCurve* curve) const override {
 *         ((SkTCubic*) curve)->fCubic = fCubic.subDivide(t1, t2);
 *     }
 * }
 * ```
 */
public open class SkTCubic public constructor() : SkTCurve() {
  /**
   * C++ original:
   * ```cpp
   * SkDCubic fCubic
   * ```
   */
  public var fCubic: SkDCubic = TODO("Initialize fCubic")

  /**
   * C++ original:
   * ```cpp
   * SkTCubic() {}
   * ```
   */
  public constructor(c: SkDCubic) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkDPoint& operator[](int n) const override { return fCubic[n]; }
   * ```
   */
  public override operator fun `get`(n: Int): SkDPoint {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDPoint& operator[](int n) override { return fCubic[n]; }
   * ```
   */
  public override fun collapsed(): Boolean {
    TODO("Implement collapsed")
  }

  /**
   * C++ original:
   * ```cpp
   * bool collapsed() const override { return fCubic.collapsed(); }
   * ```
   */
  public override fun controlsInside(): Boolean {
    TODO("Implement controlsInside")
  }

  /**
   * C++ original:
   * ```cpp
   * bool controlsInside() const override { return fCubic.controlsInside(); }
   * ```
   */
  public override fun debugInit() {
    TODO("Implement debugInit")
  }

  /**
   * C++ original:
   * ```cpp
   * void debugInit() override { return fCubic.debugInit(); }
   * ```
   */
  public override fun dxdyAtT(t: Double): SkDVector {
    TODO("Implement dxdyAtT")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDVector dxdyAtT(double t) const override { return fCubic.dxdyAtT(t); }
   * ```
   */
  public override fun globalState(): SkOpGlobalState {
    TODO("Implement globalState")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpGlobalState* globalState() const override { return fCubic.globalState(); }
   * ```
   */
  public override fun hullIntersects(quad: SkDQuad, isLinear: Boolean?): Boolean {
    TODO("Implement hullIntersects")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTCubic::hullIntersects(const SkDQuad& quad, bool* isLinear) const {
   *     return quad.hullIntersects(fCubic, isLinear);
   * }
   * ```
   */
  public override fun hullIntersects(conic: SkDConic, isLinear: Boolean?): Boolean {
    TODO("Implement hullIntersects")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTCubic::hullIntersects(const SkDConic& conic, bool* isLinear) const  {
   *     return conic.hullIntersects(fCubic, isLinear);
   * }
   * ```
   */
  public override fun hullIntersects(cubic: SkDCubic, isLinear: Boolean?): Boolean {
    TODO("Implement hullIntersects")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hullIntersects(const SkDCubic& cubic, bool* isLinear) const override {
   *         return cubic.hullIntersects(fCubic, isLinear);
   *     }
   * ```
   */
  public override fun hullIntersects(curve: SkTCurve, isLinear: Boolean?): Boolean {
    TODO("Implement hullIntersects")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hullIntersects(const SkTCurve& curve, bool* isLinear) const override {
   *         return curve.hullIntersects(fCubic, isLinear);
   *     }
   * ```
   */
  public override fun intersectRay(i: SkIntersections?, line: SkDLine): Int {
    TODO("Implement intersectRay")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkTCubic::intersectRay(SkIntersections* i, const SkDLine& line) const {
   *     return i->intersectRay(fCubic, line);
   * }
   * ```
   */
  public override fun isConic(): Boolean {
    TODO("Implement isConic")
  }

  /**
   * C++ original:
   * ```cpp
   * bool IsConic() const override { return false; }
   * ```
   */
  public override fun make(heap: SkArenaAlloc): SkTCurve {
    TODO("Implement make")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTCurve* make(SkArenaAlloc& heap) const override { return heap.make<SkTCubic>(); }
   * ```
   */
  public override fun maxIntersections(): Int {
    TODO("Implement maxIntersections")
  }

  /**
   * C++ original:
   * ```cpp
   * int maxIntersections() const override { return SkDCubic::kMaxIntersections; }
   * ```
   */
  public override fun otherPts(oddMan: Int, endPt: Int) {
    TODO("Implement otherPts")
  }

  /**
   * C++ original:
   * ```cpp
   * void otherPts(int oddMan, const SkDPoint* endPt[2]) const override {
   *         fCubic.otherPts(oddMan, endPt);
   *     }
   * ```
   */
  public override fun pointCount(): Int {
    TODO("Implement pointCount")
  }

  /**
   * C++ original:
   * ```cpp
   * int pointCount() const override { return SkDCubic::kPointCount; }
   * ```
   */
  public override fun pointLast(): Int {
    TODO("Implement pointLast")
  }

  /**
   * C++ original:
   * ```cpp
   * int pointLast() const override { return SkDCubic::kPointLast; }
   * ```
   */
  public override fun ptAtT(t: Double): SkDPoint {
    TODO("Implement ptAtT")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDPoint ptAtT(double t) const override { return fCubic.ptAtT(t); }
   * ```
   */
  public override fun setBounds(rect: SkDRect?) {
    TODO("Implement setBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTCubic::setBounds(SkDRect* rect) const {
   *     rect->setBounds(fCubic);
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
