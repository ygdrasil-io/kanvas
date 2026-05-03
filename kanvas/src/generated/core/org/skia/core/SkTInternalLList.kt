package org.skia.core

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SkTInternalLList {
 * public:
 *     SkTInternalLList() {}
 *
 *     void reset() {
 *         fHead = nullptr;
 *         fTail = nullptr;
 *     }
 *
 *     void remove(T* entry) {
 *         SkASSERT(fHead && fTail);
 *         SkASSERT(this->isInList(entry));
 *
 *         T* prev = entry->fPrev;
 *         T* next = entry->fNext;
 *
 *         if (prev) {
 *             prev->fNext = next;
 *         } else {
 *             fHead = next;
 *         }
 *         if (next) {
 *             next->fPrev = prev;
 *         } else {
 *             fTail = prev;
 *         }
 *
 *         entry->fPrev = nullptr;
 *         entry->fNext = nullptr;
 *
 * #ifdef SK_DEBUG
 *         entry->fList = nullptr;
 * #endif
 *     }
 *
 *     void addToHead(T* entry) {
 *         SkASSERT(nullptr == entry->fPrev && nullptr == entry->fNext);
 *         SkASSERT(nullptr == entry->fList);
 *
 *         entry->fPrev = nullptr;
 *         entry->fNext = fHead;
 *         if (fHead) {
 *             fHead->fPrev = entry;
 *         }
 *         fHead = entry;
 *         if (nullptr == fTail) {
 *             fTail = entry;
 *         }
 *
 * #ifdef SK_DEBUG
 *         entry->fList = this;
 * #endif
 *     }
 *
 *     void addToTail(T* entry) {
 *         SkASSERT(nullptr == entry->fPrev && nullptr == entry->fNext);
 *         SkASSERT(nullptr == entry->fList);
 *
 *         entry->fPrev = fTail;
 *         entry->fNext = nullptr;
 *         if (fTail) {
 *             fTail->fNext = entry;
 *         }
 *         fTail = entry;
 *         if (nullptr == fHead) {
 *             fHead = entry;
 *         }
 *
 * #ifdef SK_DEBUG
 *         entry->fList = this;
 * #endif
 *     }
 *
 *     /**
 *      * Inserts a new list entry before an existing list entry. The new entry must not already be
 *      * a member of this or any other list. If existingEntry is NULL then the new entry is added
 *      * at the tail.
 *      */
 *     void addBefore(T* newEntry, T* existingEntry) {
 *         SkASSERT(newEntry);
 *
 *         if (nullptr == existingEntry) {
 *             this->addToTail(newEntry);
 *             return;
 *         }
 *
 *         SkASSERT(this->isInList(existingEntry));
 *         newEntry->fNext = existingEntry;
 *         T* prev = existingEntry->fPrev;
 *         existingEntry->fPrev = newEntry;
 *         newEntry->fPrev = prev;
 *         if (nullptr == prev) {
 *             SkASSERT(fHead == existingEntry);
 *             fHead = newEntry;
 *         } else {
 *             prev->fNext = newEntry;
 *         }
 * #ifdef SK_DEBUG
 *         newEntry->fList = this;
 * #endif
 *     }
 *
 *     /**
 *      * Inserts a new list entry after an existing list entry. The new entry must not already be
 *      * a member of this or any other list. If existingEntry is NULL then the new entry is added
 *      * at the head.
 *      */
 *     void addAfter(T* newEntry, T* existingEntry) {
 *         SkASSERT(newEntry);
 *
 *         if (nullptr == existingEntry) {
 *             this->addToHead(newEntry);
 *             return;
 *         }
 *
 *         SkASSERT(this->isInList(existingEntry));
 *         newEntry->fPrev = existingEntry;
 *         T* next = existingEntry->fNext;
 *         existingEntry->fNext = newEntry;
 *         newEntry->fNext = next;
 *         if (nullptr == next) {
 *             SkASSERT(fTail == existingEntry);
 *             fTail = newEntry;
 *         } else {
 *             next->fPrev = newEntry;
 *         }
 * #ifdef SK_DEBUG
 *         newEntry->fList = this;
 * #endif
 *     }
 *
 *     void concat(SkTInternalLList&& list) {
 *         if (list.isEmpty()) {
 *             return;
 *         }
 *
 *         list.fHead->fPrev = fTail;
 *         if (!fHead) {
 *             SkASSERT(!list.fHead->fPrev);
 *             fHead = list.fHead;
 *         } else {
 *             SkASSERT(fTail);
 *             fTail->fNext = list.fHead;
 *         }
 *         fTail = list.fTail;
 *
 * #ifdef SK_DEBUG
 *         for (T* node = list.fHead; node; node = node->fNext) {
 *             SkASSERT(node->fList == &list);
 *             node->fList = this;
 *         }
 * #endif
 *
 *         list.fHead = list.fTail = nullptr;
 *     }
 *
 *     bool isEmpty() const {
 *         SkASSERT(SkToBool(fHead) == SkToBool(fTail));
 *         return !fHead;
 *     }
 *
 *     T* head() const { return fHead; }
 *     T* tail() const { return fTail; }
 *
 *     class Iter {
 *     public:
 *         enum IterStart {
 *             kHead_IterStart,
 *             kTail_IterStart
 *         };
 *
 *         Iter() : fCurr(nullptr) {}
 *         Iter(const Iter& iter) : fCurr(iter.fCurr) {}
 *         Iter& operator= (const Iter& iter) { fCurr = iter.fCurr; return *this; }
 *
 *         T* init(const SkTInternalLList& list, IterStart startLoc) {
 *             if (kHead_IterStart == startLoc) {
 *                 fCurr = list.fHead;
 *             } else {
 *                 SkASSERT(kTail_IterStart == startLoc);
 *                 fCurr = list.fTail;
 *             }
 *
 *             return fCurr;
 *         }
 *
 *         T* get() { return fCurr; }
 *
 *         /**
 *          * Return the next/previous element in the list or NULL if at the end.
 *          */
 *         T* next() {
 *             if (nullptr == fCurr) {
 *                 return nullptr;
 *             }
 *
 *             fCurr = fCurr->fNext;
 *             return fCurr;
 *         }
 *
 *         T* prev() {
 *             if (nullptr == fCurr) {
 *                 return nullptr;
 *             }
 *
 *             fCurr = fCurr->fPrev;
 *             return fCurr;
 *         }
 *
 *         /**
 *          * C++11 range-for interface.
 *          */
 *         bool operator!=(const Iter& that) const { return fCurr != that.fCurr; }
 *         T* operator*() { return this->get(); }
 *         void operator++() { this->next(); }
 *
 *     private:
 *         T* fCurr;
 *     };
 *
 *     Iter begin() const {
 *         Iter iter;
 *         iter.init(*this, Iter::kHead_IterStart);
 *         return iter;
 *     }
 *
 *     Iter end() const { return Iter(); }
 *
 * #ifdef SK_DEBUG
 *     void validate() const {
 *         SkASSERT(!fHead == !fTail);
 *         Iter iter;
 *         for (T* item = iter.init(*this, Iter::kHead_IterStart); item; item = iter.next()) {
 *             SkASSERT(this->isInList(item));
 *             if (nullptr == item->fPrev) {
 *                 SkASSERT(fHead == item);
 *             } else {
 *                 SkASSERT(item->fPrev->fNext == item);
 *             }
 *             if (nullptr == item->fNext) {
 *                 SkASSERT(fTail == item);
 *             } else {
 *                 SkASSERT(item->fNext->fPrev == item);
 *             }
 *         }
 *     }
 *
 *     /**
 *      * Debugging-only method that uses the list back pointer to check if 'entry' is indeed in 'this'
 *      * list.
 *      */
 *     bool isInList(const T* entry) const {
 *         return entry->fList == this;
 *     }
 *
 *     /**
 *      * Debugging-only method that laboriously counts the list entries.
 *      */
 *     int countEntries() const {
 *         int count = 0;
 *         for (T* entry = fHead; entry; entry = entry->fNext) {
 *             ++count;
 *         }
 *         return count;
 *     }
 * #endif // SK_DEBUG
 *
 * private:
 *     T* fHead = nullptr;
 *     T* fTail = nullptr;
 *
 *     SkTInternalLList(const SkTInternalLList&) = delete;
 *     SkTInternalLList& operator=(const SkTInternalLList&) = delete;
 * }
 * ```
 */
public data class SkTInternalLList<T> public constructor(
  /**
   * C++ original:
   * ```cpp
   * T* fHead = nullptr
   * ```
   */
  private var fHead: T,
  /**
   * C++ original:
   * ```cpp
   * T* fTail = nullptr
   * ```
   */
  private var fTail: T,
) {
  /**
   * C++ original:
   * ```cpp
   * void reset() {
   *         fHead = nullptr;
   *         fTail = nullptr;
   *     }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void remove(T* entry) {
   *         SkASSERT(fHead && fTail);
   *         SkASSERT(this->isInList(entry));
   *
   *         T* prev = entry->fPrev;
   *         T* next = entry->fNext;
   *
   *         if (prev) {
   *             prev->fNext = next;
   *         } else {
   *             fHead = next;
   *         }
   *         if (next) {
   *             next->fPrev = prev;
   *         } else {
   *             fTail = prev;
   *         }
   *
   *         entry->fPrev = nullptr;
   *         entry->fNext = nullptr;
   *
   * #ifdef SK_DEBUG
   *         entry->fList = nullptr;
   * #endif
   *     }
   * ```
   */
  public fun remove(entry: T) {
    TODO("Implement remove")
  }

  /**
   * C++ original:
   * ```cpp
   * void addToHead(T* entry) {
   *         SkASSERT(nullptr == entry->fPrev && nullptr == entry->fNext);
   *         SkASSERT(nullptr == entry->fList);
   *
   *         entry->fPrev = nullptr;
   *         entry->fNext = fHead;
   *         if (fHead) {
   *             fHead->fPrev = entry;
   *         }
   *         fHead = entry;
   *         if (nullptr == fTail) {
   *             fTail = entry;
   *         }
   *
   * #ifdef SK_DEBUG
   *         entry->fList = this;
   * #endif
   *     }
   * ```
   */
  public fun addToHead(entry: T) {
    TODO("Implement addToHead")
  }

  /**
   * C++ original:
   * ```cpp
   * void addToTail(T* entry) {
   *         SkASSERT(nullptr == entry->fPrev && nullptr == entry->fNext);
   *         SkASSERT(nullptr == entry->fList);
   *
   *         entry->fPrev = fTail;
   *         entry->fNext = nullptr;
   *         if (fTail) {
   *             fTail->fNext = entry;
   *         }
   *         fTail = entry;
   *         if (nullptr == fHead) {
   *             fHead = entry;
   *         }
   *
   * #ifdef SK_DEBUG
   *         entry->fList = this;
   * #endif
   *     }
   * ```
   */
  public fun addToTail(entry: T) {
    TODO("Implement addToTail")
  }

  /**
   * C++ original:
   * ```cpp
   * void addBefore(T* newEntry, T* existingEntry) {
   *         SkASSERT(newEntry);
   *
   *         if (nullptr == existingEntry) {
   *             this->addToTail(newEntry);
   *             return;
   *         }
   *
   *         SkASSERT(this->isInList(existingEntry));
   *         newEntry->fNext = existingEntry;
   *         T* prev = existingEntry->fPrev;
   *         existingEntry->fPrev = newEntry;
   *         newEntry->fPrev = prev;
   *         if (nullptr == prev) {
   *             SkASSERT(fHead == existingEntry);
   *             fHead = newEntry;
   *         } else {
   *             prev->fNext = newEntry;
   *         }
   * #ifdef SK_DEBUG
   *         newEntry->fList = this;
   * #endif
   *     }
   * ```
   */
  public fun addBefore(newEntry: T, existingEntry: T) {
    TODO("Implement addBefore")
  }

  /**
   * C++ original:
   * ```cpp
   * void addAfter(T* newEntry, T* existingEntry) {
   *         SkASSERT(newEntry);
   *
   *         if (nullptr == existingEntry) {
   *             this->addToHead(newEntry);
   *             return;
   *         }
   *
   *         SkASSERT(this->isInList(existingEntry));
   *         newEntry->fPrev = existingEntry;
   *         T* next = existingEntry->fNext;
   *         existingEntry->fNext = newEntry;
   *         newEntry->fNext = next;
   *         if (nullptr == next) {
   *             SkASSERT(fTail == existingEntry);
   *             fTail = newEntry;
   *         } else {
   *             next->fPrev = newEntry;
   *         }
   * #ifdef SK_DEBUG
   *         newEntry->fList = this;
   * #endif
   *     }
   * ```
   */
  public fun addAfter(newEntry: T, existingEntry: T) {
    TODO("Implement addAfter")
  }

  /**
   * C++ original:
   * ```cpp
   * void concat(SkTInternalLList&& list) {
   *         if (list.isEmpty()) {
   *             return;
   *         }
   *
   *         list.fHead->fPrev = fTail;
   *         if (!fHead) {
   *             SkASSERT(!list.fHead->fPrev);
   *             fHead = list.fHead;
   *         } else {
   *             SkASSERT(fTail);
   *             fTail->fNext = list.fHead;
   *         }
   *         fTail = list.fTail;
   *
   * #ifdef SK_DEBUG
   *         for (T* node = list.fHead; node; node = node->fNext) {
   *             SkASSERT(node->fList == &list);
   *             node->fList = this;
   *         }
   * #endif
   *
   *         list.fHead = list.fTail = nullptr;
   *     }
   * ```
   */
  public fun concat(list: SkTInternalLList<T>) {
    TODO("Implement concat")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isEmpty() const {
   *         SkASSERT(SkToBool(fHead) == SkToBool(fTail));
   *         return !fHead;
   *     }
   * ```
   */
  public fun isEmpty(): Boolean {
    TODO("Implement isEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * T* head() const { return fHead; }
   * ```
   */
  public fun head(): T {
    TODO("Implement head")
  }

  /**
   * C++ original:
   * ```cpp
   * T* tail() const { return fTail; }
   * ```
   */
  public fun tail(): T {
    TODO("Implement tail")
  }

  /**
   * C++ original:
   * ```cpp
   * Iter begin() const {
   *         Iter iter;
   *         iter.init(*this, Iter::kHead_IterStart);
   *         return iter;
   *     }
   * ```
   */
  private fun begin(): Iter {
    TODO("Implement begin")
  }

  /**
   * C++ original:
   * ```cpp
   * Iter end() const { return Iter(); }
   * ```
   */
  private fun end(): Iter {
    TODO("Implement end")
  }

  /**
   * C++ original:
   * ```cpp
   * void validate() const {
   *         SkASSERT(!fHead == !fTail);
   *         Iter iter;
   *         for (T* item = iter.init(*this, Iter::kHead_IterStart); item; item = iter.next()) {
   *             SkASSERT(this->isInList(item));
   *             if (nullptr == item->fPrev) {
   *                 SkASSERT(fHead == item);
   *             } else {
   *                 SkASSERT(item->fPrev->fNext == item);
   *             }
   *             if (nullptr == item->fNext) {
   *                 SkASSERT(fTail == item);
   *             } else {
   *                 SkASSERT(item->fNext->fPrev == item);
   *             }
   *         }
   *     }
   * ```
   */
  private fun validate() {
    TODO("Implement validate")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isInList(const T* entry) const {
   *         return entry->fList == this;
   *     }
   * ```
   */
  private fun isInList(entry: T): Boolean {
    TODO("Implement isInList")
  }

  /**
   * C++ original:
   * ```cpp
   * int countEntries() const {
   *         int count = 0;
   *         for (T* entry = fHead; entry; entry = entry->fNext) {
   *             ++count;
   *         }
   *         return count;
   *     }
   * ```
   */
  private fun countEntries(): Int {
    TODO("Implement countEntries")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTInternalLList& operator=(const SkTInternalLList&) = delete
   * ```
   */
  private fun assign(param0: SkTInternalLList<T>) {
    TODO("Implement assign")
  }

  public open class Iter<T> public constructor() {
    private var fCurr: T? = TODO("Initialize fCurr")

    public constructor(iter: org.skia.core.Iter) : this() {
      TODO("Implement constructor")
    }

    public fun assign(iter: org.skia.core.Iter) {
      TODO("Implement assign")
    }

    public fun `init`(list: SkTInternalLList, startLoc: org.skia.core.Iter.IterStart): T {
      TODO("Implement init")
    }

    public fun `get`(): T {
      TODO("Implement get")
    }

    public fun next(): T {
      TODO("Implement next")
    }

    public fun prev(): T {
      TODO("Implement prev")
    }

    public override operator fun equals(other: Any?): Boolean {
      TODO("Implement equals")
    }

    public operator fun times(): T {
      TODO("Implement times")
    }

    public operator fun inc() {
      TODO("Implement inc")
    }

    public enum class IterStart {
      kHead_IterStart,
      kTail_IterStart,
    }
  }
}
