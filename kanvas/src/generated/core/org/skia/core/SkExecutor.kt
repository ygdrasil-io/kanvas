package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.Unit

/**
 * C++ original:
 * ```cpp
 * class SK_API SkExecutor {
 * public:
 *     virtual ~SkExecutor();
 *
 *     // Create a thread pool SkExecutor with a fixed thread count, by default the number of cores.
 *     static std::unique_ptr<SkExecutor> MakeFIFOThreadPool(int threads = 0,
 *                                                           bool allowBorrowing = true);
 *     static std::unique_ptr<SkExecutor> MakeLIFOThreadPool(int threads = 0,
 *                                                           bool allowBorrowing = true);
 *
 *     // A work list is the queue or stack to which work is added and removed. The above two
 *     // factory functions create an executor with only one list while the following two factories
 *     // can create executors with multiple work lists. Having multiple work lists allows for
 *     // prioritization with work being pulled from the lower indexed work lists first - with
 *     // work list '0' being the highest priority.
 *     static std::unique_ptr<SkExecutor> MakeMultiListFIFOThreadPool(int numWorkLists,
 *                                                                    int threads = 0,
 *                                                                    bool allowBorrowing = true);
 *     static std::unique_ptr<SkExecutor> MakeMultiListLIFOThreadPool(int numWorkLists,
 *                                                                    int threads = 0,
 *                                                                    bool allowBorrowing = true);
 *
 *     // There is always a default SkExecutor available by calling SkExecutor::GetDefault().
 *     static SkExecutor& GetDefault();
 *     static void SetDefault(SkExecutor*);  // Does not take ownership.  Not thread safe.
 *
 *     // Add work to execute.
 *     virtual void add(std::function<void(void)> fn, int /* workList */) { this->add(std::move(fn)); }
 *
 *     // deprecated
 *     virtual void add(std::function<void(void)>) = 0;
 *
 *     // Returns the number of discarded work units
 *     virtual int discardAllPendingWork() { return 0; }
 *
 *     // If it makes sense for this executor, use this thread to execute work for a little while.
 *     virtual void borrow() {}
 *
 * protected:
 *     SkExecutor() = default;
 *     SkExecutor(const SkExecutor&) = delete;
 *     SkExecutor& operator=(const SkExecutor&) = delete;
 * }
 * ```
 */
public abstract class SkExecutor public constructor() {
  /**
   * C++ original:
   * ```cpp
   * SkExecutor() = default
   * ```
   */
  public constructor(param0: SkExecutor) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void add(std::function<void(void)> fn, int /* workList */) { this->add(std::move(fn)); }
   * ```
   */
  public open fun add(fn: () -> Unit, param1: Int) {
    TODO("Implement add")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void add(std::function<void(void)>) = 0
   * ```
   */
  public abstract fun add(param0: () -> Unit)

  /**
   * C++ original:
   * ```cpp
   * virtual int discardAllPendingWork() { return 0; }
   * ```
   */
  public open fun discardAllPendingWork(): Int {
    TODO("Implement discardAllPendingWork")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void borrow() {}
   * ```
   */
  public open fun borrow() {
    TODO("Implement borrow")
  }

  /**
   * C++ original:
   * ```cpp
   * SkExecutor& operator=(const SkExecutor&) = delete
   * ```
   */
  protected fun assign(param0: SkExecutor) {
    TODO("Implement assign")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<SkExecutor> SkExecutor::MakeFIFOThreadPool(int threads, bool allowBorrowing) {
     *     using WorkList = std::deque<std::function<void(void)>>;
     *     return std::make_unique<SkThreadPool<WorkList>>(/* numWorkLists= */ 1,
     *                                                     threads > 0 ? threads : num_cores(),
     *                                                     allowBorrowing);
     * }
     * ```
     */
    public fun makeFIFOThreadPool(threads: Int = TODO(), allowBorrowing: Boolean = TODO()): SkExecutor? {
      TODO("Implement makeFIFOThreadPool")
    }

    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<SkExecutor> SkExecutor::MakeLIFOThreadPool(int threads, bool allowBorrowing) {
     *     using WorkList = TArray<std::function<void(void)>>;
     *     return std::make_unique<SkThreadPool<WorkList>>(/* numWorkLists= */ 1,
     *                                                     threads > 0 ? threads : num_cores(),
     *                                                     allowBorrowing);
     * }
     * ```
     */
    public fun makeLIFOThreadPool(threads: Int = TODO(), allowBorrowing: Boolean = TODO()): SkExecutor? {
      TODO("Implement makeLIFOThreadPool")
    }

    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<SkExecutor> SkExecutor::MakeMultiListFIFOThreadPool(int numWorkLists,
     *                                                                     int threads,
     *                                                                     bool allowBorrowing) {
     *     using WorkList = std::deque<std::function<void(void)>>;
     *     return std::make_unique<SkThreadPool<WorkList>>(numWorkLists,
     *                                                     threads > 0 ? threads : num_cores(),
     *                                                     allowBorrowing);
     * }
     * ```
     */
    public fun makeMultiListFIFOThreadPool(
      numWorkLists: Int,
      threads: Int = TODO(),
      allowBorrowing: Boolean = TODO(),
    ): SkExecutor? {
      TODO("Implement makeMultiListFIFOThreadPool")
    }

    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<SkExecutor> SkExecutor::MakeMultiListLIFOThreadPool(int numWorkLists,
     *                                                                     int threads,
     *                                                                     bool allowBorrowing) {
     *     using WorkList = TArray<std::function<void(void)>>;
     *     return std::make_unique<SkThreadPool<WorkList>>(numWorkLists,
     *                                                     threads > 0 ? threads : num_cores(),
     *                                                     allowBorrowing);
     * }
     * ```
     */
    public fun makeMultiListLIFOThreadPool(
      numWorkLists: Int,
      threads: Int = TODO(),
      allowBorrowing: Boolean = TODO(),
    ): SkExecutor? {
      TODO("Implement makeMultiListLIFOThreadPool")
    }

    /**
     * C++ original:
     * ```cpp
     * SkExecutor& SkExecutor::GetDefault() {
     *     if (gDefaultExecutor) {
     *         return *gDefaultExecutor;
     *     }
     *     return trivial_executor();
     * }
     * ```
     */
    public fun getDefault(): SkExecutor {
      TODO("Implement getDefault")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkExecutor::SetDefault(SkExecutor* executor) {
     *     gDefaultExecutor = executor;
     * }
     * ```
     */
    public fun setDefault(executor: SkExecutor?) {
      TODO("Implement setDefault")
    }
  }
}
