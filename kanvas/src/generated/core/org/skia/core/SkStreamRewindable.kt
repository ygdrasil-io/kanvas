package org.skia.core

import kotlin.Boolean

/**
 * C++ original:
 * ```cpp
 * class SK_API SkStreamRewindable : public SkStream {
 * public:
 *     bool rewind() override = 0;
 *     std::unique_ptr<SkStreamRewindable> duplicate() const {
 *         return std::unique_ptr<SkStreamRewindable>(this->onDuplicate());
 *     }
 * private:
 *     SkStreamRewindable* onDuplicate() const override = 0;
 * }
 * ```
 */
public abstract class SkStreamRewindable : SkStream() {
  /**
   * C++ original:
   * ```cpp
   * bool rewind() override = 0
   * ```
   */
  public abstract override fun rewind(): Boolean

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkStreamRewindable> duplicate() const {
   *         return std::unique_ptr<SkStreamRewindable>(this->onDuplicate());
   *     }
   * ```
   */
  public override fun duplicate(): SkStreamRewindable? {
    TODO("Implement duplicate")
  }

  /**
   * C++ original:
   * ```cpp
   * SkStreamRewindable* onDuplicate() const override = 0
   * ```
   */
  public abstract override fun onDuplicate(): SkStreamRewindable
}
