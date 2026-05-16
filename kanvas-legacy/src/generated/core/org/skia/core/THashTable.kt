package org.skia.core

import SlotVal
import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import IterTTable as IterTTable_
import undefined.IterTTable as UndefinedIterTTable

/**
 * C++ original:
 * ```cpp
 * template <typename T, typename K, typename Traits = T>
 * class THashTable {
 * public:
 *     THashTable()  = default;
 *     ~THashTable() = default;
 *
 *     THashTable(const THashTable&  that) { *this = that; }
 *     THashTable(      THashTable&& that) { *this = std::move(that); }
 *
 *     THashTable& operator=(const THashTable& that) {
 *         if (this != &that) {
 *             fCount     = that.fCount;
 *             fCapacity  = that.fCapacity;
 *             fSlots.reset(new Slot[that.fCapacity]);
 *             for (int i = 0; i < fCapacity; i++) {
 *                 fSlots[i] = that.fSlots[i];
 *             }
 *         }
 *         return *this;
 *     }
 *
 *     THashTable& operator=(THashTable&& that) {
 *         if (this != &that) {
 *             fCount    = that.fCount;
 *             fCapacity = that.fCapacity;
 *             fSlots    = std::move(that.fSlots);
 *
 *             that.fCount = that.fCapacity = 0;
 *         }
 *         return *this;
 *     }
 *
 *     // Clear the table.
 *     void reset() { *this = THashTable(); }
 *
 *     // How many entries are in the table?
 *     int count() const { return fCount; }
 *
 *     // How many slots does the table contain? (Note that unlike an array, hash tables can grow
 *     // before reaching 100% capacity.)
 *     int capacity() const { return fCapacity; }
 *
 *     // Approximately how many bytes of memory do we use beyond sizeof(*this)?
 *     size_t approxBytesUsed() const { return fCapacity * sizeof(Slot); }
 *
 *     // Exchange two hash tables.
 *     void swap(THashTable& that) {
 *         std::swap(fCount, that.fCount);
 *         std::swap(fCapacity, that.fCapacity);
 *         std::swap(fSlots, that.fSlots);
 *     }
 *
 *     void swap(THashTable&& that) {
 *         *this = std::move(that);
 *     }
 *
 *     // !!!!!!!!!!!!!!!!!                 CAUTION                   !!!!!!!!!!!!!!!!!
 *     // set(), find() and foreach() all allow mutable access to table entries.
 *     // If you change an entry so that it no longer has the same key, all hell
 *     // will break loose.  Do not do that!
 *     //
 *     // Please prefer to use THashMap or THashSet, which do not have this danger.
 *
 *     // The pointers returned by set() and find() are valid only until the next call to set().
 *     // The pointers you receive in foreach() are only valid for its duration.
 *
 *     // Copy val into the hash table, returning a pointer to the copy now in the table.
 *     // If there already is an entry in the table with the same key, we overwrite it.
 *     T* set(T val) {
 *         bool shouldGrow = false;
 *         if constexpr (HasShouldGrow<Traits>::value) {
 *             shouldGrow = Traits::ShouldGrow(fCount, fCapacity);
 *         } else {
 *             shouldGrow = (4 * fCount >= 3 * fCapacity);
 *         }
 *         if (shouldGrow) {
 *             this->resize(fCapacity > 0 ? fCapacity * 2 : 4);
 *         }
 *         return this->uncheckedSet(std::move(val));
 *     }
 *
 *     // If there is an entry in the table with this key, return a pointer to it.  If not, null.
 *     T* find(const K& key) const {
 *         uint32_t hash = Hash(key);
 *         int index = hash & (fCapacity-1);
 *         for (int n = 0; n < fCapacity; n++) {
 *             Slot& s = fSlots[index];
 *             if (s.empty()) {
 *                 return nullptr;
 *             }
 *             if (hash == s.fHash && key == Traits::GetKey(*s)) {
 *                 return &*s;
 *             }
 *             index = this->next(index);
 *         }
 *         SkASSERT(fCapacity == fCount);
 *         return nullptr;
 *     }
 *
 *     // If there is an entry in the table with this key, return it.  If not, null.
 *     // This only works for pointer type T, and cannot be used to find an nullptr entry.
 *     T findOrNull(const K& key) const {
 *         if (T* p = this->find(key)) {
 *             return *p;
 *         }
 *         return nullptr;
 *     }
 *
 *     // If a value with this key exists in the hash table, removes it and returns true.
 *     // Otherwise, returns false.
 *     bool removeIfExists(const K& key) {
 *         uint32_t hash = Hash(key);
 *         int index = hash & (fCapacity-1);
 *         for (int n = 0; n < fCapacity; n++) {
 *             Slot& s = fSlots[index];
 *             if (s.empty()) {
 *                 return false;
 *             }
 *             if (hash == s.fHash && key == Traits::GetKey(*s)) {
 *                 this->removeSlot(index);
 *                 if (fCapacity > 4) {
 *                     bool shouldShrink = false;
 *                     if constexpr (HasShouldShrink<Traits>::value) {
 *                         shouldShrink = Traits::ShouldShrink(fCount, fCapacity);
 *                     } else {
 *                         shouldShrink = (4 * fCount <= fCapacity);
 *                     }
 *                     if (shouldShrink) {
 *                         this->resize(fCapacity / 2);
 *                     }
 *                 }
 *                 return true;
 *             }
 *             index = this->next(index);
 *         }
 *         SkASSERT(fCapacity == fCount);
 *         return false;
 *     }
 *
 *     // Removes the value with this key from the hash table. Asserts if it is missing.
 *     void remove(const K& key) {
 *         SkAssertResult(this->removeIfExists(key));
 *     }
 *
 *     // Hash tables will automatically resize themselves when set() and remove() are called, but
 *     // resize() can be called to manually grow capacity before a bulk insertion.
 *     void resize(int capacity) {
 *         // We must have enough capacity to hold every key.
 *         SkASSERT(capacity >= fCount);
 *         // `capacity` must be a power of two, because we use `hash & (capacity-1)` to look up keys
 *         // in the table (since this is faster than a modulo).
 *         SkASSERT((capacity & (capacity - 1)) == 0);
 *
 *         int oldCapacity = fCapacity;
 *         SkDEBUGCODE(int oldCount = fCount);
 *
 *         fCount = 0;
 *         fCapacity = capacity;
 *         std::unique_ptr<Slot[]> oldSlots = std::move(fSlots);
 *         fSlots.reset(new Slot[capacity]);
 *
 *         for (int i = 0; i < oldCapacity; i++) {
 *             Slot& s = oldSlots[i];
 *             if (s.has_value()) {
 *                 this->uncheckedSet(*std::move(s));
 *             }
 *         }
 *         SkASSERT(fCount == oldCount);
 *     }
 *
 *     // Reserve extra capacity. This only grows capacity; requests to shrink are ignored.
 *     // We assume that the passed-in value represents the number of items that the caller wants to
 *     // store in the table. The passed-in value is adjusted to honor the following rules:
 *     // - Hash tables must have a power-of-two capacity.
 *     // - Hash tables grow when they exceed 3/4 capacity, not when they are full.
 *     void reserve(int n) {
 *         int newCapacity = SkNextPow2(n);
 *
 *         bool shouldGrow = false;
 *         if constexpr (HasShouldGrow<Traits>::value) {
 *             shouldGrow = Traits::ShouldGrow(n, newCapacity);
 *         } else {
 *             shouldGrow = (n * 4 > newCapacity * 3);
 *         }
 *         if (shouldGrow) {
 *             newCapacity *= 2;
 *         }
 *
 *         if (newCapacity > fCapacity) {
 *             this->resize(newCapacity);
 *         }
 *     }
 *
 *     // Call fn on every entry in the table.  You may mutate the entries, but be very careful.
 *     template <typename Fn>  // f(T*)
 *     void foreach(Fn&& fn) {
 *         for (int i = 0; i < fCapacity; i++) {
 *             if (fSlots[i].has_value()) {
 *                 fn(&*fSlots[i]);
 *             }
 *         }
 *     }
 *
 *     // Call fn on every entry in the table.  You may not mutate anything.
 *     template <typename Fn>  // f(T) or f(const T&)
 *     void foreach(Fn&& fn) const {
 *         for (int i = 0; i < fCapacity; i++) {
 *             if (fSlots[i].has_value()) {
 *                 fn(*fSlots[i]);
 *             }
 *         }
 *     }
 *
 *     // A basic iterator-like class which disallows mutation; sufficient for range-based for loops.
 *     // Intended for use by THashMap and THashSet via begin() and end().
 *     // Adding or removing elements may invalidate all iterators.
 *     template <typename SlotVal>
 *     class Iter {
 *     public:
 *         using TTable = THashTable<T, K, Traits>;
 *
 *         Iter(const TTable* table, int slot) : fTable(table), fSlot(slot) {}
 *
 *         static Iter MakeBegin(const TTable* table) {
 *             return Iter{table, table->firstPopulatedSlot()};
 *         }
 *
 *         static Iter MakeEnd(const TTable* table) {
 *             return Iter{table, table->capacity()};
 *         }
 *
 *         const SlotVal& operator*() const {
 *             return *fTable->slot(fSlot);
 *         }
 *
 *         const SlotVal* operator->() const {
 *             return fTable->slot(fSlot);
 *         }
 *
 *         bool operator==(const Iter& that) const {
 *             // Iterators from different tables shouldn't be compared against each other.
 *             SkASSERT(fTable == that.fTable);
 *             return fSlot == that.fSlot;
 *         }
 *
 *         bool operator!=(const Iter& that) const {
 *             return !(*this == that);
 *         }
 *
 *         Iter& operator++() {
 *             fSlot = fTable->nextPopulatedSlot(fSlot);
 *             return *this;
 *         }
 *
 *         Iter operator++(int) {
 *             Iter old = *this;
 *             this->operator++();
 *             return old;
 *         }
 *
 *     protected:
 *         const TTable* fTable;
 *         int fSlot;
 *     };
 *
 * private:
 *     template <typename U, typename = void> struct HasShouldGrow : std::false_type {};
 *     template <typename U, typename = void> struct HasShouldShrink : std::false_type {};
 *
 *     template <typename U>
 *     struct HasShouldGrow<
 *             U,
 *             std::void_t<decltype(U::ShouldGrow(std::declval<int>(), std::declval<int>()))>>
 *             : std::true_type {
 *         static_assert(HasShouldShrink<U>::value,
 *                       "The traits class must also provide ShouldShrink() method.");
 *     };
 *
 *     template <typename U>
 *     struct HasShouldShrink<
 *             U,
 *             std::void_t<decltype(U::ShouldShrink(std::declval<int>(), std::declval<int>()))>>
 *             : std::true_type {
 *         static_assert(HasShouldGrow<U>::value,
 *                       "The traits class must also provide ShouldGrow() method.");
 *     };
 *
 *     // Finds the first non-empty slot for an iterator.
 *     int firstPopulatedSlot() const {
 *         for (int i = 0; i < fCapacity; i++) {
 *             if (fSlots[i].has_value()) {
 *                 return i;
 *             }
 *         }
 *         return fCapacity;
 *     }
 *
 *     // Increments an iterator's slot.
 *     int nextPopulatedSlot(int currentSlot) const {
 *         for (int i = currentSlot + 1; i < fCapacity; i++) {
 *             if (fSlots[i].has_value()) {
 *                 return i;
 *             }
 *         }
 *         return fCapacity;
 *     }
 *
 *     // Reads from an iterator's slot.
 *     const T* slot(int i) const {
 *         SkASSERT(fSlots[i].has_value());
 *         return &*fSlots[i];
 *     }
 *
 *     T* uncheckedSet(T&& val) {
 *         const K& key = Traits::GetKey(val);
 *         SkASSERT(key == key);
 *         uint32_t hash = Hash(key);
 *         int index = hash & (fCapacity-1);
 *         for (int n = 0; n < fCapacity; n++) {
 *             Slot& s = fSlots[index];
 *             if (s.empty()) {
 *                 // New entry.
 *                 s.emplace(std::move(val), hash);
 *                 fCount++;
 *                 return &*s;
 *             }
 *             if (hash == s.fHash && key == Traits::GetKey(*s)) {
 *                 // Overwrite previous entry.
 *                 // Note: this triggers extra copies when adding the same value repeatedly.
 *                 s.emplace(std::move(val), hash);
 *                 return &*s;
 *             }
 *
 *             index = this->next(index);
 *         }
 *         SkASSERT(false);
 *         return nullptr;
 *     }
 *
 *     void removeSlot(int index) {
 *         fCount--;
 *
 *         // Rearrange elements to restore the invariants for linear probing.
 *         for (;;) {
 *             Slot& emptySlot = fSlots[index];
 *             int emptyIndex = index;
 *             int originalIndex;
 *             // Look for an element that can be moved into the empty slot.
 *             // If the empty slot is in between where an element landed, and its native slot, then
 *             // move it to the empty slot. Don't move it if its native slot is in between where
 *             // the element landed and the empty slot.
 *             // [native] <= [empty] < [candidate] == GOOD, can move candidate to empty slot
 *             // [empty] < [native] < [candidate] == BAD, need to leave candidate where it is
 *             do {
 *                 index = this->next(index);
 *                 Slot& s = fSlots[index];
 *                 if (s.empty()) {
 *                     // We're done shuffling elements around.  Clear the last empty slot.
 *                     emptySlot.reset();
 *                     return;
 *                 }
 *                 originalIndex = s.fHash & (fCapacity - 1);
 *             } while ((index <= originalIndex && originalIndex < emptyIndex)
 *                      || (originalIndex < emptyIndex && emptyIndex < index)
 *                      || (emptyIndex < index && index <= originalIndex));
 *             // Move the element to the empty slot.
 *             Slot& moveFrom = fSlots[index];
 *             emptySlot = std::move(moveFrom);
 *         }
 *     }
 *
 *     int next(int index) const {
 *         index--;
 *         if (index < 0) { index += fCapacity; }
 *         return index;
 *     }
 *
 *     static uint32_t Hash(const K& key) {
 *         uint32_t hash = Traits::Hash(key) & 0xffffffff;
 *         return hash ? hash : 1;  // We reserve hash 0 to mark empty.
 *     }
 *
 *     class Slot {
 *     public:
 *         Slot() = default;
 *         ~Slot() { this->reset(); }
 *
 *         Slot(const Slot& that) { *this = that; }
 *         Slot& operator=(const Slot& that) {
 *             if (this == &that) {
 *                 return *this;
 *             }
 *             if (fHash) {
 *                 if (that.fHash) {
 *                     fVal.fStorage = that.fVal.fStorage;
 *                     fHash = that.fHash;
 *                 } else {
 *                     this->reset();
 *                 }
 *             } else {
 *                 if (that.fHash) {
 *                     new (&fVal.fStorage) T(that.fVal.fStorage);
 *                     fHash = that.fHash;
 *                 } else {
 *                     // do nothing, no value on either side
 *                 }
 *             }
 *             return *this;
 *         }
 *
 *         Slot(Slot&& that) { *this = std::move(that); }
 *         Slot& operator=(Slot&& that) {
 *             if (this == &that) {
 *                 return *this;
 *             }
 *             if (fHash) {
 *                 if (that.fHash) {
 *                     fVal.fStorage = std::move(that.fVal.fStorage);
 *                     fHash = that.fHash;
 *                 } else {
 *                     this->reset();
 *                 }
 *             } else {
 *                 if (that.fHash) {
 *                     new (&fVal.fStorage) T(std::move(that.fVal.fStorage));
 *                     fHash = that.fHash;
 *                 } else {
 *                     // do nothing, no value on either side
 *                 }
 *             }
 *             return *this;
 *         }
 *
 *         T& operator*() & { return fVal.fStorage; }
 *         const T& operator*() const& { return fVal.fStorage; }
 *         T&& operator*() && { return std::move(fVal.fStorage); }
 *         const T&& operator*() const&& { return std::move(fVal.fStorage); }
 *
 *         Slot& emplace(T&& v, uint32_t h) {
 *             this->reset();
 *             new (&fVal.fStorage) T(std::move(v));
 *             fHash = h;
 *             return *this;
 *         }
 *
 *         bool has_value() const { return fHash != 0; }
 *         explicit operator bool() const { return this->has_value(); }
 *         bool empty() const { return !this->has_value(); }
 *
 *         void reset() {
 *             if (fHash) {
 *                 fVal.fStorage.~T();
 *                 fHash = 0;
 *             }
 *         }
 *
 *         uint32_t fHash = 0;
 *
 *     private:
 *         union Storage {
 *             T fStorage;
 *             Storage() {}
 *             ~Storage() {}
 *         } fVal;
 *     };
 *
 *     int fCount    = 0,
 *         fCapacity = 0;
 *     std::unique_ptr<Slot[]> fSlots;
 * }
 * ```
 */
