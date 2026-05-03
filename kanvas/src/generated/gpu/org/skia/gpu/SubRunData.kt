package org.skia.gpu

import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SubRunData {
 * public:
 *     SubRunData() = delete;
 *     SubRunData(const SubRunData& subRun) = default;
 *     SubRunData(SubRunData&&) = delete;
 *
 *     SubRunData(const sktext::gpu::AtlasSubRun* subRun,
 *                sk_sp<SkRefCnt> supportDataKeepAlive,
 *                Rect deviceBounds,
 *                const SkM44& deviceToLocal,
 *                int startGlyphIndex,
 *                int glyphCount,
 *                SkColor luminanceColor,
 *                bool useGammaCorrectDistanceTable,
 *                SkPixelGeometry pixelGeometry,
 *                Recorder* recorder,
 *                sktext::gpu::RendererData rendererData)
 *         : fSubRun(subRun)
 *         , fSupportDataKeepAlive(std::move(supportDataKeepAlive))
 *         , fBounds(deviceBounds)
 *         , fDeviceToLocal(deviceToLocal)
 *         , fStartGlyphIndex(startGlyphIndex)
 *         , fGlyphCount(glyphCount)
 *         , fLuminanceColor(luminanceColor)
 *         , fUseGammaCorrectDistanceTable(useGammaCorrectDistanceTable)
 *         , fPixelGeometry(pixelGeometry)
 *         , fRecorder(recorder)
 *         , fRendererData(rendererData) {}
 *
 *     ~SubRunData() = default;
 *
 *     // NOTE: None of the geometry types benefit from move semantics, so we don't bother
 *     // defining a move assignment operator for SubRunData.
 *     SubRunData& operator=(SubRunData&&) = delete;
 *     SubRunData& operator=(const SubRunData& that) = default;
 *
 *     // The bounding box of the originating AtlasSubRun.
 *     Rect bounds() const { return fBounds; }
 *
 *     // The inverse local-to-device matrix.
 *     const SkM44& deviceToLocal() const { return fDeviceToLocal; }
 *
 *     // Access the individual elements of the subrun data.
 *     const sktext::gpu::AtlasSubRun* subRun() const { return fSubRun; }
 *     int startGlyphIndex() const { return fStartGlyphIndex; }
 *     int glyphCount() const { return fGlyphCount; }
 *     SkColor luminanceColor() const { return fLuminanceColor; }
 *     bool useGammaCorrectDistanceTable() const { return fUseGammaCorrectDistanceTable; }
 *     SkPixelGeometry pixelGeometry() const { return fPixelGeometry; }
 *     Recorder* recorder() const { return fRecorder; }
 *     const sktext::gpu::RendererData& rendererData() const { return fRendererData; }
 *
 * private:
 *     const sktext::gpu::AtlasSubRun* fSubRun;
 *     // Keep the TextBlob or Slug alive until we're done with the Geometry.
 *     sk_sp<SkRefCnt> fSupportDataKeepAlive;
 *
 *     Rect fBounds;  // bounds of the data stored in the SubRun
 *     SkM44 fDeviceToLocal;
 *     int fStartGlyphIndex;
 *     int fGlyphCount;
 *     SkColor fLuminanceColor;            // only used by SDFTextRenderStep
 *     bool fUseGammaCorrectDistanceTable; // only used by SDFTextRenderStep
 *     SkPixelGeometry fPixelGeometry;     // only used by SDFTextLCDRenderStep
 *     Recorder* fRecorder; // this SubRun can only be associated with this Recorder's atlas
 *     sktext::gpu::RendererData fRendererData;
 * }
 * ```
 */
public data class SubRunData public constructor(
  /**
   * C++ original:
   * ```cpp
   * const sktext::gpu::AtlasSubRun* fSubRun
   * ```
   */
  private val fSubRun: Int?,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkRefCnt> fSupportDataKeepAlive
   * ```
   */
  private var fSupportDataKeepAlive: Int,
  /**
   * C++ original:
   * ```cpp
   * Rect fBounds
   * ```
   */
  private var fBounds: Int,
  /**
   * C++ original:
   * ```cpp
   * SkM44 fDeviceToLocal
   * ```
   */
  private var fDeviceToLocal: Int,
  /**
   * C++ original:
   * ```cpp
   * int fStartGlyphIndex
   * ```
   */
  private var fStartGlyphIndex: Int,
  /**
   * C++ original:
   * ```cpp
   * int fGlyphCount
   * ```
   */
  private var fGlyphCount: Int,
  /**
   * C++ original:
   * ```cpp
   * SkColor fLuminanceColor
   * ```
   */
  private var fLuminanceColor: Int,
  /**
   * C++ original:
   * ```cpp
   * bool fUseGammaCorrectDistanceTable
   * ```
   */
  private var fUseGammaCorrectDistanceTable: Boolean,
  /**
   * C++ original:
   * ```cpp
   * SkPixelGeometry fPixelGeometry
   * ```
   */
  private var fPixelGeometry: Int,
  /**
   * C++ original:
   * ```cpp
   * Recorder* fRecorder
   * ```
   */
  private var fRecorder: Recorder?,
  /**
   * C++ original:
   * ```cpp
   * sktext::gpu::RendererData fRendererData
   * ```
   */
  private var fRendererData: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * SubRunData& operator=(SubRunData&&) = delete
   * ```
   */
  public fun assign(param0: SubRunData) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * SubRunData& operator=(const SubRunData& that) = default
   * ```
   */
  public fun bounds(): Int {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * Rect bounds() const { return fBounds; }
   * ```
   */
  public fun deviceToLocal(): Int {
    TODO("Implement deviceToLocal")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkM44& deviceToLocal() const { return fDeviceToLocal; }
   * ```
   */
  public fun subRun(): Int {
    TODO("Implement subRun")
  }

  /**
   * C++ original:
   * ```cpp
   * const sktext::gpu::AtlasSubRun* subRun() const { return fSubRun; }
   * ```
   */
  public fun startGlyphIndex(): Int {
    TODO("Implement startGlyphIndex")
  }

  /**
   * C++ original:
   * ```cpp
   * int startGlyphIndex() const { return fStartGlyphIndex; }
   * ```
   */
  public fun glyphCount(): Int {
    TODO("Implement glyphCount")
  }

  /**
   * C++ original:
   * ```cpp
   * int glyphCount() const { return fGlyphCount; }
   * ```
   */
  public fun luminanceColor(): Int {
    TODO("Implement luminanceColor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColor luminanceColor() const { return fLuminanceColor; }
   * ```
   */
  public fun useGammaCorrectDistanceTable(): Boolean {
    TODO("Implement useGammaCorrectDistanceTable")
  }

  /**
   * C++ original:
   * ```cpp
   * bool useGammaCorrectDistanceTable() const { return fUseGammaCorrectDistanceTable; }
   * ```
   */
  public fun pixelGeometry(): Int {
    TODO("Implement pixelGeometry")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPixelGeometry pixelGeometry() const { return fPixelGeometry; }
   * ```
   */
  public fun recorder(): Recorder {
    TODO("Implement recorder")
  }

  /**
   * C++ original:
   * ```cpp
   * Recorder* recorder() const { return fRecorder; }
   * ```
   */
  public fun rendererData(): Int {
    TODO("Implement rendererData")
  }
}
