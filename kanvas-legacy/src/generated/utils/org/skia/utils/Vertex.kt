package org.skia.utils

import kotlin.Boolean
import kotlin.UShort
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * struct Vertex {
 *     static bool Left(const Vertex& qv0, const Vertex& qv1) {
 *         return left(qv0.fPosition, qv1.fPosition);
 *     }
 *
 *     // packed to fit into 16 bytes (one cache line)
 *     SkPoint  fPosition;
 *     uint16_t fIndex;       // index in unsorted polygon
 *     uint16_t fPrevIndex;   // indices for previous and next vertex in unsorted polygon
 *     uint16_t fNextIndex;
 *     uint16_t fFlags;
 * }
 * ```
 */
public data class Vertex public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkPoint  fPosition
   * ```
   */
  public var fPosition: SkPoint,
  /**
   * C++ original:
   * ```cpp
   * uint16_t fIndex
   * ```
   */
  public var fIndex: UShort,
  /**
   * C++ original:
   * ```cpp
   * uint16_t fPrevIndex
   * ```
   */
  public var fPrevIndex: UShort,
  /**
   * C++ original:
   * ```cpp
   * uint16_t fNextIndex
   * ```
   */
  public var fNextIndex: UShort,
  /**
   * C++ original:
   * ```cpp
   * uint16_t fFlags
   * ```
   */
  public var fFlags: UShort,
) {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static bool Left(const Vertex& qv0, const Vertex& qv1) {
     *         return left(qv0.fPosition, qv1.fPosition);
     *     }
     * ```
     */
    public fun left(qv0: Vertex, qv1: Vertex): Boolean {
      TODO("Implement left")
    }
  }
}
