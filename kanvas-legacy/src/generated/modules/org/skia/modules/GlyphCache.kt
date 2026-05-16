package org.skia.modules

import kotlin.Float
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkFont
import org.skia.foundation.SkGlyphID
import org.skia.foundation.SkPath
import org.skia.foundation.SkUnichar
import org.skia.memory.SkArenaAlloc

/**
 * C++ original:
 * ```cpp
 * class GlyphCache {
 * public:
 *     struct GlyphRec {
 *         SkUnichar fID;
 *         float     fWidth;
 *         SkPath    fPath;
 *     };
 *
 *     void addGlyph(const std::string_view& font_name, SkUnichar id, const SkFont& font,
 *                   SkGlyphID glyph) {
 *         std::vector<GlyphRec>& font_glyphs =
 *                 fFontGlyphs.emplace(font_name, std::vector<GlyphRec>()).first->second;
 *
 *         // We don't expect a large number of glyphs, linear search should be fine.
 *         for (const auto& rec : font_glyphs) {
 *             if (rec.fID == id) {
 *                 return;
 *             }
 *         }
 *
 *         SkPath path;
 *         if (auto result = font.getPath(glyph)) {
 *             path = *result;
 *         } else {
 *             // Only glyphs that can be represented as paths are supported for now, color glyphs are
 *             // ignored.  We could look into converting these to comp-based Lottie fonts if needed.
 *
 *             // TODO: plumb a client-privided skottie::Logger for error reporting.
 *             std::cerr << "Glyph ID %d could not be converted to a path, discarding.";
 *         }
 *
 *         float width = font.getWidth(glyph);
 *
 *         // Lottie glyph shapes are always defined at a normalized size of 100.
 *         const float scale = 100 / font.getSize();
 *
 *         font_glyphs.push_back({
 *             id,
 *             width * scale,
 *             path.makeTransform(SkMatrix::Scale(scale, scale))
 *         });
 *     }
 *
 *     std::tuple<Value, Value> toLottie(SkArenaAlloc& alloc, const Value& orig_fonts) const {
 *         auto find_font_info = [&](const std::string& font_name) -> const ObjectValue* {
 *             if (const ArrayValue* jlist = orig_fonts["list"]) {
 *                 for (const auto& jfont : *jlist) {
 *                     if (const StringValue* jname = jfont["fName"]) {
 *                         if (font_name == jname->begin()) {
 *                             return jfont;
 *                         }
 *                     }
 *                 }
 *             }
 *
 *             return nullptr;
 *         };
 *
 *         // Lottie glyph shape font data is stored in two arrays:
 *         //   - "fonts" holds font metadata (name, family, style, etc)
 *         //   - "chars" holds character data (char id, size, advance, path, etc)
 *         // Individual chars are associated with specific fonts based on their
 *         // "fFamily" and "style" props.
 *         std::vector<Value> fonts, chars;
 *
 *         for (const auto& font : fFontGlyphs) {
 *             const ObjectValue* orig_font = find_font_info(font.first);
 *             SkASSERT(orig_font);
 *
 *             // New font entry based on existing font data + updated name.
 *             const SkString font_name = preshapedFontName(font.first);
 *             orig_font->writable("fName", alloc) =
 *                     StringValue(font_name.c_str(), font_name.size(), alloc);
 *             fonts.push_back(*orig_font);
 *
 *             for (const auto& glyph : font.second) {
 *                 // New char entry.
 *                 char glyphid_as_utf8[SkUTF::kMaxBytesInUTF8Sequence];
 *                 size_t utf8_len = SkUTF::ToUTF8(glyph.fID, glyphid_as_utf8);
 *
 *                 skjson::Member fields[] = {
 *                     { StringValue("ch"     , alloc), StringValue(glyphid_as_utf8, utf8_len, alloc)},
 *                     { StringValue("fFamily", alloc), (*orig_font)["fFamily"]                      },
 *                     { StringValue("style"  , alloc), (*orig_font)["fStyle"]                       },
 *                     { StringValue("size"   , alloc), NumberValue(100)                             },
 *                     { StringValue("w"      , alloc), NumberValue(glyph.fWidth)                    },
 *                     { StringValue("data"   , alloc), pathToLottie(glyph.fPath, alloc)             },
 *                 };
 *
 *                 chars.push_back(ObjectValue(fields, std::size(fields), alloc));
 *             }
 *         }
 *
 *         skjson::Member fonts_fields[] = {
 *             { StringValue("list", alloc), ArrayValue(fonts.data(), fonts.size(), alloc) },
 *         };
 *         return std::make_tuple(ObjectValue(fonts_fields, std::size(fonts_fields), alloc),
 *                                ArrayValue(chars.data(), chars.size(), alloc));
 *     }
 *
 * private:
 *     std::unordered_map<std::string, std::vector<GlyphRec>> fFontGlyphs;
 * }
 * ```
 */
