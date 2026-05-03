package org.skia.tools

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.ULong
import kotlin.Unit
import org.skia.core.SkFontMetrics
import org.skia.foundation.SkDescriptor
import org.skia.foundation.SkFontArguments
import org.skia.foundation.SkFontDescriptor
import org.skia.foundation.SkFontParameters
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkFontTableTag
import org.skia.foundation.SkGlyphID
import org.skia.foundation.SkPath
import org.skia.foundation.SkScalerContextEffects
import org.skia.foundation.SkScalerContextRec
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.foundation.SkStreamAsset
import org.skia.foundation.SkTypeface
import org.skia.foundation.SkUnichar
import org.skia.math.SkVector

/**
 * C++ original:
 * ```cpp
 * class TestTypeface : public SkTypeface {
 * public:
 *     struct List {
 *         struct Family {
 *             struct Face {
 *                 sk_sp<SkTypeface> typeface;
 *                 const char* name;
 *                 bool isDefault;
 *             };
 *             std::vector<Face> faces;
 *             const char* name;
 *         };
 *         std::vector<Family> families;
 *     };
 *     static const List& Typefaces();
 *
 *     SkVector getAdvance(SkGlyphID) const;
 *     void getFontMetrics(SkFontMetrics* metrics);
 *     SkPath getPath(SkGlyphID glyph);
 *
 *     struct Register { Register(); };
 * protected:
 *     std::unique_ptr<SkScalerContext> onCreateScalerContext(const SkScalerContextEffects&,
 *                                                            const SkDescriptor* desc) const override;
 *     void onFilterRec(SkScalerContextRec* rec) const override;
 *     void getGlyphToUnicodeMap(SkSpan<SkUnichar>) const override;
 *     std::unique_ptr<SkAdvancedTypefaceMetrics> onGetAdvancedMetrics() const override;
 *
 *     std::unique_ptr<SkStreamAsset> onOpenStream(int* ttcIndex) const override;
 *
 *     sk_sp<SkTypeface> onMakeClone(const SkFontArguments& args) const override {
 *         return sk_ref_sp(this);
 *     }
 *
 *     void onGetFontDescriptor(SkFontDescriptor* desc, bool* serialize) const override;
 *
 *     void onCharsToGlyphs(SkSpan<const SkUnichar>, SkSpan<SkGlyphID>) const override;
 *
 *     int onCountGlyphs() const override { return (int)fTestFont->fCharCodesCount; }
 *
 *     void getPostScriptGlyphNames(SkString*) const override {}
 *
 *     int onGetUPEM() const override { return 2048; }
 *
 *     void onGetFamilyName(SkString* familyName) const override;
 *     bool onGetPostScriptName(SkString*) const override;
 *     SkTypeface::LocalizedStrings* onCreateFamilyNameIterator() const override;
 *
 *     bool onGlyphMaskNeedsCurrentColor() const override { return false; }
 *
 *     int onGetVariationDesignPosition(
 *                          SkSpan<SkFontArguments::VariationPosition::Coordinate>) const override {
 *         return 0;
 *     }
 *
 *     int onGetVariationDesignParameters(SkSpan<SkFontParameters::Variation::Axis>) const override {
 *         return 0;
 *     }
 *
 *     int onGetTableTags(SkSpan<SkFontTableTag>) const override { return 0; }
 *
 *     size_t onGetTableData(SkFontTableTag tag,
 *                           size_t         offset,
 *                           size_t         length,
 *                           void*          data) const override {
 *         return 0;
 *     }
 *
 * private:
 *     static constexpr SkTypeface::FactoryId FactoryId = SkSetFourByteTag('t','e','s','t');
 *     static sk_sp<SkTypeface> MakeFromStream(std::unique_ptr<SkStreamAsset>, const SkFontArguments&);
 *     TestTypeface(sk_sp<SkTestFont>, const SkFontStyle& style);
 *     sk_sp<SkTestFont> fTestFont;
 *     friend class SkTestScalerContext;
 * }
 * ```
 */
