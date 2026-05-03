package org.skia.modules

import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.String
import kotlin.ULong
import kotlin.collections.List
import org.skia.core.SkCanvas
import org.skia.foundation.SkUnichar
import org.skia.math.SkPoint
import org.skia.math.SkScalar
import undefined.TextInfo

/**
 * C++ original:
 * ```cpp
 * class TextEditor final : public skottie::GlyphDecorator {
 * public:
 *     TextEditor(std::unique_ptr<skottie::TextPropertyHandle>&&,
 *                std::vector<std::unique_ptr<skottie::TextPropertyHandle>>&&);
 *     ~TextEditor() override;
 *
 *     void toggleEnabled();
 *     void setEnabled(bool);
 *
 *     void onDecorate(SkCanvas*, const TextInfo&) override;
 *
 *     bool onMouseInput(SkScalar x, SkScalar y, skui::InputState state, skui::ModifierKey);
 *
 *     bool onCharInput(SkUnichar c);
 *
 *     void setCursorWeight(float w) { fCursorWeight = w; }
 *
 * private:
 *     struct GlyphData {
 *         SkRect fDevBounds; // Glyph bounds mapped to device space.
 *         size_t fCluster;   // UTF8 cluster index.
 *     };
 *
 *     std::tuple<size_t, size_t> currentSelection() const;
 *     size_t closestGlyph(const SkPoint& pt) const;
 *     void drawCursor(SkCanvas*, const TextInfo&) const;
 *     void insertChar(SkUnichar c);
 *     void deleteChars(size_t offset, size_t count);
 *     bool deleteSelection();
 *     void updateDeps(const SkString&);
 *
 *     const std::unique_ptr<skottie::TextPropertyHandle>              fTextProp;
 *     const std::vector<std::unique_ptr<skottie::TextPropertyHandle>> fDependentProps;
 *     const SkPath                                                    fCursorPath;
 *     const SkRect                                                    fCursorBounds;
 *
 *     std::vector<GlyphData>     fGlyphData;
 *     std::tuple<size_t, size_t> fSelection    = {0,0};  // Indices in the glyphs domain.
 *     size_t                     fCursorIndex  = 0;      // Index in the UTF8 domain.
 *     float                      fCursorWeight = 1;
 *     bool                       fEnabled      = false;
 *     bool                       fMouseDown    = false;
 *
 *     std::chrono::time_point<std::chrono::steady_clock> fTimeBase;
 * }
 * ```
 */
