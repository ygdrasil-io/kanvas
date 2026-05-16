package org.skia.utils

import kotlin.Boolean
import kotlin.Int
import kotlin.UShort
import org.skia.core.SkTInternalLList
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkVector
import org.skia.memory.SkTDArray

/**
 * C++ original:
 * ```cpp
 * class ReflexHash {
 * public:
 *     bool init(const SkRect& bounds, int vertexCount) {
 *         fBounds = bounds;
 *         fNumVerts = 0;
 *         SkScalar width = bounds.width();
 *         SkScalar height = bounds.height();
 *         if (!SkIsFinite(width, height)) {
 *             return false;
 *         }
 *
 *         // We want vertexCount grid cells, roughly distributed to match the bounds ratio
 *         SkScalar hCount = SkScalarSqrt(sk_ieee_float_divide(vertexCount*width, height));
 *         if (!SkIsFinite(hCount)) {
 *             return false;
 *         }
 *         fHCount = std::max(std::min(SkScalarRoundToInt(hCount), vertexCount), 1);
 *         fVCount = vertexCount/fHCount;
 *         fGridConversion.set(sk_ieee_float_divide(fHCount - 0.001f, width),
 *                             sk_ieee_float_divide(fVCount - 0.001f, height));
 *         if (!fGridConversion.isFinite()) {
 *             return false;
 *         }
 *
 *         fGrid.resize(fHCount*fVCount);
 *         for (int i = 0; i < fGrid.size(); ++i) {
 *             fGrid[i].reset();
 *         }
 *
 *         return true;
 *     }
 *
 *     void add(TriangulationVertex* v) {
 *         int index = hash(v);
 *         fGrid[index].addToTail(v);
 *         ++fNumVerts;
 *     }
 *
 *     void remove(TriangulationVertex* v) {
 *         int index = hash(v);
 *         fGrid[index].remove(v);
 *         --fNumVerts;
 *     }
 *
 *     bool checkTriangle(const SkPoint& p0, const SkPoint& p1, const SkPoint& p2,
 *                        uint16_t ignoreIndex0, uint16_t ignoreIndex1) const {
 *         if (!fNumVerts) {
 *             return false;
 *         }
 *
 *         SkRect triBounds;
 *         compute_triangle_bounds(p0, p1, p2, &triBounds);
 *         int h0 = (triBounds.fLeft - fBounds.fLeft)*fGridConversion.fX;
 *         int h1 = (triBounds.fRight - fBounds.fLeft)*fGridConversion.fX;
 *         int v0 = (triBounds.fTop - fBounds.fTop)*fGridConversion.fY;
 *         int v1 = (triBounds.fBottom - fBounds.fTop)*fGridConversion.fY;
 *
 *         for (int v = v0; v <= v1; ++v) {
 *             for (int h = h0; h <= h1; ++h) {
 *                 int i = v * fHCount + h;
 *                 for (SkTInternalLList<TriangulationVertex>::Iter reflexIter = fGrid[i].begin();
 *                      reflexIter != fGrid[i].end(); ++reflexIter) {
 *                     TriangulationVertex* reflexVertex = *reflexIter;
 *                     if (reflexVertex->fIndex != ignoreIndex0 &&
 *                         reflexVertex->fIndex != ignoreIndex1 &&
 *                         point_in_triangle(p0, p1, p2, reflexVertex->fPosition)) {
 *                         return true;
 *                     }
 *                 }
 *
 *             }
 *         }
 *
 *         return false;
 *     }
 *
 * private:
 *     int hash(TriangulationVertex* vert) const {
 *         int h = (vert->fPosition.fX - fBounds.fLeft)*fGridConversion.fX;
 *         int v = (vert->fPosition.fY - fBounds.fTop)*fGridConversion.fY;
 *         SkASSERT(v*fHCount + h >= 0);
 *         return v*fHCount + h;
 *     }
 *
 *     SkRect fBounds;
 *     int fHCount;
 *     int fVCount;
 *     int fNumVerts;
 *     // converts distance from the origin to a grid location (when cast to int)
 *     SkVector fGridConversion;
 *     SkTDArray<SkTInternalLList<TriangulationVertex>> fGrid;
 * }
 * ```
 */
