package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SubRunControl {
 * public:
 * #if !defined(SK_DISABLE_SDF_TEXT)
 *     SubRunControl(bool ableToUseSDFT, bool useSDFTForSmallText, bool useSDFTForPerspectiveText,
 *                   SkScalar min, SkScalar max,
 *                   bool forcePathAA=false);
 *
 *     // Produce a font, a scale factor from the nominal size to the source space size, and matrix
 *     // range where this font can be reused.
 *     std::tuple<SkFont, SkScalar, SDFTMatrixRange>
 *     getSDFFont(const SkFont& font, const SkMatrix& viewMatrix, const SkPoint& textLocation) const;
 *
 *     bool isSDFT(SkScalar approximateDeviceTextSize, const SkPaint& paint,
 *                 const SkMatrix& matrix) const;
 *     SkScalar maxSize() const { return fMaxDistanceFieldFontSize; }
 * #else
 *     explicit SubRunControl(bool forcePathAA = false) : fForcePathAA(forcePathAA) {}
 * #endif
 *     bool isDirect(SkScalar approximateDeviceTextSize, const SkPaint& paint,
 *                   const SkMatrix& matrix) const;
 *
 *     bool forcePathAA() const { return fForcePathAA; }
 *
 * private:
 * #if !defined(SK_DISABLE_SDF_TEXT)
 *     static SkScalar MinSDFTRange(bool useSDFTForSmallText, SkScalar min);
 *
 *     // Below this size (in device space) distance field text will not be used.
 *     const SkScalar fMinDistanceFieldFontSize;
 *
 *     // Above this size (in device space) distance field text will not be used and glyphs will
 *     // be rendered from outline as individual paths.
 *     const SkScalar fMaxDistanceFieldFontSize;
 *
 *     const bool fAbleToUseSDFT;
 *     const bool fAbleToUsePerspectiveSDFT;
 * #endif
 *
 *     // If true, glyphs drawn as paths are always anti-aliased regardless of any edge hinting.
 *     const bool fForcePathAA;
 * }
 * ```
 */
public data class SubRunControl public constructor(
  /**
   * C++ original:
   * ```cpp
   * const SkScalar fMinDistanceFieldFontSize
   * ```
   */
  private val fMinDistanceFieldFontSize: Int,
  /**
   * C++ original:
   * ```cpp
   * const SkScalar fMaxDistanceFieldFontSize
   * ```
   */
  private val fMaxDistanceFieldFontSize: Int,
  /**
   * C++ original:
   * ```cpp
   * const bool fAbleToUseSDFT
   * ```
   */
  private val fAbleToUseSDFT: Boolean,
  /**
   * C++ original:
   * ```cpp
   * const bool fAbleToUsePerspectiveSDFT
   * ```
   */
  private val fAbleToUsePerspectiveSDFT: Boolean,
  /**
   * C++ original:
   * ```cpp
   * const bool fForcePathAA
   * ```
   */
  private val fForcePathAA: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * std::tuple<SkFont, SkScalar, SDFTMatrixRange>
   * SubRunControl::getSDFFont(const SkFont& font, const SkMatrix& viewMatrix,
   *                         const SkPoint& textLoc) const {
   *     SkScalar textSize = font.getSize();
   *     SkScalar scaledTextSize = SkFontPriv::ApproximateTransformedTextSize(font, viewMatrix, textLoc);
   *     if (scaledTextSize <= 0 || SkScalarNearlyEqual(textSize, scaledTextSize)) {
   *         scaledTextSize = textSize;
   *     }
   *
   *     SkFont dfFont{font};
   *
   *     SkScalar dfMaskScaleFloor;
   *     SkScalar dfMaskScaleCeil;
   *     SkScalar dfMaskSize;
   *     if (scaledTextSize <= kSmallDFFontLimit) {
   *         dfMaskScaleFloor = fMinDistanceFieldFontSize;
   *         dfMaskScaleCeil = kSmallDFFontLimit;
   *         dfMaskSize = kSmallDFFontLimit;
   *     } else if (scaledTextSize <= kMediumDFFontLimit) {
   *         dfMaskScaleFloor = kSmallDFFontLimit;
   *         dfMaskScaleCeil = kMediumDFFontLimit;
   *         dfMaskSize = kMediumDFFontLimit;
   * #ifdef SK_BUILD_FOR_MAC
   *     } else if (scaledTextSize <= kLargeDFFontLimit) {
   *         dfMaskScaleFloor = kMediumDFFontLimit;
   *         dfMaskScaleCeil = kLargeDFFontLimit;
   *         dfMaskSize = kLargeDFFontLimit;
   *     } else {
   *         dfMaskScaleFloor = kLargeDFFontLimit;
   *         dfMaskScaleCeil = fMaxDistanceFieldFontSize;
   *         dfMaskSize = kExtraLargeDFFontLimit;
   *     }
   * #else
   *     } else {
   *         dfMaskScaleFloor = kMediumDFFontLimit;
   *         dfMaskScaleCeil = fMaxDistanceFieldFontSize;
   *         dfMaskSize = kLargeDFFontLimit;
   *     }
   * #endif
   *
   *     dfFont.setSize(dfMaskSize);
   *     dfFont.setEdging(SkFont::Edging::kAntiAlias);
   *     dfFont.setForceAutoHinting(false);
   *     dfFont.setHinting(SkFontHinting::kNormal);
   *
   *     // The sub-pixel position will always happen when transforming to the screen.
   *     dfFont.setSubpixel(false);
   *
   *     SkScalar minMatrixScale = dfMaskScaleFloor / textSize,
   *              maxMatrixScale = dfMaskScaleCeil  / textSize;
   *     return {dfFont, textSize / dfMaskSize, {minMatrixScale, maxMatrixScale}};
   * }
   * ```
   */
  public fun getSDFFont(
    font: SkFont,
    viewMatrix: SkMatrix,
    textLocation: SkPoint,
  ): Int {
    TODO("Implement getSDFFont")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SubRunControl::isSDFT(SkScalar approximateDeviceTextSize, const SkPaint& paint,
   *                            const SkMatrix& matrix) const {
   *     const bool wideStroke = paint.getStyle() == SkPaint::kStroke_Style &&
   *             paint.getStrokeWidth() > 0;
   *     return fAbleToUseSDFT &&
   *            paint.getMaskFilter() == nullptr &&
   *             (paint.getStyle() == SkPaint::kFill_Style || wideStroke) &&
   *            0 < approximateDeviceTextSize &&
   *            (fAbleToUsePerspectiveSDFT || !matrix.hasPerspective()) &&
   *            (fMinDistanceFieldFontSize <= approximateDeviceTextSize || matrix.hasPerspective()) &&
   *            approximateDeviceTextSize <= fMaxDistanceFieldFontSize;
   * }
   * ```
   */
  public fun isSDFT(
    approximateDeviceTextSize: SkScalar,
    paint: SkPaint,
    matrix: SkMatrix,
  ): Boolean {
    TODO("Implement isSDFT")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar maxSize() const { return fMaxDistanceFieldFontSize; }
   * ```
   */
  public fun maxSize(): Int {
    TODO("Implement maxSize")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SubRunControl::isDirect(SkScalar approximateDeviceTextSize, const SkPaint& paint,
   *                            const SkMatrix& matrix) const {
   * #if !defined(SK_DISABLE_SDF_TEXT)
   *     const bool isSDFT = this->isSDFT(approximateDeviceTextSize, paint, matrix);
   * #else
   *     const bool isSDFT = false;
   * #endif
   *     return !isSDFT &&
   *            !matrix.hasPerspective() &&
   *             0 < approximateDeviceTextSize &&
   *             approximateDeviceTextSize < SkGlyphDigest::kSkSideTooBigForAtlas;
   * }
   * ```
   */
  public fun isDirect(
    approximateDeviceTextSize: SkScalar,
    paint: SkPaint,
    matrix: SkMatrix,
  ): Boolean {
    TODO("Implement isDirect")
  }

  /**
   * C++ original:
   * ```cpp
   * bool forcePathAA() const { return fForcePathAA; }
   * ```
   */
  public fun forcePathAA(): Boolean {
    TODO("Implement forcePathAA")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * SkScalar SubRunControl::MinSDFTRange(bool useSDFTForSmallText, SkScalar min) {
     *     if (!useSDFTForSmallText) {
     *         return kLargeDFFontLimit;
     *     }
     *     return min;
     * }
     * ```
     */
    private fun minSDFTRange(useSDFTForSmallText: Boolean, min: SkScalar): Int {
      TODO("Implement minSDFTRange")
    }
  }
}
