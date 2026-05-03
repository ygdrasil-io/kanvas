package org.skia.core

import kotlin.Int
import kotlin.Unit

/**
 * C++ original:
 * ```cpp
 * class SkTrivialExecutor final : public SkExecutor {
 * public:
 *     void add(std::function<void(void)> work, int /* workList */) override {
 *         work();
 *     }
 *     void add(std::function<void(void)> work) override {
 *         this->add(std::move(work), /* workList= */ 0);
 *     }
 *     int discardAllPendingWork() override { return 0;}
 * }
 * ```
 */
public class SkTrivialExecutor : SkExecutor() {
  /**
   * C++ original:
   * ```cpp
   * void add(std::function<void(void)> work, int /* workList */) override {
   *         work();
   *     }
   * ```
   */
  public override fun add(work: () -> Unit, param1: Int) {
    TODO("Implement add")
  }

  /**
   * C++ original:
   * ```cpp
   * void add(std::function<void(void)> work) override {
   *         this->add(std::move(work), /* workList= */ 0);
   *     }
   * ```
   */
  public override fun add(work: () -> Unit) {
    TODO("Implement add")
  }

  /**
   * C++ original:
   * ```cpp
   * int discardAllPendingWork() override { return 0;}
   * ```
   */
  public override fun discardAllPendingWork(): Int {
    TODO("Implement discardAllPendingWork")
  }
}
