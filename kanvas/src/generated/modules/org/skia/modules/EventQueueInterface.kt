package org.skia.modules

/**
 * C++ original:
 * ```cpp
 * class EventQueueInterface {
 * public:
 *     EventQueueInterface() = default;
 *     EventQueueInterface(const EventQueueInterface&) = default;
 *     EventQueueInterface(EventQueueInterface&&) = default;
 *     EventQueueInterface& operator=(const EventQueueInterface&) = default;
 *     EventQueueInterface& operator=(EventQueueInterface&&) = default;
 *     virtual ~EventQueueInterface() = default;
 *
 *     virtual void addCrossing(Point crossingPoint, const Segment& s0, const Segment& s1) = 0;
 * }
 * ```
 */
public abstract class EventQueueInterface public constructor() {
  /**
   * C++ original:
   * ```cpp
   * EventQueueInterface() = default
   * ```
   */
  public constructor(param0: EventQueueInterface) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * EventQueueInterface& operator=(const EventQueueInterface&) = default
   * ```
   */
  public fun assign(param0: EventQueueInterface) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * EventQueueInterface& operator=(EventQueueInterface&&) = default
   * ```
   */
  public abstract fun addCrossing(
    crossingPoint: Point,
    s0: Segment,
    s1: Segment,
  )
}
