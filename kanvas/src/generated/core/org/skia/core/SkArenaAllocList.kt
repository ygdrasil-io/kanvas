package org.skia.core

import kotlin.Any
import kotlin.Boolean
import org.skia.memory.SkArenaAlloc
import undefined.Args

/**
 * C++ original:
 * ```cpp
 * template <typename T>
 * class SkArenaAllocList {
 * private:
 *     struct Node;
 *
 * public:
 *     SkArenaAllocList() = default;
 *
 *     void reset() { fHead = fTail = nullptr; }
 *
 *     template <typename... Args>
 *     inline T& append(SkArenaAlloc* arena, Args... args);
 *
 *     class Iter {
 *     public:
 *         Iter() = default;
 *         inline Iter& operator++();
 *         T& operator*() const { return fCurr->fT; }
 *         T* operator->() const { return &fCurr->fT; }
 *         bool operator==(const Iter& that) const { return fCurr == that.fCurr; }
 *         bool operator!=(const Iter& that) const { return !(*this == that); }
 *
 *     private:
 *         friend class SkArenaAllocList;
 *         explicit Iter(Node* node) : fCurr(node) {}
 *         Node* fCurr = nullptr;
 *     };
 *
 *     Iter begin() { return Iter(fHead); }
 *     Iter end() { return Iter(); }
 *     Iter tail() { return Iter(fTail); }
 *
 * private:
 *     struct Node {
 *         template <typename... Args>
 *         Node(Args... args) : fT(std::forward<Args>(args)...) {}
 *         T fT;
 *         Node* fNext = nullptr;
 *     };
 *     Node* fHead = nullptr;
 *     Node* fTail = nullptr;
 * }
 * ```
 */
public data class SkArenaAllocList<T> public constructor(
  /**
   * C++ original:
   * ```cpp
   * Node* fHead = nullptr
   * ```
   */
  private var fHead: Node?,
  /**
   * C++ original:
   * ```cpp
   * Node* fTail = nullptr
   * ```
   */
  private var fTail: Node?,
) {
  /**
   * C++ original:
   * ```cpp
   * void reset() { fHead = fTail = nullptr; }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * template <typename T>
   * template <typename... Args>
   * T& SkArenaAllocList<T>::append(SkArenaAlloc* arena, Args... args) {
   *     SkASSERT(!fHead == !fTail);
   *     auto* n = arena->make<Node>(std::forward<Args>(args)...);
   *     if (!fTail) {
   *         fHead = fTail = n;
   *     } else {
   *         fTail = fTail->fNext = n;
   *     }
   *     return fTail->fT;
   * }
   * ```
   */
  public fun <Args> append(arena: SkArenaAlloc?, args: Args): T {
    TODO("Implement append")
  }

  /**
   * C++ original:
   * ```cpp
   * Iter begin() { return Iter(fHead); }
   * ```
   */
  private fun begin(): Iter {
    TODO("Implement begin")
  }

  /**
   * C++ original:
   * ```cpp
   * Iter end() { return Iter(); }
   * ```
   */
  private fun end(): Iter {
    TODO("Implement end")
  }

  /**
   * C++ original:
   * ```cpp
   * Iter tail() { return Iter(fTail); }
   * ```
   */
  private fun tail(): Iter {
    TODO("Implement tail")
  }

  public open class Iter public constructor() {
    private var fCurr: org.skia.modules.Node? = TODO("Initialize fCurr")

    public constructor(node: org.skia.modules.Node?) : this() {
      TODO("Implement constructor")
    }

    public operator fun inc(): org.skia.core.Iter {
      TODO("Implement inc")
    }

    public operator fun times(): T {
      TODO("Implement times")
    }

    public fun `get`(): T {
      TODO("Implement get")
    }

    public override operator fun equals(other: Any?): Boolean {
      TODO("Implement equals")
    }
  }

  public open class Node<T> public constructor(
    public var fT: T,
    public var fNext: org.skia.modules.Node?,
  ) {
    public constructor(args: Args) : this() {
      TODO("Implement constructor")
    }
  }
}
