package org.skia.modules

import kotlin.Float
import kotlin.Int
import org.skia.foundation.SkSp
import undefined.ScalarValue

/**
 * C++ original:
 * ```cpp
 * class DisplacementMapAdapter final : public DiscardableAdapterBase<DisplacementMapAdapter,
 *                                                                    DisplacementNode> {
 * public:
 *     DisplacementMapAdapter(const skjson::ArrayValue& jprops,
 *                            const AnimationBuilder* abuilder,
 *                            sk_sp<DisplacementNode> node)
 *         : INHERITED(std::move(node)) {
 *         EffectBinder(jprops, *abuilder, this)
 *                 .bind(kUseForHorizontal_Index, fHorizontalSelector)
 *                 .bind(kMaxHorizontal_Index   , fMaxHorizontal     )
 *                 .bind(kUseForVertical_Index  , fVerticalSelector  )
 *                 .bind(kMaxVertical_Index     , fMaxVertical       )
 *                 .bind(kMapBehavior_Index     , fMapBehavior       )
 *                 .bind(kEdgeBehavior_Index    , fEdgeBehavior      )
 *                 .bind(kExpandOutput_Index    , fExpandOutput      );
 *     }
 *
 *     static EffectBuilder::LayerContent GetDisplacementSource(
 *             const skjson::ArrayValue& jprops,
 *             const EffectBuilder* ebuilder) {
 *
 *         if (const skjson::ObjectValue* jv = EffectBuilder::GetPropValue(jprops, kMapLayer_Index)) {
 *             return ebuilder->getLayerContent(ParseDefault((*jv)["k"], -1));
 *         }
 *
 *         return { nullptr, {0,0} };
 *     }
 *
 * private:
 *     enum : size_t {
 *         kMapLayer_Index         = 0,
 *         kUseForHorizontal_Index = 1,
 *         kMaxHorizontal_Index    = 2,
 *         kUseForVertical_Index   = 3,
 *         kMaxVertical_Index      = 4,
 *         kMapBehavior_Index      = 5,
 *         kEdgeBehavior_Index     = 6,
 *         kExpandOutput_Index     = 7,
 *     };
 *
 *     template <typename E>
 *     E ToEnum(float v) {
 *         // map one-based float "enums" to real enum types
 *         const auto uv = std::min(static_cast<unsigned>(v) - 1,
 *                                  static_cast<unsigned>(E::kLast));
 *
 *         return static_cast<E>(uv);
 *     }
 *
 *     void onSync() override {
 *         if (!this->node()) {
 *             return;
 *         }
 *
 *         this->node()->setScale({fMaxHorizontal, fMaxVertical});
 *         this->node()->setChildTileMode(fEdgeBehavior != 0 ? SkTileMode::kRepeat
 *                                                           : SkTileMode::kDecal);
 *
 *         this->node()->setPos(ToEnum<DisplacementNode::Pos>(fMapBehavior));
 *         this->node()->setXSelector(ToEnum<DisplacementNode::Selector>(fHorizontalSelector));
 *         this->node()->setYSelector(ToEnum<DisplacementNode::Selector>(fVerticalSelector));
 *         this->node()->setExpandBounds(fExpandOutput != 0);
 *     }
 *
 *     ScalarValue  fHorizontalSelector = 0,
 *                  fVerticalSelector   = 0,
 *                  fMaxHorizontal      = 0,
 *                  fMaxVertical        = 0,
 *                  fMapBehavior        = 0,
 *                  fEdgeBehavior       = 0,
 *                  fExpandOutput       = 0;
 *
 *     using INHERITED = DiscardableAdapterBase<DisplacementMapAdapter, DisplacementNode>;
 * }
 * ```
 */
