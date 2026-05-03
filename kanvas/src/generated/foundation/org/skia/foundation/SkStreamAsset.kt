package org.skia.foundation

import kotlin.Boolean
import kotlin.ULong

/**
 * C++ original:
 * ```cpp
 * class SK_API SkStreamAsset : public SkStreamSeekable {
 * public:
 *     bool hasLength() const override { return true; }
 *     size_t getLength() const override = 0;
 *
 *     std::unique_ptr<SkStreamAsset> duplicate() const {
 *         return std::unique_ptr<SkStreamAsset>(this->onDuplicate());
 *     }
 *     std::unique_ptr<SkStreamAsset> fork() const {
 *         return std::unique_ptr<SkStreamAsset>(this->onFork());
 *     }
 * private:
 *     SkStreamAsset* onDuplicate() const override = 0;
 *     SkStreamAsset* onFork() const override = 0;
 * }
 * ```
 */
public abstract class SkStreamAsset : SkStreamSeekable() {
  /**
   * C++ original:
   * ```cpp
   * bool hasLength() const override { return true; }
   * ```
   */
  public override fun hasLength(): Boolean {
    TODO("Implement hasLength")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t getLength() const override = 0
   * ```
   */
  public abstract override fun getLength(): ULong

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkStreamAsset> duplicate() const {
   *         return std::unique_ptr<SkStreamAsset>(this->onDuplicate());
   *     }
   * ```
   */
  public override fun duplicate(): SkStreamAsset? {
    TODO("Implement duplicate")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkStreamAsset> fork() const {
   *         return std::unique_ptr<SkStreamAsset>(this->onFork());
   *     }
   * ```
   */
  public override fun fork(): SkStreamAsset? {
    TODO("Implement fork")
  }

  /**
   * C++ original:
   * ```cpp
   * SkStreamAsset* onDuplicate() const override = 0
   * ```
   */
  public abstract override fun onDuplicate(): SkStreamAsset

  /**
   * C++ original:
   * ```cpp
   * SkStreamAsset* onFork() const override = 0
   * ```
   */
  public abstract override fun onFork(): SkStreamAsset
}

public typealias SkFILEStreamINHERITED = SkStreamAsset
