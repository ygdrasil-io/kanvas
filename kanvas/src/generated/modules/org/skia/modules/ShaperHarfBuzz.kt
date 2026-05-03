package org.skia.modules

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.ULong
import org.skia.`external`.Feature
import org.skia.`external`.HbLanguageT
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontMgr
import org.skia.foundation.SkSp
import org.skia.math.SkScalar
import undefined.BiDiRunIterator
import undefined.FontRunIterator
import undefined.HBBuffer
import undefined.LanguageRunIterator

/**
 * C++ original:
 * ```cpp
 * class ShaperHarfBuzz : public SkShaper {
 * public:
 *     ShaperHarfBuzz(sk_sp<SkUnicode>,
 *                    HBBuffer,
 *                    sk_sp<SkFontMgr>);
 *
 * protected:
 *     sk_sp<SkUnicode> fUnicode;
 *
 *     ShapedRun shape(const char* utf8, size_t utf8Bytes,
 *                     const char* utf8Start,
 *                     const char* utf8End,
 *                     const BiDiRunIterator&,
 *                     const LanguageRunIterator&,
 *                     const ScriptRunIterator&,
 *                     const FontRunIterator&,
 *                     const Feature*, size_t featuresSize) const;
 * private:
 *     const sk_sp<SkFontMgr> fFontMgr; // for fallback
 *     HBBuffer               fBuffer;
 *     hb_language_t          fUndefinedLanguage;
 *
 * #if !defined(SK_DISABLE_LEGACY_SKSHAPER_FUNCTIONS)
 *     void shape(const char* utf8, size_t utf8Bytes,
 *                const SkFont&,
 *                bool leftToRight,
 *                SkScalar width,
 *                RunHandler*) const override;
 *
 *     void shape(const char* utf8Text, size_t textBytes,
 *                FontRunIterator&,
 *                BiDiRunIterator&,
 *                ScriptRunIterator&,
 *                LanguageRunIterator&,
 *                SkScalar width,
 *                RunHandler*) const override;
 * #endif
 *
 *     void shape(const char* utf8Text, size_t textBytes,
 *                FontRunIterator&,
 *                BiDiRunIterator&,
 *                ScriptRunIterator&,
 *                LanguageRunIterator&,
 *                const Feature*, size_t featuresSize,
 *                SkScalar width,
 *                RunHandler*) const override;
 *
 *     virtual void wrap(char const * utf8, size_t utf8Bytes,
 *                       const BiDiRunIterator&,
 *                       const LanguageRunIterator&,
 *                       const ScriptRunIterator&,
 *                       const FontRunIterator&,
 *                       RunIteratorQueue& runSegmenter,
 *                       const Feature*, size_t featuresSize,
 *                       SkScalar width,
 *                       RunHandler*) const = 0;
 * }
 * ```
 */
