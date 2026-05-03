package org.skia.modules

import org.skia.foundation.SkSp
import undefined.ScalarValue

/**
 * C++ original:
 * ```cpp
 * class EasyLevelsEffectAdapter final : public DiscardableAdapterBase<EasyLevelsEffectAdapter,
 *                                                                     sksg::ExternalColorFilter> {
 * public:
 *     EasyLevelsEffectAdapter(const skjson::ArrayValue& jprops,
 *                             sk_sp<sksg::RenderNode> layer,
 *                             const AnimationBuilder* abuilder)
 *         : INHERITED(sksg::ExternalColorFilter::Make(std::move(layer))) {
 *         enum : size_t {
 *                    kChannel_Index = 0,
 *                    // kHist_Index = 1,
 *                    kInBlack_Index = 2,
 *                    kInWhite_Index = 3,
 *                      kGamma_Index = 4,
 *                   kOutBlack_Index = 5,
 *                   kOutWhite_Index = 6,
 *             kClipToOutBlack_Index = 7,
 *             kClipToOutWhite_Index = 8,
 *         };
 *
 *         EffectBinder(jprops, *abuilder, this)
 *             .bind(       kChannel_Index, fChannel         )
 *             .bind(       kInBlack_Index, fMapper.fInBlack )
 *             .bind(       kInWhite_Index, fMapper.fInWhite )
 *             .bind(         kGamma_Index, fMapper.fGamma   )
 *             .bind(      kOutBlack_Index, fMapper.fOutBlack)
 *             .bind(      kOutWhite_Index, fMapper.fOutWhite)
 *             .bind(kClipToOutBlack_Index, fClip.fClipBlack )
 *             .bind(kClipToOutWhite_Index, fClip.fClipWhite );
 *     }
 *
 * private:
 *     void onSync() override {
 *         enum LottieChannel {
 *             kRGB_Channel = 1,
 *               kR_Channel = 2,
 *               kG_Channel = 3,
 *               kB_Channel = 4,
 *               kA_Channel = 5,
 *         };
 *
 *         const auto channel = SkScalarTruncToInt(fChannel);
 *         std::array<uint8_t, 256> lut;
 *         if (channel < kRGB_Channel || channel > kA_Channel || !fMapper.build_lut(lut, fClip)) {
 *             this->node()->setColorFilter(nullptr);
 *             return;
 *         }
 *
 *         this->node()->setColorFilter(SkColorFilters::TableARGB(
 *             channel == kA_Channel                            ? lut.data() : nullptr,
 *             channel == kR_Channel || channel == kRGB_Channel ? lut.data() : nullptr,
 *             channel == kG_Channel || channel == kRGB_Channel ? lut.data() : nullptr,
 *             channel == kB_Channel || channel == kRGB_Channel ? lut.data() : nullptr
 *         ));
 *     }
 *
 *     ChannelMapper fMapper;
 *     ClipInfo      fClip;
 *     ScalarValue   fChannel   = 1; // 1: RGB, 2: R, 3: G, 4: B, 5: A
 *
 *     using INHERITED = DiscardableAdapterBase<EasyLevelsEffectAdapter, sksg::ExternalColorFilter>;
 * }
 * ```
 */
public class EasyLevelsEffectAdapter public constructor(
  jprops: ArrayValue,
  layer: SkSp<RenderNode>,
  abuilder: AnimationBuilder?,
) : DiscardableAdapterBase(TODO()),
    EasyLevelsEffectAdapter,
    ExternalColorFilter {
  /**
   * C++ original:
   * ```cpp
   * ChannelMapper fMapper
   * ```
   */
  private var fMapper: ChannelMapper = TODO("Initialize fMapper")

  /**
   * C++ original:
   * ```cpp
   * ClipInfo      fClip
   * ```
   */
  private var fClip: ClipInfo = TODO("Initialize fClip")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue   fChannel   = 1
   * ```
   */
  private var fChannel: ScalarValue = TODO("Initialize fChannel")

  /**
   * C++ original:
   * ```cpp
   * void onSync() override {
   *         enum LottieChannel {
   *             kRGB_Channel = 1,
   *               kR_Channel = 2,
   *               kG_Channel = 3,
   *               kB_Channel = 4,
   *               kA_Channel = 5,
   *         };
   *
   *         const auto channel = SkScalarTruncToInt(fChannel);
   *         std::array<uint8_t, 256> lut;
   *         if (channel < kRGB_Channel || channel > kA_Channel || !fMapper.build_lut(lut, fClip)) {
   *             this->node()->setColorFilter(nullptr);
   *             return;
   *         }
   *
   *         this->node()->setColorFilter(SkColorFilters::TableARGB(
   *             channel == kA_Channel                            ? lut.data() : nullptr,
   *             channel == kR_Channel || channel == kRGB_Channel ? lut.data() : nullptr,
   *             channel == kG_Channel || channel == kRGB_Channel ? lut.data() : nullptr,
   *             channel == kB_Channel || channel == kRGB_Channel ? lut.data() : nullptr
   *         ));
   *     }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }
}
