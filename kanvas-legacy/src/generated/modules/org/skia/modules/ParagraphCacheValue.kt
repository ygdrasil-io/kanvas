package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import org.skia.core.TArrayfalse
import org.skia.core.TArraytrue

/**
 * C++ original:
 * ```cpp
 * class ParagraphCacheValue {
 * public:
 *     ParagraphCacheValue(ParagraphCacheKey&& key, const ParagraphImpl* paragraph)
 *         : fKey(std::move(key))
 *         , fRuns(paragraph->fRuns)
 *         , fClusters(paragraph->fClusters)
 *         , fClustersIndexFromCodeUnit(paragraph->fClustersIndexFromCodeUnit)
 *         , fCodeUnitProperties(paragraph->fCodeUnitProperties)
 *         , fWords(paragraph->fWords)
 *         , fBidiRegions(paragraph->fBidiRegions)
 *         , fHasLineBreaks(paragraph->fHasLineBreaks)
 *         , fHasWhitespacesInside(paragraph->fHasWhitespacesInside)
 *         , fTrailingSpaces(paragraph->fTrailingSpaces) { }
 *
 *     // Input == key
 *     ParagraphCacheKey fKey;
 *
 *     // Shaped results
 *     TArray<Run, false> fRuns;
 *     TArray<Cluster, true> fClusters;
 *     TArray<size_t, true> fClustersIndexFromCodeUnit;
 *     // ICU results
 *     TArray<SkUnicode::CodeUnitFlags, true> fCodeUnitProperties;
 *     std::vector<size_t> fWords;
 *     std::vector<SkUnicode::BidiRegion> fBidiRegions;
 *     bool fHasLineBreaks;
 *     bool fHasWhitespacesInside;
 *     TextIndex fTrailingSpaces;
 * }
 * ```
 */
public data class ParagraphCacheValue public constructor(
  /**
   * C++ original:
   * ```cpp
   * ParagraphCacheKey fKey
   * ```
   */
  public var fKey: ParagraphCacheKey,
  /**
   * C++ original:
   * ```cpp
   * TArray<Run, false> fRuns
   * ```
   */
  public var fRuns: TArrayfalse<Run>,
  /**
   * C++ original:
   * ```cpp
   * TArray<Cluster, true> fClusters
   * ```
   */
  public var fClusters: TArraytrue<Cluster>,
  /**
   * C++ original:
   * ```cpp
   * TArray<size_t, true> fClustersIndexFromCodeUnit
   * ```
   */
  public var fClustersIndexFromCodeUnit: TArraytrue<ULong>,
  /**
   * C++ original:
   * ```cpp
   * TArray<SkUnicode::CodeUnitFlags, true> fCodeUnitProperties
   * ```
   */
  public var fCodeUnitProperties: TArraytrue<SkUnicode.CodeUnitFlags>,
  /**
   * C++ original:
   * ```cpp
   * std::vector<size_t> fWords
   * ```
   */
  public var fWords: Int,
  /**
   * C++ original:
   * ```cpp
   * std::vector<SkUnicode::BidiRegion> fBidiRegions
   * ```
   */
  public var fBidiRegions: Int,
  /**
   * C++ original:
   * ```cpp
   * bool fHasLineBreaks
   * ```
   */
  public var fHasLineBreaks: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fHasWhitespacesInside
   * ```
   */
  public var fHasWhitespacesInside: Boolean,
  /**
   * C++ original:
   * ```cpp
   * TextIndex fTrailingSpaces
   * ```
   */
  public var fTrailingSpaces: TextIndex,
)
