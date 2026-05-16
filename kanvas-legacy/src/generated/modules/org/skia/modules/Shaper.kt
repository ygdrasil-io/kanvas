package org.skia.modules

import SkShapers.Factory
import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.ULong
import kotlin.collections.List
import org.skia.core.SkCanvas
import org.skia.foundation.SkFontMgr
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSp
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class Shaper final {
 * public:
 *     struct RunRec {
 *         SkFont fFont;
 *         size_t fSize;
 *
 *         static_assert(::sk_is_trivially_relocatable<decltype(fFont)>::value);
 *
 *         using sk_is_trivially_relocatable = std::true_type;
 *     };
 *
 *     struct ShapedGlyphs {
 *         std::vector<RunRec>    fRuns;
 *
 *         // Consolidated storage for all runs.
 *         std::vector<SkGlyphID> fGlyphIDs;
 *         std::vector<SkPoint>   fGlyphPos;
 *
 *         // fClusters[i] is an input string index, pointing to the start of the UTF sequence
 *         // associated with fGlyphs[i].  The number of entries matches the number of glyphs.
 *         // Only available with Flags::kClusters.
 *         std::vector<size_t>    fClusters;
 *
 *         enum class BoundsType { kConservative, kTight };
 *         SkRect computeBounds(BoundsType) const;
 *
 *         void draw(SkCanvas*, const SkPoint& origin, const SkPaint&) const;
 *     };
 *
 *     struct Fragment {
 *         ShapedGlyphs fGlyphs;
 *         SkPoint      fOrigin;
 *
 *         // Only valid for kFragmentGlyphs
 *         float        fAdvance,
 *                      fAscent;
 *         uint32_t     fLineIndex;    // 0-based index for the line this fragment belongs to.
 *         bool         fIsWhitespace; // True if the first code point in the corresponding
 *                                     // cluster is whitespace.
 *     };
 *
 *     struct Result {
 *         std::vector<Fragment> fFragments;
 *         size_t                fMissingGlyphCount = 0;
 *         // Relative text size scale, when using an auto-scaling ResizePolicy
 *         // (otherwise 1.0).  This is informative of the final text size, and is
 *         // not required to render the Result.
 *         float                 fScale = 1.0f;
 *
 *         SkRect computeVisualBounds() const;
 *     };
 *
 *     enum class VAlign : uint8_t {
 *         // Align the first line typographical top with the text box top (AE box text).
 *         kTop,
 *         // Align the first line typographical baseline with the text box top (AE point text).
 *         kTopBaseline,
 *
 *         // Skottie vertical alignment extensions
 *
 *         // These are based on a hybrid extent box defined (in Y) as
 *         //
 *         //   ------------------------------------------------------
 *         //   MIN(visual_top_extent   , typographical_top_extent   )
 *         //
 *         //                         ...
 *         //
 *         //   MAX(visual_bottom_extent, typographical_bottom_extent)
 *         //   ------------------------------------------------------
 *         kHybridTop,     // extent box top    -> text box top
 *         kHybridCenter,  // extent box center -> text box center
 *         kHybridBottom,  // extent box bottom -> text box bottom
 *
 *         // Visual alignement modes -- these are using tight visual bounds for the paragraph.
 *         kVisualTop,     // visual top    -> text box top
 *         kVisualCenter,  // visual center -> text box center
 *         kVisualBottom,  // visual bottom -> text box bottom
 *     };
 *
 *     enum class ResizePolicy : uint8_t {
 *         // Use the specified text size.
 *         kNone,
 *         // Resize the text such that the extent box fits (snuggly) in the text box,
 *         // both horizontally and vertically.
 *         kScaleToFit,
 *         // Same kScaleToFit if the text doesn't fit at the specified font size.
 *         // Otherwise, same as kNone.
 *         kDownscaleToFit,
 *     };
 *
 *     enum class LinebreakPolicy : uint8_t {
 *         // Break lines such that they fit in a non-empty paragraph box, horizontally.
 *         kParagraph,
 *         // Only break lines when requested explicitly (\r), regardless of paragraph box dimensions.
 *         kExplicit,
 *     };
 *
 *     // Initial text direction.
 *     enum class Direction : uint8_t { kLTR, kRTL };
 *
 *     enum class Capitalization {
 *         kNone,
 *         kUpperCase,
 *     };
 *
 *     enum Flags : uint32_t {
 *         kNone                       = 0x00,
 *
 *         // Split out individual glyphs into separate Fragments
 *         // (useful when the caller intends to manipulate glyphs independently).
 *         kFragmentGlyphs             = 0x01,
 *
 *         // Compute the advance and ascent for each fragment.
 *         kTrackFragmentAdvanceAscent = 0x02,
 *
 *         // Return cluster information.
 *         kClusters                   = 0x04,
 *     };
 *
 *     struct TextDesc {
 *         const sk_sp<SkTypeface>&  fTypeface;
 *         SkScalar                  fTextSize       = 0,
 *                                   fMinTextSize    = 0,  // when auto-sizing
 *                                   fMaxTextSize    = 0,  // when auto-sizing
 *                                   fLineHeight     = 0,
 *                                   fLineShift      = 0,
 *                                   fAscent         = 0;
 *         SkTextUtils::Align        fHAlign         = SkTextUtils::kLeft_Align;
 *         VAlign                    fVAlign         = Shaper::VAlign::kTop;
 *         ResizePolicy              fResize         = Shaper::ResizePolicy::kNone;
 *         LinebreakPolicy           fLinebreak      = Shaper::LinebreakPolicy::kExplicit;
 *         Direction                 fDirection      = Shaper::Direction::kLTR ;
 *         Capitalization            fCapitalization = Shaper::Capitalization::kNone;
 *         size_t                    fMaxLines       = 0;  // when auto-sizing, 0 -> no max
 *         uint32_t                  fFlags          = 0;
 *         const char*               fLocale         = nullptr;
 *         const char*               fFontFamily     = nullptr;
 *     };
 *
 *     // Performs text layout along an infinite horizontal line, starting at |point|.
 *     // Only explicit line breaks (\r) are observed.
 *     static Result Shape(const SkString& text, const TextDesc& desc, const SkPoint& point,
 *                         const sk_sp<SkFontMgr>&, const sk_sp<SkShapers::Factory>&);
 *
 *     // Performs text layout within |box|, injecting line breaks as needed to ensure
 *     // horizontal fitting.  The result is *not* guaranteed to fit vertically (it may extend
 *     // below the box bottom).
 *     static Result Shape(const SkString& text, const TextDesc& desc, const SkRect& box,
 *                         const sk_sp<SkFontMgr>&, const sk_sp<SkShapers::Factory>&);
 *
 * #if !defined(SK_DISABLE_LEGACY_SHAPER_FACTORY)
 *     static Result Shape(const SkString& text, const TextDesc& desc, const SkPoint& point,
 *                         const sk_sp<SkFontMgr>&);
 *     static Result Shape(const SkString& text, const TextDesc& desc, const SkRect& box,
 *                         const sk_sp<SkFontMgr>&);
 * #endif
 *
 * private:
 *     Shaper() = delete;
 * }
 * ```
 */
public class Shaper public constructor() {
  /**
   * C++ original:
   * ```cpp
   * Shaper::Result Shaper::Shape(const SkString& text, const TextDesc& desc, const SkPoint& point,
   *                              const sk_sp<SkFontMgr>& fontmgr, const sk_sp<SkShapers::Factory>& shapingFactory) {
   *     const AdjustedText adjText(text, desc, shapingFactory->getUnicode());
   *
   *     return (desc.fResize == ResizePolicy::kScaleToFit ||
   *             desc.fResize == ResizePolicy::kDownscaleToFit) // makes no sense in point mode
   *             ? Result()
   *             : ShapeImpl(adjText, desc, SkRect::MakeEmpty().makeOffset(point.x(), point.y()),
   *                         fontmgr, shapingFactory, nullptr);
   * }
   * ```
   */
  public fun shape(
    text: String,
    desc: TextDesc,
    point: SkPoint,
    fontmgr: SkSp<SkFontMgr>,
    shapingFactory: SkSp<Factory>,
  ): Result {
    TODO("Implement shape")
  }

  /**
   * C++ original:
   * ```cpp
   * Shaper::Result Shaper::Shape(const SkString& text, const TextDesc& desc, const SkRect& box,
   *                              const sk_sp<SkFontMgr>& fontmgr, const sk_sp<SkShapers::Factory>& shapingFactory) {
   *     const AdjustedText adjText(text, desc, shapingFactory->getUnicode());
   *
   *     switch(desc.fResize) {
   *     case ResizePolicy::kNone:
   *         return ShapeImpl(adjText, desc, box, fontmgr, shapingFactory, nullptr);
   *     case ResizePolicy::kScaleToFit:
   *         return ShapeToFit(adjText, desc, box, fontmgr, shapingFactory);
   *     case ResizePolicy::kDownscaleToFit: {
   *         SkSize size;
   *         auto result = ShapeImpl(adjText, desc, box, fontmgr, shapingFactory, &size);
   *
   *         return result_fits(result, size, box, desc)
   *                 ? result
   *                 : ShapeToFit(adjText, desc, box, fontmgr, shapingFactory);
   *     }
   *     }
   *
   *     SkUNREACHABLE;
   * }
   * ```
   */
  public fun shape(
    text: String,
    desc: TextDesc,
    box: SkRect,
    fontmgr: SkSp<SkFontMgr>,
    shapingFactory: SkSp<Factory>,
  ): Result {
    TODO("Implement shape")
  }

  /**
   * C++ original:
   * ```cpp
   * Shaper::Result Shaper::Shape(const SkString& text, const TextDesc& desc, const SkPoint& point,
   *              const sk_sp<SkFontMgr>& fontmgr) {
   *     return Shaper::Shape(text, desc, point, fontmgr, SkShapers::BestAvailable());
   * }
   * ```
   */
  public fun shape(
    text: String,
    desc: TextDesc,
    point: SkPoint,
    fontmgr: SkSp<SkFontMgr>,
  ): Result {
    TODO("Implement shape")
  }

  /**
   * C++ original:
   * ```cpp
   * Shaper::Result Shaper::Shape(const SkString& text, const TextDesc& desc, const SkRect& box,
   *              const sk_sp<SkFontMgr>& fontmgr) {
   *     return Shaper::Shape(text, desc, box, fontmgr, SkShapers::BestAvailable());
   * }
   * ```
   */
  public fun shape(
    text: String,
    desc: TextDesc,
    box: SkRect,
    fontmgr: SkSp<SkFontMgr>,
  ): Result {
    TODO("Implement shape")
  }

  public data class RunRec public constructor(
    public var fFont: Int,
    public var fSize: ULong,
  )

  public data class ShapedGlyphs public constructor(
    public var fRuns: List<RunRec>,
    public var fGlyphIDs: Int,
    public var fGlyphPos: List<SkPaint>,
    public var fClusters: Int,
  ) {
    public fun computeBounds(btype: ShapedGlyphs.BoundsType): SkRect {
      TODO("Implement computeBounds")
    }

    public fun draw(
      canvas: SkCanvas?,
      origin: SkPoint,
      paint: SkPaint,
    ) {
      TODO("Implement draw")
    }

    public enum class BoundsType {
      kConservative,
      kTight,
    }
  }

  public data class Fragment public constructor(
    public var fGlyphs: undefined.ShapedGlyphs,
    public var fOrigin: Int,
    public var fAdvance: Float,
    public var fAscent: Float,
    public var fLineIndex: UInt,
    public var fIsWhitespace: Boolean,
  )

  public open class Result public constructor(
    public var fFragments: Int,
    public var fMissingGlyphCount: ULong,
    public var fScale: Float,
  ) {
    public fun computeVisualBounds(): SkRect {
      TODO("Implement computeVisualBounds")
    }
  }

  public data class TextDesc public constructor(
    public val fTypeface: Int,
    public var fTextSize: Int,
    public var fMinTextSize: Int,
    public var fMaxTextSize: Int,
    public var fLineHeight: Int,
    public var fLineShift: Int,
    public var fAscent: Int,
    public var fHAlign: Int,
    public var fVAlign: undefined.VAlign,
    public var fResize: undefined.ResizePolicy,
    public var fLinebreak: undefined.LinebreakPolicy,
    public var fDirection: undefined.Direction,
    public var fCapitalization: undefined.Capitalization,
    public var fMaxLines: ULong,
    public var fFlags: UInt,
    public val fLocale: String?,
    public val fFontFamily: String?,
  )

  public enum class VAlign {
    kTop,
    kTopBaseline,
    kHybridTop,
    kHybridCenter,
    kHybridBottom,
    kVisualTop,
    kVisualCenter,
    kVisualBottom,
  }

  public enum class ResizePolicy {
    kNone,
    kScaleToFit,
    kDownscaleToFit,
  }

  public enum class LinebreakPolicy {
    kParagraph,
    kExplicit,
  }

  public enum class Direction {
    kLTR,
    kRTL,
  }

  public enum class Capitalization {
    kNone,
    kUpperCase,
  }

  public enum class Flags {
    kNone,
    kFragmentGlyphs,
    kTrackFragmentAdvanceAscent,
    kClusters,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static Result Shape(const SkString& text, const TextDesc& desc, const SkPoint& point,
     *                         const sk_sp<SkFontMgr>&, const sk_sp<SkShapers::Factory>&)
     * ```
     */
    public fun shape(
      text: String,
      desc: TextDesc,
      point: SkPoint,
      param3: SkSp<SkFontMgr>,
      param4: SkSp<Factory>,
    ): Result {
      TODO("Implement shape")
    }

    /**
     * C++ original:
     * ```cpp
     * static Result Shape(const SkString& text, const TextDesc& desc, const SkRect& box,
     *                         const sk_sp<SkFontMgr>&, const sk_sp<SkShapers::Factory>&)
     * ```
     */
    public fun shape(
      text: String,
      desc: TextDesc,
      box: SkRect,
      param3: SkSp<SkFontMgr>,
      param4: SkSp<Factory>,
    ): Result {
      TODO("Implement shape")
    }

    /**
     * C++ original:
     * ```cpp
     * static Result Shape(const SkString& text, const TextDesc& desc, const SkPoint& point,
     *                         const sk_sp<SkFontMgr>&)
     * ```
     */
    public fun shape(
      text: String,
      desc: TextDesc,
      point: SkPoint,
      param3: SkSp<SkFontMgr>,
    ): Result {
      TODO("Implement shape")
    }

    /**
     * C++ original:
     * ```cpp
     * static Result Shape(const SkString& text, const TextDesc& desc, const SkRect& box,
     *                         const sk_sp<SkFontMgr>&)
     * ```
     */
    public fun shape(
      text: String,
      desc: TextDesc,
      box: SkRect,
      param3: SkSp<SkFontMgr>,
    ): Result {
      TODO("Implement shape")
    }
  }
}
