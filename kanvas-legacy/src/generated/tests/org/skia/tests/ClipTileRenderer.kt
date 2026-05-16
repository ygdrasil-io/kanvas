package org.skia.tests

import kotlin.Array
import kotlin.BooleanArray
import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.foundation.SkRefCntBase
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class ClipTileRenderer : public SkRefCntBase {
 * public:
 *     // Draw the base rect, possibly clipped by 'clip' if that is not null. The edges to antialias
 *     // are specified in 'edgeAA' (to make manipulation easier than an unsigned bitfield). 'tileID'
 *     // represents the location of rect within the tile grid, 'quadID' is the unique ID of the clip
 *     // region within the tile (reset for each tile).
 *     //
 *     // The edgeAA order matches that of clip, so it refers to top, right, bottom, left.
 *     // Return draw count
 *     virtual int drawTile(SkCanvas* canvas, const SkRect& rect, const SkPoint clip[4],
 *                           const bool edgeAA[4], int tileID, int quadID) = 0;
 *
 *     virtual void drawBanner(SkCanvas* canvas) = 0;
 *
 *     // Return draw count
 *     virtual int drawTiles(SkCanvas* canvas) {
 *         // All three lines in a list
 *         SkPoint lines[6];
 *         clipping_line_segment(kClipP1, kClipP2, lines);
 *         clipping_line_segment(kClipP2, kClipP3, lines + 2);
 *         clipping_line_segment(kClipP3, kClipP1, lines + 4);
 *
 *         bool edgeAA[4];
 *         int tileID = 0;
 *         int drawCount = 0;
 *         for (int i = 0; i < kRowCount; ++i) {
 *             for (int j = 0; j < kColCount; ++j) {
 *                 // The unclipped tile geometry
 *                 SkRect tile = SkRect::MakeXYWH(j * kTileWidth, i * kTileHeight,
 *                                                kTileWidth, kTileHeight);
 *                 // Base edge AA flags if there are no clips; clipped lines will only turn off edges
 *                 edgeAA[0] = i == 0;             // Top
 *                 edgeAA[1] = j == kColCount - 1; // Right
 *                 edgeAA[2] = i == kRowCount - 1; // Bottom
 *                 edgeAA[3] = j == 0;             // Left
 *
 *                 // Now clip against the 3 lines formed by kClipPx and split into general purpose
 *                 // quads as needed.
 *                 int quadCount = 0;
 *                 drawCount += this->clipTile(canvas, tileID, tile, nullptr, edgeAA, lines, 3,
 *                                             &quadCount);
 *                 tileID++;
 *             }
 *         }
 *
 *         return drawCount;
 *     }
 *
 * protected:
 *     SkCanvas::QuadAAFlags maskToFlags(const bool edgeAA[4]) const {
 *         unsigned flags = (edgeAA[0] * SkCanvas::kTop_QuadAAFlag) |
 *                          (edgeAA[1] * SkCanvas::kRight_QuadAAFlag) |
 *                          (edgeAA[2] * SkCanvas::kBottom_QuadAAFlag) |
 *                          (edgeAA[3] * SkCanvas::kLeft_QuadAAFlag);
 *         return static_cast<SkCanvas::QuadAAFlags>(flags);
 *     }
 *
 *     // Recursively splits the quadrilateral against the segments stored in 'lines', which must be
 *     // 2 * lineCount long. Increments 'quadCount' for each split quadrilateral, and invokes the
 *     // drawTile at leaves.
 *     int clipTile(SkCanvas* canvas, int tileID, const SkRect& baseRect, const SkPoint quad[4],
 *                   const bool edgeAA[4], const SkPoint lines[], int lineCount, int* quadCount) {
 *         if (lineCount == 0) {
 *             // No lines, so end recursion by drawing the tile. If the tile was never split then
 *             // 'quad' remains null so that drawTile() can differentiate how it should draw.
 *             int draws = this->drawTile(canvas, baseRect, quad, edgeAA, tileID, *quadCount);
 *             *quadCount = *quadCount + 1;
 *             return draws;
 *         }
 *
 *         static constexpr int kTL = 0; // Top-left point index in points array
 *         static constexpr int kTR = 1; // Top-right point index in points array
 *         static constexpr int kBR = 2; // Bottom-right point index in points array
 *         static constexpr int kBL = 3; // Bottom-left point index in points array
 *         static constexpr int kS0 = 4; // First split point index in points array
 *         static constexpr int kS1 = 5; // Second split point index in points array
 *
 *         SkPoint points[6];
 *         if (quad) {
 *             // Copy the original 4 points into set of points to consider
 *             for (int i = 0; i < 4; ++i) {
 *                 points[i] = quad[i];
 *             }
 *         } else {
 *             //  Haven't been split yet, so fill in based on the rect
 *             baseRect.copyToQuad(points);
 *         }
 *
 *         // Consider the first line against the 4 quad edges in tile, which should have 0,1, or 2
 *         // intersection points since the tile is convex.
 *         int splitIndices[2]; // Edge that was intersected
 *         int intersectionCount = 0;
 *         for (int i = 0; i < 4; ++i) {
 *             SkPoint intersect;
 *             if (intersect_line_segments(points[i], points[i == 3 ? 0 : i + 1],
 *                                         lines[0], lines[1], &intersect)) {
 *                 // If the intersected point is the same as the last found intersection, the line
 *                 // runs through a vertex, so don't double count it
 *                 bool duplicate = false;
 *                 for (int j = 0; j < intersectionCount; ++j) {
 *                     if (SkScalarNearlyZero((intersect - points[kS0 + j]).length())) {
 *                         duplicate = true;
 *                         break;
 *                     }
 *                 }
 *                 if (!duplicate) {
 *                     points[kS0 + intersectionCount] = intersect;
 *                     splitIndices[intersectionCount] = i;
 *                     intersectionCount++;
 *                 }
 *             }
 *         }
 *
 *         if (intersectionCount < 2) {
 *             // Either the first line never intersected the quad (count == 0), or it intersected at a
 *             // single vertex without going through quad area (count == 1), so check next line
 *             return this->clipTile(
 *                     canvas, tileID, baseRect, quad, edgeAA, lines + 2, lineCount - 1, quadCount);
 *         }
 *
 *         SkASSERT(intersectionCount == 2);
 *         // Split the tile points into 2+ sub quads and recurse to the next lines, which may or may
 *         // not further split the tile. Since the configurations are relatively simple, the possible
 *         // splits are hardcoded below; subtile quad orderings are such that the sub tiles remain in
 *         // clockwise order and match expected edges for QuadAAFlags. subtile indices refer to the
 *         // 6-element 'points' array.
 *         STArray<3, std::array<int, 4>> subtiles;
 *         int s2 = -1; // Index of an original vertex chosen for a artificial split
 *         if (splitIndices[1] - splitIndices[0] == 2) {
 *             // Opposite edges, so the split trivially forms 2 sub quads
 *             if (splitIndices[0] == 0) {
 *                 subtiles.push_back({{kTL, kS0, kS1, kBL}});
 *                 subtiles.push_back({{kS0, kTR, kBR, kS1}});
 *             } else {
 *                 subtiles.push_back({{kTL, kTR, kS0, kS1}});
 *                 subtiles.push_back({{kS1, kS0, kBR, kBL}});
 *             }
 *         } else {
 *             // Adjacent edges, which makes for a more complicated split, since it forms a degenerate
 *             // quad (triangle) and a pentagon that must be artificially split. The pentagon is split
 *             // using one of the original vertices (remembered in 's2'), which adds an additional
 *             // degenerate quad, but ensures there are no T-junctions.
 *             switch(splitIndices[0]) {
 *                 case 0:
 *                     // Could be connected to edge 1 or edge 3
 *                     if (splitIndices[1] == 1) {
 *                         s2 = kBL;
 *                         subtiles.push_back({{kS0, kTR, kS1, kS0}}); // degenerate
 *                         subtiles.push_back({{kTL, kS0, edgeAA[0] ? kS0 : kBL, kBL}}); // degenerate
 *                         subtiles.push_back({{kS0, kS1, kBR, kBL}});
 *                     } else {
 *                         SkASSERT(splitIndices[1] == 3);
 *                         s2 = kBR;
 *                         subtiles.push_back({{kTL, kS0, kS1, kS1}}); // degenerate
 *                         subtiles.push_back({{kS1, edgeAA[3] ? kS1 : kBR, kBR, kBL}}); // degenerate
 *                         subtiles.push_back({{kS0, kTR, kBR, kS1}});
 *                     }
 *                     break;
 *                 case 1:
 *                     // Edge 0 handled above, should only be connected to edge 2
 *                     SkASSERT(splitIndices[1] == 2);
 *                     s2 = kTL;
 *                     subtiles.push_back({{kS0, kS0, kBR, kS1}}); // degenerate
 *                     subtiles.push_back({{kTL, kTR, kS0, edgeAA[1] ? kS0 : kTL}}); // degenerate
 *                     subtiles.push_back({{kTL, kS0, kS1, kBL}});
 *                     break;
 *                 case 2:
 *                     // Edge 1 handled above, should only be connected to edge 3
 *                     SkASSERT(splitIndices[1] == 3);
 *                     s2 = kTR;
 *                     subtiles.push_back({{kS1, kS0, kS0, kBL}}); // degenerate
 *                     subtiles.push_back({{edgeAA[2] ? kS0 : kTR, kTR, kBR, kS0}}); // degenerate
 *                     subtiles.push_back({{kTL, kTR, kS0, kS1}});
 *                     break;
 *                 case 3:
 *                     // Fall through, an adjacent edge split that hits edge 3 should have first found
 *                     // been found with edge 0 or edge 2 for the other end
 *                 default:
 *                     SkASSERT(false);
 *                     return 0;
 *             }
 *         }
 *
 *         SkPoint sub[4];
 *         bool subAA[4];
 *         int draws = 0;
 *         for (int i = 0; i < subtiles.size(); ++i) {
 *             // Fill in the quad points and update edge AA rules for new interior edges
 *             for (int j = 0; j < 4; ++j) {
 *                 int p = subtiles[i][j];
 *                 sub[j] = points[p];
 *
 *                 int np = j == 3 ? subtiles[i][0] : subtiles[i][j + 1];
 *                 // The "new" edges are the edges that connect between the two split points or
 *                 // between a split point and the chosen s2 point. Otherwise the edge remains aligned
 *                 // with the original shape, so should preserve the AA setting.
 *                 if ((p >= kS0 && (np == s2 || np >= kS0)) ||
 *                     ((np >= kS0) && (p == s2 || p >= kS0))) {
 *                     // New edge
 *                     subAA[j] = false;
 *                 } else {
 *                     // The subtiles indices were arranged so that their edge ordering was still top,
 *                     // right, bottom, left so 'j' can be used to access edgeAA
 *                     subAA[j] = edgeAA[j];
 *                 }
 *             }
 *
 *             // Split the sub quad with the next line
 *             draws += this->clipTile(canvas, tileID, baseRect, sub, subAA, lines + 2, lineCount - 1,
 *                                     quadCount);
 *         }
 *         return draws;
 *     }
 * }
 * ```
 */
public abstract class ClipTileRenderer : SkRefCntBase() {
  /**
   * C++ original:
   * ```cpp
   * virtual int drawTile(SkCanvas* canvas, const SkRect& rect, const SkPoint clip[4],
   *                           const bool edgeAA[4], int tileID, int quadID) = 0
   * ```
   */
  public abstract fun drawTile(
    canvas: SkCanvas?,
    rect: SkRect,
    clip: Array<SkPoint>,
    edgeAA: BooleanArray,
    tileID: Int,
    quadID: Int,
  ): Int

  /**
   * C++ original:
   * ```cpp
   * virtual void drawBanner(SkCanvas* canvas) = 0
   * ```
   */
  public abstract fun drawBanner(canvas: SkCanvas?)

  /**
   * C++ original:
   * ```cpp
   * virtual int drawTiles(SkCanvas* canvas) {
   *         // All three lines in a list
   *         SkPoint lines[6];
   *         clipping_line_segment(kClipP1, kClipP2, lines);
   *         clipping_line_segment(kClipP2, kClipP3, lines + 2);
   *         clipping_line_segment(kClipP3, kClipP1, lines + 4);
   *
   *         bool edgeAA[4];
   *         int tileID = 0;
   *         int drawCount = 0;
   *         for (int i = 0; i < kRowCount; ++i) {
   *             for (int j = 0; j < kColCount; ++j) {
   *                 // The unclipped tile geometry
   *                 SkRect tile = SkRect::MakeXYWH(j * kTileWidth, i * kTileHeight,
   *                                                kTileWidth, kTileHeight);
   *                 // Base edge AA flags if there are no clips; clipped lines will only turn off edges
   *                 edgeAA[0] = i == 0;             // Top
   *                 edgeAA[1] = j == kColCount - 1; // Right
   *                 edgeAA[2] = i == kRowCount - 1; // Bottom
   *                 edgeAA[3] = j == 0;             // Left
   *
   *                 // Now clip against the 3 lines formed by kClipPx and split into general purpose
   *                 // quads as needed.
   *                 int quadCount = 0;
   *                 drawCount += this->clipTile(canvas, tileID, tile, nullptr, edgeAA, lines, 3,
   *                                             &quadCount);
   *                 tileID++;
   *             }
   *         }
   *
   *         return drawCount;
   *     }
   * ```
   */
  public open fun drawTiles(canvas: SkCanvas?): Int {
    TODO("Implement drawTiles")
  }

  /**
   * C++ original:
   * ```cpp
   * SkCanvas::QuadAAFlags maskToFlags(const bool edgeAA[4]) const {
   *         unsigned flags = (edgeAA[0] * SkCanvas::kTop_QuadAAFlag) |
   *                          (edgeAA[1] * SkCanvas::kRight_QuadAAFlag) |
   *                          (edgeAA[2] * SkCanvas::kBottom_QuadAAFlag) |
   *                          (edgeAA[3] * SkCanvas::kLeft_QuadAAFlag);
   *         return static_cast<SkCanvas::QuadAAFlags>(flags);
   *     }
   * ```
   */
  protected fun maskToFlags(edgeAA: BooleanArray): SkCanvas.QuadAAFlags {
    TODO("Implement maskToFlags")
  }

  /**
   * C++ original:
   * ```cpp
   * int clipTile(SkCanvas* canvas, int tileID, const SkRect& baseRect, const SkPoint quad[4],
   *                   const bool edgeAA[4], const SkPoint lines[], int lineCount, int* quadCount) {
   *         if (lineCount == 0) {
   *             // No lines, so end recursion by drawing the tile. If the tile was never split then
   *             // 'quad' remains null so that drawTile() can differentiate how it should draw.
   *             int draws = this->drawTile(canvas, baseRect, quad, edgeAA, tileID, *quadCount);
   *             *quadCount = *quadCount + 1;
   *             return draws;
   *         }
   *
   *         static constexpr int kTL = 0; // Top-left point index in points array
   *         static constexpr int kTR = 1; // Top-right point index in points array
   *         static constexpr int kBR = 2; // Bottom-right point index in points array
   *         static constexpr int kBL = 3; // Bottom-left point index in points array
   *         static constexpr int kS0 = 4; // First split point index in points array
   *         static constexpr int kS1 = 5; // Second split point index in points array
   *
   *         SkPoint points[6];
   *         if (quad) {
   *             // Copy the original 4 points into set of points to consider
   *             for (int i = 0; i < 4; ++i) {
   *                 points[i] = quad[i];
   *             }
   *         } else {
   *             //  Haven't been split yet, so fill in based on the rect
   *             baseRect.copyToQuad(points);
   *         }
   *
   *         // Consider the first line against the 4 quad edges in tile, which should have 0,1, or 2
   *         // intersection points since the tile is convex.
   *         int splitIndices[2]; // Edge that was intersected
   *         int intersectionCount = 0;
   *         for (int i = 0; i < 4; ++i) {
   *             SkPoint intersect;
   *             if (intersect_line_segments(points[i], points[i == 3 ? 0 : i + 1],
   *                                         lines[0], lines[1], &intersect)) {
   *                 // If the intersected point is the same as the last found intersection, the line
   *                 // runs through a vertex, so don't double count it
   *                 bool duplicate = false;
   *                 for (int j = 0; j < intersectionCount; ++j) {
   *                     if (SkScalarNearlyZero((intersect - points[kS0 + j]).length())) {
   *                         duplicate = true;
   *                         break;
   *                     }
   *                 }
   *                 if (!duplicate) {
   *                     points[kS0 + intersectionCount] = intersect;
   *                     splitIndices[intersectionCount] = i;
   *                     intersectionCount++;
   *                 }
   *             }
   *         }
   *
   *         if (intersectionCount < 2) {
   *             // Either the first line never intersected the quad (count == 0), or it intersected at a
   *             // single vertex without going through quad area (count == 1), so check next line
   *             return this->clipTile(
   *                     canvas, tileID, baseRect, quad, edgeAA, lines + 2, lineCount - 1, quadCount);
   *         }
   *
   *         SkASSERT(intersectionCount == 2);
   *         // Split the tile points into 2+ sub quads and recurse to the next lines, which may or may
   *         // not further split the tile. Since the configurations are relatively simple, the possible
   *         // splits are hardcoded below; subtile quad orderings are such that the sub tiles remain in
   *         // clockwise order and match expected edges for QuadAAFlags. subtile indices refer to the
   *         // 6-element 'points' array.
   *         STArray<3, std::array<int, 4>> subtiles;
   *         int s2 = -1; // Index of an original vertex chosen for a artificial split
   *         if (splitIndices[1] - splitIndices[0] == 2) {
   *             // Opposite edges, so the split trivially forms 2 sub quads
   *             if (splitIndices[0] == 0) {
   *                 subtiles.push_back({{kTL, kS0, kS1, kBL}});
   *                 subtiles.push_back({{kS0, kTR, kBR, kS1}});
   *             } else {
   *                 subtiles.push_back({{kTL, kTR, kS0, kS1}});
   *                 subtiles.push_back({{kS1, kS0, kBR, kBL}});
   *             }
   *         } else {
   *             // Adjacent edges, which makes for a more complicated split, since it forms a degenerate
   *             // quad (triangle) and a pentagon that must be artificially split. The pentagon is split
   *             // using one of the original vertices (remembered in 's2'), which adds an additional
   *             // degenerate quad, but ensures there are no T-junctions.
   *             switch(splitIndices[0]) {
   *                 case 0:
   *                     // Could be connected to edge 1 or edge 3
   *                     if (splitIndices[1] == 1) {
   *                         s2 = kBL;
   *                         subtiles.push_back({{kS0, kTR, kS1, kS0}}); // degenerate
   *                         subtiles.push_back({{kTL, kS0, edgeAA[0] ? kS0 : kBL, kBL}}); // degenerate
   *                         subtiles.push_back({{kS0, kS1, kBR, kBL}});
   *                     } else {
   *                         SkASSERT(splitIndices[1] == 3);
   *                         s2 = kBR;
   *                         subtiles.push_back({{kTL, kS0, kS1, kS1}}); // degenerate
   *                         subtiles.push_back({{kS1, edgeAA[3] ? kS1 : kBR, kBR, kBL}}); // degenerate
   *                         subtiles.push_back({{kS0, kTR, kBR, kS1}});
   *                     }
   *                     break;
   *                 case 1:
   *                     // Edge 0 handled above, should only be connected to edge 2
   *                     SkASSERT(splitIndices[1] == 2);
   *                     s2 = kTL;
   *                     subtiles.push_back({{kS0, kS0, kBR, kS1}}); // degenerate
   *                     subtiles.push_back({{kTL, kTR, kS0, edgeAA[1] ? kS0 : kTL}}); // degenerate
   *                     subtiles.push_back({{kTL, kS0, kS1, kBL}});
   *                     break;
   *                 case 2:
   *                     // Edge 1 handled above, should only be connected to edge 3
   *                     SkASSERT(splitIndices[1] == 3);
   *                     s2 = kTR;
   *                     subtiles.push_back({{kS1, kS0, kS0, kBL}}); // degenerate
   *                     subtiles.push_back({{edgeAA[2] ? kS0 : kTR, kTR, kBR, kS0}}); // degenerate
   *                     subtiles.push_back({{kTL, kTR, kS0, kS1}});
   *                     break;
   *                 case 3:
   *                     // Fall through, an adjacent edge split that hits edge 3 should have first found
   *                     // been found with edge 0 or edge 2 for the other end
   *                 default:
   *                     SkASSERT(false);
   *                     return 0;
   *             }
   *         }
   *
   *         SkPoint sub[4];
   *         bool subAA[4];
   *         int draws = 0;
   *         for (int i = 0; i < subtiles.size(); ++i) {
   *             // Fill in the quad points and update edge AA rules for new interior edges
   *             for (int j = 0; j < 4; ++j) {
   *                 int p = subtiles[i][j];
   *                 sub[j] = points[p];
   *
   *                 int np = j == 3 ? subtiles[i][0] : subtiles[i][j + 1];
   *                 // The "new" edges are the edges that connect between the two split points or
   *                 // between a split point and the chosen s2 point. Otherwise the edge remains aligned
   *                 // with the original shape, so should preserve the AA setting.
   *                 if ((p >= kS0 && (np == s2 || np >= kS0)) ||
   *                     ((np >= kS0) && (p == s2 || p >= kS0))) {
   *                     // New edge
   *                     subAA[j] = false;
   *                 } else {
   *                     // The subtiles indices were arranged so that their edge ordering was still top,
   *                     // right, bottom, left so 'j' can be used to access edgeAA
   *                     subAA[j] = edgeAA[j];
   *                 }
   *             }
   *
   *             // Split the sub quad with the next line
   *             draws += this->clipTile(canvas, tileID, baseRect, sub, subAA, lines + 2, lineCount - 1,
   *                                     quadCount);
   *         }
   *         return draws;
   *     }
   * ```
   */
  protected fun clipTile(
    canvas: SkCanvas?,
    tileID: Int,
    baseRect: SkRect,
    quad: Array<SkPoint>,
    edgeAA: BooleanArray,
    lines: Array<SkPoint>,
    lineCount: Int,
    quadCount: Int?,
  ): Int {
    TODO("Implement clipTile")
  }
}

public typealias DebugTileRendererINHERITED = ClipTileRenderer

public typealias SolidColorRendererINHERITED = ClipTileRenderer

public typealias TextureSetRendererINHERITED = ClipTileRenderer

public typealias YUVTextureSetRendererINHERITED = ClipTileRenderer
