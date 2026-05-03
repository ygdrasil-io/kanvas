package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.memory.SkTDArray

/**
 * C++ original:
 * ```cpp
 * class SkTDPQueue {
 * public:
 *     SkTDPQueue() {}
 *     SkTDPQueue(int reserve) { fArray.reserve(reserve); }
 *
 *     SkTDPQueue(SkTDPQueue&&) = default;
 *     SkTDPQueue& operator =(SkTDPQueue&&) = default;
 *
 *     SkTDPQueue(const SkTDPQueue&) = delete;
 *     SkTDPQueue& operator=(const SkTDPQueue&) = delete;
 *
 *     /** Number of items in the queue. */
 *     int count() const { return fArray.size(); }
 *
 *     /** Gets the next item in the queue without popping it. */
 *     const T& peek() const { return fArray[0]; }
 *     T& peek() { return fArray[0]; }
 *
 *     /** Removes the next item. */
 *     void pop() {
 *         this->validate();
 *         SkDEBUGCODE(if (SkToBool(INDEX)) { *INDEX(fArray[0]) = -1; })
 *         if (1 == fArray.size()) {
 *             fArray.pop_back();
 *             return;
 *         }
 *
 *         fArray[0] = fArray[fArray.size() - 1];
 *         this->setIndex(0);
 *         fArray.pop_back();
 *         this->percolateDownIfNecessary(0);
 *
 *         this->validate();
 *     }
 *
 *     /** Inserts a new item in the queue based on its priority. */
 *     void insert(T entry) {
 *         this->validate();
 *         int index = fArray.size();
 *         *fArray.append() = entry;
 *         this->setIndex(fArray.size() - 1);
 *         this->percolateUpIfNecessary(index);
 *         this->validate();
 *     }
 *
 *     /** Random access removal. This requires that the INDEX function is non-nullptr. */
 *     void remove(T entry) {
 *         SkASSERT(nullptr != INDEX);
 *         int index = *INDEX(entry);
 *         SkASSERT(index >= 0 && index < fArray.size());
 *         this->validate();
 *         SkDEBUGCODE(*INDEX(fArray[index]) = -1;)
 *         if (index == fArray.size() - 1) {
 *             fArray.pop_back();
 *             return;
 *         }
 *         fArray[index] = fArray[fArray.size() - 1];
 *         fArray.pop_back();
 *         this->setIndex(index);
 *         this->percolateUpOrDown(index);
 *         this->validate();
 *     }
 *
 *     /** Notification that the priority of an entry has changed. This must be called after an
 *         item's priority is changed to maintain correct ordering. Changing the priority is only
 *         allowed if an INDEX function is provided. */
 *     void priorityDidChange(T entry) {
 *         SkASSERT(nullptr != INDEX);
 *         int index = *INDEX(entry);
 *         SkASSERT(index >= 0 && index < fArray.size());
 *         this->validate(index);
 *         this->percolateUpOrDown(index);
 *         this->validate();
 *     }
 *
 *     /** Gets the item at index i in the priority queue (for i < this->count()). at(0) is equivalent
 *         to peek(). Otherwise, there is no guarantee about ordering of elements in the queue. */
 *     T at(int i) const { return fArray[i]; }
 *
 *     /** Sorts the queue into priority order.  The queue is only guarenteed to remain in sorted order
 *      *  until any other operation, other than at(), is performed.
 *      */
 *     void sort() {
 *         if (fArray.size() > 1) {
 *             SkTQSort<T>(fArray.begin(), fArray.end(), LESS);
 *             for (int i = 0; i < fArray.size(); i++) {
 *                 this->setIndex(i);
 *             }
 *             this->validate();
 *         }
 *     }
 *
 * private:
 *     static int LeftOf(int x) { SkASSERT(x >= 0); return 2 * x + 1; }
 *     static int ParentOf(int x) { SkASSERT(x > 0); return (x - 1) >> 1; }
 *
 *     void percolateUpOrDown(int index) {
 *         SkASSERT(index >= 0);
 *         if (!percolateUpIfNecessary(index)) {
 *             this->validate(index);
 *             this->percolateDownIfNecessary(index);
 *         }
 *     }
 *
 *     bool percolateUpIfNecessary(int index) {
 *         SkASSERT(index >= 0);
 *         bool percolated = false;
 *         do {
 *             if (0 == index) {
 *                 this->setIndex(index);
 *                 return percolated;
 *             }
 *             int p = ParentOf(index);
 *             if (LESS(fArray[index], fArray[p])) {
 *                 using std::swap;
 *                 swap(fArray[index], fArray[p]);
 *                 this->setIndex(index);
 *                 index = p;
 *                 percolated = true;
 *             } else {
 *                 this->setIndex(index);
 *                 return percolated;
 *             }
 *             this->validate(index);
 *         } while (true);
 *     }
 *
 *     void percolateDownIfNecessary(int index) {
 *         SkASSERT(index >= 0);
 *         do {
 *             int child = LeftOf(index);
 *
 *             if (child >= fArray.size()) {
 *                 // We're a leaf.
 *                 this->setIndex(index);
 *                 return;
 *             }
 *
 *             if (child + 1 >= fArray.size()) {
 *                 // We only have a left child.
 *                 if (LESS(fArray[child], fArray[index])) {
 *                     using std::swap;
 *                     swap(fArray[child], fArray[index]);
 *                     this->setIndex(child);
 *                     this->setIndex(index);
 *                     return;
 *                 }
 *             } else if (LESS(fArray[child + 1], fArray[child])) {
 *                 // The right child is the one we should swap with, if we swap.
 *                 child++;
 *             }
 *
 *             // Check if we need to swap.
 *             if (LESS(fArray[child], fArray[index])) {
 *                 using std::swap;
 *                 swap(fArray[child], fArray[index]);
 *                 this->setIndex(index);
 *                 index = child;
 *             } else {
 *                 // We're less than both our children.
 *                 this->setIndex(index);
 *                 return;
 *             }
 *             this->validate(index);
 *         } while (true);
 *     }
 *
 *     void setIndex(int index) {
 *         SkASSERT(index < fArray.size());
 *         if (SkToBool(INDEX)) {
 *             *INDEX(fArray[index]) = index;
 *         }
 *     }
 *
 *     void validate(int excludedIndex = -1) const {
 * #ifdef SK_DEBUG
 *         for (int i = 1; i < fArray.size(); ++i) {
 *             int p = ParentOf(i);
 *             if (excludedIndex != p && excludedIndex != i) {
 *                 SkASSERT(!(LESS(fArray[i], fArray[p])));
 *                 SkASSERT(!SkToBool(INDEX) || *INDEX(fArray[i]) == i);
 *             }
 *         }
 * #endif
 *     }
 *
 *     SkTDArray<T> fArray;
 * }
 * ```
 */
public data class SkTDPQueue<T> public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkTDArray<T> fArray
   * ```
   */
  private var fArray: SkTDArray<T>,
) {
  /**
   * C++ original:
   * ```cpp
   * SkTDPQueue& operator =(SkTDPQueue&&) = default
   * ```
   */
  public fun assign(param0: SkTDPQueue) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTDPQueue& operator=(const SkTDPQueue&) = delete
   * ```
   */
  public fun count(): Int {
    TODO("Implement count")
  }

  /**
   * C++ original:
   * ```cpp
   * int count() const { return fArray.size(); }
   * ```
   */
  public fun peek(): T {
    TODO("Implement peek")
  }

  /**
   * C++ original:
   * ```cpp
   * const T& peek() const { return fArray[0]; }
   * ```
   */
  public fun pop() {
    TODO("Implement pop")
  }

  /**
   * C++ original:
   * ```cpp
   * T& peek() { return fArray[0]; }
   * ```
   */
  public fun insert(entry: T) {
    TODO("Implement insert")
  }

  /**
   * C++ original:
   * ```cpp
   * void pop() {
   *         this->validate();
   *         SkDEBUGCODE(if (SkToBool(INDEX)) { *INDEX(fArray[0]) = -1; })
   *         if (1 == fArray.size()) {
   *             fArray.pop_back();
   *             return;
   *         }
   *
   *         fArray[0] = fArray[fArray.size() - 1];
   *         this->setIndex(0);
   *         fArray.pop_back();
   *         this->percolateDownIfNecessary(0);
   *
   *         this->validate();
   *     }
   * ```
   */
  public fun remove(entry: T) {
    TODO("Implement remove")
  }

  /**
   * C++ original:
   * ```cpp
   * void insert(T entry) {
   *         this->validate();
   *         int index = fArray.size();
   *         *fArray.append() = entry;
   *         this->setIndex(fArray.size() - 1);
   *         this->percolateUpIfNecessary(index);
   *         this->validate();
   *     }
   * ```
   */
  public fun priorityDidChange(entry: T) {
    TODO("Implement priorityDidChange")
  }

  /**
   * C++ original:
   * ```cpp
   * void remove(T entry) {
   *         SkASSERT(nullptr != INDEX);
   *         int index = *INDEX(entry);
   *         SkASSERT(index >= 0 && index < fArray.size());
   *         this->validate();
   *         SkDEBUGCODE(*INDEX(fArray[index]) = -1;)
   *         if (index == fArray.size() - 1) {
   *             fArray.pop_back();
   *             return;
   *         }
   *         fArray[index] = fArray[fArray.size() - 1];
   *         fArray.pop_back();
   *         this->setIndex(index);
   *         this->percolateUpOrDown(index);
   *         this->validate();
   *     }
   * ```
   */
  public fun at(i: Int): T {
    TODO("Implement at")
  }

  /**
   * C++ original:
   * ```cpp
   * void priorityDidChange(T entry) {
   *         SkASSERT(nullptr != INDEX);
   *         int index = *INDEX(entry);
   *         SkASSERT(index >= 0 && index < fArray.size());
   *         this->validate(index);
   *         this->percolateUpOrDown(index);
   *         this->validate();
   *     }
   * ```
   */
  public fun sort() {
    TODO("Implement sort")
  }

  /**
   * C++ original:
   * ```cpp
   * T at(int i) const { return fArray[i]; }
   * ```
   */
  private fun percolateUpOrDown(index: Int) {
    TODO("Implement percolateUpOrDown")
  }

  /**
   * C++ original:
   * ```cpp
   * void sort() {
   *         if (fArray.size() > 1) {
   *             SkTQSort<T>(fArray.begin(), fArray.end(), LESS);
   *             for (int i = 0; i < fArray.size(); i++) {
   *                 this->setIndex(i);
   *             }
   *             this->validate();
   *         }
   *     }
   * ```
   */
  private fun percolateUpIfNecessary(index: Int): Boolean {
    TODO("Implement percolateUpIfNecessary")
  }

  /**
   * C++ original:
   * ```cpp
   * void percolateUpOrDown(int index) {
   *         SkASSERT(index >= 0);
   *         if (!percolateUpIfNecessary(index)) {
   *             this->validate(index);
   *             this->percolateDownIfNecessary(index);
   *         }
   *     }
   * ```
   */
  private fun percolateDownIfNecessary(index: Int) {
    TODO("Implement percolateDownIfNecessary")
  }

  /**
   * C++ original:
   * ```cpp
   * bool percolateUpIfNecessary(int index) {
   *         SkASSERT(index >= 0);
   *         bool percolated = false;
   *         do {
   *             if (0 == index) {
   *                 this->setIndex(index);
   *                 return percolated;
   *             }
   *             int p = ParentOf(index);
   *             if (LESS(fArray[index], fArray[p])) {
   *                 using std::swap;
   *                 swap(fArray[index], fArray[p]);
   *                 this->setIndex(index);
   *                 index = p;
   *                 percolated = true;
   *             } else {
   *                 this->setIndex(index);
   *                 return percolated;
   *             }
   *             this->validate(index);
   *         } while (true);
   *     }
   * ```
   */
  private fun setIndex(index: Int) {
    TODO("Implement setIndex")
  }

  /**
   * C++ original:
   * ```cpp
   * void percolateDownIfNecessary(int index) {
   *         SkASSERT(index >= 0);
   *         do {
   *             int child = LeftOf(index);
   *
   *             if (child >= fArray.size()) {
   *                 // We're a leaf.
   *                 this->setIndex(index);
   *                 return;
   *             }
   *
   *             if (child + 1 >= fArray.size()) {
   *                 // We only have a left child.
   *                 if (LESS(fArray[child], fArray[index])) {
   *                     using std::swap;
   *                     swap(fArray[child], fArray[index]);
   *                     this->setIndex(child);
   *                     this->setIndex(index);
   *                     return;
   *                 }
   *             } else if (LESS(fArray[child + 1], fArray[child])) {
   *                 // The right child is the one we should swap with, if we swap.
   *                 child++;
   *             }
   *
   *             // Check if we need to swap.
   *             if (LESS(fArray[child], fArray[index])) {
   *                 using std::swap;
   *                 swap(fArray[child], fArray[index]);
   *                 this->setIndex(index);
   *                 index = child;
   *             } else {
   *                 // We're less than both our children.
   *                 this->setIndex(index);
   *                 return;
   *             }
   *             this->validate(index);
   *         } while (true);
   *     }
   * ```
   */
  private fun validate(excludedIndex: Int = TODO()) {
    TODO("Implement validate")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static int LeftOf(int x) { SkASSERT(x >= 0); return 2 * x + 1; }
     * ```
     */
    private fun leftOf(x: Int): Int {
      TODO("Implement leftOf")
    }

    /**
     * C++ original:
     * ```cpp
     * static int ParentOf(int x) { SkASSERT(x > 0); return (x - 1) >> 1; }
     * ```
     */
    private fun parentOf(x: Int): Int {
      TODO("Implement parentOf")
    }
  }
}
