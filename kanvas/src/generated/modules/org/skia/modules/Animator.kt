package org.skia.modules

import kotlin.Float
import org.skia.foundation.SkRefCnt

/**
 * C++ original:
 * ```cpp
 * class Animator : public SkRefCnt {
 * public:
 *     using StateChanged = bool;
 *     StateChanged seek(float t) { return this->onSeek(t); }
 *
 * protected:
 *     Animator() = default;
 *
 *     virtual StateChanged onSeek(float t) = 0;
 *
 * private:
 *     Animator(const Animator&) = delete;
 *     Animator& operator=(const Animator&) = delete;
 * }
 * ```
 */
public abstract class Animator public constructor() : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * Animator() = default
   * ```
   */
  public constructor(param0: Animator) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * StateChanged seek(float t) { return this->onSeek(t); }
   * ```
   */
  public fun seek(t: Float): AnimatorStateChanged {
    TODO("Implement seek")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual StateChanged onSeek(float t) = 0
   * ```
   */
  protected abstract fun onSeek(t: Float): AnimatorStateChanged

  /**
   * C++ original:
   * ```cpp
   * Animator& operator=(const Animator&) = delete
   * ```
   */
  private fun assign(param0: Animator) {
    TODO("Implement assign")
  }
}
