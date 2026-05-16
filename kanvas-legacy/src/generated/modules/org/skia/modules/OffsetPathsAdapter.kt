package org.skia.modules

import org.skia.foundation.SkSp
import undefined.ScalarValue

/**
 * C++ original:
 * ```cpp
 * class OffsetPathsAdapter final : public DiscardableAdapterBase<OffsetPathsAdapter,
 *                                                                sksg::OffsetEffect> {
 * public:
 *     OffsetPathsAdapter(const skjson::ObjectValue& joffset,
 *                        const AnimationBuilder& abuilder,
 *                        sk_sp<sksg::GeometryNode> child)
 *         : INHERITED(sksg::OffsetEffect::Make(std::move(child))) {
 *         static constexpr SkPaint::Join gJoinMap[] = {
 *             SkPaint::kMiter_Join,  // 'lj': 1
 *             SkPaint::kRound_Join,  // 'lj': 2
 *             SkPaint::kBevel_Join,  // 'lj': 3
 *         };
 *
 *         const auto join = ParseDefault<int>(joffset["lj"], 1) - 1;
 *         this->node()->setJoin(gJoinMap[SkTPin<int>(join, 0, std::size(gJoinMap) - 1)]);
 *
 *         this->bind(abuilder, joffset["a" ], fAmount);
 *         this->bind(abuilder, joffset["ml"], fMiterLimit);
 *     }
 *
 * private:
 *     void onSync() override {
 *         this->node()->setOffset(fAmount);
 *         this->node()->setMiterLimit(fMiterLimit);
 *     }
 *
 *     ScalarValue fAmount     = 0,
 *                 fMiterLimit = 0;
 *
 *     using INHERITED = DiscardableAdapterBase<OffsetPathsAdapter, sksg::OffsetEffect>;
 * }
 * ```
 */
public class OffsetPathsAdapter public constructor(
  joffset: ObjectValue,
  abuilder: AnimationBuilder,
  child: SkSp<GeometryNode>,
) : DiscardableAdapterBase(TODO()),
    OffsetPathsAdapter,
    OffsetEffect {
  /**
   * C++ original:
   * ```cpp
   * ScalarValue fAmount     = 0
   * ```
   */
  private var fAmount: ScalarValue = TODO("Initialize fAmount")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fAmount     = 0,
   *                 fMiterLimit = 0
   * ```
   */
  private var fMiterLimit: ScalarValue = TODO("Initialize fMiterLimit")

  /**
   * C++ original:
   * ```cpp
   * void onSync() override {
   *         this->node()->setOffset(fAmount);
   *         this->node()->setMiterLimit(fMiterLimit);
   *     }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }
}
