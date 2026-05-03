package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import org.skia.memory.SkArenaAlloc

/**
 * C++ original:
 * ```cpp
 * class IntersectionTree {
 * public:
 *     enum class SplitType : bool {
 *         kX,
 *         kY
 *     };
 *
 *     IntersectionTree();
 *
 *     bool add(Rect rect) {
 *         if (rect.isEmptyNegativeOrNaN()) {
 *             // Empty and undefined rects can simply pass without modifying the tree.
 *             return true;
 *         }
 *         if (!fRoot->intersects(rect)) {
 *             fRoot = fRoot->addNonIntersecting(rect, &fArena);
 *             return true;
 *         }
 *         return false;
 *     }
 *
 * private:
 *     class Node {
 *     public:
 *         virtual ~Node() = default;
 *
 *         virtual bool intersects(Rect) = 0;
 *         virtual Node* addNonIntersecting(Rect, SkArenaAlloc*) = 0;
 *     };
 *
 *     template<SplitType kSplitType> class TreeNode;
 *     class LeafNode;
 *
 *     // The TreeNode size is made of a vtable (i.e. sizeof(void*)), float, and two Node* pointers.
 *     // We also align between the Node* and the float which may add some extra padding.
 *     constexpr static int kTreeNodeSize = SkAlignTo(sizeof(void*) + sizeof(float), alignof(void*)) +
 *                                          2 * sizeof(Node*);
 *     constexpr static int kLeafNodeSize = 16 + (2 + 64) * sizeof(Rect);
 *     constexpr static int kPadSize = 256;  // For footers and alignment.
 *     SkArenaAlloc fArena{kLeafNodeSize + kTreeNodeSize + kPadSize*2};
 *     Node* fRoot;
 * }
 * ```
 */
public data class IntersectionTree public constructor(
  /**
   * C++ original:
   * ```cpp
   * constexpr static int kTreeNodeSize = SkAlignTo(sizeof(void*) + sizeof(float), alignof(void*)) +
   *                                          2 * sizeof(Node*)
   * ```
   */
  public var fArena: Int,
  /**
   * C++ original:
   * ```cpp
   * constexpr static int kLeafNodeSize
   * ```
   */
  public var fRoot: Node?,
) {
  /**
   * C++ original:
   * ```cpp
   * bool add(Rect rect) {
   *         if (rect.isEmptyNegativeOrNaN()) {
   *             // Empty and undefined rects can simply pass without modifying the tree.
   *             return true;
   *         }
   *         if (!fRoot->intersects(rect)) {
   *             fRoot = fRoot->addNonIntersecting(rect, &fArena);
   *             return true;
   *         }
   *         return false;
   *     }
   * ```
   */
  public fun add(rect: Rect): Boolean {
    TODO("Implement add")
  }

  public abstract class Node {
    public abstract fun intersects(param0: Rect): Boolean

    public abstract fun addNonIntersecting(param0: Rect, param1: SkArenaAlloc?): org.skia.modules.Node
  }

  public enum class SplitType {
    kX,
    kY,
  }

  public companion object {
    public val kTreeNodeSize: Int = TODO("Initialize kTreeNodeSize")

    public val kLeafNodeSize: Int = TODO("Initialize kLeafNodeSize")

    public val kPadSize: Int = TODO("Initialize kPadSize")
  }
}
