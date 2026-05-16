package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.IntArray
import org.skia.core.SkPMColor4f

/**
 * C++ original:
 * ```cpp
 * class VertexColor {
 * public:
 *     VertexColor() = default;
 *
 *     explicit VertexColor(const SkPMColor4f& color, bool wideColor) {
 *         this->set(color, wideColor);
 *     }
 *
 *     void set(const SkPMColor4f& color, bool wideColor) {
 *         if (wideColor) {
 *             memcpy(fColor, color.vec(), sizeof(fColor));
 *         } else {
 *             fColor[0] = color.toBytes_RGBA();
 *         }
 *         fWideColor = wideColor;
 *     }
 *
 *     size_t size() const { return fWideColor ? 16 : 4; }
 *
 * private:
 *     template <typename T>
 *     friend VertexWriter& operator<<(VertexWriter&, const T&);
 *
 *     uint32_t fColor[4];
 *     bool     fWideColor;
 * }
 * ```
 */
public data class VertexColor public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint32_t fColor[4]
   * ```
   */
  private var fColor: IntArray,
  /**
   * C++ original:
   * ```cpp
   * bool     fWideColor
   * ```
   */
  private var fWideColor: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * void set(const SkPMColor4f& color, bool wideColor) {
   *         if (wideColor) {
   *             memcpy(fColor, color.vec(), sizeof(fColor));
   *         } else {
   *             fColor[0] = color.toBytes_RGBA();
   *         }
   *         fWideColor = wideColor;
   *     }
   * ```
   */
  public fun `set`(color: SkPMColor4f, wideColor: Boolean) {
    TODO("Implement set")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t size() const { return fWideColor ? 16 : 4; }
   * ```
   */
  public fun size(): Int {
    TODO("Implement size")
  }
}
