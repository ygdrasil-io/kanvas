package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkColor
import org.skia.math.SkIRect
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SK_SPI SkLatticeIter {
 * public:
 *
 *     static bool Valid(int imageWidth, int imageHeight, const SkCanvas::Lattice& lattice);
 *
 *     SkLatticeIter(const SkCanvas::Lattice& lattice, const SkRect& dst);
 *
 *     static bool Valid(int imageWidth, int imageHeight, const SkIRect& center);
 *
 *     SkLatticeIter(int imageWidth, int imageHeight, const SkIRect& center, const SkRect& dst);
 *
 *     /**
 *      *  While it returns true, use src/dst to draw the image/bitmap. Optional parameters
 *      *  isFixedColor and fixedColor specify if the rectangle is filled with a fixed color.
 *      *  If (*isFixedColor) is true, then (*fixedColor) contains the rectangle color.
 *      */
 *     bool next(SkIRect* src, SkRect* dst, bool* isFixedColor = nullptr,
 *               SkColor* fixedColor = nullptr);
 *
 *     /** Version of above that converts the integer src rect to a scalar rect. */
 *     bool next(SkRect* src, SkRect* dst, bool* isFixedColor = nullptr,
 *               SkColor* fixedColor = nullptr) {
 *         SkIRect isrcR;
 *         if (this->next(&isrcR, dst, isFixedColor, fixedColor)) {
 *             *src = SkRect::Make(isrcR);
 *             return true;
 *         }
 *         return false;
 *     }
 *
 *     /**
 *      *  Apply a matrix to the dst points.
 *      */
 *     void mapDstScaleTranslate(const SkMatrix& matrix);
 *
 *     /**
 *      *  Returns the number of rects that will actually be drawn.
 *      */
 *     int numRectsToDraw() const {
 *         return fNumRectsToDraw;
 *     }
 *
 * private:
 *     skia_private::TArray<int> fSrcX;
 *     skia_private::TArray<int> fSrcY;
 *     skia_private::TArray<SkScalar> fDstX;
 *     skia_private::TArray<SkScalar> fDstY;
 *     skia_private::TArray<SkCanvas::Lattice::RectType> fRectTypes;
 *     skia_private::TArray<SkColor> fColors;
 *
 *     int  fCurrX;
 *     int  fCurrY;
 *     int  fNumRectsInLattice;
 *     int  fNumRectsToDraw;
 * }
 * ```
 */
public data class SkLatticeIter public constructor(
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<int> fSrcX
   * ```
   */
  private var fSrcX: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<int> fSrcY
   * ```
   */
  private var fSrcY: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<SkScalar> fDstX
   * ```
   */
  private var fDstX: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<SkScalar> fDstY
   * ```
   */
  private var fDstY: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<SkCanvas::Lattice::RectType> fRectTypes
   * ```
   */
  private var fRectTypes: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<SkColor> fColors
   * ```
   */
  private var fColors: Int,
  /**
   * C++ original:
   * ```cpp
   * int  fCurrX
   * ```
   */
  private var fCurrX: Int,
  /**
   * C++ original:
   * ```cpp
   * int  fCurrY
   * ```
   */
  private var fCurrY: Int,
  /**
   * C++ original:
   * ```cpp
   * int  fNumRectsInLattice
   * ```
   */
  private var fNumRectsInLattice: Int,
  /**
   * C++ original:
   * ```cpp
   * int  fNumRectsToDraw
   * ```
   */
  private var fNumRectsToDraw: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool SkLatticeIter::next(SkIRect* src, SkRect* dst, bool* isFixedColor, SkColor* fixedColor) {
   *     int currRect = fCurrX + fCurrY * (fSrcX.size() - 1);
   *     if (currRect == fNumRectsInLattice) {
   *         return false;
   *     }
   *
   *     const int x = fCurrX;
   *     const int y = fCurrY;
   *     SkASSERT(x >= 0 && x < fSrcX.size() - 1);
   *     SkASSERT(y >= 0 && y < fSrcY.size() - 1);
   *
   *     if (fSrcX.size() - 1 == ++fCurrX) {
   *         fCurrX = 0;
   *         fCurrY += 1;
   *     }
   *
   *     if (!fRectTypes.empty() && SkToBool(SkCanvas::Lattice::kTransparent == fRectTypes[currRect])) {
   *         return this->next(src, dst, isFixedColor, fixedColor);
   *     }
   *
   *     src->setLTRB(fSrcX[x], fSrcY[y], fSrcX[x + 1], fSrcY[y + 1]);
   *     dst->setLTRB(fDstX[x], fDstY[y], fDstX[x + 1], fDstY[y + 1]);
   *     if (isFixedColor && fixedColor) {
   *         *isFixedColor = !fRectTypes.empty() &&
   *                         SkToBool(SkCanvas::Lattice::kFixedColor == fRectTypes[currRect]);
   *         if (*isFixedColor) {
   *             *fixedColor = fColors[currRect];
   *         }
   *     }
   *     return true;
   * }
   * ```
   */
  public fun next(
    src: SkIRect?,
    dst: SkRect?,
    isFixedColor: Boolean? = TODO(),
    fixedColor: SkColor? = TODO(),
  ): Boolean {
    TODO("Implement next")
  }

  /**
   * C++ original:
   * ```cpp
   * bool next(SkRect* src, SkRect* dst, bool* isFixedColor = nullptr,
   *               SkColor* fixedColor = nullptr) {
   *         SkIRect isrcR;
   *         if (this->next(&isrcR, dst, isFixedColor, fixedColor)) {
   *             *src = SkRect::Make(isrcR);
   *             return true;
   *         }
   *         return false;
   *     }
   * ```
   */
  public fun next(
    src: SkRect?,
    dst: SkRect?,
    isFixedColor: Boolean? = TODO(),
    fixedColor: SkColor? = TODO(),
  ): Boolean {
    TODO("Implement next")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkLatticeIter::mapDstScaleTranslate(const SkMatrix& matrix) {
   *     SkASSERT(matrix.isScaleTranslate());
   *     SkScalar tx = matrix.getTranslateX();
   *     SkScalar sx = matrix.getScaleX();
   *     for (int i = 0; i < fDstX.size(); i++) {
   *         fDstX[i] = fDstX[i] * sx + tx;
   *     }
   *
   *     SkScalar ty = matrix.getTranslateY();
   *     SkScalar sy = matrix.getScaleY();
   *     for (int i = 0; i < fDstY.size(); i++) {
   *         fDstY[i] = fDstY[i] * sy + ty;
   *     }
   * }
   * ```
   */
  public fun mapDstScaleTranslate(matrix: SkMatrix) {
    TODO("Implement mapDstScaleTranslate")
  }

  /**
   * C++ original:
   * ```cpp
   * int numRectsToDraw() const {
   *         return fNumRectsToDraw;
   *     }
   * ```
   */
  public fun numRectsToDraw(): Int {
    TODO("Implement numRectsToDraw")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * bool SkLatticeIter::Valid(int width, int height, const SkCanvas::Lattice& lattice) {
     *     SkIRect totalBounds = SkIRect::MakeWH(width, height);
     *     SkASSERT(lattice.fBounds);
     *     const SkIRect latticeBounds = *lattice.fBounds;
     *     if (!totalBounds.contains(latticeBounds)) {
     *         return false;
     *     }
     *
     *     bool zeroXDivs = lattice.fXCount <= 0 || (1 == lattice.fXCount &&
     *                                               latticeBounds.fLeft == lattice.fXDivs[0]);
     *     bool zeroYDivs = lattice.fYCount <= 0 || (1 == lattice.fYCount &&
     *                                               latticeBounds.fTop == lattice.fYDivs[0]);
     *     if (zeroXDivs && zeroYDivs) {
     *         return false;
     *     }
     *
     *     return valid_divs(lattice.fXDivs, lattice.fXCount, latticeBounds.fLeft, latticeBounds.fRight)
     *         && valid_divs(lattice.fYDivs, lattice.fYCount, latticeBounds.fTop, latticeBounds.fBottom);
     * }
     * ```
     */
    public fun valid(
      imageWidth: Int,
      imageHeight: Int,
      lattice: SkCanvas.Lattice,
    ): Boolean {
      TODO("Implement valid")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkLatticeIter::Valid(int width, int height, const SkIRect& center) {
     *     return !center.isEmpty() && SkIRect::MakeWH(width, height).contains(center);
     * }
     * ```
     */
    public fun valid(
      imageWidth: Int,
      imageHeight: Int,
      center: SkIRect,
    ): Boolean {
      TODO("Implement valid")
    }
  }
}
