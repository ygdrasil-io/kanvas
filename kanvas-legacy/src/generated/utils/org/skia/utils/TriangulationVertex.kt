package org.skia.utils

import kotlin.UShort
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * struct TriangulationVertex {
 *     SK_DECLARE_INTERNAL_LLIST_INTERFACE(TriangulationVertex);
 *
 *     enum class VertexType { kConvex, kReflex };
 *
 *     SkPoint    fPosition;
 *     VertexType fVertexType;
 *     uint16_t   fIndex;
 *     uint16_t   fPrevIndex;
 *     uint16_t   fNextIndex;
 * }
 * ```
 */
public data class TriangulationVertex public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkPoint    fPosition
   * ```
   */
  public var fPosition: SkPoint,
  /**
   * C++ original:
   * ```cpp
   * VertexType fVertexType
   * ```
   */
  public var fVertexType: VertexType,
  /**
   * C++ original:
   * ```cpp
   * uint16_t   fIndex
   * ```
   */
  public var fIndex: UShort,
  /**
   * C++ original:
   * ```cpp
   * uint16_t   fPrevIndex
   * ```
   */
  public var fPrevIndex: UShort,
  /**
   * C++ original:
   * ```cpp
   * uint16_t   fNextIndex
   * ```
   */
  public var fNextIndex: UShort,
) {
  public enum class VertexType {
    kConvex,
    kReflex,
  }
}
