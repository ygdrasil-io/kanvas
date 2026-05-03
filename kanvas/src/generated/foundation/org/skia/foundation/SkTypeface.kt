package org.skia.foundation

import FactoryId
import `*make)(std`.unique_ptr
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.ULong
import kotlin.Unit
import org.skia.core.SkFontParameters
import org.skia.core.SkTextEncoding
import org.skia.core.SkWeakRefCnt
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SK_API SkTypeface : public SkWeakRefCnt {
 * public:
 *     /** Returns the typeface's intrinsic style attributes. */
 *     SkFontStyle fontStyle() const;
 *
 *     /** Returns true if style() has the kBold bit set. */
 *     bool isBold() const;
 *
 *     /** Returns true if style() has the kItalic bit set. */
 *     bool isItalic() const;
 *
 *     /** Returns true if the typeface claims to be fixed-pitch.
 *      *  This is a style bit, advance widths may vary even if this returns true.
 *      */
 *     bool isFixedPitch() const;
 *
 *     /** Copy into 'coordinates' (allocated by the caller) the design variation coordinates.
 *      *
 *      *  @param coordinates the span into which to write the design variation coordinates.
 *      *
 *      *  @return The number of axes, or -1 if there is an error.
 *      *  If 'coordinates.size() >= numAxes' then 'coordinates' will be
 *      *  filled with the variation coordinates describing the position of this typeface in design
 *      *  variation space. It is possible the number of axes can be retrieved but actual position
 *      *  cannot.
 *      */
 *     int getVariationDesignPosition(
 *                        SkSpan<SkFontArguments::VariationPosition::Coordinate> coordinates) const;
 *
 *     /** Copy into 'parameters' (allocated by the caller) the design variation parameters.
 *      *
 *      *  @param parameters the span into which to write the design variation parameters.
 *      *
 *      *  @return The number of axes, or -1 if there is an error.
 *      *  If 'parameters.size() >= numAxes' then 'parameters' will be
 *      *  filled with the variation parameters describing the position of this typeface in design
 *      *  variation space. It is possible the number of axes can be retrieved but actual parameters
 *      *  cannot.
 *      */
 *     int getVariationDesignParameters(SkSpan<SkFontParameters::Variation::Axis> parameters) const;
 *
 *     /** Return a 32bit value for this typeface, unique for the underlying font
 *         data. Will never return 0.
 *      */
 *     SkTypefaceID uniqueID() const { return fUniqueID; }
 *
 *     /** Returns true if the two typefaces reference the same underlying font,
 *         handling either being null (treating null as not equal to any font).
 *      */
 *     static bool Equal(const SkTypeface* facea, const SkTypeface* faceb);
 *
 *     /** Returns a non-null typeface which contains no glyphs. */
 *     static sk_sp<SkTypeface> MakeEmpty();
 *
 *     /** Return a new typeface based on this typeface but parameterized as specified in the
 *         SkFontArguments. If the SkFontArguments does not supply an argument for a parameter
 *         in the font then the value from this typeface will be used as the value for that
 *         argument. If the cloned typeface would be exaclty the same as this typeface then
 *         this typeface may be ref'ed and returned. May return nullptr on failure.
 *     */
 *     sk_sp<SkTypeface> makeClone(const SkFontArguments&) const;
 *
 *     /**
 *      *  A typeface can serialize just a descriptor (names, etc.), or it can also include the
 *      *  actual font data (which can be large). This enum controls how serialize() decides what
 *      *  to serialize.
 *      */
 *     enum class SerializeBehavior {
 *         kDoIncludeData,
 *         kDontIncludeData,
 *         kIncludeDataIfLocal,
 *     };
 *
 *     /** Write a unique signature to a stream, sufficient to reconstruct a
 *         typeface referencing the same font when Deserialize is called.
 *      */
 *     void serialize(SkWStream*, SerializeBehavior = SerializeBehavior::kIncludeDataIfLocal) const;
 *
 *     /**
 *      *  Same as serialize(SkWStream*, ...) but returns the serialized data in SkData, instead of
 *      *  writing it to a stream.
 *      */
 *     sk_sp<SkData> serialize(SerializeBehavior = SerializeBehavior::kIncludeDataIfLocal) const;
 *
 *     /** Given the data previously written by serialize(), return a new instance
 *         of a typeface referring to the same font. If that font is not available,
 *         return nullptr.
 *         Goes through all registered typeface factories and lastResortMgr (if non-null).
 *         Does not affect ownership of SkStream.
 *      */
 *
 *     static sk_sp<SkTypeface> MakeDeserialize(SkStream*, sk_sp<SkFontMgr> lastResortMgr);
 *
 *     /**
 *      *  Given an array of UTF32 character codes, return their corresponding glyph IDs.
 *      *
 *      *  @param unis span of UTF32 chars
 *      *  @param glyphs returns the corresponding glyph IDs for each character.
 *      */
 *     void unicharsToGlyphs(SkSpan<const SkUnichar> unis, SkSpan<SkGlyphID> glyphs) const;
 *
 *     size_t textToGlyphs(const void* text, size_t byteLength, SkTextEncoding encoding,
 *                         SkSpan<SkGlyphID> glyphs) const;
 *
 *     /**
 *      *  Return the glyphID that corresponds to the specified unicode code-point
 *      *  (in UTF32 encoding). If the unichar is not supported, returns 0.
 *      *
 *      *  This is a short-cut for calling unicharsToGlyphs().
 *      */
 *     SkGlyphID unicharToGlyph(SkUnichar unichar) const;
 *
 *     /**
 *      *  Return the number of glyphs in the typeface.
 *      */
 *     int countGlyphs() const;
 *
 *     // Table getters -- may fail if the underlying font format is not organized
 *     // as 4-byte tables.
 *
 *     /** Return the number of tables in the font. */
 *     int countTables() const;
 *
 *     /** Copy into tags[] (allocated by the caller) the list of table tags in
 *      *  the font, and return the number. This will be the same as CountTables()
 *      *  or 0 if an error occured. If tags is empty, this only returns the count
 *      *  (the same as calling countTables()).
 *      */
 *     int readTableTags(SkSpan<SkFontTableTag> tags) const;
 *
 *     /** Given a table tag, return the size of its contents, or 0 if not present
 *      */
 *     size_t getTableSize(SkFontTableTag) const;
 *
 *     /** Copy the contents of a table into data (allocated by the caller). Note
 *      *  that the contents of the table will be in their native endian order
 *      *  (which for most truetype tables is big endian). If the table tag is
 *      *  not found, or there is an error copying the data, then 0 is returned.
 *      *  If this happens, it is possible that some or all of the memory pointed
 *      *  to by data may have been written to, even though an error has occured.
 *      *
 *      *  @param tag  The table tag whose contents are to be copied
 *      *  @param offset The offset in bytes into the table's contents where the
 *      *  copy should start from.
 *      *  @param length The number of bytes, starting at offset, of table data
 *      *  to copy.
 *      *  @param data storage address where the table contents are copied to
 *      *  @return the number of bytes actually copied into data. If offset+length
 *      *  exceeds the table's size, then only the bytes up to the table's
 *      *  size are actually copied, and this is the value returned. If
 *      *  offset > the table's size, or tag is not a valid table,
 *      *  then 0 is returned.
 *      */
 *     size_t getTableData(SkFontTableTag tag, size_t offset, size_t length,
 *                         void* data) const;
 *
 *     /**
 *      *  Return an immutable copy of the requested font table, or nullptr if that table was
 *      *  not found. This can sometimes be faster than calling getTableData() twice: once to find
 *      *  the length, and then again to copy the data.
 *      *
 *      *  @param tag  The table tag whose contents are to be copied
 *      *  @return an immutable copy of the table's data, or nullptr.
 *      */
 *     sk_sp<SkData> copyTableData(SkFontTableTag tag) const;
 *
 *     /**
 *      *  Return the units-per-em value for this typeface, or zero if there is an
 *      *  error.
 *      */
 *     int getUnitsPerEm() const;
 *
 *     /**
 *      *  Given a run of glyphs, return the associated horizontal adjustments.
 *      *  Adjustments are in "design units", which are integers relative to the
 *      *  typeface's units per em (see getUnitsPerEm).
 *      *
 *      *  Some typefaces are known to never support kerning. Calling this method
 *      *  with empty spans (e.g. getKerningPairAdustments({}, {})) returns
 *      *  a boolean indicating if the typeface might support kerning. If it
 *      *  returns false, then it will always return false (no kerning) for all
 *      *  possible glyph runs. If it returns true, then it *may* return true for
 *      *  some glyph runs.
 *      *
 *      *  If the method returns true, and there are 1 or more glyphs in the span, then
 *      *  this will return in adjustments N values,
 *      *  where N = min(glyphs.size() - 1, adjustments.size()).
 *
 *      *  If the method returns false, then no kerning should be applied, and the adjustments
 *      *  array will be in an undefined state (possibly some values may have been
 *      *  written, but none of them should be interpreted as valid values).
 *      */
 *     bool getKerningPairAdjustments(SkSpan<const SkGlyphID> glyphs,
 *                                    SkSpan<int32_t> adjustments) const;
 *
 *     struct LocalizedString {
 *         SkString fString;
 *         SkString fLanguage;
 *     };
 *     class LocalizedStrings {
 *     public:
 *         LocalizedStrings() = default;
 *         virtual ~LocalizedStrings() { }
 *         virtual bool next(LocalizedString* localizedString) = 0;
 *         void unref() { delete this; }
 *
 *     private:
 *         LocalizedStrings(const LocalizedStrings&) = delete;
 *         LocalizedStrings& operator=(const LocalizedStrings&) = delete;
 *     };
 *     /**
 *      *  Returns an iterator which will attempt to enumerate all of the
 *      *  family names specified by the font.
 *      *  It is the caller's responsibility to unref() the returned pointer.
 *      */
 *     LocalizedStrings* createFamilyNameIterator() const;
 *
 *     /**
 *      *  Return the family name for this typeface. It will always be returned
 *      *  encoded as UTF8, but the language of the name is whatever the host
 *      *  platform chooses.
 *      */
 *     void getFamilyName(SkString* name) const;
 *
 *     /**
 *      *  Return the PostScript name for this typeface.
 *      *  Value may change based on variation parameters.
 *      *  Returns false if no PostScript name is available.
 *      */
 *     bool getPostScriptName(SkString* name) const;
 *
 *     /**
 *      *  If the primary resource backing this typeface has a name (like a file
 *      *  path or URL) representable by unicode code points, the `resourceName`
 *      *  will be set. The primary purpose is as a user facing indication about
 *      *  where the data was obtained (which font file was used).
 *      *
 *      *  Returns the number of resources backing this typeface.
 *      *
 *      *  For local font collections resource name will often be a file path. The
 *      *  file path may or may not exist. If it does exist, using it to create an
 *      *  SkTypeface may or may not create a similar SkTypeface to this one.
 *      */
 *     int getResourceName(SkString* resourceName) const;
 *
 *     /**
 *      *  Return a stream for the contents of the font data, or NULL on failure.
 *      *  If ttcIndex is not null, it is set to the TrueTypeCollection index
 *      *  of this typeface within the stream, or 0 if the stream is not a
 *      *  collection.
 *      *  The caller is responsible for deleting the stream.
 *      */
 *     std::unique_ptr<SkStreamAsset> openStream(int* ttcIndex) const;
 *
 *     /**
 *      * Return a stream for the contents of the font data.
 *      * Returns nullptr on failure or if the font data isn't already available in stream form.
 *      * Use when the stream can be used opportunistically but the calling code would prefer
 *      * to fall back to table access if creating the stream would be expensive.
 *      * Otherwise acts the same as openStream.
 *      */
 *     std::unique_ptr<SkStreamAsset> openExistingStream(int* ttcIndex) const;
 *
 *     /**
 *      *  Return a scalercontext for the given descriptor. It may return a
 *      *  stub scalercontext that will not crash, but will draw nothing.
 *      */
 *     std::unique_ptr<SkScalerContext> createScalerContext(const SkScalerContextEffects&,
 *                                                          const SkDescriptor*) const;
 *
 *     /**
 *      *  Return a rectangle (scaled to 1-pt) that represents the union of the bounds of all
 *      *  of the glyphs, but each one positioned at (0,). This may be conservatively large, and
 *      *  will not take into account any hinting or other size-specific adjustments.
 *      */
 *     SkRect getBounds() const;
 *
 *     // PRIVATE / EXPERIMENTAL -- do not call
 *     void filterRec(SkScalerContextRec* rec) const {
 *         this->onFilterRec(rec);
 *     }
 *     // PRIVATE / EXPERIMENTAL -- do not call
 *     void getFontDescriptor(SkFontDescriptor* desc, bool* isLocal) const {
 *         this->onGetFontDescriptor(desc, isLocal);
 *     }
 *     // PRIVATE / EXPERIMENTAL -- do not call
 *     void* internal_private_getCTFontRef() const {
 *         return this->onGetCTFontRef();
 *     }
 *
 *     /* Skia reserves all tags that begin with a lower case letter and 0 */
 *     using FactoryId = SkFourByteTag;
 *     static void Register(
 *             FactoryId id,
 *             sk_sp<SkTypeface> (*make)(std::unique_ptr<SkStreamAsset>, const SkFontArguments&));
 *
 * #ifdef SK_SUPPORT_UNSPANNED_APIS
 * public:
 *     int getVariationDesignPosition(SkFontArguments::VariationPosition::Coordinate coordinates[],
 *                                    int count) const {
 *         return this->getVariationDesignPosition({coordinates, count});
 *     }
 *     int getVariationDesignParameters(SkFontParameters::Variation::Axis parameters[],
 *                                      int count) const {
 *         return this->getVariationDesignParameters({parameters, count});
 *     }
 *     void unicharsToGlyphs(const SkUnichar unis[], int count, SkGlyphID glyphs[]) const {
 *         this->unicharsToGlyphs({unis, count}, {glyphs, count});
 *     }
 *     int textToGlyphs(const void* text, size_t byteLength, SkTextEncoding encoding,
 *                      SkGlyphID glyphs[], int maxGlyphCount) const {
 *         return (int)this->textToGlyphs(text, byteLength, encoding, {glyphs, maxGlyphCount});
 *     }
 *     int getTableTags(SkFontTableTag tags[]) const {
 *         const size_t count = tags ? MAX_REASONABLE_TABLE_COUNT : 0;
 *         return this->readTableTags({tags, count});
 *     }
 *     bool getKerningPairAdjustments(const SkGlyphID glyphs[], int count,
 *                                    int32_t adjustments[]) const {
 *         return this->getKerningPairAdjustments({glyphs, count}, {adjustments, count});
 *     }
 * #endif
 *
 * protected:
 *     // needed until onGetTableTags() is updated to take a span
 *     enum { MAX_REASONABLE_TABLE_COUNT = (1 << 16) - 1 };
 *
 *     explicit SkTypeface(const SkFontStyle& style, bool isFixedPitch = false);
 *     ~SkTypeface() override;
 *
 *     virtual sk_sp<SkTypeface> onMakeClone(const SkFontArguments&) const = 0;
 *
 *     /** Sets the fixedPitch bit. If used, must be called in the constructor. */
 *     void setIsFixedPitch(bool isFixedPitch) { fIsFixedPitch = isFixedPitch; }
 *     /** Sets the font style. If used, must be called in the constructor. */
 *     void setFontStyle(SkFontStyle style) { fStyle = style; }
 *
 *     virtual SkFontStyle onGetFontStyle() const; // TODO: = 0;
 *
 *     virtual bool onGetFixedPitch() const; // TODO: = 0;
 *
 *     // Must return a valid scaler context. It can not return nullptr.
 *     virtual std::unique_ptr<SkScalerContext> onCreateScalerContext(
 *         const SkScalerContextEffects&, const SkDescriptor*) const = 0;
 *     virtual std::unique_ptr<SkScalerContext> onCreateScalerContextAsProxyTypeface
 *         (const SkScalerContextEffects&, const SkDescriptor*, SkTypeface* proxyTypeface) const;
 *     virtual void onFilterRec(SkScalerContextRec*) const = 0;
 *     friend class SkScalerContext;  // onFilterRec
 *
 *     //  Subclasses *must* override this method to work with the PDF backend.
 *     virtual std::unique_ptr<SkAdvancedTypefaceMetrics> onGetAdvancedMetrics() const = 0;
 *     // For type1 postscript fonts only, set the glyph names for each glyph.
 *     // destination array is non-null, and points to an array of size this->countGlyphs().
 *     // Backends that do not suport type1 fonts should not override.
 *     virtual void getPostScriptGlyphNames(SkString*) const = 0;
 *
 *     // The mapping from glyph to Unicode; array indices are glyph ids.
 *     // For each glyph, give the default Unicode value, if it exists.
 *     // dstArray is non-null, and points to an array of size this->countGlyphs().
 *     virtual void getGlyphToUnicodeMap(SkSpan<SkUnichar> dstArray) const = 0;
 *
 *     virtual std::unique_ptr<SkStreamAsset> onOpenStream(int* ttcIndex) const = 0;
 *
 *     virtual std::unique_ptr<SkStreamAsset> onOpenExistingStream(int* ttcIndex) const;
 *
 *     virtual bool onGlyphMaskNeedsCurrentColor() const = 0;
 *
 *     virtual int onGetVariationDesignPosition(
 *                                  SkSpan<SkFontArguments::VariationPosition::Coordinate>) const = 0;
 *
 *     virtual int onGetVariationDesignParameters(SkSpan<SkFontParameters::Variation::Axis>) const = 0;
 *
 *     virtual void onGetFontDescriptor(SkFontDescriptor*, bool* isLocal) const = 0;
 *
 *     virtual void onCharsToGlyphs(SkSpan<const SkUnichar>, SkSpan<SkGlyphID>) const = 0;
 *     virtual int onCountGlyphs() const = 0;
 *
 *     virtual int onGetUPEM() const = 0;
 *     virtual bool onGetKerningPairAdjustments(SkSpan<const SkGlyphID>,
 *                                              SkSpan<int32_t> adjustments) const;
 *
 *     /** Returns the family name of the typeface as known by its font manager.
 *      *  This name may or may not be produced by the family name iterator.
 *      */
 *     virtual void onGetFamilyName(SkString* familyName) const = 0;
 *     virtual bool onGetPostScriptName(SkString*) const = 0;
 *     virtual int onGetResourceName(SkString* resourceName) const; // TODO: = 0;
 *
 *     /** Returns an iterator over the family names in the font. */
 *     virtual LocalizedStrings* onCreateFamilyNameIterator() const = 0;
 *
 *     virtual int onGetTableTags(SkSpan<SkFontTableTag>) const = 0;
 *     virtual size_t onGetTableData(SkFontTableTag, size_t offset,
 *                                   size_t length, void* data) const = 0;
 *     virtual sk_sp<SkData> onCopyTableData(SkFontTableTag) const;
 *
 *     virtual bool onComputeBounds(SkRect*) const;
 *
 *     virtual void* onGetCTFontRef() const { return nullptr; }
 *
 * private:
 *     /** Returns true if the typeface's glyph masks may refer to the foreground
 *      *  paint foreground color. This is needed to determine caching requirements. Usually true for
 *      *  typefaces that contain a COLR table.
 *      */
 *     bool glyphMaskNeedsCurrentColor() const;
 *     friend class SkStrikeServerImpl;  // glyphMaskNeedsCurrentColor
 *     friend class SkTypefaceProxyPrototype;  // glyphMaskNeedsCurrentColor
 *
 *     /** Retrieve detailed typeface metrics.  Used by the PDF backend.  */
 *     std::unique_ptr<SkAdvancedTypefaceMetrics> getAdvancedMetrics() const;
 *     friend class SkRandomTypeface;   // getAdvancedMetrics
 *     friend class SkPDFFont;          // getAdvancedMetrics
 *     friend class SkTypeface_proxy;
 *     friend class SkFontPriv;         // getGlyphToUnicodeMap
 *     friend void TestSkTypefaceGlyphToUnicodeMap(SkTypeface&, SkSpan<SkUnichar>);
 *
 * private:
 *     SkTypefaceID        fUniqueID;
 *     SkFontStyle         fStyle;
 *     mutable SkRect      fBounds;
 *     mutable SkOnce      fBoundsOnce;
 *     bool                fIsFixedPitch;
 *
 *     using INHERITED = SkWeakRefCnt;
 * }
 * ```
 */
public abstract class SkTypeface public constructor(
  style: SkFontStyle,
  isFixedPitch: Boolean = TODO(),
) : SkWeakRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * SkTypefaceID        fUniqueID
   * ```
   */
  private var fUniqueID: SkTypefaceID = TODO("Initialize fUniqueID")

  /**
   * C++ original:
   * ```cpp
   * SkFontStyle         fStyle
   * ```
   */
  private var fStyle: Int = TODO("Initialize fStyle")

  /**
   * C++ original:
   * ```cpp
   * mutable SkRect      fBounds
   * ```
   */
  private var fBounds: Int = TODO("Initialize fBounds")

  /**
   * C++ original:
   * ```cpp
   * mutable SkOnce      fBoundsOnce
   * ```
   */
  private var fBoundsOnce: Int = TODO("Initialize fBoundsOnce")

  /**
   * C++ original:
   * ```cpp
   * bool                fIsFixedPitch
   * ```
   */
  private var fIsFixedPitch: Boolean = TODO("Initialize fIsFixedPitch")

  /**
   * C++ original:
   * ```cpp
   * SkFontStyle SkTypeface::fontStyle() const {
   *     return this->onGetFontStyle();
   * }
   * ```
   */
  public fun fontStyle(): Int {
    TODO("Implement fontStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTypeface::isBold() const {
   *     return this->onGetFontStyle().weight() >= SkFontStyle::kSemiBold_Weight;
   * }
   * ```
   */
  public fun isBold(): Boolean {
    TODO("Implement isBold")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTypeface::isItalic() const {
   *     return this->onGetFontStyle().slant() != SkFontStyle::kUpright_Slant;
   * }
   * ```
   */
  public fun isItalic(): Boolean {
    TODO("Implement isItalic")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTypeface::isFixedPitch() const {
   *     return this->onGetFixedPitch();
   * }
   * ```
   */
  public fun isFixedPitch(): Boolean {
    TODO("Implement isFixedPitch")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkTypeface::getVariationDesignPosition(
   *         SkSpan<SkFontArguments::VariationPosition::Coordinate> coordinates) const
   * {
   *     return this->onGetVariationDesignPosition(coordinates);
   * }
   * ```
   */
  public fun getVariationDesignPosition(coordinates: SkSpan<SkFontArguments.VariationPosition.Coordinate>): Int {
    TODO("Implement getVariationDesignPosition")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkTypeface::getVariationDesignParameters(
   *         SkSpan<SkFontParameters::Variation::Axis> parameters) const
   * {
   *     return this->onGetVariationDesignParameters(parameters);
   * }
   * ```
   */
  public fun getVariationDesignParameters(parameters: SkSpan<SkFontParameters.Variation.Axis>): Int {
    TODO("Implement getVariationDesignParameters")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTypefaceID uniqueID() const { return fUniqueID; }
   * ```
   */
  public fun uniqueID(): SkTypefaceID {
    TODO("Implement uniqueID")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> SkTypeface::makeClone(const SkFontArguments& args) const {
   *     return this->onMakeClone(args);
   * }
   * ```
   */
  public fun makeClone(args: SkFontArguments): Int {
    TODO("Implement makeClone")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTypeface::serialize(SkWStream* wstream, SerializeBehavior behavior) const {
   *     bool isLocalData = false;
   *     SkFontDescriptor desc;
   *     this->onGetFontDescriptor(&desc, &isLocalData);
   *     if (desc.getFactoryId() == 0) {
   *         SkDEBUGF("Factory was not set for %s.\n", desc.getFamilyName());
   *     }
   *
   *     bool shouldSerializeData = false;
   *     switch (behavior) {
   *         case SerializeBehavior::kDoIncludeData:      shouldSerializeData = true;        break;
   *         case SerializeBehavior::kDontIncludeData:    shouldSerializeData = false;       break;
   *         case SerializeBehavior::kIncludeDataIfLocal: shouldSerializeData = isLocalData; break;
   *     }
   *
   *     if (shouldSerializeData) {
   *         int index;
   *         desc.setStream(this->openStream(&index));
   *         if (desc.hasStream()) {
   *             desc.setCollectionIndex(index);
   *         }
   *
   *         int numAxes = this->getVariationDesignPosition({});
   *         if (0 < numAxes) {
   *             numAxes = this->getVariationDesignPosition({desc.setVariationCoordinates(numAxes),
   *                                                         (size_t)numAxes});
   *             if (numAxes <= 0) {
   *                 desc.setVariationCoordinates(0);
   *             }
   *         }
   *     }
   *     desc.serialize(wstream);
   * }
   * ```
   */
  public fun serialize(wstream: SkWStream?, behavior: SerializeBehavior = TODO()) {
    TODO("Implement serialize")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> SkTypeface::serialize(SerializeBehavior behavior) const {
   *     SkDynamicMemoryWStream stream;
   *     this->serialize(&stream, behavior);
   *     return stream.detachAsData();
   * }
   * ```
   */
  public fun serialize(behavior: SerializeBehavior = TODO()): Int {
    TODO("Implement serialize")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTypeface::unicharsToGlyphs(SkSpan<const SkUnichar> uni, SkSpan<SkGlyphID> glyphs) const {
   *     if (const size_t n = std::min(uni.size(), glyphs.size())) {
   *         this->onCharsToGlyphs(uni.first(n), glyphs.first(n));
   *     }
   * }
   * ```
   */
  public fun unicharsToGlyphs(unis: SkSpan<SkUnichar>, glyphs: SkSpan<SkGlyphID>) {
    TODO("Implement unicharsToGlyphs")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkTypeface::textToGlyphs(const void* text, size_t byteLength, SkTextEncoding encoding,
   *                                 SkSpan<SkGlyphID> glyphs) const {
   *     if (0 == byteLength) {
   *         return 0;
   *     }
   *
   *     SkASSERT(text);
   *
   *     size_t count = SkFontPriv::CountTextElements(text, byteLength, encoding);
   *     if (count > glyphs.size()) {
   *         return count;
   *     }
   *
   *     if (encoding == SkTextEncoding::kGlyphID) {
   *         memcpy(glyphs.data(), text, count << 1);
   *         return count;
   *     }
   *
   *     SkConvertToUTF32 storage;
   *     const SkUnichar* uni = storage.convert(text, byteLength, encoding);
   *
   *     this->unicharsToGlyphs({uni, count}, glyphs);
   *     return count;
   * }
   * ```
   */
  public fun textToGlyphs(
    text: Unit?,
    byteLength: ULong,
    encoding: SkTextEncoding,
    glyphs: SkSpan<SkGlyphID>,
  ): ULong {
    TODO("Implement textToGlyphs")
  }

  /**
   * C++ original:
   * ```cpp
   * SkGlyphID SkTypeface::unicharToGlyph(SkUnichar uni) const {
   *     SkGlyphID glyphs[1] = { 0 };
   *     this->onCharsToGlyphs({&uni, 1}, glyphs);
   *     return glyphs[0];
   * }
   * ```
   */
  public fun unicharToGlyph(unichar: SkUnichar): Int {
    TODO("Implement unicharToGlyph")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkTypeface::countGlyphs() const {
   *     return this->onCountGlyphs();
   * }
   * ```
   */
  public fun countGlyphs(): Int {
    TODO("Implement countGlyphs")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkTypeface::countTables() const {
   *     return this->onGetTableTags({});
   * }
   * ```
   */
  public fun countTables(): Int {
    TODO("Implement countTables")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkTypeface::readTableTags(SkSpan<SkFontTableTag> tags) const {
   *     return this->onGetTableTags(tags);
   * }
   * ```
   */
  public fun readTableTags(tags: SkSpan<SkFontTableTag>): Int {
    TODO("Implement readTableTags")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkTypeface::getTableSize(SkFontTableTag tag) const {
   *     return this->onGetTableData(tag, 0, ~0U, nullptr);
   * }
   * ```
   */
  public fun getTableSize(tag: SkFontTableTag): ULong {
    TODO("Implement getTableSize")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkTypeface::getTableData(SkFontTableTag tag, size_t offset, size_t length,
   *                                 void* data) const {
   *     return this->onGetTableData(tag, offset, length, data);
   * }
   * ```
   */
  public fun getTableData(
    tag: SkFontTableTag,
    offset: ULong,
    length: ULong,
    `data`: Unit?,
  ): ULong {
    TODO("Implement getTableData")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> SkTypeface::copyTableData(SkFontTableTag tag) const {
   *     return this->onCopyTableData(tag);
   * }
   * ```
   */
  public fun copyTableData(tag: SkFontTableTag): Int {
    TODO("Implement copyTableData")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkTypeface::getUnitsPerEm() const {
   *     // should we try to cache this in the base-class?
   *     return this->onGetUPEM();
   * }
   * ```
   */
  public fun getUnitsPerEm(): Int {
    TODO("Implement getUnitsPerEm")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTypeface::getKerningPairAdjustments(SkSpan<const SkGlyphID> glyphs,
   *                                            SkSpan<int32_t> adjustments) const {
   *     // We need glyphs.size() == adjustments.size() + 1
   *     // unless either is emptyish, in which case we still call the virtual, just
   *     // to get the boolean result.
   *     if (glyphs.size() <= 1 || adjustments.empty()) {
   *         return this->onGetKerningPairAdjustments({}, {});   // just return the bool
   *     }
   *
   *     const size_t n = std::min(glyphs.size() - 1, adjustments.size());
   *     return this->onGetKerningPairAdjustments(glyphs.first(n + 1), adjustments.first(n));
   * }
   * ```
   */
  public fun getKerningPairAdjustments(glyphs: SkSpan<SkGlyphID>, adjustments: SkSpan<Int>): Boolean {
    TODO("Implement getKerningPairAdjustments")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTypeface::LocalizedStrings* SkTypeface::createFamilyNameIterator() const {
   *     return this->onCreateFamilyNameIterator();
   * }
   * ```
   */
  private fun createFamilyNameIterator(): LocalizedStrings {
    TODO("Implement createFamilyNameIterator")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTypeface::getFamilyName(SkString* name) const {
   *     SkASSERT(name);
   *     this->onGetFamilyName(name);
   * }
   * ```
   */
  private fun getFamilyName(name: String?) {
    TODO("Implement getFamilyName")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTypeface::getPostScriptName(SkString* name) const {
   *     return this->onGetPostScriptName(name);
   * }
   * ```
   */
  private fun getPostScriptName(name: String?): Boolean {
    TODO("Implement getPostScriptName")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkTypeface::getResourceName(SkString* resourceName) const {
   *     return this->onGetResourceName(resourceName);
   * }
   * ```
   */
  private fun getResourceName(resourceName: String?): Int {
    TODO("Implement getResourceName")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkStreamAsset> SkTypeface::openStream(int* ttcIndex) const {
   *     int ttcIndexStorage;
   *     if (nullptr == ttcIndex) {
   *         // So our subclasses don't need to check for null param
   *         ttcIndex = &ttcIndexStorage;
   *     }
   *     return this->onOpenStream(ttcIndex);
   * }
   * ```
   */
  private fun openStream(ttcIndex: Int?): Int {
    TODO("Implement openStream")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkStreamAsset> SkTypeface::openExistingStream(int* ttcIndex) const {
   *     int ttcIndexStorage;
   *     if (nullptr == ttcIndex) {
   *         // So our subclasses don't need to check for null param
   *         ttcIndex = &ttcIndexStorage;
   *     }
   *     return this->onOpenExistingStream(ttcIndex);
   * }
   * ```
   */
  private fun openExistingStream(ttcIndex: Int?): Int {
    TODO("Implement openExistingStream")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkScalerContext> SkTypeface::createScalerContext(
   *         const SkScalerContextEffects& effects, const SkDescriptor* desc) const {
   *     std::unique_ptr<SkScalerContext> scalerContext = this->onCreateScalerContext(effects, desc);
   *     SkASSERT(scalerContext);
   *     return scalerContext;
   * }
   * ```
   */
  private fun createScalerContext(effects: SkScalerContextEffects, desc: SkDescriptor?): Int {
    TODO("Implement createScalerContext")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect SkTypeface::getBounds() const {
   *     fBoundsOnce([this] {
   *         if (!this->onComputeBounds(&fBounds)) {
   *             fBounds.setEmpty();
   *         }
   *     });
   *     return fBounds;
   * }
   * ```
   */
  private fun getBounds(): Int {
    TODO("Implement getBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void filterRec(SkScalerContextRec* rec) const {
   *         this->onFilterRec(rec);
   *     }
   * ```
   */
  private fun filterRec(rec: SkScalerContextRec?) {
    TODO("Implement filterRec")
  }

  /**
   * C++ original:
   * ```cpp
   * void getFontDescriptor(SkFontDescriptor* desc, bool* isLocal) const {
   *         this->onGetFontDescriptor(desc, isLocal);
   *     }
   * ```
   */
  private fun getFontDescriptor(desc: SkFontDescriptor?, isLocal: Boolean?) {
    TODO("Implement getFontDescriptor")
  }

  /**
   * C++ original:
   * ```cpp
   * void* internal_private_getCTFontRef() const {
   *         return this->onGetCTFontRef();
   *     }
   * ```
   */
  private fun internalPrivateGetCTFontRef() {
    TODO("Implement internalPrivateGetCTFontRef")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkTypeface> onMakeClone(const SkFontArguments&) const = 0
   * ```
   */
  protected abstract fun onMakeClone(param0: SkFontArguments): Int

  /**
   * C++ original:
   * ```cpp
   * void setIsFixedPitch(bool isFixedPitch) { fIsFixedPitch = isFixedPitch; }
   * ```
   */
  protected fun setIsFixedPitch(isFixedPitch: Boolean) {
    TODO("Implement setIsFixedPitch")
  }

  /**
   * C++ original:
   * ```cpp
   * void setFontStyle(SkFontStyle style) { fStyle = style; }
   * ```
   */
  protected fun setFontStyle(style: SkFontStyle) {
    TODO("Implement setFontStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFontStyle SkTypeface::onGetFontStyle() const {
   *     return fStyle;
   * }
   * ```
   */
  protected open fun onGetFontStyle(): Int {
    TODO("Implement onGetFontStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTypeface::onGetFixedPitch() const {
   *     return fIsFixedPitch;
   * }
   * ```
   */
  protected open fun onGetFixedPitch(): Boolean {
    TODO("Implement onGetFixedPitch")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual std::unique_ptr<SkScalerContext> onCreateScalerContext(
   *         const SkScalerContextEffects&, const SkDescriptor*) const = 0
   * ```
   */
  protected abstract fun onCreateScalerContext(param0: SkScalerContextEffects, param1: SkDescriptor?): Int

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkScalerContext> SkTypeface::onCreateScalerContextAsProxyTypeface
   *         (const SkScalerContextEffects&,
   *          const SkDescriptor*,
   *          SkTypeface*) const {
   *     SK_ABORT("Not implemented.");
   * }
   * ```
   */
  protected open fun onCreateScalerContextAsProxyTypeface(
    param0: SkScalerContextEffects,
    param1: SkDescriptor?,
    proxyTypeface: SkTypeface?,
  ): Int {
    TODO("Implement onCreateScalerContextAsProxyTypeface")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void onFilterRec(SkScalerContextRec*) const = 0
   * ```
   */
  protected abstract fun onFilterRec(param0: SkScalerContextRec?)

  /**
   * C++ original:
   * ```cpp
   * virtual std::unique_ptr<SkAdvancedTypefaceMetrics> onGetAdvancedMetrics() const = 0
   * ```
   */
  protected abstract fun onGetAdvancedMetrics(): Int

  /**
   * C++ original:
   * ```cpp
   * virtual void getPostScriptGlyphNames(SkString*) const = 0
   * ```
   */
  protected abstract fun getPostScriptGlyphNames(param0: String?)

  /**
   * C++ original:
   * ```cpp
   * void SkTypeface::getGlyphToUnicodeMap(SkSpan<SkUnichar> dst) const {
   *     sk_bzero(dst.data(), dst.size_bytes());
   * }
   * ```
   */
  protected abstract fun getGlyphToUnicodeMap(dstArray: SkSpan<SkUnichar>)

  /**
   * C++ original:
   * ```cpp
   * virtual std::unique_ptr<SkStreamAsset> onOpenStream(int* ttcIndex) const = 0
   * ```
   */
  protected abstract fun onOpenStream(ttcIndex: Int?): Int

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkStreamAsset> SkTypeface::onOpenExistingStream(int* ttcIndex) const {
   *     return this->onOpenStream(ttcIndex);
   * }
   * ```
   */
  protected open fun onOpenExistingStream(ttcIndex: Int?): Int {
    TODO("Implement onOpenExistingStream")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool onGlyphMaskNeedsCurrentColor() const = 0
   * ```
   */
  protected abstract fun onGlyphMaskNeedsCurrentColor(): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual int onGetVariationDesignPosition(
   *                                  SkSpan<SkFontArguments::VariationPosition::Coordinate>) const = 0
   * ```
   */
  protected abstract fun onGetVariationDesignPosition(param0: SkSpan<SkFontArguments.VariationPosition.Coordinate>): Int

  /**
   * C++ original:
   * ```cpp
   * virtual int onGetVariationDesignParameters(SkSpan<SkFontParameters::Variation::Axis>) const = 0
   * ```
   */
  protected abstract fun onGetVariationDesignParameters(param0: SkSpan<SkFontParameters.Variation.Axis>): Int

  /**
   * C++ original:
   * ```cpp
   * virtual void onGetFontDescriptor(SkFontDescriptor*, bool* isLocal) const = 0
   * ```
   */
  protected abstract fun onGetFontDescriptor(param0: SkFontDescriptor?, isLocal: Boolean?)

  /**
   * C++ original:
   * ```cpp
   * virtual void onCharsToGlyphs(SkSpan<const SkUnichar>, SkSpan<SkGlyphID>) const = 0
   * ```
   */
  protected abstract fun onCharsToGlyphs(param0: SkSpan<SkUnichar>, param1: SkSpan<SkGlyphID>)

  /**
   * C++ original:
   * ```cpp
   * virtual int onCountGlyphs() const = 0
   * ```
   */
  protected abstract fun onCountGlyphs(): Int

  /**
   * C++ original:
   * ```cpp
   * virtual int onGetUPEM() const = 0
   * ```
   */
  protected abstract fun onGetUPEM(): Int

  /**
   * C++ original:
   * ```cpp
   * bool SkTypeface::onGetKerningPairAdjustments(SkSpan<const SkGlyphID>, SkSpan<int32_t> adj) const {
   *     return false;
   * }
   * ```
   */
  protected open fun onGetKerningPairAdjustments(param0: SkSpan<SkGlyphID>, adjustments: SkSpan<Int>): Boolean {
    TODO("Implement onGetKerningPairAdjustments")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void onGetFamilyName(SkString* familyName) const = 0
   * ```
   */
  protected abstract fun onGetFamilyName(familyName: String?)

  /**
   * C++ original:
   * ```cpp
   * virtual bool onGetPostScriptName(SkString*) const = 0
   * ```
   */
  protected abstract fun onGetPostScriptName(param0: String?): Boolean

  /**
   * C++ original:
   * ```cpp
   * int SkTypeface::onGetResourceName(SkString* resourceName) const {
   *     return 0;
   * }
   * ```
   */
  protected open fun onGetResourceName(resourceName: String?): Int {
    TODO("Implement onGetResourceName")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual LocalizedStrings* onCreateFamilyNameIterator() const = 0
   * ```
   */
  protected abstract fun onCreateFamilyNameIterator(): LocalizedStrings

  /**
   * C++ original:
   * ```cpp
   * virtual int onGetTableTags(SkSpan<SkFontTableTag>) const = 0
   * ```
   */
  protected abstract fun onGetTableTags(param0: SkSpan<SkFontTableTag>): Int

  /**
   * C++ original:
   * ```cpp
   * virtual size_t onGetTableData(SkFontTableTag, size_t offset,
   *                                   size_t length, void* data) const = 0
   * ```
   */
  protected abstract fun onGetTableData(
    param0: SkFontTableTag,
    offset: ULong,
    length: ULong,
    `data`: Unit?,
  ): ULong

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> SkTypeface::onCopyTableData(SkFontTableTag tag) const {
   *     size_t size = this->getTableSize(tag);
   *     if (size) {
   *         sk_sp<SkData> data = SkData::MakeUninitialized(size);
   *         (void)this->getTableData(tag, 0, size, data->writable_data());
   *         return data;
   *     }
   *     return nullptr;
   * }
   * ```
   */
  protected open fun onCopyTableData(tag: SkFontTableTag): Int {
    TODO("Implement onCopyTableData")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTypeface::onComputeBounds(SkRect* bounds) const {
   *     // we use a big size to ensure lots of significant bits from the scalercontext.
   *     // then we scale back down to return our final answer (at 1-pt)
   *     const SkScalar textSize = 2048;
   *     const SkScalar invTextSize = 1 / textSize;
   *
   *     SkFont font;
   *     font.setTypeface(sk_ref_sp(const_cast<SkTypeface*>(this)));
   *     font.setSize(textSize);
   *     font.setLinearMetrics(true);
   *
   *     SkScalerContextRec rec;
   *     SkScalerContextEffects effects;
   *
   *     SkScalerContext::MakeRecAndEffectsFromFont(font, &rec, &effects);
   *
   *     SkAutoDescriptor ad;
   *     SkScalerContextEffects noeffects;
   *     SkScalerContext::AutoDescriptorGivenRecAndEffects(rec, noeffects, &ad);
   *
   *     std::unique_ptr<SkScalerContext> ctx = this->createScalerContext(noeffects, ad.getDesc());
   *
   *     SkFontMetrics fm;
   *     ctx->getFontMetrics(&fm);
   *     if (!fm.hasBounds()) {
   *         return false;
   *     }
   *     bounds->setLTRB(fm.fXMin * invTextSize, fm.fTop * invTextSize,
   *                     fm.fXMax * invTextSize, fm.fBottom * invTextSize);
   *     return true;
   * }
   * ```
   */
  protected open fun onComputeBounds(bounds: SkRect?): Boolean {
    TODO("Implement onComputeBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void* onGetCTFontRef() const { return nullptr; }
   * ```
   */
  protected open fun onGetCTFontRef() {
    TODO("Implement onGetCTFontRef")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTypeface::glyphMaskNeedsCurrentColor() const {
   *     return this->onGlyphMaskNeedsCurrentColor();
   * }
   * ```
   */
  private fun glyphMaskNeedsCurrentColor(): Boolean {
    TODO("Implement glyphMaskNeedsCurrentColor")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkAdvancedTypefaceMetrics> SkTypeface::getAdvancedMetrics() const {
   *     std::unique_ptr<SkAdvancedTypefaceMetrics> result = this->onGetAdvancedMetrics();
   *     if (result && result->fPostScriptName.isEmpty()) {
   *         if (!this->getPostScriptName(&result->fPostScriptName)) {
   *             this->getFamilyName(&result->fPostScriptName);
   *         }
   *     }
   *     if (result && (result->fType == SkAdvancedTypefaceMetrics::kTrueType_Font ||
   *                    result->fType == SkAdvancedTypefaceMetrics::kCFF_Font)) {
   *         SkOTTableOS2::Version::V2::Type::Field fsType;
   *         constexpr SkFontTableTag os2Tag = SkTEndian_SwapBE32(SkOTTableOS2::TAG);
   *         constexpr size_t fsTypeOffset = offsetof(SkOTTableOS2::Version::V2, fsType);
   *         if (this->getTableData(os2Tag, fsTypeOffset, sizeof(fsType), &fsType) == sizeof(fsType)) {
   *             if (fsType.Bitmap || (fsType.Restricted && !(fsType.PreviewPrint || fsType.Editable))) {
   *                 result->fFlags |= SkAdvancedTypefaceMetrics::kNotEmbeddable_FontFlag;
   *             }
   *             if (fsType.NoSubsetting) {
   *                 result->fFlags |= SkAdvancedTypefaceMetrics::kNotSubsettable_FontFlag;
   *             }
   *         }
   *     }
   *     return result;
   * }
   * ```
   */
  private fun getAdvancedMetrics(): Int {
    TODO("Implement getAdvancedMetrics")
  }

  public data class LocalizedString public constructor(
    public var fString: Int,
    public var fLanguage: Int,
  )

  public abstract class LocalizedStrings public constructor() {
    public constructor(param0: undefined.LocalizedStrings) : this() {
      TODO("Implement constructor")
    }

    public abstract fun next(localizedString: undefined.LocalizedString?): Boolean

    public fun unref() {
      TODO("Implement unref")
    }

    private fun assign(param0: undefined.LocalizedStrings) {
      TODO("Implement assign")
    }
  }

  public enum class SerializeBehavior {
    kDoIncludeData,
    kDontIncludeData,
    kIncludeDataIfLocal,
  }

  public companion object {
    public val maxREASONABLETABLECOUNT: Int = TODO("Initialize maxREASONABLETABLECOUNT")

    /**
     * C++ original:
     * ```cpp
     * bool SkTypeface::Equal(const SkTypeface* facea, const SkTypeface* faceb) {
     *     if (facea == faceb) {
     *         return true;
     *     }
     *     if (!facea || !faceb) {
     *         return false;
     *     }
     *     return facea->uniqueID() == faceb->uniqueID();
     * }
     * ```
     */
    public fun equal(facea: SkTypeface?, faceb: SkTypeface?): Boolean {
      TODO("Implement equal")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkTypeface> SkTypeface::MakeEmpty() {
     *     return SkEmptyTypeface::Make();
     * }
     * ```
     */
    public fun makeEmpty(): Int {
      TODO("Implement makeEmpty")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkTypeface> SkTypeface::MakeDeserialize(SkStream* stream, sk_sp<SkFontMgr> lastResortMgr) {
     *     SkFontDescriptor desc;
     *     if (!SkFontDescriptor::Deserialize(stream, &desc)) {
     *         return nullptr;
     *     }
     *
     *     if (desc.hasStream()) {
     *         for (const DecoderProc& proc : *decoders()) {
     *             if (proc.id == desc.getFactoryId()) {
     *                 return proc.makeFromStream(desc.detachStream(), desc.getFontArguments());
     *             }
     *         }
     *
     *         [[maybe_unused]] FactoryId id = desc.getFactoryId();
     *         SkDEBUGF("Could not find factory %c%c%c%c for %s.\n",
     *                  (char)((id >> 24) & 0xFF),
     *                  (char)((id >> 16) & 0xFF),
     *                  (char)((id >> 8) & 0xFF),
     *                  (char)((id >> 0) & 0xFF),
     *                  desc.getFamilyName());
     *
     *         if (lastResortMgr) {
     *             // If we've gotten to here, we will try desperately to find something that might match
     *             // as a kind of last ditch effort to make something work (and maybe this SkFontMgr knows
     *             // something about the serialization and can look up the right thing by name anyway if
     *             // the user provides it).
     *             // Any time it is used the user will probably get the wrong glyphs drawn (and if they're
     *             // right it is totally by accident). But sometimes drawing something or getting lucky
     *             // while debugging is better than drawing nothing at all.
     *             sk_sp<SkTypeface> typeface = lastResortMgr->makeFromStream(desc.detachStream(),
     *                                                                        desc.getFontArguments());
     *             if (typeface) {
     *                 return typeface;
     *             }
     *         }
     *     }
     *     if (lastResortMgr) {
     *         return lastResortMgr->legacyMakeTypeface(desc.getFamilyName(), desc.getStyle());
     *     }
     *     return SkEmptyTypeface::Make();
     * }
     * ```
     */
    public fun makeDeserialize(stream: SkStream?, lastResortMgr: SkSp<SkFontMgr>): Int {
      TODO("Implement makeDeserialize")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkTypeface::Register(
     *             FactoryId id,
     *             sk_sp<SkTypeface> (*make)(std::unique_ptr<SkStreamAsset>, const SkFontArguments&)) {
     *     decoders()->push_back(DecoderProc{id, make});
     * }
     * ```
     */
    private fun register(id: FactoryId, param1: (unique_ptr<SkStreamAsset>, SkFontArguments) -> SkSp<SkTypeface>) {
      TODO("Implement register")
    }
  }
}