public open class TestTypeface public constructor(
  testFont: SkSp<SkTestFont>,
  style: SkFontStyle,
) : SkTypeface(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * static constexpr SkTypeface::FactoryId FactoryId
   * ```
   */
  private var fTestFont: SkSp<SkTestFont> = TODO("Initialize fTestFont")

  /**
   * C++ original:
   * ```cpp
   * SkVector TestTypeface::getAdvance(SkGlyphID glyphID) const {
   *     glyphID = glyphID < fTestFont->fCharCodesCount ? glyphID : 0;
   *
   *     // TODO(benjaminwagner): Update users to use floats.
   *     return {SkFixedToFloat(fTestFont->fWidths[glyphID]), 0};
   * }
   * ```
   */
  public fun getAdvance(glyphID: SkGlyphID): SkVector {
    TODO("Implement getAdvance")
  }

  /**
   * C++ original:
   * ```cpp
   * void TestTypeface::getFontMetrics(SkFontMetrics* metrics) { *metrics = fTestFont->fMetrics; }
   * ```
   */
  public fun getFontMetrics(metrics: SkFontMetrics?) {
    TODO("Implement getFontMetrics")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath TestTypeface::getPath(SkGlyphID glyphID) {
   *     glyphID = glyphID < fTestFont->fCharCodesCount ? glyphID : 0;
   *     return fTestFont->fPaths[glyphID];
   * }
   * ```
   */
  public fun getPath(glyph: SkGlyphID): SkPath {
    TODO("Implement getPath")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkScalerContext> TestTypeface::onCreateScalerContext(
   *     const SkScalerContextEffects& effects, const SkDescriptor* desc) const
   * {
   *     return std::make_unique<SkTestScalerContext>(*const_cast<TestTypeface*>(this), effects, desc);
   * }
   * ```
   */
  protected override fun onCreateScalerContext(effects: SkScalerContextEffects, desc: SkDescriptor?): Int {
    TODO("Implement onCreateScalerContext")
  }

  /**
   * C++ original:
   * ```cpp
   * void TestTypeface::onFilterRec(SkScalerContextRec* rec) const {
   *     rec->useStrokeForFakeBold();
   *     rec->setHinting(SkFontHinting::kNone);
   * }
   * ```
   */
  protected override fun onFilterRec(rec: SkScalerContextRec?) {
    TODO("Implement onFilterRec")
  }

  /**
   * C++ original:
   * ```cpp
   * void TestTypeface::getGlyphToUnicodeMap(SkSpan<SkUnichar> glyphToUnicode) const {
   *     unsigned glyphCount = std::min(fTestFont->fCharCodesCount, glyphToUnicode.size());
   *     for (unsigned gid = 0; gid < glyphCount; ++gid) {
   *         glyphToUnicode[gid] = SkTo<SkUnichar>(fTestFont->fCharCodes[gid]);
   *     }
   * }
   * ```
   */
  protected override fun getGlyphToUnicodeMap(glyphToUnicode: SkSpan<SkUnichar>) {
    TODO("Implement getGlyphToUnicodeMap")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkAdvancedTypefaceMetrics> TestTypeface::onGetAdvancedMetrics() const {  // pdf only
   *     std::unique_ptr<SkAdvancedTypefaceMetrics>info(new SkAdvancedTypefaceMetrics);
   *     info->fPostScriptName.set(fTestFont->fName);
   *     return info;
   * }
   * ```
   */
  protected override fun onGetAdvancedMetrics(): Int {
    TODO("Implement onGetAdvancedMetrics")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkStreamAsset> TestTypeface::onOpenStream(int* ttcIndex) const {
   *     SkDynamicMemoryWStream wstream;
   *     wstream.write(gHeaderString, kHeaderSize);
   *
   *     SkString name;
   *     this->getFamilyName(&name);
   *     SkFontStyle style = this->fontStyle();
   *
   *     wstream.writePackedUInt(name.size());
   *     wstream.write(name.c_str(), name.size());
   *     wstream.writeScalar(style.weight());
   *     wstream.writeScalar(style.width());
   *     wstream.writePackedUInt(style.slant());
   *
   *     *ttcIndex = 0;
   *     return wstream.detachAsStream();
   * }
   * ```
   */
  protected override fun onOpenStream(ttcIndex: Int?): Int {
    TODO("Implement onOpenStream")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> onMakeClone(const SkFontArguments& args) const override {
   *         return sk_ref_sp(this);
   *     }
   * ```
   */
  protected override fun onMakeClone(args: SkFontArguments): SkSp<SkTypeface> {
    TODO("Implement onMakeClone")
  }

  /**
   * C++ original:
   * ```cpp
   * void TestTypeface::onGetFontDescriptor(SkFontDescriptor* desc, bool* serialize) const {
   *     desc->setFamilyName(fTestFont->fName);
   *     desc->setStyle(this->fontStyle());
   *     desc->setFactoryId(FactoryId);
   *     *serialize = true;
   * }
   * ```
   */
  protected override fun onGetFontDescriptor(desc: SkFontDescriptor?, serialize: Boolean?) {
    TODO("Implement onGetFontDescriptor")
  }

  /**
   * C++ original:
   * ```cpp
   * void TestTypeface::onCharsToGlyphs(SkSpan<const SkUnichar> uni, SkSpan<SkGlyphID> glyphs) const {
   *     SkASSERT(uni.size() == glyphs.size());
   *     for (size_t i = 0; i < uni.size(); ++i) {
   *         glyphs[i] = fTestFont->glyphForUnichar(uni[i]);
   *     }
   * }
   * ```
   */
  protected override fun onCharsToGlyphs(uni: SkSpan<SkUnichar>, glyphs: SkSpan<SkGlyphID>) {
    TODO("Implement onCharsToGlyphs")
  }

  /**
   * C++ original:
   * ```cpp
   * int onCountGlyphs() const override { return (int)fTestFont->fCharCodesCount; }
   * ```
   */
  protected override fun onCountGlyphs(): Int {
    TODO("Implement onCountGlyphs")
  }

  /**
   * C++ original:
   * ```cpp
   * void getPostScriptGlyphNames(SkString*) const override {}
   * ```
   */
  protected override fun getPostScriptGlyphNames(param0: String?) {
    TODO("Implement getPostScriptGlyphNames")
  }

  /**
   * C++ original:
   * ```cpp
   * int onGetUPEM() const override { return 2048; }
   * ```
   */
  protected override fun onGetUPEM(): Int {
    TODO("Implement onGetUPEM")
  }

  /**
   * C++ original:
   * ```cpp
   * void TestTypeface::onGetFamilyName(SkString* familyName) const { *familyName = fTestFont->fName; }
   * ```
   */
  protected override fun onGetFamilyName(familyName: String?) {
    TODO("Implement onGetFamilyName")
  }

  /**
   * C++ original:
   * ```cpp
   * bool TestTypeface::onGetPostScriptName(SkString*) const { return false; }
   * ```
   */
  protected override fun onGetPostScriptName(param0: String?): Boolean {
    TODO("Implement onGetPostScriptName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTypeface::LocalizedStrings* TestTypeface::onCreateFamilyNameIterator() const {
   *     SkString familyName(fTestFont->fName);
   *     SkString language("und");  // undetermined
   *     return new SkOTUtils::LocalizedStrings_SingleName(familyName, language);
   * }
   * ```
   */
  protected override fun onCreateFamilyNameIterator(): SkTypeface.LocalizedStrings {
    TODO("Implement onCreateFamilyNameIterator")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onGlyphMaskNeedsCurrentColor() const override { return false; }
   * ```
   */
  protected override fun onGlyphMaskNeedsCurrentColor(): Boolean {
    TODO("Implement onGlyphMaskNeedsCurrentColor")
  }

  /**
   * C++ original:
   * ```cpp
   * int onGetVariationDesignPosition(
   *                          SkSpan<SkFontArguments::VariationPosition::Coordinate>) const override {
   *         return 0;
   *     }
   * ```
   */
  protected override fun onGetVariationDesignPosition(param0: SkSpan<SkFontArguments.VariationPosition.Coordinate>): Int {
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
  protected override fun onGetVariationDesignParameters(param0: SkSpan<SkFontParameters.Variation.Axis>): Int {
    TODO("Implement onGetVariationDesignParameters")
  }

  /**
   * C++ original:
   * ```cpp
   * int onGetTableTags(SkSpan<SkFontTableTag>) const override { return 0; }
   * ```
   */
  protected override fun onGetTableTags(param0: SkSpan<SkFontTableTag>): Int {
    TODO("Implement onGetTableTags")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t onGetTableData(SkFontTableTag tag,
   *                           size_t         offset,
   *                           size_t         length,
   *                           void*          data) const override {
   *         return 0;
   *     }
   * ```
   */
  protected override fun onGetTableData(
    tag: SkFontTableTag,
    offset: ULong,
    length: ULong,
    `data`: Unit?,
  ): Int {
    TODO("Implement onGetTableData")
  }

  public data class List public constructor(
    public var families: Int,
  ) {
    public data class Family public constructor(
      public var faces: Int,
      public val name: String?,
    ) {
      public data class Face public constructor(
        public var typeface: SkSp<SkTypeface>,
        public val name: String?,
        public var isDefault: Boolean,
      )
    }
  }

  public open class Register public constructor()

  public companion object {
    private val factoryId: Int = TODO("Initialize factoryId")

    /**
     * C++ original:
     * ```cpp
     * const TestTypeface::List& TestTypeface::Typefaces() {
     *     static List list = []() -> List {
     *         TestTypeface::List list;
     *         for (const auto& sub : gSubFonts) {
     *             List::Family* existingFamily = nullptr;
     *             for (auto& family : list.families) {
     *                 if (strcmp(family.name, sub.fFamilyName) == 0) {
     *                     existingFamily = &family;
     *                     break;
     *                 }
     *             }
     *             if (!existingFamily) {
     *                 existingFamily = &list.families.emplace_back();
     *                 existingFamily->name = sub.fFamilyName;
     *             }
     *
     *             auto font = sk_make_sp<SkTestFont>(sub.fFont);
     *             sk_sp<SkTypeface> typeface(new TestTypeface(std::move(font), sub.fStyle));
     *             bool isDefault = (&sub - gSubFonts == gDefaultFontIndex);
     *             existingFamily->faces.emplace_back(
     *                 List::Family::Face{std::move(typeface), sub.fStyleName, isDefault});
     *         }
     *         return list;
     *     }();
     *     return list;
     * }
     * ```
     */
    public fun typefaces(): List {
      TODO("Implement typefaces")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkTypeface> MakeFromStream(std::unique_ptr<SkStreamAsset>, const SkFontArguments&)
     * ```
     */
    private fun makeFromStream(param0: SkStreamAsset?, param1: SkFontArguments): SkSp<SkTypeface> {
      TODO("Implement makeFromStream")
    }
  }
}
