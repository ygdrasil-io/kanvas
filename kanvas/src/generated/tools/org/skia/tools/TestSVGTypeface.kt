package org.skia.tools

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit
import org.skia.core.SkCanvas
import org.skia.core.SkFontMetrics
import org.skia.core.SkPathOp
import org.skia.core.TArray
import org.skia.core.THashMap
import org.skia.foundation.SkColor
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
import org.skia.foundation.SkWStream
import org.skia.math.SkIRect
import org.skia.math.SkPoint
import org.skia.math.SkScalar
import org.skia.math.SkSize
import org.skia.math.SkVector
import org.skia.modules.SkSVGDOM
import undefined.Fn
import undefined.SkMutex

/**
 * C++ original:
 * ```cpp
 * class TestSVGTypeface : public SkTypeface {
 * public:
 *     ~TestSVGTypeface() override;
 *     SkVector getAdvance(SkGlyphID) const;
 *     void getFontMetrics(SkFontMetrics* metrics) const;
 *
 *     static sk_sp<TestSVGTypeface> Default();
 *     static sk_sp<TestSVGTypeface> Planets();
 *     void                          exportTtxCbdt(SkWStream*, SkSpan<unsigned> strikeSizes) const;
 *     void                          exportTtxSbix(SkWStream*, SkSpan<unsigned> strikeSizes) const;
 *     void                          exportTtxColr(SkWStream*) const;
 *     virtual bool                  getPathOp(SkColor, SkPathOp*) const = 0;
 *
 *     struct GlyfLayerInfo {
 *         GlyfLayerInfo(int layerColorIndex, SkIRect bounds)
 *                 : fLayerColorIndex(layerColorIndex), fBounds(bounds) {}
 *         int     fLayerColorIndex;
 *         SkIRect fBounds;
 *     };
 *     struct GlyfInfo {
 *         GlyfInfo() : fBounds(SkIRect::MakeEmpty()) {}
 *         SkIRect                             fBounds;
 *         skia_private::TArray<GlyfLayerInfo> fLayers;
 *     };
 *
 * protected:
 *     void exportTtxCommon(
 *             SkWStream*, const char* type, const skia_private::TArray<GlyfInfo>* = nullptr) const;
 *
 *     std::unique_ptr<SkScalerContext> onCreateScalerContext(const SkScalerContextEffects&,
 *                                                            const SkDescriptor* desc) const override;
 *     void onFilterRec(SkScalerContextRec* rec) const override;
 *     void getGlyphToUnicodeMap(SkSpan<SkUnichar>) const override;
 *     std::unique_ptr<SkAdvancedTypefaceMetrics> onGetAdvancedMetrics() const override;
 *
 *     sk_sp<SkTypeface> onMakeClone(const SkFontArguments& args) const override {
 *         return sk_ref_sp(this);
 *     }
 *
 *     void onGetFontDescriptor(SkFontDescriptor* desc, bool* isLocal) const override = 0;
 *
 *     void onCharsToGlyphs(SkSpan<const SkUnichar>, SkSpan<SkGlyphID>) const override;
 *
 *     void getPostScriptGlyphNames(SkString*) const override {}
 *
 *     int onCountGlyphs() const override { return fGlyphCount; }
 *
 *     int onGetUPEM() const override { return fUpem; }
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
 *     TestSVGTypeface(const char* name, const SkFontStyle& style,
 *                     int upem, const SkFontMetrics& metrics,
 *                     SkSpan<const SkSVGTestTypefaceGlyphData> data);
 *     struct Glyph {
 *         Glyph();
 *         ~Glyph();
 *         SkPoint     fOrigin;
 *         SkScalar    fAdvance;
 *         const char* fResourcePath;
 *
 *         SkSize size() const;
 *         void render(SkCanvas*) const;
 *
 *     private:
 *         // Lazily parses the SVG from fResourcePath, and manages mutex locking.
 *         template <typename Fn> void withSVG(Fn&&) const;
 *
 *         // The mutex guards lazy parsing of the SVG, but also predates that.
 *         // Must be SkSVGDOM::render() is not thread safe?
 *         // If not, an SkOnce is enough here.
 *         mutable SkMutex         fSvgMutex;
 *         mutable bool            fParsedSvg = false;
 *         mutable sk_sp<SkSVGDOM> fSvg;
 *     };
 *
 *     const SkString fName;
 *     const int fUpem;
 *     const SkFontMetrics fFontMetrics;
 *     const std::unique_ptr<Glyph[]> fGlyphs;
 *     const int fGlyphCount;
 *     skia_private::THashMap<SkUnichar, SkGlyphID> fCMap;
 *     friend class SkTestSVGScalerContext;
 * }
 * ```
 */
