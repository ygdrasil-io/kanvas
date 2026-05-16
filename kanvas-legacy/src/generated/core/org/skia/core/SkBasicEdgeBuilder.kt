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
 * class SkBasicEdgeBuilder final : public SkEdgeBuilder {
 * public:
 *     explicit SkBasicEdgeBuilder() {}
 *
 *     SkEdge** edgeList() { return (SkEdge**)fEdgeList; }
 *
 * private:
 *     Combine combineVertical(const SkEdge* edge, SkEdge* last);
 *
 *     char* allocEdges(size_t, size_t*) override {
 *         SkDEBUGFAIL("Not implemented");
 *         return nullptr;
 *     }
 *
 *     SkRect recoverClip(const SkIRect&) const override;
 *
 *     void addLine (const SkPoint pts[]) override;
 *     void addQuad (const SkPoint pts[]) override;
 *     void addCubic(const SkPoint pts[]) override;
 *     Combine addPolyLine(const SkPoint pts[], char* edge, char** edgePtr) override {
 *         SkDEBUGFAIL("Not implemented");
 *         return kNo_Combine;
 *     }
 * }
 * ```
 */
public class SkBasicEdgeBuilder public constructor() : SkEdgeBuilder() {
  /**
   * C++ original:
   * ```cpp
   * SkEdge** edgeList() { return (SkEdge**)fEdgeList; }
   * ```
   */
  public fun edgeList(): SkEdge {
    TODO("Implement edgeList")
  }

  /**
   * C++ original:
   * ```cpp
   * SkEdgeBuilder::Combine SkBasicEdgeBuilder::combineVertical(const SkEdge* edge, SkEdge* last) {
   *     // We only consider edges that were originally lines to be vertical to avoid numerical issues
   *     // (crbug.com/1154864).
   *     if (last->fEdgeType != SkEdge::Type::kLine || last->fDxDy || edge->fX != last->fX) {
   *         return kNo_Combine;
   *     }
   *     if (edge->fWinding == last->fWinding) {
   *         if (edge->fLastY + 1 == last->fFirstY) {
   *             last->fFirstY = edge->fFirstY;
   *             return kPartial_Combine;
   *         }
   *         if (edge->fFirstY == last->fLastY + 1) {
   *             last->fLastY = edge->fLastY;
   *             return kPartial_Combine;
   *         }
   *         return kNo_Combine;
   *     }
   *     if (edge->fFirstY == last->fFirstY) {
   *         if (edge->fLastY == last->fLastY) {
   *             return kTotal_Combine;
   *         }
   *         if (edge->fLastY < last->fLastY) {
   *             last->fFirstY = edge->fLastY + 1;
   *             return kPartial_Combine;
   *         }
   *         last->fFirstY = last->fLastY + 1;
   *         last->fLastY = edge->fLastY;
   *         last->fWinding = edge->fWinding;
   *         return kPartial_Combine;
   *     }
   *     if (edge->fLastY == last->fLastY) {
   *         if (edge->fFirstY > last->fFirstY) {
   *             last->fLastY = edge->fFirstY - 1;
   *             return kPartial_Combine;
   *         }
   *         last->fLastY = last->fFirstY - 1;
   *         last->fFirstY = edge->fFirstY;
   *         last->fWinding = edge->fWinding;
   *         return kPartial_Combine;
   *     }
   *     return kNo_Combine;
   * }
   * ```
   */
  private fun combineVertical(edge: SkEdge?, last: SkEdge?): Combine {
    TODO("Implement combineVertical")
  }

  /**
   * C++ original:
   * ```cpp
   * char* allocEdges(size_t, size_t*) override {
   *         SkDEBUGFAIL("Not implemented");
   *         return nullptr;
   *     }
   * ```
   */
  public override fun allocEdges(param0: ULong, param1: ULong?): Char {
    TODO("Implement allocEdges")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect SkBasicEdgeBuilder::recoverClip(const SkIRect& src) const {
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
   * void SkBasicEdgeBuilder::addLine(const SkPoint pts[]) {
   *     SkEdge* edge = fAlloc.make<SkEdge>();
   *     if (edge->setLine(pts[0], pts[1])) {
   *         Combine combine = is_vertical(edge) && !fList.empty()
   *             ? this->combineVertical(edge, (SkEdge*)fList.back())
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
   * void SkBasicEdgeBuilder::addQuad(const SkPoint pts[]) {
   *     SkQuadraticEdge* edge = fAlloc.make<SkQuadraticEdge>();
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
   * void SkBasicEdgeBuilder::addCubic(const SkPoint pts[]) {
   *     SkCubicEdge* edge = fAlloc.make<SkCubicEdge>();
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
   * Combine addPolyLine(const SkPoint pts[], char* edge, char** edgePtr) override {
   *         SkDEBUGFAIL("Not implemented");
   *         return kNo_Combine;
   *     }
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
