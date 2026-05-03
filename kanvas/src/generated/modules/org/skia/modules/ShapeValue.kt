package org.skia.modules

import kotlin.Any
import kotlin.Float
import kotlin.collections.List
import org.skia.foundation.SkPath

/**
 * C++ original:
 * ```cpp
 * class ShapeValue final : public std::vector<float> {
 * public:
 *     operator SkPath() const;
 * }
 * ```
 */
public class ShapeValue : List<Any>(), Float {
  /**
   * C++ original:
   * ```cpp
   * ShapeValue::operator SkPath() const {
   *     const auto vertex_count = this->size() / kFloatsPerVertex;
   *
   *     SkPathBuilder path;
   *
   *     if (vertex_count) {
   *         // conservatively assume all cubics
   *         path.incReserve(1 + SkToInt(vertex_count * 3));
   *
   *         // Move to first vertex.
   *         path.moveTo((*this)[kX_Index], (*this)[kY_Index]);
   *     }
   *
   *     auto addCubic = [&](size_t from_vertex, size_t to_vertex) {
   *         const auto from_index = kFloatsPerVertex * from_vertex,
   *                      to_index = kFloatsPerVertex *   to_vertex;
   *
   *         const SkPoint p0 = SkPoint{ (*this)[from_index +    kX_Index],
   *                                     (*this)[from_index +    kY_Index] },
   *                       p1 = SkPoint{ (*this)[  to_index +    kX_Index],
   *                                     (*this)[  to_index +    kY_Index] },
   *                       c0 = SkPoint{ (*this)[from_index + kOutX_Index],
   *                                     (*this)[from_index + kOutY_Index] } + p0,
   *                       c1 = SkPoint{ (*this)[  to_index +  kInX_Index],
   *                                     (*this)[  to_index +  kInY_Index] } + p1;
   *
   *         if (c0 == p0 && c1 == p1) {
   *             // If the control points are coincident, we can power-reduce to a straight line.
   *             // TODO: we could also do that when the controls are on the same line as the
   *             //       vertices, but it's unclear how common that case is.
   *             path.lineTo(p1);
   *         } else {
   *             path.cubicTo(c0, c1, p1);
   *         }
   *     };
   *
   *     for (size_t i = 1; i < vertex_count; ++i) {
   *         addCubic(i - 1, i);
   *     }
   *
   *     // Close the path with an extra cubic, if needed.
   *     if (vertex_count && this->back() != 0) {
   *         addCubic(vertex_count - 1, 0);
   *         path.close();
   *     }
   *
   *     return path.detach();
   * }
   * ```
   */
  public fun toSkPath(): SkPath {
    TODO("Implement toSkPath")
  }
}
