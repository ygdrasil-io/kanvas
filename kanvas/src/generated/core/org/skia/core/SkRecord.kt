package org.skia.core

import kotlin.Any
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkRefCnt
import org.skia.memory.SkArenaAlloc

/**
 * C++ original:
 * ```cpp
 * class SkRecord : public SkRefCnt {
 * public:
 *     SkRecord() = default;
 *     ~SkRecord() override;
 *
 *     // Returns the number of canvas commands in this SkRecord.
 *     int count() const { return fCount; }
 *
 *     // Visit the i-th canvas command with a functor matching this interface:
 *     //   template <typename T>
 *     //   R operator()(const T& record) { ... }
 *     // This operator() must be defined for at least all SkRecords::*.
 *     template <typename F>
 *     auto visit(int i, F&& f) const -> decltype(f(SkRecords::NoOp())) {
 *         return fRecords[i].visit(f);
 *     }
 *
 *     // Mutate the i-th canvas command with a functor matching this interface:
 *     //   template <typename T>
 *     //   R operator()(T* record) { ... }
 *     // This operator() must be defined for at least all SkRecords::*.
 *     template <typename F>
 *     auto mutate(int i, F&& f) -> decltype(f((SkRecords::NoOp*)nullptr)) {
 *         return fRecords[i].mutate(f);
 *     }
 *
 *     // Allocate contiguous space for count Ts, to be freed when the SkRecord is destroyed.
 *     // Here T can be any class, not just those from SkRecords.  Throws on failure.
 *     template <typename T>
 *     T* alloc(size_t count = 1) {
 *         struct RawBytes {
 *             alignas(T) char data[sizeof(T)];
 *         };
 *         fApproxBytesAllocated += count * sizeof(T) + alignof(T);
 *         return (T*)fAlloc.makeArrayDefault<RawBytes>(count);
 *     }
 *
 *     // Add a new command of type T to the end of this SkRecord.
 *     // You are expected to placement new an object of type T onto this pointer.
 *     template <typename T>
 *     T* append() {
 *         if (fCount == fReserved) {
 *             this->grow();
 *         }
 *         return fRecords[fCount++].set(this->allocCommand<T>());
 *     }
 *
 *     // Replace the i-th command with a new command of type T.
 *     // You are expected to placement new an object of type T onto this pointer.
 *     // References to the original command are invalidated.
 *     template <typename T>
 *     T* replace(int i) {
 *         SkASSERT(i < this->count());
 *
 *         Destroyer destroyer;
 *         this->mutate(i, destroyer);
 *
 *         return fRecords[i].set(this->allocCommand<T>());
 *     }
 *
 *     // Does not return the bytes in any pointers embedded in the Records; callers
 *     // need to iterate with a visitor to measure those they care for.
 *     size_t bytesUsed() const;
 *
 *     // Rearrange and resize this record to eliminate any NoOps.
 *     // May change count() and the indices of ops, but preserves their order.
 *     void defrag();
 *
 * private:
 *     // An SkRecord is structured as an array of pointers into a big chunk of memory where
 *     // records representing each canvas draw call are stored:
 *     //
 *     // fRecords:  [*][*][*]...
 *     //             |  |  |
 *     //             |  |  |
 *     //             |  |  +---------------------------------------+
 *     //             |  +-----------------+                        |
 *     //             |                    |                        |
 *     //             v                    v                        v
 *     //   fAlloc:  [SkRecords::DrawRect][SkRecords::DrawPosTextH][SkRecords::DrawRect]...
 *     //
 *     // We store the types of each of the pointers alongside the pointer.
 *     // The cost to append a T to this structure is 8 + sizeof(T) bytes.
 *
 *     // A mutator that can be used with replace to destroy canvas commands.
 *     struct Destroyer {
 *         template <typename T>
 *         void operator()(T* record) { record->~T(); }
 *     };
 *
 *     template <typename T>
 *     std::enable_if_t<std::is_empty<T>::value, T*> allocCommand() {
 *         static T singleton = {};
 *         return &singleton;
 *     }
 *
 *     template <typename T>
 *     std::enable_if_t<!std::is_empty<T>::value, T*> allocCommand() { return this->alloc<T>(); }
 *
 *     void grow();
 *
 *     // A typed pointer to some bytes in fAlloc.  visit() and mutate() allow polymorphic dispatch.
 *     struct Record {
 *         SkRecords::Type fType;
 *         void*           fPtr;
 *
 *         // Point this record to its data in fAlloc.  Returns ptr for convenience.
 *         template <typename T>
 *         T* set(T* ptr) {
 *             fType = T::kType;
 *             fPtr  = ptr;
 *             SkASSERT(this->ptr() == ptr && this->type() == T::kType);
 *             return ptr;
 *         }
 *
 *         SkRecords::Type type() const { return fType; }
 *         void* ptr() const { return fPtr; }
 *
 *         // Visit this record with functor F (see public API above).
 *         template <typename F>
 *         auto visit(F&& f) const -> decltype(f(SkRecords::NoOp())) {
 *         #define CASE(T) case SkRecords::T##_Type: return f(*(const SkRecords::T*)this->ptr());
 *             switch(this->type()) { SK_RECORD_TYPES(CASE) }
 *         #undef CASE
 *             SkDEBUGFAIL("Unreachable");
 *             static const SkRecords::NoOp noop{};
 *             return f(noop);
 *         }
 *
 *         // Mutate this record with functor F (see public API above).
 *         template <typename F>
 *         auto mutate(F&& f) -> decltype(f((SkRecords::NoOp*)nullptr)) {
 *         #define CASE(T) case SkRecords::T##_Type: return f((SkRecords::T*)this->ptr());
 *             switch(this->type()) { SK_RECORD_TYPES(CASE) }
 *         #undef CASE
 *             SkDEBUGFAIL("Unreachable");
 *             static const SkRecords::NoOp noop{};
 *             return f(const_cast<SkRecords::NoOp*>(&noop));
 *         }
 *     };
 *
 *     // fRecords needs to be a data structure that can append fixed length data, and need to
 *     // support efficient random access and forward iteration.  (It doesn't need to be contiguous.)
 *     int fCount{0},
 *         fReserved{0};
 *     skia_private::AutoTMalloc<Record> fRecords;
 *
 *     // fAlloc needs to be a data structure which can append variable length data in contiguous
 *     // chunks, returning a stable handle to that data for later retrieval.
 *     SkArenaAlloc fAlloc{256};
 *     size_t       fApproxBytesAllocated{0};
 * }
 * ```
 */
