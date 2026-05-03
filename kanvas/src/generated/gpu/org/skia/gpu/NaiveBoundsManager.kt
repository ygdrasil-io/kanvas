package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class NaiveBoundsManager final : public BoundsManager {
 * public:
 *     ~NaiveBoundsManager() override {}
 *
 *     CompressedPaintersOrder getMostRecentDraw(const Rect& bounds) const override {
 *         return fLatestDraw;
 *     }
 *
 *
 *     void recordDraw(const Rect& bounds, CompressedPaintersOrder order) override {
 *         if (fLatestDraw < order) {
 *             fLatestDraw = order;
 *         }
 *     }
 *
 *     void reset() override {
 *         fLatestDraw = CompressedPaintersOrder::First();
 *     }
 *
 * private:
 *     CompressedPaintersOrder fLatestDraw = CompressedPaintersOrder::First();
 * }
 * ```
 */
public class NaiveBoundsManager : BoundsManager() {
  /**
   * C++ original:
   * ```cpp
   * CompressedPaintersOrder fLatestDraw
   * ```
   */
  private var fLatestDraw: Int = TODO("Initialize fLatestDraw")

  /**
   * C++ original:
   * ```cpp
   * CompressedPaintersOrder getMostRecentDraw(const Rect& bounds) const override {
   *         return fLatestDraw;
   *     }
   * ```
   */
  public override fun getMostRecentDraw(bounds: Rect): Int {
    TODO("Implement getMostRecentDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void recordDraw(const Rect& bounds, CompressedPaintersOrder order) override {
   *         if (fLatestDraw < order) {
   *             fLatestDraw = order;
   *         }
   *     }
   * ```
   */
  public override fun recordDraw(bounds: Rect, order: CompressedPaintersOrder) {
    TODO("Implement recordDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void reset() override {
   *         fLatestDraw = CompressedPaintersOrder::First();
   *     }
   * ```
   */
  public override fun reset() {
    TODO("Implement reset")
  }
}
