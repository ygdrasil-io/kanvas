package org.skia.core

import kotlin.Array
import kotlin.Int
import kotlin.UShort
import kotlin.collections.List
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SkRTree : public SkBBoxHierarchy {
 * public:
 *     SkRTree();
 *
 *     void insert(const SkRect[], int N) override;
 *     void search(const SkRect& query, std::vector<int>* results) const override;
 *     size_t bytesUsed() const override;
 *
 *     // Methods and constants below here are only public for tests.
 *
 *     // Return the depth of the tree structure.
 *     int getDepth() const { return fCount ? fRoot.fSubtree->fLevel + 1 : 0; }
 *     // Insertion count (not overall node count, which may be greater).
 *     int getCount() const { return fCount; }
 *
 *     // These values were empirically determined to produce reasonable performance in most cases.
 *     static const int kMinChildren = 6,
 *                      kMaxChildren = 11;
 *
 * private:
 *     struct Node;
 *
 *     struct Branch {
 *         union {
 *             Node* fSubtree;
 *             int fOpIndex;
 *         };
 *         SkRect fBounds;
 *     };
 *
 *     struct Node {
 *         uint16_t fNumChildren;
 *         uint16_t fLevel;
 *         Branch fChildren[kMaxChildren];
 *     };
 *
 *     void search(Node* root, const SkRect& query, std::vector<int>* results) const;
 *
 *     // Consumes the input array.
 *     Branch bulkLoad(std::vector<Branch>* branches, int level = 0);
 *
 *     // How many times will bulkLoad() call allocateNodeAtLevel()?
 *     static int CountNodes(int branches);
 *
 *     Node* allocateNodeAtLevel(uint16_t level);
 *
 *     // This is the count of data elements (rather than total nodes in the tree)
 *     int fCount;
 *     Branch fRoot;
 *     std::vector<Node> fNodes;
 * }
 * ```
 */
public abstract class SkRTree public constructor() : SkBBoxHierarchy() {
  /**
   * C++ original:
   * ```cpp
   * static const int kMinChildren = 6
   * ```
   */
  private var fCount: Int = TODO("Initialize fCount")

  /**
   * C++ original:
   * ```cpp
   * static const int kMinChildren = 6,
   *                      kMaxChildren = 11
   * ```
   */
  private var fRoot: Branch = TODO("Initialize fRoot")

  /**
   * C++ original:
   * ```cpp
   * int fCount
   * ```
   */
  private var fNodes: Int = TODO("Initialize fNodes")

  /**
   * C++ original:
   * ```cpp
   * void SkRTree::insert(const SkRect boundsArray[], int N) {
   *     SkASSERT(0 == fCount);
   *
   *     std::vector<Branch> branches;
   *     branches.reserve(N);
   *
   *     for (int i = 0; i < N; i++) {
   *         const SkRect& bounds = boundsArray[i];
   *         if (bounds.isEmpty()) {
   *             continue;
   *         }
   *
   *         Branch b;
   *         b.fBounds = bounds;
   *         b.fOpIndex = i;
   *         branches.push_back(b);
   *     }
   *
   *     fCount = (int)branches.size();
   *     if (fCount) {
   *         if (1 == fCount) {
   *             fNodes.reserve(1);
   *             Node* n = this->allocateNodeAtLevel(0);
   *             n->fNumChildren = 1;
   *             n->fChildren[0] = branches[0];
   *             fRoot.fSubtree = n;
   *             fRoot.fBounds  = branches[0].fBounds;
   *         } else {
   *             fNodes.reserve(CountNodes(fCount));
   *             fRoot = this->bulkLoad(&branches);
   *         }
   *     }
   * }
   * ```
   */
  public override fun insert(boundsArray: Array<SkRect>, n: Int) {
    TODO("Implement insert")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRTree::search(const SkRect& query, std::vector<int>* results) const {
   *     if (fCount > 0 && SkRect::Intersects(fRoot.fBounds, query)) {
   *         this->search(fRoot.fSubtree, query, results);
   *     }
   * }
   * ```
   */
  public override fun search(query: SkRect, results: List<Int>?) {
    TODO("Implement search")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkRTree::bytesUsed() const {
   *     size_t byteCount = sizeof(SkRTree);
   *
   *     byteCount += fNodes.capacity() * sizeof(Node);
   *
   *     return byteCount;
   * }
   * ```
   */
  public override fun bytesUsed(): Int {
    TODO("Implement bytesUsed")
  }

  /**
   * C++ original:
   * ```cpp
   * int getDepth() const { return fCount ? fRoot.fSubtree->fLevel + 1 : 0; }
   * ```
   */
  public fun getDepth(): Int {
    TODO("Implement getDepth")
  }

  /**
   * C++ original:
   * ```cpp
   * int getCount() const { return fCount; }
   * ```
   */
  public fun getCount(): Int {
    TODO("Implement getCount")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRTree::search(Node* node, const SkRect& query, std::vector<int>* results) const {
   *     for (int i = 0; i < node->fNumChildren; ++i) {
   *         if (SkRect::Intersects(node->fChildren[i].fBounds, query)) {
   *             if (0 == node->fLevel) {
   *                 results->push_back(node->fChildren[i].fOpIndex);
   *             } else {
   *                 this->search(node->fChildren[i].fSubtree, query, results);
   *             }
   *         }
   *     }
   * }
   * ```
   */
  private fun search(
    root: Node?,
    query: SkRect,
    results: List<Int>?,
  ) {
    TODO("Implement search")
  }

  /**
   * C++ original:
   * ```cpp
   * Branch bulkLoad(std::vector<Branch>* branches, int level = 0)
   * ```
   */
  private abstract fun bulkLoad(branches: List<Branch>?, level: Int = TODO()): Branch

  /**
   * C++ original:
   * ```cpp
   * SkRTree::Node* SkRTree::allocateNodeAtLevel(uint16_t level) {
   *     SkDEBUGCODE(Node* p = fNodes.data());
   *     fNodes.push_back(Node{});
   *     Node& out = fNodes.back();
   *     SkASSERT(fNodes.data() == p);  // If this fails, we didn't reserve() enough.
   *     out.fNumChildren = 0;
   *     out.fLevel = level;
   *     return &out;
   * }
   * ```
   */
  private fun allocateNodeAtLevel(level: UShort): Node {
    TODO("Implement allocateNodeAtLevel")
  }

  public data class Branch public constructor(
    public var fBounds: SkRect,
    private var fSubtree: org.skia.modules.Node?,
    private var fOpIndex: Int,
  )

  public open class Node public constructor(
    public var fNumChildren: Int,
    public var fLevel: Int,
    public var fChildren: Array<Branch>,
  )

  public companion object {
    public val kMinChildren: Int = TODO("Initialize kMinChildren")

    public val kMaxChildren: Int = TODO("Initialize kMaxChildren")

    /**
     * C++ original:
     * ```cpp
     * int SkRTree::CountNodes(int branches) {
     *     if (branches == 1) {
     *         return 1;
     *     }
     *     int remainder   = branches % kMaxChildren;
     *     if (remainder > 0) {
     *         if (remainder >= kMinChildren) {
     *             remainder = 0;
     *         } else {
     *             remainder = kMinChildren - remainder;
     *         }
     *     }
     *     int currentBranch = 0;
     *     int nodes = 0;
     *     while (currentBranch < branches) {
     *         int incrementBy = kMaxChildren;
     *         if (remainder != 0) {
     *             if (remainder <= kMaxChildren - kMinChildren) {
     *                 incrementBy -= remainder;
     *                 remainder = 0;
     *             } else {
     *                 incrementBy = kMinChildren;
     *                 remainder -= kMaxChildren - kMinChildren;
     *             }
     *         }
     *         nodes++;
     *         currentBranch++;
     *         for (int k = 1; k < incrementBy && currentBranch < branches; ++k) {
     *             currentBranch++;
     *         }
     *     }
     *     return nodes + CountNodes(nodes);
     * }
     * ```
     */
    private fun countNodes(branches: Int): Int {
      TODO("Implement countNodes")
    }
  }
}
