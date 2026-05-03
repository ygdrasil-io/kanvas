package org.skia.modules

import kotlin.Int
import org.skia.foundation.SkSpan
import org.skia.tests.Reporter
import undefined.DeletionSegmentSet
import undefined.InsertionSegmentSet

/**
 * C++ original:
 * ```cpp
 * struct TestEventHandler : public SweepLineInterface {
 *     TestEventHandler(skiatest::Reporter*r,
 *                      Point eventPoint,
 *                      SkSpan<const Segment> deletions,
 *                      SkSpan<const Segment> insertions,
 *                      SkSpan<const Crossing> crossings,
 *                      HasDeletions hasDeletions = kHasDeletions)
 *         : fR(r)
 *         , fCandidateEventPoint{eventPoint}
 *         , fDeletions{deletions.begin(), deletions.end()}
 *         , fInsertions{insertions.begin(), insertions.end()}
 *         , fCrossings{crossings.begin(), crossings.end()}
 *         , fHasDeletions{hasDeletions} {}
 *
 *     void handleDeletions(Point eventPoint,
 *                          const DeletionSegmentSet& removing) override {
 *         if (fHasDeletions == kHasNoDeletions) {
 *             REPORTER_ASSERT(fR, false, "There should be no deletions.");
 *             return;
 *         }
 *
 *         REPORTER_ASSERT(fR, eventPoint == fCandidateEventPoint);
 *
 *         REPORTER_ASSERT(fR, removing.size() == fDeletions.size());
 *
 *         for (const Segment& s : fDeletions) {
 *             REPORTER_ASSERT(fR, removing.find(s) != removing.end());
 *         }
 *     }
 *
 *     void
 *     handleInsertionsAndCheckForNewCrossings(Point eventPoint,
 *                                             const InsertionSegmentSet& inserting,
 *                                             EventQueueInterface* queue) override {
 *         REPORTER_ASSERT(fR, eventPoint == fCandidateEventPoint);
 *
 *         REPORTER_ASSERT(fR, inserting.size() == fInsertions.size());
 *
 *         for (const Segment& s : fInsertions) {
 *             REPORTER_ASSERT(fR, inserting.find(s) != inserting.end());
 *         }
 *
 *         for (const Crossing& crossing : fCrossings) {
 *             auto [s0, s1, pt] = crossing;
 *             queue->addCrossing(pt, s0, s1);
 *         }
 *     }
 *
 *     skiatest::Reporter* const fR;
 *     const Point fCandidateEventPoint;
 *     std::vector<Segment> fDeletions;
 *     std::vector<Segment> fInsertions;
 *     std::vector<Crossing> fCrossings;
 *     const HasDeletions fHasDeletions;
 * }
 * ```
 */
public open class TestEventHandler public constructor(
  /**
   * C++ original:
   * ```cpp
   * skiatest::Reporter* const fR
   * ```
   */
  public val fR: Reporter?,
  /**
   * C++ original:
   * ```cpp
   * const Point fCandidateEventPoint
   * ```
   */
  public val fCandidateEventPoint: Point,
  /**
   * C++ original:
   * ```cpp
   * std::vector<Segment> fDeletions
   * ```
   */
  public var fDeletions: Int,
  /**
   * C++ original:
   * ```cpp
   * std::vector<Segment> fInsertions
   * ```
   */
  public var fInsertions: Int,
  /**
   * C++ original:
   * ```cpp
   * std::vector<Crossing> fCrossings
   * ```
   */
  public var fCrossings: Int,
  /**
   * C++ original:
   * ```cpp
   * const HasDeletions fHasDeletions
   * ```
   */
  public val fHasDeletions: HasDeletions,
) : SweepLineInterface(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * TestEventHandler(skiatest::Reporter*r,
   *                      Point eventPoint,
   *                      SkSpan<const Segment> deletions,
   *                      SkSpan<const Segment> insertions,
   *                      SkSpan<const Crossing> crossings,
   *                      HasDeletions hasDeletions = kHasDeletions)
   *         : fR(r)
   *         , fCandidateEventPoint{eventPoint}
   *         , fDeletions{deletions.begin(), deletions.end()}
   *         , fInsertions{insertions.begin(), insertions.end()}
   *         , fCrossings{crossings.begin(), crossings.end()}
   *         , fHasDeletions{hasDeletions} {}
   * ```
   */
  public constructor(
    r: Reporter,
    eventPoint: Point,
    deletions: SkSpan<Segment>,
    insertions: SkSpan<Segment>,
    crossings: SkSpan<Crossing>,
    hasDeletions: HasDeletions,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * void handleDeletions(Point eventPoint,
   *                          const DeletionSegmentSet& removing) override {
   *         if (fHasDeletions == kHasNoDeletions) {
   *             REPORTER_ASSERT(fR, false, "There should be no deletions.");
   *             return;
   *         }
   *
   *         REPORTER_ASSERT(fR, eventPoint == fCandidateEventPoint);
   *
   *         REPORTER_ASSERT(fR, removing.size() == fDeletions.size());
   *
   *         for (const Segment& s : fDeletions) {
   *             REPORTER_ASSERT(fR, removing.find(s) != removing.end());
   *         }
   *     }
   * ```
   */
  public override fun handleDeletions(eventPoint: Point, removing: DeletionSegmentSet) {
    TODO("Implement handleDeletions")
  }

  /**
   * C++ original:
   * ```cpp
   * void
   *     handleInsertionsAndCheckForNewCrossings(Point eventPoint,
   *                                             const InsertionSegmentSet& inserting,
   *                                             EventQueueInterface* queue) override {
   *         REPORTER_ASSERT(fR, eventPoint == fCandidateEventPoint);
   *
   *         REPORTER_ASSERT(fR, inserting.size() == fInsertions.size());
   *
   *         for (const Segment& s : fInsertions) {
   *             REPORTER_ASSERT(fR, inserting.find(s) != inserting.end());
   *         }
   *
   *         for (const Crossing& crossing : fCrossings) {
   *             auto [s0, s1, pt] = crossing;
   *             queue->addCrossing(pt, s0, s1);
   *         }
   *     }
   * ```
   */
  public override fun handleInsertionsAndCheckForNewCrossings(
    eventPoint: Point,
    inserting: InsertionSegmentSet,
    queue: EventQueueInterface?,
  ) {
    TODO("Implement handleInsertionsAndCheckForNewCrossings")
  }
}
