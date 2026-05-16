package org.skia.modules

import SkShapers.Factory
import kotlin.CharArray
import org.skia.foundation.SkData
import org.skia.foundation.SkFontMgr
import org.skia.foundation.SkSp
import org.skia.memory.SkArenaAlloc
import org.skia.tests.Builder

/**
 * C++ original:
 * ```cpp
 * class Preshaper {
 * public:
 *     Preshaper(sk_sp<ResourceProvider> rp, sk_sp<SkFontMgr> fontmgr, sk_sp<SkShapers::Factory> sfact)
 *         : fFontMgr(fontmgr)
 *         , fShapersFact(sfact)
 *         , fBuilder(rp ? std::move(rp) : sk_make_sp<NullResourceProvider>(),
 *                    std::move(fontmgr),
 *                    nullptr, nullptr, nullptr, nullptr, nullptr,
 *                    std::move(sfact),
 *                    &fStats, {0, 0}, 1, 1, 0)
 *         , fAlloc(4096)
 *     {}
 *
 *     void preshape(const Value& jlottie) {
 *         fBuilder.parseFonts(jlottie["fonts"], jlottie["chars"]);
 *
 *         this->preshapeComp(jlottie);
 *         if (const ArrayValue* jassets = jlottie["assets"]) {
 *             for (const auto& jasset : *jassets) {
 *                 this->preshapeComp(jasset);
 *             }
 *         }
 *
 *         const auto& [fonts, chars] = fGlyphCache.toLottie(fAlloc, jlottie["fonts"]);
 *
 *         jlottie.as<ObjectValue>().writable("fonts", fAlloc) = fonts;
 *         jlottie.as<ObjectValue>().writable("chars", fAlloc) = chars;
 *     }
 *
 * private:
 *     class NullResourceProvider final : public ResourceProvider {
 *         sk_sp<SkData> load(const char[], const char[]) const override { return nullptr; }
 *     };
 *
 *     void preshapeComp(const Value& jcomp) {
 *        if (const ArrayValue* jlayers = jcomp["layers"]) {
 *            for (const auto& jlayer : *jlayers) {
 *                this->preshapeLayer(jlayer);
 *            }
 *        }
 *     }
 *
 *     void preshapeLayer(const Value& jlayer) {
 *         static constexpr int kTextLayerType = 5;
 *         if (skottie::ParseDefault<int>(jlayer["ty"], -1) != kTextLayerType) {
 *             return;
 *         }
 *
 *         const ArrayValue* jtxts = jlayer["t"]["d"]["k"];
 *         if (!jtxts) {
 *             return;
 *         }
 *
 *         for (const auto& jtxt : *jtxts) {
 *             const Value& jtxt_val = jtxt["s"];
 *
 *             const StringValue* jfont_name = jtxt_val["f"];
 *             skottie::TextValue txt_val;
 *             if (!skottie::internal::Parse(jtxt_val, fBuilder , &txt_val) || !jfont_name) {
 *                 continue;
 *             }
 *
 *             const std::string_view font_name(jfont_name->begin(), jfont_name->size());
 *
 *             static constexpr float kMinSize =    0.1f,
 *                                    kMaxSize = 1296.0f;
 *             const skottie::Shaper::TextDesc text_desc = {
 *                 txt_val.fTypeface,
 *                 SkTPin(txt_val.fTextSize,    kMinSize, kMaxSize),
 *                 SkTPin(txt_val.fMinTextSize, kMinSize, kMaxSize),
 *                 SkTPin(txt_val.fMaxTextSize, kMinSize, kMaxSize),
 *                 txt_val.fLineHeight,
 *                 txt_val.fLineShift,
 *                 txt_val.fAscent,
 *                 txt_val.fHAlign,
 *                 txt_val.fVAlign,
 *                 txt_val.fResize,
 *                 txt_val.fLineBreak,
 *                 txt_val.fDirection,
 *                 txt_val.fCapitalization,
 *                 txt_val.fMaxLines,
 *                 skottie::Shaper::Flags::kFragmentGlyphs |
 *                     skottie::Shaper::Flags::kTrackFragmentAdvanceAscent |
 *                     skottie::Shaper::Flags::kClusters,
 *                 txt_val.fLocale.isEmpty()     ? nullptr : txt_val.fLocale.c_str(),
 *                 txt_val.fFontFamily.isEmpty() ? nullptr : txt_val.fFontFamily.c_str(),
 *             };
 *
 *             auto shape_result = skottie::Shaper::Shape(txt_val.fText, text_desc, txt_val.fBox,
 *                                                        fFontMgr, fShapersFact);
 *
 *             auto shaped_glyph_info = [this](SkUnichar ch, const SkPoint& pos, float advance,
 *                                             size_t line, size_t cluster) -> Value {
 *                 const NumberValue jpos[] = { NumberValue(pos.fX), NumberValue(pos.fY) };
 *                 char utf8[SkUTF::kMaxBytesInUTF8Sequence];
 *                 const size_t utf8_len = SkUTF::ToUTF8(ch, utf8);
 *
 *                 const skjson::Member fields[] = {
 *                     { StringValue("ch" , fAlloc), StringValue(utf8, utf8_len, fAlloc)       },
 *                     { StringValue("ps" , fAlloc), ArrayValue(jpos, std::size(jpos), fAlloc) },
 *                     { StringValue("w"  , fAlloc), NumberValue(advance)                      },
 *                     { StringValue("l"  , fAlloc), NumberValue(SkToInt(line))                },
 *                     { StringValue("cix", fAlloc), NumberValue(SkToInt(cluster))             },
 *                 };
 *
 *                 return ObjectValue(fields, std::size(fields), fAlloc);
 *             };
 *
 *             std::vector<Value> shaped_info;
 *             for (const auto& frag : shape_result.fFragments) {
 *                 SkASSERT(frag.fGlyphs.fGlyphIDs.size() == 1);
 *                 SkASSERT(frag.fGlyphs.fClusters.size() == frag.fGlyphs.fGlyphIDs.size());
 *                 size_t offset = 0;
 *                 for (const auto& runrec : frag.fGlyphs.fRuns) {
 *                     const SkGlyphID*  glyphs = frag.fGlyphs.fGlyphIDs.data() + offset;
 *                     const SkPoint* glyph_pos = frag.fGlyphs.fGlyphPos.data() + offset;
 *                     const size_t*   clusters = frag.fGlyphs.fClusters.data() + offset;
 *                     const char*     end_utf8 = txt_val.fText.c_str() + txt_val.fText.size();
 *                     for (size_t i = 0; i < runrec.fSize; ++i) {
 *                         // TODO: we are only considering the fist code point in the cluster,
 *                         // similar to how Lottie handles custom/path-based fonts at the moment.
 *                         // To correctly handle larger clusters, we'll have to check for collisions
 *                         // and potentially allocate a synthetic glyph IDs.  TBD.
 *                         const char* ch_utf8 = txt_val.fText.c_str() + clusters[i];
 *                         const SkUnichar ch = SkUTF::NextUTF8(&ch_utf8, end_utf8);
 *
 *                         fGlyphCache.addGlyph(font_name, ch, runrec.fFont, glyphs[i]);
 *                         shaped_info.push_back(shaped_glyph_info(ch,
 *                                                                 frag.fOrigin + glyph_pos[i],
 *                                                                 frag.fAdvance,
 *                                                                 frag.fLineIndex,
 *                                                                 clusters[i]));
 *                     }
 *                     offset += runrec.fSize;
 *                 }
 *             }
 *
 *             // Preshaped glyphs.
 *             jtxt_val.as<ObjectValue>().writable("gl", fAlloc) =
 *                 ArrayValue(shaped_info.data(), shaped_info.size(), fAlloc);
 *             // Effecive size for preshaped glyphs, accounting for auto-sizing scale.
 *             jtxt_val.as<ObjectValue>().writable("gs", fAlloc) =
 *                 NumberValue(text_desc.fTextSize * shape_result.fScale);
 *             // Updated font name.
 *             jtxt_val.as<ObjectValue>().writable("f", fAlloc) =
 *                 StringValue(preshapedFontName(font_name).c_str(), fAlloc);
 *         }
 *     }
 *
 *     const sk_sp<SkFontMgr>              fFontMgr;
 *     const sk_sp<SkShapers::Factory>     fShapersFact;
 *     skottie::Animation::Builder::Stats  fStats;
 *     skottie::internal::AnimationBuilder fBuilder;
 *     SkArenaAlloc                        fAlloc;
 *     GlyphCache                          fGlyphCache;
 * }
 * ```
 */