public open class SkRecord public constructor() : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   *     template <typename T>
   *     std::enable_if_t<std::is_empty<T>::value, T
   * ```
   */
  private var fCount: Int = TODO("Initialize fCount")

  /**
   * C++ original:
   * ```cpp
   * int fCount{0}
   * ```
   */
  private var fReserved: Int = TODO("Initialize fReserved")

  /**
   * C++ original:
   * ```cpp
   * int fCount{0},
   *         fReserved{0}
   * ```
   */
  private var fRecords: Int = TODO("Initialize fRecords")

  /**
   * C++ original:
   * ```cpp
   * skia_private::AutoTMalloc<Record> fRecords
   * ```
   */
  private var fAlloc: SkArenaAlloc = TODO("Initialize fAlloc")

  /**
   * C++ original:
   * ```cpp
   * SkArenaAlloc fAlloc
   * ```
   */
  private var fApproxBytesAllocated: ULong = TODO("Initialize fApproxBytesAllocated")

  /**
   * C++ original:
   * ```cpp
   * int count() const { return fCount; }
   * ```
   */
  public fun count(): Int {
    TODO("Implement count")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename F>
   *     auto visit(int i, F&& f) const -> decltype(f(SkRecords::NoOp())) {
   *         return fRecords[i].visit(f);
   *     }
   * ```
   */
  public fun <F> visit(i: Int, f: F): Any {
    TODO("Implement visit")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename F>
   *     auto mutate(int i, F&& f) -> decltype(f((SkRecords::NoOp*)nullptr)) {
   *         return fRecords[i].mutate(f);
   *     }
   * ```
   */
  public fun <F> mutate(i: Int, f: F): Any {
    TODO("Implement mutate")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename T>
   *     T* alloc(size_t count = 1) {
   *         struct RawBytes {
   *             alignas(T) char data[sizeof(T)];
   *         };
   *         fApproxBytesAllocated += count * sizeof(T) + alignof(T);
   *         return (T*)fAlloc.makeArrayDefault<RawBytes>(count);
   *     }
   * ```
   */
  public fun <T> alloc(count: ULong = 1u): T {
    TODO("Implement alloc")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename T>
   *     T* append() {
   *         if (fCount == fReserved) {
   *             this->grow();
   *         }
   *         return fRecords[fCount++].set(this->allocCommand<T>());
   *     }
   * ```
   */
  public fun <T> append(): T {
    TODO("Implement append")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename T>
   *     T* replace(int i) {
   *         SkASSERT(i < this->count());
   *
   *         Destroyer destroyer;
   *         this->mutate(i, destroyer);
   *
   *         return fRecords[i].set(this->allocCommand<T>());
   *     }
   * ```
   */
  public fun <T> replace(i: Int): T {
    TODO("Implement replace")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkRecord::bytesUsed() const {
   *     size_t bytes = fApproxBytesAllocated + sizeof(SkRecord);
   *     return bytes;
   * }
   * ```
   */
  public fun bytesUsed(): ULong {
    TODO("Implement bytesUsed")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRecord::defrag() {
   *     // Remove all the NoOps, preserving the order of other ops, e.g.
   *     //      Save, ClipRect, NoOp, DrawRect, NoOp, NoOp, Restore
   *     //  ->  Save, ClipRect, DrawRect, Restore
   *     Record* noops = std::remove_if(fRecords.get(), fRecords.get() + fCount,
   *                                    [](Record op) { return op.type() == SkRecords::NoOp_Type; });
   *     fCount = noops - fRecords.get();
   * }
   * ```
   */
  public fun defrag() {
    TODO("Implement defrag")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRecord::grow() {
   *     SkASSERT(fCount == fReserved);
   *     fReserved = fReserved ? fReserved * 2 : 4;
   *     fRecords.realloc(fReserved);
   * }
   * ```
   */
  public fun grow() {
    TODO("Implement grow")
  }

  public open class Destroyer {
    public operator fun <T> invoke(record: T?) {
      TODO("Implement invoke")
    }
  }

  public open class Record public constructor(
    public var fType: Type,
    public var fPtr: Unit?,
  ) {
    public fun <T> `set`(ptr: T?): T {
      TODO("Implement set")
    }

    public fun type(): Type {
      TODO("Implement type")
    }

    public fun ptr() {
      TODO("Implement ptr")
    }

    public fun <F> visit(f: F): Any {
      TODO("Implement visit")
    }

    public fun <F> mutate(f: F): Any {
      TODO("Implement mutate")
    }
  }

  public companion object {
    private var t: Int = TODO("Initialize t")
  }
}
