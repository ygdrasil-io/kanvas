package org.skia.core

/**
 * C++ original:
 * ```cpp
 * class SK_API SkStreamMemory : public SkStreamAsset {
 * public:
 *     const void* getMemoryBase() override = 0;
 *
 *     std::unique_ptr<SkStreamMemory> duplicate() const {
 *         return std::unique_ptr<SkStreamMemory>(this->onDuplicate());
 *     }
 *     std::unique_ptr<SkStreamMemory> fork() const {
 *         return std::unique_ptr<SkStreamMemory>(this->onFork());
 *     }
 * private:
 *     SkStreamMemory* onDuplicate() const override = 0;
 *     SkStreamMemory* onFork() const override = 0;
 * }
 * ```
 */
public abstract class SkStreamMemory : SkStreamAsset() {
  /**
   * C++ original:
   * ```cpp
   * const void* getMemoryBase() override = 0
   * ```
   */
  public abstract override fun getMemoryBase()

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkStreamMemory> duplicate() const {
   *         return std::unique_ptr<SkStreamMemory>(this->onDuplicate());
   *     }
   * ```
   */
  public override fun duplicate(): SkStreamMemory? {
    TODO("Implement duplicate")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkStreamMemory> fork() const {
   *         return std::unique_ptr<SkStreamMemory>(this->onFork());
   *     }
   * ```
   */
  public override fun fork(): SkStreamMemory? {
    TODO("Implement fork")
  }

  /**
   * C++ original:
   * ```cpp
   * SkStreamMemory* onDuplicate() const override = 0
   * ```
   */
  public abstract override fun onDuplicate(): SkStreamMemory

  /**
   * C++ original:
   * ```cpp
   * SkStreamMemory* onFork() const override = 0
   * ```
   */
  public abstract override fun onFork(): SkStreamMemory
}

public typealias SkMemoryStreamINHERITED = SkStreamMemory
