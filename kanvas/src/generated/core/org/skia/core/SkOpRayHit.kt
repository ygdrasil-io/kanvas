package org.skia.core

import kotlin.Boolean
import kotlin.Double
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * struct SkOpRayHit {
 *     SkOpRayDir makeTestBase(SkOpSpan* span, double t) {
 *         fNext = nullptr;
 *         fSpan = span;
 *         fT = span->t() * (1 - t) + span->next()->t() * t;
 *         SkOpSegment* segment = span->segment();
 *         fSlope = segment->dSlopeAtT(fT);
 *         fPt = segment->ptAtT(fT);
 *         fValid = true;
 *         return fabs(fSlope.fX) < fabs(fSlope.fY) ? SkOpRayDir::kLeft : SkOpRayDir::kTop;
 *     }
 *
 *     SkOpRayHit* fNext;
 *     SkOpSpan* fSpan;
 *     SkPoint fPt;
 *     double fT;
 *     SkDVector fSlope;
 *     bool fValid;
 * }
 * ```
 */
public data class SkOpRayHit public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkOpRayHit* fNext
   * ```
   */
  public var fNext: SkOpRayHit?,
  /**
   * C++ original:
   * ```cpp
   * SkOpSpan* fSpan
   * ```
   */
  public var fSpan: SkOpSpan?,
  /**
   * C++ original:
   * ```cpp
   * SkPoint fPt
   * ```
   */
  public var fPt: SkPoint,
  /**
   * C++ original:
   * ```cpp
   * double fT
   * ```
   */
  public var fT: Double,
  /**
   * C++ original:
   * ```cpp
   * SkDVector fSlope
   * ```
   */
  public var fSlope: SkDVector,
  /**
   * C++ original:
   * ```cpp
   * bool fValid
   * ```
   */
  public var fValid: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * SkOpRayDir makeTestBase(SkOpSpan* span, double t) {
   *         fNext = nullptr;
   *         fSpan = span;
   *         fT = span->t() * (1 - t) + span->next()->t() * t;
   *         SkOpSegment* segment = span->segment();
   *         fSlope = segment->dSlopeAtT(fT);
   *         fPt = segment->ptAtT(fT);
   *         fValid = true;
   *         return fabs(fSlope.fX) < fabs(fSlope.fY) ? SkOpRayDir::kLeft : SkOpRayDir::kTop;
   *     }
   * ```
   */
  public fun makeTestBase(span: SkOpSpan?, t: Double): SkOpRayDir {
    TODO("Implement makeTestBase")
  }
}