public open class GlyphCache {
  /**
   * C++ original:
   * ```cpp
   * void addGlyph(const std::string_view& font_name, SkUnichar id, const SkFont& font,
   *                   SkGlyphID glyph) {
   *         std::vector<GlyphRec>& font_glyphs =
   *                 fFontGlyphs.emplace(font_name, std::vector<GlyphRec>()).first->second;
   *
   *         // We don't expect a large number of glyphs, linear search should be fine.
   *         for (const auto& rec : font_glyphs) {
   *             if (rec.fID == id) {
   *                 return;
   *             }
   *         }
   *
   *         SkPath path;
   *         if (auto result = font.getPath(glyph)) {
   *             path = *result;
   *         } else {
   *             // Only glyphs that can be represented as paths are supported for now, color glyphs are
   *             // ignored.  We could look into converting these to comp-based Lottie fonts if needed.
   *
   *             // TODO: plumb a client-privided skottie::Logger for error reporting.
   *             std::cerr << "Glyph ID %d could not be converted to a path, discarding.";
   *         }
   *
   *         float width = font.getWidth(glyph);
   *
   *         // Lottie glyph shapes are always defined at a normalized size of 100.
   *         const float scale = 100 / font.getSize();
   *
   *         font_glyphs.push_back({
   *             id,
   *             width * scale,
   *             path.makeTransform(SkMatrix::Scale(scale, scale))
   *         });
   *     }
   * ```
   */
  public fun addGlyph(
    fontName: String,
    id: SkUnichar,
    font: SkFont,
    glyph: SkGlyphID,
  ) {
    TODO("Implement addGlyph")
  }

  /**
   * C++ original:
   * ```cpp
   * std::tuple<Value, Value> toLottie(SkArenaAlloc& alloc, const Value& orig_fonts) const {
   *         auto find_font_info = [&](const std::string& font_name) -> const ObjectValue* {
   *             if (const ArrayValue* jlist = orig_fonts["list"]) {
   *                 for (const auto& jfont : *jlist) {
   *                     if (const StringValue* jname = jfont["fName"]) {
   *                         if (font_name == jname->begin()) {
   *                             return jfont;
   *                         }
   *                     }
   *                 }
   *             }
   *
   *             return nullptr;
   *         };
   *
   *         // Lottie glyph shape font data is stored in two arrays:
   *         //   - "fonts" holds font metadata (name, family, style, etc)
   *         //   - "chars" holds character data (char id, size, advance, path, etc)
   *         // Individual chars are associated with specific fonts based on their
   *         // "fFamily" and "style" props.
   *         std::vector<Value> fonts, chars;
   *
   *         for (const auto& font : fFontGlyphs) {
   *             const ObjectValue* orig_font = find_font_info(font.first);
   *             SkASSERT(orig_font);
   *
   *             // New font entry based on existing font data + updated name.
   *             const SkString font_name = preshapedFontName(font.first);
   *             orig_font->writable("fName", alloc) =
   *                     StringValue(font_name.c_str(), font_name.size(), alloc);
   *             fonts.push_back(*orig_font);
   *
   *             for (const auto& glyph : font.second) {
   *                 // New char entry.
   *                 char glyphid_as_utf8[SkUTF::kMaxBytesInUTF8Sequence];
   *                 size_t utf8_len = SkUTF::ToUTF8(glyph.fID, glyphid_as_utf8);
   *
   *                 skjson::Member fields[] = {
   *                     { StringValue("ch"     , alloc), StringValue(glyphid_as_utf8, utf8_len, alloc)},
   *                     { StringValue("fFamily", alloc), (*orig_font)["fFamily"]                      },
   *                     { StringValue("style"  , alloc), (*orig_font)["fStyle"]                       },
   *                     { StringValue("size"   , alloc), NumberValue(100)                             },
   *                     { StringValue("w"      , alloc), NumberValue(glyph.fWidth)                    },
   *                     { StringValue("data"   , alloc), pathToLottie(glyph.fPath, alloc)             },
   *                 };
   *
   *                 chars.push_back(ObjectValue(fields, std::size(fields), alloc));
   *             }
   *         }
   *
   *         skjson::Member fonts_fields[] = {
   *             { StringValue("list", alloc), ArrayValue(fonts.data(), fonts.size(), alloc) },
   *         };
   *         return std::make_tuple(ObjectValue(fonts_fields, std::size(fonts_fields), alloc),
   *                                ArrayValue(chars.data(), chars.size(), alloc));
   *     }
   * ```
   */
  public fun toLottie(alloc: SkArenaAlloc, origFonts: Value): Int {
    TODO("Implement toLottie")
  }

  public data class GlyphRec public constructor(
    public var fID: SkUnichar,
    public var fWidth: Float,
    public var fPath: SkPath,
  )
}