public class DisplacementMapAdapter public constructor(
  jprops: ArrayValue,
  abuilder: AnimationBuilder?,
  node: SkSp<DisplacementNode>,
) : DiscardableAdapterBase(TODO()),
    DisplacementMapAdapter,
    DisplacementNode {
  /**
   * C++ original:
   * ```cpp
   * ScalarValue  fHorizontalSelector = 0
   * ```
   */
  private var fHorizontalSelector: ScalarValue = TODO("Initialize fHorizontalSelector")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue  fHorizontalSelector = 0,
   *                  fVerticalSelector   = 0
   * ```
   */
  private var fVerticalSelector: ScalarValue = TODO("Initialize fVerticalSelector")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue  fHorizontalSelector = 0,
   *                  fVerticalSelector   = 0,
   *                  fMaxHorizontal      = 0
   * ```
   */
  private var fMaxHorizontal: ScalarValue = TODO("Initialize fMaxHorizontal")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue  fHorizontalSelector = 0,
   *                  fVerticalSelector   = 0,
   *                  fMaxHorizontal      = 0,
   *                  fMaxVertical        = 0
   * ```
   */
  private var fMaxVertical: ScalarValue = TODO("Initialize fMaxVertical")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue  fHorizontalSelector = 0,
   *                  fVerticalSelector   = 0,
   *                  fMaxHorizontal      = 0,
   *                  fMaxVertical        = 0,
   *                  fMapBehavior        = 0
   * ```
   */
  private var fMapBehavior: ScalarValue = TODO("Initialize fMapBehavior")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue  fHorizontalSelector = 0,
   *                  fVerticalSelector   = 0,
   *                  fMaxHorizontal      = 0,
   *                  fMaxVertical        = 0,
   *                  fMapBehavior        = 0,
   *                  fEdgeBehavior       = 0
   * ```
   */
  private var fEdgeBehavior: ScalarValue = TODO("Initialize fEdgeBehavior")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue  fHorizontalSelector = 0,
   *                  fVerticalSelector   = 0,
   *                  fMaxHorizontal      = 0,
   *                  fMaxVertical        = 0,
   *                  fMapBehavior        = 0,
   *                  fEdgeBehavior       = 0,
   *                  fExpandOutput       = 0
   * ```
   */
  private var fExpandOutput: ScalarValue = TODO("Initialize fExpandOutput")

  /**
   * C++ original:
   * ```cpp
   *     template <typename E>
   *     E ToEnum(float v) {
   *         // map one-based float "enums" to real enum types
   *         const auto uv = std::min(static_cast<unsigned>(v) - 1,
   *                                  static_cast<unsigned>(E::kLast));
   *
   *         return static_cast<E>(uv);
   *     }
   * ```
   */
  public override fun <E> toEnum(v: Float): E {
    TODO("Implement toEnum")
  }

  /**
   * C++ original:
   * ```cpp
   * void onSync() override {
   *         if (!this->node()) {
   *             return;
   *         }
   *
   *         this->node()->setScale({fMaxHorizontal, fMaxVertical});
   *         this->node()->setChildTileMode(fEdgeBehavior != 0 ? SkTileMode::kRepeat
   *                                                           : SkTileMode::kDecal);
   *
   *         this->node()->setPos(ToEnum<DisplacementNode::Pos>(fMapBehavior));
   *         this->node()->setXSelector(ToEnum<DisplacementNode::Selector>(fHorizontalSelector));
   *         this->node()->setYSelector(ToEnum<DisplacementNode::Selector>(fVerticalSelector));
   *         this->node()->setExpandBounds(fExpandOutput != 0);
   *     }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }

  public companion object {
    public val kMapLayerIndex: Int = TODO("Initialize kMapLayerIndex")

    public val kUseForHorizontalIndex: Int = TODO("Initialize kUseForHorizontalIndex")

    public val kMaxHorizontalIndex: Int = TODO("Initialize kMaxHorizontalIndex")

    public val kUseForVerticalIndex: Int = TODO("Initialize kUseForVerticalIndex")

    public val kMaxVerticalIndex: Int = TODO("Initialize kMaxVerticalIndex")

    public val kMapBehaviorIndex: Int = TODO("Initialize kMapBehaviorIndex")

    public val kEdgeBehaviorIndex: Int = TODO("Initialize kEdgeBehaviorIndex")

    public val kExpandOutputIndex: Int = TODO("Initialize kExpandOutputIndex")

    /**
     * C++ original:
     * ```cpp
     * static EffectBuilder::LayerContent GetDisplacementSource(
     *             const skjson::ArrayValue& jprops,
     *             const EffectBuilder* ebuilder) {
     *
     *         if (const skjson::ObjectValue* jv = EffectBuilder::GetPropValue(jprops, kMapLayer_Index)) {
     *             return ebuilder->getLayerContent(ParseDefault((*jv)["k"], -1));
     *         }
     *
     *         return { nullptr, {0,0} };
     *     }
     * ```
     */
    public override fun getDisplacementSource(jprops: ArrayValue, ebuilder: EffectBuilder?): EffectBuilder.LayerContent {
      TODO("Implement getDisplacementSource")
    }
  }
}