public abstract class ShaperHarfBuzz public constructor(
  unicode: SkSp<SkUnicode>,
  buffer: HBBuffer,
  fallback: SkSp<SkFontMgr>,
) : SkShaper() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkUnicode> fUnicode
   * ```
   */
  protected var fUnicode: SkSp<SkUnicode> = TODO("Initialize fUnicode")

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<SkFontMgr> fFontMgr
   * ```
   */
  private val fFontMgr: SkSp<SkFontMgr> = TODO("Initialize fFontMgr")

  /**
   * C++ original:
   * ```cpp
   * HBBuffer               fBuffer
   * ```
   */
  private var fBuffer: Int = TODO("Initialize fBuffer")

  /**
   * C++ original:
   * ```cpp
   * hb_language_t          fUndefinedLanguage
   * ```
   */
  private var fUndefinedLanguage: HbLanguageT = TODO("Initialize fUndefinedLanguage")

  /**
   * C++ original:
   * ```cpp
   * ShapedRun shape(const char* utf8, size_t utf8Bytes,
   *                     const char* utf8Start,
   *                     const char* utf8End,
   *                     const BiDiRunIterator&,
   *                     const LanguageRunIterator&,
   *                     const ScriptRunIterator&,
   *                     const FontRunIterator&,
   *                     const Feature*, size_t featuresSize) const
   * ```
   */
  protected fun shape(
    utf8: String?,
    utf8Bytes: ULong,
    utf8Start: String?,
    utf8End: String?,
    param4: BiDiRunIterator,
    param5: LanguageRunIterator,
    param6: ScriptRunIterator,
    param7: FontRunIterator,
    param8: Feature?,
    featuresSize: ULong,
  ): ShapedRun {
    TODO("Implement shape")
  }

  /**
   * C++ original:
   * ```cpp
   * void ShaperHarfBuzz::shape(const char* utf8,
   *                            size_t utf8Bytes,
   *                            const SkFont& srcFont,
   *                            bool leftToRight,
   *                            SkScalar width,
   *                            RunHandler* handler) const {
   *     SkBidiIterator::Level defaultLevel = leftToRight ? SkBidiIterator::kLTR : SkBidiIterator::kRTL;
   *     std::unique_ptr<BiDiRunIterator> bidi(
   *             SkShapers::unicode::BidiRunIterator(fUnicode, utf8, utf8Bytes, defaultLevel));
   *
   *     if (!bidi) {
   *         return;
   *     }
   *
   *     std::unique_ptr<LanguageRunIterator> language(MakeStdLanguageRunIterator(utf8, utf8Bytes));
   *     if (!language) {
   *         return;
   *     }
   *
   *     std::unique_ptr<ScriptRunIterator> script(SkShapers::HB::ScriptRunIterator(utf8, utf8Bytes));
   *     if (!script) {
   *         return;
   *     }
   *
   *     std::unique_ptr<FontRunIterator> font(
   *                 MakeFontMgrRunIterator(utf8, utf8Bytes, srcFont, fFontMgr));
   *     if (!font) {
   *         return;
   *     }
   *
   *     this->shape(utf8, utf8Bytes, *font, *bidi, *script, *language, width, handler);
   * }
   * ```
   */
  public override fun shape(
    utf8: String?,
    utf8Bytes: ULong,
    srcFont: SkFont,
    leftToRight: Boolean,
    width: SkScalar,
    handler: RunHandler?,
  ) {
    TODO("Implement shape")
  }

  /**
   * C++ original:
   * ```cpp
   * void ShaperHarfBuzz::shape(const char* utf8,
   *                            size_t utf8Bytes,
   *                            FontRunIterator& font,
   *                            BiDiRunIterator& bidi,
   *                            ScriptRunIterator& script,
   *                            LanguageRunIterator& language,
   *                            SkScalar width,
   *                            RunHandler* handler) const {
   *     this->shape(utf8, utf8Bytes, font, bidi, script, language, nullptr, 0, width, handler);
   * }
   * ```
   */
  public override fun shape(
    utf8Text: String?,
    textBytes: ULong,
    font: FontRunIterator,
    bidi: BiDiRunIterator,
    script: ScriptRunIterator,
    language: LanguageRunIterator,
    width: SkScalar,
    handler: RunHandler?,
  ) {
    TODO("Implement shape")
  }

  /**
   * C++ original:
   * ```cpp
   * void ShaperHarfBuzz::shape(const char* utf8,
   *                            size_t utf8Bytes,
   *                            FontRunIterator& font,
   *                            BiDiRunIterator& bidi,
   *                            ScriptRunIterator& script,
   *                            LanguageRunIterator& language,
   *                            const Feature* features,
   *                            size_t featuresSize,
   *                            SkScalar width,
   *                            RunHandler* handler) const {
   *     SkASSERT(handler);
   *     RunIteratorQueue runSegmenter;
   *     runSegmenter.insert(&font,     3); // The font iterator is always run last in case of tie.
   *     runSegmenter.insert(&bidi,     2);
   *     runSegmenter.insert(&script,   1);
   *     runSegmenter.insert(&language, 0);
   *
   *     this->wrap(utf8, utf8Bytes, bidi, language, script, font, runSegmenter,
   *                features, featuresSize, width, handler);
   * }
   * ```
   */
  public override fun shape(
    utf8Text: String?,
    textBytes: ULong,
    font: FontRunIterator,
    bidi: BiDiRunIterator,
    script: ScriptRunIterator,
    language: LanguageRunIterator,
    features: Feature?,
    featuresSize: ULong,
    width: SkScalar,
    handler: RunHandler?,
  ) {
    TODO("Implement shape")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void wrap(char const * utf8, size_t utf8Bytes,
   *                       const BiDiRunIterator&,
   *                       const LanguageRunIterator&,
   *                       const ScriptRunIterator&,
   *                       const FontRunIterator&,
   *                       RunIteratorQueue& runSegmenter,
   *                       const Feature*, size_t featuresSize,
   *                       SkScalar width,
   *                       RunHandler*) const = 0
   * ```
   */
  private abstract fun wrap(
    utf8: String?,
    utf8Bytes: ULong,
    param2: BiDiRunIterator,
    param3: LanguageRunIterator,
    param4: ScriptRunIterator,
    param5: FontRunIterator,
    runSegmenter: RunIteratorQueue,
    param7: Feature?,
    featuresSize: ULong,
    width: SkScalar,
    param10: RunHandler?,
  )

  /**
   * C++ original:
   * ```cpp
   * ShapedRun ShaperHarfBuzz::shape(char const * const utf8,
   *                                   size_t const utf8Bytes,
   *                                   char const * const utf8Start,
   *                                   char const * const utf8End,
   *                                   const BiDiRunIterator& bidi,
   *                                   const LanguageRunIterator& language,
   *                                   const ScriptRunIterator& script,
   *                                   const FontRunIterator& font,
   *                                   Feature const * const features, size_t const featuresSize) const
   * {
   *     size_t utf8runLength = utf8End - utf8Start;
   *     ShapedRun run(RunHandler::Range(utf8Start - utf8, utf8runLength),
   *                   font.currentFont(), bidi.currentLevel(),
   *                   script.currentScript(), language.currentLanguage(),
   *                   nullptr, 0);
   *
   *     hb_buffer_t* buffer = fBuffer.get();
   *     SkAutoTCallVProc<hb_buffer_t, hb_buffer_clear_contents> autoClearBuffer(buffer);
   *     hb_buffer_set_content_type(buffer, HB_BUFFER_CONTENT_TYPE_UNICODE);
   *     hb_buffer_set_cluster_level(buffer, HB_BUFFER_CLUSTER_LEVEL_MONOTONE_CHARACTERS);
   *
   *     // Documentation for HB_BUFFER_FLAG_BOT/EOT at 763e5466c0a03a7c27020e1e2598e488612529a7.
   *     // Currently BOT forces a dotted circle when first codepoint is a mark; EOT has no effect.
   *     // Avoid adding dotted circle, re-evaluate if BOT/EOT change. See https://skbug.com/40040947.
   *     // hb_buffer_set_flags(buffer, HB_BUFFER_FLAG_BOT | HB_BUFFER_FLAG_EOT);
   *
   *     // Add precontext.
   *     hb_buffer_add_utf8(buffer, utf8, utf8Start - utf8, utf8Start - utf8, 0);
   *
   *     // Populate the hb_buffer directly with utf8 cluster indexes.
   *     const char* utf8Current = utf8Start;
   *     while (utf8Current < utf8End) {
   *         unsigned int cluster = utf8Current - utf8;
   *         hb_codepoint_t u = utf8_next(&utf8Current, utf8End);
   *         hb_buffer_add(buffer, u, cluster);
   *     }
   *
   *     // Add postcontext.
   *     hb_buffer_add_utf8(buffer, utf8Current, utf8 + utf8Bytes - utf8Current, 0, 0);
   *
   *     hb_direction_t direction = is_LTR(bidi.currentLevel()) ? HB_DIRECTION_LTR:HB_DIRECTION_RTL;
   *     hb_buffer_set_direction(buffer, direction);
   *     hb_buffer_set_script(buffer, hb_script_from_iso15924_tag((hb_tag_t)script.currentScript()));
   *     // Buffers with HB_LANGUAGE_INVALID race since hb_language_get_default is not thread safe.
   *     // The user must provide a language, but may provide data hb_language_from_string cannot use.
   *     // Use "und" for the undefined language in this case (RFC5646 4.1 5).
   *     hb_language_t hbLanguage = hb_language_from_string(language.currentLanguage(), -1);
   *     if (hbLanguage == HB_LANGUAGE_INVALID) {
   *         hbLanguage = fUndefinedLanguage;
   *     }
   *     hb_buffer_set_language(buffer, hbLanguage);
   *     hb_buffer_guess_segment_properties(buffer);
   *
   *     // TODO: better cache HBFace (data) / hbfont (typeface)
   *     // An HBFace is expensive (it sanitizes the bits).
   *     // An HBFont is fairly inexpensive.
   *     // An HBFace is actually tied to the data, not the typeface.
   *     // The size of 100 here is completely arbitrary and used to match libtxt.
   *     HBFont hbFont;
   *     {
   *         HBLockedFaceCache cache = get_hbFace_cache();
   *         SkTypefaceID dataId = font.currentFont().getTypeface()->uniqueID();
   *         HBFont* typefaceFontCached = cache.find(dataId);
   *         if (!typefaceFontCached) {
   *             HBFont typefaceFont(create_typeface_hb_font(*font.currentFont().getTypeface()));
   *             typefaceFontCached = cache.insert(dataId, std::move(typefaceFont));
   *         }
   *         hbFont = create_sub_hb_font(font.currentFont(), *typefaceFontCached);
   *     }
   *     if (!hbFont) {
   *         return run;
   *     }
   *
   *     STArray<32, hb_feature_t> hbFeatures;
   *     for (const auto& feature : SkSpan(features, featuresSize)) {
   *         if (feature.end < SkTo<size_t>(utf8Start - utf8) ||
   *                           SkTo<size_t>(utf8End   - utf8)  <= feature.start)
   *         {
   *             continue;
   *         }
   *         if (feature.start <= SkTo<size_t>(utf8Start - utf8) &&
   *                              SkTo<size_t>(utf8End   - utf8) <= feature.end)
   *         {
   *             hbFeatures.push_back({ (hb_tag_t)feature.tag, feature.value,
   *                                    HB_FEATURE_GLOBAL_START, HB_FEATURE_GLOBAL_END});
   *         } else {
   *             hbFeatures.push_back({ (hb_tag_t)feature.tag, feature.value,
   *                                    SkTo<unsigned>(feature.start), SkTo<unsigned>(feature.end)});
   *         }
   *     }
   *
   *     hb_shape(hbFont.get(), buffer, hbFeatures.data(), hbFeatures.size());
   *     unsigned len = hb_buffer_get_length(buffer);
   *     if (len == 0) {
   *         return run;
   *     }
   *
   *     if (direction == HB_DIRECTION_RTL) {
   *         // Put the clusters back in logical order.
   *         // Note that the advances remain ltr.
   *         hb_buffer_reverse(buffer);
   *     }
   *     hb_glyph_info_t* info = hb_buffer_get_glyph_infos(buffer, nullptr);
   *     hb_glyph_position_t* pos = hb_buffer_get_glyph_positions(buffer, nullptr);
   *
   *     run = ShapedRun(RunHandler::Range(utf8Start - utf8, utf8runLength),
   *                     font.currentFont(), bidi.currentLevel(),
   *                     script.currentScript(), language.currentLanguage(),
   *                     std::unique_ptr<ShapedGlyph[]>(new ShapedGlyph[len]), len);
   *
   *     // Undo skhb_position with (1.0/(1<<16)) and scale as needed.
   *     AutoSTArray<32, SkGlyphID> glyphIDs(len);
   *     for (unsigned i = 0; i < len; i++) {
   *         glyphIDs[i] = info[i].codepoint;
   *     }
   *     AutoSTArray<32, SkRect> glyphBounds(len);
   *     SkPaint p;
   *     run.fFont.getBounds(glyphIDs, glyphBounds, &p);
   *
   *     double SkScalarFromHBPosX = +(1.52587890625e-5) * run.fFont.getScaleX();
   *     double SkScalarFromHBPosY = -(1.52587890625e-5);  // HarfBuzz y-up, Skia y-down
   *     SkVector runAdvance = { 0, 0 };
   *     for (unsigned i = 0; i < len; i++) {
   *         ShapedGlyph& glyph = run.fGlyphs[i];
   *         glyph.fID = info[i].codepoint;
   *         glyph.fCluster = info[i].cluster;
   *         glyph.fOffset.fX = pos[i].x_offset * SkScalarFromHBPosX;
   *         glyph.fOffset.fY = pos[i].y_offset * SkScalarFromHBPosY;
   *         glyph.fAdvance.fX = pos[i].x_advance * SkScalarFromHBPosX;
   *         glyph.fAdvance.fY = pos[i].y_advance * SkScalarFromHBPosY;
   *
   *         glyph.fHasVisual = !glyphBounds[i].isEmpty(); //!font->currentTypeface()->glyphBoundsAreZero(glyph.fID);
   * #if SK_HB_VERSION_CHECK(1, 5, 0)
   *         glyph.fUnsafeToBreak = info[i].mask & HB_GLYPH_FLAG_UNSAFE_TO_BREAK;
   * #else
   *         glyph.fUnsafeToBreak = false;
   * #endif
   *         glyph.fMustLineBreakBefore = false;
   *
   *         runAdvance += glyph.fAdvance;
   *     }
   *     run.fAdvance = runAdvance;
   *
   *     return run;
   * }
   * ```
   */
  public fun shape(
    utf8: String?,
    utf8Bytes: ULong,
    utf8Start: String?,
    utf8End: String?,
    bidi: BiDiRunIterator,
    language: LanguageRunIterator,
    script: ScriptRunIterator,
    font: FontRunIterator,
    features: Any?,
    featuresSize: ULong,
  ): ShapedRun {
    TODO("Implement shape")
  }
}
