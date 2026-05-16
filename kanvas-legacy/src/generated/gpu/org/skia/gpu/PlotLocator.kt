package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class PlotLocator {
 * public:
 *     // These are both restricted by the space they occupy in the PlotLocator.
 *     // maxPages is also limited by being crammed into the glyph uvs.
 *     // maxPlots is also limited by the fPlotAlreadyUpdated bitfield in
 *     // GrDrawOpAtlas::BulkUseTokenUpdater.
 *     inline static constexpr auto kMaxMultitexturePages = 4;
 *     inline static constexpr int kMaxPlots = 32;
 *
 *     PlotLocator(uint32_t pageIdx, uint32_t plotIdx, uint64_t generation)
 *             : fGenID(generation)
 *             , fPlotIndex(plotIdx)
 *             , fPageIndex(pageIdx) {
 *         SkASSERT(pageIdx < kMaxMultitexturePages);
 *         SkASSERT(plotIdx < kMaxPlots);
 *         SkASSERT(generation < ((uint64_t)1 << 48));
 *     }
 *
 *     PlotLocator()
 *             : fGenID(AtlasGenerationCounter::kInvalidGeneration)
 *             , fPlotIndex(0)
 *             , fPageIndex(0) {}
 *
 *     bool isValid() const {
 *         return fGenID != AtlasGenerationCounter::kInvalidGeneration ||
 *                fPlotIndex != 0 || fPageIndex != 0;
 *     }
 *
 *     void makeInvalid() {
 *         fGenID = AtlasGenerationCounter::kInvalidGeneration;
 *         fPlotIndex = 0;
 *         fPageIndex = 0;
 *     }
 *
 *     bool operator==(const PlotLocator& other) const {
 *         return fGenID == other.fGenID &&
 *                fPlotIndex == other.fPlotIndex &&
 *                fPageIndex == other.fPageIndex; }
 *
 *     uint32_t pageIndex() const { return fPageIndex; }
 *     uint32_t plotIndex() const { return fPlotIndex; }
 *     uint64_t genID() const { return fGenID; }
 *
 * private:
 *     uint64_t fGenID:48;
 *     uint64_t fPlotIndex:8;
 *     uint64_t fPageIndex:8;
 * }
 * ```
 */
public data class PlotLocator public constructor(
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr auto kMaxMultitexturePages = 4
   * ```
   */
  private var fGenID: Int,
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kMaxPlots = 32
   * ```
   */
  private var fPlotIndex: Int,
  /**
   * C++ original:
   * ```cpp
   * uint64_t fGenID
   * ```
   */
  private var fPageIndex: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool isValid() const {
   *         return fGenID != AtlasGenerationCounter::kInvalidGeneration ||
   *                fPlotIndex != 0 || fPageIndex != 0;
   *     }
   * ```
   */
  public fun isValid(): Boolean {
    TODO("Implement isValid")
  }

  /**
   * C++ original:
   * ```cpp
   * void makeInvalid() {
   *         fGenID = AtlasGenerationCounter::kInvalidGeneration;
   *         fPlotIndex = 0;
   *         fPageIndex = 0;
   *     }
   * ```
   */
  public fun makeInvalid() {
    TODO("Implement makeInvalid")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator==(const PlotLocator& other) const {
   *         return fGenID == other.fGenID &&
   *                fPlotIndex == other.fPlotIndex &&
   *                fPageIndex == other.fPageIndex; }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t pageIndex() const { return fPageIndex; }
   * ```
   */
  public fun pageIndex(): Int {
    TODO("Implement pageIndex")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t plotIndex() const { return fPlotIndex; }
   * ```
   */
  public fun plotIndex(): Int {
    TODO("Implement plotIndex")
  }

  /**
   * C++ original:
   * ```cpp
   * uint64_t genID() const { return fGenID; }
   * ```
   */
  public fun genID(): Int {
    TODO("Implement genID")
  }

  public companion object {
    public val kMaxMultitexturePages: Int = TODO("Initialize kMaxMultitexturePages")

    public val kMaxPlots: Int = TODO("Initialize kMaxPlots")
  }
}
