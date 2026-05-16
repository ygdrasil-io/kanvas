package org.skia.modules

import undefined.DeletionSegmentSet
import undefined.InsertionSegmentSet

/**
 * C++ original:
 * ```cpp
 * class SweepLineInterface {
 * public:
 *     virtual ~SweepLineInterface() = default;
 *
 *     // These are the segments to remove from the sweep line.
 *     virtual void handleDeletions(Point eventPoint, const DeletionSegmentSet& removing) = 0;
 *
 *     // Insert inserting into the sweep line. Check the inserting segments against the existing
 *     // sweep line segments and report any crossings using the addCrossing from the
 *     // EventQueueInterface.
 *     virtual void handleInsertionsAndCheckForNewCrossings(
 *             Point eventPoint, const InsertionSegmentSet& inserting, EventQueueInterface* queue) = 0;
 * }
 * ```
 */
public abstract class SweepLineInterface {
  /**
   * C++ original:
   * ```cpp
   * virtual void handleDeletions(Point eventPoint, const DeletionSegmentSet& removing) = 0
   * ```
   */
  public abstract fun handleDeletions(eventPoint: Point, removing: DeletionSegmentSet)

  /**
   * C++ original:
   * ```cpp
   * virtual void handleInsertionsAndCheckForNewCrossings(
   *             Point eventPoint, const InsertionSegmentSet& inserting, EventQueueInterface* queue) = 0
   * ```
   */
  public abstract fun handleInsertionsAndCheckForNewCrossings(
    eventPoint: Point,
    inserting: InsertionSegmentSet,
    queue: EventQueueInterface?,
  )
}
