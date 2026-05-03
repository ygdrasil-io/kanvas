package org.skia.utils

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UShort
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * class ActiveEdgeList {
 * public:
 *     ActiveEdgeList(int maxEdges) {
 *         fAllocation = (char*) sk_malloc_throw(sizeof(ActiveEdge)*maxEdges);
 *         fCurrFree = 0;
 *         fMaxFree = maxEdges;
 *     }
 *     ~ActiveEdgeList() {
 *         fTreeHead.fChild[1] = nullptr;
 *         sk_free(fAllocation);
 *     }
 *
 *     bool insert(const SkPoint& p0, const SkPoint& p1, uint16_t index0, uint16_t index1) {
 *         SkVector v = p1 - p0;
 *         if (!v.isFinite()) {
 *             return false;
 *         }
 *         // empty tree case -- easy
 *         if (!fTreeHead.fChild[1]) {
 *             ActiveEdge* root = fTreeHead.fChild[1] = this->allocate(p0, v, index0, index1);
 *             SkASSERT(root);
 *             if (!root) {
 *                 return false;
 *             }
 *             root->fRed = false;
 *             return true;
 *         }
 *
 *         // set up helpers
 *         ActiveEdge* top = &fTreeHead;
 *         ActiveEdge *grandparent = nullptr;
 *         ActiveEdge *parent = nullptr;
 *         ActiveEdge *curr = top->fChild[1];
 *         int dir = 0;
 *         int last = 0; // ?
 *         // predecessor and successor, for intersection check
 *         ActiveEdge* pred = nullptr;
 *         ActiveEdge* succ = nullptr;
 *
 *         // search down the tree
 *         while (true) {
 *             if (!curr) {
 *                 // check for intersection with predecessor and successor
 *                 if ((pred && pred->intersect(p0, v, index0, index1)) ||
 *                     (succ && succ->intersect(p0, v, index0, index1))) {
 *                     return false;
 *                 }
 *                 // insert new node at bottom
 *                 parent->fChild[dir] = curr = this->allocate(p0, v, index0, index1);
 *                 SkASSERT(curr);
 *                 if (!curr) {
 *                     return false;
 *                 }
 *                 curr->fAbove = pred;
 *                 curr->fBelow = succ;
 *                 if (pred) {
 *                     if (pred->fSegment.fP0 == curr->fSegment.fP0 &&
 *                         pred->fSegment.fV == curr->fSegment.fV) {
 *                         return false;
 *                     }
 *                     pred->fBelow = curr;
 *                 }
 *                 if (succ) {
 *                     if (succ->fSegment.fP0 == curr->fSegment.fP0 &&
 *                         succ->fSegment.fV == curr->fSegment.fV) {
 *                         return false;
 *                     }
 *                     succ->fAbove = curr;
 *                 }
 *                 if (IsRed(parent)) {
 *                     int dir2 = (top->fChild[1] == grandparent);
 *                     if (curr == parent->fChild[last]) {
 *                         top->fChild[dir2] = SingleRotation(grandparent, !last);
 *                     } else {
 *                         top->fChild[dir2] = DoubleRotation(grandparent, !last);
 *                     }
 *                 }
 *                 break;
 *             } else if (IsRed(curr->fChild[0]) && IsRed(curr->fChild[1])) {
 *                 // color flip
 *                 curr->fRed = true;
 *                 curr->fChild[0]->fRed = false;
 *                 curr->fChild[1]->fRed = false;
 *                 if (IsRed(parent)) {
 *                     int dir2 = (top->fChild[1] == grandparent);
 *                     if (curr == parent->fChild[last]) {
 *                         top->fChild[dir2] = SingleRotation(grandparent, !last);
 *                     } else {
 *                         top->fChild[dir2] = DoubleRotation(grandparent, !last);
 *                     }
 *                 }
 *             }
 *
 *             last = dir;
 *             int side;
 *             // check to see if segment is above or below
 *             if (curr->fIndex0 == index0) {
 *                 side = compute_side(curr->fSegment.fP0, curr->fSegment.fV, p1);
 *             } else {
 *                 side = compute_side(curr->fSegment.fP0, curr->fSegment.fV, p0);
 *             }
 *             if (0 == side) {
 *                 return false;
 *             }
 *             dir = (side < 0);
 *
 *             if (0 == dir) {
 *                 succ = curr;
 *             } else {
 *                 pred = curr;
 *             }
 *
 *             // update helpers
 *             if (grandparent) {
 *                 top = grandparent;
 *             }
 *             grandparent = parent;
 *             parent = curr;
 *             curr = curr->fChild[dir];
 *         }
 *
 *         // update root and make it black
 *         fTreeHead.fChild[1]->fRed = false;
 *
 *         SkDEBUGCODE(VerifyTree(fTreeHead.fChild[1]));
 *
 *         return true;
 *     }
 *
 *     // replaces edge p0p1 with p1p2
 *     bool replace(const SkPoint& p0, const SkPoint& p1, const SkPoint& p2,
 *                  uint16_t index0, uint16_t index1, uint16_t index2) {
 *         if (!fTreeHead.fChild[1]) {
 *             return false;
 *         }
 *
 *         SkVector v = p2 - p1;
 *         ActiveEdge* curr = &fTreeHead;
 *         ActiveEdge* found = nullptr;
 *         int dir = 1;
 *
 *         // search
 *         while (curr->fChild[dir] != nullptr) {
 *             // update helpers
 *             curr = curr->fChild[dir];
 *             // save found node
 *             if (curr->equals(index0, index1)) {
 *                 found = curr;
 *                 break;
 *             } else {
 *                 // check to see if segment is above or below
 *                 int side;
 *                 if (curr->fIndex1 == index1) {
 *                     side = compute_side(curr->fSegment.fP0, curr->fSegment.fV, p0);
 *                 } else {
 *                     side = compute_side(curr->fSegment.fP0, curr->fSegment.fV, p1);
 *                 }
 *                 if (0 == side) {
 *                     return false;
 *                 }
 *                 dir = (side < 0);
 *             }
 *         }
 *
 *         if (!found) {
 *             return false;
 *         }
 *
 *         // replace if found
 *         ActiveEdge* pred = found->fAbove;
 *         ActiveEdge* succ = found->fBelow;
 *         // check deletion and insert intersection cases
 *         if (pred && (pred->intersect(found) || pred->intersect(p1, v, index1, index2))) {
 *             return false;
 *         }
 *         if (succ && (succ->intersect(found) || succ->intersect(p1, v, index1, index2))) {
 *             return false;
 *         }
 *         found->fSegment.fP0 = p1;
 *         found->fSegment.fV = v;
 *         found->fIndex0 = index1;
 *         found->fIndex1 = index2;
 *         // above and below should stay the same
 *
 *         SkDEBUGCODE(VerifyTree(fTreeHead.fChild[1]));
 *
 *         return true;
 *     }
 *
 *     bool remove(const SkPoint& p0, const SkPoint& p1, uint16_t index0, uint16_t index1) {
 *         if (!fTreeHead.fChild[1]) {
 *             return false;
 *         }
 *
 *         ActiveEdge* curr = &fTreeHead;
 *         ActiveEdge* parent = nullptr;
 *         ActiveEdge* grandparent = nullptr;
 *         ActiveEdge* found = nullptr;
 *         int dir = 1;
 *
 *         // search and push a red node down
 *         while (curr->fChild[dir] != nullptr) {
 *             int last = dir;
 *
 *             // update helpers
 *             grandparent = parent;
 *             parent = curr;
 *             curr = curr->fChild[dir];
 *             // save found node
 *             if (curr->equals(index0, index1)) {
 *                 found = curr;
 *                 dir = 0;
 *             } else {
 *                 // check to see if segment is above or below
 *                 int side;
 *                 if (curr->fIndex1 == index1) {
 *                     side = compute_side(curr->fSegment.fP0, curr->fSegment.fV, p0);
 *                 } else {
 *                     side = compute_side(curr->fSegment.fP0, curr->fSegment.fV, p1);
 *                 }
 *                 if (0 == side) {
 *                     return false;
 *                 }
 *                 dir = (side < 0);
 *             }
 *
 *             // push the red node down
 *             if (!IsRed(curr) && !IsRed(curr->fChild[dir])) {
 *                 if (IsRed(curr->fChild[!dir])) {
 *                     parent = parent->fChild[last] = SingleRotation(curr, dir);
 *                 } else {
 *                     ActiveEdge *s = parent->fChild[!last];
 *
 *                     if (s != nullptr) {
 *                         if (!IsRed(s->fChild[!last]) && !IsRed(s->fChild[last])) {
 *                             // color flip
 *                             parent->fRed = false;
 *                             s->fRed = true;
 *                             curr->fRed = true;
 *                         } else {
 *                             int dir2 = (grandparent->fChild[1] == parent);
 *
 *                             if (IsRed(s->fChild[last])) {
 *                                 grandparent->fChild[dir2] = DoubleRotation(parent, last);
 *                             } else if (IsRed(s->fChild[!last])) {
 *                                 grandparent->fChild[dir2] = SingleRotation(parent, last);
 *                             }
 *
 *                             // ensure correct coloring
 *                             curr->fRed = grandparent->fChild[dir2]->fRed = true;
 *                             grandparent->fChild[dir2]->fChild[0]->fRed = false;
 *                             grandparent->fChild[dir2]->fChild[1]->fRed = false;
 *                         }
 *                     }
 *                 }
 *             }
 *         }
 *
 *         // replace and remove if found
 *         if (found) {
 *             ActiveEdge* pred = found->fAbove;
 *             ActiveEdge* succ = found->fBelow;
 *             if ((pred && pred->intersect(found)) || (succ && succ->intersect(found))) {
 *                 return false;
 *             }
 *             if (found != curr) {
 *                 found->fSegment = curr->fSegment;
 *                 found->fIndex0 = curr->fIndex0;
 *                 found->fIndex1 = curr->fIndex1;
 *                 found->fAbove = curr->fAbove;
 *                 pred = found->fAbove;
 *                 // we don't need to set found->fBelow here
 *             } else {
 *                 if (succ) {
 *                     succ->fAbove = pred;
 *                 }
 *             }
 *             if (pred) {
 *                 pred->fBelow = curr->fBelow;
 *             }
 *             parent->fChild[parent->fChild[1] == curr] = curr->fChild[!curr->fChild[0]];
 *
 *             // no need to delete
 *             curr->fAbove = reinterpret_cast<ActiveEdge*>(0xdeadbeefll);
 *             curr->fBelow = reinterpret_cast<ActiveEdge*>(0xdeadbeefll);
 *             if (fTreeHead.fChild[1]) {
 *                 fTreeHead.fChild[1]->fRed = false;
 *             }
 *         }
 *
 *         // update root and make it black
 *         if (fTreeHead.fChild[1]) {
 *             fTreeHead.fChild[1]->fRed = false;
 *         }
 *
 *         SkDEBUGCODE(VerifyTree(fTreeHead.fChild[1]));
 *
 *         return true;
 *     }
 *
 * private:
 *     // allocator
 *     ActiveEdge * allocate(const SkPoint& p0, const SkPoint& p1, uint16_t index0, uint16_t index1) {
 *         if (fCurrFree >= fMaxFree) {
 *             return nullptr;
 *         }
 *         char* bytes = fAllocation + sizeof(ActiveEdge)*fCurrFree;
 *         ++fCurrFree;
 *         return new(bytes) ActiveEdge(p0, p1, index0, index1);
 *     }
 *
 *     ///////////////////////////////////////////////////////////////////////////////////
 *     // Red-black tree methods
 *     ///////////////////////////////////////////////////////////////////////////////////
 *     static bool IsRed(const ActiveEdge* node) {
 *         return node && node->fRed;
 *     }
 *
 *     static ActiveEdge* SingleRotation(ActiveEdge* node, int dir) {
 *         ActiveEdge* tmp = node->fChild[!dir];
 *
 *         node->fChild[!dir] = tmp->fChild[dir];
 *         tmp->fChild[dir] = node;
 *
 *         node->fRed = true;
 *         tmp->fRed = false;
 *
 *         return tmp;
 *     }
 *
 *     static ActiveEdge* DoubleRotation(ActiveEdge* node, int dir) {
 *         node->fChild[!dir] = SingleRotation(node->fChild[!dir], !dir);
 *
 *         return SingleRotation(node, dir);
 *     }
 *
 *     // returns black link count
 *     static int VerifyTree(const ActiveEdge* tree) {
 *         if (!tree) {
 *             return 1;
 *         }
 *
 *         const ActiveEdge* left = tree->fChild[0];
 *         const ActiveEdge* right = tree->fChild[1];
 *
 *         // no consecutive red links
 *         if (IsRed(tree) && (IsRed(left) || IsRed(right))) {
 *             SkASSERT(false);
 *             return 0;
 *         }
 *
 *         // check secondary links
 *         if (tree->fAbove) {
 *             SkASSERT(tree->fAbove->fBelow == tree);
 *             SkASSERT(tree->fAbove->lessThan(tree));
 *         }
 *         if (tree->fBelow) {
 *             SkASSERT(tree->fBelow->fAbove == tree);
 *             SkASSERT(tree->lessThan(tree->fBelow));
 *         }
 *
 *         // violates binary tree order
 *         if ((left && tree->lessThan(left)) || (right && right->lessThan(tree))) {
 *             SkASSERT(false);
 *             return 0;
 *         }
 *
 *         int leftCount = VerifyTree(left);
 *         int rightCount = VerifyTree(right);
 *
 *         // return black link count
 *         if (leftCount != 0 && rightCount != 0) {
 *             // black height mismatch
 *             if (leftCount != rightCount) {
 *                 SkASSERT(false);
 *                 return 0;
 *             }
 *             return IsRed(tree) ? leftCount : leftCount + 1;
 *         } else {
 *             return 0;
 *         }
 *     }
 *
 *     ActiveEdge fTreeHead;
 *     char*      fAllocation;
 *     int        fCurrFree;
 *     int        fMaxFree;
 * }
 * ```
 */
public data class ActiveEdgeList public constructor(
  /**
   * C++ original:
   * ```cpp
   * ActiveEdge fTreeHead
   * ```
   */
  private var fTreeHead: ActiveEdge,
  /**
   * C++ original:
   * ```cpp
   * char*      fAllocation
   * ```
   */
  private var fAllocation: String?,
  /**
   * C++ original:
   * ```cpp
   * int        fCurrFree
   * ```
   */
  private var fCurrFree: Int,
  /**
   * C++ original:
   * ```cpp
   * int        fMaxFree
   * ```
   */
  private var fMaxFree: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool insert(const SkPoint& p0, const SkPoint& p1, uint16_t index0, uint16_t index1) {
   *         SkVector v = p1 - p0;
   *         if (!v.isFinite()) {
   *             return false;
   *         }
   *         // empty tree case -- easy
   *         if (!fTreeHead.fChild[1]) {
   *             ActiveEdge* root = fTreeHead.fChild[1] = this->allocate(p0, v, index0, index1);
   *             SkASSERT(root);
   *             if (!root) {
   *                 return false;
   *             }
   *             root->fRed = false;
   *             return true;
   *         }
   *
   *         // set up helpers
   *         ActiveEdge* top = &fTreeHead;
   *         ActiveEdge *grandparent = nullptr;
   *         ActiveEdge *parent = nullptr;
   *         ActiveEdge *curr = top->fChild[1];
   *         int dir = 0;
   *         int last = 0; // ?
   *         // predecessor and successor, for intersection check
   *         ActiveEdge* pred = nullptr;
   *         ActiveEdge* succ = nullptr;
   *
   *         // search down the tree
   *         while (true) {
   *             if (!curr) {
   *                 // check for intersection with predecessor and successor
   *                 if ((pred && pred->intersect(p0, v, index0, index1)) ||
   *                     (succ && succ->intersect(p0, v, index0, index1))) {
   *                     return false;
   *                 }
   *                 // insert new node at bottom
   *                 parent->fChild[dir] = curr = this->allocate(p0, v, index0, index1);
   *                 SkASSERT(curr);
   *                 if (!curr) {
   *                     return false;
   *                 }
   *                 curr->fAbove = pred;
   *                 curr->fBelow = succ;
   *                 if (pred) {
   *                     if (pred->fSegment.fP0 == curr->fSegment.fP0 &&
   *                         pred->fSegment.fV == curr->fSegment.fV) {
   *                         return false;
   *                     }
   *                     pred->fBelow = curr;
   *                 }
   *                 if (succ) {
   *                     if (succ->fSegment.fP0 == curr->fSegment.fP0 &&
   *                         succ->fSegment.fV == curr->fSegment.fV) {
   *                         return false;
   *                     }
   *                     succ->fAbove = curr;
   *                 }
   *                 if (IsRed(parent)) {
   *                     int dir2 = (top->fChild[1] == grandparent);
   *                     if (curr == parent->fChild[last]) {
   *                         top->fChild[dir2] = SingleRotation(grandparent, !last);
   *                     } else {
   *                         top->fChild[dir2] = DoubleRotation(grandparent, !last);
   *                     }
   *                 }
   *                 break;
   *             } else if (IsRed(curr->fChild[0]) && IsRed(curr->fChild[1])) {
   *                 // color flip
   *                 curr->fRed = true;
   *                 curr->fChild[0]->fRed = false;
   *                 curr->fChild[1]->fRed = false;
   *                 if (IsRed(parent)) {
   *                     int dir2 = (top->fChild[1] == grandparent);
   *                     if (curr == parent->fChild[last]) {
   *                         top->fChild[dir2] = SingleRotation(grandparent, !last);
   *                     } else {
   *                         top->fChild[dir2] = DoubleRotation(grandparent, !last);
   *                     }
   *                 }
   *             }
   *
   *             last = dir;
   *             int side;
   *             // check to see if segment is above or below
   *             if (curr->fIndex0 == index0) {
   *                 side = compute_side(curr->fSegment.fP0, curr->fSegment.fV, p1);
   *             } else {
   *                 side = compute_side(curr->fSegment.fP0, curr->fSegment.fV, p0);
   *             }
   *             if (0 == side) {
   *                 return false;
   *             }
   *             dir = (side < 0);
   *
   *             if (0 == dir) {
   *                 succ = curr;
   *             } else {
   *                 pred = curr;
   *             }
   *
   *             // update helpers
   *             if (grandparent) {
   *                 top = grandparent;
   *             }
   *             grandparent = parent;
   *             parent = curr;
   *             curr = curr->fChild[dir];
   *         }
   *
   *         // update root and make it black
   *         fTreeHead.fChild[1]->fRed = false;
   *
   *         SkDEBUGCODE(VerifyTree(fTreeHead.fChild[1]));
   *
   *         return true;
   *     }
   * ```
   */
  public fun insert(
    p0: SkPoint,
    p1: SkPoint,
    index0: UShort,
    index1: UShort,
  ): Boolean {
    TODO("Implement insert")
  }

  /**
   * C++ original:
   * ```cpp
   * bool replace(const SkPoint& p0, const SkPoint& p1, const SkPoint& p2,
   *                  uint16_t index0, uint16_t index1, uint16_t index2) {
   *         if (!fTreeHead.fChild[1]) {
   *             return false;
   *         }
   *
   *         SkVector v = p2 - p1;
   *         ActiveEdge* curr = &fTreeHead;
   *         ActiveEdge* found = nullptr;
   *         int dir = 1;
   *
   *         // search
   *         while (curr->fChild[dir] != nullptr) {
   *             // update helpers
   *             curr = curr->fChild[dir];
   *             // save found node
   *             if (curr->equals(index0, index1)) {
   *                 found = curr;
   *                 break;
   *             } else {
   *                 // check to see if segment is above or below
   *                 int side;
   *                 if (curr->fIndex1 == index1) {
   *                     side = compute_side(curr->fSegment.fP0, curr->fSegment.fV, p0);
   *                 } else {
   *                     side = compute_side(curr->fSegment.fP0, curr->fSegment.fV, p1);
   *                 }
   *                 if (0 == side) {
   *                     return false;
   *                 }
   *                 dir = (side < 0);
   *             }
   *         }
   *
   *         if (!found) {
   *             return false;
   *         }
   *
   *         // replace if found
   *         ActiveEdge* pred = found->fAbove;
   *         ActiveEdge* succ = found->fBelow;
   *         // check deletion and insert intersection cases
   *         if (pred && (pred->intersect(found) || pred->intersect(p1, v, index1, index2))) {
   *             return false;
   *         }
   *         if (succ && (succ->intersect(found) || succ->intersect(p1, v, index1, index2))) {
   *             return false;
   *         }
   *         found->fSegment.fP0 = p1;
   *         found->fSegment.fV = v;
   *         found->fIndex0 = index1;
   *         found->fIndex1 = index2;
   *         // above and below should stay the same
   *
   *         SkDEBUGCODE(VerifyTree(fTreeHead.fChild[1]));
   *
   *         return true;
   *     }
   * ```
   */
  public fun replace(
    p0: SkPoint,
    p1: SkPoint,
    p2: SkPoint,
    index0: UShort,
    index1: UShort,
    index2: UShort,
  ): Boolean {
    TODO("Implement replace")
  }

  /**
   * C++ original:
   * ```cpp
   * bool remove(const SkPoint& p0, const SkPoint& p1, uint16_t index0, uint16_t index1) {
   *         if (!fTreeHead.fChild[1]) {
   *             return false;
   *         }
   *
   *         ActiveEdge* curr = &fTreeHead;
   *         ActiveEdge* parent = nullptr;
   *         ActiveEdge* grandparent = nullptr;
   *         ActiveEdge* found = nullptr;
   *         int dir = 1;
   *
   *         // search and push a red node down
   *         while (curr->fChild[dir] != nullptr) {
   *             int last = dir;
   *
   *             // update helpers
   *             grandparent = parent;
   *             parent = curr;
   *             curr = curr->fChild[dir];
   *             // save found node
   *             if (curr->equals(index0, index1)) {
   *                 found = curr;
   *                 dir = 0;
   *             } else {
   *                 // check to see if segment is above or below
   *                 int side;
   *                 if (curr->fIndex1 == index1) {
   *                     side = compute_side(curr->fSegment.fP0, curr->fSegment.fV, p0);
   *                 } else {
   *                     side = compute_side(curr->fSegment.fP0, curr->fSegment.fV, p1);
   *                 }
   *                 if (0 == side) {
   *                     return false;
   *                 }
   *                 dir = (side < 0);
   *             }
   *
   *             // push the red node down
   *             if (!IsRed(curr) && !IsRed(curr->fChild[dir])) {
   *                 if (IsRed(curr->fChild[!dir])) {
   *                     parent = parent->fChild[last] = SingleRotation(curr, dir);
   *                 } else {
   *                     ActiveEdge *s = parent->fChild[!last];
   *
   *                     if (s != nullptr) {
   *                         if (!IsRed(s->fChild[!last]) && !IsRed(s->fChild[last])) {
   *                             // color flip
   *                             parent->fRed = false;
   *                             s->fRed = true;
   *                             curr->fRed = true;
   *                         } else {
   *                             int dir2 = (grandparent->fChild[1] == parent);
   *
   *                             if (IsRed(s->fChild[last])) {
   *                                 grandparent->fChild[dir2] = DoubleRotation(parent, last);
   *                             } else if (IsRed(s->fChild[!last])) {
   *                                 grandparent->fChild[dir2] = SingleRotation(parent, last);
   *                             }
   *
   *                             // ensure correct coloring
   *                             curr->fRed = grandparent->fChild[dir2]->fRed = true;
   *                             grandparent->fChild[dir2]->fChild[0]->fRed = false;
   *                             grandparent->fChild[dir2]->fChild[1]->fRed = false;
   *                         }
   *                     }
   *                 }
   *             }
   *         }
   *
   *         // replace and remove if found
   *         if (found) {
   *             ActiveEdge* pred = found->fAbove;
   *             ActiveEdge* succ = found->fBelow;
   *             if ((pred && pred->intersect(found)) || (succ && succ->intersect(found))) {
   *                 return false;
   *             }
   *             if (found != curr) {
   *                 found->fSegment = curr->fSegment;
   *                 found->fIndex0 = curr->fIndex0;
   *                 found->fIndex1 = curr->fIndex1;
   *                 found->fAbove = curr->fAbove;
   *                 pred = found->fAbove;
   *                 // we don't need to set found->fBelow here
   *             } else {
   *                 if (succ) {
   *                     succ->fAbove = pred;
   *                 }
   *             }
   *             if (pred) {
   *                 pred->fBelow = curr->fBelow;
   *             }
   *             parent->fChild[parent->fChild[1] == curr] = curr->fChild[!curr->fChild[0]];
   *
   *             // no need to delete
   *             curr->fAbove = reinterpret_cast<ActiveEdge*>(0xdeadbeefll);
   *             curr->fBelow = reinterpret_cast<ActiveEdge*>(0xdeadbeefll);
   *             if (fTreeHead.fChild[1]) {
   *                 fTreeHead.fChild[1]->fRed = false;
   *             }
   *         }
   *
   *         // update root and make it black
   *         if (fTreeHead.fChild[1]) {
   *             fTreeHead.fChild[1]->fRed = false;
   *         }
   *
   *         SkDEBUGCODE(VerifyTree(fTreeHead.fChild[1]));
   *
   *         return true;
   *     }
   * ```
   */
  public fun remove(
    p0: SkPoint,
    p1: SkPoint,
    index0: UShort,
    index1: UShort,
  ): Boolean {
    TODO("Implement remove")
  }

  /**
   * C++ original:
   * ```cpp
   * ActiveEdge * allocate(const SkPoint& p0, const SkPoint& p1, uint16_t index0, uint16_t index1) {
   *         if (fCurrFree >= fMaxFree) {
   *             return nullptr;
   *         }
   *         char* bytes = fAllocation + sizeof(ActiveEdge)*fCurrFree;
   *         ++fCurrFree;
   *         return new(bytes) ActiveEdge(p0, p1, index0, index1);
   *     }
   * ```
   */
  private fun allocate(
    p0: SkPoint,
    p1: SkPoint,
    index0: UShort,
    index1: UShort,
  ): ActiveEdge {
    TODO("Implement allocate")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static bool IsRed(const ActiveEdge* node) {
     *         return node && node->fRed;
     *     }
     * ```
     */
    private fun isRed(node: ActiveEdge?): Boolean {
      TODO("Implement isRed")
    }

    /**
     * C++ original:
     * ```cpp
     * static ActiveEdge* SingleRotation(ActiveEdge* node, int dir) {
     *         ActiveEdge* tmp = node->fChild[!dir];
     *
     *         node->fChild[!dir] = tmp->fChild[dir];
     *         tmp->fChild[dir] = node;
     *
     *         node->fRed = true;
     *         tmp->fRed = false;
     *
     *         return tmp;
     *     }
     * ```
     */
    private fun singleRotation(node: ActiveEdge?, dir: Int): ActiveEdge {
      TODO("Implement singleRotation")
    }

    /**
     * C++ original:
     * ```cpp
     * static ActiveEdge* DoubleRotation(ActiveEdge* node, int dir) {
     *         node->fChild[!dir] = SingleRotation(node->fChild[!dir], !dir);
     *
     *         return SingleRotation(node, dir);
     *     }
     * ```
     */
    private fun doubleRotation(node: ActiveEdge?, dir: Int): ActiveEdge {
      TODO("Implement doubleRotation")
    }

    /**
     * C++ original:
     * ```cpp
     * static int VerifyTree(const ActiveEdge* tree) {
     *         if (!tree) {
     *             return 1;
     *         }
     *
     *         const ActiveEdge* left = tree->fChild[0];
     *         const ActiveEdge* right = tree->fChild[1];
     *
     *         // no consecutive red links
     *         if (IsRed(tree) && (IsRed(left) || IsRed(right))) {
     *             SkASSERT(false);
     *             return 0;
     *         }
     *
     *         // check secondary links
     *         if (tree->fAbove) {
     *             SkASSERT(tree->fAbove->fBelow == tree);
     *             SkASSERT(tree->fAbove->lessThan(tree));
     *         }
     *         if (tree->fBelow) {
     *             SkASSERT(tree->fBelow->fAbove == tree);
     *             SkASSERT(tree->lessThan(tree->fBelow));
     *         }
     *
     *         // violates binary tree order
     *         if ((left && tree->lessThan(left)) || (right && right->lessThan(tree))) {
     *             SkASSERT(false);
     *             return 0;
     *         }
     *
     *         int leftCount = VerifyTree(left);
     *         int rightCount = VerifyTree(right);
     *
     *         // return black link count
     *         if (leftCount != 0 && rightCount != 0) {
     *             // black height mismatch
     *             if (leftCount != rightCount) {
     *                 SkASSERT(false);
     *                 return 0;
     *             }
     *             return IsRed(tree) ? leftCount : leftCount + 1;
     *         } else {
     *             return 0;
     *         }
     *     }
     * ```
     */
    private fun verifyTree(tree: ActiveEdge?): Int {
      TODO("Implement verifyTree")
    }
  }
}