public open class THashTable<T, K, Traits> public constructor() {
  /**
   * C++ original:
   * ```cpp
   * int fCount    = 0
   * ```
   */
  private var fCount: Int = TODO("Initialize fCount")

  /**
   * C++ original:
   * ```cpp
   * int fCount    = 0,
   *         fCapacity = 0
   * ```
   */
  private var fCapacity: Int = TODO("Initialize fCapacity")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<Slot[]> fSlots
   * ```
   */
  private var fSlots: Int = TODO("Initialize fSlots")

  /**
   * C++ original:
   * ```cpp
   * THashTable()  = default
   * ```
   */
  public constructor(that: THashTable<T, K, Traits>) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * THashTable& operator=(const THashTable& that) {
   *         if (this != &that) {
   *             fCount     = that.fCount;
   *             fCapacity  = that.fCapacity;
   *             fSlots.reset(new Slot[that.fCapacity]);
   *             for (int i = 0; i < fCapacity; i++) {
   *                 fSlots[i] = that.fSlots[i];
   *             }
   *         }
   *         return *this;
   *     }
   * ```
   */
  public fun assign(that: THashTable<T, K, Traits>) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * THashTable& operator=(THashTable&& that) {
   *         if (this != &that) {
   *             fCount    = that.fCount;
   *             fCapacity = that.fCapacity;
   *             fSlots    = std::move(that.fSlots);
   *
   *             that.fCount = that.fCapacity = 0;
   *         }
   *         return *this;
   *     }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void reset() { *this = THashTable(); }
   * ```
   */
  public fun count(): Int {
    TODO("Implement count")
  }

  /**
   * C++ original:
   * ```cpp
   * int count() const { return fCount; }
   * ```
   */
  public fun capacity(): Int {
    TODO("Implement capacity")
  }

  /**
   * C++ original:
   * ```cpp
   * int capacity() const { return fCapacity; }
   * ```
   */
  public fun approxBytesUsed(): Int {
    TODO("Implement approxBytesUsed")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t approxBytesUsed() const { return fCapacity * sizeof(Slot); }
   * ```
   */
  public fun swap(that: THashTable<T, K, Traits>) {
    TODO("Implement swap")
  }

