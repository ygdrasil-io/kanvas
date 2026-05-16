package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkSpan
import undefined.Queue

/**
 * C++ original:
 * ```cpp
 * class EventQueue : public EventQueueInterface {
 * public:
 *     // Queue ordered by Event where the point is the most important followed by type and then
 *     // contents of the event. The ordering of the contents of the event is arbitrary but need to
 *     // enforce uniqueness of the events in the queue.
 *     using Queue = std::set<Event>;
 *
 *     static std::optional<EventQueue> Make(SkSpan<const Segment> segments);
 *
 *     explicit EventQueue(Queue&& queue);
 *     EventQueue() = default;
 *
 *     void addCrossing(Point crossingPoint, const Segment& s0, const Segment& s1) override;
 *
 *     bool hasMoreEvents() const;
 *     void handleNextEventPoint(SweepLineInterface* handler);
 *     std::vector<Crossing> crossings();
 *
 * private:
 *     friend class EventQueueTestingPeer;
 *     Point fLastEventPoint = Point::Smallest();
 *
 *     void add(const Event& e);
 *
 *     DeletionSegmentSet fDeletionSet;
 *     InsertionSegmentSet fInsertionSet;
 *     Queue fQueue;
 *     std::vector<Crossing> fCrossings;
 * }
 * ```
 */
public open class EventQueue public constructor(
  queue: Queue,
) : EventQueueInterface() {
  /**
   * C++ original:
   * ```cpp
   * Point fLastEventPoint
   * ```
   */
  private var fLastEventPoint: Int = TODO("Initialize fLastEventPoint")

  /**
   * C++ original:
   * ```cpp
   * DeletionSegmentSet fDeletionSet
   * ```
   */
  private var fDeletionSet: Int = TODO("Initialize fDeletionSet")

  /**
   * C++ original:
   * ```cpp
   * InsertionSegmentSet fInsertionSet
   * ```
   */
  private var fInsertionSet: Int = TODO("Initialize fInsertionSet")

  /**
   * C++ original:
   * ```cpp
   * Queue fQueue
   * ```
   */
  private var fQueue: Int = TODO("Initialize fQueue")

  /**
   * C++ original:
   * ```cpp
   * std::vector<Crossing> fCrossings
   * ```
   */
  private var fCrossings: Int = TODO("Initialize fCrossings")

  /**
   * C++ original:
   * ```cpp
   * EventQueue::EventQueue(EventQueue::Queue&& queue) : fQueue{std::move(queue)} { }
   * ```
   */
  public constructor() : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * void EventQueue::addCrossing(Point crossingPoint, const Segment& s0, const Segment& s1) {
   *     this->add({crossingPoint, Cross{s0, s1}});
   *     fCrossings.push_back({s0, s1, crossingPoint});
   * }
   * ```
   */
  public override fun addCrossing(
    crossingPoint: Point,
    s0: Segment,
    s1: Segment,
  ) {
    TODO("Implement addCrossing")
  }

  /**
   * C++ original:
   * ```cpp
   * bool EventQueue::hasMoreEvents() const {
   *     return !fQueue.empty();
   * }
   * ```
   */
  public fun hasMoreEvents(): Boolean {
    TODO("Implement hasMoreEvents")
  }

  /**
   * C++ original:
   * ```cpp
   * void EventQueue::handleNextEventPoint(SweepLineInterface* handler) {
   *     SkASSERT(!fQueue.empty());
   *
   *     // Clear temp segment buffers.
   *     fDeletionSet.clear();
   *     fInsertionSet.clear();
   *
   *     // An events that are Lower points.
   *     bool hasLower = false;
   *
   *     // Set up the visitors for the different event types.
   *     auto handleLower = [&hasLower](const Lower& l) {
   *         hasLower = true;
   *     };
   *
   *     // Crossing Segments must be deleted and re-inserted in the sweep line.
   *     auto handleCross = [this](const Cross& c) {
   *         fDeletionSet.insert({c.s0, c.s1});
   *         fInsertionSet.insert({c.s0, c.s1});
   *     };
   *
   *     // Upper events are added to the sweep line, and a lower event is added to the event queue.
   *     auto handleUpper = [this](const Upper& u) {
   *         fInsertionSet.insert(u.s);
   *         // Add the delete event for the inserted segment. Make sure we are not adding more events
   *         // on this eventPoint.
   *         SkASSERT(u.s.lower() != u.s.upper());
   *         this->add(Event{u.s.lower(), Lower{}});
   *     };
   *
   *     Visitor visitor{handleLower, handleCross, handleUpper};
   *
   *     const Point eventPoint = fQueue.begin()->where;
   *
   *     // We must make forward progress.
   *     SkASSERT(fLastEventPoint < eventPoint);
   *     fLastEventPoint = eventPoint;
   *
   *     // Accumulate changes for all events with the same event point.
   *     auto cursor = fQueue.begin();
   *     const auto queueEnd = fQueue.end();
   *     for (; cursor != queueEnd && cursor->where == eventPoint;
   *          ++cursor) {
   *         const Event& event = *cursor;
   *         std::visit(visitor, event.type);
   *     }
   *
   *     // Remove all accumulated events with the same event point.
   *     fQueue.erase(fQueue.begin(), cursor);
   *
   *     if (hasLower || !fDeletionSet.empty()) {
   *         // There are segments to delete.
   *         handler->handleDeletions(eventPoint, fDeletionSet);
   *     }
   *
   *     if (hasLower || !fDeletionSet.empty() || !fInsertionSet.empty()) {
   *         // If there are insertions then insert them. If there are no insertions, but there were
   *         // deletions we need to check for new crossings.
   *         handler->handleInsertionsAndCheckForNewCrossings(eventPoint, fInsertionSet, this);
   *     }
   * }
   * ```
   */
  public fun handleNextEventPoint(handler: SweepLineInterface?) {
    TODO("Implement handleNextEventPoint")
  }

  /**
   * C++ original:
   * ```cpp
   * std::vector<Crossing> EventQueue::crossings() {
   *     return std::vector<Crossing>{fCrossings.begin(), fCrossings.end()};
   * }
   * ```
   */
  public fun crossings(): Int {
    TODO("Implement crossings")
  }

  /**
   * C++ original:
   * ```cpp
   * void EventQueue::add(const Event& event) {
   *     // New events must be up stream from the current event.
   *     SkASSERT(fLastEventPoint < event.where);
   *
   *     fQueue.insert(event);
   * }
   * ```
   */
  private fun add(e: Event) {
    TODO("Implement add")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * std::optional<EventQueue> EventQueue::Make(SkSpan<const Segment> segments) {
     *     Queue queue;
     *
     *     int32_t left   = Point::Largest().x,
     *             top    = Point::Largest().y,
     *             right  = Point::Smallest().x,
     *             bottom = Point::Smallest().y;
     *
     *     for(const Segment& s : segments) {
     *         auto [l, t, r, b] = s.bounds();
     *         left   = std::min(l, left);
     *         top    = std::min(t, top);
     *         right  = std::max(r, right);
     *         bottom = std::max(b, bottom);
     *
     *         queue.insert(Event{s.upper(), Upper{s}});
     *     }
     *
     *     // If min max difference is too large, fail.
     *     if (Point::DifferenceTooBig(Point{left, top}, Point{right, bottom})) {
     *         return std::nullopt;
     *     }
     *
     *     return EventQueue{std::move(queue)};
     * }
     * ```
     */
    public fun make(segments: SkSpan<Segment>): EventQueue? {
      TODO("Implement make")
    }
  }
}
