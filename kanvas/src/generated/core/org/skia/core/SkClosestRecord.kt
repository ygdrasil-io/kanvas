package org.skia.core

import kotlin.Boolean
import kotlin.Double
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct SkClosestRecord {
 *     bool operator<(const SkClosestRecord& rh) const {
 *         return fClosest < rh.fClosest;
 *     }
 *
 *     void addIntersection(SkIntersections* intersections) const {
 *         double r1t = fC1Index ? fC1Span->endT() : fC1Span->startT();
 *         double r2t = fC2Index ? fC2Span->endT() : fC2Span->startT();
 *         intersections->insert(r1t, r2t, fC1Span->part()[fC1Index]);
 *     }
 *
 *     void findEnd(const SkTSpan* span1, const SkTSpan* span2,
 *             int c1Index, int c2Index) {
 *         const SkTCurve& c1 = span1->part();
 *         const SkTCurve& c2 = span2->part();
 *         if (!c1[c1Index].approximatelyEqual(c2[c2Index])) {
 *             return;
 *         }
 *         double dist = c1[c1Index].distanceSquared(c2[c2Index]);
 *         if (fClosest < dist) {
 *             return;
 *         }
 *         fC1Span = span1;
 *         fC2Span = span2;
 *         fC1StartT = span1->startT();
 *         fC1EndT = span1->endT();
 *         fC2StartT = span2->startT();
 *         fC2EndT = span2->endT();
 *         fC1Index = c1Index;
 *         fC2Index = c2Index;
 *         fClosest = dist;
 *     }
 *
 *     bool matesWith(const SkClosestRecord& mate  SkDEBUGPARAMS(SkIntersections* i)) const {
 *         SkOPOBJASSERT(i, fC1Span == mate.fC1Span || fC1Span->endT() <= mate.fC1Span->startT()
 *                 || mate.fC1Span->endT() <= fC1Span->startT());
 *         SkOPOBJASSERT(i, fC2Span == mate.fC2Span || fC2Span->endT() <= mate.fC2Span->startT()
 *                 || mate.fC2Span->endT() <= fC2Span->startT());
 *         return fC1Span == mate.fC1Span || fC1Span->endT() == mate.fC1Span->startT()
 *                 || fC1Span->startT() == mate.fC1Span->endT()
 *                 || fC2Span == mate.fC2Span
 *                 || fC2Span->endT() == mate.fC2Span->startT()
 *                 || fC2Span->startT() == mate.fC2Span->endT();
 *     }
 *
 *     void merge(const SkClosestRecord& mate) {
 *         fC1Span = mate.fC1Span;
 *         fC2Span = mate.fC2Span;
 *         fClosest = mate.fClosest;
 *         fC1Index = mate.fC1Index;
 *         fC2Index = mate.fC2Index;
 *     }
 *
 *     void reset() {
 *         fClosest = FLT_MAX;
 *         SkDEBUGCODE(fC1Span = nullptr);
 *         SkDEBUGCODE(fC2Span = nullptr);
 *         SkDEBUGCODE(fC1Index = fC2Index = -1);
 *     }
 *
 *     void update(const SkClosestRecord& mate) {
 *         fC1StartT = std::min(fC1StartT, mate.fC1StartT);
 *         fC1EndT = std::max(fC1EndT, mate.fC1EndT);
 *         fC2StartT = std::min(fC2StartT, mate.fC2StartT);
 *         fC2EndT = std::max(fC2EndT, mate.fC2EndT);
 *     }
 *
 *     const SkTSpan* fC1Span;
 *     const SkTSpan* fC2Span;
 *     double fC1StartT;
 *     double fC1EndT;
 *     double fC2StartT;
 *     double fC2EndT;
 *     double fClosest;
 *     int fC1Index;
 *     int fC2Index;
 * }
 * ```
 */
public data class SkClosestRecord public constructor(
  /**
   * C++ original:
   * ```cpp
   * const SkTSpan* fC1Span
   * ```
   */
  public val fC1Span: SkTSpan?,
  /**
   * C++ original:
   * ```cpp
   * const SkTSpan* fC2Span
   * ```
   */
  public val fC2Span: SkTSpan?,
  /**
   * C++ original:
   * ```cpp
   * double fC1StartT
   * ```
   */
  public var fC1StartT: Double,
  /**
   * C++ original:
   * ```cpp
   * double fC1EndT
   * ```
   */
  public var fC1EndT: Double,
  /**
   * C++ original:
   * ```cpp
   * double fC2StartT
   * ```
   */
  public var fC2StartT: Double,
  /**
   * C++ original:
   * ```cpp
   * double fC2EndT
   * ```
   */
  public var fC2EndT: Double,
  /**
   * C++ original:
   * ```cpp
   * double fClosest
   * ```
   */
  public var fClosest: Double,
  /**
   * C++ original:
   * ```cpp
   * int fC1Index
   * ```
   */
  public var fC1Index: Int,
  /**
   * C++ original:
   * ```cpp
   * int fC2Index
   * ```
   */
  public var fC2Index: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool operator<(const SkClosestRecord& rh) const {
   *         return fClosest < rh.fClosest;
   *     }
   * ```
   */
  public operator fun compareTo(rh: SkClosestRecord): Int {
    TODO("Implement compareTo")
  }

  /**
   * C++ original:
   * ```cpp
   * void addIntersection(SkIntersections* intersections) const {
   *         double r1t = fC1Index ? fC1Span->endT() : fC1Span->startT();
   *         double r2t = fC2Index ? fC2Span->endT() : fC2Span->startT();
   *         intersections->insert(r1t, r2t, fC1Span->part()[fC1Index]);
   *     }
   * ```
   */
  public fun addIntersection(intersections: SkIntersections?) {
    TODO("Implement addIntersection")
  }

  /**
   * C++ original:
   * ```cpp
   * void findEnd(const SkTSpan* span1, const SkTSpan* span2,
   *             int c1Index, int c2Index) {
   *         const SkTCurve& c1 = span1->part();
   *         const SkTCurve& c2 = span2->part();
   *         if (!c1[c1Index].approximatelyEqual(c2[c2Index])) {
   *             return;
   *         }
   *         double dist = c1[c1Index].distanceSquared(c2[c2Index]);
   *         if (fClosest < dist) {
   *             return;
   *         }
   *         fC1Span = span1;
   *         fC2Span = span2;
   *         fC1StartT = span1->startT();
   *         fC1EndT = span1->endT();
   *         fC2StartT = span2->startT();
   *         fC2EndT = span2->endT();
   *         fC1Index = c1Index;
   *         fC2Index = c2Index;
   *         fClosest = dist;
   *     }
   * ```
   */
  public fun findEnd(
    span1: SkTSpan?,
    span2: SkTSpan?,
    c1Index: Int,
    c2Index: Int,
  ) {
    TODO("Implement findEnd")
  }

  /**
   * C++ original:
   * ```cpp
   * bool matesWith(const SkClosestRecord& mate  SkDEBUGPARAMS(SkIntersections* i)) const {
   *         SkOPOBJASSERT(i, fC1Span == mate.fC1Span || fC1Span->endT() <= mate.fC1Span->startT()
   *                 || mate.fC1Span->endT() <= fC1Span->startT());
   *         SkOPOBJASSERT(i, fC2Span == mate.fC2Span || fC2Span->endT() <= mate.fC2Span->startT()
   *                 || mate.fC2Span->endT() <= fC2Span->startT());
   *         return fC1Span == mate.fC1Span || fC1Span->endT() == mate.fC1Span->startT()
   *                 || fC1Span->startT() == mate.fC1Span->endT()
   *                 || fC2Span == mate.fC2Span
   *                 || fC2Span->endT() == mate.fC2Span->startT()
   *                 || fC2Span->startT() == mate.fC2Span->endT();
   *     }
   * ```
   */
  public fun matesWith(param0: SkClosestRecord, param1: SkIntersections?): Boolean {
    TODO("Implement matesWith")
  }

  /**
   * C++ original:
   * ```cpp
   * void merge(const SkClosestRecord& mate) {
   *         fC1Span = mate.fC1Span;
   *         fC2Span = mate.fC2Span;
   *         fClosest = mate.fClosest;
   *         fC1Index = mate.fC1Index;
   *         fC2Index = mate.fC2Index;
   *     }
   * ```
   */
  public fun merge(mate: SkClosestRecord) {
    TODO("Implement merge")
  }

  /**
   * C++ original:
   * ```cpp
   * void reset() {
   *         fClosest = FLT_MAX;
   *         SkDEBUGCODE(fC1Span = nullptr);
   *         SkDEBUGCODE(fC2Span = nullptr);
   *         SkDEBUGCODE(fC1Index = fC2Index = -1);
   *     }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void update(const SkClosestRecord& mate) {
   *         fC1StartT = std::min(fC1StartT, mate.fC1StartT);
   *         fC1EndT = std::max(fC1EndT, mate.fC1EndT);
   *         fC2StartT = std::min(fC2StartT, mate.fC2StartT);
   *         fC2EndT = std::max(fC2EndT, mate.fC2EndT);
   *     }
   * ```
   */
  public fun update(mate: SkClosestRecord) {
    TODO("Implement update")
  }
}
