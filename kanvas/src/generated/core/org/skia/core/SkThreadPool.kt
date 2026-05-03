package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.Unit

/**
 * C++ original:
 * ```cpp
 * template <typename WorkList>
 * class SkThreadPool final : public SkExecutor {
 * public:
 *     explicit SkThreadPool(int numWorkLists, int threads, bool allowBorrowing)
 *             : fNumWorkLists(numWorkLists < 1 ? 1 : numWorkLists)
 *             , fAllowBorrowing(allowBorrowing) {
 *
 *         fWorkLists = std::make_unique<WorkList[]>(fNumWorkLists);
 *
 *         for (int i = 0; i < threads; i++) {
 *             fThreads.emplace_back(&Loop, this);
 *         }
 *     }
 *
 *     ~SkThreadPool() override {
 *         // Signal each thread that it's time to shut down.
 *         for (int i = 0; i < fThreads.size(); i++) {
 *             // Add the notification to the highest priority list
 *             this->add(nullptr, /* workList= */ 0);
 *         }
 *         // Wait for each thread to shut down.
 *         for (int i = 0; i < fThreads.size(); i++) {
 *             fThreads[i].join();
 *         }
 *     }
 *
 *     void add(std::function<void(void)> work, int workList) override {
 *         workList = SkTPin(workList, 0, fNumWorkLists-1);
 *
 *         // Add some work to our pile of work to do.
 *         {
 *             SkAutoMutexExclusive lock(fWorkLock);
 *
 *             fWorkLists[workList].emplace_back(std::move(work));
 *         }
 *         // Tell the Loop() threads to pick it up.
 *         fWorkAvailable.signal(1);
 *     }
 *
 *     void add(std::function<void(void)> work) override {
 *         this->add(std::move(work), /* workList= */ 0);
 *     }
 *
 *     int discardAllPendingWork() override {
 *         SkAutoMutexExclusive lock(fWorkLock);
 *
 *         int numDiscarded = 0;
 *         for (int i = 0; i < fNumWorkLists; ++i) {
 *             numDiscarded += fWorkLists[i].size();
 *             fWorkLists[i].clear();
 *         }
 *
 *         return numDiscarded;
 *     }
 *
 *     void borrow() override {
 *         // If there is work waiting and we're allowed to borrow work, do it.
 *         if (fAllowBorrowing && fWorkAvailable.try_wait()) {
 *             SkAssertResult(this->do_work());
 *         }
 *     }
 *
 * private:
 *     // This method should usually be called only when fWorkAvailable indicates there's work to do.
 *     bool do_work() {
 *         std::function<void(void)> work;
 *         bool workAvailable = false;
 *         {
 *             SkAutoMutexExclusive lock(fWorkLock);
 *
 *             for (int i = 0; i < fNumWorkLists; ++i) {
 *                 if (!fWorkLists[i].empty()) {
 *                     workAvailable = true;
 *                     work = pop(&fWorkLists[i]);
 *                     break;
 *                 }
 *             }
 *         }
 *
 *         if (!workAvailable) {
 *             // Because we can discard work asynchronous to Loop() we can sometimes get in this
 *             // method with no work to do
 *             return true;
 *         }
 *
 *         if (!work) {
 *             return false;  // This is Loop()'s signal to shut down.
 *         }
 *
 *         work();
 *         return true;
 *     }
 *
 *     static void Loop(void* ctx) {
 *         auto pool = (SkThreadPool*)ctx;
 *         do {
 *             pool->fWorkAvailable.wait();
 *         } while (pool->do_work());
 *     }
 *
 *     // Both SkMutex and SkSpinlock can work here.
 *     using Lock = SkMutex;
 *
 *     TArray<std::thread>         fThreads;
 *     const int                   fNumWorkLists; // guaranteed >= 1
 *     std::unique_ptr<WorkList[]> fWorkLists SK_GUARDED_BY(fWorkLock);
 *     Lock                        fWorkLock;
 *     SkSemaphore                 fWorkAvailable;
 *     const bool                  fAllowBorrowing;
 * }
 * ```
 */
public open class SkThreadPool<WorkList> public constructor(
  numWorkLists: Int,
  threads: Int,
  allowBorrowing: Boolean,
) : SkExecutor() {
  /**
   * C++ original:
   * ```cpp
   * TArray<std::thread>         fThreads
   * ```
   */
  private var fThreads: Int = TODO("Initialize fThreads")

  /**
   * C++ original:
   * ```cpp
   * const int                   fNumWorkLists
   * ```
   */
  private val fNumWorkLists: Int = TODO("Initialize fNumWorkLists")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<WorkList[]> fWorkLists
   * ```
   */
  private var fWorkLists: Int = TODO("Initialize fWorkLists")

  /**
   * C++ original:
   * ```cpp
   * Lock                        fWorkLock
   * ```
   */
  private var fWorkLock: SkThreadPoolLock = TODO("Initialize fWorkLock")

  /**
   * C++ original:
   * ```cpp
   * SkSemaphore                 fWorkAvailable
   * ```
   */
  private var fWorkAvailable: SkSemaphore = TODO("Initialize fWorkAvailable")

  /**
   * C++ original:
   * ```cpp
   * const bool                  fAllowBorrowing
   * ```
   */
  private val fAllowBorrowing: Boolean = TODO("Initialize fAllowBorrowing")

  /**
   * C++ original:
   * ```cpp
   * void add(std::function<void(void)> work, int workList) override {
   *         workList = SkTPin(workList, 0, fNumWorkLists-1);
   *
   *         // Add some work to our pile of work to do.
   *         {
   *             SkAutoMutexExclusive lock(fWorkLock);
   *
   *             fWorkLists[workList].emplace_back(std::move(work));
   *         }
   *         // Tell the Loop() threads to pick it up.
   *         fWorkAvailable.signal(1);
   *     }
   * ```
   */
  public override fun add(work: () -> Unit, workList: Int) {
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
   * int discardAllPendingWork() override {
   *         SkAutoMutexExclusive lock(fWorkLock);
   *
   *         int numDiscarded = 0;
   *         for (int i = 0; i < fNumWorkLists; ++i) {
   *             numDiscarded += fWorkLists[i].size();
   *             fWorkLists[i].clear();
   *         }
   *
   *         return numDiscarded;
   *     }
   * ```
   */
  public override fun discardAllPendingWork(): Int {
    TODO("Implement discardAllPendingWork")
  }

  /**
   * C++ original:
   * ```cpp
   * void borrow() override {
   *         // If there is work waiting and we're allowed to borrow work, do it.
   *         if (fAllowBorrowing && fWorkAvailable.try_wait()) {
   *             SkAssertResult(this->do_work());
   *         }
   *     }
   * ```
   */
  public override fun borrow() {
    TODO("Implement borrow")
  }

  /**
   * C++ original:
   * ```cpp
   * bool do_work() {
   *         std::function<void(void)> work;
   *         bool workAvailable = false;
   *         {
   *             SkAutoMutexExclusive lock(fWorkLock);
   *
   *             for (int i = 0; i < fNumWorkLists; ++i) {
   *                 if (!fWorkLists[i].empty()) {
   *                     workAvailable = true;
   *                     work = pop(&fWorkLists[i]);
   *                     break;
   *                 }
   *             }
   *         }
   *
   *         if (!workAvailable) {
   *             // Because we can discard work asynchronous to Loop() we can sometimes get in this
   *             // method with no work to do
   *             return true;
   *         }
   *
   *         if (!work) {
   *             return false;  // This is Loop()'s signal to shut down.
   *         }
   *
   *         work();
   *         return true;
   *     }
   * ```
   */
  private fun doWork(): Boolean {
    TODO("Implement doWork")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static void Loop(void* ctx) {
     *         auto pool = (SkThreadPool*)ctx;
     *         do {
     *             pool->fWorkAvailable.wait();
     *         } while (pool->do_work());
     *     }
     * ```
     */
    private fun loop(ctx: Unit?) {
      TODO("Implement loop")
    }
  }
}