  /**
   * C++ original:
   * ```cpp
   * void swap(THashTable& that) {
   *         std::swap(fCount, that.fCount);
   *         std::swap(fCapacity, that.fCapacity);
   *         std::swap(fSlots, that.fSlots);
   *     }
   * ```
   */
  public fun `set`(`val`: T): T {
    TODO("Implement set")
  }

  /**
   * C++ original:
   * ```cpp
   * void swap(THashTable&& that) {
   *         *this = std::move(that);
   *     }
   * ```
   */
  public fun find(key: K): T {
    TODO("Implement find")
  }

  /**
   * C++ original:
   * ```cpp
   * T* set(T val) {
   *         bool shouldGrow = false;
   *         if constexpr (HasShouldGrow<Traits>::value) {
   *             shouldGrow = Traits::ShouldGrow(fCount, fCapacity);
   *         } else {
   *             shouldGrow = (4 * fCount >= 3 * fCapacity);
   *         }
   *         if (shouldGrow) {
   *             this->resize(fCapacity > 0 ? fCapacity * 2 : 4);
   *         }
   *         return this->uncheckedSet(std::move(val));
   *     }
   * ```
   */
  public fun findOrNull(key: K): T {
    TODO("Implement findOrNull")
  }

  /**
   * C++ original:
   * ```cpp
   * T* find(const K& key) const {
   *         uint32_t hash = Hash(key);
   *         int index = hash & (fCapacity-1);
   *         for (int n = 0; n < fCapacity; n++) {
   *             Slot& s = fSlots[index];
   *             if (s.empty()) {
   *                 return nullptr;
   *             }
   *             if (hash == s.fHash && key == Traits::GetKey(*s)) {
   *                 return &*s;
   *             }
   *             index = this->next(index);
   *         }
   *         SkASSERT(fCapacity == fCount);
   *         return nullptr;
   *     }
   * ```
   */
  public fun removeIfExists(key: K): Boolean {
    TODO("Implement removeIfExists")
  }

