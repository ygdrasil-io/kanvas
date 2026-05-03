package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.Unit
import org.skia.foundation.SkRefCnt
import org.skia.memory.SkTDArray

/**
 * C++ original:
 * ```cpp
 * class SkPtrSet : public SkRefCnt {
 * public:
 *
 *
 *     /**
 *      *  Search for the specified ptr in the set. If it is found, return its
 *      *  32bit ID [1..N], or if not found, return 0. Always returns 0 for nullptr.
 *      */
 *     uint32_t find(void*) const;
 *
 *     /**
 *      *  Add the specified ptr to the set, returning a unique 32bit ID for it
 *      *  [1...N]. Duplicate ptrs will return the same ID.
 *      *
 *      *  If the ptr is nullptr, it is not added, and 0 is returned.
 *      */
 *     uint32_t add(void*);
 *
 *     /**
 *      *  Return the number of (non-null) ptrs in the set.
 *      */
 *     int count() const { return fList.size(); }
 *
 *     /**
 *      *  Copy the ptrs in the set into the specified array (allocated by the
 *      *  caller). The ptrs are assgined to the array based on their corresponding
 *      *  ID. e.g. array[ptr.ID - 1] = ptr.
 *      *
 *      *  incPtr() and decPtr() are not called during this operation.
 *      */
 *     void copyToArray(void* array[]) const;
 *
 *     /**
 *      *  Call decPtr() on each ptr in the set, and the reset the size of the set
 *      *  to 0.
 *      */
 *     void reset();
 *
 *     /**
 *      * Set iterator.
 *      */
 *     class Iter {
 *     public:
 *         Iter(const SkPtrSet& set)
 *             : fSet(set)
 *             , fIndex(0) {}
 *
 *         /**
 *          * Return the next ptr in the set or null if the end was reached.
 *          */
 *         void* next() {
 *             return fIndex < fSet.fList.size() ? fSet.fList[fIndex++].fPtr : nullptr;
 *         }
 *
 *     private:
 *         const SkPtrSet& fSet;
 *         int             fIndex;
 *     };
 *
 * protected:
 *     virtual void incPtr(void*) {}
 *     virtual void decPtr(void*) {}
 *
 * private:
 *     struct Pair {
 *         void*       fPtr;   // never nullptr
 *         uint32_t    fIndex; // 1...N
 *     };
 *
 *     // we store the ptrs in sorted-order (using Cmp) so that we can efficiently
 *     // detect duplicates when add() is called. Hence we need to store the
 *     // ptr and its ID/fIndex explicitly, since the ptr's position in the array
 *     // is not related to its "index".
 *     SkTDArray<Pair>  fList;
 *
 *     static bool Less(const Pair& a, const Pair& b);
 *
 *     using INHERITED = SkRefCnt;
 * }
 * ```
 */
public open class SkPtrSet : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * SkTDArray<Pair>  fList
   * ```
   */
  private var fList: SkTDArray<org.skia.tests.Pair> = TODO("Initialize fList")

  /**
   * C++ original:
   * ```cpp
   * uint32_t SkPtrSet::find(void* ptr) const {
   *     if (nullptr == ptr) {
   *         return 0;
   *     }
   *
   *     int count = fList.size();
   *     Pair pair;
   *     pair.fPtr = ptr;
   *
   *     int index = SkTSearch<Pair, Less>(fList.begin(), count, pair, sizeof(pair));
   *     if (index < 0) {
   *         return 0;
   *     }
   *     return fList[index].fIndex;
   * }
   * ```
   */
  public fun find(ptr: Unit?): Int {
    TODO("Implement find")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t SkPtrSet::add(void* ptr) {
   *     if (nullptr == ptr) {
   *         return 0;
   *     }
   *
   *     int count = fList.size();
   *     Pair pair;
   *     pair.fPtr = ptr;
   *
   *     int index = SkTSearch<Pair, Less>(fList.begin(), count, pair, sizeof(pair));
   *     if (index < 0) {
   *         index = ~index; // turn it back into an index for insertion
   *         this->incPtr(ptr);
   *         pair.fIndex = count + 1;
   *         *fList.insert(index) = pair;
   *         return count + 1;
   *     } else {
   *         return fList[index].fIndex;
   *     }
   * }
   * ```
   */
  public fun add(ptr: Unit?): Int {
    TODO("Implement add")
  }

  /**
   * C++ original:
   * ```cpp
   * int count() const { return fList.size(); }
   * ```
   */
  public fun count(): Int {
    TODO("Implement count")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPtrSet::copyToArray(void* array[]) const {
   *     int count = fList.size();
   *     if (count > 0) {
   *         SkASSERT(array);
   *         const Pair* p = fList.begin();
   *         // p->fIndex is base-1, so we need to subtract to find its slot
   *         for (int i = 0; i < count; i++) {
   *             int index = p[i].fIndex - 1;
   *             SkASSERT((unsigned)index < (unsigned)count);
   *             array[index] = p[i].fPtr;
   *         }
   *     }
   * }
   * ```
   */
  public fun copyToArray(array: Int) {
    TODO("Implement copyToArray")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPtrSet::reset() {
   *     Pair* p = fList.begin();
   *     Pair* stop = fList.end();
   *     while (p < stop) {
   *         this->decPtr(p->fPtr);
   *         p += 1;
   *     }
   *     fList.reset();
   * }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void incPtr(void*) {}
   * ```
   */
  protected open fun incPtr(param0: Unit?) {
    TODO("Implement incPtr")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void decPtr(void*) {}
   * ```
   */
  protected open fun decPtr(param0: Unit?) {
    TODO("Implement decPtr")
  }

  public open class Iter public constructor(
    `set`: SkPtrSet,
  ) {
    private val fSet: SkPtrSet = TODO("Initialize fSet")

    private var fIndex: Int = TODO("Initialize fIndex")

    public fun next() {
      TODO("Implement next")
    }
  }

  public data class Pair public constructor(
    public var fPtr: Unit?,
    public var fIndex: Int,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * bool SkPtrSet::Less(const Pair& a, const Pair& b) {
     *     return (char*)a.fPtr < (char*)b.fPtr;
     * }
     * ```
     */
    private fun less(a: Pair, b: Pair): Boolean {
      TODO("Implement less")
    }
  }
}

public typealias SkTPtrSetINHERITED = SkPtrSet
