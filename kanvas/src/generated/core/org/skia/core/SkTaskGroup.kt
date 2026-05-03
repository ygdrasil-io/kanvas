package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.Unit

/**
 * C++ original:
 * ```cpp
 * class SkTaskGroup : SkNoncopyable {
 * public:
 *     // Tasks added to this SkTaskGroup will run on its executor.
 *     explicit SkTaskGroup(SkExecutor& executor = SkExecutor::GetDefault());
 *     ~SkTaskGroup() { this->wait(); }
 *
 *     // Add a task to this SkTaskGroup.
 *     void add(std::function<void(void)> fn);
 *     void add(std::function<void(void)> fn, int workList);
 *
 *     void discardAllPendingWork();
 *
 *     // Add a batch of N tasks, all calling fn with different arguments.
 *     void batch(int N, std::function<void(int)> fn);
 *
 *     // Returns true if all Tasks previously add()ed to this SkTaskGroup have run.
 *     // It is safe to reuse this SkTaskGroup once done().
 *     bool done() const;
 *
 *     // Block until done().
 *     void wait();
 *
 *     // A convenience for testing tools.
 *     // Creates and owns a thread pool, and passes it to SkExecutor::SetDefault().
 *     struct Enabler {
 *         explicit Enabler(int threads = -1);  // -1 -> num_cores, 0 -> noop
 *         std::unique_ptr<SkExecutor> fThreadPool;
 *     };
 *
 * private:
 *     std::atomic<int32_t> fPending;
 *     SkExecutor&          fExecutor;
 * }
 * ```
 */
public open class SkTaskGroup public constructor(
  executor: SkExecutor = TODO(),
) : SkNoncopyable() {
  /**
   * C++ original:
   * ```cpp
   * std::atomic<int32_t> fPending
   * ```
   */
  private var fPending: Int = TODO("Initialize fPending")

  /**
   * C++ original:
   * ```cpp
   * SkExecutor&          fExecutor
   * ```
   */
  private var fExecutor: SkExecutor = TODO("Initialize fExecutor")

  /**
   * C++ original:
   * ```cpp
   * void add(std::function<void(void)> fn)
   * ```
   */
  public fun add(fn: () -> Unit) {
    TODO("Implement add")
  }

  /**
   * C++ original:
   * ```cpp
   * void add(std::function<void(void)> fn, int workList)
   * ```
   */
  public fun add(fn: () -> Unit, workList: Int) {
    TODO("Implement add")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTaskGroup::discardAllPendingWork() {
   *     int numDiscarded = fExecutor.discardAllPendingWork();
   *     fPending.fetch_add(-numDiscarded, std::memory_order_release);
   * }
   * ```
   */
  public fun discardAllPendingWork() {
    TODO("Implement discardAllPendingWork")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTaskGroup::batch(int N, std::function<void(int)> fn) {
   *     // TODO: I really thought we had some sort of more clever chunking logic.
   *     fPending.fetch_add(+N, std::memory_order_relaxed);
   *     for (int i = 0; i < N; i++) {
   *         fExecutor.add([fn, i, this] {
   *             fn(i);
   *             fPending.fetch_add(-1, std::memory_order_release);
   *         });
   *     }
   * }
   * ```
   */
  public fun batch(n: Int, fn: (Int) -> Unit) {
    TODO("Implement batch")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTaskGroup::done() const {
   *     return fPending.load(std::memory_order_acquire) == 0;
   * }
   * ```
   */
  public fun done(): Boolean {
    TODO("Implement done")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTaskGroup::wait() {
   *     // Actively help the executor do work until our task group is done.
   *     // This lets SkTaskGroups nest arbitrarily deep on a single SkExecutor:
   *     // no thread ever blocks waiting for others to do its work.
   *     // (We may end up doing work that's not part of our task group.  That's fine.)
   *     while (!this->done()) {
   *         fExecutor.borrow();
   *     }
   * }
   * ```
   */
  public fun wait() {
    TODO("Implement wait")
  }

  public data class Enabler public constructor(
    public var fThreadPool: Int,
  )
}