  /**
   * C++ original:
   * ```cpp
   * T findOrNull(const K& key) const {
   *         if (T* p = this->find(key)) {
   *             return *p;
   *         }
   *         return nullptr;
   *     }
   * ```
   */
  public fun remove(key: K) {
    TODO("Implement remove")
  }

  /**
   * C++ original:
   * ```cpp
   * bool removeIfExists(const K& key) {
   *         uint32_t hash = Hash(key);
   *         int index = hash & (fCapacity-1);
   *         for (int n = 0; n < fCapacity; n++) {
   *             Slot& s = fSlots[index];
   *             if (s.empty()) {
   *                 return false;
   *             }
   *             if (hash == s.fHash && key == Traits::GetKey(*s)) {
   *                 this->removeSlot(index);
   *                 if (fCapacity > 4) {
   *                     bool shouldShrink = false;
   *                     if constexpr (HasShouldShrink<Traits>::value) {
   *                         shouldShrink = Traits::ShouldShrink(fCount, fCapacity);
   *                     } else {
   *                         shouldShrink = (4 * fCount <= fCapacity);
   *                     }
   *                     if (shouldShrink) {
   *                         this->resize(fCapacity / 2);
   *                     }
   *                 }
   *                 return true;
   *             }
   *             index = this->next(index);
   *         }
   *         SkASSERT(fCapacity == fCount);
   *         return false;
   *     }
   * ```
   */
  public fun resize(capacity: Int) {
    TODO("Implement resize")
  }

