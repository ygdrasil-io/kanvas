package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.String
import kotlin.ULong
import kotlin.Unit
import org.skia.math.SkIRect
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.memory.SkSTArenaAlloc512
import org.skia.memory.SkTDArray

/**
 * C++ original:
 * ```cpp
 * class SkEdgeBuilder {
 * public:
 *     int buildEdges(const SkPathRaw&, const SkIRect* shiftedClip);
 *     int buildEdges(const SkPath&, const SkIRect* shiftedClip);
 *
 * protected:
 *     SkEdgeBuilder() = default;
 *     virtual ~SkEdgeBuilder() = default;
 *
 *     // In general mode we allocate pointers in fList and fEdgeList points to its head.
 *     // In polygon mode we preallocated edges contiguously in fAlloc and fEdgeList points there.
 *     void**              fEdgeList = nullptr;
 *     SkTDArray<void*>    fList;
 *     SkSTArenaAlloc<512> fAlloc;
 *
 *     enum Combine {
 *         kNo_Combine,
 *         kPartial_Combine,
 *         kTotal_Combine
 *     };
 *
 * private:
 *     int build    (const SkPathRaw&, const SkIRect* clip, bool clipToTheRight);
 *     int buildPoly(const SkPathRaw&, const SkIRect* clip, bool clipToTheRight);
 *
 *     virtual char* allocEdges(size_t n, size_t* sizeof_edge) = 0;
 *     virtual SkRect recoverClip(const SkIRect&) const = 0;
 *
 *     virtual void addLine (const SkPoint pts[]) = 0;
 *     virtual void addQuad (const SkPoint pts[]) = 0;
 *     virtual void addCubic(const SkPoint pts[]) = 0;
 *     virtual Combine addPolyLine(const SkPoint pts[], char* edge, char** edgePtr) = 0;
 * }
 * ```
 */
public abstract class SkEdgeBuilder public constructor() {
  /**
   * C++ original:
   * ```cpp
   * void**              fEdgeList = nullptr
   * ```
   */
  protected var fEdgeList: Int? = TODO("Initialize fEdgeList")

  /**
   * C++ original:
   * ```cpp
   * SkTDArray<void*>    fList
   * ```
   */
  protected var fList: SkTDArray<Unit?> = TODO("Initialize fList")

  /**
   * C++ original:
   * ```cpp
   * SkSTArenaAlloc<512> fAlloc
   * ```
   */
  protected var fAlloc: SkSTArenaAlloc512 = TODO("Initialize fAlloc")

