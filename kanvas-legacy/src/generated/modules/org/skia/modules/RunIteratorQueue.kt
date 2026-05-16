package org.skia.modules

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.ULong

/**
 * C++ original:
 * ```cpp
 * class RunIteratorQueue {
 * public:
 *     void insert(SkShaper::RunIterator* runIterator, int priority) {
 *         fEntries.insert({runIterator, priority});
 *     }
 *
 *     bool advanceRuns() {
 *         const SkShaper::RunIterator* leastRun = fEntries.peek().runIterator;
 *         if (leastRun->atEnd()) {
 *             SkASSERT(this->allRunsAreAtEnd());
 *             return false;
 *         }
 *         const size_t leastEnd = leastRun->endOfCurrentRun();
 *         SkShaper::RunIterator* currentRun = nullptr;
 *         SkDEBUGCODE(size_t previousEndOfCurrentRun);
 *         while ((currentRun = fEntries.peek().runIterator)->endOfCurrentRun() <= leastEnd) {
 *             int priority = fEntries.peek().priority;
 *             fEntries.pop();
 *             SkDEBUGCODE(previousEndOfCurrentRun = currentRun->endOfCurrentRun());
 *             currentRun->consume();
 *             SkASSERT(previousEndOfCurrentRun < currentRun->endOfCurrentRun());
 *             fEntries.insert({currentRun, priority});
 *         }
 *         return true;
 *     }
 *
 *     size_t endOfCurrentRun() const {
 *         return fEntries.peek().runIterator->endOfCurrentRun();
 *     }
 *
 * private:
 *     bool allRunsAreAtEnd() const {
 *         for (int i = 0; i < fEntries.count(); ++i) {
 *             if (!fEntries.at(i).runIterator->atEnd()) {
 *                 return false;
 *             }
 *         }
 *         return true;
 *     }
 *
 *     struct Entry {
 *         SkShaper::RunIterator* runIterator;
 *         int priority;
 *     };
 *     static bool CompareEntry(Entry const& a, Entry const& b) {
 *         size_t aEnd = a.runIterator->endOfCurrentRun();
 *         size_t bEnd = b.runIterator->endOfCurrentRun();
 *         return aEnd  < bEnd || (aEnd == bEnd && a.priority < b.priority);
 *     }
 *     SkTDPQueue<Entry, CompareEntry> fEntries;
 * }
 * ```
 */
public data class RunIteratorQueue public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkTDPQueue<Entry, CompareEntry> fEntries
   * ```
   */
  private var fEntries: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * void insert(SkShaper::RunIterator* runIterator, int priority) {
   *         fEntries.insert({runIterator, priority});
   *     }
   * ```
   */
  public fun insert(runIterator: SkShaper.RunIterator?, priority: Int) {
    TODO("Implement insert")
  }

  /**
   * C++ original:
   * ```cpp
   * bool advanceRuns() {
   *         const SkShaper::RunIterator* leastRun = fEntries.peek().runIterator;
   *         if (leastRun->atEnd()) {
   *             SkASSERT(this->allRunsAreAtEnd());
   *             return false;
   *         }
   *         const size_t leastEnd = leastRun->endOfCurrentRun();
   *         SkShaper::RunIterator* currentRun = nullptr;
   *         SkDEBUGCODE(size_t previousEndOfCurrentRun);
   *         while ((currentRun = fEntries.peek().runIterator)->endOfCurrentRun() <= leastEnd) {
   *             int priority = fEntries.peek().priority;
   *             fEntries.pop();
   *             SkDEBUGCODE(previousEndOfCurrentRun = currentRun->endOfCurrentRun());
   *             currentRun->consume();
   *             SkASSERT(previousEndOfCurrentRun < currentRun->endOfCurrentRun());
   *             fEntries.insert({currentRun, priority});
   *         }
   *         return true;
   *     }
   * ```
   */
  public fun advanceRuns(): Boolean {
    TODO("Implement advanceRuns")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t endOfCurrentRun() const {
   *         return fEntries.peek().runIterator->endOfCurrentRun();
   *     }
   * ```
   */
  public fun endOfCurrentRun(): ULong {
    TODO("Implement endOfCurrentRun")
  }

  /**
   * C++ original:
   * ```cpp
   * bool allRunsAreAtEnd() const {
   *         for (int i = 0; i < fEntries.count(); ++i) {
   *             if (!fEntries.at(i).runIterator->atEnd()) {
   *                 return false;
   *             }
   *         }
   *         return true;
   *     }
   * ```
   */
  private fun allRunsAreAtEnd(): Boolean {
    TODO("Implement allRunsAreAtEnd")
  }

  public open class Entry public constructor(
    public var runIterator: SkShaper.RunIterator?,
    public var priority: Int,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static bool CompareEntry(Entry const& a, Entry const& b) {
     *         size_t aEnd = a.runIterator->endOfCurrentRun();
     *         size_t bEnd = b.runIterator->endOfCurrentRun();
     *         return aEnd  < bEnd || (aEnd == bEnd && a.priority < b.priority);
     *     }
     * ```
     */
    private fun compareEntry(a: Any, b: Any): Boolean {
      TODO("Implement compareEntry")
    }
  }
}