  /**
   * C++ original:
   * ```cpp
   * void remove(const K& key) {
   *         SkAssertResult(this->removeIfExists(key));
   *     }
   * ```
   */
  public fun reserve(n: Int) {
    TODO("Implement reserve")
  }

  /**
   * C++ original:
   * ```cpp
   * void resize(int capacity) {
   *         // We must have enough capacity to hold every key.
   *         SkASSERT(capacity >= fCount);
   *         // `capacity` must be a power of two, because we use `hash & (capacity-1)` to look up keys
   *         // in the table (since this is faster than a modulo).
   *         SkASSERT((capacity & (capacity - 1)) == 0);
   *
   *         int oldCapacity = fCapacity;
   *         SkDEBUGCODE(int oldCount = fCount);
   *
   *         fCount = 0;
   *         fCapacity = capacity;
   *         std::unique_ptr<Slot[]> oldSlots = std::move(fSlots);
   *         fSlots.reset(new Slot[capacity]);
   *
   *         for (int i = 0; i < oldCapacity; i++) {
   *             Slot& s = oldSlots[i];
   *             if (s.has_value()) {
   *                 this->uncheckedSet(*std::move(s));
   *             }
   *         }
   *         SkASSERT(fCount == oldCount);
   *     }
   * ```
   */
  public fun <Fn> foreach(param0: T) {
    TODO("Implement foreach")
  }

