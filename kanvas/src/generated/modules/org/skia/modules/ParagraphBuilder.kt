package org.skia.modules

import kotlin.Int
import kotlin.String
import kotlin.ULong
import kotlin.u16string
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class ParagraphBuilder {
 * protected:
 *     ParagraphBuilder() {}
 *
 * public:
 *     virtual ~ParagraphBuilder() = default;
 *
 *     // Push a style to the stack. The corresponding text added with AddText will
 *     // use the top-most style.
 *     virtual void pushStyle(const TextStyle& style) = 0;
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
 *     virtual void pop() = 0;
 *
 *     virtual TextStyle peekStyle() = 0;
 *
 *     // Adds UTF16-encoded text to the builder. Forms the proper runs to use the upper-most style
 *     // on the style_stack.
 *     virtual void addText(const std::u16string& text) = 0;
 *
 *     // Adds UTF8-encoded text to the builder, using the top-most style on the style_stack.
 *     virtual void addText(const char* text) = 0;
 *     virtual void addText(const char* text, size_t len) = 0;
 *
 *     // Pushes the information required to leave an open space, where Flutter may
 *     // draw a custom placeholder into.
 *     // Internally, this method adds a single object replacement character (0xFFFC)
 *     virtual void addPlaceholder(const PlaceholderStyle& placeholderStyle) = 0;
 *
 *     // Constructs a SkParagraph object that can be used to layout and paint the text to a SkCanvas.
 *     virtual std::unique_ptr<Paragraph> Build() = 0;
 *
 *     virtual SkSpan<char> getText() = 0;
 *     virtual const ParagraphStyle& getParagraphStyle() const = 0;
 *
 * #if !defined(SK_DISABLE_LEGACY_CLIENT_UNICODE) && defined(SK_UNICODE_CLIENT_IMPLEMENTATION)
 *     // Mainly, support for "Client" unicode
 *     virtual void setWordsUtf8(std::vector<SkUnicode::Position> wordsUtf8) = 0;
 *     virtual void setWordsUtf16(std::vector<SkUnicode::Position> wordsUtf16) = 0;
 *
 *     virtual void setGraphemeBreaksUtf8(std::vector<SkUnicode::Position> graphemesUtf8) = 0;
 *     virtual void setGraphemeBreaksUtf16(std::vector<SkUnicode::Position> graphemesUtf16) = 0;
 *
 *     virtual void setLineBreaksUtf8(std::vector<SkUnicode::LineBreakBefore> lineBreaksUtf8) = 0;
 *     virtual void setLineBreaksUtf16(std::vector<SkUnicode::LineBreakBefore> lineBreaksUtf16) = 0;
 *
 *     virtual std::tuple<std::vector<SkUnicode::Position>,
 *                std::vector<SkUnicode::Position>,
 *                std::vector<SkUnicode::LineBreakBefore>>
 *         getClientICUData() const = 0;
 *
 *     virtual void SetUnicode(sk_sp<SkUnicode> unicode) = 0;
 * #endif
 *
 *     // Resets this builder to its initial state, discarding any text, styles, placeholders that have
 *     // been added, but keeping the initial ParagraphStyle.
 *     virtual void Reset() = 0;
 *
 *     static std::unique_ptr<ParagraphBuilder> make(const ParagraphStyle& style,
 *                                                   sk_sp<FontCollection> fontCollection,
 *                                                   sk_sp<SkUnicode> unicode);
 * }
 * ```
 */
public abstract class ParagraphBuilder public constructor() {
  /**
   * C++ original:
   * ```cpp
   * virtual void pushStyle(const TextStyle& style) = 0
   * ```
   */
  public abstract fun pushStyle(style: TextStyle)

  /**
   * C++ original:
   * ```cpp
   * virtual void pop() = 0
   * ```
   */
  public abstract fun pop()

  /**
   * C++ original:
   * ```cpp
   * virtual TextStyle peekStyle() = 0
   * ```
   */
  public abstract fun peekStyle(): Int

  /**
   * C++ original:
   * ```cpp
   * virtual void addText(const std::u16string& text) = 0
   * ```
   */
  public abstract fun addText(text: u16string)

  /**
   * C++ original:
   * ```cpp
   * virtual void addText(const char* text) = 0
   * ```
   */
  public abstract fun addText(text: String?)

  /**
   * C++ original:
   * ```cpp
   * virtual void addText(const char* text, size_t len) = 0
   * ```
   */
  public abstract fun addText(text: String?, len: ULong)

  /**
   * C++ original:
   * ```cpp
   * virtual void addPlaceholder(const PlaceholderStyle& placeholderStyle) = 0
   * ```
   */
  public abstract fun addPlaceholder(placeholderStyle: PlaceholderStyle)

  /**
   * C++ original:
   * ```cpp
   * virtual std::unique_ptr<Paragraph> Build() = 0
   * ```
   */
  public abstract fun build(): Int

  /**
   * C++ original:
   * ```cpp
   * virtual SkSpan<char> getText() = 0
   * ```
   */
  public abstract fun getText(): Int

  /**
   * C++ original:
   * ```cpp
   * virtual const ParagraphStyle& getParagraphStyle() const = 0
   * ```
   */
  public abstract fun getParagraphStyle(): Int

  /**
   * C++ original:
   * ```cpp
   * virtual void Reset() = 0
   * ```
   */
  public abstract fun reset()

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<ParagraphBuilder> ParagraphBuilder::make(const ParagraphStyle& style,
     *                                                          sk_sp<FontCollection> fontCollection,
     *                                                          sk_sp<SkUnicode> unicode) {
     *     return ParagraphBuilderImpl::make(style, std::move(fontCollection), std::move(unicode));
     * }
     * ```
     */
    public fun make(
      style: ParagraphStyle,
      fontCollection: SkSp<FontCollection>,
      unicode: SkSp<SkUnicode>,
    ): ParagraphBuilder? {
      TODO("Implement make")
    }
  }
}
