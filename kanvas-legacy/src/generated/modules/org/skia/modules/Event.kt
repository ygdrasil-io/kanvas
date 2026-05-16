package org.skia.modules

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct Event {
 *     Point where;
 *     EventType type;
 *
 *     friend bool operator< (const Event& e0, const Event& e1) {
 *         return std::tie(e0.where, e0.type) < std::tie(e1.where, e1.type);
 *     }
 * }
 * ```
 */
public data class Event public constructor(
  /**
   * C++ original:
   * ```cpp
   * Point where
   * ```
   */
  public var `where`: Int,
  /**
   * C++ original:
   * ```cpp
   * EventType type
   * ```
   */
  public var type: EventType,
)
