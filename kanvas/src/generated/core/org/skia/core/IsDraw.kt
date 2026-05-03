package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class IsDraw {
 * public:
 *     IsDraw() : fPaint(nullptr) {}
 *
 *     SkPaint* get() { return fPaint; }
 *
 *     template <typename T>
 *     std::enable_if_t<(T::kTags & kDrawWithPaint_Tag) == kDrawWithPaint_Tag, bool>
 *     operator()(T* draw) {
 *         fPaint = AsPtr(draw->paint);
 *         return true;
 *     }
 *
 *     template <typename T>
 *     std::enable_if_t<(T::kTags & kDrawWithPaint_Tag) == kDraw_Tag, bool> operator()(T* draw) {
 *         fPaint = nullptr;
 *         return true;
 *     }
 *
 *     template <typename T>
 *     std::enable_if_t<!(T::kTags & kDraw_Tag), bool> operator()(T* draw) {
 *         fPaint = nullptr;
 *         return false;
 *     }
 *
 * private:
 *     // Abstracts away whether the paint is always part of the command or optional.
 *     template <typename T> static T* AsPtr(SkRecords::Optional<T>& x) { return x; }
 *     template <typename T> static T* AsPtr(T& x) { return &x; }
 *
 *     SkPaint* fPaint;
 * }
 * ```
 */
public open class IsDraw public constructor() {
  /**
   * C++ original:
   * ```cpp
   * SkPaint* get() { return fPaint; }
   * ```
   */
  public fun `get`(): SkPaint {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename T>
   *     std::enable_if_t<(T::kTags & kDrawWithPaint_Tag) == kDrawWithPaint_Tag, bool>
   *     operator()(T* draw) {
   *         fPaint = AsPtr(draw->paint);
   *         return true;
   *     }
   * ```
   */
  public operator fun <T> invoke(draw: T?): Int {
    TODO("Implement invoke")
  }

  public companion object {
    private var fPaint: SkPaint? = TODO("Initialize fPaint")

    /**
     * C++ original:
     * ```cpp
     * static T* AsPtr(SkRecords::Optional<T>& x) { return x; }
     * ```
     */
    private fun <T> asPtr(x: Optional<T>): T {
      TODO("Implement asPtr")
    }

    /**
     * C++ original:
     * ```cpp
     * static T* AsPtr(T& x) { return &x; }
     * ```
     */
    private fun asPtr(x: T): T {
      TODO("Implement asPtr")
    }
  }
}
