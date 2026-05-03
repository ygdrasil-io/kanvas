package org.skia.gpu

import kotlin.Int
import org.skia.math.SkIPoint
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * class AtlasLocator {
 * public:
 *     std::array<uint16_t, 4> getUVs() const {
 *         return fUVs;
 *     }
 *
 *     void invalidatePlotLocator() { fPlotLocator.makeInvalid(); }
 *
 *     // TODO: Remove the small path renderer's use of this for eviction
 *     PlotLocator plotLocator() const { return fPlotLocator; }
 *
 *     uint32_t pageIndex() const { return fPlotLocator.pageIndex(); }
 *
 *     uint32_t plotIndex() const { return fPlotLocator.plotIndex(); }
 *
 *     uint64_t genID() const { return fPlotLocator.genID(); }
 *
 *     SkIPoint topLeft() const {
 *         return {fUVs[0] & 0x1FFF, fUVs[1]};
 *     }
 *
 *     SkPoint widthHeight() const {
 *         auto width =  fUVs[2] - fUVs[0],
 *              height = fUVs[3] - fUVs[1];
 *         return SkPoint::Make(width, height);
 *     }
 *
 *     uint16_t width() const {
 *         return fUVs[2] - fUVs[0];
 *     }
 *
 *     uint16_t height() const {
 *         return fUVs[3] - fUVs[1];
 *     }
 *
 *     void insetSrc(int padding) {
 *         SkASSERT(2 * padding <= this->width());
 *         SkASSERT(2 * padding <= this->height());
 *
 *         fUVs[0] += padding;
 *         fUVs[1] += padding;
 *         fUVs[2] -= padding;
 *         fUVs[3] -= padding;
 *     }
 *
 *     void updatePlotLocator(PlotLocator p) {
 *         fPlotLocator = p;
 *         SkASSERT(fPlotLocator.pageIndex() <= 3);
 *         uint16_t page = fPlotLocator.pageIndex() << 13;
 *         fUVs[0] = (fUVs[0] & 0x1FFF) | page;
 *         fUVs[2] = (fUVs[2] & 0x1FFF) | page;
 *     }
 *
 *     void updateRect(skgpu::IRect16 rect) {
 *         SkASSERT(rect.fLeft <= rect.fRight);
 *         SkASSERT(rect.fRight <= 0x1FFF);
 *         fUVs[0] = (fUVs[0] & 0xE000) | rect.fLeft;
 *         fUVs[1] = rect.fTop;
 *         fUVs[2] = (fUVs[2] & 0xE000) | rect.fRight;
 *         fUVs[3] = rect.fBottom;
 *     }
 *
 * private:
 *     PlotLocator fPlotLocator{0, 0, AtlasGenerationCounter::kInvalidGeneration};
 *
 *     // The inset padded bounds in the atlas in the lower 13 bits, and page index in bits 13 &
 *     // 14 of the Us.
 *     std::array<uint16_t, 4> fUVs{0, 0, 0, 0};
 * }
 * ```
 */
public data class AtlasLocator public constructor(
  /**
   * C++ original:
   * ```cpp
   * PlotLocator fPlotLocator
   * ```
   */
  private var fPlotLocator: PlotLocator,
  /**
   * C++ original:
   * ```cpp
   * std::array<uint16_t, 4> fUVs
   * ```
   */
  private var fUVs: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * std::array<uint16_t, 4> getUVs() const {
   *         return fUVs;
   *     }
   * ```
   */
  public fun getUVs(): Int {
    TODO("Implement getUVs")
  }

  /**
   * C++ original:
   * ```cpp
   * void invalidatePlotLocator() { fPlotLocator.makeInvalid(); }
   * ```
   */
  public fun invalidatePlotLocator() {
    TODO("Implement invalidatePlotLocator")
  }

  /**
   * C++ original:
   * ```cpp
   * PlotLocator plotLocator() const { return fPlotLocator; }
   * ```
   */
  public fun plotLocator(): PlotLocator {
    TODO("Implement plotLocator")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t pageIndex() const { return fPlotLocator.pageIndex(); }
   * ```
   */
  public fun pageIndex(): Int {
    TODO("Implement pageIndex")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t plotIndex() const { return fPlotLocator.plotIndex(); }
   * ```
   */
  public fun plotIndex(): Int {
    TODO("Implement plotIndex")
  }

  /**
   * C++ original:
   * ```cpp
   * uint64_t genID() const { return fPlotLocator.genID(); }
   * ```
   */
  public fun genID(): Int {
    TODO("Implement genID")
  }

  /**
   * C++ original:
   * ```cpp
   * SkIPoint topLeft() const {
   *         return {fUVs[0] & 0x1FFF, fUVs[1]};
   *     }
   * ```
   */
  public fun topLeft(): SkIPoint {
    TODO("Implement topLeft")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPoint widthHeight() const {
   *         auto width =  fUVs[2] - fUVs[0],
   *              height = fUVs[3] - fUVs[1];
   *         return SkPoint::Make(width, height);
   *     }
   * ```
   */
  public fun widthHeight(): SkPoint {
    TODO("Implement widthHeight")
  }

  /**
   * C++ original:
   * ```cpp
   * uint16_t width() const {
   *         return fUVs[2] - fUVs[0];
   *     }
   * ```
   */
  public fun width(): Int {
    TODO("Implement width")
  }

  /**
   * C++ original:
   * ```cpp
   * uint16_t height() const {
   *         return fUVs[3] - fUVs[1];
   *     }
   * ```
   */
  public fun height(): Int {
    TODO("Implement height")
  }

  /**
   * C++ original:
   * ```cpp
   * void insetSrc(int padding) {
   *         SkASSERT(2 * padding <= this->width());
   *         SkASSERT(2 * padding <= this->height());
   *
   *         fUVs[0] += padding;
   *         fUVs[1] += padding;
   *         fUVs[2] -= padding;
   *         fUVs[3] -= padding;
   *     }
   * ```
   */
  public fun insetSrc(padding: Int) {
    TODO("Implement insetSrc")
  }

  /**
   * C++ original:
   * ```cpp
   * void updatePlotLocator(PlotLocator p) {
   *         fPlotLocator = p;
   *         SkASSERT(fPlotLocator.pageIndex() <= 3);
   *         uint16_t page = fPlotLocator.pageIndex() << 13;
   *         fUVs[0] = (fUVs[0] & 0x1FFF) | page;
   *         fUVs[2] = (fUVs[2] & 0x1FFF) | page;
   *     }
   * ```
   */
  public fun updatePlotLocator(p: PlotLocator) {
    TODO("Implement updatePlotLocator")
  }

  /**
   * C++ original:
   * ```cpp
   * void updateRect(skgpu::IRect16 rect) {
   *         SkASSERT(rect.fLeft <= rect.fRight);
   *         SkASSERT(rect.fRight <= 0x1FFF);
   *         fUVs[0] = (fUVs[0] & 0xE000) | rect.fLeft;
   *         fUVs[1] = rect.fTop;
   *         fUVs[2] = (fUVs[2] & 0xE000) | rect.fRight;
   *         fUVs[3] = rect.fBottom;
   *     }
   * ```
   */
  public fun updateRect(rect: IRect16) {
    TODO("Implement updateRect")
  }
}
