package org.skia.modules

import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class ProLevelsEffectAdapter final : public DiscardableAdapterBase<ProLevelsEffectAdapter,
 *                                                                    sksg::ExternalColorFilter> {
 * public:
 *     ProLevelsEffectAdapter(const skjson::ArrayValue& jprops,
 *                            sk_sp<sksg::RenderNode> layer,
 *                            const AnimationBuilder* abuilder)
 *         : INHERITED(sksg::ExternalColorFilter::Make(std::move(layer))) {
 *         enum : size_t {
 *             //    kHistChan_Index =  0,
 *             //        kHist_Index =  1,
 *             //    kRGBBegin_Index =  2,
 *                 kRGBInBlack_Index =  3,
 *                 kRGBInWhite_Index =  4,
 *                   kRGBGamma_Index =  5,
 *                kRGBOutBlack_Index =  6,
 *                kRGBOutWhite_Index =  7,
 *             //      kRGBEnd_Index =  8,
 *             //      kRBegin_Index =  9,
 *                   kRInBlack_Index = 10,
 *                   kRInWhite_Index = 11,
 *                     kRGamma_Index = 12,
 *                  kROutBlack_Index = 13,
 *                  kROutWhite_Index = 14,
 *             //        kREnd_Index = 15,
 *             //      kGBegin_Index = 16,
 *                   kGInBlack_Index = 17,
 *                   kGInWhite_Index = 18,
 *                     kGGamma_Index = 19,
 *                  kGOutBlack_Index = 20,
 *                  kGOutWhite_Index = 21,
 *             //        kGEnd_Index = 22,
 *             //      kBBegin_Index = 23,
 *                   kBInBlack_Index = 24,
 *                   kBInWhite_Index = 25,
 *                     kBGamma_Index = 26,
 *                  kBOutBlack_Index = 27,
 *                  kBOutWhite_Index = 28,
 *             //        kBEnd_Index = 29,
 *             //      kABegin_Index = 30,
 *                   kAInBlack_Index = 31,
 *                   kAInWhite_Index = 32,
 *                     kAGamma_Index = 33,
 *                  kAOutBlack_Index = 34,
 *                  kAOutWhite_Index = 35,
 *             //        kAEnd_Index = 36,
 *             kClipToOutBlack_Index = 37,
 *             kClipToOutWhite_Index = 38,
 *         };
 *
 *         EffectBinder(jprops, *abuilder, this)
 *             .bind( kRGBInBlack_Index, fRGBMapper.fInBlack )
 *             .bind( kRGBInWhite_Index, fRGBMapper.fInWhite )
 *             .bind(   kRGBGamma_Index, fRGBMapper.fGamma   )
 *             .bind(kRGBOutBlack_Index, fRGBMapper.fOutBlack)
 *             .bind(kRGBOutWhite_Index, fRGBMapper.fOutWhite)
 *
 *             .bind( kRInBlack_Index, fRMapper.fInBlack )
 *             .bind( kRInWhite_Index, fRMapper.fInWhite )
 *             .bind(   kRGamma_Index, fRMapper.fGamma   )
 *             .bind(kROutBlack_Index, fRMapper.fOutBlack)
 *             .bind(kROutWhite_Index, fRMapper.fOutWhite)
 *
 *             .bind( kGInBlack_Index, fGMapper.fInBlack )
 *             .bind( kGInWhite_Index, fGMapper.fInWhite )
 *             .bind(   kGGamma_Index, fGMapper.fGamma   )
 *             .bind(kGOutBlack_Index, fGMapper.fOutBlack)
 *             .bind(kGOutWhite_Index, fGMapper.fOutWhite)
 *
 *             .bind( kBInBlack_Index, fBMapper.fInBlack )
 *             .bind( kBInWhite_Index, fBMapper.fInWhite )
 *             .bind(   kBGamma_Index, fBMapper.fGamma   )
 *             .bind(kBOutBlack_Index, fBMapper.fOutBlack)
 *             .bind(kBOutWhite_Index, fBMapper.fOutWhite)
 *
 *             .bind( kAInBlack_Index, fAMapper.fInBlack )
 *             .bind( kAInWhite_Index, fAMapper.fInWhite )
 *             .bind(   kAGamma_Index, fAMapper.fGamma   )
 *             .bind(kAOutBlack_Index, fAMapper.fOutBlack)
 *             .bind(kAOutWhite_Index, fAMapper.fOutWhite);
 *     }
 *
 * private:
 *     void onSync() override {
 *         std::array<uint8_t, 256> a_lut_storage,
 *                                  r_lut_storage,
 *                                  g_lut_storage,
 *                                  b_lut_storage;
 *
 *         auto cf = SkColorFilters::TableARGB(fAMapper.build_lut(a_lut_storage, fClip),
 *                                             fRMapper.build_lut(r_lut_storage, fClip),
 *                                             fGMapper.build_lut(g_lut_storage, fClip),
 *                                             fBMapper.build_lut(b_lut_storage, fClip));
 *
 *         // The RGB mapper composes outside individual channel mappers.
 *         if (const auto* rgb_lut = fRGBMapper.build_lut(a_lut_storage, fClip)) {
 *             cf = SkColorFilters::Compose(SkColorFilters::TableARGB(nullptr,
 *                                                                    rgb_lut,
 *                                                                    rgb_lut,
 *                                                                    rgb_lut),
 *                                          std::move(cf));
 *         }
 *
 *         this->node()->setColorFilter(std::move(cf));
 *     }
 *
 *     ChannelMapper fRGBMapper,
 *                   fRMapper,
 *                   fGMapper,
 *                   fBMapper,
 *                   fAMapper;
 *
 *     ClipInfo      fClip;
 *
 *     using INHERITED = DiscardableAdapterBase<ProLevelsEffectAdapter, sksg::ExternalColorFilter>;
 * }
 * ```
 */
public class ProLevelsEffectAdapter public constructor(
  jprops: ArrayValue,
  layer: SkSp<RenderNode>,
  abuilder: AnimationBuilder?,
) : DiscardableAdapterBase(TODO()),
    ProLevelsEffectAdapter,
    ExternalColorFilter {
  /**
   * C++ original:
   * ```cpp
   * ChannelMapper fRGBMapper
   * ```
   */
  private var fRGBMapper: ChannelMapper = TODO("Initialize fRGBMapper")

  /**
   * C++ original:
   * ```cpp
   * ChannelMapper fRGBMapper,
   *                   fRMapper
   * ```
   */
  private var fRMapper: ChannelMapper = TODO("Initialize fRMapper")

  /**
   * C++ original:
   * ```cpp
   * ChannelMapper fRGBMapper,
   *                   fRMapper,
   *                   fGMapper
   * ```
   */
  private var fGMapper: ChannelMapper = TODO("Initialize fGMapper")

  /**
   * C++ original:
   * ```cpp
   * ChannelMapper fRGBMapper,
   *                   fRMapper,
   *                   fGMapper,
   *                   fBMapper
   * ```
   */
  private var fBMapper: ChannelMapper = TODO("Initialize fBMapper")

  /**
   * C++ original:
   * ```cpp
   * ChannelMapper fRGBMapper,
   *                   fRMapper,
   *                   fGMapper,
   *                   fBMapper,
   *                   fAMapper
   * ```
   */
  private var fAMapper: ChannelMapper = TODO("Initialize fAMapper")

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
   * void onSync() override {
   *         std::array<uint8_t, 256> a_lut_storage,
   *                                  r_lut_storage,
   *                                  g_lut_storage,
   *                                  b_lut_storage;
   *
   *         auto cf = SkColorFilters::TableARGB(fAMapper.build_lut(a_lut_storage, fClip),
   *                                             fRMapper.build_lut(r_lut_storage, fClip),
   *                                             fGMapper.build_lut(g_lut_storage, fClip),
   *                                             fBMapper.build_lut(b_lut_storage, fClip));
   *
   *         // The RGB mapper composes outside individual channel mappers.
   *         if (const auto* rgb_lut = fRGBMapper.build_lut(a_lut_storage, fClip)) {
   *             cf = SkColorFilters::Compose(SkColorFilters::TableARGB(nullptr,
   *                                                                    rgb_lut,
   *                                                                    rgb_lut,
   *                                                                    rgb_lut),
   *                                          std::move(cf));
   *         }
   *
   *         this->node()->setColorFilter(std::move(cf));
   *     }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }
}
