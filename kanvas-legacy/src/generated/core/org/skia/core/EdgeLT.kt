package org.skia.core

import kotlin.Boolean

/**
 * C++ original:
 * ```cpp
 * struct EdgeLT {
 *     bool operator()(const Edge& a, const Edge& b) const {
 *         return (a.fX == b.fX) ? a.top() < b.top() : a.fX < b.fX;
 *     }
 * }
 * ```
 */
public open class EdgeLT {
  /**
   * C++ original:
   * ```cpp
   * bool operator()(const Edge& a, const Edge& b) const {
   *         return (a.fX == b.fX) ? a.top() < b.top() : a.fX < b.fX;
   *     }
   * ```
   */
  public operator fun invoke(a: Edge, b: Edge): Boolean {
    TODO("Implement invoke")
  }
}
