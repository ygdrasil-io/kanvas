package org.skia.modules

import org.skia.foundation.SkSp
import org.skia.math.SkSize
import undefined.ScalarValue
import undefined.Vec2Value

/**
 * C++ original:
 * ```cpp
 * class MotionTileAdapter final : public DiscardableAdapterBase<MotionTileAdapter, TileRenderNode> {
 * public:
 *     MotionTileAdapter(const skjson::ArrayValue& jprops,
 *                       sk_sp<sksg::RenderNode> layer,
 *                       const AnimationBuilder& abuilder,
 *                       const SkSize& layer_size)
 *         : INHERITED(sk_make_sp<TileRenderNode>(layer_size, std::move(layer))) {
 *
 *         enum : size_t {
 *                       kTileCenter_Index = 0,
 *                        kTileWidth_Index = 1,
 *                       kTileHeight_Index = 2,
 *                      kOutputWidth_Index = 3,
 *                     kOutputHeight_Index = 4,
 *                      kMirrorEdges_Index = 5,
 *                            kPhase_Index = 6,
 *             kHorizontalPhaseShift_Index = 7,
 *         };
 *
 *         EffectBinder(jprops, abuilder, this)
 *             .bind(          kTileCenter_Index, fTileCenter     )
 *             .bind(           kTileWidth_Index, fTileW          )
 *             .bind(          kTileHeight_Index, fTileH          )
 *             .bind(         kOutputWidth_Index, fOutputW        )
 *             .bind(        kOutputHeight_Index, fOutputH        )
 *             .bind(         kMirrorEdges_Index, fMirrorEdges    )
 *             .bind(               kPhase_Index, fPhase          )
 *             .bind(kHorizontalPhaseShift_Index, fHorizontalPhase);
 *     }
 *
 * private:
 *     void onSync() override {
 *         const auto& tiler = this->node();
 *
 *         tiler->setTileCenter({fTileCenter.x, fTileCenter.y});
 *         tiler->setTileWidth (fTileW);
 *         tiler->setTileHeight(fTileH);
 *         tiler->setOutputWidth (fOutputW);
 *         tiler->setOutputHeight(fOutputH);
 *         tiler->setPhase(fPhase);
 *         tiler->setMirrorEdges(SkToBool(fMirrorEdges));
 *         tiler->setHorizontalPhase(SkToBool(fHorizontalPhase));
 *     }
 *
 *     Vec2Value   fTileCenter      = {0,0};
 *     ScalarValue fTileW           = 1,
 *                 fTileH           = 1,
 *                 fOutputW         = 1,
 *                 fOutputH         = 1,
 *                 fMirrorEdges     = 0,
 *                 fPhase           = 0,
 *                 fHorizontalPhase = 0;
 *
 *     using INHERITED = DiscardableAdapterBase<MotionTileAdapter, TileRenderNode>;
 * }
 * ```
 */
public class MotionTileAdapter public constructor(
  jprops: ArrayValue,
  layer: SkSp<RenderNode>,
  abuilder: AnimationBuilder,
  layerSize: SkSize,
) : DiscardableAdapterBase(TODO()),
    MotionTileAdapter,
    TileRenderNode {
  /**
   * C++ original:
   * ```cpp
   * Vec2Value   fTileCenter      = {0,0}
   * ```
   */
  private var fTileCenter: Vec2Value = TODO("Initialize fTileCenter")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fTileW           = 1
   * ```
   */
  private var fTileW: ScalarValue = TODO("Initialize fTileW")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fTileW           = 1,
   *                 fTileH           = 1
   * ```
   */
  private var fTileH: ScalarValue = TODO("Initialize fTileH")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fTileW           = 1,
   *                 fTileH           = 1,
   *                 fOutputW         = 1
   * ```
   */
  private var fOutputW: ScalarValue = TODO("Initialize fOutputW")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fTileW           = 1,
   *                 fTileH           = 1,
   *                 fOutputW         = 1,
   *                 fOutputH         = 1
   * ```
   */
  private var fOutputH: ScalarValue = TODO("Initialize fOutputH")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fTileW           = 1,
   *                 fTileH           = 1,
   *                 fOutputW         = 1,
   *                 fOutputH         = 1,
   *                 fMirrorEdges     = 0
   * ```
   */
  private var fMirrorEdges: ScalarValue = TODO("Initialize fMirrorEdges")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fTileW           = 1,
   *                 fTileH           = 1,
   *                 fOutputW         = 1,
   *                 fOutputH         = 1,
   *                 fMirrorEdges     = 0,
   *                 fPhase           = 0
   * ```
   */
  private var fPhase: ScalarValue = TODO("Initialize fPhase")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fTileW           = 1,
   *                 fTileH           = 1,
   *                 fOutputW         = 1,
   *                 fOutputH         = 1,
   *                 fMirrorEdges     = 0,
   *                 fPhase           = 0,
   *                 fHorizontalPhase = 0
   * ```
   */
  private var fHorizontalPhase: ScalarValue = TODO("Initialize fHorizontalPhase")

  /**
   * C++ original:
   * ```cpp
   * void onSync() override {
   *         const auto& tiler = this->node();
   *
   *         tiler->setTileCenter({fTileCenter.x, fTileCenter.y});
   *         tiler->setTileWidth (fTileW);
   *         tiler->setTileHeight(fTileH);
   *         tiler->setOutputWidth (fOutputW);
   *         tiler->setOutputHeight(fOutputH);
   *         tiler->setPhase(fPhase);
   *         tiler->setMirrorEdges(SkToBool(fMirrorEdges));
   *         tiler->setHorizontalPhase(SkToBool(fHorizontalPhase));
   *     }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }
}
