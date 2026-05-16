package org.skia.core

import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct SkClosestSect {
 *     SkClosestSect()
 *         : fUsed(0) {
 *         fClosest.push_back().reset();
 *     }
 *
 *     bool find(const SkTSpan* span1, const SkTSpan* span2
 *             SkDEBUGPARAMS(SkIntersections* i)) {
 *         SkClosestRecord* record = &fClosest[fUsed];
 *         record->findEnd(span1, span2, 0, 0);
 *         record->findEnd(span1, span2, 0, span2->part().pointLast());
 *         record->findEnd(span1, span2, span1->part().pointLast(), 0);
 *         record->findEnd(span1, span2, span1->part().pointLast(), span2->part().pointLast());
 *         if (record->fClosest == FLT_MAX) {
 *             return false;
 *         }
 *         for (int index = 0; index < fUsed; ++index) {
 *             SkClosestRecord* test = &fClosest[index];
 *             if (test->matesWith(*record  SkDEBUGPARAMS(i))) {
 *                 if (test->fClosest > record->fClosest) {
 *                     test->merge(*record);
 *                 }
 *                 test->update(*record);
 *                 record->reset();
 *                 return false;
 *             }
 *         }
 *         ++fUsed;
 *         fClosest.push_back().reset();
 *         return true;
 *     }
 *
 *     void finish(SkIntersections* intersections) const {
 *         STArray<SkDCubic::kMaxIntersections * 3,
 *                 const SkClosestRecord*, true> closestPtrs;
 *         for (int index = 0; index < fUsed; ++index) {
 *             closestPtrs.push_back(&fClosest[index]);
 *         }
 *         SkTQSort<const SkClosestRecord>(closestPtrs.begin(), closestPtrs.end());
 *         for (int index = 0; index < fUsed; ++index) {
 *             const SkClosestRecord* test = closestPtrs[index];
 *             test->addIntersection(intersections);
 *         }
 *     }
 *
 *     // this is oversized so that an extra records can merge into final one
 *     STArray<SkDCubic::kMaxIntersections * 2, SkClosestRecord, true> fClosest;
 *     int fUsed;
 * }
 * ```
 */
public data class SkClosestSect public constructor(
  /**
   * C++ original:
   * ```cpp
   * STArray<SkDCubic::kMaxIntersections * 2, SkClosestRecord, true> fClosest
   * ```
   */
  public var fClosest: STArray<SkDCubic.`KMaxIntersections * 2`, SkClosestRecord>,
  /**
   * C++ original:
   * ```cpp
   * int fUsed
   * ```
   */
  public var fUsed: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool find(const SkTSpan* span1, const SkTSpan* span2
   *             SkDEBUGPARAMS(SkIntersections* i)) {
   *         SkClosestRecord* record = &fClosest[fUsed];
   *         record->findEnd(span1, span2, 0, 0);
   *         record->findEnd(span1, span2, 0, span2->part().pointLast());
   *         record->findEnd(span1, span2, span1->part().pointLast(), 0);
   *         record->findEnd(span1, span2, span1->part().pointLast(), span2->part().pointLast());
   *         if (record->fClosest == FLT_MAX) {
   *             return false;
   *         }
   *         for (int index = 0; index < fUsed; ++index) {
   *             SkClosestRecord* test = &fClosest[index];
   *             if (test->matesWith(*record  SkDEBUGPARAMS(i))) {
   *                 if (test->fClosest > record->fClosest) {
   *                     test->merge(*record);
   *                 }
   *                 test->update(*record);
   *                 record->reset();
   *                 return false;
   *             }
   *         }
   *         ++fUsed;
   *         fClosest.push_back().reset();
   *         return true;
   *     }
   * ```
   */
  public fun find(
    param0: SkTSpan?,
    param1: SkTSpan?,
    param2: SkIntersections?,
  ): Boolean {
    TODO("Implement find")
  }

  /**
   * C++ original:
   * ```cpp
   * void finish(SkIntersections* intersections) const {
   *         STArray<SkDCubic::kMaxIntersections * 3,
   *                 const SkClosestRecord*, true> closestPtrs;
   *         for (int index = 0; index < fUsed; ++index) {
   *             closestPtrs.push_back(&fClosest[index]);
   *         }
   *         SkTQSort<const SkClosestRecord>(closestPtrs.begin(), closestPtrs.end());
   *         for (int index = 0; index < fUsed; ++index) {
   *             const SkClosestRecord* test = closestPtrs[index];
   *             test->addIntersection(intersections);
   *         }
   *     }
   * ```
   */
  public fun finish(intersections: SkIntersections?) {
    TODO("Implement finish")
  }
}
