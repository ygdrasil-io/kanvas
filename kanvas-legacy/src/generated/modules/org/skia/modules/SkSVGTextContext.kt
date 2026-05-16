package org.skia.modules

import kotlin.Any
import kotlin.Boolean
import kotlin.Float
import kotlin.FloatArray
import kotlin.Int
import kotlin.String
import kotlin.ULong
import org.skia.foundation.SkFont
import org.skia.foundation.SkGlyphID
import org.skia.foundation.SkUnichar
import org.skia.math.SkPoint
import org.skia.math.SkRSXform
import undefined.RunInfo
import undefined.ShapedTextCallback

/**
 * C++ original:
 * ```cpp
 * class SkSVGTextContext final : SkShaper::RunHandler {
 * public:
 *     using ShapedTextCallback = std::function<void(const SkSVGRenderContext&,
 *                                                   const sk_sp<SkTextBlob>&,
 *                                                   const SkPaint*,
 *                                                   const SkPaint*)>;
 *
 *     // Helper for encoding optional positional attributes.
 *     class PosAttrs {
 *     public:
 *         // TODO: rotate
 *         enum Attr : size_t {
 *             kX      = 0,
 *             kY      = 1,
 *             kDx     = 2,
 *             kDy     = 3,
 *             kRotate = 4,
 *         };
 *
 *         float  operator[](Attr a) const { return fStorage[a]; }
 *         float& operator[](Attr a)       { return fStorage[a]; }
 *
 *         bool has(Attr a) const { return fStorage[a] != kNone; }
 *         bool hasAny()    const {
 *             return this->has(kX)
 *                 || this->has(kY)
 *                 || this->has(kDx)
 *                 || this->has(kDy)
 *                 || this->has(kRotate);
 *         }
 *
 *         void setImplicitRotate(bool imp) { fImplicitRotate = imp; }
 *         bool isImplicitRotate() const { return fImplicitRotate; }
 *
 *     private:
 *         inline static constexpr auto kNone = std::numeric_limits<float>::infinity();
 *
 *         float fStorage[5]     = { kNone, kNone, kNone, kNone, kNone };
 *         bool  fImplicitRotate = false;
 *     };
 *
 *     // Helper for cascading position attribute resolution (x, y, dx, dy, rotate) [1]:
 *     //   - each text position element can specify an arbitrary-length attribute array
 *     //   - for each character, we look up a given attribute first in its local attribute array,
 *     //     then in the ancestor chain (cascading/fallback) - and return the first value encountered.
 *     //   - the lookup is based on character index relative to the text content subtree
 *     //     (i.e. the index crosses chunk boundaries)
 *     //
 *     // [1] https://www.w3.org/TR/SVG11/text.html#TSpanElementXAttribute
 *     class ScopedPosResolver {
 *     public:
 *         ScopedPosResolver(const SkSVGTextContainer&, const SkSVGLengthContext&, SkSVGTextContext*,
 *                           size_t);
 *
 *         ScopedPosResolver(const SkSVGTextContainer&, const SkSVGLengthContext&, SkSVGTextContext*);
 *
 *         ~ScopedPosResolver();
 *
 *         PosAttrs resolve(size_t charIndex) const;
 *
 *     private:
 *         SkSVGTextContext*         fTextContext;
 *         const ScopedPosResolver*  fParent;          // parent resolver (fallback)
 *         const size_t              fCharIndexOffset; // start index for the current resolver
 *         const std::vector<float>  fX,
 *                                   fY,
 *                                   fDx,
 *                                   fDy;
 *         const std::vector<float>& fRotate;
 *
 *         // cache for the last known index with explicit positioning
 *         mutable size_t           fLastPosIndex = std::numeric_limits<size_t>::max();
 *
 *     };
 *
 *     SkSVGTextContext(const SkSVGRenderContext&,
 *                      const ShapedTextCallback&,
 *                      const SkSVGTextPath* = nullptr);
 *     ~SkSVGTextContext() override;
 *
 *     // Shape and queue codepoints for final alignment.
 *     void shapeFragment(const SkString&, const SkSVGRenderContext&, SkSVGXmlSpace);
 *
 *     // Perform final adjustments and push shaped blobs to the callback.
 *     void flushChunk(const SkSVGRenderContext& ctx);
 *
 *     const ShapedTextCallback& getCallback() const { return fCallback; }
 *
 * private:
 *     struct PositionAdjustment {
 *         SkVector offset;
 *         float    rotation;
 *     };
 *
 *     struct ShapeBuffer {
 *         skia_private::STArray<128, char              , true> fUtf8;
 *         // per-utf8-char cumulative pos adjustments
 *         skia_private::STArray<128, PositionAdjustment, true> fUtf8PosAdjust;
 *
 *         void reserve(size_t size) {
 *             fUtf8.reserve_exact(fUtf8.size() + SkToInt(size));
 *             fUtf8PosAdjust.reserve_exact(fUtf8PosAdjust.size() + SkToInt(size));
 *         }
 *
 *         void reset() {
 *             fUtf8.clear();
 *             fUtf8PosAdjust.clear();
 *         }
 *
 *         void append(SkUnichar, PositionAdjustment);
 *     };
 *
 *     struct RunRec {
 *         SkFont                                font;
 *         std::unique_ptr<SkPaint>              fillPaint,
 *                                               strokePaint;
 *         std::unique_ptr<SkGlyphID[]>          glyphs;        // filled by SkShaper
 *         std::unique_ptr<SkPoint[]>            glyphPos;      // filled by SkShaper
 *         std::unique_ptr<PositionAdjustment[]> glyhPosAdjust; // deferred positioning adjustments
 *         size_t                                glyphCount;
 *         SkVector                              advance;
 *     };
 *
 *     // Caches path information to accelerate position lookups.
 *     class PathData {
 *     public:
 *         PathData(const SkSVGRenderContext&, const SkSVGTextPath&);
 *
 *         SkMatrix getMatrixAt(float offset) const;
 *
 *         float length() const { return fLength; }
 *
 *     private:
 *         std::vector<sk_sp<SkContourMeasure>> fContours;
 *         float                                fLength = 0; // total path length
 *     };
 *
 *     void shapePendingBuffer(const SkSVGRenderContext&, const SkFont&);
 *
 *     SkRSXform computeGlyphXform(SkGlyphID, const SkFont&, const SkPoint& glyph_pos,
 *                                 const PositionAdjustment&) const;
 *
 *     // SkShaper callbacks
 *     void beginLine() override {}
 *     void runInfo(const RunInfo&) override {}
 *     void commitRunInfo() override {}
 *     Buffer runBuffer(const RunInfo& ri) override;
 *     void commitRunBuffer(const RunInfo& ri) override;
 *     void commitLine() override;
 *
 *     // http://www.w3.org/TR/SVG11/text.html#TextLayout
 *     const SkSVGRenderContext&       fRenderContext; // original render context
 *     const ShapedTextCallback&       fCallback;
 *     std::unique_ptr<SkShaper>       fShaper;
 *     std::vector<RunRec>             fRuns;
 *     const ScopedPosResolver*        fPosResolver = nullptr;
 *     std::unique_ptr<PathData>       fPathData;
 *
 *     // shaper state
 *     ShapeBuffer                     fShapeBuffer;
 *     std::vector<uint32_t>           fShapeClusterBuffer;
 *
 *     // chunk state
 *     SkPoint                         fChunkPos     = {0,0}; // current text chunk position
 *     SkVector                        fChunkAdvance = {0,0}; // cumulative advance
 *     float                           fChunkAlignmentFactor; // current chunk alignment
 *
 *     // tracks the global text subtree char index (cross chunks).  Used for position resolution.
 *     size_t                          fCurrentCharIndex = 0;
 *
 *     // cached for access from SkShaper callbacks.
 *     std::optional<SkPaint>          fCurrentFill;
 *     std::optional<SkPaint>          fCurrentStroke;
 *
 *     bool                            fPrevCharSpace = true; // WS filter state
 *     bool                            fForcePrimitiveShaping = false;
 * }
 * ```
 */