  /**
   * C++ original:
   * ```cpp
   * void reserve(int n) {
   *         int newCapacity = SkNextPow2(n);
   *
   *         bool shouldGrow = false;
   *         if constexpr (HasShouldGrow<Traits>::value) {
   *             shouldGrow = Traits::ShouldGrow(n, newCapacity);
   *         } else {
   *             shouldGrow = (n * 4 > newCapacity * 3);
   *         }
   *         if (shouldGrow) {
   *             newCapacity *= 2;
   *         }
   *
   *         if (newCapacity > fCapacity) {
   *             this->resize(newCapacity);
   *         }
   *     }
   * ```
   */
  private fun firstPopulatedSlot(): Int {
    TODO("Implement firstPopulatedSlot")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename Fn>  // f(T*)
   *     void foreach(Fn&& fn) {
   *         for (int i = 0; i < fCapacity; i++) {
   *             if (fSlots[i].has_value()) {
   *                 fn(&*fSlots[i]);
   *             }
   *         }
   *     }
   * ```
   */
  private fun nextPopulatedSlot(currentSlot: Int): Int {
    TODO("Implement nextPopulatedSlot")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename Fn>  // f(T) or f(const T&)
   *     void foreach(Fn&& fn) const {
   *         for (int i = 0; i < fCapacity; i++) {
   *             if (fSlots[i].has_value()) {
   *                 fn(*fSlots[i]);
   *             }
   *         }
   *     }
   * ```
   */
  private fun slot(i: Int): T {
    TODO("Implement slot")
  }

  /**
   * C++ original:
   * ```cpp
   * int firstPopulatedSlot() const {
   *         for (int i = 0; i < fCapacity; i++) {
   *             if (fSlots[i].has_value()) {
   *                 return i;
   *             }
   *         }
   *         return fCapacity;
   *     }
   * ```
   */
  private fun uncheckedSet(`val`: T): T {
    TODO("Implement uncheckedSet")
  }

  /**
   * C++ original:
   * ```cpp
   * int nextPopulatedSlot(int currentSlot) const {
   *         for (int i = currentSlot + 1; i < fCapacity; i++) {
   *             if (fSlots[i].has_value()) {
   *                 return i;
   *             }
   *         }
   *         return fCapacity;
   *     }
   * ```
   */
  private fun removeSlot(index: Int) {
    TODO("Implement removeSlot")
  }

  /**
   * C++ original:
   * ```cpp
   * const T* slot(int i) const {
   *         SkASSERT(fSlots[i].has_value());
   *         return &*fSlots[i];
   *     }
   * ```
   */
  private fun next(index: Int): Int {
    TODO("Implement next")
  }

  public open class Iter<SlotVal> public constructor(
    table: UndefinedIterTTable?,
    slot: Int,
  ) {
    protected val fTable: UndefinedIterTTable? = TODO("Initialize fTable")

    protected var fSlot: Int = TODO("Initialize fSlot")

    public fun `get`(): SlotVal {
      TODO("Implement get")
    }

    public override operator fun equals(other: Any?): Boolean {
      TODO("Implement equals")
    }

    public operator fun inc(): org.skia.core.Iter<SlotVal> {
      TODO("Implement inc")
    }

    public companion object {
      public fun makeBegin(table: IterTTable_?): org.skia.core.Iter<SlotVal> {
        TODO("Implement makeBegin")
      }

      public fun makeEnd(table: IterTTable_?): org.skia.core.Iter<SlotVal> {
        TODO("Implement makeEnd")
      }
    }
  }

  public open class HasShouldGrow<U, > : Boolean()

  public open class HasShouldShrink<U, > : Boolean()

  public data class Slot public constructor(
    public var fHash: Int,
    private var fVal: Any,
  ) {
    public fun assign(that: org.skia.sksl.Slot) {
      TODO("Implement assign")
    }

    public fun emplace(v: T, h: UInt): org.skia.sksl.Slot {
      TODO("Implement emplace")
    }

    public fun hasValue(): Boolean {
      TODO("Implement hasValue")
    }

    public fun empty(): Boolean {
      TODO("Implement empty")
    }

    public fun reset() {
      TODO("Implement reset")
    }

    public data class Storage<T> public constructor(
      private var fStorage: T,
    )
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static uint32_t Hash(const K& key) {
     *         uint32_t hash = Traits::Hash(key) & 0xffffffff;
     *         return hash ? hash : 1;  // We reserve hash 0 to mark empty.
     *     }
     * ```
     */
    private fun hash(key: Any): Int {
      TODO("Implement hash")
    }
  }
}
