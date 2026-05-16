package org.skia.modules

import kotlin.Boolean
import kotlin.String
import kotlin.ULong
import org.skia.`external`.Feature
import org.skia.foundation.SkFont
import org.skia.math.SkScalar
import undefined.BiDiRunIterator
import undefined.FontRunIterator
import undefined.LanguageRunIterator

/**
 * C++ original:
 * ```cpp
 * class SkShaperPrimitive : public SkShaper {
 * public:
 *     SkShaperPrimitive() {}
 * private:
 * #if !defined(SK_DISABLE_LEGACY_SKSHAPER_FUNCTIONS)
 *     void shape(const char* utf8, size_t utf8Bytes,
 *                const SkFont& srcFont,
 *                bool leftToRight,
 *                SkScalar width,
 *                RunHandler*) const override;
 *
 *     void shape(const char* utf8, size_t utf8Bytes,
 *                FontRunIterator&,
 *                BiDiRunIterator&,
 *                ScriptRunIterator&,
 *                LanguageRunIterator&,
 *                SkScalar width,
 *                RunHandler*) const override;
 * #endif
 *
 *     void shape(const char* utf8, size_t utf8Bytes,
 *                FontRunIterator&,
 *                BiDiRunIterator&,
 *                ScriptRunIterator&,
 *                LanguageRunIterator&,
 *                const Feature*, size_t featureSize,
 *                SkScalar width,
 *                RunHandler*) const override;
 * }
 * ```
 */
public open class SkShaperPrimitive public constructor() : SkShaper() {
  /**
   * C++ original:
   * ```cpp
   * void SkShaperPrimitive::shape(const char* utf8,
   *                               size_t utf8Bytes,
   *                               const SkFont& font,
   *                               bool leftToRight,
   *                               SkScalar width,
   *                               RunHandler* handler) const {
   *     std::unique_ptr<FontRunIterator> fontRuns(
   *             MakeFontMgrRunIterator(utf8, utf8Bytes, font, nullptr));
   *     if (!fontRuns) {
   *         return;
   *     }
   *     // bidi, script, and lang are all unused so we can construct them with empty data.
   *     TrivialBiDiRunIterator bidi{0, 0};
   *     TrivialScriptRunIterator script{0, 0};
   *     TrivialLanguageRunIterator lang{nullptr, 0};
   *     return this->shape(utf8, utf8Bytes, *fontRuns, bidi, script, lang, nullptr, 0, width, handler);
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
   * void SkShaperPrimitive::shape(const char* utf8,
   *                               size_t utf8Bytes,
   *                               FontRunIterator& font,
   *                               BiDiRunIterator& bidi,
   *                               ScriptRunIterator& script,
   *                               LanguageRunIterator& lang,
   *                               SkScalar width,
   *                               RunHandler* handler) const {
   *     return this->shape(utf8, utf8Bytes, font, bidi, script, lang, nullptr, 0, width, handler);
   * }
   * ```
   */
  public override fun shape(
    utf8: String?,
    utf8Bytes: ULong,
    font: FontRunIterator,
    bidi: BiDiRunIterator,
    script: ScriptRunIterator,
    lang: LanguageRunIterator,
    width: SkScalar,
    handler: RunHandler?,
  ) {
    TODO("Implement shape")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkShaperPrimitive::shape(const char* utf8,
   *                               size_t utf8Bytes,
   *                               FontRunIterator& fontRuns,
   *                               BiDiRunIterator&,
   *                               ScriptRunIterator&,
   *                               LanguageRunIterator&,
   *                               const Feature*,
   *                               size_t,
   *                               SkScalar width,
   *                               RunHandler* handler) const {
   *     SkFont font;
   *     if (!fontRuns.atEnd()) {
   *         fontRuns.consume();
   *         font = fontRuns.currentFont();
   *     }
   *     SkASSERT(font.getTypeface());
   *
   *     int glyphCount = font.countText(utf8, utf8Bytes, SkTextEncoding::kUTF8);
   *     if (glyphCount < 0) {
   *         return;
   *     }
   *
   *     std::unique_ptr<SkGlyphID[]> glyphs(new SkGlyphID[glyphCount]);
   *     font.textToGlyphs(utf8, utf8Bytes, SkTextEncoding::kUTF8, {glyphs.get(), (size_t)glyphCount});
   *
   *     std::unique_ptr<SkScalar[]> advances(new SkScalar[glyphCount]);
   *     font.getWidths({glyphs.get(), (size_t)glyphCount}, {advances.get(), (size_t)glyphCount});
   *
   *     size_t glyphOffset = 0;
   *     size_t utf8Offset = 0;
   *     do {
   *         size_t bytesCollapsed;
   *         size_t bytesConsumed = linebreak(utf8, utf8 + utf8Bytes, font, width,
   *                                          advances.get() + glyphOffset, &bytesCollapsed);
   *         size_t bytesVisible = bytesConsumed - bytesCollapsed;
   *
   *         size_t numGlyphs = SkUTF::CountUTF8(utf8, bytesVisible);
   *         const RunHandler::RunInfo info = {
   *             font, 0, 0, "",
   *             { font.measureText(utf8, bytesVisible, SkTextEncoding::kUTF8), 0 },
   *             numGlyphs,
   *             RunHandler::Range(utf8Offset, bytesVisible)
   *         };
   *         handler->beginLine();
   *         if (info.glyphCount) {
   *             handler->runInfo(info);
   *         }
   *         handler->commitRunInfo();
   *         if (info.glyphCount) {
   *             const auto buffer = handler->runBuffer(info);
   *
   *             memcpy(buffer.glyphs, glyphs.get() + glyphOffset, info.glyphCount * sizeof(SkGlyphID));
   *             SkPoint position = buffer.point;
   *             for (size_t i = 0; i < info.glyphCount; ++i) {
   *                 buffer.positions[i] = position;
   *                 position.fX += advances[i + glyphOffset];
   *             }
   *             if (buffer.clusters) {
   *                 const char* txtPtr = utf8;
   *                 for (size_t i = 0; i < info.glyphCount; ++i) {
   *                     // Each character maps to exactly one glyph.
   *                     buffer.clusters[i] = SkToU32(txtPtr - utf8 + utf8Offset);
   *                     SkUTF::NextUTF8(&txtPtr, utf8 + utf8Bytes);
   *                 }
   *             }
   *             handler->commitRunBuffer(info);
   *         }
   *         handler->commitLine();
   *
   *         glyphOffset += SkUTF::CountUTF8(utf8, bytesConsumed);
   *         utf8Offset += bytesConsumed;
   *         utf8 += bytesConsumed;
   *         utf8Bytes -= bytesConsumed;
   *     } while (0 < utf8Bytes);
   * }
   * ```
   */
  public override fun shape(
    utf8: String?,
    utf8Bytes: ULong,
    fontRuns: FontRunIterator,
    param3: BiDiRunIterator,
    param4: ScriptRunIterator,
    param5: LanguageRunIterator,
    param6: Feature?,
    featureSize: ULong,
    width: SkScalar,
    handler: RunHandler?,
  ) {
    TODO("Implement shape")
  }
}
