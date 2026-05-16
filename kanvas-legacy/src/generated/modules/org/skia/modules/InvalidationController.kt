package org.skia.modules

import kotlin.Int
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class InvalidationController {
 * public:
 *     InvalidationController();
 *     InvalidationController(const InvalidationController&) = delete;
 *     InvalidationController& operator=(const InvalidationController&) = delete;
 *
 *     void inval(const SkRect&, const SkMatrix& ctm = SkMatrix::I());
 *
 *     const SkRect& bounds() const { return fBounds; }
 *
 *     auto begin() const { return fRects.cbegin(); }
 *     auto   end() const { return fRects.cend();   }
 *
 *     void reset();
 *
 * private:
 *     std::vector<SkRect> fRects;
 *     SkRect              fBounds;
 * }
 * ```
 */
public data class InvalidationController public constructor(
  /**
   * C++ original:
   * ```cpp
   * std::vector<SkRect> fRects
   * ```
   */
  private var fRects: Int,
  /**
   * C++ original:
   * ```cpp
   * SkRect              fBounds
   * ```
   */
  private var fBounds: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * InvalidationController& operator=(const InvalidationController&) = delete
   * ```
   */
  public fun assign(param0: InvalidationController) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * void InvalidationController::inval(const SkRect& r, const SkMatrix& ctm) {
   *     if (r.isEmpty()) {
   *         return;
   *     }
   *
   *     SkRect rect = ctm.mapRect(r);
   *
   *     fRects.push_back(rect);
   *     fBounds.join(rect);
   * }
   * ```
   */
  public fun inval(r: SkRect, ctm: SkMatrix = TODO()) {
    TODO("Implement inval")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkRect& bounds() const { return fBounds; }
   * ```
   */
  public fun bounds(): Int {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * auto begin() const { return fRects.cbegin(); }
   * ```
   */
  public fun begin() {
    TODO("Implement begin")
  }

  /**
   * C++ original:
   * ```cpp
   * auto   end() const { return fRects.cend();   }
   * ```
   */
  public fun end() {
    TODO("Implement end")
  }

  /**
   * C++ original:
   * ```cpp
   * void InvalidationController::reset() {
   *     fRects.clear();
   *     fBounds.setEmpty();
   * }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }
}
