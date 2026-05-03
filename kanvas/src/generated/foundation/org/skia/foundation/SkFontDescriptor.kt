package org.skia.foundation

import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.String
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SkFontDescriptor : SkNoncopyable {
 * public:
 *     SkFontDescriptor();
 *     // Does not affect ownership of SkStream.
 *     static bool Deserialize(SkStream*, SkFontDescriptor* result);
 *
 *     void serialize(SkWStream*) const;
 *
 *     SkFontStyle getStyle() const { return fStyle; }
 *     void setStyle(SkFontStyle style) { fStyle = style; }
 *
 *     const char* getFamilyName() const { return fFamilyName.c_str(); }
 *     const char* getFullName() const { return fFullName.c_str(); }
 *     const char* getPostscriptName() const { return fPostscriptName.c_str(); }
 *
 *     void setFamilyName(const char* name) { fFamilyName.set(name); }
 *     void setFullName(const char* name) { fFullName.set(name); }
 *     void setPostscriptName(const char* name) { fPostscriptName.set(name); }
 *
 *     bool hasStream() const { return bool(fStream); }
 *     std::unique_ptr<SkStreamAsset> dupStream() const { return fStream->duplicate(); }
 *     int getCollectionIndex() const { return fCollectionIndex; }
 *     int getPaletteIndex() const { return fPaletteIndex; }
 *     int getVariationCoordinateCount() const { return fCoordinateCount; }
 *     const SkFontArguments::VariationPosition::Coordinate* getVariation() const {
 *         return fVariation.get();
 *     }
 *     int getPaletteEntryOverrideCount() const { return fPaletteEntryOverrideCount; }
 *     const SkFontArguments::Palette::Override* getPaletteEntryOverrides() const {
 *         return fPaletteEntryOverrides.get();
 *     }
 *     SkTypeface::FactoryId getFactoryId() {
 *         return fFactoryId;
 *     }
 *
 *     std::unique_ptr<SkStreamAsset> detachStream() { return std::move(fStream); }
 *     void setStream(std::unique_ptr<SkStreamAsset> stream) { fStream = std::move(stream); }
 *     void setCollectionIndex(int collectionIndex) { fCollectionIndex = collectionIndex; }
 *     void setPaletteIndex(int paletteIndex) { fPaletteIndex = paletteIndex; }
 *     SkFontArguments::VariationPosition::Coordinate* setVariationCoordinates(int coordinateCount) {
 *         fCoordinateCount = coordinateCount;
 *         return fVariation.reset(coordinateCount);
 *     }
 *     SkFontArguments::Palette::Override* setPaletteEntryOverrides(int paletteEntryOverrideCount) {
 *         fPaletteEntryOverrideCount = paletteEntryOverrideCount;
 *         return fPaletteEntryOverrides.reset(paletteEntryOverrideCount);
 *     }
 *     void setFactoryId(SkTypeface::FactoryId factoryId) {
 *         fFactoryId = factoryId;
 *     }
 *
 *     SkFontArguments getFontArguments() const {
 *         return SkFontArguments()
 *             .setCollectionIndex(this->getCollectionIndex())
 *             .setVariationDesignPosition({this->getVariation(),this->getVariationCoordinateCount()})
 *             .setPalette({this->getPaletteIndex(),
 *                          this->getPaletteEntryOverrides(),
 *                          this->getPaletteEntryOverrideCount()});
 *     }
 *     static SkFontStyle::Width SkFontStyleWidthForWidthAxisValue(SkScalar width);
 *     static SkScalar SkFontWidthAxisValueForStyleWidth(int width);
 *
 * private:
 *     SkString fFamilyName;
 *     SkString fFullName;
 *     SkString fPostscriptName;
 *     SkFontStyle fStyle;
 *
 *     std::unique_ptr<SkStreamAsset> fStream;
 *     int fCollectionIndex = 0;
 *     using Coordinates =
 *             skia_private::AutoSTMalloc<4, SkFontArguments::VariationPosition::Coordinate>;
 *     int fCoordinateCount = 0;
 *     Coordinates fVariation;
 *     int fPaletteIndex = 0;
 *     int fPaletteEntryOverrideCount = 0;
 *     skia_private::AutoTMalloc<SkFontArguments::Palette::Override> fPaletteEntryOverrides;
 *     SkTypeface::FactoryId fFactoryId = 0;
 * }
 * ```
 */
public open class SkFontDescriptor public constructor() : SkNoncopyable() {
  /**
   * C++ original:
   * ```cpp
   * SkString fFamilyName
   * ```
   */
  private var fFamilyName: String = TODO("Initialize fFamilyName")

  /**
   * C++ original:
   * ```cpp
   * SkString fFullName
   * ```
   */
  private var fFullName: String = TODO("Initialize fFullName")

  /**
   * C++ original:
   * ```cpp
   * SkString fPostscriptName
   * ```
   */
  private var fPostscriptName: String = TODO("Initialize fPostscriptName")

  /**
   * C++ original:
   * ```cpp
   * SkFontStyle fStyle
   * ```
   */
  private var fStyle: SkFontStyle = TODO("Initialize fStyle")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkStreamAsset> fStream
   * ```
   */
  private var fStream: Int = TODO("Initialize fStream")

  /**
   * C++ original:
   * ```cpp
   * int fCollectionIndex = 0
   * ```
   */
  private var fCollectionIndex: Int = TODO("Initialize fCollectionIndex")

  /**
   * C++ original:
   * ```cpp
   * int fCoordinateCount = 0
   * ```
   */
  private var fCoordinateCount: Int = TODO("Initialize fCoordinateCount")

  /**
   * C++ original:
   * ```cpp
   * Coordinates fVariation
   * ```
   */
  private var fVariation: Int = TODO("Initialize fVariation")

  /**
   * C++ original:
   * ```cpp
   * int fPaletteIndex = 0
   * ```
   */
  private var fPaletteIndex: Int = TODO("Initialize fPaletteIndex")

  /**
   * C++ original:
   * ```cpp
   * int fPaletteEntryOverrideCount = 0
   * ```
   */
  private var fPaletteEntryOverrideCount: Int = TODO("Initialize fPaletteEntryOverrideCount")

  /**
   * C++ original:
   * ```cpp
   * skia_private::AutoTMalloc<SkFontArguments::Palette::Override> fPaletteEntryOverrides
   * ```
   */
  private var fPaletteEntryOverrides: Int = TODO("Initialize fPaletteEntryOverrides")

  /**
   * C++ original:
   * ```cpp
   * SkTypeface::FactoryId fFactoryId
   * ```
   */
  private var fFactoryId: Int = TODO("Initialize fFactoryId")

  /**
   * C++ original:
   * ```cpp
   * void SkFontDescriptor::serialize(SkWStream* stream) const {
   *     uint32_t styleBits = (fStyle.weight() << 16) | (fStyle.width() << 8) | (fStyle.slant());
   *     stream->writePackedUInt(styleBits);
   *
   *     write_string(stream, fFamilyName, kFontFamilyName);
   *     write_string(stream, fFullName, kFullName);
   *     write_string(stream, fPostscriptName, kPostscriptName);
   *
   *     write_scalar(stream, fStyle.weight(), kWeight);
   *     write_scalar(stream, fStyle.width()[width_for_usWidth], kWidth);
   *     write_scalar(stream, fStyle.slant() == SkFontStyle::kUpright_Slant ? 0 : 14, kSlant);
   *     write_scalar(stream, fStyle.slant() == SkFontStyle::kItalic_Slant ? 1 : 0, kItalic);
   *
   *     if (fCollectionIndex > 0) {
   *         write_uint(stream, fCollectionIndex, kFontIndex);
   *     }
   *     if (fPaletteIndex > 0) {
   *         write_uint(stream, fPaletteIndex, kPaletteIndex);
   *     }
   *     if (fCoordinateCount > 0) {
   *         write_uint(stream, fCoordinateCount, kFontVariation);
   *         for (int i = 0; i < fCoordinateCount; ++i) {
   *             stream->write32(fVariation[i].axis);
   *             stream->writeScalar(fVariation[i].value);
   *         }
   *     }
   *     if (fPaletteEntryOverrideCount > 0) {
   *         write_uint(stream, fPaletteEntryOverrideCount, kPaletteEntryOverrides);
   *         for (int i = 0; i < fPaletteEntryOverrideCount; ++i) {
   *             stream->writePackedUInt(fPaletteEntryOverrides[i].index);
   *             stream->write32(fPaletteEntryOverrides[i].color);
   *         }
   *     }
   *
   *     write_uint(stream, fFactoryId, kFactoryId);
   *
   *     stream->writePackedUInt(kSentinel);
   *
   *     if (fStream) {
   *         std::unique_ptr<SkStreamAsset> fontStream = fStream->duplicate();
   *         size_t length = fontStream->getLength();
   *         stream->writePackedUInt(length);
   *         stream->writeStream(fontStream.get(), length);
   *     } else {
   *         stream->writePackedUInt(0);
   *     }
   * }
   * ```
   */
  public fun serialize(stream: SkWStream?) {
    TODO("Implement serialize")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFontStyle getStyle() const { return fStyle; }
   * ```
   */
  public fun getStyle(): SkFontStyle {
    TODO("Implement getStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * void setStyle(SkFontStyle style) { fStyle = style; }
   * ```
   */
  public fun setStyle(style: SkFontStyle) {
    TODO("Implement setStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* getFamilyName() const { return fFamilyName.c_str(); }
   * ```
   */
  public fun getFamilyName(): Char {
    TODO("Implement getFamilyName")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* getFullName() const { return fFullName.c_str(); }
   * ```
   */
  public fun getFullName(): Char {
    TODO("Implement getFullName")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* getPostscriptName() const { return fPostscriptName.c_str(); }
   * ```
   */
  public fun getPostscriptName(): Char {
    TODO("Implement getPostscriptName")
  }

  /**
   * C++ original:
   * ```cpp
   * void setFamilyName(const char* name) { fFamilyName.set(name); }
   * ```
   */
  public fun setFamilyName(name: String?) {
    TODO("Implement setFamilyName")
  }

  /**
   * C++ original:
   * ```cpp
   * void setFullName(const char* name) { fFullName.set(name); }
   * ```
   */
  public fun setFullName(name: String?) {
    TODO("Implement setFullName")
  }

  /**
   * C++ original:
   * ```cpp
   * void setPostscriptName(const char* name) { fPostscriptName.set(name); }
   * ```
   */
  public fun setPostscriptName(name: String?) {
    TODO("Implement setPostscriptName")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasStream() const { return bool(fStream); }
   * ```
   */
  public fun hasStream(): Boolean {
    TODO("Implement hasStream")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkStreamAsset> dupStream() const { return fStream->duplicate(); }
   * ```
   */
  public fun dupStream(): Int {
    TODO("Implement dupStream")
  }

  /**
   * C++ original:
   * ```cpp
   * int getCollectionIndex() const { return fCollectionIndex; }
   * ```
   */
  public fun getCollectionIndex(): Int {
    TODO("Implement getCollectionIndex")
  }

  /**
   * C++ original:
   * ```cpp
   * int getPaletteIndex() const { return fPaletteIndex; }
   * ```
   */
  public fun getPaletteIndex(): Int {
    TODO("Implement getPaletteIndex")
  }

  /**
   * C++ original:
   * ```cpp
   * int getVariationCoordinateCount() const { return fCoordinateCount; }
   * ```
   */
  public fun getVariationCoordinateCount(): Int {
    TODO("Implement getVariationCoordinateCount")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkFontArguments::VariationPosition::Coordinate* getVariation() const {
   *         return fVariation.get();
   *     }
   * ```
   */
  public fun getVariation(): SkFontArguments.VariationPosition.Coordinate {
    TODO("Implement getVariation")
  }

  /**
   * C++ original:
   * ```cpp
   * int getPaletteEntryOverrideCount() const { return fPaletteEntryOverrideCount; }
   * ```
   */
  public fun getPaletteEntryOverrideCount(): Int {
    TODO("Implement getPaletteEntryOverrideCount")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkFontArguments::Palette::Override* getPaletteEntryOverrides() const {
   *         return fPaletteEntryOverrides.get();
   *     }
   * ```
   */
  public fun getPaletteEntryOverrides(): SkFontArguments.Palette.Override {
    TODO("Implement getPaletteEntryOverrides")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTypeface::FactoryId getFactoryId() {
   *         return fFactoryId;
   *     }
   * ```
   */
  public fun getFactoryId(): Int {
    TODO("Implement getFactoryId")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkStreamAsset> detachStream() { return std::move(fStream); }
   * ```
   */
  public fun detachStream(): Int {
    TODO("Implement detachStream")
  }

  /**
   * C++ original:
   * ```cpp
   * void setStream(std::unique_ptr<SkStreamAsset> stream) { fStream = std::move(stream); }
   * ```
   */
  public fun setStream(stream: SkStreamAsset?) {
    TODO("Implement setStream")
  }

  /**
   * C++ original:
   * ```cpp
   * void setCollectionIndex(int collectionIndex) { fCollectionIndex = collectionIndex; }
   * ```
   */
  public fun setCollectionIndex(collectionIndex: Int) {
    TODO("Implement setCollectionIndex")
  }

  /**
   * C++ original:
   * ```cpp
   * void setPaletteIndex(int paletteIndex) { fPaletteIndex = paletteIndex; }
   * ```
   */
  public fun setPaletteIndex(paletteIndex: Int) {
    TODO("Implement setPaletteIndex")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFontArguments::VariationPosition::Coordinate* setVariationCoordinates(int coordinateCount) {
   *         fCoordinateCount = coordinateCount;
   *         return fVariation.reset(coordinateCount);
   *     }
   * ```
   */
  public fun setVariationCoordinates(coordinateCount: Int): SkFontArguments.VariationPosition.Coordinate {
    TODO("Implement setVariationCoordinates")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFontArguments::Palette::Override* setPaletteEntryOverrides(int paletteEntryOverrideCount) {
   *         fPaletteEntryOverrideCount = paletteEntryOverrideCount;
   *         return fPaletteEntryOverrides.reset(paletteEntryOverrideCount);
   *     }
   * ```
   */
  public fun setPaletteEntryOverrides(paletteEntryOverrideCount: Int): SkFontArguments.Palette.Override {
    TODO("Implement setPaletteEntryOverrides")
  }

  /**
   * C++ original:
   * ```cpp
   * void setFactoryId(SkTypeface::FactoryId factoryId) {
   *         fFactoryId = factoryId;
   *     }
   * ```
   */
  public fun setFactoryId(factoryId: SkTypeface.FactoryId) {
    TODO("Implement setFactoryId")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFontArguments getFontArguments() const {
   *         return SkFontArguments()
   *             .setCollectionIndex(this->getCollectionIndex())
   *             .setVariationDesignPosition({this->getVariation(),this->getVariationCoordinateCount()})
   *             .setPalette({this->getPaletteIndex(),
   *                          this->getPaletteEntryOverrides(),
   *                          this->getPaletteEntryOverrideCount()});
   *     }
   * ```
   */
  public fun getFontArguments(): SkFontArguments {
    TODO("Implement getFontArguments")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * bool SkFontDescriptor::Deserialize(SkStream* stream, SkFontDescriptor* result) {
     *     size_t factoryId;
     *     using FactoryIdType = decltype(result->fFactoryId);
     *
     *     size_t coordinateCount;
     *     using CoordinateCountType = decltype(result->fCoordinateCount);
     *
     *     size_t index;
     *     using CollectionIndexType = decltype(result->fCollectionIndex);
     *
     *     size_t paletteIndex;
     *     using PaletteIndexType = decltype(result->fPaletteIndex);
     *
     *     size_t paletteEntryOverrideCount;
     *     using PaletteEntryOverrideCountType = decltype(result->fPaletteEntryOverrideCount);
     *
     *     size_t paletteEntryOverrideIndex;
     *     using PaletteEntryOverrideIndexType = decltype(result->fPaletteEntryOverrides[0].index);
     *
     *     SkScalar weight = SkFontStyle::kNormal_Weight;
     *     SkScalar width = SkFontStyle::kNormal_Width;
     *     SkScalar slant = 0;
     *     SkScalar italic = 0;
     *
     *     size_t styleBits;
     *     if (!stream->readPackedUInt(&styleBits)) { return false; }
     *     weight = ((styleBits >> 16) & 0xFFFF);
     *     width  = ((styleBits >>  8) & 0x000F)[width_for_usWidth];
     *     slant  = ((styleBits >>  0) & 0x000F) != SkFontStyle::kUpright_Slant ? 14 : 0;
     *     italic = ((styleBits >>  0) & 0x000F) == SkFontStyle::kItalic_Slant ? 1 : 0;
     *
     *     for (size_t id; (id = read_id(stream)) != kSentinel;) {
     *         switch (id) {
     *             case kFontFamilyName:
     *                 if (!read_string(stream, &result->fFamilyName)) { return false; }
     *                 break;
     *             case kFullName:
     *                 if (!read_string(stream, &result->fFullName)) { return false; }
     *                 break;
     *             case kPostscriptName:
     *                 if (!read_string(stream, &result->fPostscriptName)) { return false; }
     *                 break;
     *             case kWeight:
     *                 if (!stream->readScalar(&weight)) { return false; }
     *                 break;
     *             case kWidth:
     *                 if (!stream->readScalar(&width)) { return false; }
     *                 break;
     *             case kSlant:
     *                 if (!stream->readScalar(&slant)) { return false; }
     *                 break;
     *             case kItalic:
     *                 if (!stream->readScalar(&italic)) { return false; }
     *                 break;
     *             case kFontVariation:
     *                 if (!stream->readPackedUInt(&coordinateCount)) { return false; }
     *                 if (!SkTFitsIn<CoordinateCountType>(coordinateCount)) { return false; }
     *                 if (SkStreamPriv::RemainingLengthIsBelow(stream, coordinateCount)) {
     *                     return false;
     *                 }
     *                 result->fCoordinateCount = SkTo<CoordinateCountType>(coordinateCount);
     *
     *                 result->fVariation.reset(coordinateCount);
     *                 for (size_t i = 0; i < coordinateCount; ++i) {
     *                     if (!stream->readU32(&result->fVariation[i].axis)) { return false; }
     *                     if (!stream->readScalar(&result->fVariation[i].value)) { return false; }
     *                 }
     *                 break;
     *             case kFontIndex:
     *                 if (!stream->readPackedUInt(&index)) { return false; }
     *                 if (!SkTFitsIn<CollectionIndexType>(index)) { return false; }
     *                 result->fCollectionIndex = SkTo<CollectionIndexType>(index);
     *                 break;
     *             case kPaletteIndex:
     *                 if (!stream->readPackedUInt(&paletteIndex)) { return false; }
     *                 if (!SkTFitsIn<PaletteIndexType>(paletteIndex)) { return false; }
     *                 result->fPaletteIndex = SkTo<PaletteIndexType>(paletteIndex);
     *                 break;
     *             case kPaletteEntryOverrides:
     *                 if (!stream->readPackedUInt(&paletteEntryOverrideCount)) { return false; }
     *                 if (!SkTFitsIn<PaletteEntryOverrideCountType>(paletteEntryOverrideCount)) {
     *                     return false;
     *                 }
     *                 if (SkStreamPriv::RemainingLengthIsBelow(stream, paletteEntryOverrideCount)) {
     *                     return false;
     *                 }
     *                 result->fPaletteEntryOverrideCount =
     *                         SkTo<PaletteEntryOverrideCountType>(paletteEntryOverrideCount);
     *
     *                 result->fPaletteEntryOverrides.reset(paletteEntryOverrideCount);
     *                 for (size_t i = 0; i < paletteEntryOverrideCount; ++i) {
     *                     if (!stream->readPackedUInt(&paletteEntryOverrideIndex)) { return false; }
     *                     if (!SkTFitsIn<PaletteEntryOverrideIndexType>(paletteEntryOverrideIndex)) {
     *                         return false;
     *                     }
     *                     result->fPaletteEntryOverrides[i].index =
     *                             SkTo<PaletteEntryOverrideIndexType>(paletteEntryOverrideIndex);
     *                     if (!stream->readU32(&result->fPaletteEntryOverrides[i].color)) {
     *                         return false;
     *                     }
     *                 }
     *                 break;
     *             case kFactoryId:
     *                 if (!stream->readPackedUInt(&factoryId)) { return false; }
     *                 if (!SkTFitsIn<FactoryIdType>(factoryId)) { return false; }
     *                 result->fFactoryId = SkTo<FactoryIdType>(factoryId);
     *                 break;
     *             default:
     *                 SkDEBUGFAIL("Unknown id used by a font descriptor");
     *                 return false;
     *         }
     *     }
     *
     *     SkFontStyle::Slant slantEnum = SkFontStyle::kUpright_Slant;
     *     if (slant != 0) { slantEnum = SkFontStyle::kOblique_Slant; }
     *     if (0 < italic) { slantEnum = SkFontStyle::kItalic_Slant; }
     *     SkFontStyle::Width widthEnum = SkFontStyleWidthForWidthAxisValue(width);
     *     result->fStyle = SkFontStyle(SkScalarRoundToInt(weight), widthEnum, slantEnum);
     *
     *     size_t length;
     *     if (!stream->readPackedUInt(&length)) { return false; }
     *     if (length > 0) {
     *         if (SkStreamPriv::RemainingLengthIsBelow(stream, length)) {
     *             return false;
     *         }
     *         sk_sp<SkData> data(SkData::MakeUninitialized(length));
     *         if (stream->read(data->writable_data(), length) != length) {
     *             SkDEBUGFAIL("Could not read font data");
     *             return false;
     *         }
     *         result->fStream = SkMemoryStream::Make(std::move(data));
     *     }
     *     return true;
     * }
     * ```
     */
    public fun deserialize(stream: SkStream?, result: SkFontDescriptor?): Boolean {
      TODO("Implement deserialize")
    }

    /**
     * C++ original:
     * ```cpp
     * SkFontStyle::Width SkFontDescriptor::SkFontStyleWidthForWidthAxisValue(SkScalar width) {
     *     int usWidth = SkScalarRoundToInt(SkFloatInterpFunc(width, &width_for_usWidth[1], usWidths, 9));
     *     return static_cast<SkFontStyle::Width>(usWidth);
     * }
     * ```
     */
    public fun skFontStyleWidthForWidthAxisValue(width: SkScalar): SkFontStyle.Width {
      TODO("Implement skFontStyleWidthForWidthAxisValue")
    }

    /**
     * C++ original:
     * ```cpp
     * SkScalar SkFontDescriptor::SkFontWidthAxisValueForStyleWidth(int width) {
     *     return width_for_usWidth[width & 0xF];
     * }
     * ```
     */
    public fun skFontWidthAxisValueForStyleWidth(width: Int): SkScalar {
      TODO("Implement skFontWidthAxisValueForStyleWidth")
    }
  }
}