public abstract class TestSVGTypeface public constructor(
  name: String?,
  style: SkFontStyle,
  upem: Int,
  metrics: SkFontMetrics,
  `data`: SkSpan<SkSVGTestTypefaceGlyphData>,
) : SkTypeface(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * const SkString fName
   * ```
   */
  private val fName: String = TODO("Initialize fName")

  /**
   * C++ original:
   * ```cpp
   * const int fUpem
   * ```
   */
  private val fUpem: Int = TODO("Initialize fUpem")

  /**
   * C++ original:
   * ```cpp
   * const SkFontMetrics fFontMetrics
   * ```
   */
  private val fFontMetrics: SkFontMetrics = TODO("Initialize fFontMetrics")

  /**
   * C++ original:
   * ```cpp
   * const std::unique_ptr<Glyph[]> fGlyphs
   * ```
   */
  private val fGlyphs: Int = TODO("Initialize fGlyphs")

  /**
   * C++ original:
   * ```cpp
   * const int fGlyphCount
   * ```
   */
  private val fGlyphCount: Int = TODO("Initialize fGlyphCount")

  /**
   * C++ original:
   * ```cpp
   * skia_private::THashMap<SkUnichar, SkGlyphID> fCMap
   * ```
   */
  private var fCMap: THashMap<SkUnichar, SkGlyphID> = TODO("Initialize fCMap")

  /**
   * C++ original:
   * ```cpp
   * SkVector TestSVGTypeface::getAdvance(SkGlyphID glyphID) const {
   *     glyphID = glyphID < fGlyphCount ? glyphID : 0;
   *     return {fGlyphs[glyphID].fAdvance, 0};
   * }
   * ```
   */
  public fun getAdvance(glyphID: SkGlyphID): SkVector {
    TODO("Implement getAdvance")
  }

  /**
   * C++ original:
   * ```cpp
   * void TestSVGTypeface::getFontMetrics(SkFontMetrics* metrics) const { *metrics = fFontMetrics; }
   * ```
   */
  public fun getFontMetrics(metrics: SkFontMetrics?) {
    TODO("Implement getFontMetrics")
  }

  /**
   * C++ original:
   * ```cpp
   * void TestSVGTypeface::exportTtxCbdt(SkWStream* out, SkSpan<unsigned> strikeSizes) const {
   *     SkPaint paint;
   *     SkFont  font;
   *     font.setTypeface(sk_ref_sp(const_cast<TestSVGTypeface*>(this)));
   *     SkString name;
   *     this->getFamilyName(&name);
   *
   *     // The CBDT/CBLC format is quite restrictive. Only write strikes which fully fit.
   *     STArray<8, int> goodStrikeSizes;
   *     for (size_t strikeIndex = 0; strikeIndex < strikeSizes.size(); ++strikeIndex) {
   *         font.setSize(strikeSizes[strikeIndex]);
   *
   *         // CBLC limits
   *         SkFontMetrics fm;
   *         font.getMetrics(&fm);
   *         if (!SkTFitsIn<int8_t>((int)(-fm.fTop)) || !SkTFitsIn<int8_t>((int)(-fm.fBottom)) ||
   *             !SkTFitsIn<uint8_t>((int)(fm.fXMax - fm.fXMin))) {
   *             SkDebugf("Metrics too big cbdt font size %f for %s.\n", font.getSize(), name.c_str());
   *             continue;
   *         }
   *
   *         // CBDT limits
   *         auto exceedsCbdtLimits = [&]() {
   *             for (int i = 0; i < fGlyphCount; ++i) {
   *                 SkGlyphID gid = i;
   *                 SkScalar  advance;
   *                 SkRect    bounds;
   *                 font.getWidthsBounds({&gid, 1}, {&advance, 1}, {&bounds, 1}, nullptr);
   *                 SkIRect ibounds = bounds.roundOut();
   *                 if (!SkTFitsIn<int8_t>(ibounds.fLeft) || !SkTFitsIn<int8_t>(ibounds.fTop) ||
   *                     !SkTFitsIn<uint8_t>(ibounds.width()) || !SkTFitsIn<uint8_t>(ibounds.height()) ||
   *                     !SkTFitsIn<uint8_t>((int)advance)) {
   *                     return true;
   *                 }
   *             }
   *             return false;
   *         };
   *         if (exceedsCbdtLimits()) {
   *             SkDebugf("Glyphs too big cbdt font size %f for %s.\n", font.getSize(), name.c_str());
   *             continue;
   *         }
   *
   *         goodStrikeSizes.emplace_back(strikeSizes[strikeIndex]);
   *     }
   *
   *     if (goodStrikeSizes.empty()) {
   *         SkDebugf("No strike size fit for cbdt font for %s.\n", name.c_str());
   *         return;
   *     }
   *
   *     out->writeText("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
   *     out->writeText("<ttFont sfntVersion=\"\\x00\\x01\\x00\\x00\" ttLibVersion=\"3.19\">\n");
   *     this->exportTtxCommon(out, "CBDT");
   *
   *     out->writeText("  <CBDT>\n");
   *     out->writeText("    <header version=\"2.0\"/>\n");
   *     for (int strikeIndex = 0; strikeIndex < goodStrikeSizes.size(); ++strikeIndex) {
   *         font.setSize(goodStrikeSizes[strikeIndex]);
   *
   *         out->writeText("    <strikedata index=\"");
   *         out->writeDecAsText(strikeIndex);
   *         out->writeText("\">\n");
   *         for (int i = 0; i < fGlyphCount; ++i) {
   *             SkGlyphID gid = i;
   *             SkScalar  advance;
   *             SkRect    bounds;
   *             font.getWidthsBounds({&gid, 1}, {&advance, 1}, {&bounds, 1}, nullptr);
   *             SkIRect ibounds = bounds.roundOut();
   *             if (ibounds.isEmpty()) {
   *                 continue;
   *             }
   *             SkImageInfo image_info = SkImageInfo::MakeN32Premul(ibounds.width(), ibounds.height());
   *             sk_sp<SkSurface> surface(SkSurfaces::Raster(image_info));
   *             SkASSERT(surface);
   *             SkCanvas* canvas = surface->getCanvas();
   *             canvas->clear(0);
   *             SkPixmap pix;
   *             surface->peekPixels(&pix);
   *             canvas->drawSimpleText(&gid,
   *                                    sizeof(gid),
   *                                    SkTextEncoding::kGlyphID,
   *                                    -bounds.fLeft,
   *                                    -bounds.fTop,
   *                                    font,
   *                                    paint);
   *
   *             sk_sp<SkImage> image = surface->makeImageSnapshot();
   *             sk_sp<SkData> data = SkPngEncoder::Encode(nullptr, image.get(), {});
   *
   *             out->writeText("      <cbdt_bitmap_format_17 name=\"glyf");
   *             out->writeHexAsText(i, 4);
   *             out->writeText("\">\n");
   *             out->writeText("        <SmallGlyphMetrics>\n");
   *             out->writeText("          <height value=\"");
   *             out->writeDecAsText(image->height());
   *             out->writeText("\"/>\n");
   *             out->writeText("          <width value=\"");
   *             out->writeDecAsText(image->width());
   *             out->writeText("\"/>\n");
   *             out->writeText("          <BearingX value=\"");
   *             out->writeDecAsText(ibounds.fLeft);
   *             out->writeText("\"/>\n");
   *             out->writeText("          <BearingY value=\"");
   *             out->writeDecAsText(-ibounds.fTop);
   *             out->writeText("\"/>\n");
   *             out->writeText("          <Advance value=\"");
   *             out->writeDecAsText((int)advance);
   *             out->writeText("\"/>\n");
   *             out->writeText("        </SmallGlyphMetrics>\n");
   *             out->writeText("        <rawimagedata>");
   *             uint8_t const* bytes = data->bytes();
   *             for (size_t j = 0; j < data->size(); ++j) {
   *                 if ((j % 0x10) == 0x0) {
   *                     out->writeText("\n          ");
   *                 } else if (((j - 1) % 0x4) == 0x3) {
   *                     out->writeText(" ");
   *                 }
   *                 out->writeHexAsText(bytes[j], 2);
   *             }
   *             out->writeText("\n");
   *             out->writeText("        </rawimagedata>\n");
   *             out->writeText("      </cbdt_bitmap_format_17>\n");
   *         }
   *         out->writeText("    </strikedata>\n");
   *     }
   *     out->writeText("  </CBDT>\n");
   *
   *     SkFontMetrics fm;
   *     out->writeText("  <CBLC>\n");
   *     out->writeText("    <header version=\"2.0\"/>\n");
   *     for (int strikeIndex = 0; strikeIndex < goodStrikeSizes.size(); ++strikeIndex) {
   *         font.setSize(goodStrikeSizes[strikeIndex]);
   *         font.getMetrics(&fm);
   *         out->writeText("    <strike index=\"");
   *         out->writeDecAsText(strikeIndex);
   *         out->writeText("\">\n");
   *         out->writeText("      <bitmapSizeTable>\n");
   *         out->writeText("        <sbitLineMetrics direction=\"hori\">\n");
   *         out->writeText("          <ascender value=\"");
   *         out->writeDecAsText((int)(-fm.fTop));
   *         out->writeText("\"/>\n");
   *         out->writeText("          <descender value=\"");
   *         out->writeDecAsText((int)(-fm.fBottom));
   *         out->writeText("\"/>\n");
   *         out->writeText("          <widthMax value=\"");
   *         out->writeDecAsText((int)(fm.fXMax - fm.fXMin));
   *         out->writeText("\"/>\n");
   *         out->writeText("          <caretSlopeNumerator value=\"0\"/>\n");
   *         out->writeText("          <caretSlopeDenominator value=\"0\"/>\n");
   *         out->writeText("          <caretOffset value=\"0\"/>\n");
   *         out->writeText("          <minOriginSB value=\"0\"/>\n");
   *         out->writeText("          <minAdvanceSB value=\"0\"/>\n");
   *         out->writeText("          <maxBeforeBL value=\"0\"/>\n");
   *         out->writeText("          <minAfterBL value=\"0\"/>\n");
   *         out->writeText("          <pad1 value=\"0\"/>\n");
   *         out->writeText("          <pad2 value=\"0\"/>\n");
   *         out->writeText("        </sbitLineMetrics>\n");
   *         out->writeText("        <sbitLineMetrics direction=\"vert\">\n");
   *         out->writeText("          <ascender value=\"");
   *         out->writeDecAsText((int)(-fm.fTop));
   *         out->writeText("\"/>\n");
   *         out->writeText("          <descender value=\"");
   *         out->writeDecAsText((int)(-fm.fBottom));
   *         out->writeText("\"/>\n");
   *         out->writeText("          <widthMax value=\"");
   *         out->writeDecAsText((int)(fm.fXMax - fm.fXMin));
   *         out->writeText("\"/>\n");
   *         out->writeText("          <caretSlopeNumerator value=\"0\"/>\n");
   *         out->writeText("          <caretSlopeDenominator value=\"0\"/>\n");
   *         out->writeText("          <caretOffset value=\"0\"/>\n");
   *         out->writeText("          <minOriginSB value=\"0\"/>\n");
   *         out->writeText("          <minAdvanceSB value=\"0\"/>\n");
   *         out->writeText("          <maxBeforeBL value=\"0\"/>\n");
   *         out->writeText("          <minAfterBL value=\"0\"/>\n");
   *         out->writeText("          <pad1 value=\"0\"/>\n");
   *         out->writeText("          <pad2 value=\"0\"/>\n");
   *         out->writeText("        </sbitLineMetrics>\n");
   *         out->writeText("        <colorRef value=\"0\"/>\n");
   *         out->writeText("        <startGlyphIndex value=\"1\"/>\n");
   *         out->writeText("        <endGlyphIndex value=\"1\"/>\n");
   *         out->writeText("        <ppemX value=\"");
   *         out->writeDecAsText(goodStrikeSizes[strikeIndex]);
   *         out->writeText("\"/>\n");
   *         out->writeText("        <ppemY value=\"");
   *         out->writeDecAsText(goodStrikeSizes[strikeIndex]);
   *         out->writeText("\"/>\n");
   *         out->writeText("        <bitDepth value=\"32\"/>\n");
   *         out->writeText("        <flags value=\"1\"/>\n");
   *         out->writeText("      </bitmapSizeTable>\n");
   *         out->writeText(
   *                 "      <eblc_index_sub_table_1 imageFormat=\"17\" firstGlyphIndex=\"1\" "
   *                 "lastGlyphIndex=\"1\">\n");
   *         for (int i = 0; i < fGlyphCount; ++i) {
   *             SkGlyphID gid = i;
   *             SkRect    bounds = font.getBounds(gid, nullptr);
   *             if (bounds.isEmpty()) {
   *                 continue;
   *             }
   *             out->writeText("        <glyphLoc name=\"glyf");
   *             out->writeHexAsText(i, 4);
   *             out->writeText("\"/>\n");
   *         }
   *         out->writeText("      </eblc_index_sub_table_1>\n");
   *         out->writeText("    </strike>\n");
   *     }
   *     out->writeText("  </CBLC>\n");
   *
   *     out->writeText("</ttFont>\n");
   * }
   * ```
   */
  public fun exportTtxCbdt(`out`: SkWStream?, strikeSizes: SkSpan<UInt>) {
    TODO("Implement exportTtxCbdt")
  }

  /**
   * C++ original:
   * ```cpp
   * void TestSVGTypeface::exportTtxSbix(SkWStream* out, SkSpan<unsigned> strikeSizes) const {
   *     out->writeText("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
   *     out->writeText("<ttFont sfntVersion=\"\\x00\\x01\\x00\\x00\" ttLibVersion=\"3.19\">\n");
   *     this->exportTtxCommon(out, "sbix");
   *
   *     SkPaint paint;
   *     SkFont  font;
   *     font.setTypeface(sk_ref_sp(const_cast<TestSVGTypeface*>(this)));
   *
   *     out->writeText("  <glyf>\n");
   *     for (int i = 0; i < fGlyphCount; ++i) {
   *         const TestSVGTypeface::Glyph& glyphData = this->fGlyphs[i];
   *
   *         SkSize containerSize = glyphData.size();
   *         SkRect  bounds = SkRect::MakeXYWH(glyphData.fOrigin.fX, -glyphData.fOrigin.fY,
   *                                           containerSize.fWidth, containerSize.fHeight);
   *         SkIRect ibounds = bounds.roundOut();
   *         out->writeText("    <TTGlyph name=\"glyf");
   *         out->writeHexAsText(i, 4);
   *         out->writeText("\" xMin=\"");
   *         out->writeDecAsText(/*ibounds.fLeft*/0); //hmtx::lsb already has this from common
   *         out->writeText("\" yMin=\"");
   *         out->writeDecAsText(-ibounds.fBottom);
   *         out->writeText("\" xMax=\"");
   *         out->writeDecAsText(ibounds.fRight - ibounds.fLeft);
   *         out->writeText("\" yMax=\"");
   *         out->writeDecAsText(-ibounds.fTop);
   *         out->writeText("\">\n");
   *         out->writeText("      <contour>\n");
   *         out->writeText("        <pt x=\"");
   *         out->writeDecAsText(/*ibounds.fLeft*/0);
   *         out->writeText("\" y=\"");
   *         out->writeDecAsText(-ibounds.fBottom);
   *         out->writeText("\" on=\"1\"/>\n");
   *         out->writeText("      </contour>\n");
   *         out->writeText("      <contour>\n");
   *         out->writeText("        <pt x=\"");
   *         out->writeDecAsText(ibounds.fRight - ibounds.fLeft);
   *         out->writeText("\" y=\"");
   *         out->writeDecAsText(-ibounds.fTop);
   *         out->writeText("\" on=\"1\"/>\n");
   *         out->writeText("      </contour>\n");
   *         out->writeText("      <instructions/>\n");
   *         out->writeText("    </TTGlyph>\n");
   *     }
   *     out->writeText("  </glyf>\n");
   *
   *     // The loca table will be re-calculated, but if we don't write one we don't get one.
   *     out->writeText("  <loca/>\n");
   *
   *     out->writeText("  <sbix>\n");
   *     out->writeText("    <version value=\"1\"/>\n");
   *     out->writeText("    <flags value=\"00000000 00000001\"/>\n");
   *     for (size_t strikeIndex = 0; strikeIndex < strikeSizes.size(); ++strikeIndex) {
   *         font.setSize(strikeSizes[strikeIndex]);
   *         out->writeText("    <strike>\n");
   *         out->writeText("      <ppem value=\"");
   *         out->writeDecAsText(strikeSizes[strikeIndex]);
   *         out->writeText("\"/>\n");
   *         out->writeText("      <resolution value=\"72\"/>\n");
   *         for (int i = 0; i < fGlyphCount; ++i) {
   *             SkGlyphID gid = i;
   *             SkScalar  advance;
   *             SkRect    bounds;
   *             font.getWidthsBounds({&gid, 1}, {&advance, 1}, {&bounds, 1}, nullptr);
   *             SkIRect ibounds = bounds.roundOut();
   *             if (ibounds.isEmpty()) {
   *                 continue;
   *             }
   *             SkImageInfo image_info = SkImageInfo::MakeN32Premul(ibounds.width(), ibounds.height());
   *             sk_sp<SkSurface> surface(SkSurfaces::Raster(image_info));
   *             SkASSERT(surface);
   *             SkCanvas* canvas = surface->getCanvas();
   *             canvas->clear(0);
   *             SkPixmap pix;
   *             surface->peekPixels(&pix);
   *             canvas->drawSimpleText(&gid,
   *                                    sizeof(gid),
   *                                    SkTextEncoding::kGlyphID,
   *                                    -bounds.fLeft,
   *                                    -bounds.fTop,
   *                                    font,
   *                                    paint);
   *
   *             sk_sp<SkImage> image = surface->makeImageSnapshot();
   *             sk_sp<SkData> data = SkPngEncoder::Encode(nullptr, image.get(), {});
   *
   *             out->writeText("      <glyph name=\"glyf");
   *             out->writeHexAsText(i, 4);
   *
   *             // DirectWrite and CoreGraphics use positive values of originOffsetY to push the
   *             // image visually up (but from different origins).
   *             // FreeType used positive values to push the image down until 2.12.0.
   *             // However, in a bitmap only font there is little reason for these to not be zero.
   *             out->writeText("\" graphicType=\"png \" originOffsetX=\"0\" originOffsetY=\"0\">\n");
   *
   *             out->writeText("        <hexdata>");
   *             uint8_t const* bytes = data->bytes();
   *             for (size_t j = 0; j < data->size(); ++j) {
   *                 if ((j % 0x10) == 0x0) {
   *                     out->writeText("\n          ");
   *                 } else if (((j - 1) % 0x4) == 0x3) {
   *                     out->writeText(" ");
   *                 }
   *                 out->writeHexAsText(bytes[j], 2);
   *             }
   *             out->writeText("\n");
   *             out->writeText("        </hexdata>\n");
   *             out->writeText("      </glyph>\n");
   *         }
   *         out->writeText("    </strike>\n");
   *     }
   *     out->writeText("  </sbix>\n");
   *     out->writeText("</ttFont>\n");
   * }
   * ```
   */
  public fun exportTtxSbix(`out`: SkWStream?, strikeSizes: SkSpan<UInt>) {
    TODO("Implement exportTtxSbix")
  }

  /**
   * C++ original:
   * ```cpp
   * void TestSVGTypeface::exportTtxColr(SkWStream* out) const {
   *     out->writeText("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
   *     out->writeText("<ttFont sfntVersion=\"\\x00\\x01\\x00\\x00\" ttLibVersion=\"3.19\">\n");
   *
   *     THashMap<SkColor, int> colors;
   *     TArray<GlyfInfo>       glyfInfos(fGlyphCount);
   *
   *     // Need to know all the glyphs up front for the common tables.
   *     SkDynamicMemoryWStream glyfOut;
   *     glyfOut.writeText("  <glyf>\n");
   *     for (int i = 0; i < fGlyphCount; ++i) {
   *         const TestSVGTypeface::Glyph& glyphData = this->fGlyphs[i];
   *
   *         SkSize containerSize = glyphData.size();
   *         SkRect       bounds = SkRect::MakeXYWH(glyphData.fOrigin.fX,
   *                                          -glyphData.fOrigin.fY,
   *                                          containerSize.fWidth,
   *                                          containerSize.fHeight);
   *         SkCOLRCanvas canvas(bounds, *this, i, &glyfInfos.emplace_back(), &colors, &glyfOut);
   *         glyphData.render(&canvas);
   *         canvas.finishGlyph();
   *     }
   *     glyfOut.writeText("  </glyf>\n");
   *
   *     this->exportTtxCommon(out, "COLR", &glyfInfos);
   *
   *     // The loca table will be re-calculated, but if we don't write one we don't get one.
   *     out->writeText("  <loca/>\n");
   *
   *     std::unique_ptr<SkStreamAsset> glyfStream = glyfOut.detachAsStream();
   *     out->writeStream(glyfStream.get(), glyfStream->getLength());
   *
   *     out->writeText("  <COLR>\n");
   *     out->writeText("    <version value=\"0\"/>\n");
   *     for (int i = 0; i < fGlyphCount; ++i) {
   *         if (glyfInfos[i].fLayers.empty()) {
   *             continue;
   *         }
   *         if (glyfInfos[i].fBounds.isEmpty()) {
   *             SkDebugf("Glyph %d is empty but has layers.\n", i);
   *         }
   *         out->writeText("    <ColorGlyph name=\"glyf");
   *         out->writeHexAsText(i, 4);
   *         out->writeText("\">\n");
   *         for (int j = 0; j < glyfInfos[i].fLayers.size(); ++j) {
   *             const int colorIndex = glyfInfos[i].fLayers[j].fLayerColorIndex;
   *             out->writeText("      <layer colorID=\"");
   *             out->writeDecAsText(colorIndex);
   *             out->writeText("\" name=\"glyf");
   *             out->writeHexAsText(i, 4);
   *             out->writeText("l");
   *             out->writeHexAsText(j, 4);
   *             out->writeText("\"/>\n");
   *         }
   *         out->writeText("    </ColorGlyph>\n");
   *     }
   *     out->writeText("  </COLR>\n");
   *
   *     // The colors must be written in order, the 'index' is ignored by ttx.
   *     AutoTMalloc<SkColor> colorsInOrder(colors.count());
   *     colors.foreach ([&colorsInOrder](const SkColor& c, const int* i) { colorsInOrder[*i] = c; });
   *     out->writeText("  <CPAL>\n");
   *     out->writeText("    <version value=\"0\"/>\n");
   *     out->writeText("    <numPaletteEntries value=\"");
   *     out->writeDecAsText(colors.count());
   *     out->writeText("\"/>\n");
   *     out->writeText("    <palette index=\"0\">\n");
   *     for (int i = 0; i < colors.count(); ++i) {
   *         SkColor c = colorsInOrder[i];
   *         out->writeText("      <color index=\"");
   *         out->writeDecAsText(i);
   *         out->writeText("\" value=\"#");
   *         out->writeHexAsText(SkColorGetR(c), 2);
   *         out->writeHexAsText(SkColorGetG(c), 2);
   *         out->writeHexAsText(SkColorGetB(c), 2);
   *         out->writeHexAsText(SkColorGetA(c), 2);
   *         out->writeText("\"/>\n");
   *     }
   *     out->writeText("    </palette>\n");
   *     out->writeText("  </CPAL>\n");
   *
   *     out->writeText("</ttFont>\n");
   * }
   * ```
   */
  public fun exportTtxColr(`out`: SkWStream?) {
    TODO("Implement exportTtxColr")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool                  getPathOp(SkColor, SkPathOp*) const = 0
   * ```
   */
  public abstract fun getPathOp(param0: SkColor, param1: SkPathOp?): Boolean

  /**
   * C++ original:
   * ```cpp
   * void TestSVGTypeface::exportTtxCommon(SkWStream*                out,
   *                                       const char*               type,
   *                                       const TArray<GlyfInfo>* glyfInfo) const {
   *     int totalGlyphs = fGlyphCount;
   *     out->writeText("  <GlyphOrder>\n");
   *     for (int i = 0; i < fGlyphCount; ++i) {
   *         out->writeText("    <GlyphID name=\"glyf");
   *         out->writeHexAsText(i, 4);
   *         out->writeText("\"/>\n");
   *     }
   *     if (glyfInfo) {
   *         for (int i = 0; i < fGlyphCount; ++i) {
   *             for (int j = 0; j < (*glyfInfo)[i].fLayers.size(); ++j) {
   *                 out->writeText("    <GlyphID name=\"glyf");
   *                 out->writeHexAsText(i, 4);
   *                 out->writeText("l");
   *                 out->writeHexAsText(j, 4);
   *                 out->writeText("\"/>\n");
   *                 ++totalGlyphs;
   *             }
   *         }
   *     }
   *     out->writeText("  </GlyphOrder>\n");
   *
   *     out->writeText("  <head>\n");
   *     out->writeText("    <tableVersion value=\"1.0\"/>\n");
   *     out->writeText("    <fontRevision value=\"1.0\"/>\n");
   *     out->writeText("    <checkSumAdjustment value=\"0xa9c3274\"/>\n");
   *     out->writeText("    <magicNumber value=\"0x5f0f3cf5\"/>\n");
   *     out->writeText("    <flags value=\"00000000 00011011\"/>\n");
   *     out->writeText("    <unitsPerEm value=\"");
   *     out->writeDecAsText(fUpem);
   *     out->writeText("\"/>\n");
   *     out->writeText("    <created value=\"Thu Feb 15 12:55:49 2018\"/>\n");
   *     out->writeText("    <modified value=\"Thu Feb 15 12:55:49 2018\"/>\n");
   *     // TODO: not recalculated for bitmap fonts?
   *     out->writeText("    <xMin value=\"");
   *     out->writeScalarAsText(fFontMetrics.fXMin);
   *     out->writeText("\"/>\n");
   *     out->writeText("    <yMin value=\"");
   *     out->writeScalarAsText(-fFontMetrics.fBottom);
   *     out->writeText("\"/>\n");
   *     out->writeText("    <xMax value=\"");
   *     out->writeScalarAsText(fFontMetrics.fXMax);
   *     out->writeText("\"/>\n");
   *     out->writeText("    <yMax value=\"");
   *     out->writeScalarAsText(-fFontMetrics.fTop);
   *     out->writeText("\"/>\n");
   *
   *     char macStyle[16] = {
   *             '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0'};
   *     if (this->fontStyle().weight() >= SkFontStyle::Bold().weight()) {
   *         macStyle[0xF - 0x0] = '1';  // Bold
   *     }
   *     switch (this->fontStyle().slant()) {
   *         case SkFontStyle::kUpright_Slant: break;
   *         case SkFontStyle::kItalic_Slant:
   *             macStyle[0xF - 0x1] = '1';  // Italic
   *             break;
   *         case SkFontStyle::kOblique_Slant:
   *             macStyle[0xF - 0x1] = '1';  // Italic
   *             break;
   *         default: SK_ABORT("Unknown slant.");
   *     }
   *     if (this->fontStyle().width() <= SkFontStyle::kCondensed_Width) {
   *         macStyle[0xF - 0x5] = '1';  // Condensed
   *     } else if (this->fontStyle().width() >= SkFontStyle::kExpanded_Width) {
   *         macStyle[0xF - 0x6] = '1';  // Extended
   *     }
   *     out->writeText("    <macStyle value=\"");
   *     out->write(macStyle, 8);
   *     out->writeText(" ");
   *     out->write(macStyle + 8, 8);
   *     out->writeText("\"/>\n");
   *     out->writeText("    <lowestRecPPEM value=\"8\"/>\n");
   *     out->writeText("    <fontDirectionHint value=\"2\"/>\n");
   *     out->writeText("    <indexToLocFormat value=\"0\"/>\n");
   *     out->writeText("    <glyphDataFormat value=\"0\"/>\n");
   *     out->writeText("  </head>\n");
   *
   *     out->writeText("  <hhea>\n");
   *     out->writeText("    <tableVersion value=\"0x00010000\"/>\n");
   *     out->writeText("    <ascent value=\"");
   *     out->writeDecAsText(-fFontMetrics.fAscent);
   *     out->writeText("\"/>\n");
   *     out->writeText("    <descent value=\"");
   *     out->writeDecAsText(-fFontMetrics.fDescent);
   *     out->writeText("\"/>\n");
   *     out->writeText("    <lineGap value=\"");
   *     out->writeDecAsText(fFontMetrics.fLeading);
   *     out->writeText("\"/>\n");
   *     out->writeText("    <advanceWidthMax value=\"0\"/>\n");
   *     out->writeText("    <minLeftSideBearing value=\"0\"/>\n");
   *     out->writeText("    <minRightSideBearing value=\"0\"/>\n");
   *     out->writeText("    <xMaxExtent value=\"");
   *     out->writeScalarAsText(fFontMetrics.fXMax - fFontMetrics.fXMin);
   *     out->writeText("\"/>\n");
   *     out->writeText("    <caretSlopeRise value=\"1\"/>\n");
   *     out->writeText("    <caretSlopeRun value=\"0\"/>\n");
   *     out->writeText("    <caretOffset value=\"0\"/>\n");
   *     out->writeText("    <reserved0 value=\"0\"/>\n");
   *     out->writeText("    <reserved1 value=\"0\"/>\n");
   *     out->writeText("    <reserved2 value=\"0\"/>\n");
   *     out->writeText("    <reserved3 value=\"0\"/>\n");
   *     out->writeText("    <metricDataFormat value=\"0\"/>\n");
   *     out->writeText("    <numberOfHMetrics value=\"0\"/>\n");
   *     out->writeText("  </hhea>\n");
   *
   *     // Some of this table is going to be re-calculated, but we have to write it out anyway.
   *     out->writeText("  <maxp>\n");
   *     out->writeText("    <tableVersion value=\"0x10000\"/>\n");
   *     out->writeText("    <numGlyphs value=\"");
   *     out->writeDecAsText(totalGlyphs);
   *     out->writeText("\"/>\n");
   *     out->writeText("    <maxPoints value=\"4\"/>\n");
   *     out->writeText("    <maxContours value=\"1\"/>\n");
   *     out->writeText("    <maxCompositePoints value=\"0\"/>\n");
   *     out->writeText("    <maxCompositeContours value=\"0\"/>\n");
   *     out->writeText("    <maxZones value=\"1\"/>\n");
   *     out->writeText("    <maxTwilightPoints value=\"0\"/>\n");
   *     out->writeText("    <maxStorage value=\"0\"/>\n");
   *     out->writeText("    <maxFunctionDefs value=\"10\"/>\n");
   *     out->writeText("    <maxInstructionDefs value=\"0\"/>\n");
   *     out->writeText("    <maxStackElements value=\"512\"/>\n");
   *     out->writeText("    <maxSizeOfInstructions value=\"24\"/>\n");
   *     out->writeText("    <maxComponentElements value=\"0\"/>\n");
   *     out->writeText("    <maxComponentDepth value=\"0\"/>\n");
   *     out->writeText("  </maxp>\n");
   *
   *     out->writeText("  <OS_2>\n");
   *     out->writeText("    <version value=\"4\"/>\n");
   *     out->writeText("    <xAvgCharWidth value=\"");
   *     out->writeScalarAsText(fFontMetrics.fAvgCharWidth);
   *     out->writeText("\"/>\n");
   *     out->writeText("    <usWeightClass value=\"");
   *     out->writeDecAsText(this->fontStyle().weight());
   *     out->writeText("\"/>\n");
   *     out->writeText("    <usWidthClass value=\"");
   *     out->writeDecAsText(this->fontStyle().width());
   *     out->writeText("\"/>\n");
   *     out->writeText("    <fsType value=\"00000000 00000000\"/>\n");
   *     out->writeText("    <ySubscriptXSize value=\"665\"/>\n");
   *     out->writeText("    <ySubscriptYSize value=\"716\"/>\n");
   *     out->writeText("    <ySubscriptXOffset value=\"0\"/>\n");
   *     out->writeText("    <ySubscriptYOffset value=\"143\"/>\n");
   *     out->writeText("    <ySuperscriptXSize value=\"665\"/>\n");
   *     out->writeText("    <ySuperscriptYSize value=\"716\"/>\n");
   *     out->writeText("    <ySuperscriptXOffset value=\"0\"/>\n");
   *     out->writeText("    <ySuperscriptYOffset value=\"491\"/>\n");
   *     out->writeText("    <yStrikeoutSize value=\"");
   *     out->writeScalarAsText(fFontMetrics.fStrikeoutThickness);
   *     out->writeText("\"/>\n");
   *     out->writeText("    <yStrikeoutPosition value=\"");
   *     out->writeScalarAsText(-fFontMetrics.fStrikeoutPosition);
   *     out->writeText("\"/>\n");
   *     out->writeText("    <sFamilyClass value=\"0\"/>\n");
   *     out->writeText("    <panose>\n");
   *     out->writeText("      <bFamilyType value=\"0\"/>\n");
   *     out->writeText("      <bSerifStyle value=\"0\"/>\n");
   *     out->writeText("      <bWeight value=\"0\"/>\n");
   *     out->writeText("      <bProportion value=\"0\"/>\n");
   *     out->writeText("      <bContrast value=\"0\"/>\n");
   *     out->writeText("      <bStrokeVariation value=\"0\"/>\n");
   *     out->writeText("      <bArmStyle value=\"0\"/>\n");
   *     out->writeText("      <bLetterForm value=\"0\"/>\n");
   *     out->writeText("      <bMidline value=\"0\"/>\n");
   *     out->writeText("      <bXHeight value=\"0\"/>\n");
   *     out->writeText("    </panose>\n");
   *     out->writeText("    <ulUnicodeRange1 value=\"00000000 00000000 00000000 00000001\"/>\n");
   *     out->writeText("    <ulUnicodeRange2 value=\"00010000 00000000 00000000 00000000\"/>\n");
   *     out->writeText("    <ulUnicodeRange3 value=\"00000000 00000000 00000000 00000000\"/>\n");
   *     out->writeText("    <ulUnicodeRange4 value=\"00000000 00000000 00000000 00000000\"/>\n");
   *     out->writeText("    <achVendID value=\"Skia\"/>\n");
   *     char fsSelection[16] = {
   *             '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0'};
   *     fsSelection[0xF - 0x7] = '1';  // Use typo metrics
   *     if (this->fontStyle().weight() >= SkFontStyle::Bold().weight()) {
   *         fsSelection[0xF - 0x5] = '1';  // Bold
   *     }
   *     switch (this->fontStyle().slant()) {
   *         case SkFontStyle::kUpright_Slant:
   *             if (this->fontStyle().weight() < SkFontStyle::Bold().weight()) {
   *                 fsSelection[0xF - 0x6] = '1';  // Not bold or italic, is regular
   *             }
   *             break;
   *         case SkFontStyle::kItalic_Slant:
   *             fsSelection[0xF - 0x0] = '1';  // Italic
   *             break;
   *         case SkFontStyle::kOblique_Slant:
   *             fsSelection[0xF - 0x0] = '1';  // Italic
   *             fsSelection[0xF - 0x9] = '1';  // Oblique
   *             break;
   *         default: SK_ABORT("Unknown slant.");
   *     }
   *     out->writeText("    <fsSelection value=\"");
   *     out->write(fsSelection, 8);
   *     out->writeText(" ");
   *     out->write(fsSelection + 8, 8);
   *     out->writeText("\"/>\n");
   *     out->writeText("    <usFirstCharIndex value=\"0\"/>\n");
   *     out->writeText("    <usLastCharIndex value=\"0\"/>\n");
   *     out->writeText("    <sTypoAscender value=\"");
   *     out->writeScalarAsText(-fFontMetrics.fAscent);
   *     out->writeText("\"/>\n");
   *     out->writeText("    <sTypoDescender value=\"");
   *     out->writeScalarAsText(-fFontMetrics.fDescent);
   *     out->writeText("\"/>\n");
   *     out->writeText("    <sTypoLineGap value=\"");
   *     out->writeScalarAsText(fFontMetrics.fLeading);
   *     out->writeText("\"/>\n");
   *     out->writeText("    <usWinAscent value=\"");
   *     out->writeScalarAsText(-fFontMetrics.fAscent);
   *     out->writeText("\"/>\n");
   *     out->writeText("    <usWinDescent value=\"");
   *     out->writeScalarAsText(fFontMetrics.fDescent);
   *     out->writeText("\"/>\n");
   *     out->writeText("    <ulCodePageRange1 value=\"00000000 00000000 00000000 00000000\"/>\n");
   *     out->writeText("    <ulCodePageRange2 value=\"00000000 00000000 00000000 00000000\"/>\n");
   *     out->writeText("    <sxHeight value=\"");
   *     out->writeScalarAsText(fFontMetrics.fXHeight);
   *     out->writeText("\"/>\n");
   *     out->writeText("    <sCapHeight value=\"");
   *     out->writeScalarAsText(fFontMetrics.fCapHeight);
   *     out->writeText("\"/>\n");
   *     out->writeText("    <usDefaultChar value=\"0\"/>\n");
   *     out->writeText("    <usBreakChar value=\"32\"/>\n");
   *     out->writeText("    <usMaxContext value=\"0\"/>\n");
   *     out->writeText("  </OS_2>\n");
   *
   *     out->writeText("  <hmtx>\n");
   *     for (int i = 0; i < fGlyphCount; ++i) {
   *         out->writeText("    <mtx name=\"glyf");
   *         out->writeHexAsText(i, 4);
   *         out->writeText("\" width=\"");
   *         out->writeDecAsText(fGlyphs[i].fAdvance);
   *         out->writeText("\" lsb=\"");
   *         int lsb = fGlyphs[i].fOrigin.fX;
   *         if (glyfInfo) {
   *             lsb += (*glyfInfo)[i].fBounds.fLeft;
   *         }
   *         out->writeDecAsText(lsb);
   *         out->writeText("\"/>\n");
   *     }
   *     if (glyfInfo) {
   *         for (int i = 0; i < fGlyphCount; ++i) {
   *             for (int j = 0; j < (*glyfInfo)[i].fLayers.size(); ++j) {
   *                 out->writeText("    <mtx name=\"glyf");
   *                 out->writeHexAsText(i, 4);
   *                 out->writeText("l");
   *                 out->writeHexAsText(j, 4);
   *                 out->writeText("\" width=\"");
   *                 out->writeDecAsText(fGlyphs[i].fAdvance);
   *                 out->writeText("\" lsb=\"");
   *                 int32_t lsb = fGlyphs[i].fOrigin.fX + (*glyfInfo)[i].fLayers[j].fBounds.fLeft;
   *                 out->writeDecAsText(lsb);
   *                 out->writeText("\"/>\n");
   *             }
   *         }
   *     }
   *     out->writeText("  </hmtx>\n");
   *
   *     bool hasNonBMP = false;
   *     out->writeText("  <cmap>\n");
   *     out->writeText("    <tableVersion version=\"0\"/>\n");
   *     out->writeText("    <cmap_format_4 platformID=\"3\" platEncID=\"1\" language=\"0\">\n");
   *     fCMap.foreach ([&out, &hasNonBMP](const SkUnichar& c, const SkGlyphID& g) {
   *         if (0xFFFF < c) {
   *             hasNonBMP = true;
   *             return;
   *         }
   *         out->writeText("      <map code=\"0x");
   *         out->writeHexAsText(c, 4);
   *         out->writeText("\" name=\"glyf");
   *         out->writeHexAsText(g, 4);
   *         out->writeText("\"/>\n");
   *     });
   *     out->writeText("    </cmap_format_4>\n");
   *     if (hasNonBMP) {
   *         out->writeText(
   *                 "    <cmap_format_12 platformID=\"3\" platEncID=\"10\" format=\"12\" "
   *                 "reserved=\"0\" length=\"1\" language=\"0\" nGroups=\"0\">\n");
   *         fCMap.foreach ([&out](const SkUnichar& c, const SkGlyphID& g) {
   *             out->writeText("      <map code=\"0x");
   *             out->writeHexAsText(c, 6);
   *             out->writeText("\" name=\"glyf");
   *             out->writeHexAsText(g, 4);
   *             out->writeText("\"/>\n");
   *         });
   *         out->writeText("    </cmap_format_12>\n");
   *     }
   *     out->writeText("  </cmap>\n");
   *
   *     out->writeText("  <name>\n");
   *     out->writeText(
   *             "    <namerecord nameID=\"1\" platformID=\"3\" platEncID=\"1\" langID=\"0x409\">\n");
   *     out->writeText("      ");
   *     out->writeText(fName.c_str());
   *     out->writeText(" ");
   *     out->writeText(type);
   *     out->writeText("\n");
   *     out->writeText("    </namerecord>\n");
   *     out->writeText(
   *             "    <namerecord nameID=\"2\" platformID=\"3\" platEncID=\"1\" langID=\"0x409\">\n");
   *     out->writeText("      Regular\n");
   *     out->writeText("    </namerecord>\n");
   *         out->writeText(
   *             "    <namerecord nameID=\"6\" platformID=\"3\" platEncID=\"1\" langID=\"0x409\">\n");
   *     out->writeText("      ");
   *     out->writeText(fName.c_str());
   *     out->writeText("_");
   *     out->writeText(type);
   *     out->writeText("\n");
   *     out->writeText("    </namerecord>\n");
   *     out->writeText("  </name>\n");
   *
   *     out->writeText("  <post>\n");
   *     out->writeText("    <formatType value=\"3.0\"/>\n");
   *     out->writeText("    <italicAngle value=\"0.0\"/>\n");
   *     out->writeText("    <underlinePosition value=\"");
   *     out->writeScalarAsText(fFontMetrics.fUnderlinePosition);
   *     out->writeText("\"/>\n");
   *     out->writeText("    <underlineThickness value=\"");
   *     out->writeScalarAsText(fFontMetrics.fUnderlineThickness);
   *     out->writeText("\"/>\n");
   *     out->writeText("    <isFixedPitch value=\"0\"/>\n");
   *     out->writeText("    <minMemType42 value=\"0\"/>\n");
   *     out->writeText("    <maxMemType42 value=\"0\"/>\n");
   *     out->writeText("    <minMemType1 value=\"0\"/>\n");
   *     out->writeText("    <maxMemType1 value=\"0\"/>\n");
   *     out->writeText("  </post>\n");
   * }
   * ```
   */
  protected fun exportTtxCommon(
    `out`: SkWStream?,
    type: String?,
    glyfInfo: TArray<GlyfInfo>? = TODO(),
  ) {
    TODO("Implement exportTtxCommon")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkScalerContext> TestSVGTypeface::onCreateScalerContext(
   *     const SkScalerContextEffects& e, const SkDescriptor* desc) const
   * {
   *     return std::make_unique<SkTestSVGScalerContext>(*const_cast<TestSVGTypeface*>(this), e, desc);
   * }
   * ```
   */
  protected override fun onCreateScalerContext(e: SkScalerContextEffects, desc: SkDescriptor?): Int {
    TODO("Implement onCreateScalerContext")
  }

  /**
   * C++ original:
   * ```cpp
   * void TestSVGTypeface::onFilterRec(SkScalerContextRec* rec) const {
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
   * void TestSVGTypeface::getGlyphToUnicodeMap(SkSpan<SkUnichar> glyphToUnicode) const {
   *     SkDEBUGCODE(unsigned glyphCount = this->countGlyphs());
   *     fCMap.foreach ([=](const SkUnichar& c, const SkGlyphID& g) {
   *         SkASSERT(g < glyphCount);
   *         SkASSERT(g < glyphToUnicode.size());
   *         glyphToUnicode[g] = c;
   *     });
   * }
   * ```
   */
  protected override fun getGlyphToUnicodeMap(glyphToUnicode: SkSpan<SkUnichar>) {
    TODO("Implement getGlyphToUnicodeMap")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkAdvancedTypefaceMetrics> TestSVGTypeface::onGetAdvancedMetrics() const {
   *     std::unique_ptr<SkAdvancedTypefaceMetrics> info(new SkAdvancedTypefaceMetrics);
   *     info->fPostScriptName = fName;
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
   * void TestSVGTypeface::onGetFontDescriptor(SkFontDescriptor* desc, bool* serialize) const {
   *     desc->setFamilyName(fName.c_str());
   *     desc->setStyle(this->fontStyle());
   *     *serialize = true;
   * }
   * ```
   */
  protected abstract override fun onGetFontDescriptor(desc: SkFontDescriptor?, isLocal: Boolean?)

  /**
   * C++ original:
   * ```cpp
   * void TestSVGTypeface::onCharsToGlyphs(SkSpan<const SkUnichar> uni, SkSpan<SkGlyphID> glyphs) const {
   *     SkASSERT(uni.size() == glyphs.size());
   *     for (size_t i = 0; i < uni.size(); i++) {
   *         SkGlyphID* g = fCMap.find(uni[i]);
   *         glyphs[i]    = g ? *g : 0;
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
   * void getPostScriptGlyphNames(SkString*) const override {}
   * ```
   */
  protected override fun getPostScriptGlyphNames(param0: String?) {
    TODO("Implement getPostScriptGlyphNames")
  }

  /**
   * C++ original:
   * ```cpp
   * int onCountGlyphs() const override { return fGlyphCount; }
   * ```
   */
  protected override fun onCountGlyphs(): Int {
    TODO("Implement onCountGlyphs")
  }

  /**
   * C++ original:
   * ```cpp
   * int onGetUPEM() const override { return fUpem; }
   * ```
   */
  protected override fun onGetUPEM(): Int {
    TODO("Implement onGetUPEM")
  }

  /**
   * C++ original:
   * ```cpp
   * void TestSVGTypeface::onGetFamilyName(SkString* familyName) const { *familyName = fName; }
   * ```
   */
  protected override fun onGetFamilyName(familyName: String?) {
    TODO("Implement onGetFamilyName")
  }

  /**
   * C++ original:
   * ```cpp
   * bool TestSVGTypeface::onGetPostScriptName(SkString*) const { return false; }
   * ```
   */
  protected override fun onGetPostScriptName(param0: String?): Boolean {
    TODO("Implement onGetPostScriptName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTypeface::LocalizedStrings* TestSVGTypeface::onCreateFamilyNameIterator() const {
   *     SkString familyName(fName);
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

  public data class GlyfLayerInfo public constructor(
    public var fLayerColorIndex: Int,
    public var fBounds: SkIRect,
  )

  public data class GlyfInfo public constructor(
    public var fBounds: SkIRect,
    public var fLayers: Int,
  )

  public data class Glyph public constructor(
    public var fOrigin: SkPoint,
    public var fAdvance: SkScalar,
    public val fResourcePath: String?,
    private var fSvgMutex: SkMutex,
    private var fParsedSvg: Boolean,
    private var fSvg: SkSp<SkSVGDOM>,
  ) {
    public fun size(): SkSize {
      TODO("Implement size")
    }

    public fun render(canvas: SkCanvas?) {
      TODO("Implement render")
    }

    private fun withSVG(fn: Fn) {
      TODO("Implement withSVG")
    }
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<TestSVGTypeface> TestSVGTypeface::Default() {
     *     // Recommended that the first four be .notdef, .null, CR, space
     *     constexpr const static SkSVGTestTypefaceGlyphData glyphs[] = {
     *             {"fonts/svg/notdef.svg", {100, 800}, 800, 0x0},      // .notdef
     *             {"fonts/svg/empty.svg", {0, 0}, 800, 0x0020},        // space
     *             {"fonts/svg/diamond.svg", {100, 800}, 800, 0x2662},  //  
     *             {"fonts/svg/smile.svg", {0, 800}, 800, 0x1F600},     // 😀
     *     };
     *     SkFontMetrics metrics;
     *     metrics.fFlags = SkFontMetrics::kUnderlineThicknessIsValid_Flag |
     *                      SkFontMetrics::kUnderlinePositionIsValid_Flag |
     *                      SkFontMetrics::kStrikeoutThicknessIsValid_Flag |
     *                      SkFontMetrics::kStrikeoutPositionIsValid_Flag;
     *     metrics.fTop                = -800;
     *     metrics.fAscent             = -800;
     *     metrics.fDescent            = 200;
     *     metrics.fBottom             = 200;
     *     metrics.fLeading            = 100;
     *     metrics.fAvgCharWidth       = 1000;
     *     metrics.fMaxCharWidth       = 1000;
     *     metrics.fXMin               = 0;
     *     metrics.fXMax               = 1000;
     *     metrics.fXHeight            = 500;
     *     metrics.fCapHeight          = 700;
     *     metrics.fUnderlineThickness = 40;
     *     metrics.fUnderlinePosition  = 20;
     *     metrics.fStrikeoutThickness = 20;
     *     metrics.fStrikeoutPosition  = -400;
     *
     *     return sk_sp<TestSVGTypeface>(
     *         new DefaultTypeface("Emoji", SkFontStyle::Normal(), 1000, metrics, glyphs));
     * }
     * ```
     */
    public fun default(): SkSp<TestSVGTypeface> {
      TODO("Implement default")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<TestSVGTypeface> TestSVGTypeface::Planets() {
     *     // Recommended that the first four be .notdef, .null, CR, space
     *     constexpr const static SkSVGTestTypefaceGlyphData glyphs[] = {
     *             {"fonts/svg/planets/pluto.svg", {0, 20}, 60, 0x0},             // .notdef
     *             {"fonts/svg/empty.svg", {0, 0}, 400, 0x0020},                  // space
     *             {"fonts/svg/planets/mercury.svg", {0, 45}, 120, 0x263F},       // ☿
     *             {"fonts/svg/planets/venus.svg", {0, 100}, 240, 0x2640},        // ♀
     *             {"fonts/svg/planets/earth.svg", {0, 100}, 240, 0x2641},        // ♁
     *             {"fonts/svg/planets/mars.svg", {0, 50}, 130, 0x2642},          // ♂
     *             {"fonts/svg/planets/jupiter.svg", {0, 1000}, 2200, 0x2643},    // ♃
     *             {"fonts/svg/planets/saturn.svg", {-300, 1500}, 2600, 0x2644},  // ♄
     *             {"fonts/svg/planets/uranus.svg", {0, 375}, 790, 0x2645},       // ♅
     *             {"fonts/svg/planets/neptune.svg", {0, 350}, 740, 0x2646},      // ♆
     *     };
     *     SkFontMetrics metrics;
     *     metrics.fFlags = SkFontMetrics::kUnderlineThicknessIsValid_Flag |
     *                      SkFontMetrics::kUnderlinePositionIsValid_Flag |
     *                      SkFontMetrics::kStrikeoutThicknessIsValid_Flag |
     *                      SkFontMetrics::kStrikeoutPositionIsValid_Flag;
     *     metrics.fTop                = -1500;
     *     metrics.fAscent             = -200;
     *     metrics.fDescent            = 50;
     *     metrics.fBottom             = 1558;
     *     metrics.fLeading            = 10;
     *     metrics.fAvgCharWidth       = 200;
     *     metrics.fMaxCharWidth       = 200;
     *     metrics.fXMin               = -300;
     *     metrics.fXMax               = 2566;
     *     metrics.fXHeight            = 100;
     *     metrics.fCapHeight          = 180;
     *     metrics.fUnderlineThickness = 8;
     *     metrics.fUnderlinePosition  = 2;
     *     metrics.fStrikeoutThickness = 2;
     *     metrics.fStrikeoutPosition  = -80;
     *
     *     return sk_sp<TestSVGTypeface>(
     *         new PlanetTypeface("Planets", SkFontStyle::Normal(), 200, metrics, glyphs));
     * }
     * ```
     */
    public fun planets(): SkSp<TestSVGTypeface> {
      TODO("Implement planets")
    }
  }
}