public class TextEditor public constructor(
  prop: TextPropertyHandle?,
  deps: List<TextPropertyHandle?>,
) : GlyphDecorator() {
  /**
   * C++ original:
   * ```cpp
   * const std::unique_ptr<skottie::TextPropertyHandle>              fTextProp
   * ```
   */
  private val fTextProp: Int = TODO("Initialize fTextProp")

  /**
   * C++ original:
   * ```cpp
   * const SkPath                                                    fCursorPath
   * ```
   */
  private val fCursorPath: Int = TODO("Initialize fCursorPath")

  /**
   * C++ original:
   * ```cpp
   * const SkRect                                                    fCursorBounds
   * ```
   */
  private val fCursorBounds: Int = TODO("Initialize fCursorBounds")

  /**
   * C++ original:
   * ```cpp
   * std::vector<GlyphData>     fGlyphData
   * ```
   */
  private var fGlyphData: Int = TODO("Initialize fGlyphData")

  /**
   * C++ original:
   * ```cpp
   * std::tuple<size_t, size_t> fSelection
   * ```
   */
  private var fSelection: Int = TODO("Initialize fSelection")

  /**
   * C++ original:
   * ```cpp
   * size_t                     fCursorIndex
   * ```
   */
  private var fCursorIndex: Int = TODO("Initialize fCursorIndex")

  /**
   * C++ original:
   * ```cpp
   * float                      fCursorWeight = 1
   * ```
   */
  private var fCursorWeight: Float = TODO("Initialize fCursorWeight")

  /**
   * C++ original:
   * ```cpp
   * bool                       fEnabled      = false
   * ```
   */
  private var fEnabled: Boolean = TODO("Initialize fEnabled")

  /**
   * C++ original:
   * ```cpp
   * bool                       fMouseDown    = false
   * ```
   */
  private var fMouseDown: Boolean = TODO("Initialize fMouseDown")

  /**
   * C++ original:
   * ```cpp
   * std::chrono::time_point<std::chrono::steady_clock> fTimeBase
   * ```
   */
  private var fTimeBase: Int = TODO("Initialize fTimeBase")

  /**
   * C++ original:
   * ```cpp
   * void TextEditor::toggleEnabled() {
   *     fEnabled = !fEnabled;
   *
   *     auto txt = fTextProp->get();
   *     txt.fDecorator = fEnabled ? sk_ref_sp(this) : nullptr;
   *     fTextProp->set(txt);
   *
   *     if (fEnabled) {
   *         // Always reset the cursor position to the end.
   *         fCursorIndex = txt.fText.size();
   *     }
   *
   *     fTimeBase = std::chrono::steady_clock::now();
   * }
   * ```
   */
  public fun toggleEnabled() {
    TODO("Implement toggleEnabled")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextEditor::setEnabled(bool enabled) {
   *     if (enabled != fEnabled) {
   *         this->toggleEnabled();
   *     }
   * }
   * ```
   */
  public fun setEnabled(enabled: Boolean) {
    TODO("Implement setEnabled")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextEditor::onDecorate(SkCanvas* canvas, const TextInfo& tinfo) {
   *     const auto [sel_start, sel_end] = this->currentSelection();
   *
   *     fGlyphData.clear();
   *
   *     for (size_t i = 0; i < tinfo.fGlyphs.size(); ++i) {
   *         const auto& ginfo = tinfo.fGlyphs[i];
   *
   *         SkAutoCanvasRestore acr(canvas, true);
   *         canvas->concat(ginfo.fMatrix);
   *
   *         // Stash some glyph info, for later use.
   *         fGlyphData.push_back({
   *             canvas->getLocalToDevice().asM33().mapRect(ginfo.fBounds),
   *             ginfo.fCluster
   *         });
   *
   *         if (i < sel_start || i >= sel_end) {
   *             continue;
   *         }
   *
   *         static constexpr SkColor4f kSelectionColor{0, 0, 1, 0.4f};
   *         canvas->drawRect(ginfo.fBounds, SkPaint(kSelectionColor));
   *     }
   *
   *     // Only draw the cursor when there's no active selection.
   *     if (sel_start == sel_end) {
   *         this->drawCursor(canvas, tinfo);
   *     }
   * }
   * ```
   */
  public override fun onDecorate(canvas: SkCanvas?, tinfo: TextInfo) {
    TODO("Implement onDecorate")
  }

  /**
   * C++ original:
   * ```cpp
   * bool TextEditor::onMouseInput(SkScalar x, SkScalar y, skui::InputState state,
   *                                      skui::ModifierKey) {
   *     if (!fEnabled || fGlyphData.empty()) {
   *         return false;
   *     }
   *
   *     switch (state) {
   *     case skui::InputState::kDown: {
   *         fMouseDown = true;
   *
   *         const auto closest = this->closestGlyph({x, y});
   *         fSelection = {closest, closest};
   *     }   break;
   *     case skui::InputState::kUp:
   *         fMouseDown = false;
   *         break;
   *     case skui::InputState::kMove:
   *         if (fMouseDown) {
   *             const auto closest = this->closestGlyph({x, y});
   *             std::get<1>(fSelection) = closest < std::get<0>(fSelection)
   *                                             ? closest
   *                                             : closest + 1;
   *         }
   *         break;
   *     default:
   *         break;
   *     }
   *
   *     return true;
   * }
   * ```
   */
  public fun onMouseInput(
    x: SkScalar,
    y: SkScalar,
    state: InputState,
    param3: ModifierKey,
  ): Boolean {
    TODO("Implement onMouseInput")
  }

  /**
   * C++ original:
   * ```cpp
   * bool TextEditor::onCharInput(SkUnichar c) {
   *     if (!fEnabled || fGlyphData.empty()) {
   *         return false;
   *     }
   *
   *     const auto& txt_str = fTextProp->get().fText;
   *
   *     // Natural editor bindings are currently intercepted by Viewer, so we use these instead.
   *     switch (c) {
   *     case '|':     // commit changes and exit editing mode
   *         this->toggleEnabled();
   *         break;
   *     case ']': {   // move right
   *         if (fCursorIndex < txt_str.size()) {
   *             fCursorIndex = next_utf8(txt_str, fCursorIndex);
   *         }
   *     } break;
   *     case '[':     // move left
   *         if (fCursorIndex > 0) {
   *             fCursorIndex = prev_utf8(txt_str, fCursorIndex);
   *         }
   *         break;
   *     case '\\': {  // delete
   *         if (!this->deleteSelection() && fCursorIndex > 0) {
   *             // Delete preceding char.
   *             const auto del_index = prev_utf8(txt_str, fCursorIndex),
   *                        del_count = fCursorIndex - del_index;
   *
   *             this->deleteChars(del_index, del_count);
   *         }
   *     }   break;
   *     default:
   *         // Delete any selection on insert.
   *         this->deleteSelection();
   *         this->insertChar(c);
   *         break;
   *     }
   *
   *     // Reset the cursor blink timer on input.
   *     fTimeBase = std::chrono::steady_clock::now();
   *
   *     return true;
   * }
   * ```
   */
  public fun onCharInput(c: SkUnichar): Boolean {
    TODO("Implement onCharInput")
  }

  /**
   * C++ original:
   * ```cpp
   * void setCursorWeight(float w) { fCursorWeight = w; }
   * ```
   */
  public fun setCursorWeight(w: Float) {
    TODO("Implement setCursorWeight")
  }

  /**
   * C++ original:
   * ```cpp
   * std::tuple<size_t, size_t> TextEditor::currentSelection() const {
   *     // Selection can be inverted.
   *     return std::make_tuple(std::min(std::get<0>(fSelection), std::get<1>(fSelection)),
   *                            std::max(std::get<0>(fSelection), std::get<1>(fSelection)));
   * }
   * ```
   */
  private fun currentSelection(): Int {
    TODO("Implement currentSelection")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t TextEditor::closestGlyph(const SkPoint& pt) const {
   *     float  min_distance = std::numeric_limits<float>::max();
   *     size_t min_index    = 0;
   *
   *     for (size_t i = 0; i < fGlyphData.size(); ++i) {
   *         const auto dist = (fGlyphData[i].fDevBounds.center() - pt).length();
   *         if (dist < min_distance) {
   *             min_distance = dist;
   *             min_index = i;
   *         }
   *     }
   *
   *     return min_index;
   * }
   * ```
   */
  private fun closestGlyph(pt: SkPoint): Int {
    TODO("Implement closestGlyph")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextEditor::drawCursor(SkCanvas* canvas, const TextInfo& tinfo) const {
   *     constexpr double kCursorHz = 2;
   *     const auto now_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
   *                             std::chrono::steady_clock::now() - fTimeBase).count();
   *     const long cycle = static_cast<long>(static_cast<double>(now_ms) * 0.001 * kCursorHz);
   *     if (cycle & 1) {
   *         // blink
   *         return;
   *     }
   *
   *     auto txt_prop  = fTextProp->get();
   *
   *     const auto glyph_index = [&]() -> size_t {
   *         if (!fCursorIndex) {
   *             return 0;
   *         }
   *
   *         const auto prev_index = prev_utf8(txt_prop.fText, fCursorIndex);
   *         for (size_t i = 0; i < tinfo.fGlyphs.size(); ++i) {
   *             if (tinfo.fGlyphs[i].fCluster >= prev_index) {
   *                 return i;
   *             }
   *         }
   *
   *         return tinfo.fGlyphs.size() - 1;
   *     }();
   *
   *     // Cursor index mapping:
   *     //   0 -> before the first char
   *     //   1 -> after the first char
   *     //   2 -> after the second char
   *     //   ...
   *     // The cursor is bottom-aligned to the baseline (y = 0), and horizontally centered to the right
   *     // of the glyph advance.
   *     const auto cscale = txt_prop.fTextSize * tinfo.fScale,
   *                 cxpos = (fCursorIndex ? tinfo.fGlyphs[glyph_index].fAdvance : 0)
   *                          - fCursorBounds.width() * cscale * 0.5f,
   *                 cypos = - fCursorBounds.height() * cscale;
   *     const auto cpath  = fCursorPath.makeTransform(SkMatrix::Translate(cxpos, cypos) *
   *                                                   SkMatrix::Scale(cscale, cscale));
   *
   *     // We stroke the cursor twice, with different colors, to ensure reasonable contrast
   *     // regardless of background.
   *     // The default inner stroke width is .5px for a font size of 10, and scales proportionally.
   *     // The outer stroke width is slightly larger.
   *     const auto inner_width = cscale * fCursorWeight * 0.05f,
   *                outer_width = inner_width * 3 / 2;
   *
   *     SkPaint p;
   *     p.setAntiAlias(true);
   *     p.setStyle(SkPaint::kStroke_Style);
   *     p.setStrokeCap(SkPaint::kRound_Cap);
   *
   *     SkAutoCanvasRestore acr(canvas, true);
   *     canvas->concat(tinfo.fGlyphs[glyph_index].fMatrix);
   *
   *     p.setColor(SK_ColorWHITE);
   *     p.setStrokeWidth(outer_width);
   *     canvas->drawPath(cpath, p);
   *     p.setColor(SK_ColorBLACK);
   *     p.setStrokeWidth(inner_width);
   *     canvas->drawPath(cpath, p);
   * }
   * ```
   */
  private fun drawCursor(canvas: SkCanvas?, tinfo: TextInfo) {
    TODO("Implement drawCursor")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextEditor::insertChar(SkUnichar c) {
   *     auto txt = fTextProp->get();
   *     const auto initial_size = txt.fText.size();
   *
   *     txt.fText.insertUnichar(fCursorIndex, c);
   *     fCursorIndex += txt.fText.size() - initial_size;
   *
   *     fTextProp->set(txt);
   *     this->updateDeps(txt.fText);
   * }
   * ```
   */
  private fun insertChar(c: SkUnichar) {
    TODO("Implement insertChar")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextEditor::deleteChars(size_t offset, size_t count) {
   *     auto txt = fTextProp->get();
   *
   *     txt.fText.remove(offset, count);
   *     fTextProp->set(txt);
   *     this->updateDeps(txt.fText);
   *
   *     fCursorIndex = offset;
   * }
   * ```
   */
  private fun deleteChars(offset: ULong, count: ULong) {
    TODO("Implement deleteChars")
  }

  /**
   * C++ original:
   * ```cpp
   * bool TextEditor::deleteSelection() {
   *     const auto [glyph_sel_start, glyph_sel_end] = this->currentSelection();
   *     if (glyph_sel_start == glyph_sel_end) {
   *         return false;
   *     }
   *
   *     const auto utf8_sel_start = fGlyphData[glyph_sel_start].fCluster,
   *                utf8_sel_end   = fGlyphData[glyph_sel_end  ].fCluster;
   *     SkASSERT(utf8_sel_start < utf8_sel_end);
   *
   *     this->deleteChars(utf8_sel_start, utf8_sel_end - utf8_sel_start);
   *
   *     fSelection = {0,0};
   *
   *     return true;
   * }
   * ```
   */
  private fun deleteSelection(): Boolean {
    TODO("Implement deleteSelection")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextEditor::updateDeps(const SkString& txt) {
   *     for (const auto& dep : fDependentProps) {
   *         auto txt_prop = dep->get();
   *         txt_prop.fText = txt;
   *         dep->set(txt_prop);
   *     }
   * }
   * ```
   */
  private fun updateDeps(txt: String) {
    TODO("Implement updateDeps")
  }

  public data class GlyphData public constructor(
    public var fDevBounds: Int,
    public var fCluster: Int,
  )
}
