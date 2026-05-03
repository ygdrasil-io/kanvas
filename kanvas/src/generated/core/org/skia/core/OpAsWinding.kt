package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.math.SkPathFillType

/**
 * C++ original:
 * ```cpp
 * class OpAsWinding {
 * public:
 *     enum class Edge {
 *         kInitial,
 *         kCompare,
 *     };
 *
 *     OpAsWinding(const SkPath& path)
 *         : fPath(path) {
 *     }
 *
 *     void contourBounds(vector<Contour>* containers) {
 *         SkRect bounds;
 *         bounds.setEmpty();
 *         int lastStart = 0;
 *         int verbStart = 0;
 *         for (auto [verb, pts, w] : SkPathPriv::Iterate(fPath)) {
 *             if (SkPathVerb::kMove == verb) {
 *                 if (!bounds.isEmpty()) {
 *                     containers->emplace_back(bounds, lastStart, verbStart);
 *                     lastStart = verbStart;
 *                }
 *                 bounds.setBounds({&pts[VerbPtIndex(SkPathVerb::kMove)],
 *                                   VerbPtCount(SkPathVerb::kMove)});
 *             }
 *             if (SkPathVerb::kLine <= verb && verb <= SkPathVerb::kCubic) {
 *                 SkRect verbBounds;
 *                 verbBounds.setBounds({&pts[VerbPtIndex(verb)], VerbPtCount(verb)});
 *                 bounds.joinPossiblyEmptyRect(verbBounds);
 *             }
 *             ++verbStart;
 *         }
 *         if (!bounds.isEmpty()) {
 *             containers->emplace_back(bounds, lastStart, ++verbStart);
 *         }
 *     }
 *
 *     Contour::Direction getDirection(Contour& contour) {
 *         SkPath::Iter iter(fPath, true);
 *         int verbCount = -1;
 *
 *         SkScalar total_signed_area = 0;
 *         while (auto rec = iter.next()) {
 *             if (++verbCount < contour.fVerbStart) {
 *                 continue;
 *             }
 *             if (verbCount >= contour.fVerbEnd) {
 *                 continue;
 *             }
 *             if (SkPathVerb::kLine > rec->fVerb || rec->fVerb > SkPathVerb::kCubic) {
 *                 continue;
 *             }
 *
 *             SkSpan<const SkPoint> pts = rec->fPoints;
 *             switch (rec->fVerb) {
 *                 case SkPathVerb::kLine:
 *                     total_signed_area += (pts[0].fY - pts[1].fY) * (pts[0].fX + pts[1].fX);
 *                     break;
 *                 case SkPathVerb::kQuad:
 *                 case SkPathVerb::kConic:
 *                     total_signed_area += (pts[0].fY - pts[2].fY) * (pts[0].fX + pts[2].fX);
 *                     break;
 *                 case SkPathVerb::kCubic:
 *                     total_signed_area += (pts[0].fY - pts[3].fY) * (pts[0].fX + pts[3].fX);
 *                     break;
 *                 default:
 *                     break;
 *             }
 *         }
 *
 *         return total_signed_area < 0 ? Contour::Direction::kCCW: Contour::Direction::kCW;
 *     }
 *
 *     int nextEdge(Contour& contour, Edge edge) {
 *         SkPath::Iter iter(fPath, true);
 *         int verbCount = -1;
 *         int winding = 0;
 *         while (auto rec = iter.next()) {
 *             const SkPathVerb verb = rec->fVerb;
 *             if (++verbCount < contour.fVerbStart) {
 *                 continue;
 *             }
 *             if (verbCount >= contour.fVerbEnd) {
 *                 continue;
 *             }
 *             if (SkPathVerb::kLine > verb || verb > SkPathVerb::kCubic) {
 *                 continue;
 *             }
 *
 *             SkSpan<const SkPoint> pts = rec->fPoints;
 *             bool horizontal = true;
 *             for (size_t index = 1; index <= VerbPtCount(verb); ++index) {
 *                 if (pts[0].fY != pts[index].fY) {
 *                     horizontal = false;
 *                     break;
 *                 }
 *             }
 *             if (horizontal) {
 *                 continue;
 *             }
 *             if (edge == Edge::kCompare) {
 *                 winding += contains_edge(pts.data(), verb, conic_weight(*rec),
 *                                          contour.fMinXY);
 *                 continue;
 *             }
 *             SkASSERT(edge == Edge::kInitial);
 *             SkPoint minXY = left_edge(pts.data(), verb, conic_weight(*rec));
 *             if (minXY.fX > contour.fMinXY.fX) {
 *                 continue;
 *             }
 *             if (minXY.fX == contour.fMinXY.fX) {
 *                 if (minXY.fY != contour.fMinXY.fY) {
 *                     continue;
 *                 }
 *             }
 *             contour.fMinXY = minXY;
 *         }
 *         return winding;
 *     }
 *
 *     bool containerContains(Contour& contour, Contour& test) {
 *         // find outside point on lesser contour
 *         // arbitrarily, choose non-horizontal edge where point <= bounds left
 *         // note that if leftmost point is control point, may need tight bounds
 *         // to find edge with minimum-x
 *         if (SK_ScalarMax == test.fMinXY.fX) {
 *             this->nextEdge(test, Edge::kInitial);
 *         }
 *         // find all edges on greater equal or to the left of one on lesser
 *         contour.fMinXY = test.fMinXY;
 *         int winding = this->nextEdge(contour, Edge::kCompare);
 *         // if edge is up, mark contour cw, otherwise, ccw
 *         // sum of greater edges direction should be cw, 0, ccw
 *         test.fContained = winding != 0;
 *         return -1 <= winding && winding <= 1;
 *     }
 *
 *     void inParent(Contour& contour, Contour& parent) {
 *         // move contour into sibling list contained by parent
 *         for (auto test : parent.fChildren) {
 *             if (test->fBounds.contains(contour.fBounds)) {
 *                 inParent(contour, *test);
 *                 return;
 *             }
 *         }
 *         // move parent's children into contour's children if contained by contour
 *         for (auto iter = parent.fChildren.begin(); iter != parent.fChildren.end(); ) {
 *             if (contour.fBounds.contains((*iter)->fBounds)) {
 *                 contour.fChildren.push_back(*iter);
 *                 iter = parent.fChildren.erase(iter);
 *                 continue;
 *             }
 *             ++iter;
 *         }
 *         parent.fChildren.push_back(&contour);
 *     }
 *
 *     bool checkContainerChildren(Contour* parent, Contour* child) {
 *         for (auto grandChild : child->fChildren) {
 *             if (!checkContainerChildren(child, grandChild)) {
 *                 return false;
 *             }
 *         }
 *         if (parent) {
 *             if (!containerContains(*parent, *child)) {
 *                 return false;
 *             }
 *         }
 *         return true;
 *     }
 *
 *     bool markReverse(Contour* parent, Contour* child) {
 *         bool reversed = false;
 *         for (auto grandChild : child->fChildren) {
 *             reversed |= markReverse(grandChild->fContained ? child : parent, grandChild);
 *         }
 *
 *         child->fDirection = getDirection(*child);
 *         if (parent && parent->fDirection == child->fDirection) {
 *             child->fReverse = true;
 *             child->fDirection = (Contour::Direction) -(int) child->fDirection;
 *             return true;
 *         }
 *         return reversed;
 *     }
 *
 *     SkPath reverseMarkedContours(vector<Contour>& contours, SkPathFillType fillType) {
 *         SkPathPriv::Iterate iterate(fPath);
 *         auto iter = iterate.begin();
 *         int verbCount = 0;
 *
 *         SkPathBuilder result;
 *         result.setFillType(fillType);
 *         for (const Contour& contour : contours) {
 *             SkPathBuilder reverse;
 *             SkPathBuilder* temp = contour.fReverse ? &reverse : &result;
 *             for (; iter != iterate.end() && verbCount < contour.fVerbEnd; ++iter, ++verbCount) {
 *                 auto [verb, pts, w] = *iter;
 *                 switch (verb) {
 *                     case SkPathVerb::kMove:
 *                         temp->moveTo(pts[0]);
 *                         break;
 *                     case SkPathVerb::kLine:
 *                         temp->lineTo(pts[1]);
 *                         break;
 *                     case SkPathVerb::kQuad:
 *                         temp->quadTo(pts[1], pts[2]);
 *                         break;
 *                     case SkPathVerb::kConic:
 *                         temp->conicTo(pts[1], pts[2], *w);
 *                         break;
 *                     case SkPathVerb::kCubic:
 *                         temp->cubicTo(pts[1], pts[2], pts[3]);
 *                         break;
 *                     case SkPathVerb::kClose:
 *                         temp->close();
 *                         break;
 *                 }
 *             }
 *             if (contour.fReverse) {
 *                 SkASSERT(temp == &reverse);
 *                 SkPathPriv::ReverseAddPath(&result, reverse.detach());
 *             }
 *         }
 *         return result.detach();
 *     }
 *
 * private:
 *     const SkPath& fPath;
 * }
 * ```
 */
