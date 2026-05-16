package org.skia.core

import org.graphiks.math.SkRect

/**
 * Bottom-up bulk-loaded R-tree. Mirrors Skia's
 * [`SkRTree`](https://github.com/google/skia/blob/main/src/core/SkRTree.h) :
 * a balanced N-ary tree of axis-aligned bounding rectangles where
 * each leaf points to one input rect (the picture op's index) and
 * each interior node carries the union of its children's bounds.
 *
 * **Construction** is single-shot bulk-load — no incremental insert.
 * The flow :
 *
 *  1. The caller hands a list of N rects to [insert].
 *  2. Empty rects are skipped (they can never intersect any query).
 *  3. The remaining rects become level-0 [Branch]es.
 *  4. We chunk consecutive branches into nodes of size
 *     `[kMinChildren, kMaxChildren]` ; each node's bounds = the
 *     union of its children. The new nodes form the next level
 *     and the algorithm recurses until one node remains — the root.
 *
 * The chunk-size formula (`incrementBy = kMaxChildren - remainder`)
 * matches Skia's exact implementation : when the trailing chunk
 * would otherwise be too small to satisfy `kMinChildren`, we pull
 * branches from earlier chunks to balance the tree.
 *
 * **Insertion order is preserved through the tree** : because we
 * chunk consecutive branches without sorting, the leaves of the tree
 * appear in the same order they were inserted, and the search
 * traversal visits children left-to-right — so [search] returns its
 * result indices ascending by insertion order. This matches Skia's
 * RTreeTest expectation (`found == expected` with `expected` built
 * by linear iteration over `rects`).
 *
 * **Querying** is a top-down traversal : at each node, recurse into
 * children whose bounds intersect [query] ; at leaves, push the op
 * index. Average cost is `O(log N + K)` for K = result size ; worst
 * case (all rects intersect) is `O(N)`.
 *
 * **Thread-safety** : a built [SkRTree] is read-only and therefore
 * safe to query concurrently. Construction is **not** thread-safe.
 *
 * **Reference** : Beckmann, Kriegel, Schneider, Seeger (1990) —
 * *The R\*-tree : an efficient and robust access method for points
 * and rectangles*.
 */
public class SkRTree : SkBBoxHierarchy() {

    /**
     * One leaf-or-interior link. At leaf level (`Node.level == 0`)
     * [opIndex] holds the input-rect index ; at interior levels,
     * [subtree] points to the child node. The two are mutually
     * exclusive — encoded as a discriminated union via [isLeaf].
     */
    private class Branch(
        var bounds: SkRect = SkRect.MakeEmpty(),
        var subtree: Node? = null,
        var opIndex: Int = -1,
    ) {
        val isLeaf: Boolean get() = subtree == null
    }

    private class Node(
        val level: Int,
    ) {
        val children: MutableList<Branch> = ArrayList(kMaxChildren)
    }

    private var count: Int = 0
    private var root: Branch? = null
    private val nodes: MutableList<Node> = ArrayList()
    private var inserted: Boolean = false

    /**
     * Number of rects actually indexed (after dropping empties).
     * Useful for tests and parity with upstream's `getCount`.
     */
    public val countInserted: Int get() = count

    /**
     * Tree depth. `0` for an empty tree ; `1` for a single-node tree
     * (root + one leaf level) ; `n + 1` for a tree whose root sits
     * at level `n`. Mirrors Skia's `getDepth`.
     */
    public val depth: Int get() = root?.subtree?.let { it.level + 1 } ?: 0

    override val bytesUsed: Long
        get() {
            // Cheap heuristic : sizeof(Node) ≈ 16 bytes header + 12 *
            // children, each Branch ≈ 32 bytes (bounds + ref + int).
            // Counts are stable post-insert.
            val nodeBytes = 16L + nodes.sumOf { 16L + 32L * it.children.size }
            return nodeBytes
        }

    override fun insert(rects: Array<SkRect>, n: Int) {
        check(!inserted) { "SkRTree is single-shot ; insert may be called only once" }
        inserted = true

        // Filter empty rects ; they can never intersect a non-empty
        // query, so dropping them keeps the tree tight without
        // changing semantics.
        val branches = ArrayList<Branch>(n)
        for (i in 0 until n) {
            val r = rects[i]
            if (r.isEmpty) continue
            branches.add(Branch(bounds = r.copy(), opIndex = i))
        }

        count = branches.size
        if (count == 0) return

        if (count == 1) {
            // Single-rect special case : one leaf node, root points at it.
            val n0 = allocateNodeAtLevel(0)
            n0.children.add(branches[0])
            root = Branch(bounds = branches[0].bounds.copy(), subtree = n0)
            return
        }
        root = bulkLoad(branches, level = 0)
    }

    override fun search(query: SkRect): IntArray {
        val r = root ?: return EMPTY_INT
        if (!r.bounds.intersects(query)) return EMPTY_INT
        val results = ArrayList<Int>()
        searchNode(r.subtree!!, query, results)
        return results.toIntArray()
    }

    private fun searchNode(node: Node, query: SkRect, out: MutableList<Int>) {
        for (b in node.children) {
            if (!b.bounds.intersects(query)) continue
            if (node.level == 0) {
                out.add(b.opIndex)
            } else {
                searchNode(b.subtree!!, query, out)
            }
        }
    }

    /**
     * Mirrors Skia's `bulkLoad` exactly : chunk consecutive branches
     * into level-`level` nodes, then recurse one level up. The
     * `remainder` accounting balances the trailing chunk so every
     * node ends up with `[kMinChildren, kMaxChildren]` children
     * (except for the root, which may hold fewer).
     */
    private fun bulkLoad(branches: ArrayList<Branch>, level: Int): Branch {
        if (branches.size == 1) return branches[0]

        var remainder = branches.size % kMaxChildren
        if (remainder > 0) {
            remainder = if (remainder >= kMinChildren) 0
            else kMinChildren - remainder
        }

        var current = 0
        var newCount = 0
        while (current < branches.size) {
            var incrementBy = kMaxChildren
            if (remainder != 0) {
                if (remainder <= kMaxChildren - kMinChildren) {
                    incrementBy -= remainder
                    remainder = 0
                } else {
                    incrementBy = kMinChildren
                    remainder -= kMaxChildren - kMinChildren
                }
            }
            val n = allocateNodeAtLevel(level)
            n.children.add(branches[current])
            val parent = Branch(
                bounds = branches[current].bounds.copy(),
                subtree = n,
            )
            current++
            var k = 1
            while (k < incrementBy && current < branches.size) {
                parent.bounds.join(branches[current].bounds)
                n.children.add(branches[current])
                current++
                k++
            }
            branches[newCount] = parent
            newCount++
        }
        // In-place truncation of the working list — mirrors C++'s
        // `branches->resize(newBranches)`.
        while (branches.size > newCount) branches.removeAt(branches.size - 1)
        return bulkLoad(branches, level + 1)
    }

    private fun allocateNodeAtLevel(level: Int): Node {
        val n = Node(level)
        nodes.add(n)
        return n
    }

    public companion object {
        /**
         * Branch-factor floor and ceiling. Empirically chosen by
         * upstream for "reasonable performance in most cases" — narrow
         * trees (small fan-out) thrash the cache on traversal ;
         * wide trees (large fan-out) waste work on per-node bounds
         * checks. `[6, 11]` lands the sweet spot for typical pictures.
         */
        public const val kMinChildren: Int = 6
        public const val kMaxChildren: Int = 11

        private val EMPTY_INT: IntArray = IntArray(0)
    }
}