public data class Preshaper public constructor(
  /**
   * C++ original:
   * ```cpp
   * const sk_sp<SkFontMgr>              fFontMgr
   * ```
   */
  private val fFontMgr: SkSp<SkFontMgr>,
  /**
   * C++ original:
   * ```cpp
   * const sk_sp<SkShapers::Factory>     fShapersFact
   * ```
   */
  private val fShapersFact: SkSp<Factory>,
  /**
   * C++ original:
   * ```cpp
   * skottie::Animation::Builder::Stats  fStats
   * ```
   */
  private var fStats: Builder.Stats,
  /**
   * C++ original:
   * ```cpp
   * skottie::internal::AnimationBuilder fBuilder
   * ```
   */
  private var fBuilder: AnimationBuilder,
  /**
   * C++ original:
   * ```cpp
   * SkArenaAlloc                        fAlloc
   * ```
   */
  private var fAlloc: SkArenaAlloc,
  /**
   * C++ original:
   * ```cpp
   * GlyphCache                          fGlyphCache
   * ```
   */
  private var fGlyphCache: GlyphCache,
) {
  /**
   * C++ original:
   * ```cpp
   * void preshape(const Value& jlottie) {
   *         fBuilder.parseFonts(jlottie["fonts"], jlottie["chars"]);
   *
   *         this->preshapeComp(jlottie);
   *         if (const ArrayValue* jassets = jlottie["assets"]) {
   *             for (const auto& jasset : *jassets) {
   *                 this->preshapeComp(jasset);
   *             }
   *         }
   *
   *         const auto& [fonts, chars] = fGlyphCache.toLottie(fAlloc, jlottie["fonts"]);
   *
   *         jlottie.as<ObjectValue>().writable("fonts", fAlloc) = fonts;
   *         jlottie.as<ObjectValue>().writable("chars", fAlloc) = chars;
   *     }
   * ```
   */
  public fun preshape(jlottie: Value) {
    TODO("Implement preshape")
  }

  /**
   * C++ original:
   * ```cpp
   * void preshapeComp(const Value& jcomp) {
   *        if (const ArrayValue* jlayers = jcomp["layers"]) {
   *            for (const auto& jlayer : *jlayers) {
   *                this->preshapeLayer(jlayer);
   *            }
   *        }
   *     }
   * ```
   */
  private fun preshapeComp(jcomp: Value) {
    TODO("Implement preshapeComp")
  }

  /**
   * C++ original:
   * ```cpp
   * void preshapeLayer(const Value& jlayer) {
   *         static constexpr int kTextLayerType = 5;
   *         if (skottie::ParseDefault<int>(jlayer["ty"], -1) != kTextLayerType) {
   *             return;
   *         }
   *
   *         const ArrayValue* jtxts = jlayer["t"]["d"]["k"];
   *         if (!jtxts) {
   *             return;
   *         }
   *
   *         for (const auto& jtxt : *jtxts) {
   *             const Value& jtxt_val = jtxt["s"];
   *
   *             const StringValue* jfont_name = jtxt_val["f"];
   *             skottie::TextValue txt_val;
   *             if (!skottie::internal::Parse(jtxt_val, fBuilder , &txt_val) || !jfont_name) {
   *                 continue;
   *             }
   *
   *             const std::string_view font_name(jfont_name->begin(), jfont_name->size());
   *
   *             static constexpr float kMinSize =    0.1f,
   *                                    kMaxSize = 1296.0f;
   *             const skottie::Shaper::TextDesc text_desc = {
   *                 txt_val.fTypeface,
   *                 SkTPin(txt_val.fTextSize,    kMinSize, kMaxSize),
   *                 SkTPin(txt_val.fMinTextSize, kMinSize, kMaxSize),
   *                 SkTPin(txt_val.fMaxTextSize, kMinSize, kMaxSize),
   *                 txt_val.fLineHeight,
   *                 txt_val.fLineShift,
   *                 txt_val.fAscent,
   *                 txt_val.fHAlign,
   *                 txt_val.fVAlign,
   *                 txt_val.fResize,
   *                 txt_val.fLineBreak,
   *                 txt_val.fDirection,
   *                 txt_val.fCapitalization,
   *                 txt_val.fMaxLines,
   *                 skottie::Shaper::Flags::kFragmentGlyphs |
   *                     skottie::Shaper::Flags::kTrackFragmentAdvanceAscent |
   *                     skottie::Shaper::Flags::kClusters,
   *                 txt_val.fLocale.isEmpty()     ? nullptr : txt_val.fLocale.c_str(),
   *                 txt_val.fFontFamily.isEmpty() ? nullptr : txt_val.fFontFamily.c_str(),
   *             };
   *
   *             auto shape_result = skottie::Shaper::Shape(txt_val.fText, text_desc, txt_val.fBox,
   *                                                        fFontMgr, fShapersFact);
   *
   *             auto shaped_glyph_info = [this](SkUnichar ch, const SkPoint& pos, float advance,
   *                                             size_t line, size_t cluster) -> Value {
   *                 const NumberValue jpos[] = { NumberValue(pos.fX), NumberValue(pos.fY) };
   *                 char utf8[SkUTF::kMaxBytesInUTF8Sequence];
   *                 const size_t utf8_len = SkUTF::ToUTF8(ch, utf8);
   *
   *                 const skjson::Member fields[] = {
   *                     { StringValue("ch" , fAlloc), StringValue(utf8, utf8_len, fAlloc)       },
   *                     { StringValue("ps" , fAlloc), ArrayValue(jpos, std::size(jpos), fAlloc) },
   *                     { StringValue("w"  , fAlloc), NumberValue(advance)                      },
   *                     { StringValue("l"  , fAlloc), NumberValue(SkToInt(line))                },
   *                     { StringValue("cix", fAlloc), NumberValue(SkToInt(cluster))             },
   *                 };
   *
   *                 return ObjectValue(fields, std::size(fields), fAlloc);
   *             };
   *
   *             std::vector<Value> shaped_info;
   *             for (const auto& frag : shape_result.fFragments) {
   *                 SkASSERT(frag.fGlyphs.fGlyphIDs.size() == 1);
   *                 SkASSERT(frag.fGlyphs.fClusters.size() == frag.fGlyphs.fGlyphIDs.size());
   *                 size_t offset = 0;
   *                 for (const auto& runrec : frag.fGlyphs.fRuns) {
   *                     const SkGlyphID*  glyphs = frag.fGlyphs.fGlyphIDs.data() + offset;
   *                     const SkPoint* glyph_pos = frag.fGlyphs.fGlyphPos.data() + offset;
   *                     const size_t*   clusters = frag.fGlyphs.fClusters.data() + offset;
   *                     const char*     end_utf8 = txt_val.fText.c_str() + txt_val.fText.size();
   *                     for (size_t i = 0; i < runrec.fSize; ++i) {
   *                         // TODO: we are only considering the fist code point in the cluster,
   *                         // similar to how Lottie handles custom/path-based fonts at the moment.
   *                         // To correctly handle larger clusters, we'll have to check for collisions
   *                         // and potentially allocate a synthetic glyph IDs.  TBD.
   *                         const char* ch_utf8 = txt_val.fText.c_str() + clusters[i];
   *                         const SkUnichar ch = SkUTF::NextUTF8(&ch_utf8, end_utf8);
   *
   *                         fGlyphCache.addGlyph(font_name, ch, runrec.fFont, glyphs[i]);
   *                         shaped_info.push_back(shaped_glyph_info(ch,
   *                                                                 frag.fOrigin + glyph_pos[i],
   *                                                                 frag.fAdvance,
   *                                                                 frag.fLineIndex,
   *                                                                 clusters[i]));
   *                     }
   *                     offset += runrec.fSize;
   *                 }
   *             }
   *
   *             // Preshaped glyphs.
   *             jtxt_val.as<ObjectValue>().writable("gl", fAlloc) =
   *                 ArrayValue(shaped_info.data(), shaped_info.size(), fAlloc);
   *             // Effecive size for preshaped glyphs, accounting for auto-sizing scale.
   *             jtxt_val.as<ObjectValue>().writable("gs", fAlloc) =
   *                 NumberValue(text_desc.fTextSize * shape_result.fScale);
   *             // Updated font name.
   *             jtxt_val.as<ObjectValue>().writable("f", fAlloc) =
   *                 StringValue(preshapedFontName(font_name).c_str(), fAlloc);
   *         }
   *     }
   * ```
   */
  private fun preshapeLayer(jlayer: Value) {
    TODO("Implement preshapeLayer")
  }

  public class NullResourceProvider : ResourceProvider() {
    public override fun load(param0: CharArray, param1: CharArray): SkSp<SkData> {
      TODO("Implement load")
    }
  }
}
