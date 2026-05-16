package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.IntArray
import org.skia.core.STArraykMinItemsTrue

/**
 * C++ original:
 * ```cpp
 * class BulkUsePlotUpdater {
 * public:
 *     BulkUsePlotUpdater() {
 *         memset(fPlotAlreadyUpdated, 0, sizeof(fPlotAlreadyUpdated));
 *     }
 *     BulkUsePlotUpdater(const BulkUsePlotUpdater& that)
 *             : fPlotsToUpdate(that.fPlotsToUpdate) {
 *         memcpy(fPlotAlreadyUpdated, that.fPlotAlreadyUpdated, sizeof(fPlotAlreadyUpdated));
 *     }
 *
 *     bool add(const skgpu::AtlasLocator& atlasLocator) {
 *         int plotIdx = atlasLocator.plotIndex();
 *         int pageIdx = atlasLocator.pageIndex();
 *         if (this->find(pageIdx, plotIdx)) {
 *             return false;
 *         }
 *         this->set(pageIdx, plotIdx);
 *         return true;
 *     }
 *
 *     void reset() {
 *         fPlotsToUpdate.clear();
 *         memset(fPlotAlreadyUpdated, 0, sizeof(fPlotAlreadyUpdated));
 *     }
 *
 *     struct PlotData {
 *         PlotData(int pageIdx, int plotIdx) : fPageIndex(pageIdx), fPlotIndex(plotIdx) {}
 *         uint32_t fPageIndex;
 *         uint32_t fPlotIndex;
 *     };
 *
 *     int count() const { return fPlotsToUpdate.size(); }
 *
 *     const PlotData& plotData(int index) const { return fPlotsToUpdate[index]; }
 *
 * private:
 *     bool find(int pageIdx, int index) const {
 *         SkASSERT(index < skgpu::PlotLocator::kMaxPlots);
 *         return (fPlotAlreadyUpdated[pageIdx] >> index) & 1;
 *     }
 *
 *     void set(int pageIdx, int index) {
 *         SkASSERT(!this->find(pageIdx, index));
 *         fPlotAlreadyUpdated[pageIdx] |= (1 << index);
 *         fPlotsToUpdate.push_back(PlotData(pageIdx, index));
 *     }
 *
 *     inline static constexpr int kMinItems = 4;
 *     skia_private::STArray<kMinItems, PlotData, true> fPlotsToUpdate;
 *     // TODO: increase this to uint64_t to allow more plots per page
 *     uint32_t fPlotAlreadyUpdated[skgpu::PlotLocator::kMaxMultitexturePages];
 * }
 * ```
 */
public data class BulkUsePlotUpdater public constructor(
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kMinItems = 4
   * ```
   */
  private var fPlotsToUpdate: STArraykMinItemsTrue<PlotData>,
  /**
   * C++ original:
   * ```cpp
   * skia_private::STArray<kMinItems, PlotData, true> fPlotsToUpdate
   * ```
   */
  private var fPlotAlreadyUpdated: IntArray,
) {
  /**
   * C++ original:
   * ```cpp
   * bool add(const skgpu::AtlasLocator& atlasLocator) {
   *         int plotIdx = atlasLocator.plotIndex();
   *         int pageIdx = atlasLocator.pageIndex();
   *         if (this->find(pageIdx, plotIdx)) {
   *             return false;
   *         }
   *         this->set(pageIdx, plotIdx);
   *         return true;
   *     }
   * ```
   */
  public fun add(atlasLocator: AtlasLocator): Boolean {
    TODO("Implement add")
  }

  /**
   * C++ original:
   * ```cpp
   * void reset() {
   *         fPlotsToUpdate.clear();
   *         memset(fPlotAlreadyUpdated, 0, sizeof(fPlotAlreadyUpdated));
   *     }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * int count() const { return fPlotsToUpdate.size(); }
   * ```
   */
  public fun count(): Int {
    TODO("Implement count")
  }

  /**
   * C++ original:
   * ```cpp
   * const PlotData& plotData(int index) const { return fPlotsToUpdate[index]; }
   * ```
   */
  public fun plotData(index: Int): PlotData {
    TODO("Implement plotData")
  }

  /**
   * C++ original:
   * ```cpp
   * bool find(int pageIdx, int index) const {
   *         SkASSERT(index < skgpu::PlotLocator::kMaxPlots);
   *         return (fPlotAlreadyUpdated[pageIdx] >> index) & 1;
   *     }
   * ```
   */
  private fun find(pageIdx: Int, index: Int): Boolean {
    TODO("Implement find")
  }

  /**
   * C++ original:
   * ```cpp
   * void set(int pageIdx, int index) {
   *         SkASSERT(!this->find(pageIdx, index));
   *         fPlotAlreadyUpdated[pageIdx] |= (1 << index);
   *         fPlotsToUpdate.push_back(PlotData(pageIdx, index));
   *     }
   * ```
   */
  private fun `set`(pageIdx: Int, index: Int) {
    TODO("Implement set")
  }

  public data class PlotData public constructor(
    public var fPageIndex: Int,
    public var fPlotIndex: Int,
  )

  public companion object {
    private val kMinItems: Int = TODO("Initialize kMinItems")
  }
}
