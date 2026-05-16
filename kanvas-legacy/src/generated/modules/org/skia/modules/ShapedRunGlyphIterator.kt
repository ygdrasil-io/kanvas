package org.skia.modules

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.ULong

/**
 * C++ original:
 * ```cpp
 * struct ShapedRunGlyphIterator {
 *     ShapedRunGlyphIterator(const TArray<ShapedRun>& origRuns)
 *         : fRuns(&origRuns), fRunIndex(0), fGlyphIndex(0)
 *     { }
 *
 *     ShapedRunGlyphIterator(const ShapedRunGlyphIterator& that) = default;
 *     ShapedRunGlyphIterator& operator=(const ShapedRunGlyphIterator& that) = default;
 *     bool operator==(const ShapedRunGlyphIterator& that) const {
 *         return fRuns == that.fRuns &&
 *                fRunIndex == that.fRunIndex &&
 *                fGlyphIndex == that.fGlyphIndex;
 *     }
 *     bool operator!=(const ShapedRunGlyphIterator& that) const {
 *         return fRuns != that.fRuns ||
 *                fRunIndex != that.fRunIndex ||
 *                fGlyphIndex != that.fGlyphIndex;
 *     }
 *
 *     ShapedGlyph* next() {
 *         const TArray<ShapedRun>& runs = *fRuns;
 *         SkASSERT(fRunIndex < runs.size());
 *         SkASSERT(fGlyphIndex < runs[fRunIndex].fNumGlyphs);
 *
 *         ++fGlyphIndex;
 *         if (fGlyphIndex == runs[fRunIndex].fNumGlyphs) {
 *             fGlyphIndex = 0;
 *             ++fRunIndex;
 *             if (fRunIndex >= runs.size()) {
 *                 return nullptr;
 *             }
 *         }
 *         return &runs[fRunIndex].fGlyphs[fGlyphIndex];
 *     }
 *
 *     ShapedGlyph* current() {
 *         const TArray<ShapedRun>& runs = *fRuns;
 *         if (fRunIndex >= runs.size()) {
 *             return nullptr;
 *         }
 *         return &runs[fRunIndex].fGlyphs[fGlyphIndex];
 *     }
 *
 *     const TArray<ShapedRun>* fRuns;
 *     int fRunIndex;
 *     size_t fGlyphIndex;
 * }
 * ```
 */
public data class ShapedRunGlyphIterator public constructor(
  /**
   * C++ original:
   * ```cpp
   * const TArray<ShapedRun>* fRuns
   * ```
   */
  public val fRuns: Int?,
  /**
   * C++ original:
   * ```cpp
   * int fRunIndex
   * ```
   */
  public var fRunIndex: Int,
  /**
   * C++ original:
   * ```cpp
   * size_t fGlyphIndex
   * ```
   */
  public var fGlyphIndex: ULong,
) {
  /**
   * C++ original:
   * ```cpp
   * ShapedRunGlyphIterator& operator=(const ShapedRunGlyphIterator& that) = default
   * ```
   */
  public fun assign(that: ShapedRunGlyphIterator) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator==(const ShapedRunGlyphIterator& that) const {
   *         return fRuns == that.fRuns &&
   *                fRunIndex == that.fRunIndex &&
   *                fGlyphIndex == that.fGlyphIndex;
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const ShapedRunGlyphIterator& that) const {
   *         return fRuns != that.fRuns ||
   *                fRunIndex != that.fRunIndex ||
   *                fGlyphIndex != that.fGlyphIndex;
   *     }
   * ```
   */
  public fun next(): ShapedGlyph {
    TODO("Implement next")
  }

  /**
   * C++ original:
   * ```cpp
   * ShapedGlyph* next() {
   *         const TArray<ShapedRun>& runs = *fRuns;
   *         SkASSERT(fRunIndex < runs.size());
   *         SkASSERT(fGlyphIndex < runs[fRunIndex].fNumGlyphs);
   *
   *         ++fGlyphIndex;
   *         if (fGlyphIndex == runs[fRunIndex].fNumGlyphs) {
   *             fGlyphIndex = 0;
   *             ++fRunIndex;
   *             if (fRunIndex >= runs.size()) {
   *                 return nullptr;
   *             }
   *         }
   *         return &runs[fRunIndex].fGlyphs[fGlyphIndex];
   *     }
   * ```
   */
  public fun current(): ShapedGlyph {
    TODO("Implement current")
  }
}
