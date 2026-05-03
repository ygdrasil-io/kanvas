package org.skia.foundation

import kotlin.Boolean
import kotlin.Long
import kotlin.ULong

/**
 * C++ original:
 * ```cpp
 * class SK_API SkStreamSeekable : public SkStreamRewindable {
 * public:
 *     std::unique_ptr<SkStreamSeekable> duplicate() const {
 *         return std::unique_ptr<SkStreamSeekable>(this->onDuplicate());
 *     }
 *
 *     bool hasPosition() const override { return true; }
 *     size_t getPosition() const override = 0;
 *     bool seek(size_t position) override = 0;
 *     bool move(long offset) override = 0;
 *
 *     std::unique_ptr<SkStreamSeekable> fork() const {
 *         return std::unique_ptr<SkStreamSeekable>(this->onFork());
 *     }
 * private:
 *     SkStreamSeekable* onDuplicate() const override = 0;
 *     SkStreamSeekable* onFork() const override = 0;
 * }
 * ```
 */
public abstract class SkStreamSeekable : SkStreamRewindable() {
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkStreamSeekable> duplicate() const {
   *         return std::unique_ptr<SkStreamSeekable>(this->onDuplicate());
   *     }
   * ```
   */
  public override fun duplicate(): SkStreamSeekable? {
    TODO("Implement duplicate")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasPosition() const override { return true; }
   * ```
   */
  public override fun hasPosition(): Boolean {
    TODO("Implement hasPosition")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t getPosition() const override = 0
   * ```
   */
  public abstract override fun getPosition(): ULong

  /**
   * C++ original:
   * ```cpp
   * bool seek(size_t position) override = 0
   * ```
   */
  public abstract override fun seek(position: ULong): Boolean

  /**
   * C++ original:
   * ```cpp
   * bool move(long offset) override = 0
   * ```
   */
  public abstract override fun move(offset: Long): Boolean

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkStreamSeekable> fork() const {
   *         return std::unique_ptr<SkStreamSeekable>(this->onFork());
   *     }
   * ```
   */
  public fun fork(): SkStreamSeekable? {
    TODO("Implement fork")
  }

  /**
   * C++ original:
   * ```cpp
   * SkStreamSeekable* onDuplicate() const override = 0
   * ```
   */
  public abstract override fun onDuplicate(): SkStreamSeekable

  /**
   * C++ original:
   * ```cpp
   * SkStreamSeekable* onFork() const override = 0
   * ```
   */
  public abstract override fun onFork(): SkStreamSeekable
}
