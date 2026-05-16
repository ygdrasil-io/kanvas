package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import undefined.SubRunOwner

/**
 * C++ original:
 * ```cpp
 * class SubRunList {
 * public:
 *     class Iterator {
 *     public:
 *         using value_type = SubRun;
 *         using difference_type = ptrdiff_t;
 *         using pointer = value_type*;
 *         using reference = value_type&;
 *         using iterator_category = std::input_iterator_tag;
 *         Iterator(SubRun* subRun) : fPtr{subRun} { }
 *         Iterator& operator++() { fPtr = fPtr->fNext.get(); return *this; }
 *         Iterator operator++(int) { Iterator tmp(*this); operator++(); return tmp; }
 *         bool operator==(const Iterator& rhs) const { return fPtr == rhs.fPtr; }
 *         bool operator!=(const Iterator& rhs) const { return fPtr != rhs.fPtr; }
 *         reference operator*() { return *fPtr; }
 *
 *     private:
 *         SubRun* fPtr;
 *     };
 *
 *     void append(SubRunOwner subRun) {
 *         SubRunOwner* newTail = &subRun->fNext;
 *         *fTail = std::move(subRun);
 *         fTail = newTail;
 *     }
 *     bool isEmpty() const { return fHead == nullptr; }
 *     Iterator begin() { return Iterator{ fHead.get()}; }
 *     Iterator end() { return Iterator{nullptr}; }
 *     Iterator begin() const { return Iterator{ fHead.get()}; }
 *     Iterator end() const { return Iterator{nullptr}; }
 *     SubRun& front() const {return *fHead; }
 *
 * private:
 *     SubRunOwner fHead{nullptr};
 *     SubRunOwner* fTail{&fHead};
 * }
 * ```
 */
public data class SubRunList public constructor(
  /**
   * C++ original:
   * ```cpp
   * SubRunOwner fHead
   * ```
   */
  private var fHead: Int,
  /**
   * C++ original:
   * ```cpp
   * SubRunOwner* fTail
   * ```
   */
  private var fTail: Int?,
) {
  /**
   * C++ original:
   * ```cpp
   * void append(SubRunOwner subRun) {
   *         SubRunOwner* newTail = &subRun->fNext;
   *         *fTail = std::move(subRun);
   *         fTail = newTail;
   *     }
   * ```
   */
  private fun append(subRun: SubRunOwner) {
    TODO("Implement append")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isEmpty() const { return fHead == nullptr; }
   * ```
   */
  private fun isEmpty(): Boolean {
    TODO("Implement isEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * Iterator begin() { return Iterator{ fHead.get()}; }
   * ```
   */
  private fun begin(): Iterator {
    TODO("Implement begin")
  }

  /**
   * C++ original:
   * ```cpp
   * Iterator end() { return Iterator{nullptr}; }
   * ```
   */
  private fun end(): Iterator {
    TODO("Implement end")
  }

  /**
   * C++ original:
   * ```cpp
   * Iterator begin() const { return Iterator{ fHead.get()}; }
   * ```
   */
  private fun front(): SubRun {
    TODO("Implement front")
  }

  public data class Iterator public constructor(
    private var fPtr: SubRun?,
  ) {
    public operator fun inc(): undefined.Iterator {
      TODO("Implement inc")
    }

    public override operator fun equals(other: Any?): Boolean {
      TODO("Implement equals")
    }
  }
}
