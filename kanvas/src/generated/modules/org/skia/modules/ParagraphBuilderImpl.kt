package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.ULong
import kotlin.u16string
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class ParagraphBuilderImpl : public ParagraphBuilder {
 * public:
 *     ParagraphBuilderImpl(const ParagraphStyle& style,
 *                          sk_sp<FontCollection> fontCollection,
 *                          sk_sp<SkUnicode> unicode);
 *
 *     ~ParagraphBuilderImpl() override;
 *
 *     // Push a style to the stack. The corresponding text added with AddText will
 *     // use the top-most style.
 *     void pushStyle(const TextStyle& style) override;
 *
 *     // Remove a style from the stack. Useful to apply different styles to chunks
 *     // of text such as bolding.
 *     // Example:
 *     //   builder.PushStyle(normal_style);
 *     //   builder.AddText("Hello this is normal. ");
 *     //
 *     //   builder.PushStyle(bold_style);
 *     //   builder.AddText("And this is BOLD. ");
 *     //
 *     //   builder.Pop();
 *     //   builder.AddText(" Back to normal again.");
 *     void pop() override;
 *
 *     TextStyle peekStyle() override;
 *
 *     // Adds text to the builder. Forms the proper runs to use the upper-most style
 *     // on the style_stack.
 *     void addText(const std::u16string& text) override;
 *
 *     // Adds text to the builder, using the top-most style on on the style_stack.
 *     void addText(const char* text) override; // Don't use this one - going away soon
 *     void addText(const char* text, size_t len) override;
 *
 *     void addPlaceholder(const PlaceholderStyle& placeholderStyle) override;
 *
 *     // Constructs a SkParagraph object that can be used to layout and paint the text to a SkCanvas.
 *     std::unique_ptr<Paragraph> Build() override;
 *
 *     // Support for "Client" unicode
 *     SkSpan<char> getText() override;
 *     const ParagraphStyle& getParagraphStyle() const override;
 *
 * #if !defined(SK_DISABLE_LEGACY_CLIENT_UNICODE) && defined(SK_UNICODE_CLIENT_IMPLEMENTATION)
 *     void setWordsUtf8(std::vector<SkUnicode::Position> wordsUtf8) override;
 *     void setWordsUtf16(std::vector<SkUnicode::Position> wordsUtf16) override;
 *
 *     void setGraphemeBreaksUtf8(std::vector<SkUnicode::Position> graphemesUtf8) override;
 *     void setGraphemeBreaksUtf16(std::vector<SkUnicode::Position> graphemesUtf16) override;
 *
 *     void setLineBreaksUtf8(std::vector<SkUnicode::LineBreakBefore> lineBreaksUtf8) override;
 *     void setLineBreaksUtf16(std::vector<SkUnicode::LineBreakBefore> lineBreaksUtf16) override;
 *
 *     std::tuple<std::vector<SkUnicode::Position>,
 *                std::vector<SkUnicode::Position>,
 *                std::vector<SkUnicode::LineBreakBefore>>
 *         getClientICUData() const override {
 *             return { fWordsUtf16, fGraphemeBreaksUtf8, fLineBreaksUtf8 };
 *     }
 *
 *     void SetUnicode(sk_sp<SkUnicode> unicode) override {
 *         fUnicode = std::move(unicode);
 *     }
 * #endif
 *     // Support for Flutter optimization
 *     void Reset() override;
 *
 *     static std::unique_ptr<ParagraphBuilder> make(const ParagraphStyle& style,
 *                                                   sk_sp<FontCollection> fontCollection,
 *                                                   sk_sp<SkUnicode> unicode);
 *
 *
 * #if !defined(SK_DISABLE_LEGACY_PARAGRAPH_UNICODE)
 *     // Just until we fix all the code; calls icu::make inside
 *     static std::unique_ptr<ParagraphBuilder> make(const ParagraphStyle& style,
 *                                                   sk_sp<FontCollection> fontCollection);
 * #endif
 *
 *     static bool RequiresClientICU();
 * protected:
 *     void startStyledBlock();
 *     void endRunIfNeeded();
 *     const TextStyle& internalPeekStyle();
 *     void addPlaceholder(const PlaceholderStyle& placeholderStyle, bool lastOne);
 *     void finalize();
 *
 *     SkString fUtf8;
 *     skia_private::STArray<4, TextStyle, true> fTextStyles;
 *     skia_private::STArray<4, Block, true> fStyledBlocks;
 *     skia_private::STArray<4, Placeholder, true> fPlaceholders;
 *     sk_sp<FontCollection> fFontCollection;
 *     ParagraphStyle fParagraphStyle;
 *
 *     sk_sp<SkUnicode> fUnicode;
 * private:
 *     SkOnce fillUTF16MappingOnce;
 *     void ensureUTF16Mapping();
 *     skia_private::TArray<TextIndex, true> fUTF8IndexForUTF16Index;
 *     skia_private::TArray<TextIndex, true> fUTF16IndexForUTF8Index;
 * #if defined(SK_UNICODE_CLIENT_IMPLEMENTATION)
 *     bool fTextIsFinalized;
 *     bool fUsingClientInfo;
 *     std::vector<SkUnicode::Position> fWordsUtf16;
 *     std::vector<SkUnicode::Position> fGraphemeBreaksUtf8;
 *     std::vector<SkUnicode::LineBreakBefore> fLineBreaksUtf8;
 * #endif
 * }
 * ```
 */
public open class ParagraphBuilderImpl public constructor(
  style: ParagraphStyle,
  fontCollection: SkSp<FontCollection>,
  unicode: SkSp<SkUnicode>,
) : ParagraphBuilder() {
  /**
   * C++ original:
   * ```cpp
   * SkString fUtf8
   * ```
   */
  protected var fUtf8: Int = TODO("Initialize fUtf8")

  /**
   * C++ original:
   * ```cpp
   * skia_private::STArray<4, TextStyle, true> fTextStyles
   * ```
   */
  protected var fTextStyles: Int = TODO("Initialize fTextStyles")

  /**
   * C++ original:
   * ```cpp
   * skia_private::STArray<4, Block, true> fStyledBlocks
   * ```
   */
  protected var fStyledBlocks: Int = TODO("Initialize fStyledBlocks")

  /**
   * C++ original:
   * ```cpp
   * skia_private::STArray<4, Placeholder, true> fPlaceholders
   * ```
   */
  protected var fPlaceholders: Int = TODO("Initialize fPlaceholders")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<FontCollection> fFontCollection
   * ```
   */
  protected var fFontCollection: Int = TODO("Initialize fFontCollection")

  /**
   * C++ original:
   * ```cpp
   * ParagraphStyle fParagraphStyle
   * ```
   */
  protected var fParagraphStyle: Int = TODO("Initialize fParagraphStyle")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkUnicode> fUnicode
   * ```
   */
  protected var fUnicode: Int = TODO("Initialize fUnicode")

  /**
   * C++ original:
   * ```cpp
   * SkOnce fillUTF16MappingOnce
   * ```
   */
  private var fillUTF16MappingOnce: Int = TODO("Initialize fillUTF16MappingOnce")

  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<TextIndex, true> fUTF8IndexForUTF16Index
   * ```
   */
  private var fUTF8IndexForUTF16Index: Int = TODO("Initialize fUTF8IndexForUTF16Index")

  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<TextIndex, true> fUTF16IndexForUTF8Index
   * ```
   */
  private var fUTF16IndexForUTF8Index: Int = TODO("Initialize fUTF16IndexForUTF8Index")

  /**
   * C++ original:
   * ```cpp
   * void ParagraphBuilderImpl::pushStyle(const TextStyle& style) {
   *     fTextStyles.push_back(style);
   *     if (!fStyledBlocks.empty() && fStyledBlocks.back().fRange.end == fUtf8.size() &&
   *         fStyledBlocks.back().fStyle == style) {
   *         // Just continue with the same style
   *     } else {
   *         // Go with the new style
   *         startStyledBlock();
   *     }
   * }
   * ```
   */
  public override fun pushStyle(style: TextStyle) {
    TODO("Implement pushStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * void ParagraphBuilderImpl::pop() {
   *     if (!fTextStyles.empty()) {
   *         fTextStyles.pop_back();
   *     } else {
   *         // In this case we use paragraph style and skip Pop operation
   *         SkDEBUGF("SkParagraphBuilder.Pop() called too many times.\n");
   *     }
   *
   *     this->startStyledBlock();
   * }
   * ```
   */
  public override fun pop() {
    TODO("Implement pop")
  }

  /**
   * C++ original:
   * ```cpp
   * TextStyle ParagraphBuilderImpl::peekStyle() {
   *     return this->internalPeekStyle();
   * }
   * ```
   */
  public override fun peekStyle(): Int {
    TODO("Implement peekStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * void ParagraphBuilderImpl::addText(const std::u16string& text) {
   * #if defined(SK_UNICODE_CLIENT_IMPLEMENTATION)
   *     SkASSERT(!fTextIsFinalized);
   * #endif
   *     auto utf8 = SkUnicode::convertUtf16ToUtf8(text);
   *     fUtf8.append(utf8);
   * }
   * ```
   */
  public override fun addText(text: u16string) {
    TODO("Implement addText")
  }

  /**
   * C++ original:
   * ```cpp
   * void ParagraphBuilderImpl::addText(const char* text) {
   * #if defined(SK_UNICODE_CLIENT_IMPLEMENTATION)
   *     SkASSERT(!fTextIsFinalized);
   * #endif
   *     fUtf8.append(text);
   * }
   * ```
   */
  public override fun addText(text: String?) {
    TODO("Implement addText")
  }

  /**
   * C++ original:
   * ```cpp
   * void ParagraphBuilderImpl::addText(const char* text, size_t len) {
   * #if defined(SK_UNICODE_CLIENT_IMPLEMENTATION)
   *     SkASSERT(!fTextIsFinalized);
   * #endif
   *     fUtf8.append(text, len);
   * }
   * ```
   */
  public override fun addText(text: String?, len: ULong) {
    TODO("Implement addText")
  }

  /**
   * C++ original:
   * ```cpp
   * void ParagraphBuilderImpl::addPlaceholder(const PlaceholderStyle& placeholderStyle) {
   * #if defined(SK_UNICODE_CLIENT_IMPLEMENTATION)
   *     SkASSERT(!fTextIsFinalized);
   * #endif
   *     addPlaceholder(placeholderStyle, false);
   * }
   * ```
   */
  public override fun addPlaceholder(placeholderStyle: PlaceholderStyle) {
    TODO("Implement addPlaceholder")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<Paragraph> ParagraphBuilderImpl::Build() {
   *     this->finalize();
   *     // Add one fake placeholder with the rest of the text
   *     this->addPlaceholder(PlaceholderStyle(), true);
   *
   *     fUTF8IndexForUTF16Index.clear();
   *     fUTF16IndexForUTF8Index.clear();
   *
   *     SkASSERT_RELEASE(fUnicode);
   *     return std::make_unique<ParagraphImpl>(
   *             fUtf8, fParagraphStyle, fStyledBlocks, fPlaceholders, fFontCollection, fUnicode);
   * }
   * ```
   */
  public override fun build(): Int {
    TODO("Implement build")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<char> ParagraphBuilderImpl::getText() {
   *     this->finalize();
   *     return SkSpan<char>(fUtf8.isEmpty() ? nullptr : fUtf8.data(), fUtf8.size());
   * }
   * ```
   */
  public override fun getText(): Int {
    TODO("Implement getText")
  }

  /**
   * C++ original:
   * ```cpp
   * const ParagraphStyle& ParagraphBuilderImpl::getParagraphStyle() const {
   *     return fParagraphStyle;
   * }
   * ```
   */
  public override fun getParagraphStyle(): Int {
    TODO("Implement getParagraphStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * void ParagraphBuilderImpl::Reset() {
   *
   *     fTextStyles.clear();
   *     fUtf8.reset();
   *     fStyledBlocks.clear();
   *     fPlaceholders.clear();
   *     fUTF8IndexForUTF16Index.clear();
   *     fUTF16IndexForUTF8Index.clear();
   * #if defined(SK_UNICODE_CLIENT_IMPLEMENTATION)
   *     fWordsUtf16.clear();
   *     fGraphemeBreaksUtf8.clear();
   *     fLineBreaksUtf8.clear();
   *     fTextIsFinalized = false;
   * #endif
   *     startStyledBlock();
   * }
   * ```
   */
  public override fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void ParagraphBuilderImpl::startStyledBlock() {
   *     endRunIfNeeded();
   *     fStyledBlocks.emplace_back(fUtf8.size(), fUtf8.size(), internalPeekStyle());
   * }
   * ```
   */
  protected fun startStyledBlock() {
    TODO("Implement startStyledBlock")
  }

  /**
   * C++ original:
   * ```cpp
   * void ParagraphBuilderImpl::endRunIfNeeded() {
   *     if (fStyledBlocks.empty()) {
   *         return;
   *     }
   *
   *     auto& last = fStyledBlocks.back();
   *     if (last.fRange.start == fUtf8.size()) {
   *         fStyledBlocks.pop_back();
   *     } else {
   *         last.fRange.end = fUtf8.size();
   *     }
   * }
   * ```
   */
  protected fun endRunIfNeeded() {
    TODO("Implement endRunIfNeeded")
  }

  /**
   * C++ original:
   * ```cpp
   * const TextStyle& ParagraphBuilderImpl::internalPeekStyle() {
   *     if (fTextStyles.empty()) {
   *         return fParagraphStyle.getTextStyle();
   *     } else {
   *         return fTextStyles.back();
   *     }
   * }
   * ```
   */
  protected fun internalPeekStyle(): Int {
    TODO("Implement internalPeekStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * void ParagraphBuilderImpl::addPlaceholder(const PlaceholderStyle& placeholderStyle, bool lastOne) {
   * #if defined(SK_UNICODE_CLIENT_IMPLEMENTATION)
   *     // The very last placeholder is added automatically
   *     // and only AFTER finalize() is called
   *     SkASSERT(!fTextIsFinalized || lastOne);
   * #endif
   *     if (!fUtf8.isEmpty() && !lastOne) {
   *         // We keep the very last text style
   *         this->endRunIfNeeded();
   *     }
   *
   *     BlockRange stylesBefore(fPlaceholders.empty() ? 0 : fPlaceholders.back().fBlocksBefore.end + 1,
   *                             fStyledBlocks.size());
   *     TextRange textBefore(fPlaceholders.empty() ? 0 : fPlaceholders.back().fRange.end,
   *                             fUtf8.size());
   *     auto start = fUtf8.size();
   *     auto topStyle = internalPeekStyle();
   *     if (!lastOne) {
   *         pushStyle(topStyle.cloneForPlaceholder());
   *         addText(std::u16string(1ull, 0xFFFC));
   *         pop();
   *     }
   *     auto end = fUtf8.size();
   *     fPlaceholders.emplace_back(start, end, placeholderStyle, topStyle, stylesBefore, textBefore);
   * }
   * ```
   */
  public fun addPlaceholder(placeholderStyle: PlaceholderStyle, lastOne: Boolean) {
    TODO("Implement addPlaceholder")
  }

  /**
   * C++ original:
   * ```cpp
   * void ParagraphBuilderImpl::finalize() {
   * #if defined(SK_UNICODE_CLIENT_IMPLEMENTATION)
   *     if (fTextIsFinalized) {
   *         return;
   *     }
   * #endif
   *     if (!fUtf8.isEmpty()) {
   *         this->endRunIfNeeded();
   *     }
   *
   * #if defined(SK_UNICODE_CLIENT_IMPLEMENTATION)
   *     fTextIsFinalized = true;
   * #endif
   * }
   * ```
   */
  protected fun finalize() {
    TODO("Implement finalize")
  }

  /**
   * C++ original:
   * ```cpp
   * void ParagraphBuilderImpl::ensureUTF16Mapping() {
   *     fillUTF16MappingOnce([&] {
   *         SkUnicode::extractUtfConversionMapping(
   *                 this->getText(),
   *                 [&](size_t index) { fUTF8IndexForUTF16Index.emplace_back(index); },
   *                 [&](size_t index) { fUTF16IndexForUTF8Index.emplace_back(index); });
   *     });
   * }
   * ```
   */
  private fun ensureUTF16Mapping() {
    TODO("Implement ensureUTF16Mapping")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<ParagraphBuilder> ParagraphBuilderImpl::make(const ParagraphStyle& style,
     *                                                              sk_sp<FontCollection> fontCollection,
     *                                                              sk_sp<SkUnicode> unicode) {
     *     return std::make_unique<ParagraphBuilderImpl>(style, std::move(fontCollection),
     *                                                   std::move(unicode));
     * }
     * ```
     */
    public override fun make(
      style: ParagraphStyle,
      fontCollection: SkSp<FontCollection>,
      unicode: SkSp<SkUnicode>,
    ): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * static std::unique_ptr<ParagraphBuilder> make(const ParagraphStyle& style,
     *                                                   sk_sp<FontCollection> fontCollection)
     * ```
     */
    public fun make(style: ParagraphStyle, fontCollection: SkSp<FontCollection>): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * bool ParagraphBuilderImpl::RequiresClientICU() {
     * #if defined(SK_UNICODE_CLIENT_IMPLEMENTATION)
     *     return true;
     * #else
     *     return false;
     * #endif
     * }
     * ```
     */
    public fun requiresClientICU(): Boolean {
      TODO("Implement requiresClientICU")
    }
  }
}