  /**
   * C++ original:
   * ```cpp
   * int SkEdgeBuilder::buildEdges(const SkPathRaw& raw,
   *                               const SkIRect* shiftedClip) {
   *     // If we're convex, then we need both edges, even if the right edge is past the clip.
   *     const bool canCullToTheRight = !raw.isKnownToBeConvex();
   *
   *     // We can use our buildPoly() optimization if all the segments are lines.
   *     // (Edges are homogeneous and stored contiguously in memory, no need for indirection.)
   *     const int count = SkPath::kLine_SegmentMask == raw.segmentMasks()
   *         ? this->buildPoly(raw, shiftedClip, canCullToTheRight)
   *         : this->build    (raw, shiftedClip, canCullToTheRight);
   *
   *     SkASSERT(count >= 0);
   *
   *     // If we can't cull to the right, we should have count > 1 (or 0).
   *     if (!canCullToTheRight) {
   *         SkASSERT(count != 1);
   *     }
   *     return count;
   * }
   * ```
   */
  public fun buildEdges(raw: SkPathRaw, shiftedClip: SkIRect?): Int {
    TODO("Implement buildEdges")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkEdgeBuilder::buildEdges(const SkPath& path, const SkIRect* shiftedClip) {
   *     if (auto raw = SkPathPriv::Raw(path, SkResolveConvexity::kYes)) {
   *         return buildEdges(*raw, shiftedClip);
   *     }
   *     return 0;   // no edges were built
   * }
   * ```
   */
  public fun buildEdges(path: SkPath, shiftedClip: SkIRect?): Int {
    TODO("Implement buildEdges")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkEdgeBuilder::build(const SkPathRaw& raw, const SkIRect* iclip, bool canCullToTheRight) {
   *     if (iclip) {
   *         SkRect clip = this->recoverClip(*iclip);
   *         struct Rec {
   *             SkEdgeBuilder* fBuilder;
   *             bool           fIsFinite;
   *         } rec = { this, true };
   *
   *         SkEdgeClipper::ClipPath(raw, clip, canCullToTheRight,
   *                                 [](SkEdgeClipper* clipper, bool, void* ctx) {
   *             Rec* rec = (Rec*)ctx;
   *             SkPoint      pts[4];
   *
   *             while (auto verb = clipper->next(pts)) {
   *                 const int count = SkPathPriv::PtsInIter(*verb);
   *                 if (!SkIsFinite(&pts[0].fX, count*2)) {
   *                     rec->fIsFinite = false;
   *                     return;
   *                 }
   *                 switch (*verb) {
   *                     case SkPathVerb::kLine:  rec->fBuilder->addLine (pts); break;
   *                     case SkPathVerb::kQuad:  rec->fBuilder->addQuad (pts); break;
   *                     case SkPathVerb::kCubic: rec->fBuilder->addCubic(pts); break;
   *                     default: break;
   *                 }
   *             }
   *         }, &rec);
   *         fEdgeList = fList.begin();
   *         return rec.fIsFinite ? fList.size() : 0;
   *     }
   *
   *     SkPathEdgeIter iter(raw);
   *     SkAutoConicToQuads quadder;
   *     constexpr float kConicTol = 0.25f;
   *     SkPoint monoY[10];
   *     SkPoint monoX[5];
   *     auto handle_quad = [this, &monoX](const SkPoint pts[3]) {
   *         int n = SkChopQuadAtYExtrema(pts, monoX);
   *         for (int i = 0; i <= n; i++) {
   *             this->addQuad(&monoX[i * 2]);
   *         }
   *     };
   *
   *     while (auto e = iter.next()) {
   *         switch (e.fEdge) {
   *             case SkPathEdgeIter::Edge::kLine:
   *                 this->addLine(e.fPts);
   *                 break;
   *             case SkPathEdgeIter::Edge::kQuad: {
   *                 handle_quad(e.fPts);
   *                 break;
   *             }
   *             case SkPathEdgeIter::Edge::kConic: {
   *                 const SkPoint* quadPts =
   *                         quadder.computeQuads(e.fPts, iter.conicWeight(), kConicTol);
   *                 for (int i = 0; i < quadder.countQuads(); ++i) {
   *                     handle_quad(quadPts);
   *                     quadPts += 2;
   *                 }
   *             } break;
   *             case SkPathEdgeIter::Edge::kCubic: {
   *                 int n = SkChopCubicAtYExtrema(e.fPts, monoY);
   *                 for (int i = 0; i <= n; i++) {
   *                     this->addCubic(&monoY[i * 3]);
   *                 }
   *                 break;
   *             }
   *             default:
   *                 SkDEBUGFAIL("Unknown edge type");
   *                 break;
   *         }
   *     }
   *     fEdgeList = fList.begin();
   *     return fList.size();
   * }
   * ```
   */
  private fun build(
    raw: SkPathRaw,
    clip: SkIRect?,
    clipToTheRight: Boolean,
  ): Int {
    TODO("Implement build")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkEdgeBuilder::buildPoly(const SkPathRaw& raw, const SkIRect* iclip, bool canCullToTheRight) {
   *     size_t maxEdgeCount = raw.fPoints.size();
   *     if (iclip) {
   *         // clipping can turn 1 line into (up to) kMaxClippedLineSegments, since
   *         // we turn portions that are clipped out on the left/right into vertical
   *         // segments.
   *         SkSafeMath safe;
   *         maxEdgeCount = safe.mul(maxEdgeCount, SkLineClipper::kMaxClippedLineSegments);
   *         if (!safe) {
   *             return 0;
   *         }
   *     }
   *
   *     SkPathEdgeIter iter(raw);
   *     if (iclip) {
   *         SkRect clip = this->recoverClip(*iclip);
   *
   *         while (auto e = iter.next()) {
   *             switch (e.fEdge) {
   *                 case SkPathEdgeIter::Edge::kLine: {
   *                     SkPoint lines[SkLineClipper::kMaxPoints];
   *                     int lineCount = SkLineClipper::ClipLine(e.fPts, clip, lines, canCullToTheRight);
   *                     SkASSERT(lineCount <= SkLineClipper::kMaxClippedLineSegments);
   *                     for (int i = 0; i < lineCount; i++) {
   *                         this->addLine(lines + i);
   *                     }
   *                     break;
   *                 }
   *                 default:
   *                     SkDEBUGFAIL("unexpected verb");
   *                     break;
   *             }
   *         }
   *     } else {
   *         while (auto e = iter.next()) {
   *             switch (e.fEdge) {
   *                 case SkPathEdgeIter::Edge::kLine: {
   *                     this->addLine(e.fPts);
   *                     break;
   *                 }
   *                 default:
   *                     SkDEBUGFAIL("unexpected verb");
   *                     break;
   *             }
   *         }
   *     }
   *     fEdgeList = fList.begin();
   *     return fList.size();
   * }
   * ```
   */
  private fun buildPoly(
    raw: SkPathRaw,
    clip: SkIRect?,
    clipToTheRight: Boolean,
  ): Int {
    TODO("Implement buildPoly")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual char* allocEdges(size_t n, size_t* sizeof_edge) = 0
   * ```
   */
  private abstract fun allocEdges(n: ULong, sizeofEdge: ULong?): Char

  /**
   * C++ original:
   * ```cpp
   * virtual SkRect recoverClip(const SkIRect&) const = 0
   * ```
   */
  private abstract fun recoverClip(param0: SkIRect): SkRect

  /**
   * C++ original:
   * ```cpp
   * virtual void addLine (const SkPoint pts[]) = 0
   * ```
   */
  private abstract fun addLine(pts: Array<SkPoint>)

  /**
   * C++ original:
   * ```cpp
   * virtual void addQuad (const SkPoint pts[]) = 0
   * ```
   */
  private abstract fun addQuad(pts: Array<SkPoint>)

  /**
   * C++ original:
   * ```cpp
   * virtual void addCubic(const SkPoint pts[]) = 0
   * ```
   */
  private abstract fun addCubic(pts: Array<SkPoint>)

  /**
   * C++ original:
   * ```cpp
   * virtual Combine addPolyLine(const SkPoint pts[], char* edge, char** edgePtr) = 0
   * ```
   */
  private abstract fun addPolyLine(
    pts: Array<SkPoint>,
    edge: String?,
    edgePtr: Int?,
  ): Combine

  public enum class Combine {
    kNo_Combine,
    kPartial_Combine,
    kTotal_Combine,
  }
}
