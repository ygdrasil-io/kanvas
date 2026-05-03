package org.skia.modules

import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.String
import kotlin.UByte
import kotlin.UInt
import kotlin.ULong
import org.skia.core.SkFourByteTag
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontMgr
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkSp
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SKSHAPER_API SkShaper {
 * public:
 * #if !defined(SK_DISABLE_LEGACY_SKSHAPER_FUNCTIONS)
 *     static std::unique_ptr<SkShaper> MakePrimitive();
 *
 * #if defined(SK_SHAPER_HARFBUZZ_AVAILABLE)
 *     static std::unique_ptr<SkShaper> MakeShaperDrivenWrapper(sk_sp<SkFontMgr> fallback);
 *     static std::unique_ptr<SkShaper> MakeShapeThenWrap(sk_sp<SkFontMgr> fallback);
 *     static void PurgeHarfBuzzCache();
 * #endif
 *
 * #if defined(SK_SHAPER_CORETEXT_AVAILABLE)
 *     static std::unique_ptr<SkShaper> MakeCoreText();
 * #endif
 *
 *     static std::unique_ptr<SkShaper> Make(sk_sp<SkFontMgr> fallback = nullptr);
 *     static void PurgeCaches();
 * #endif  // !defined(SK_DISABLE_LEGACY_SKSHAPER_FUNCTIONS)
 *
 *     SkShaper();
 *     virtual ~SkShaper();
 *
 *     class RunIterator {
 *     public:
 *         virtual ~RunIterator() = default;
 *         /** Set state to that of current run and move iterator to end of that run. */
 *         virtual void consume() = 0;
 *         /** Offset to one past the last (utf8) element in the current run. */
 *         virtual size_t endOfCurrentRun() const = 0;
 *         /** Return true if consume should no longer be called. */
 *         virtual bool atEnd() const = 0;
 *     };
 *     class FontRunIterator : public RunIterator {
 *     public:
 *         virtual const SkFont& currentFont() const = 0;
 *     };
 *     class BiDiRunIterator : public RunIterator {
 *     public:
 *         /** The unicode bidi embedding level (even ltr, odd rtl) */
 *         virtual uint8_t currentLevel() const = 0;
 *     };
 *     class ScriptRunIterator : public RunIterator {
 *     public:
 *         /** Should be iso15924 codes. */
 *         virtual SkFourByteTag currentScript() const = 0;
 *     };
 *     class LanguageRunIterator : public RunIterator {
 *     public:
 *         /** Should be BCP-47, c locale names may also work. */
 *         virtual const char* currentLanguage() const = 0;
 *     };
 *     struct Feature {
 *         SkFourByteTag tag;
 *         uint32_t value;
 *         size_t start; // Offset to the start (utf8) element of the run.
 *         size_t end;   // Offset to one past the last (utf8) element of the run.
 *     };
 *
 * private:
 *     template <typename RunIteratorSubclass>
 *     class TrivialRunIterator : public RunIteratorSubclass {
 *     public:
 *         static_assert(std::is_base_of<RunIterator, RunIteratorSubclass>::value, "");
 *         TrivialRunIterator(size_t utf8Bytes) : fEnd(utf8Bytes), fAtEnd(fEnd == 0) {}
 *         void consume() override { SkASSERT(!fAtEnd); fAtEnd = true; }
 *         size_t endOfCurrentRun() const override { return fAtEnd ? fEnd : 0; }
 *         bool atEnd() const override { return fAtEnd; }
 *     private:
 *         size_t fEnd;
 *         bool fAtEnd;
 *     };
 *
 * public:
 *     static std::unique_ptr<FontRunIterator>
 *     MakeFontMgrRunIterator(const char* utf8, size_t utf8Bytes,
 *                            const SkFont& font, sk_sp<SkFontMgr> fallback);
 *     static std::unique_ptr<SkShaper::FontRunIterator>
 *     MakeFontMgrRunIterator(const char* utf8, size_t utf8Bytes,
 *                            const SkFont& font, sk_sp<SkFontMgr> fallback,
 *                            const char* requestName, SkFontStyle requestStyle,
 *                            const SkShaper::LanguageRunIterator*);
 *     class TrivialFontRunIterator : public TrivialRunIterator<FontRunIterator> {
 *     public:
 *         TrivialFontRunIterator(const SkFont& font, size_t utf8Bytes)
 *             : TrivialRunIterator(utf8Bytes), fFont(font) {}
 *         const SkFont& currentFont() const override { return fFont; }
 *     private:
 *         SkFont fFont;
 *     };
 *
 * #if !defined(SK_DISABLE_LEGACY_SKSHAPER_FUNCTIONS)
 *     static std::unique_ptr<BiDiRunIterator>
 *     MakeBiDiRunIterator(const char* utf8, size_t utf8Bytes, uint8_t bidiLevel);
 * #if defined(SK_SHAPER_UNICODE_AVAILABLE)
 *     static std::unique_ptr<BiDiRunIterator>
 *     MakeIcuBiDiRunIterator(const char* utf8, size_t utf8Bytes, uint8_t bidiLevel);
 * #endif  // defined(SK_SHAPER_UNICODE_AVAILABLE)
 * #endif  // !defined(SK_DISABLE_LEGACY_SKSHAPER_FUNCTIONS)
 *
 *     class TrivialBiDiRunIterator : public TrivialRunIterator<BiDiRunIterator> {
 *     public:
 *         TrivialBiDiRunIterator(uint8_t bidiLevel, size_t utf8Bytes)
 *             : TrivialRunIterator(utf8Bytes), fBidiLevel(bidiLevel) {}
 *         uint8_t currentLevel() const override { return fBidiLevel; }
 *     private:
 *         uint8_t fBidiLevel;
 *     };
 *
 * #if !defined(SK_DISABLE_LEGACY_SKSHAPER_FUNCTIONS)
 *     static std::unique_ptr<ScriptRunIterator>
 *     MakeScriptRunIterator(const char* utf8, size_t utf8Bytes, SkFourByteTag script);
 * #if defined(SK_SHAPER_HARFBUZZ_AVAILABLE)
 *     static std::unique_ptr<ScriptRunIterator>
 *     MakeSkUnicodeHbScriptRunIterator(const char* utf8, size_t utf8Bytes);
 *     static std::unique_ptr<ScriptRunIterator>
 *     MakeSkUnicodeHbScriptRunIterator(const char* utf8, size_t utf8Bytes, SkFourByteTag script);
 *     // Still used in some cases
 *     static std::unique_ptr<ScriptRunIterator>
 *     MakeHbIcuScriptRunIterator(const char* utf8, size_t utf8Bytes);
 * #endif  // defined(SK_SHAPER_HARFBUZZ_AVAILABLE)
 * #endif  // !defined(SK_DISABLE_LEGACY_SKSHAPER_FUNCTIONS)
 *
 *     class TrivialScriptRunIterator : public TrivialRunIterator<ScriptRunIterator> {
 *     public:
 *         TrivialScriptRunIterator(SkFourByteTag script, size_t utf8Bytes)
 *             : TrivialRunIterator(utf8Bytes), fScript(script) {}
 *         SkFourByteTag currentScript() const override { return fScript; }
 *     private:
 *         SkFourByteTag fScript;
 *     };
 *
 *     static std::unique_ptr<LanguageRunIterator>
 *     MakeStdLanguageRunIterator(const char* utf8, size_t utf8Bytes);
 *     class TrivialLanguageRunIterator : public TrivialRunIterator<LanguageRunIterator> {
 *     public:
 *         TrivialLanguageRunIterator(const char* language, size_t utf8Bytes)
 *             : TrivialRunIterator(utf8Bytes), fLanguage(language) {}
 *         const char* currentLanguage() const override { return fLanguage.c_str(); }
 *     private:
 *         SkString fLanguage;
 *     };
 *
 *     class RunHandler {
 *     public:
 *         virtual ~RunHandler() = default;
 *
 *         struct Range {
 *             constexpr Range() : fBegin(0), fSize(0) {}
 *             constexpr Range(size_t begin, size_t size) : fBegin(begin), fSize(size) {}
 *             size_t fBegin;
 *             size_t fSize;
 *             constexpr size_t begin() const { return fBegin; }
 *             constexpr size_t end() const { return begin() + size(); }
 *             constexpr size_t size() const { return fSize; }
 *         };
 *
 *         struct RunInfo {
 *             const SkFont& fFont;
 *             uint8_t fBidiLevel;
 *             SkFourByteTag fScript;
 *             const char* fLanguage;
 *             SkVector fAdvance;
 *             size_t glyphCount;
 *             Range utf8Range;
 *         };
 *
 *         struct Buffer {
 *             SkGlyphID* glyphs;  // required
 *             SkPoint* positions; // required, if (!offsets) put glyphs[i] at positions[i]
 *                                 //           if ( offsets) positions[i+1]-positions[i] are advances
 *             SkPoint* offsets;   // optional, if ( offsets) put glyphs[i] at positions[i]+offsets[i]
 *             uint32_t* clusters; // optional, utf8+clusters[i] starts run which produced glyphs[i]
 *             SkPoint point;      // offset to add to all positions
 *         };
 *
 *         /** Called when beginning a line. */
 *         virtual void beginLine() = 0;
 *
 *         /** Called once for each run in a line. Can compute baselines and offsets. */
 *         virtual void runInfo(const RunInfo&) = 0;
 *
 *         /** Called after all runInfo calls for a line. */
 *         virtual void commitRunInfo() = 0;
 *
 *         /** Called for each run in a line after commitRunInfo. The buffer will be filled out. */
 *         virtual Buffer runBuffer(const RunInfo&) = 0;
 *
 *         /** Called after each runBuffer is filled out. */
 *         virtual void commitRunBuffer(const RunInfo&) = 0;
 *
 *         /** Called when ending a line. */
 *         virtual void commitLine() = 0;
 *     };
 *
 * #if !defined(SK_DISABLE_LEGACY_SKSHAPER_FUNCTIONS)
 *     virtual void shape(const char* utf8, size_t utf8Bytes,
 *                        const SkFont& srcFont,
 *                        bool leftToRight,
 *                        SkScalar width,
 *                        RunHandler*) const = 0;
 *
 *     virtual void shape(const char* utf8, size_t utf8Bytes,
 *                        FontRunIterator&,
 *                        BiDiRunIterator&,
 *                        ScriptRunIterator&,
 *                        LanguageRunIterator&,
 *                        SkScalar width,
 *                        RunHandler*) const = 0;
 * #endif
 *     virtual void shape(const char* utf8,
 *                        size_t utf8Bytes,
 *                        FontRunIterator&,
 *                        BiDiRunIterator&,
 *                        ScriptRunIterator&,
 *                        LanguageRunIterator&,
 *                        const Feature* features,
 *                        size_t featuresSize,
 *                        SkScalar width,
 *                        RunHandler*) const = 0;
 *
 * private:
 *     SkShaper(const SkShaper&) = delete;
 *     SkShaper& operator=(const SkShaper&) = delete;
 * }
 * ```
 */
public abstract class SkShaper public constructor() {
  /**
   * C++ original:
   * ```cpp
   * SkShaper::SkShaper() {}
   * ```
   */
  public constructor(param0: SkShaper) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void shape(const char* utf8, size_t utf8Bytes,
   *                        const SkFont& srcFont,
   *                        bool leftToRight,
   *                        SkScalar width,
   *                        RunHandler*) const = 0
   * ```
   */
  public abstract fun shape(
    utf8: String?,
    utf8Bytes: ULong,
    srcFont: SkFont,
    leftToRight: Boolean,
    width: SkScalar,
    param5: RunHandler?,
  )

  /**
   * C++ original:
   * ```cpp
   * virtual void shape(const char* utf8, size_t utf8Bytes,
   *                        FontRunIterator&,
   *                        BiDiRunIterator&,
   *                        ScriptRunIterator&,
   *                        LanguageRunIterator&,
   *                        SkScalar width,
   *                        RunHandler*) const = 0
   * ```
   */
  public abstract fun shape(
    utf8: String?,
    utf8Bytes: ULong,
    param2: FontRunIterator,
    param3: BiDiRunIterator,
    param4: ScriptRunIterator,
    param5: LanguageRunIterator,
    width: SkScalar,
    param7: RunHandler?,
  )

  /**
   * C++ original:
   * ```cpp
   * virtual void shape(const char* utf8,
   *                        size_t utf8Bytes,
   *                        FontRunIterator&,
   *                        BiDiRunIterator&,
   *                        ScriptRunIterator&,
   *                        LanguageRunIterator&,
   *                        const Feature* features,
   *                        size_t featuresSize,
   *                        SkScalar width,
   *                        RunHandler*) const = 0
   * ```
   */
  public abstract fun shape(
    utf8: String?,
    utf8Bytes: ULong,
    param2: FontRunIterator,
    param3: BiDiRunIterator,
    param4: ScriptRunIterator,
    param5: LanguageRunIterator,
    features: Feature?,
    featuresSize: ULong,
    width: SkScalar,
    param9: RunHandler?,
  )

  /**
   * C++ original:
   * ```cpp
   * SkShaper& operator=(const SkShaper&) = delete
   * ```
   */
  private fun assign(param0: SkShaper) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkShaper::BiDiRunIterator> SkShaper::MakeIcuBiDiRunIterator(const char* utf8,
   *                                                                             size_t utf8Bytes,
   *                                                                             uint8_t bidiLevel) {
   *     static auto unicode = get_unicode();
   *     if (!unicode) {
   *         return nullptr;
   *     }
   *     return SkShapers::unicode::BidiRunIterator(unicode, utf8, utf8Bytes, bidiLevel);
   * }
   * ```
   */
  public fun makeIcuBiDiRunIterator(
    utf8: String?,
    utf8Bytes: ULong,
    bidiLevel: UByte,
  ): Int {
    TODO("Implement makeIcuBiDiRunIterator")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkShaper::ScriptRunIterator>
   * SkShaper::MakeHbIcuScriptRunIterator(const char* utf8, size_t utf8Bytes) {
   *     return SkShapers::HB::ScriptRunIterator(utf8, utf8Bytes);
   * }
   * ```
   */
  public fun makeHbIcuScriptRunIterator(utf8: String?, utf8Bytes: ULong): Int {
    TODO("Implement makeHbIcuScriptRunIterator")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkShaper::ScriptRunIterator>
   * SkShaper::MakeSkUnicodeHbScriptRunIterator(const char* utf8, size_t utf8Bytes) {
   *     return SkShapers::HB::ScriptRunIterator(utf8, utf8Bytes);
   * }
   * ```
   */
  public fun makeSkUnicodeHbScriptRunIterator(utf8: String?, utf8Bytes: ULong): Int {
    TODO("Implement makeSkUnicodeHbScriptRunIterator")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkShaper::ScriptRunIterator> SkShaper::MakeSkUnicodeHbScriptRunIterator(
   *         const char* utf8, size_t utf8Bytes, SkFourByteTag script) {
   *     return SkShapers::HB::ScriptRunIterator(utf8, utf8Bytes, script);
   * }
   * ```
   */
  public fun makeSkUnicodeHbScriptRunIterator(
    utf8: String?,
    utf8Bytes: ULong,
    script: SkFourByteTag,
  ): Int {
    TODO("Implement makeSkUnicodeHbScriptRunIterator")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkShaper> SkShaper::MakeShaperDrivenWrapper(sk_sp<SkFontMgr> fontmgr) {
   *     return SkShapers::HB::ShaperDrivenWrapper(get_unicode(), fontmgr);
   * }
   * ```
   */
  public fun makeShaperDrivenWrapper(fontmgr: SkSp<SkFontMgr>): Int {
    TODO("Implement makeShaperDrivenWrapper")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkShaper> SkShaper::MakeShapeThenWrap(sk_sp<SkFontMgr> fontmgr) {
   *     return SkShapers::HB::ShapeThenWrap(get_unicode(), fontmgr);
   * }
   * ```
   */
  public fun makeShapeThenWrap(fontmgr: SkSp<SkFontMgr>): Int {
    TODO("Implement makeShapeThenWrap")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkShaper::PurgeHarfBuzzCache() { SkShapers::HB::PurgeCaches(); }
   * ```
   */
  public fun purgeHarfBuzzCache() {
    TODO("Implement purgeHarfBuzzCache")
  }

  public abstract class RunIterator {
    public abstract fun consume()

    public abstract fun endOfCurrentRun(): ULong

    public abstract fun atEnd(): Boolean
  }

  public abstract class FontRunIterator : undefined.RunIterator() {
    public abstract fun currentFont(): Int
  }

  public abstract class BiDiRunIterator : undefined.RunIterator() {
    public abstract fun currentLevel(): UByte
  }

  public abstract class ScriptRunIterator : undefined.RunIterator() {
    public abstract fun currentScript(): Int
  }

  public abstract class LanguageRunIterator : undefined.RunIterator() {
    public abstract fun currentLanguage(): Char
  }

  public open class Feature public constructor(
    public var tag: Int,
    public var `value`: UInt,
    public var start: ULong,
    public var end: ULong,
  )

  public open class TrivialRunIterator<RunIteratorSubclass> public constructor(
    utf8Bytes: ULong,
  ) : RunIteratorSubclass() {
    private var fEnd: ULong = TODO("Initialize fEnd")

    private var fAtEnd: Boolean = TODO("Initialize fAtEnd")

    public override fun consume() {
      TODO("Implement consume")
    }

    public override fun endOfCurrentRun(): ULong {
      TODO("Implement endOfCurrentRun")
    }

    public override fun atEnd(): Boolean {
      TODO("Implement atEnd")
    }
  }

  public open class TrivialFontRunIterator public constructor(
    font: SkFont,
    utf8Bytes: ULong,
  ) : undefined.TrivialRunIterator(TODO()),
      undefined.FontRunIterator {
    private var fFont: Int = TODO("Initialize fFont")

    public override fun currentFont(): Int {
      TODO("Implement currentFont")
    }
  }

  public open class TrivialBiDiRunIterator public constructor(
    bidiLevel: UByte,
    utf8Bytes: ULong,
  ) : undefined.TrivialRunIterator(TODO()),
      undefined.BiDiRunIterator {
    private var fBidiLevel: UByte = TODO("Initialize fBidiLevel")

    public override fun currentLevel(): UByte {
      TODO("Implement currentLevel")
    }
  }

  public open class TrivialScriptRunIterator public constructor(
    script: SkFourByteTag,
    utf8Bytes: ULong,
  ) : undefined.TrivialRunIterator(TODO()),
      org.skia.modules.ScriptRunIterator {
    private var fScript: Int = TODO("Initialize fScript")

    public override fun currentScript(): Int {
      TODO("Implement currentScript")
    }
  }

  public open class TrivialLanguageRunIterator public constructor(
    language: String?,
    utf8Bytes: ULong,
  ) : undefined.TrivialRunIterator(TODO()),
      undefined.LanguageRunIterator {
    private var fLanguage: Int = TODO("Initialize fLanguage")

    public override fun currentLanguage(): Char {
      TODO("Implement currentLanguage")
    }
  }

  public abstract class RunHandler {
    public abstract fun beginLine()

    public abstract fun runInfo(param0: org.skia.modules.RunHandler.RunInfo)

    public abstract fun commitRunInfo()

    public abstract fun runBuffer(param0: org.skia.modules.RunHandler.RunInfo): org.skia.modules.RunHandler.Buffer

    public abstract fun commitRunBuffer(param0: org.skia.modules.RunHandler.RunInfo)

    public abstract fun commitLine()

    public data class Range public constructor(
      public var fBegin: ULong,
      public var fSize: ULong,
    ) {
      public fun begin(): ULong {
        TODO("Implement begin")
      }

      public fun end(): ULong {
        TODO("Implement end")
      }

      public fun size(): ULong {
        TODO("Implement size")
      }
    }

    public data class RunInfo public constructor(
      public val fFont: Int,
      public var fBidiLevel: UByte,
      public var fScript: Int,
      public val fLanguage: String?,
      public var fAdvance: Int,
      public var glyphCount: ULong,
      public var utf8Range: org.skia.core.Range,
    )

    public open class Buffer public constructor(
      public var glyphs: Int?,
      public var positions: Int?,
      public var offsets: Int?,
      public var clusters: UInt?,
      public var point: Int,
    )
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<SkShaper> SkShaper::MakePrimitive() { return SkShapers::Primitive::PrimitiveText(); }
     * ```
     */
    public fun makePrimitive(): SkShaper? {
      TODO("Implement makePrimitive")
    }

    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<SkShaper> SkShaper::Make(sk_sp<SkFontMgr> fallback) {
     * #if defined(SK_SHAPER_HARFBUZZ_AVAILABLE) && defined(SK_SHAPER_UNICODE_AVAILABLE)
     *     std::unique_ptr<SkShaper> shaper = MakeShapeThenWrap(std::move(fallback));
     *     if (shaper) {
     *         return shaper;
     *     }
     * #elif defined(SK_SHAPER_CORETEXT_AVAILABLE)
     *     if (auto shaper = SkShapers::CT::CoreText()) {
     *         return shaper;
     *     }
     * #endif
     *     return SkShapers::Primitive::PrimitiveText();
     * }
     * ```
     */
    public fun make(fallback: SkSp<SkFontMgr> = TODO()): SkShaper? {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkShaper::PurgeCaches() {
     * #if defined(SK_SHAPER_HARFBUZZ_AVAILABLE) && defined(SK_SHAPER_UNICODE_AVAILABLE)
     *     SkShapers::HB::PurgeCaches();
     * #endif
     * }
     * ```
     */
    public fun purgeCaches() {
      TODO("Implement purgeCaches")
    }

    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<SkShaper::FontRunIterator>
     * SkShaper::MakeFontMgrRunIterator(const char* utf8, size_t utf8Bytes,
     *                                  const SkFont& font, sk_sp<SkFontMgr> fallback)
     * {
     *     return std::make_unique<FontMgrRunIterator>(utf8, utf8Bytes, font, std::move(fallback));
     * }
     * ```
     */
    public fun makeFontMgrRunIterator(
      utf8: String?,
      utf8Bytes: ULong,
      font: SkFont,
      fallback: SkSp<SkFontMgr>,
    ): Int {
      TODO("Implement makeFontMgrRunIterator")
    }

    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<SkShaper::FontRunIterator>
     * SkShaper::MakeFontMgrRunIterator(const char* utf8, size_t utf8Bytes, const SkFont& font,
     *                                  sk_sp<SkFontMgr> fallback,
     *                                  const char* requestName, SkFontStyle requestStyle,
     *                                  const SkShaper::LanguageRunIterator* language)
     * {
     *     return std::make_unique<FontMgrRunIterator>(utf8, utf8Bytes, font, std::move(fallback),
     *                                                 requestName, requestStyle, language);
     * }
     * ```
     */
    public fun makeFontMgrRunIterator(
      utf8: String?,
      utf8Bytes: ULong,
      font: SkFont,
      fallback: SkSp<SkFontMgr>,
      requestName: String?,
      requestStyle: SkFontStyle,
      language: LanguageRunIterator?,
    ): Int {
      TODO("Implement makeFontMgrRunIterator")
    }

    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<SkShaper::BiDiRunIterator>
     * SkShaper::MakeBiDiRunIterator(const char* utf8, size_t utf8Bytes, uint8_t bidiLevel) {
     * #if defined(SK_SHAPER_UNICODE_AVAILABLE)
     *       std::unique_ptr<SkShaper::BiDiRunIterator> bidi = MakeIcuBiDiRunIterator(utf8, utf8Bytes, bidiLevel);
     *       if (bidi) {
     *           return bidi;
     *       }
     * #endif
     *     return std::make_unique<SkShaper::TrivialBiDiRunIterator>(bidiLevel, utf8Bytes);
     * }
     * ```
     */
    private fun makeBiDiRunIterator(
      utf8: String?,
      utf8Bytes: ULong,
      bidiLevel: UByte,
    ): Int {
      TODO("Implement makeBiDiRunIterator")
    }

    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<SkShaper::ScriptRunIterator>
     * SkShaper::MakeScriptRunIterator(const char* utf8, size_t utf8Bytes, SkFourByteTag scriptTag) {
     * #if defined(SK_SHAPER_HARFBUZZ_AVAILABLE) && defined(SK_SHAPER_UNICODE_AVAILABLE)
     *     std::unique_ptr<SkShaper::ScriptRunIterator> script =
     *             SkShapers::HB::ScriptRunIterator(utf8, utf8Bytes, scriptTag);
     *     if (script) {
     *         return script;
     *     }
     * #endif
     *     return std::make_unique<SkShaper::TrivialScriptRunIterator>(scriptTag, utf8Bytes);
     * }
     * ```
     */
    private fun makeScriptRunIterator(
      utf8: String?,
      utf8Bytes: ULong,
      script: SkFourByteTag,
    ): Int {
      TODO("Implement makeScriptRunIterator")
    }

    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<SkShaper::LanguageRunIterator>
     * SkShaper::MakeStdLanguageRunIterator(const char* utf8, size_t utf8Bytes) {
     *     return std::make_unique<TrivialLanguageRunIterator>(std::locale().name().c_str(), utf8Bytes);
     * }
     * ```
     */
    private fun makeStdLanguageRunIterator(utf8: String?, utf8Bytes: ULong): Int {
      TODO("Implement makeStdLanguageRunIterator")
    }
  }
}
