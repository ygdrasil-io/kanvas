package org.skia.core

import SI
import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import undefined.Args

/**
 * C++ original:
 * ```cpp
 * template <typename T, int StartingItems = 1>
 * class SkTBlockList {
 * public:
 *     /**
 *      * Create an list that defaults to using StartingItems as heap increment and the
 *      * kFixed growth policy (e.g. all allocations will match StartingItems).
 *      */
 *     SkTBlockList() : SkTBlockList(SkBlockAllocator::GrowthPolicy::kFixed) {}
 *
 *     /**
 *      * Create an list that defaults to using StartingItems as the heap increment, but with
 *      * the defined growth policy.
 *     */
 *     explicit SkTBlockList(SkBlockAllocator::GrowthPolicy policy)
 *             : SkTBlockList(StartingItems, policy) {}
 *
 *     /**
 *      * Create an list.
 *      *
 *      * @param   itemsPerBlock   the number of items to allocate at once
 *      * @param   policy          the growth policy for subsequent blocks of items
 *      */
 *     explicit SkTBlockList(int itemsPerBlock,
 *                           SkBlockAllocator::GrowthPolicy policy =
 *                                   SkBlockAllocator::GrowthPolicy::kFixed)
 *             : fAllocator(policy,
 *                          SkBlockAllocator::BlockOverhead<alignof(T)>() + sizeof(T)*itemsPerBlock) {}
 *
 *     ~SkTBlockList() { this->reset(); }
 *
 *     /**
 *      * Adds an item and returns it.
 *      *
 *      * @return the added item.
 *      */
 *     T& push_back() {
 *         return *new (this->pushItem()) T;
 *     }
 *     T& push_back(const T& t) {
 *         return *new (this->pushItem()) T(t);
 *     }
 *     T& push_back(T&& t) {
 *         return *new (this->pushItem()) T(std::move(t));
 *     }
 *
 *     template <typename... Args>
 *     T& emplace_back(Args&&... args) {
 *         return *new (this->pushItem()) T(std::forward<Args>(args)...);
 *     }
 *
 *     /**
 *      * Move all items from 'other' to the end of this collection. When this returns, 'other' will
 *      * be empty. Items in 'other' may be moved as part of compacting the pre-allocated start of
 *      * 'other' into this list (using T's move constructor or memcpy if T is trivially copyable), but
 *      * this is O(StartingItems) and not O(N). All other items are concatenated in O(1).
 *      */
 *     template <int SI>
 *     void concat(SkTBlockList<T, SI>&& other);
 *
 *     /**
 *      * Allocate, if needed, space to hold N more Ts before another malloc will occur.
 *      */
 *     void reserve(int n) {
 *         int avail = fAllocator->currentBlock()->template avail<alignof(T)>() / sizeof(T);
 *         if (n > avail) {
 *             int reserved = n - avail;
 *             // Don't consider existing bytes since we've already determined how to split the N items
 *             fAllocator->template reserve<alignof(T)>(
 *                     reserved * sizeof(T), SkBlockAllocator::kIgnoreExistingBytes_Flag);
 *         }
 *     }
 *
 *     /**
 *      * Remove the last item, only call if count() != 0
 *      */
 *     void pop_back() {
 *         SkASSERT(this->count() > 0);
 *
 *         SkBlockAllocator::Block* block = fAllocator->currentBlock();
 *
 *         // Run dtor for the popped item
 *         int releaseIndex = Last(block);
 *         GetItem(block, releaseIndex).~T();
 *
 *         if (releaseIndex == First(block)) {
 *             fAllocator->releaseBlock(block);
 *         } else {
 *             // Since this always follows LIFO, the block should always be able to release the memory
 *             SkAssertResult(block->release(releaseIndex, releaseIndex + sizeof(T)));
 *             block->setMetadata(Decrement(block, releaseIndex));
 *         }
 *
 *         fAllocator->setMetadata(fAllocator->metadata() - 1);
 *     }
 *
 *     /**
 *      * Removes all added items.
 *      */
 *     void reset() {
 *         // Invoke destructors in reverse order if not trivially destructible
 *         if constexpr (!std::is_trivially_destructible<T>::value) {
 *             for (T& t : this->ritems()) {
 *                 t.~T();
 *             }
 *         }
 *
 *         fAllocator->reset();
 *     }
 *
 *     /**
 *      * Returns the item count.
 *      */
 *     int count() const {
 * #ifdef SK_DEBUG
 *         // Confirm total count matches sum of block counts
 *         int count = 0;
 *         for (const auto* b :fAllocator->blocks()) {
 *             if (b->metadata() == 0) {
 *                 continue; // skip empty
 *             }
 *             count += (sizeof(T) + Last(b) - First(b)) / sizeof(T);
 *         }
 *         SkASSERT(count == fAllocator->metadata());
 * #endif
 *         return fAllocator->metadata();
 *     }
 *
 *     /**
 *      * Is the count 0?
 *      */
 *     bool empty() const { return this->count() == 0; }
 *
 *     /**
 *      * Access first item, only call if count() != 0
 *      */
 *     T& front() {
 *         // This assumes that the head block actually have room to store the first item.
 *         static_assert(StartingItems >= 1);
 *         SkASSERT(this->count() > 0 && fAllocator->headBlock()->metadata() > 0);
 *         return GetItem(fAllocator->headBlock(), First(fAllocator->headBlock()));
 *     }
 *     const T& front() const {
 *         SkASSERT(this->count() > 0 && fAllocator->headBlock()->metadata() > 0);
 *         return GetItem(fAllocator->headBlock(), First(fAllocator->headBlock()));
 *     }
 *
 *     /**
 *      * Access last item, only call if count() != 0
 *      */
 *     T& back() {
 *         SkASSERT(this->count() > 0 && fAllocator->currentBlock()->metadata() > 0);
 *         return GetItem(fAllocator->currentBlock(), Last(fAllocator->currentBlock()));
 *     }
 *     const T& back() const {
 *         SkASSERT(this->count() > 0 && fAllocator->currentBlock()->metadata() > 0);
 *         return GetItem(fAllocator->currentBlock(), Last(fAllocator->currentBlock()));
 *     }
 *
 *     /**
 *      * Access item by index. Not an operator[] since it should not be considered constant time.
 *      * Use for-range loops by calling items() or ritems() instead to access all added items in order
 *      */
 *     T& item(int i) {
 *         SkASSERT(i >= 0 && i < this->count());
 *
 *         // Iterate over blocks until we find the one that contains i.
 *         for (auto* b : fAllocator->blocks()) {
 *             if (b->metadata() == 0) {
 *                 continue; // skip empty
 *             }
 *
 *             int start = First(b);
 *             int end = Last(b) + sizeof(T); // exclusive
 *             int index = start + i * sizeof(T);
 *             if (index < end) {
 *                 return GetItem(b, index);
 *             } else {
 *                 i -= (end - start) / sizeof(T);
 *             }
 *         }
 *         SkUNREACHABLE;
 *     }
 *     const T& item(int i) const {
 *         return const_cast<SkTBlockList*>(this)->item(i);
 *     }
 *
 * private:
 *     // Let other SkTBlockLists have access (only ever used when T and S are the same but you
 *     // cannot have partial specializations declared as a friend...)
 *     template<typename S, int N> friend class SkTBlockList;
 *     friend class TBlockListTestAccess;  // for fAllocator
 *
 *     inline static constexpr size_t StartingSize =
 *             SkBlockAllocator::Overhead<alignof(T)>() + StartingItems * sizeof(T);
 *
 *     static T& GetItem(SkBlockAllocator::Block* block, int index) {
 *         return *static_cast<T*>(block->ptr(index));
 *     }
 *     static const T& GetItem(const SkBlockAllocator::Block* block, int index) {
 *         return *static_cast<const T*>(block->ptr(index));
 *     }
 *     static int First(const SkBlockAllocator::Block* b) {
 *         return b->firstAlignedOffset<alignof(T)>();
 *     }
 *     static int Last(const SkBlockAllocator::Block* b) {
 *         return b->metadata();
 *     }
 *     static int Increment(const SkBlockAllocator::Block* b, int index) {
 *         return index + sizeof(T);
 *     }
 *     static int Decrement(const SkBlockAllocator::Block* b, int index) {
 *         return index - sizeof(T);
 *     }
 *
 *     void* pushItem() {
 *         // 'template' required because fAllocator is a template, calling a template member
 *         auto br = fAllocator->template allocate<alignof(T)>(sizeof(T));
 *         SkASSERT(br.fStart == br.fAlignedOffset ||
 *                  br.fAlignedOffset == First(fAllocator->currentBlock()));
 *         br.fBlock->setMetadata(br.fAlignedOffset);
 *         fAllocator->setMetadata(fAllocator->metadata() + 1);
 *         return br.fBlock->ptr(br.fAlignedOffset);
 *     }
 *
 *     // N represents the number of items, whereas SkSBlockAllocator takes total bytes, so must
 *     // account for the block allocator's size too.
 *     //
 *     // This class uses the SkBlockAllocator's metadata to track total count of items, and per-block
 *     // metadata to track the index of the last allocated item within each block.
 *     SkSBlockAllocator<StartingSize> fAllocator;
 *
 * public:
 *     using Iter   = BlockIndexIterator<T&,       true,  false, &First, &Last,  &Increment, &GetItem>;
 *     using CIter  = BlockIndexIterator<const T&, true,  true,  &First, &Last,  &Increment, &GetItem>;
 *     using RIter  = BlockIndexIterator<T&,       false, false, &Last,  &First, &Decrement, &GetItem>;
 *     using CRIter = BlockIndexIterator<const T&, false, true,  &Last,  &First, &Decrement, &GetItem>;
 *
 *     /**
 *      * Iterate over all items in allocation order (oldest to newest) using a for-range loop:
 *      *
 *      *   for (auto&& T : this->items()) {}
 *      */
 *     Iter   items() { return Iter(fAllocator.allocator()); }
 *     CIter  items() const { return CIter(fAllocator.allocator()); }
 *
 *     // Iterate from newest to oldest using a for-range loop.
 *     RIter  ritems() { return RIter(fAllocator.allocator()); }
 *     CRIter ritems() const { return CRIter(fAllocator.allocator()); }
 * }
 * ```
 */
public data class SkTBlockList16<T> public constructor(
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr size_t StartingSize
   * ```
   */
  private var fAllocator: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * T& push_back() {
   *         return *new (this->pushItem()) T;
   *     }
   * ```
   */
  public fun pushBack(): T {
    TODO("Implement pushBack")
  }

  /**
   * C++ original:
   * ```cpp
   * T& push_back(const T& t) {
   *         return *new (this->pushItem()) T(t);
   *     }
   * ```
   */
  public fun pushBack(t: T): T {
    TODO("Implement pushBack")
  }

  /**
   * C++ original:
   * ```cpp
   * T& push_back(T&& t) {
   *         return *new (this->pushItem()) T(std::move(t));
   *     }
   * ```
   */
  public fun <Args> emplaceBack(args: Args): T {
    TODO("Implement emplaceBack")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename... Args>
   *     T& emplace_back(Args&&... args) {
   *         return *new (this->pushItem()) T(std::forward<Args>(args)...);
   *     }
   * ```
   */
  public fun <SI> concat(other: SkTBlockList<T, SI>) {
    TODO("Implement concat")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <int SI>
   *     void concat(SkTBlockList<T, SI>&& other)
   * ```
   */
  public fun reserve(n: Int) {
    TODO("Implement reserve")
  }

  /**
   * C++ original:
   * ```cpp
   * void reserve(int n) {
   *         int avail = fAllocator->currentBlock()->template avail<alignof(T)>() / sizeof(T);
   *         if (n > avail) {
   *             int reserved = n - avail;
   *             // Don't consider existing bytes since we've already determined how to split the N items
   *             fAllocator->template reserve<alignof(T)>(
   *                     reserved * sizeof(T), SkBlockAllocator::kIgnoreExistingBytes_Flag);
   *         }
   *     }
   * ```
   */
  public fun popBack() {
    TODO("Implement popBack")
  }

  /**
   * C++ original:
   * ```cpp
   * void pop_back() {
   *         SkASSERT(this->count() > 0);
   *
   *         SkBlockAllocator::Block* block = fAllocator->currentBlock();
   *
   *         // Run dtor for the popped item
   *         int releaseIndex = Last(block);
   *         GetItem(block, releaseIndex).~T();
   *
   *         if (releaseIndex == First(block)) {
   *             fAllocator->releaseBlock(block);
   *         } else {
   *             // Since this always follows LIFO, the block should always be able to release the memory
   *             SkAssertResult(block->release(releaseIndex, releaseIndex + sizeof(T)));
   *             block->setMetadata(Decrement(block, releaseIndex));
   *         }
   *
   *         fAllocator->setMetadata(fAllocator->metadata() - 1);
   *     }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void reset() {
   *         // Invoke destructors in reverse order if not trivially destructible
   *         if constexpr (!std::is_trivially_destructible<T>::value) {
   *             for (T& t : this->ritems()) {
   *                 t.~T();
   *             }
   *         }
   *
   *         fAllocator->reset();
   *     }
   * ```
   */
  public fun count(): Int {
    TODO("Implement count")
  }

  /**
   * C++ original:
   * ```cpp
   * int count() const {
   * #ifdef SK_DEBUG
   *         // Confirm total count matches sum of block counts
   *         int count = 0;
   *         for (const auto* b :fAllocator->blocks()) {
   *             if (b->metadata() == 0) {
   *                 continue; // skip empty
   *             }
   *             count += (sizeof(T) + Last(b) - First(b)) / sizeof(T);
   *         }
   *         SkASSERT(count == fAllocator->metadata());
   * #endif
   *         return fAllocator->metadata();
   *     }
   * ```
   */
  public fun empty(): Boolean {
    TODO("Implement empty")
  }

  /**
   * C++ original:
   * ```cpp
   * bool empty() const { return this->count() == 0; }
   * ```
   */
  public fun front(): T {
    TODO("Implement front")
  }

  /**
   * C++ original:
   * ```cpp
   * T& front() {
   *         // This assumes that the head block actually have room to store the first item.
   *         static_assert(StartingItems >= 1);
   *         SkASSERT(this->count() > 0 && fAllocator->headBlock()->metadata() > 0);
   *         return GetItem(fAllocator->headBlock(), First(fAllocator->headBlock()));
   *     }
   * ```
   */
  public fun back(): T {
    TODO("Implement back")
  }

  /**
   * C++ original:
   * ```cpp
   * const T& front() const {
   *         SkASSERT(this->count() > 0 && fAllocator->headBlock()->metadata() > 0);
   *         return GetItem(fAllocator->headBlock(), First(fAllocator->headBlock()));
   *     }
   * ```
   */
  public fun item(i: Int): T {
    TODO("Implement item")
  }

  /**
   * C++ original:
   * ```cpp
   * T& back() {
   *         SkASSERT(this->count() > 0 && fAllocator->currentBlock()->metadata() > 0);
   *         return GetItem(fAllocator->currentBlock(), Last(fAllocator->currentBlock()));
   *     }
   * ```
   */
  private fun pushItem() {
    TODO("Implement pushItem")
  }

  /**
   * C++ original:
   * ```cpp
   * const T& back() const {
   *         SkASSERT(this->count() > 0 && fAllocator->currentBlock()->metadata() > 0);
   *         return GetItem(fAllocator->currentBlock(), Last(fAllocator->currentBlock()));
   *     }
   * ```
   */
  public fun items(): Int {
    TODO("Implement items")
  }

  /**
   * C++ original:
   * ```cpp
   * T& item(int i) {
   *         SkASSERT(i >= 0 && i < this->count());
   *
   *         // Iterate over blocks until we find the one that contains i.
   *         for (auto* b : fAllocator->blocks()) {
   *             if (b->metadata() == 0) {
   *                 continue; // skip empty
   *             }
   *
   *             int start = First(b);
   *             int end = Last(b) + sizeof(T); // exclusive
   *             int index = start + i * sizeof(T);
   *             if (index < end) {
   *                 return GetItem(b, index);
   *             } else {
   *                 i -= (end - start) / sizeof(T);
   *             }
   *         }
   *         SkUNREACHABLE;
   *     }
   * ```
   */
  public fun ritems(): Int {
    TODO("Implement ritems")
  }

  public companion object {
    private val startingSize: Int = TODO("Initialize startingSize")

    /**
     * C++ original:
     * ```cpp
     * static T& GetItem(SkBlockAllocator::Block* block, int index) {
     *         return *static_cast<T*>(block->ptr(index));
     *     }
     * ```
     */
    private fun getItem(block: SkBlockAllocator.Block?, index: Int): Any {
      TODO("Implement getItem")
    }

    /**
     * C++ original:
     * ```cpp
     * static const T& GetItem(const SkBlockAllocator::Block* block, int index) {
     *         return *static_cast<const T*>(block->ptr(index));
     *     }
     * ```
     */
    private fun first(b: SkBlockAllocator.Block?): Int {
      TODO("Implement first")
    }

    /**
     * C++ original:
     * ```cpp
     * static int First(const SkBlockAllocator::Block* b) {
     *         return b->firstAlignedOffset<alignof(T)>();
     *     }
     * ```
     */
    private fun last(b: SkBlockAllocator.Block?): Int {
      TODO("Implement last")
    }

    /**
     * C++ original:
     * ```cpp
     * static int Last(const SkBlockAllocator::Block* b) {
     *         return b->metadata();
     *     }
     * ```
     */
    private fun increment(b: SkBlockAllocator.Block?, index: Int): Int {
      TODO("Implement increment")
    }

    /**
     * C++ original:
     * ```cpp
     * static int Increment(const SkBlockAllocator::Block* b, int index) {
     *         return index + sizeof(T);
     *     }
     * ```
     */
    private fun decrement(b: SkBlockAllocator.Block?, index: Int): Int {
      TODO("Implement decrement")
    }
  }
}