public data class ReflexHash public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkRect fBounds
   * ```
   */
  private var fBounds: SkRect,
  /**
   * C++ original:
   * ```cpp
   * int fHCount
   * ```
   */
  private var fHCount: Int,
  /**
   * C++ original:
   * ```cpp
   * int fVCount
   * ```
   */
  private var fVCount: Int,
  /**
   * C++ original:
   * ```cpp
   * int fNumVerts
   * ```
   */
  private var fNumVerts: Int,
  /**
   * C++ original:
   * ```cpp
   * SkVector fGridConversion
   * ```
   */
  private var fGridConversion: SkVector,
  /**
   * C++ original:
   * ```cpp
   * SkTDArray<SkTInternalLList<TriangulationVertex>> fGrid
   * ```
   */
  private var fGrid: SkTDArray<SkTInternalLList<TriangulationVertex>>,
) {
  /**
   * C++ original:
   * ```cpp
   * bool init(const SkRect& bounds, int vertexCount) {
   *         fBounds = bounds;
   *         fNumVerts = 0;
   *         SkScalar width = bounds.width();
   *         SkScalar height = bounds.height();
   *         if (!SkIsFinite(width, height)) {
   *             return false;
   *         }
   *
   *         // We want vertexCount grid cells, roughly distributed to match the bounds ratio
   *         SkScalar hCount = SkScalarSqrt(sk_ieee_float_divide(vertexCount*width, height));
   *         if (!SkIsFinite(hCount)) {
   *             return false;
   *         }
   *         fHCount = std::max(std::min(SkScalarRoundToInt(hCount), vertexCount), 1);
   *         fVCount = vertexCount/fHCount;
   *         fGridConversion.set(sk_ieee_float_divide(fHCount - 0.001f, width),
   *                             sk_ieee_float_divide(fVCount - 0.001f, height));
   *         if (!fGridConversion.isFinite()) {
   *             return false;
   *         }
   *
   *         fGrid.resize(fHCount*fVCount);
   *         for (int i = 0; i < fGrid.size(); ++i) {
   *             fGrid[i].reset();
   *         }
   *
   *         return true;
   *     }
   * ```
   */
  public fun `init`(bounds: SkRect, vertexCount: Int): Boolean {
    TODO("Implement init")
  }

  /**
   * C++ original:
   * ```cpp
   * void add(TriangulationVertex* v) {
   *         int index = hash(v);
   *         fGrid[index].addToTail(v);
   *         ++fNumVerts;
   *     }
   * ```
   */
  public fun add(v: TriangulationVertex?) {
    TODO("Implement add")
  }

  /**
   * C++ original:
   * ```cpp
   * void remove(TriangulationVertex* v) {
   *         int index = hash(v);
   *         fGrid[index].remove(v);
   *         --fNumVerts;
   *     }
   * ```
   */
  public fun remove(v: TriangulationVertex?) {
    TODO("Implement remove")
  }

  /**
   * C++ original:
   * ```cpp
   * bool checkTriangle(const SkPoint& p0, const SkPoint& p1, const SkPoint& p2,
   *                        uint16_t ignoreIndex0, uint16_t ignoreIndex1) const {
   *         if (!fNumVerts) {
   *             return false;
   *         }
   *
   *         SkRect triBounds;
   *         compute_triangle_bounds(p0, p1, p2, &triBounds);
   *         int h0 = (triBounds.fLeft - fBounds.fLeft)*fGridConversion.fX;
   *         int h1 = (triBounds.fRight - fBounds.fLeft)*fGridConversion.fX;
   *         int v0 = (triBounds.fTop - fBounds.fTop)*fGridConversion.fY;
   *         int v1 = (triBounds.fBottom - fBounds.fTop)*fGridConversion.fY;
   *
   *         for (int v = v0; v <= v1; ++v) {
   *             for (int h = h0; h <= h1; ++h) {
   *                 int i = v * fHCount + h;
   *                 for (SkTInternalLList<TriangulationVertex>::Iter reflexIter = fGrid[i].begin();
   *                      reflexIter != fGrid[i].end(); ++reflexIter) {
   *                     TriangulationVertex* reflexVertex = *reflexIter;
   *                     if (reflexVertex->fIndex != ignoreIndex0 &&
   *                         reflexVertex->fIndex != ignoreIndex1 &&
   *                         point_in_triangle(p0, p1, p2, reflexVertex->fPosition)) {
   *                         return true;
   *                     }
   *                 }
   *
   *             }
   *         }
   *
   *         return false;
   *     }
   * ```
   */
  public fun checkTriangle(
    p0: SkPoint,
    p1: SkPoint,
    p2: SkPoint,
    ignoreIndex0: UShort,
    ignoreIndex1: UShort,
  ): Boolean {
    TODO("Implement checkTriangle")
  }

  /**
   * C++ original:
   * ```cpp
   * int hash(TriangulationVertex* vert) const {
   *         int h = (vert->fPosition.fX - fBounds.fLeft)*fGridConversion.fX;
   *         int v = (vert->fPosition.fY - fBounds.fTop)*fGridConversion.fY;
   *         SkASSERT(v*fHCount + h >= 0);
   *         return v*fHCount + h;
   *     }
   * ```
   */
  private fun hash(vert: TriangulationVertex?): Int {
    TODO("Implement hash")
  }
}
