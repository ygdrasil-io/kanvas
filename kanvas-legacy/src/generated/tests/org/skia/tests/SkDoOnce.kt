package org.skia.tests

import kotlin.Boolean

/**
 * C++ original:
 * ```cpp
 * class SkDoOnce {
 * public:
 *     SkDoOnce() { fDidOnce = false; }
 *     // Make noncopyable
 *     SkDoOnce(SkDoOnce&) = delete;
 *     SkDoOnce& operator=(SkDoOnce&) = delete;
 *
 *     bool needToDo() const { return !fDidOnce; }
 *     bool alreadyDone() const { return fDidOnce; }
 *     void accomplished() {
 *         SkASSERT(!fDidOnce);
 *         fDidOnce = true;
 *     }
 *
 * private:
 *     bool fDidOnce;
 * }
 * ```
 */
public data class SkDoOnce public constructor(
  /**
   * C++ original:
   * ```cpp
   * bool fDidOnce
   * ```
   */
  private var fDidOnce: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * SkDoOnce& operator=(SkDoOnce&) = delete
   * ```
   */
  public fun assign(param0: SkDoOnce) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool needToDo() const { return !fDidOnce; }
   * ```
   */
  public fun needToDo(): Boolean {
    TODO("Implement needToDo")
  }

  /**
   * C++ original:
   * ```cpp
   * bool alreadyDone() const { return fDidOnce; }
   * ```
   */
  public fun alreadyDone(): Boolean {
    TODO("Implement alreadyDone")
  }

  /**
   * C++ original:
   * ```cpp
   * void accomplished() {
   *         SkASSERT(!fDidOnce);
   *         fDidOnce = true;
   *     }
   * ```
   */
  public fun accomplished() {
    TODO("Implement accomplished")
  }
}
