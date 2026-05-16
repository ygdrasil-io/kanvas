package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SkScopeExit {
 * public:
 *     SkScopeExit() = default;
 *     explicit SkScopeExit(std::function<void()> f) : fFn(std::move(f)) {}
 *     SkScopeExit(SkScopeExit&& that) : fFn(std::move(that.fFn)) {}
 *
 *     ~SkScopeExit() {
 *         if (fFn) {
 *             fFn();
 *         }
 *     }
 *
 *     void clear() { fFn = {}; }
 *
 *     SkScopeExit& operator=(SkScopeExit&& that) {
 *         fFn = std::move(that.fFn);
 *         return *this;
 *     }
 *
 * private:
 *     std::function<void()> fFn;
 *
 *     SkScopeExit(           const SkScopeExit& ) = delete;
 *     SkScopeExit& operator=(const SkScopeExit& ) = delete;
 * }
 * ```
 */
public data class SkScopeExit public constructor(
  /**
   * C++ original:
   * ```cpp
   * std::function<void()> fFn
   * ```
   */
  private var fFn: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * void clear() { fFn = {}; }
   * ```
   */
  public fun clear() {
    TODO("Implement clear")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScopeExit& operator=(SkScopeExit&& that) {
   *         fFn = std::move(that.fFn);
   *         return *this;
   *     }
   * ```
   */
  public fun assign(that: SkScopeExit) {
    TODO("Implement assign")
  }
}
