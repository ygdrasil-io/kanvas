package org.skia.modules

import kotlin.Int
import org.skia.foundation.SkSp
import undefined.ScalarValue

/**
 * C++ original:
 * ```cpp
 * class DashAdapter final : public DiscardableAdapterBase<DashAdapter, sksg::DashEffect> {
 * public:
 *     DashAdapter(const skjson::ArrayValue& jdash,
 *                 const AnimationBuilder& abuilder,
 *                 sk_sp<sksg::GeometryNode> geo)
 *         : INHERITED(sksg::DashEffect::Make(std::move(geo))) {
 *         SkASSERT(jdash.size() > 1);
 *
 *         // The dash is encoded as an arbitrary number of intervals (alternating dash/gap),
 *         // plus a single trailing offset.  Each value can be animated independently.
 *         const auto interval_count = jdash.size() - 1;
 *         fIntervals.resize(interval_count, 0);
 *
 *         for (size_t i = 0; i < jdash.size(); ++i) {
 *             if (const skjson::ObjectValue* jint = jdash[i]) {
 *                 auto* target = i < interval_count
 *                         ? &fIntervals[i]
 *                         : &fOffset;
 *                 this->bind(abuilder, (*jint)["v"], target);
 *             }
 *         }
 *     }
 *
 * private:
 *     void onSync() override {
 *         this->node()->setPhase(fOffset);
 *         this->node()->setIntervals(fIntervals);
 *     }
 *
 *     std::vector<ScalarValue> fIntervals;
 *     ScalarValue              fOffset = 0;
 *
 *     using INHERITED = DiscardableAdapterBase<DashAdapter, sksg::DashEffect>;
 * }
 * ```
 */
public class DashAdapter public constructor(
  jdash: ArrayValue,
  abuilder: AnimationBuilder,
  geo: SkSp<GeometryNode>,
) : DiscardableAdapterBase(TODO()),
    DashAdapter,
    DashEffect {
  /**
   * C++ original:
   * ```cpp
   * std::vector<ScalarValue> fIntervals
   * ```
   */
  private var fIntervals: Int = TODO("Initialize fIntervals")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue              fOffset = 0
   * ```
   */
  private var fOffset: ScalarValue = TODO("Initialize fOffset")

  /**
   * C++ original:
   * ```cpp
   * void onSync() override {
   *         this->node()->setPhase(fOffset);
   *         this->node()->setIntervals(fIntervals);
   *     }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }
}
