package org.skia.core

import kotlin.Char
import kotlin.Int
import org.skia.foundation.SkGlyphID
import org.skia.foundation.SkSpan
import org.skia.math.SkPoint
import org.skia.math.SkVector

/**
 * C++ original:
 * ```cpp
 * class GlyphRun {
 * public:
 *     GlyphRun(const SkFont& font,
 *              SkSpan<const SkPoint> positions,
 *              SkSpan<const SkGlyphID> glyphIDs,
 *              SkSpan<const char> text,
 *              SkSpan<const uint32_t> clusters,
 *              SkSpan<const SkVector> scaledRotations);
 *
 *     GlyphRun(const GlyphRun& glyphRun, const SkFont& font);
 *
 *     size_t runSize() const { return fSource.size(); }
 *     SkSpan<const SkPoint> positions() const { return fSource.get<1>(); }
 *     SkSpan<const SkGlyphID> glyphsIDs() const { return fSource.get<0>(); }
 *     SkZip<const SkGlyphID, const SkPoint> source() const { return fSource; }
 *     const SkFont& font() const { return fFont; }
 *     SkSpan<const uint32_t> clusters() const { return fClusters; }
 *     SkSpan<const char> text() const { return fText; }
 *     SkSpan<const SkVector> scaledRotations() const { return fScaledRotations; }
 *
 * private:
 *     // GlyphIDs and positions.
 *     const SkZip<const SkGlyphID, const SkPoint> fSource;
 *     // Original text from SkTextBlob if present. Will be empty of not present.
 *     const SkSpan<const char> fText;
 *     // Original clusters from SkTextBlob if present. Will be empty if not present.
 *     const SkSpan<const uint32_t>   fClusters;
 *     // Possible RSXForm information
 *     const SkSpan<const SkVector> fScaledRotations;
 *     // Font for this run modified to have glyph encoding and left alignment.
 *     SkFont fFont;
 * }
 * ```
 */
public data class GlyphRun public constructor(
  /**
   * C++ original:
   * ```cpp
   * const SkZip<const SkGlyphID, const SkPoint> fSource
   * ```
   */
  private val fSource: SkZip<SkGlyphID, SkPoint>,
  /**
   * C++ original:
   * ```cpp
   * const SkSpan<const char> fText
   * ```
   */
  private val fText: SkSpan<Char>,
  /**
   * C++ original:
   * ```cpp
   * const SkSpan<const uint32_t>   fClusters
   * ```
   */
  private val fClusters: Int,
  /**
   * C++ original:
   * ```cpp
   * const SkSpan<const SkVector> fScaledRotations
   * ```
   */
  private val fScaledRotations: SkSpan<SkVector>,
  /**
   * C++ original:
   * ```cpp
   * SkFont fFont
   * ```
   */
  private var fFont: SkFont,
) {
  /**
   * C++ original:
   * ```cpp
   * size_t runSize() const { return fSource.size(); }
   * ```
   */
  public fun runSize(): Int {
    TODO("Implement runSize")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const SkPoint> positions() const { return fSource.get<1>(); }
   * ```
   */
  public fun positions(): SkSpan<SkPoint> {
    TODO("Implement positions")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const SkGlyphID> glyphsIDs() const { return fSource.get<0>(); }
   * ```
   */
  public fun glyphsIDs(): SkSpan<SkGlyphID> {
    TODO("Implement glyphsIDs")
  }

  /**
   * C++ original:
   * ```cpp
   * SkZip<const SkGlyphID, const SkPoint> source() const { return fSource; }
   * ```
   */
  public fun source(): SkZip<SkGlyphID, SkPoint> {
    TODO("Implement source")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkFont& font() const { return fFont; }
   * ```
   */
  public fun font(): SkFont {
    TODO("Implement font")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const uint32_t> clusters() const { return fClusters; }
   * ```
   */
  public fun clusters(): Int {
    TODO("Implement clusters")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const char> text() const { return fText; }
   * ```
   */
  public fun text(): SkSpan<Char> {
    TODO("Implement text")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const SkVector> scaledRotations() const { return fScaledRotations; }
   * ```
   */
  public fun scaledRotations(): SkSpan<SkVector> {
    TODO("Implement scaledRotations")
  }
}