public class SkSVGTextContext public constructor(
  ctx: SkSVGRenderContext,
  cb: ShapedTextCallback,
  tpath: SkSVGTextPath? = TODO(),
) : SkShaper.RunHandler() {
  /**
   * C++ original:
   * ```cpp
   * const SkSVGRenderContext&       fRenderContext
   * ```
   */
  private val fRenderContext: SkSVGRenderContext = TODO("Initialize fRenderContext")

  /**
   * C++ original:
   * ```cpp
   * const ShapedTextCallback&       fCallback
   * ```
   */
  private val fCallback: Int = TODO("Initialize fCallback")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkShaper>       fShaper
   * ```
   */
  private var fShaper: Int = TODO("Initialize fShaper")

  /**
   * C++ original:
   * ```cpp
   * std::vector<RunRec>             fRuns
   * ```
   */
  private var fRuns: Int = TODO("Initialize fRuns")

  /**
   * C++ original:
   * ```cpp
   * const ScopedPosResolver*        fPosResolver = nullptr
   * ```
   */
  private val fPosResolver: ScopedPosResolver? = TODO("Initialize fPosResolver")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<PathData>       fPathData
   * ```
   */
  private var fPathData: Int = TODO("Initialize fPathData")

  /**
   * C++ original:
   * ```cpp
   * ShapeBuffer                     fShapeBuffer
   * ```
   */
  private var fShapeBuffer: ShapeBuffer = TODO("Initialize fShapeBuffer")

  /**
   * C++ original:
   * ```cpp
   * std::vector<uint32_t>           fShapeClusterBuffer
   * ```
   */
  private var fShapeClusterBuffer: Int = TODO("Initialize fShapeClusterBuffer")

  /**
   * C++ original:
   * ```cpp
   * SkPoint                         fChunkPos
   * ```
   */
  private var fChunkPos: Int = TODO("Initialize fChunkPos")

  /**
   * C++ original:
   * ```cpp
   * SkVector                        fChunkAdvance
   * ```
   */
  private var fChunkAdvance: Int = TODO("Initialize fChunkAdvance")

  /**
   * C++ original:
   * ```cpp
   * float                           fChunkAlignmentFactor
   * ```
   */
  private var fChunkAlignmentFactor: Float = TODO("Initialize fChunkAlignmentFactor")

  /**
   * C++ original:
   * ```cpp
   * size_t                          fCurrentCharIndex
   * ```
   */
  private var fCurrentCharIndex: Int = TODO("Initialize fCurrentCharIndex")

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkPaint>          fCurrentFill
   * ```
   */
  private var fCurrentFill: Int = TODO("Initialize fCurrentFill")

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkPaint>          fCurrentStroke
   * ```
   */
  private var fCurrentStroke: Int = TODO("Initialize fCurrentStroke")

  /**
   * C++ original:
   * ```cpp
   * bool                            fPrevCharSpace = true
   * ```
   */
  private var fPrevCharSpace: Boolean = TODO("Initialize fPrevCharSpace")

  /**
   * C++ original:
   * ```cpp
   * bool                            fForcePrimitiveShaping = false
   * ```
   */
  private var fForcePrimitiveShaping: Boolean = TODO("Initialize fForcePrimitiveShaping")

  /**
   * C++ original:
   * ```cpp
   * void SkSVGTextContext::shapeFragment(const SkString& txt, const SkSVGRenderContext& ctx,
   *                                      SkSVGXmlSpace xs) {
   *     // https://www.w3.org/TR/SVG11/text.html#WhiteSpace
   *     // https://www.w3.org/TR/2008/REC-xml-20081126/#NT-S
   *     auto filterWSDefault = [this](SkUnichar ch) -> SkUnichar {
   *         // Remove all newline chars.
   *         if (ch == '\n') {
   *             return -1;
   *         }
   *
   *         // Convert tab chars to space.
   *         if (ch == '\t') {
   *             ch = ' ';
   *         }
   *
   *         // Consolidate contiguous space chars and strip leading spaces (fPrevCharSpace
   *         // starts off as true).
   *         if (fPrevCharSpace && ch == ' ') {
   *             return -1;
   *         }
   *
   *         // TODO: Strip trailing WS?  Doing this across chunks would require another buffering
   *         //   layer.  In general, trailing WS should have no rendering side effects. Skipping
   *         //   for now.
   *         return ch;
   *     };
   *     auto filterWSPreserve = [](SkUnichar ch) -> SkUnichar {
   *         // Convert newline and tab chars to space.
   *         if (ch == '\n' || ch == '\t') {
   *             ch = ' ';
   *         }
   *         return ch;
   *     };
   *
   *     // Stash paints for access from SkShaper callbacks.
   *     fCurrentFill   = ctx.fillPaint();
   *     fCurrentStroke = ctx.strokePaint();
   *
   *     const auto font = ResolveFont(ctx);
   *     fShapeBuffer.reserve(txt.size());
   *
   *     const char* ch_ptr = txt.c_str();
   *     const char* ch_end = ch_ptr + txt.size();
   *
   *     while (ch_ptr < ch_end) {
   *         auto ch = SkUTF::NextUTF8(&ch_ptr, ch_end);
   *         ch = (xs == SkSVGXmlSpace::kDefault)
   *                 ? filterWSDefault(ch)
   *                 : filterWSPreserve(ch);
   *
   *         if (ch < 0) {
   *             // invalid utf or char filtered out
   *             continue;
   *         }
   *
   *         SkASSERT(fPosResolver);
   *         const auto pos = fPosResolver->resolve(fCurrentCharIndex++);
   *
   *         // Absolute position adjustments define a new chunk.
   *         // (https://www.w3.org/TR/SVG11/text.html#TextLayoutIntroduction)
   *         if (pos.has(PosAttrs::kX) || pos.has(PosAttrs::kY)) {
   *             this->shapePendingBuffer(ctx, font);
   *             this->flushChunk(ctx);
   *
   *             // New chunk position.
   *             if (pos.has(PosAttrs::kX)) {
   *                 fChunkPos.fX = pos[PosAttrs::kX];
   *             }
   *             if (pos.has(PosAttrs::kY)) {
   *                 fChunkPos.fY = pos[PosAttrs::kY];
   *             }
   *         }
   *
   *         fShapeBuffer.append(ch, {
   *             {
   *                 pos.has(PosAttrs::kDx) ? pos[PosAttrs::kDx] : 0,
   *                 pos.has(PosAttrs::kDy) ? pos[PosAttrs::kDy] : 0,
   *             },
   *             pos.has(PosAttrs::kRotate) ? SkDegreesToRadians(pos[PosAttrs::kRotate]) : 0,
   *         });
   *
   *         fPrevCharSpace = (ch == ' ');
   *     }
   *
   *     this->shapePendingBuffer(ctx, font);
   *
   *     // Note: at this point we have shaped and buffered RunRecs for the current fragment.
   *     // The active text chunk continues until an explicit or implicit flush.
   * }
   * ```
   */
  private fun shapeFragment(
    txt: String,
    ctx: SkSVGRenderContext,
    xs: SkSVGXmlSpace,
  ) {
    TODO("Implement shapeFragment")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSVGTextContext::flushChunk(const SkSVGRenderContext& ctx) {
   *     SkTextBlobBuilder blobBuilder;
   *
   *     for (const auto& run : fRuns) {
   *         const auto& buf = blobBuilder.allocRunRSXform(run.font, SkToInt(run.glyphCount));
   *         std::copy(run.glyphs.get(), run.glyphs.get() + run.glyphCount, buf.glyphs);
   *         for (size_t i = 0; i < run.glyphCount; ++i) {
   *             buf.xforms()[i] = this->computeGlyphXform(run.glyphs[i],
   *                                                       run.font,
   *                                                       run.glyphPos[i],
   *                                                       run.glyhPosAdjust[i]);
   *         }
   *
   *         fCallback(ctx, blobBuilder.make(), run.fillPaint.get(), run.strokePaint.get());
   *     }
   *
   *     fChunkPos += fChunkAdvance;
   *     fChunkAdvance = {0,0};
   *     fChunkAlignmentFactor = ComputeAlignmentFactor(ctx.presentationContext());
   *
   *     fRuns.clear();
   * }
   * ```
   */
  private fun flushChunk(ctx: SkSVGRenderContext) {
    TODO("Implement flushChunk")
  }

  /**
   * C++ original:
   * ```cpp
   * const ShapedTextCallback& getCallback() const { return fCallback; }
   * ```
   */
  private fun getCallback(): Int {
    TODO("Implement getCallback")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSVGTextContext::shapePendingBuffer(const SkSVGRenderContext& ctx, const SkFont& font) {
   *     const char* utf8 = fShapeBuffer.fUtf8.data();
   *     size_t utf8Bytes = fShapeBuffer.fUtf8.size();
   *
   *     std::unique_ptr<SkShaper::FontRunIterator> font_runs =
   *             SkShaper::MakeFontMgrRunIterator(utf8, utf8Bytes, font, ctx.fontMgr());
   *     if (!font_runs) {
   *         return;
   *     }
   *     if (!fForcePrimitiveShaping) {
   *         // Try to use the passed in shaping callbacks to shape, for example, using harfbuzz and ICU.
   *         const uint8_t defaultLTR = 0;
   *         std::unique_ptr<SkShaper::BiDiRunIterator> bidi =
   *                 ctx.makeBidiRunIterator(utf8, utf8Bytes, defaultLTR);
   *         std::unique_ptr<SkShaper::LanguageRunIterator> language =
   *                 SkShaper::MakeStdLanguageRunIterator(utf8, utf8Bytes);
   *         std::unique_ptr<SkShaper::ScriptRunIterator> script = ctx.makeScriptRunIterator(utf8, utf8Bytes);
   *
   *         if (bidi && script && language) {
   *             fShaper->shape(utf8,
   *                            utf8Bytes,
   *                            *font_runs,
   *                            *bidi,
   *                            *script,
   *                            *language,
   *                            nullptr,
   *                            0,
   *                            SK_ScalarMax,
   *                            this);
   *             fShapeBuffer.reset();
   *             return;
   *         }  // If any of the callbacks fail, we'll fallback to the primitive shaping.
   *     }
   *
   *     // bidi, script, and lang are all unused so we can construct them with empty data.
   *     SkShaper::TrivialBiDiRunIterator trivial_bidi{0, 0};
   *     SkShaper::TrivialScriptRunIterator trivial_script{0, 0};
   *     SkShaper::TrivialLanguageRunIterator trivial_lang{nullptr, 0};
   *     fShaper->shape(utf8,
   *                    utf8Bytes,
   *                    *font_runs,
   *                    trivial_bidi,
   *                    trivial_script,
   *                    trivial_lang,
   *                    nullptr,
   *                    0,
   *                    SK_ScalarMax,
   *                    this);
   *     fShapeBuffer.reset();
   * }
   * ```
   */
  private fun shapePendingBuffer(ctx: SkSVGRenderContext, font: SkFont) {
    TODO("Implement shapePendingBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRSXform SkSVGTextContext::computeGlyphXform(SkGlyphID glyph, const SkFont& font,
   *                                               const SkPoint& glyph_pos,
   *                                               const PositionAdjustment& pos_adjust) const {
   *     SkPoint pos = fChunkPos + glyph_pos + pos_adjust.offset + fChunkAdvance * fChunkAlignmentFactor;
   *     if (!fPathData) {
   *         return SkRSXform::MakeFromRadians(/*scale=*/ 1, pos_adjust.rotation, pos.fX, pos.fY, 0, 0);
   *     }
   *
   *     // We're in a textPath scope, reposition the glyph on path.
   *     // (https://www.w3.org/TR/SVG11/text.html#TextpathLayoutRules)
   *
   *     // Path positioning is based on the glyph center (horizontal component).
   *     float glyph_width = font.getWidth(glyph);
   *     auto path_offset = pos.fX + glyph_width * .5f;
   *
   *     // In addition to the path matrix, the final glyph matrix also includes:
   *     //
   *     //   -- vertical position adjustment "dy" ("dx" is factored into path_offset)
   *     //   -- glyph origin adjustment (undoing the glyph center offset above)
   *     //   -- explicit rotation adjustment (composing with the path glyph rotation)
   *     const auto m = fPathData->getMatrixAt(path_offset) *
   *             SkMatrix::Translate(-glyph_width * .5f, pos_adjust.offset.fY) *
   *             SkMatrix::RotateRad(pos_adjust.rotation);
   *
   *     return SkRSXform::Make(m.getScaleX(), m.getSkewY(), m.getTranslateX(), m.getTranslateY());
   * }
   * ```
   */
  private fun computeGlyphXform(
    glyph: SkGlyphID,
    font: SkFont,
    glyphPos: SkPoint,
    posAdjust: PositionAdjustment,
  ): SkRSXform {
    TODO("Implement computeGlyphXform")
  }

  /**
   * C++ original:
   * ```cpp
   * void beginLine() override {}
   * ```
   */
  public override fun beginLine() {
    TODO("Implement beginLine")
  }

  /**
   * C++ original:
   * ```cpp
   * void runInfo(const RunInfo&) override {}
   * ```
   */
  public override fun runInfo(param0: RunInfo) {
    TODO("Implement runInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * void commitRunInfo() override {}
   * ```
   */
  public override fun commitRunInfo() {
    TODO("Implement commitRunInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkShaper::RunHandler::Buffer SkSVGTextContext::runBuffer(const RunInfo& ri) {
   *     SkASSERT(ri.glyphCount);
   *
   *     fRuns.push_back({
   *         ri.fFont,
   *         fCurrentFill.has_value()   ? std::make_unique<SkPaint>(*fCurrentFill)   : nullptr,
   *         fCurrentStroke.has_value() ? std::make_unique<SkPaint>(*fCurrentStroke) : nullptr,
   *         std::make_unique<SkGlyphID[]         >(ri.glyphCount),
   *         std::make_unique<SkPoint[]           >(ri.glyphCount),
   *         std::make_unique<PositionAdjustment[]>(ri.glyphCount),
   *         ri.glyphCount,
   *         ri.fAdvance,
   *     });
   *
   *     // Ensure sufficient space to temporarily fetch cluster information.
   *     fShapeClusterBuffer.resize(std::max(fShapeClusterBuffer.size(), ri.glyphCount));
   *
   *     return {
   *         fRuns.back().glyphs.get(),
   *         fRuns.back().glyphPos.get(),
   *         nullptr,
   *         fShapeClusterBuffer.data(),
   *         fChunkAdvance,
   *     };
   * }
   * ```
   */
  public override fun runBuffer(ri: RunInfo): Int {
    TODO("Implement runBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSVGTextContext::commitRunBuffer(const RunInfo& ri) {
   *     const auto& current_run = fRuns.back();
   *
   *     // stash position adjustments
   *     for (size_t i = 0; i < ri.glyphCount; ++i) {
   *         const auto utf8_index = fShapeClusterBuffer[i];
   *         current_run.glyhPosAdjust[i] = fShapeBuffer.fUtf8PosAdjust[SkToInt(utf8_index)];
   *     }
   *
   *     fChunkAdvance += ri.fAdvance;
   * }
   * ```
   */
  public override fun commitRunBuffer(ri: RunInfo) {
    TODO("Implement commitRunBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSVGTextContext::commitLine() {
   *     if (!fShapeBuffer.fUtf8PosAdjust.empty()) {
   *         // Offset adjustments are cumulative - only advance the current chunk with the last value.
   *         fChunkAdvance += fShapeBuffer.fUtf8PosAdjust.back().offset;
   *     }
   * }
   * ```
   */
  public override fun commitLine() {
    TODO("Implement commitLine")
  }

  public data class PosAttrs public constructor(
    private var fStorage: FloatArray,
    private var fImplicitRotate: Boolean,
  ) {
    public operator fun `get`(a: PosAttrs.Attr): Float {
      TODO("Implement get")
    }

    public fun has(a: PosAttrs.Attr): Boolean {
      TODO("Implement has")
    }

    public fun hasAny(): Boolean {
      TODO("Implement hasAny")
    }

    public fun setImplicitRotate(imp: Boolean) {
      TODO("Implement setImplicitRotate")
    }

    public fun isImplicitRotate(): Boolean {
      TODO("Implement isImplicitRotate")
    }

    public enum class Attr

    public companion object {
      private val kNone: Any = TODO("Initialize kNone")
    }
  }

  public data class ScopedPosResolver public constructor(
    private var fTextContext: SkSVGTextContext?,
    private val fParent: ScopedPosResolver?,
    private val fCharIndexOffset: Int,
    private val fX: Int,
    private val fY: Int,
    private val fDx: Int,
    private val fDy: Int,
    private val fRotate: Int,
    private var fLastPosIndex: Int,
  ) {
    public fun resolve(charIndex: ULong): undefined.PosAttrs {
      TODO("Implement resolve")
    }
  }

  public data class PositionAdjustment public constructor(
    public var offset: Int,
    public var rotation: Float,
  )

  public data class ShapeBuffer public constructor(
    public var fUtf8: Int,
    public var fUtf8PosAdjust: Int,
  ) {
    public fun reserve(size: ULong) {
      TODO("Implement reserve")
    }

    public fun reset() {
      TODO("Implement reset")
    }

    public fun append(ch: SkUnichar, pos: undefined.PositionAdjustment) {
      TODO("Implement append")
    }
  }

  public data class RunRec public constructor(
    public var font: Int,
    public var fillPaint: Int,
    public var strokePaint: Int,
    public var glyphs: Int,
    public var glyphPos: Int,
    public var glyhPosAdjust: Int,
    public var glyphCount: Int,
    public var advance: Int,
  )

  public data class PathData public constructor(
    private var fContours: Int,
    private var fLength: Float,
  ) {
    public fun getMatrixAt(offset: Float): Int {
      TODO("Implement getMatrixAt")
    }

    public fun length(): Float {
      TODO("Implement length")
    }
  }
}
