package org.skia.modules

/**
 * C++ original:
 * ```cpp
 * class EventQueueTestingPeer {
 * public:
 *     static Event NextEvent(EventQueue* eq) {
 *         SkASSERT(eq->hasMoreEvents());
 *
 *         auto firstElement = eq->fQueue.begin();
 *
 *         // Extract event at the beginning of the queue.
 *         Event event = *firstElement;
 *
 *         // Remove the beginning element from the queue.
 *         eq->fQueue.erase(firstElement);
 *
 *         return event;
 *     }
 * }
 * ```
 */
public open class EventQueueTestingPeer {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static Event NextEvent(EventQueue* eq) {
     *         SkASSERT(eq->hasMoreEvents());
     *
     *         auto firstElement = eq->fQueue.begin();
     *
     *         // Extract event at the beginning of the queue.
     *         Event event = *firstElement;
     *
     *         // Remove the beginning element from the queue.
     *         eq->fQueue.erase(firstElement);
     *
     *         return event;
     *     }
     * ```
     */
    public fun nextEvent(eq: EventQueue?): Event {
      TODO("Implement nextEvent")
    }
  }
}