public data class OpAsWinding public constructor(
  /**
   * C++ original:
   * ```cpp
   * const SkPath& fPath
   * ```
   */
  private val fPath: SkPath,
) {
  /**
   * C++ original:
   * ```cpp
   * void contourBounds(vector<Contour>* containers) {
   *         SkRect bounds;
   *         bounds.setEmpty();
   *         int lastStart = 0;
   *         int verbStart = 0;
   *         for (auto [verb, pts, w] : SkPathPriv::Iterate(fPath)) {
   *             if (SkPathVerb::kMove == verb) {
   *                 if (!bounds.isEmpty()) {
   *                     containers->emplace_back(bounds, lastStart, verbStart);
   *                     lastStart = verbStart;
   *                }
   *                 bounds.setBounds({&pts[VerbPtIndex(SkPathVerb::kMove)],
   *                                   VerbPtCount(SkPathVerb::kMove)});
   *             }
   *             if (SkPathVerb::kLine <= verb && verb <= SkPathVerb::kCubic) {
   *                 SkRect verbBounds;
   *                 verbBounds.setBounds({&pts[VerbPtIndex(verb)], VerbPtCount(verb)});
   *                 bounds.joinPossiblyEmptyRect(verbBounds);
   *             }
   *             ++verbStart;
   *         }
   *         if (!bounds.isEmpty()) {
   *             containers->emplace_back(bounds, lastStart, ++verbStart);
   *         }
   *     }
   * ```
   */
  public fun contourBounds() {
    TODO("Implement contourBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * Contour::Direction getDirection(Contour& contour) {
   *         SkPath::Iter iter(fPath, true);
   *         int verbCount = -1;
   *
   *         SkScalar total_signed_area = 0;
   *         while (auto rec = iter.next()) {
   *             if (++verbCount < contour.fVerbStart) {
   *                 continue;
   *             }
   *             if (verbCount >= contour.fVerbEnd) {
   *                 continue;
   *             }
   *             if (SkPathVerb::kLine > rec->fVerb || rec->fVerb > SkPathVerb::kCubic) {
   *                 continue;
   *             }
   *
   *             SkSpan<const SkPoint> pts = rec->fPoints;
   *             switch (rec->fVerb) {
   *                 case SkPathVerb::kLine:
   *                     total_signed_area += (pts[0].fY - pts[1].fY) * (pts[0].fX + pts[1].fX);
   *                     break;
   *                 case SkPathVerb::kQuad:
   *                 case SkPathVerb::kConic:
   *                     total_signed_area += (pts[0].fY - pts[2].fY) * (pts[0].fX + pts[2].fX);
   *                     break;
   *                 case SkPathVerb::kCubic:
   *                     total_signed_area += (pts[0].fY - pts[3].fY) * (pts[0].fX + pts[3].fX);
   *                     break;
   *                 default:
   *                     break;
   *             }
   *         }
   *
   *         return total_signed_area < 0 ? Contour::Direction::kCCW: Contour::Direction::kCW;
   *     }
   * ```
   */
  public fun getDirection(contour: Contour): Contour.Direction {
    TODO("Implement getDirection")
  }

  /**
   * C++ original:
   * ```cpp
   * int nextEdge(Contour& contour, Edge edge) {
   *         SkPath::Iter iter(fPath, true);
   *         int verbCount = -1;
   *         int winding = 0;
   *         while (auto rec = iter.next()) {
   *             const SkPathVerb verb = rec->fVerb;
   *             if (++verbCount < contour.fVerbStart) {
   *                 continue;
   *             }
   *             if (verbCount >= contour.fVerbEnd) {
   *                 continue;
   *             }
   *             if (SkPathVerb::kLine > verb || verb > SkPathVerb::kCubic) {
   *                 continue;
   *             }
   *
   *             SkSpan<const SkPoint> pts = rec->fPoints;
   *             bool horizontal = true;
   *             for (size_t index = 1; index <= VerbPtCount(verb); ++index) {
   *                 if (pts[0].fY != pts[index].fY) {
   *                     horizontal = false;
   *                     break;
   *                 }
   *             }
   *             if (horizontal) {
   *                 continue;
   *             }
   *             if (edge == Edge::kCompare) {
   *                 winding += contains_edge(pts.data(), verb, conic_weight(*rec),
   *                                          contour.fMinXY);
   *                 continue;
   *             }
   *             SkASSERT(edge == Edge::kInitial);
   *             SkPoint minXY = left_edge(pts.data(), verb, conic_weight(*rec));
   *             if (minXY.fX > contour.fMinXY.fX) {
   *                 continue;
   *             }
   *             if (minXY.fX == contour.fMinXY.fX) {
   *                 if (minXY.fY != contour.fMinXY.fY) {
   *                     continue;
   *                 }
   *             }
   *             contour.fMinXY = minXY;
   *         }
   *         return winding;
   *     }
   * ```
   */
  public fun nextEdge(contour: Contour, edge: Edge): Int {
    TODO("Implement nextEdge")
  }

  /**
   * C++ original:
   * ```cpp
   * bool containerContains(Contour& contour, Contour& test) {
   *         // find outside point on lesser contour
   *         // arbitrarily, choose non-horizontal edge where point <= bounds left
   *         // note that if leftmost point is control point, may need tight bounds
   *         // to find edge with minimum-x
   *         if (SK_ScalarMax == test.fMinXY.fX) {
   *             this->nextEdge(test, Edge::kInitial);
   *         }
   *         // find all edges on greater equal or to the left of one on lesser
   *         contour.fMinXY = test.fMinXY;
   *         int winding = this->nextEdge(contour, Edge::kCompare);
   *         // if edge is up, mark contour cw, otherwise, ccw
   *         // sum of greater edges direction should be cw, 0, ccw
   *         test.fContained = winding != 0;
   *         return -1 <= winding && winding <= 1;
   *     }
   * ```
   */
  public fun containerContains(contour: Contour, test: Contour): Boolean {
    TODO("Implement containerContains")
  }

  /**
   * C++ original:
   * ```cpp
   * void inParent(Contour& contour, Contour& parent) {
   *         // move contour into sibling list contained by parent
   *         for (auto test : parent.fChildren) {
   *             if (test->fBounds.contains(contour.fBounds)) {
   *                 inParent(contour, *test);
   *                 return;
   *             }
   *         }
   *         // move parent's children into contour's children if contained by contour
   *         for (auto iter = parent.fChildren.begin(); iter != parent.fChildren.end(); ) {
   *             if (contour.fBounds.contains((*iter)->fBounds)) {
   *                 contour.fChildren.push_back(*iter);
   *                 iter = parent.fChildren.erase(iter);
   *                 continue;
   *             }
   *             ++iter;
   *         }
   *         parent.fChildren.push_back(&contour);
   *     }
   * ```
   */
  public fun inParent(contour: Contour, parent: Contour) {
    TODO("Implement inParent")
  }

  /**
   * C++ original:
   * ```cpp
   * bool checkContainerChildren(Contour* parent, Contour* child) {
   *         for (auto grandChild : child->fChildren) {
   *             if (!checkContainerChildren(child, grandChild)) {
   *                 return false;
   *             }
   *         }
   *         if (parent) {
   *             if (!containerContains(*parent, *child)) {
   *                 return false;
   *             }
   *         }
   *         return true;
   *     }
   * ```
   */
  public fun checkContainerChildren(parent: Contour?, child: Contour?): Boolean {
    TODO("Implement checkContainerChildren")
  }

  /**
   * C++ original:
   * ```cpp
   * bool markReverse(Contour* parent, Contour* child) {
   *         bool reversed = false;
   *         for (auto grandChild : child->fChildren) {
   *             reversed |= markReverse(grandChild->fContained ? child : parent, grandChild);
   *         }
   *
   *         child->fDirection = getDirection(*child);
   *         if (parent && parent->fDirection == child->fDirection) {
   *             child->fReverse = true;
   *             child->fDirection = (Contour::Direction) -(int) child->fDirection;
   *             return true;
   *         }
   *         return reversed;
   *     }
   * ```
   */
  public fun markReverse(parent: Contour?, child: Contour?): Boolean {
    TODO("Implement markReverse")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath reverseMarkedContours(vector<Contour>& contours, SkPathFillType fillType) {
   *         SkPathPriv::Iterate iterate(fPath);
   *         auto iter = iterate.begin();
   *         int verbCount = 0;
   *
   *         SkPathBuilder result;
   *         result.setFillType(fillType);
   *         for (const Contour& contour : contours) {
   *             SkPathBuilder reverse;
   *             SkPathBuilder* temp = contour.fReverse ? &reverse : &result;
   *             for (; iter != iterate.end() && verbCount < contour.fVerbEnd; ++iter, ++verbCount) {
   *                 auto [verb, pts, w] = *iter;
   *                 switch (verb) {
   *                     case SkPathVerb::kMove:
   *                         temp->moveTo(pts[0]);
   *                         break;
   *                     case SkPathVerb::kLine:
   *                         temp->lineTo(pts[1]);
   *                         break;
   *                     case SkPathVerb::kQuad:
   *                         temp->quadTo(pts[1], pts[2]);
   *                         break;
   *                     case SkPathVerb::kConic:
   *                         temp->conicTo(pts[1], pts[2], *w);
   *                         break;
   *                     case SkPathVerb::kCubic:
   *                         temp->cubicTo(pts[1], pts[2], pts[3]);
   *                         break;
   *                     case SkPathVerb::kClose:
   *                         temp->close();
   *                         break;
   *                 }
   *             }
   *             if (contour.fReverse) {
   *                 SkASSERT(temp == &reverse);
   *                 SkPathPriv::ReverseAddPath(&result, reverse.detach());
   *             }
   *         }
   *         return result.detach();
   *     }
   * ```
   */
  public fun reverseMarkedContours(fillType: SkPathFillType): SkPath {
    TODO("Implement reverseMarkedContours")
  }

  public enum class Edge {
    kInitial,
    kCompare,
  }
}
