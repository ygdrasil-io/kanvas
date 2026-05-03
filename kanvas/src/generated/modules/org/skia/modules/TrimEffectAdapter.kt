package org.skia.modules

import org.skia.foundation.SkSp
import undefined.ScalarValue

/**
 * C++ original:
 * ```cpp
 * class TrimEffectAdapter final : public DiscardableAdapterBase<TrimEffectAdapter, sksg::TrimEffect> {
 * public:
 *     TrimEffectAdapter(const skjson::ObjectValue& jtrim,
 *                       const AnimationBuilder& abuilder,
 *                       sk_sp<sksg::GeometryNode> child)
 *         : INHERITED(sksg::TrimEffect::Make(std::move(child))) {
 *         this->bind(abuilder, jtrim["s"], &fStart);
 *         this->bind(abuilder, jtrim["e"], &fEnd);
 *         this->bind(abuilder, jtrim["o"], &fOffset);
 *     }
 *
 * private:
 *     void onSync() override {
 *         // BM semantics: start/end are percentages, offset is "degrees" (?!).
 *         const auto  start = fStart  / 100,
 *                       end = fEnd    / 100,
 *                    offset = fOffset / 360;
 *
 *         auto startT = std::min(start, end) + offset,
 *               stopT = std::max(start, end) + offset;
 *         auto   mode = SkTrimPathEffect::Mode::kNormal;
 *
 *         if (stopT - startT < 1) {
 *             startT -= SkScalarFloorToScalar(startT);
 *             stopT  -= SkScalarFloorToScalar(stopT);
 *
 *             if (startT > stopT) {
 *                 using std::swap;
 *                 swap(startT, stopT);
 *                 mode = SkTrimPathEffect::Mode::kInverted;
 *             }
 *         } else {
 *             startT = 0;
 *             stopT  = 1;
 *         }
 *
 *         this->node()->setStart(startT);
 *         this->node()->setStop(stopT);
 *         this->node()->setMode(mode);
 *     }
 *
 *     ScalarValue fStart  =   0,
 *                 fEnd    = 100,
 *                 fOffset =   0;
 *
 *     using INHERITED = DiscardableAdapterBase<TrimEffectAdapter, sksg::TrimEffect>;
 * }
 * ```
 */
public class TrimEffectAdapter public constructor(
  jtrim: ObjectValue,
  abuilder: AnimationBuilder,
  child: SkSp<GeometryNode>,
) : DiscardableAdapterBase(TODO()),
    TrimEffectAdapter,
    TrimEffect {
  /**
   * C++ original:
   * ```cpp
   * ScalarValue fStart  =   0
   * ```
   */
  private var fStart: ScalarValue = TODO("Initialize fStart")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fStart  =   0,
   *                 fEnd    = 100
   * ```
   */
  private var fEnd: ScalarValue = TODO("Initialize fEnd")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fStart  =   0,
   *                 fEnd    = 100,
   *                 fOffset =   0
   * ```
   */
  private var fOffset: ScalarValue = TODO("Initialize fOffset")

  /**
   * C++ original:
   * ```cpp
   * void onSync() override {
   *         // BM semantics: start/end are percentages, offset is "degrees" (?!).
   *         const auto  start = fStart  / 100,
   *                       end = fEnd    / 100,
   *                    offset = fOffset / 360;
   *
   *         auto startT = std::min(start, end) + offset,
   *               stopT = std::max(start, end) + offset;
   *         auto   mode = SkTrimPathEffect::Mode::kNormal;
   *
   *         if (stopT - startT < 1) {
   *             startT -= SkScalarFloorToScalar(startT);
   *             stopT  -= SkScalarFloorToScalar(stopT);
   *
   *             if (startT > stopT) {
   *                 using std::swap;
   *                 swap(startT, stopT);
   *                 mode = SkTrimPathEffect::Mode::kInverted;
   *             }
   *         } else {
   *             startT = 0;
   *             stopT  = 1;
   *         }
   *
   *         this->node()->setStart(startT);
   *         this->node()->setStop(stopT);
   *         this->node()->setMode(mode);
   *     }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }
}
