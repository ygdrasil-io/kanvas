package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.ULong
import kotlin.Unit

/**
 * C++ original:
 * ```cpp
 * class SK_API SkDeque {
 * public:
 *     /**
 *      * elemSize specifies the size of each individual element in the deque
 *      * allocCount specifies how many elements are to be allocated as a block
 *      */
 *     explicit SkDeque(size_t elemSize, int allocCount = 1);
 *     SkDeque(size_t elemSize, void* storage, size_t storageSize, int allocCount = 1);
 *     ~SkDeque();
 *
 *     bool    empty() const { return 0 == fCount; }
 *     int     count() const { return fCount; }
 *     size_t  elemSize() const { return fElemSize; }
 *
 *     const void* front() const { return fFront; }
 *     const void* back() const  { return fBack; }
 *
 *     void* front() { return fFront; }
 *     void* back() { return fBack; }
 *
 *     /**
 *      * push_front and push_back return a pointer to the memory space
 *      * for the new element
 *      */
 *     void* push_front();
 *     void* push_back();
 *
 *     void pop_front();
 *     void pop_back();
 *
 * private:
 *     struct Block;
 *
 * public:
 *     class Iter {
 *     public:
 *         enum IterStart {
 *             kFront_IterStart,
 *             kBack_IterStart,
 *         };
 *
 *         /**
 *          * Creates an uninitialized iterator. Must be reset()
 *          */
 *         Iter();
 *
 *         Iter(const SkDeque& d, IterStart startLoc);
 *         void* next();
 *         void* prev();
 *
 *         void reset(const SkDeque& d, IterStart startLoc);
 *
 *     private:
 *         SkDeque::Block* fCurBlock;
 *         char*           fPos;
 *         size_t          fElemSize;
 *     };
 *
 *     // Inherit privately from Iter to prevent access to reverse iteration
 *     class F2BIter : private Iter {
 *     public:
 *         F2BIter() {}
 *
 *         /**
 *          * Wrap Iter's 2 parameter ctor to force initialization to the
 *          * beginning of the deque
 *          */
 *         F2BIter(const SkDeque& d) : INHERITED(d, kFront_IterStart) {}
 *
 *         using Iter::next;
 *
 *         /**
 *          * Wrap Iter::reset to force initialization to the beginning of the
 *          * deque
 *          */
 *         void reset(const SkDeque& d) {
 *             this->INHERITED::reset(d, kFront_IterStart);
 *         }
 *
 *     private:
 *         using INHERITED = Iter;
 *     };
 *
 * private:
 *     // allow unit test to call numBlocksAllocated
 *     friend class DequeUnitTestHelper;
 *
 *     void*   fFront;
 *     void*   fBack;
 *
 *     Block*  fFrontBlock;
 *     Block*  fBackBlock;
 *     size_t  fElemSize;
 *     void*   fInitialStorage;
 *     int     fCount;             // number of elements in the deque
 *     int     fAllocCount;        // number of elements to allocate per block
 *
 *     Block*  allocateBlock(int allocCount);
 *     void    freeBlock(Block* block);
 *
 *     /**
 *      * This returns the number of chunk blocks allocated by the deque. It
 *      * can be used to gauge the effectiveness of the selected allocCount.
 *      */
 *     int  numBlocksAllocated() const;
 *
 *     SkDeque(const SkDeque&) = delete;
 *     SkDeque& operator=(const SkDeque&) = delete;
 * }
 * ```
 */
public data class SkDeque public constructor(
  /**
   * C++ original:
   * ```cpp
   * void*   fFront
   * ```
   */
  private var fFront: Unit?,
  /**
   * C++ original:
   * ```cpp
   * void*   fBack
   * ```
   */
  private var fBack: Unit?,
  /**
   * C++ original:
   * ```cpp
   * Block*  fFrontBlock
   * ```
   */
  private var fFrontBlock: org.skia.modules.Block?,
  /**
   * C++ original:
   * ```cpp
   * Block*  fBackBlock
   * ```
   */
  private var fBackBlock: org.skia.modules.Block?,
  /**
   * C++ original:
   * ```cpp
   * size_t  fElemSize
   * ```
   */
  private var fElemSize: ULong,
  /**
   * C++ original:
   * ```cpp
   * void*   fInitialStorage
   * ```
   */
  private var fInitialStorage: Unit?,
  /**
   * C++ original:
   * ```cpp
   * int     fCount
   * ```
   */
  private var fCount: Int,
  /**
   * C++ original:
   * ```cpp
   * int     fAllocCount
   * ```
   */
  private var fAllocCount: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool    empty() const { return 0 == fCount; }
   * ```
   */
  public fun empty(): Boolean {
    TODO("Implement empty")
  }

  /**
   * C++ original:
   * ```cpp
   * int     count() const { return fCount; }
   * ```
   */
  public fun count(): Int {
    TODO("Implement count")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t  elemSize() const { return fElemSize; }
   * ```
   */
  public fun elemSize(): ULong {
    TODO("Implement elemSize")
  }

  /**
   * C++ original:
   * ```cpp
   * const void* front() const { return fFront; }
   * ```
   */
  public fun front() {
    TODO("Implement front")
  }

  /**
   * C++ original:
   * ```cpp
   * const void* back() const  { return fBack; }
   * ```
   */
  public fun back() {
    TODO("Implement back")
  }

  /**
   * C++ original:
   * ```cpp
   * void* front() { return fFront; }
   * ```
   */
  public fun pushFront() {
    TODO("Implement pushFront")
  }

  /**
   * C++ original:
   * ```cpp
   * void* back() { return fBack; }
   * ```
   */
  public fun pushBack() {
    TODO("Implement pushBack")
  }

  /**
   * C++ original:
   * ```cpp
   * void* SkDeque::push_front() {
   *     fCount += 1;
   *
   *     if (nullptr == fFrontBlock) {
   *         fFrontBlock = this->allocateBlock(fAllocCount);
   *         fBackBlock = fFrontBlock;     // update our linklist
   *     }
   *
   *     Block*  first = fFrontBlock;
   *     char*   begin;
   *
   *     if (nullptr == first->fBegin) {
   *     INIT_CHUNK:
   *         first->fEnd = first->fStop;
   *         begin = first->fStop - fElemSize;
   *     } else {
   *         begin = first->fBegin - fElemSize;
   *         if (begin < first->start()) {    // no more room in this chunk
   *             // should we alloc more as we accumulate more elements?
   *             first = this->allocateBlock(fAllocCount);
   *             first->fNext = fFrontBlock;
   *             fFrontBlock->fPrev = first;
   *             fFrontBlock = first;
   *             goto INIT_CHUNK;
   *         }
   *     }
   *
   *     first->fBegin = begin;
   *
   *     if (nullptr == fFront) {
   *         SkASSERT(nullptr == fBack);
   *         fFront = fBack = begin;
   *     } else {
   *         SkASSERT(fBack);
   *         fFront = begin;
   *     }
   *
   *     return begin;
   * }
   * ```
   */
  public fun popFront() {
    TODO("Implement popFront")
  }

  /**
   * C++ original:
   * ```cpp
   * void* SkDeque::push_back() {
   *     fCount += 1;
   *
   *     if (nullptr == fBackBlock) {
   *         fBackBlock = this->allocateBlock(fAllocCount);
   *         fFrontBlock = fBackBlock; // update our linklist
   *     }
   *
   *     Block*  last = fBackBlock;
   *     char*   end;
   *
   *     if (nullptr == last->fBegin) {
   *     INIT_CHUNK:
   *         last->fBegin = last->start();
   *         end = last->fBegin + fElemSize;
   *     } else {
   *         end = last->fEnd + fElemSize;
   *         if (end > last->fStop) {  // no more room in this chunk
   *             // should we alloc more as we accumulate more elements?
   *             last = this->allocateBlock(fAllocCount);
   *             last->fPrev = fBackBlock;
   *             fBackBlock->fNext = last;
   *             fBackBlock = last;
   *             goto INIT_CHUNK;
   *         }
   *     }
   *
   *     last->fEnd = end;
   *     end -= fElemSize;
   *
   *     if (nullptr == fBack) {
   *         SkASSERT(nullptr == fFront);
   *         fFront = fBack = end;
   *     } else {
   *         SkASSERT(fFront);
   *         fBack = end;
   *     }
   *
   *     return end;
   * }
   * ```
   */
  public fun popBack() {
    TODO("Implement popBack")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDeque::pop_front() {
   *     SkASSERT(fCount > 0);
   *     fCount -= 1;
   *
   *     Block*  first = fFrontBlock;
   *
   *     SkASSERT(first != nullptr);
   *
   *     if (first->fBegin == nullptr) {  // we were marked empty from before
   *         first = first->fNext;
   *         SkASSERT(first != nullptr);    // else we popped too far
   *         first->fPrev = nullptr;
   *         this->freeBlock(fFrontBlock);
   *         fFrontBlock = first;
   *     }
   *
   *     char* begin = first->fBegin + fElemSize;
   *     SkASSERT(begin <= first->fEnd);
   *
   *     if (begin < fFrontBlock->fEnd) {
   *         first->fBegin = begin;
   *         SkASSERT(first->fBegin);
   *         fFront = first->fBegin;
   *     } else {
   *         first->fBegin = first->fEnd = nullptr;  // mark as empty
   *         if (nullptr == first->fNext) {
   *             fFront = fBack = nullptr;
   *         } else {
   *             SkASSERT(first->fNext->fBegin);
   *             fFront = first->fNext->fBegin;
   *         }
   *     }
   * }
   * ```
   */
  private fun allocateBlock(allocCount: Int): org.skia.modules.Block {
    TODO("Implement allocateBlock")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDeque::pop_back() {
   *     SkASSERT(fCount > 0);
   *     fCount -= 1;
   *
   *     Block* last = fBackBlock;
   *
   *     SkASSERT(last != nullptr);
   *
   *     if (last->fEnd == nullptr) {  // we were marked empty from before
   *         last = last->fPrev;
   *         SkASSERT(last != nullptr);  // else we popped too far
   *         last->fNext = nullptr;
   *         this->freeBlock(fBackBlock);
   *         fBackBlock = last;
   *     }
   *
   *     char* end = last->fEnd - fElemSize;
   *     SkASSERT(end >= last->fBegin);
   *
   *     if (end > last->fBegin) {
   *         last->fEnd = end;
   *         SkASSERT(last->fEnd);
   *         fBack = last->fEnd - fElemSize;
   *     } else {
   *         last->fBegin = last->fEnd = nullptr;    // mark as empty
   *         if (nullptr == last->fPrev) {
   *             fFront = fBack = nullptr;
   *         } else {
   *             SkASSERT(last->fPrev->fEnd);
   *             fBack = last->fPrev->fEnd - fElemSize;
   *         }
   *     }
   * }
   * ```
   */
  private fun freeBlock(block: org.skia.modules.Block?) {
    TODO("Implement freeBlock")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDeque::Block* SkDeque::allocateBlock(int allocCount) {
   *     Block* newBlock = (Block*)sk_malloc_throw(sizeof(Block) + allocCount * fElemSize);
   *     newBlock->init(sizeof(Block) + allocCount * fElemSize);
   *     return newBlock;
   * }
   * ```
   */
  private fun numBlocksAllocated(): Int {
    TODO("Implement numBlocksAllocated")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDeque::freeBlock(Block* block) {
   *     sk_free(block);
   * }
   * ```
   */
  private fun assign(param0: SkDeque) {
    TODO("Implement assign")
  }

  public open class Iter public constructor() {
    private var fCurBlock: Block? = TODO("Initialize fCurBlock")

    private var fPos: String? = TODO("Initialize fPos")

    private var fElemSize: ULong = TODO("Initialize fElemSize")

    public constructor(d: SkDeque, startLoc: org.skia.core.Iter.IterStart) : this() {
      TODO("Implement constructor")
    }

    public fun next() {
      TODO("Implement next")
    }

    public fun prev() {
      TODO("Implement prev")
    }

    public fun reset(d: SkDeque, startLoc: org.skia.core.Iter.IterStart) {
      TODO("Implement reset")
    }

    public enum class IterStart {
      kFront_IterStart,
      kBack_IterStart,
    }
  }

  public open class F2BIter public constructor() : org.skia.core.Iter() {
    public constructor(d: SkDeque) : super(TODO(), TODO()) {
      TODO("Implement constructor")
    }

    public fun reset(d: SkDeque) {
      TODO("Implement reset")
    }
  }
}
