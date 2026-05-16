package org.skia.core

import kotlin.Boolean
import kotlin.Double
import kotlin.Int
import org.skia.memory.SkArenaAlloc

/**
 * C++ original:
 * ```cpp
 * class SkTQuad : public SkTCurve {
 * public:
 *     SkDQuad fQuad;
 *
 *     SkTQuad() {}
 *
 *     SkTQuad(const SkDQuad& q)
 *         : fQuad(q) {
 *     }
 *
 *     ~SkTQuad() override {}
 *
 *     const SkDPoint& operator[](int n) const override { return fQuad[n]; }
 *     SkDPoint& operator[](int n) override { return fQuad[n]; }
 *
 *     bool collapsed() const override { return fQuad.collapsed(); }
 *     bool controlsInside() const override { return fQuad.controlsInside(); }
 *     void debugInit() override { return fQuad.debugInit(); }
 * #if DEBUG_T_SECT
 *     void dumpID(int id) const override { return fQuad.dumpID(id); }
 * #endif
 *     SkDVector dxdyAtT(double t) const override { return fQuad.dxdyAtT(t); }
 * #ifdef SK_DEBUG
 *     SkOpGlobalState* globalState() const override { return fQuad.globalState(); }
 * #endif
 *
 *     bool hullIntersects(const SkDQuad& quad, bool* isLinear) const override {
 *         return quad.hullIntersects(fQuad, isLinear);
 *     }
 *
 *     bool hullIntersects(const SkDConic& conic, bool* isLinear) const override;
 *     bool hullIntersects(const SkDCubic& cubic, bool* isLinear) const override;
 *
 *     bool hullIntersects(const SkTCurve& curve, bool* isLinear) const override {
 *         return curve.hullIntersects(fQuad, isLinear);
 *     }
 *
 *     int intersectRay(SkIntersections* i, const SkDLine& line) const override;
 *     bool IsConic() const override { return false; }
 *     SkTCurve* make(SkArenaAlloc& heap) const override { return heap.make<SkTQuad>(); }
 *
 *     int maxIntersections() const override { return SkDQuad::kMaxIntersections; }
 *
 *     void otherPts(int oddMan, const SkDPoint* endPt[2]) const override {
 *         fQuad.otherPts(oddMan, endPt);
 *     }
 *
 *     int pointCount() const override { return SkDQuad::kPointCount; }
 *     int pointLast() const override { return SkDQuad::kPointLast; }
 *     SkDPoint ptAtT(double t) const override { return fQuad.ptAtT(t); }
 *     void setBounds(SkDRect* ) const override;
 *
 *     void subDivide(double t1, double t2, SkTCurve* curve) const override {
 *         ((SkTQuad*) curve)->fQuad = fQuad.subDivide(t1, t2);
 *     }
 * }
 * ```
 */
public open class SkTQuad public constructor() : SkTCurve() {
  /**
   * C++ original:
   * ```cpp
   * SkDQuad fQuad
   * ```
   */
  public var fQuad: SkDQuad = TODO("Initialize fQuad")

  /**
   * C++ original:
   * ```cpp
   * SkTQuad() {}
   * ```
   */
  public constructor(q: SkDQuad) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkDPoint& operator[](int n) const override { return fQuad[n]; }
   * ```
   */
  public override operator fun `get`(n: Int): SkDPoint {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDPoint& operator[](int n) override { return fQuad[n]; }
   * ```
   */
  public override fun collapsed(): Boolean {
    TODO("Implement collapsed")
  }

  /**
   * C++ original:
   * ```cpp
   * bool collapsed() const override { return fQuad.collapsed(); }
   * ```
   */
  public override fun controlsInside(): Boolean {
    TODO("Implement controlsInside")
  }

  /**
   * C++ original:
   * ```cpp
   * bool controlsInside() const override { return fQuad.controlsInside(); }
   * ```
   */
  public override fun debugInit() {
    TODO("Implement debugInit")
  }

  /**
   * C++ original:
   * ```cpp
   * void debugInit() override { return fQuad.debugInit(); }
   * ```
   */
  public override fun dxdyAtT(t: Double): SkDVector {
    TODO("Implement dxdyAtT")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDVector dxdyAtT(double t) const override { return fQuad.dxdyAtT(t); }
   * ```
   */
  public override fun globalState(): SkOpGlobalState {
    TODO("Implement globalState")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpGlobalState* globalState() const override { return fQuad.globalState(); }
   * ```
   */
  public override fun hullIntersects(quad: SkDQuad, isLinear: Boolean?): Boolean {
    TODO("Implement hullIntersects")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hullIntersects(const SkDQuad& quad, bool* isLinear) const override {
   *         return quad.hullIntersects(fQuad, isLinear);
   *     }
   * ```
   */
  public override fun hullIntersects(conic: SkDConic, isLinear: Boolean?): Boolean {
    TODO("Implement hullIntersects")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTQuad::hullIntersects(const SkDConic& conic, bool* isLinear) const  {
   *     return conic.hullIntersects(fQuad, isLinear);
   * }
   * ```
   */
  public override fun hullIntersects(cubic: SkDCubic, isLinear: Boolean?): Boolean {
    TODO("Implement hullIntersects")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTQuad::hullIntersects(const SkDCubic& cubic, bool* isLinear) const {
   *     return cubic.hullIntersects(fQuad, isLinear);
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
   *         return curve.hullIntersects(fQuad, isLinear);
   *     }
   * ```
   */
  public override fun intersectRay(i: SkIntersections?, line: SkDLine): Int {
    TODO("Implement intersectRay")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkTQuad::intersectRay(SkIntersections* i, const SkDLine& line) const {
   *     return i->intersectRay(fQuad, line);
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
   * SkTCurve* make(SkArenaAlloc& heap) const override { return heap.make<SkTQuad>(); }
   * ```
   */
  public override fun maxIntersections(): Int {
    TODO("Implement maxIntersections")
  }

  /**
   * C++ original:
   * ```cpp
   * int maxIntersections() const override { return SkDQuad::kMaxIntersections; }
   * ```
   */
  public override fun otherPts(oddMan: Int, endPt: Int) {
    TODO("Implement otherPts")
  }

  /**
   * C++ original:
   * ```cpp
   * void otherPts(int oddMan, const SkDPoint* endPt[2]) const override {
   *         fQuad.otherPts(oddMan, endPt);
   *     }
   * ```
   */
  public override fun pointCount(): Int {
    TODO("Implement pointCount")
  }

  /**
   * C++ original:
   * ```cpp
   * int pointCount() const override { return SkDQuad::kPointCount; }
   * ```
   */
  public override fun pointLast(): Int {
    TODO("Implement pointLast")
  }

  /**
   * C++ original:
   * ```cpp
   * int pointLast() const override { return SkDQuad::kPointLast; }
   * ```
   */
  public override fun ptAtT(t: Double): SkDPoint {
    TODO("Implement ptAtT")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDPoint ptAtT(double t) const override { return fQuad.ptAtT(t); }
   * ```
   */
  public override fun setBounds(rect: SkDRect?) {
    TODO("Implement setBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTQuad::setBounds(SkDRect* rect) const {
   *     rect->setBounds(fQuad);
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
