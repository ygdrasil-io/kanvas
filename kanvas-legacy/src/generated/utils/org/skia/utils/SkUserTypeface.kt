package org.skia.utils

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.ULong
import kotlin.Unit
import kotlin.collections.List
import org.skia.core.SkFontMetrics
import org.skia.foundation.SkDescriptor
import org.skia.foundation.SkFontArguments
import org.skia.foundation.SkFontDescriptor
import org.skia.foundation.SkFontParameters
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkFontTableTag
import org.skia.foundation.SkGlyphID
import org.skia.foundation.SkScalerContextEffects
import org.skia.foundation.SkScalerContextRec
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.foundation.SkTypeface
import org.skia.foundation.SkUnichar
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SkUserTypeface final : public SkTypeface {
 * private:
 *     friend class SkCustomTypefaceBuilder;
 *     friend class SkUserScalerContext;
 *
 *     explicit SkUserTypeface(SkFontStyle style, const SkFontMetrics& metrics,
 *                             std::vector<SkCustomTypefaceBuilder::GlyphRec>&& recs)
 *         : SkTypeface(style)
 *         , fGlyphRecs(std::move(recs))
 *         , fMetrics(metrics)
 *     {}
 *
 *     const std::vector<SkCustomTypefaceBuilder::GlyphRec> fGlyphRecs;
 *     const SkFontMetrics                                  fMetrics;
 *
 *     std::unique_ptr<SkScalerContext> onCreateScalerContext(const SkScalerContextEffects&,
 *                                                            const SkDescriptor* desc) const override;
 *     void onFilterRec(SkScalerContextRec* rec) const override;
 *     void getGlyphToUnicodeMap(SkSpan<SkUnichar>) const override;
 *     std::unique_ptr<SkAdvancedTypefaceMetrics> onGetAdvancedMetrics() const override;
 *
 *     void onGetFontDescriptor(SkFontDescriptor* desc, bool* isLocal) const override;
 *
 *     void onCharsToGlyphs(SkSpan<const SkUnichar>, SkSpan<SkGlyphID>) const override;
 *
 *     void onGetFamilyName(SkString* familyName) const override;
 *     bool onGetPostScriptName(SkString*) const override;
 *     SkTypeface::LocalizedStrings* onCreateFamilyNameIterator() const override;
 *
 *     std::unique_ptr<SkStreamAsset> onOpenStream(int*) const override;
 *
 *     // trivial
 *
 *     std::unique_ptr<SkStreamAsset> onOpenExistingStream(int*) const override { return nullptr; }
 *
 *     sk_sp<SkTypeface> onMakeClone(const SkFontArguments& args) const override {
 *         return sk_ref_sp(this);
 *     }
 *     int onCountGlyphs() const override { return this->glyphCount(); }
 *     int onGetUPEM() const override { return 2048; /* ?? */ }
 *     bool onComputeBounds(SkRect* bounds) const override {
 *         bounds->setLTRB(fMetrics.fXMin, fMetrics.fTop, fMetrics.fXMax, fMetrics.fBottom);
 *         return true;
 *     }
 *
 *     // noops
 *
 *     void getPostScriptGlyphNames(SkString*) const override {}
 *     bool onGlyphMaskNeedsCurrentColor() const override { return false; }
 *     int onGetVariationDesignPosition(
 *              SkSpan<SkFontArguments::VariationPosition::Coordinate>) const override { return 0; }
 *     int onGetVariationDesignParameters(SkSpan<SkFontParameters::Variation::Axis>) const override {
 *         return 0;
 *     }
 *     int onGetTableTags(SkSpan<SkFontTableTag>) const override { return 0; }
 *     size_t onGetTableData(SkFontTableTag, size_t, size_t, void*) const override { return 0; }
 *
 *     int glyphCount() const {
 *         return SkToInt(fGlyphRecs.size());
 *     }
 * }
 * ```
 */
public class SkUserTypeface public constructor(
  style: SkFontStyle,
  metrics: SkFontMetrics,
  recs: List<SkCustomTypefaceBuilder.GlyphRec>,
) : SkTypeface(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * const std::vector<SkCustomTypefaceBuilder::GlyphRec> fGlyphRecs
   * ```
   */
  private val fGlyphRecs: Int = TODO("Initialize fGlyphRecs")

  /**
   * C++ original:
   * ```cpp
   * const SkFontMetrics                                  fMetrics
   * ```
   */
  private val fMetrics: SkFontMetrics = TODO("Initialize fMetrics")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkScalerContext> SkUserTypeface::onCreateScalerContext(
   *     const SkScalerContextEffects& effects, const SkDescriptor* desc) const
   * {
   *     return std::make_unique<SkUserScalerContext>(*const_cast<SkUserTypeface*>(this), effects, desc);
   * }
   * ```
   */
  public override fun onCreateScalerContext(effects: SkScalerContextEffects, desc: SkDescriptor?): Int {
    TODO("Implement onCreateScalerContext")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkUserTypeface::onFilterRec(SkScalerContextRec* rec) const {
   *     rec->useStrokeForFakeBold();
   *     rec->setHinting(SkFontHinting::kNone);
   * }
   * ```
   */
  public override fun onFilterRec(rec: SkScalerContextRec?) {
    TODO("Implement onFilterRec")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkUserTypeface::getGlyphToUnicodeMap(SkSpan<SkUnichar> glyphToUnicode) const {
   *     const int n = std::min(this->glyphCount(), (int)glyphToUnicode.size());
   *     for (int gid = 0; gid < n; ++gid) {
   *         glyphToUnicode[gid] = SkTo<SkUnichar>(gid);
   *     }
   * }
   * ```
   */
  public override fun getGlyphToUnicodeMap(glyphToUnicode: SkSpan<SkUnichar>) {
    TODO("Implement getGlyphToUnicodeMap")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkAdvancedTypefaceMetrics> SkUserTypeface::onGetAdvancedMetrics() const {
   *     return nullptr;
   * }
   * ```
   */
  public override fun onGetAdvancedMetrics(): Int {
    TODO("Implement onGetAdvancedMetrics")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkUserTypeface::onGetFontDescriptor(SkFontDescriptor* desc, bool* isLocal) const {
   *     desc->setFactoryId(SkCustomTypefaceBuilder::FactoryId);
   *     *isLocal = true;
   * }
   * ```
   */
  public override fun onGetFontDescriptor(desc: SkFontDescriptor?, isLocal: Boolean?) {
    TODO("Implement onGetFontDescriptor")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkUserTypeface::onCharsToGlyphs(SkSpan<const SkUnichar> chars,
   *                                      SkSpan<SkGlyphID> glyphs) const {
   *     SkASSERT(chars.size() == glyphs.size());
   *     const int glyphCount = this->glyphCount();
   *     for (size_t i = 0; i < chars.size(); ++i) {
   *         glyphs[i] = chars[i] < glyphCount ? SkTo<SkGlyphID>(chars[i]) : 0;
   *     }
   * }
   * ```
   */
  public override fun onCharsToGlyphs(chars: SkSpan<SkUnichar>, glyphs: SkSpan<SkGlyphID>) {
    TODO("Implement onCharsToGlyphs")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkUserTypeface::onGetFamilyName(SkString* familyName) const {
   *     *familyName = "";
   * }
   * ```
   */
  public override fun onGetFamilyName(familyName: String?) {
    TODO("Implement onGetFamilyName")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkUserTypeface::onGetPostScriptName(SkString*) const {
   *     return false;
   * }
   * ```
   */
  public override fun onGetPostScriptName(param0: String?): Boolean {
    TODO("Implement onGetPostScriptName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTypeface::LocalizedStrings* SkUserTypeface::onCreateFamilyNameIterator() const {
   *     return nullptr;
   * }
   * ```
   */
  public override fun onCreateFamilyNameIterator(): SkTypeface.LocalizedStrings {
    TODO("Implement onCreateFamilyNameIterator")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkStreamAsset> SkUserTypeface::onOpenStream(int* ttcIndex) const {
   *     SkDynamicMemoryWStream wstream;
   *
   *     wstream.write(gHeaderString, kHeaderSize);
   *
   *     wstream.write(&fMetrics, sizeof(fMetrics));
   *
   *     SkFontStyle style = this->fontStyle();
   *     wstream.write(&style, sizeof(style));
   *
   *     wstream.write32(this->glyphCount());
   *
   *     for (const auto& rec : fGlyphRecs) {
   *         wstream.write32(rec.isDrawable() ? GlyphType::kDrawable : GlyphType::kPath);
   *
   *         wstream.writeScalar(rec.fAdvance);
   *
   *         wstream.write(&rec.fBounds, sizeof(rec.fBounds));
   *
   *         auto data = rec.isDrawable()
   *                 ? rec.fDrawable->serialize()
   *                 : rec.fPath.serialize();
   *
   *         const size_t sz = data->size();
   *         SkASSERT(SkIsAlign4(sz));
   *         wstream.write(&sz, sizeof(sz));
   *         wstream.write(data->data(), sz);
   *     }
   *
   *     *ttcIndex = 0;
   *     return wstream.detachAsStream();
   * }
   * ```
   */
  public override fun onOpenStream(ttcIndex: Int?): Int {
    TODO("Implement onOpenStream")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkStreamAsset> onOpenExistingStream(int*) const override { return nullptr; }
   * ```
   */
  public override fun onOpenExistingStream(param0: Int?): Int {
    TODO("Implement onOpenExistingStream")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> onMakeClone(const SkFontArguments& args) const override {
   *         return sk_ref_sp(this);
   *     }
   * ```
   */
  public override fun onMakeClone(args: SkFontArguments): SkSp<SkTypeface> {
    TODO("Implement onMakeClone")
  }

  /**
   * C++ original:
   * ```cpp
   * int onCountGlyphs() const override { return this->glyphCount(); }
   * ```
   */
  public override fun onCountGlyphs(): Int {
    TODO("Implement onCountGlyphs")
  }

  /**
   * C++ original:
   * ```cpp
   * int onGetUPEM() const override { return 2048; /* ?? */ }
   * ```
   */
  public override fun onGetUPEM(): Int {
    TODO("Implement onGetUPEM")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onComputeBounds(SkRect* bounds) const override {
   *         bounds->setLTRB(fMetrics.fXMin, fMetrics.fTop, fMetrics.fXMax, fMetrics.fBottom);
   *         return true;
   *     }
   * ```
   */
  public override fun onComputeBounds(bounds: SkRect?): Boolean {
    TODO("Implement onComputeBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void getPostScriptGlyphNames(SkString*) const override {}
   * ```
   */
  public override fun getPostScriptGlyphNames(param0: String?) {
    TODO("Implement getPostScriptGlyphNames")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onGlyphMaskNeedsCurrentColor() const override { return false; }
   * ```
   */
  public override fun onGlyphMaskNeedsCurrentColor(): Boolean {
    TODO("Implement onGlyphMaskNeedsCurrentColor")
  }

  /**
   * C++ original:
   * ```cpp
   * int onGetVariationDesignPosition(
   *              SkSpan<SkFontArguments::VariationPosition::Coordinate>) const override { return 0; }
   * ```
   */
  public override fun onGetVariationDesignPosition(param0: SkSpan<SkFontArguments.VariationPosition.Coordinate>): Int {
    TODO("Implement onGetVariationDesignPosition")
  }

  /**
   * C++ original:
   * ```cpp
   * int onGetVariationDesignParameters(SkSpan<SkFontParameters::Variation::Axis>) const override {
   *         return 0;
   *     }
   * ```
   */
  public override fun onGetVariationDesignParameters(param0: SkSpan<SkFontParameters.Variation.Axis>): Int {
    TODO("Implement onGetVariationDesignParameters")
  }

  /**
   * C++ original:
   * ```cpp
   * int onGetTableTags(SkSpan<SkFontTableTag>) const override { return 0; }
   * ```
   */
  public override fun onGetTableTags(param0: SkSpan<SkFontTableTag>): Int {
    TODO("Implement onGetTableTags")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t onGetTableData(SkFontTableTag, size_t, size_t, void*) const override { return 0; }
   * ```
   */
  public override fun onGetTableData(
    param0: SkFontTableTag,
    param1: ULong,
    param2: ULong,
    param3: Unit?,
  ): ULong {
    TODO("Implement onGetTableData")
  }

  /**
   * C++ original:
   * ```cpp
   * int glyphCount() const {
   *         return SkToInt(fGlyphRecs.size());
   *     }
   * ```
   */
  private fun glyphCount(): Int {
    TODO("Implement glyphCount")
  }
}
