package org.skia.gpu

import kotlin.Int
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class VelloRenderer final {
 * public:
 *     explicit VelloRenderer(const Caps*);
 *     ~VelloRenderer();
 *
 *     struct RenderParams {
 *         // Dimensions of the fine rasterization target
 *         uint32_t fWidth;
 *         uint32_t fHeight;
 *
 *         // The background color used during blending.
 *         SkColor4f fBaseColor;
 *
 *         // The antialiasing method.
 *         VelloAaConfig fAaConfig;
 *     };
 *
 *     // Run the full pipeline which supports compositing colors with different blend styles. Does
 *     // nothing if `scene` or target render dimensions are empty.
 *     //
 *     // The color type of `target` must be `kAlpha_8_SkColorType` on platforms that support R8Unorm
 *     // storage textures. Otherwise, it must be `kRGBA_8888_SkColorType`.
 *     std::unique_ptr<DispatchGroup> renderScene(const RenderParams&,
 *                                                const VelloScene&,
 *                                                sk_sp<TextureProxy> target,
 *                                                Recorder*) const;
 *
 * private:
 *     // Pipelines
 *     VelloBackdropDynStep fBackdrop;
 *     VelloBboxClearStep fBboxClear;
 *     VelloBinningStep fBinning;
 *     VelloClipLeafStep fClipLeaf;
 *     VelloClipReduceStep fClipReduce;
 *     VelloCoarseStep fCoarse;
 *     VelloDrawLeafStep fDrawLeaf;
 *     VelloDrawReduceStep fDrawReduce;
 *     VelloFlattenStep fFlatten;
 *     VelloPathCountStep fPathCount;
 *     VelloPathCountSetupStep fPathCountSetup;
 *     VelloPathTilingStep fPathTiling;
 *     VelloPathTilingSetupStep fPathTilingSetup;
 *     VelloPathtagReduceStep fPathtagReduce;
 *     VelloPathtagReduce2Step fPathtagReduce2;
 *     VelloPathtagScan1Step fPathtagScan1;
 *     VelloPathtagScanLargeStep fPathtagScanLarge;
 *     VelloPathtagScanSmallStep fPathtagScanSmall;
 *     VelloTileAllocStep fTileAlloc;
 *
 *     // Fine rasterization stage variants:
 *     std::unique_ptr<ComputeStep> fFineArea;
 *     std::unique_ptr<ComputeStep> fFineMsaa16;
 *     std::unique_ptr<ComputeStep> fFineMsaa8;
 * }
 * ```
 */
public data class VelloRenderer public constructor(
  /**
   * C++ original:
   * ```cpp
   * VelloBackdropDynStep fBackdrop
   * ```
   */
  private var fBackdrop: Int,
  /**
   * C++ original:
   * ```cpp
   * VelloBboxClearStep fBboxClear
   * ```
   */
  private var fBboxClear: Int,
  /**
   * C++ original:
   * ```cpp
   * VelloBinningStep fBinning
   * ```
   */
  private var fBinning: Int,
  /**
   * C++ original:
   * ```cpp
   * VelloClipLeafStep fClipLeaf
   * ```
   */
  private var fClipLeaf: Int,
  /**
   * C++ original:
   * ```cpp
   * VelloClipReduceStep fClipReduce
   * ```
   */
  private var fClipReduce: Int,
  /**
   * C++ original:
   * ```cpp
   * VelloCoarseStep fCoarse
   * ```
   */
  private var fCoarse: Int,
  /**
   * C++ original:
   * ```cpp
   * VelloDrawLeafStep fDrawLeaf
   * ```
   */
  private var fDrawLeaf: Int,
  /**
   * C++ original:
   * ```cpp
   * VelloDrawReduceStep fDrawReduce
   * ```
   */
  private var fDrawReduce: Int,
  /**
   * C++ original:
   * ```cpp
   * VelloFlattenStep fFlatten
   * ```
   */
  private var fFlatten: Int,
  /**
   * C++ original:
   * ```cpp
   * VelloPathCountStep fPathCount
   * ```
   */
  private var fPathCount: Int,
  /**
   * C++ original:
   * ```cpp
   * VelloPathCountSetupStep fPathCountSetup
   * ```
   */
  private var fPathCountSetup: Int,
  /**
   * C++ original:
   * ```cpp
   * VelloPathTilingStep fPathTiling
   * ```
   */
  private var fPathTiling: Int,
  /**
   * C++ original:
   * ```cpp
   * VelloPathTilingSetupStep fPathTilingSetup
   * ```
   */
  private var fPathTilingSetup: Int,
  /**
   * C++ original:
   * ```cpp
   * VelloPathtagReduceStep fPathtagReduce
   * ```
   */
  private var fPathtagReduce: Int,
  /**
   * C++ original:
   * ```cpp
   * VelloPathtagReduce2Step fPathtagReduce2
   * ```
   */
  private var fPathtagReduce2: Int,
  /**
   * C++ original:
   * ```cpp
   * VelloPathtagScan1Step fPathtagScan1
   * ```
   */
  private var fPathtagScan1: Int,
  /**
   * C++ original:
   * ```cpp
   * VelloPathtagScanLargeStep fPathtagScanLarge
   * ```
   */
  private var fPathtagScanLarge: Int,
  /**
   * C++ original:
   * ```cpp
   * VelloPathtagScanSmallStep fPathtagScanSmall
   * ```
   */
  private var fPathtagScanSmall: Int,
  /**
   * C++ original:
   * ```cpp
   * VelloTileAllocStep fTileAlloc
   * ```
   */
  private var fTileAlloc: Int,
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<ComputeStep> fFineArea
   * ```
   */
  private var fFineArea: Int,
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<ComputeStep> fFineMsaa16
   * ```
   */
  private var fFineMsaa16: Int,
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<ComputeStep> fFineMsaa8
   * ```
   */
  private var fFineMsaa8: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<DispatchGroup> renderScene(const RenderParams&,
   *                                                const VelloScene&,
   *                                                sk_sp<TextureProxy> target,
   *                                                Recorder*) const
   * ```
   */
  public fun renderScene(
    param0: RenderParams,
    param1: VelloScene,
    target: SkSp<TextureProxy>,
    param3: Recorder?,
  ): Int {
    TODO("Implement renderScene")
  }

  public data class RenderParams public constructor(
    public var fWidth: Int,
    public var fHeight: Int,
    public var fBaseColor: Int,
    public var fAaConfig: VelloAaConfig,
  )
}
