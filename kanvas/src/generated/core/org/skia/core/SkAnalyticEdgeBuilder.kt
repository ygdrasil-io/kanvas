package org.skia.core

import kotlin.Array
import kotlin.Char
import kotlin.Int
import kotlin.String
import kotlin.ULong
import org.skia.math.SkIRect
import org.skia.math.SkPoint
import org.skia.math.SkRect
import undefined.Combine

/**
 * C++ original:
 * ```cpp
 * class SkAnalyticEdgeBuilder final : public SkEdgeBuilder {
 * public:
 *     SkAnalyticEdgeBuilder() {}
 *
 *     SkAnalyticEdge** analyticEdgeList() { return (SkAnalyticEdge**)fEdgeList; }
 *
 * private:
 *     Combine combineVertical(const SkAnalyticEdge* edge, SkAnalyticEdge* last);
 *
 *     char* allocEdges(size_t, size_t*) override;
 *     SkRect recoverClip(const SkIRect&) const override;
 *
 *     void addLine (const SkPoint pts[]) override;
 *     void addQuad (const SkPoint pts[]) override;
 *     void addCubic(const SkPoint pts[]) override;
 *     Combine addPolyLine(const SkPoint pts[], char* edge, char** edgePtr) override;
 * }
 * ```
 */
public class SkAnalyticEdgeBuilder public constructor() : SkEdgeBuilder() {
  /**
   * C++ original:
   * ```cpp
   * SkAnalyticEdge** analyticEdgeList() { return (SkAnalyticEdge**)fEdgeList; }
   * ```
   */
  public fun analyticEdgeList(): SkAnalyticEdge {
    TODO("Implement analyticEdgeList")
  }

  /**
   * C++ original:
   * ```cpp
   * SkEdgeBuilder::Combine SkAnalyticEdgeBuilder::combineVertical(const SkAnalyticEdge* edge,
   *                                                               SkAnalyticEdge* last) {
   *     auto approximately_equal = [](SkFixed a, SkFixed b) {
   *         return SkAbs32(a - b) < 0x100;
   *     };
   *
   *     // We only consider edges that were originally lines to be vertical to avoid numerical issues
   *     // (crbug.com/1154864).
   *     if (last->fEdgeType != SkAnalyticEdge::Type::kLine || last->fDX || edge->fX != last->fX) {
   *         return kNo_Combine;
   *     }
   *     if (edge->fWinding == last->fWinding) {
   *         if (edge->fLowerY == last->fUpperY) {
   *             last->fUpperY = edge->fUpperY;
   *             last->fY = last->fUpperY;
   *             return kPartial_Combine;
   *         }
   *         if (approximately_equal(edge->fUpperY, last->fLowerY)) {
   *             last->fLowerY = edge->fLowerY;
   *             return kPartial_Combine;
   *         }
   *         return kNo_Combine;
   *     }
   *     if (approximately_equal(edge->fUpperY, last->fUpperY)) {
   *         if (approximately_equal(edge->fLowerY, last->fLowerY)) {
   *             return kTotal_Combine;
   *         }
   *         if (edge->fLowerY < last->fLowerY) {
   *             last->fUpperY = edge->fLowerY;
   *             last->fY = last->fUpperY;
   *             return kPartial_Combine;
   *         }
   *         last->fUpperY = last->fLowerY;
   *         last->fY = last->fUpperY;
   *         last->fLowerY = edge->fLowerY;
   *         last->fWinding = edge->fWinding;
   *         return kPartial_Combine;
   *     }
   *     if (approximately_equal(edge->fLowerY, last->fLowerY)) {
   *         if (edge->fUpperY > last->fUpperY) {
   *             last->fLowerY = edge->fUpperY;
   *             return kPartial_Combine;
   *         }
   *         last->fLowerY = last->fUpperY;
   *         last->fUpperY = edge->fUpperY;
   *         last->fY = last->fUpperY;
   *         last->fWinding = edge->fWinding;
   *         return kPartial_Combine;
   *     }
   *     return kNo_Combine;
   * }
   * ```
   */
  private fun combineVertical(edge: SkAnalyticEdge?, last: SkAnalyticEdge?): Combine {
    TODO("Implement combineVertical")
  }

  /**
   * C++ original:
   * ```cpp
   * char* SkAnalyticEdgeBuilder::allocEdges(size_t n, size_t* size) {
   *     *size = sizeof(SkAnalyticEdge);
   *     return (char*)fAlloc.makeArrayDefault<SkAnalyticEdge>(n);
   * }
   * ```
   */
  public override fun allocEdges(n: ULong, size: ULong?): Char {
    TODO("Implement allocEdges")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect SkAnalyticEdgeBuilder::recoverClip(const SkIRect& src) const {
   *     return SkRect::Make(src);
   * }
   * ```
   */
  public override fun recoverClip(src: SkIRect): SkRect {
    TODO("Implement recoverClip")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkAnalyticEdgeBuilder::addLine(const SkPoint pts[]) {
   *     SkAnalyticEdge* edge = fAlloc.make<SkAnalyticEdge>();
   *     if (edge->setLine(pts[0], pts[1])) {
   *
   *         Combine combine = is_vertical(edge) && !fList.empty()
   *             ? this->combineVertical(edge, (SkAnalyticEdge*)fList.back())
   *             : kNo_Combine;
   *
   *         switch (combine) {
   *             case kTotal_Combine:    fList.pop_back();      break;
   *             case kPartial_Combine:                         break;
   *             case kNo_Combine:       fList.push_back(edge); break;
   *         }
   *     }
   * }
   * ```
   */
  public override fun addLine(pts: Array<SkPoint>) {
    TODO("Implement addLine")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkAnalyticEdgeBuilder::addQuad(const SkPoint pts[]) {
   *     SkAnalyticQuadraticEdge* edge = fAlloc.make<SkAnalyticQuadraticEdge>();
   *     if (edge->setQuadratic(pts)) {
   *         fList.push_back(edge);
   *     }
   * }
   * ```
   */
  public override fun addQuad(pts: Array<SkPoint>) {
    TODO("Implement addQuad")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkAnalyticEdgeBuilder::addCubic(const SkPoint pts[]) {
   *     SkAnalyticCubicEdge* edge = fAlloc.make<SkAnalyticCubicEdge>();
   *     if (edge->setCubic(pts)) {
   *         fList.push_back(edge);
   *     }
   * }
   * ```
   */
  public override fun addCubic(pts: Array<SkPoint>) {
    TODO("Implement addCubic")
  }

  /**
   * C++ original:
   * ```cpp
   * SkEdgeBuilder::Combine SkAnalyticEdgeBuilder::addPolyLine(const SkPoint pts[],
   *                                                           char* arg_edge, char** arg_edgePtr) {
   *     auto edge    = (SkAnalyticEdge*) arg_edge;
   *     auto edgePtr = (SkAnalyticEdge**)arg_edgePtr;
   *
   *     if (edge->setLine(pts[0], pts[1])) {
   *         return is_vertical(edge) && edgePtr > (SkAnalyticEdge**)fEdgeList
   *             ? this->combineVertical(edge, edgePtr[-1])
   *             : kNo_Combine;
   *     }
   *     return SkEdgeBuilder::kPartial_Combine;  // As above.
   * }
   * ```
   */
  public override fun addPolyLine(
    pts: Array<SkPoint>,
    edge: String?,
    edgePtr: Int?,
  ): Combine {
    TODO("Implement addPolyLine")
  }
}
