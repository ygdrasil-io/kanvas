package org.skia.modules

import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.collections.List
import org.skia.foundation.SkNVRefCnt
import org.skia.foundation.SkSp
import undefined.ModulatorBuffer

/**
 * C++ original:
 * ```cpp
 * class TextAnimator final : public SkNVRefCnt<TextAnimator> {
 * public:
 *     static sk_sp<TextAnimator> Make(const skjson::ObjectValue*,
 *                                     const AnimationBuilder*,
 *                                     AnimatablePropertyContainer* acontainer);
 *
 *     // Direct mapping of AE properties.
 *     struct AnimatedProps {
 *         VectorValue position,
 *                     scale          = { 100, 100, 100 };
 *         ColorValue  fill_color,
 *                     stroke_color;
 *         // unlike pos/scale which are animated vectors, rotation is separated in each dimension.
 *         SkV3        rotation       = { 0, 0, 0 };
 *         Vec2Value   blur           = { 0, 0 },
 *                 line_spacing       = { 0, 0 };
 *         ScalarValue opacity        = 100,
 *                     fill_opacity   = 100,
 *                     stroke_opacity = 100,
 *                     tracking       = 0,
 *                     stroke_width   = 0;
 *     };
 *
 *     struct ResolvedProps {
 *         SkV3      position = { 0, 0, 0 },
 *                      scale = { 1, 1, 1 },
 *                   rotation = { 0, 0, 0 };
 *         float      opacity = 1,
 *                   tracking = 0,
 *               stroke_width = 0;
 *         SkColor fill_color = SK_ColorTRANSPARENT,
 *               stroke_color = SK_ColorTRANSPARENT;
 *         SkV2          blur = { 0, 0 },
 *               line_spacing = { 0, 0 };
 *     };
 *
 *     struct AnimatedPropsModulator {
 *         ResolvedProps props;     // accumulates properties across *all* animators
 *         float         coverage;  // accumulates range selector coverage for a given animator
 *     };
 *     using ModulatorBuffer = std::vector<AnimatedPropsModulator>;
 *
 *     // Domain maps describe how a given index domain (words, lines, etc) relates
 *     // to the full fragment index range.
 *     //
 *     // Each domain[i] represents a [domain[i].fOffset.. domain[i].fOffset+domain[i].fCount-1]
 *     // fragment subset.
 *     struct DomainSpan {
 *         size_t fOffset,
 *                fCount;
 *         float  fAdvance, // cumulative advance for all fragments in span
 *                fAscent;  // max ascent for all fragments in span
 *     };
 *     using DomainMap = std::vector<DomainSpan>;
 *
 *     struct DomainMaps {
 *         DomainMap fNonWhitespaceMap,
 *                   fWordsMap,
 *                   fLinesMap;
 *     };
 *
 *     void modulateProps(const DomainMaps&, ModulatorBuffer&) const;
 *
 *     bool hasBlur() const { return fHasBlur; }
 *
 *     bool requiresAnchorPoint()     const { return fRequiresAnchorPoint;     }
 *     bool requiresLineAdjustments() const { return fRequiresLineAdjustments; }
 *
 * private:
 *     TextAnimator(std::vector<sk_sp<RangeSelector>>&&,
 *                  const skjson::ObjectValue&,
 *                  const AnimationBuilder*,
 *                  AnimatablePropertyContainer*);
 *
 *     ResolvedProps modulateProps(const ResolvedProps&, float amount) const;
 *
 *     const std::vector<sk_sp<RangeSelector>> fSelectors;
 *
 *     AnimatedProps fTextProps;
 *     bool          fHasFillColor            : 1,
 *                   fHasStrokeColor          : 1,
 *                   fHasFillOpacity          : 1,
 *                   fHasStrokeOpacity        : 1,
 *                   fHasOpacity              : 1,
 *                   fHasBlur                 : 1,
 *                   fRequiresAnchorPoint     : 1, // animator sensitive to transform origin?
 *                   fRequiresLineAdjustments : 1; // animator effects line-wide fragment adjustments
 * }
 * ```
 */
public class TextAnimator public constructor(
  selectors: List<SkSp<RangeSelector>>,
  jprops: ObjectValue,
  abuilder: AnimationBuilder?,
  acontainer: AnimatablePropertyContainer?,
) : SkNVRefCnt(),
    TextAnimator {
  /**
   * C++ original:
   * ```cpp
   * const std::vector<sk_sp<RangeSelector>> fSelectors
   * ```
   */
  private val fSelectors: Int = TODO("Initialize fSelectors")

  /**
   * C++ original:
   * ```cpp
   * AnimatedProps fTextProps
   * ```
   */
  private var fTextProps: AnimatedProps = TODO("Initialize fTextProps")

  /**
   * C++ original:
   * ```cpp
   * bool          fHasFillColor            : 1
   * ```
   */
  private var fHasFillColor: Boolean = TODO("Initialize fHasFillColor")

  /**
   * C++ original:
   * ```cpp
   * bool          fHasFillColor            : 1,
   *                   fHasStrokeColor          : 1
   * ```
   */
  private var fHasStrokeColor: Boolean = TODO("Initialize fHasStrokeColor")

  /**
   * C++ original:
   * ```cpp
   * bool          fHasFillColor            : 1,
   *                   fHasStrokeColor          : 1,
   *                   fHasFillOpacity          : 1
   * ```
   */
  private var fHasFillOpacity: Boolean = TODO("Initialize fHasFillOpacity")

  /**
   * C++ original:
   * ```cpp
   * bool          fHasFillColor            : 1,
   *                   fHasStrokeColor          : 1,
   *                   fHasFillOpacity          : 1,
   *                   fHasStrokeOpacity        : 1
   * ```
   */
  private var fHasStrokeOpacity: Boolean = TODO("Initialize fHasStrokeOpacity")

  /**
   * C++ original:
   * ```cpp
   * bool          fHasFillColor            : 1,
   *                   fHasStrokeColor          : 1,
   *                   fHasFillOpacity          : 1,
   *                   fHasStrokeOpacity        : 1,
   *                   fHasOpacity              : 1
   * ```
   */
  private var fHasOpacity: Boolean = TODO("Initialize fHasOpacity")

  /**
   * C++ original:
   * ```cpp
   * bool          fHasFillColor            : 1,
   *                   fHasStrokeColor          : 1,
   *                   fHasFillOpacity          : 1,
   *                   fHasStrokeOpacity        : 1,
   *                   fHasOpacity              : 1,
   *                   fHasBlur                 : 1
   * ```
   */
  private var fHasBlur: Boolean = TODO("Initialize fHasBlur")

  /**
   * C++ original:
   * ```cpp
   * bool          fHasFillColor            : 1,
   *                   fHasStrokeColor          : 1,
   *                   fHasFillOpacity          : 1,
   *                   fHasStrokeOpacity        : 1,
   *                   fHasOpacity              : 1,
   *                   fHasBlur                 : 1,
   *                   fRequiresAnchorPoint     : 1
   * ```
   */
  private var fRequiresAnchorPoint: Boolean = TODO("Initialize fRequiresAnchorPoint")

  /**
   * C++ original:
   * ```cpp
   * bool          fHasFillColor            : 1,
   *                   fHasStrokeColor          : 1,
   *                   fHasFillOpacity          : 1,
   *                   fHasStrokeOpacity        : 1,
   *                   fHasOpacity              : 1,
   *                   fHasBlur                 : 1,
   *                   fRequiresAnchorPoint     : 1, // animator sensitive to transform origin?
   *                   fRequiresLineAdjustments : 1
   * ```
   */
  private var fRequiresLineAdjustments: Boolean = TODO("Initialize fRequiresLineAdjustments")

  /**
   * C++ original:
   * ```cpp
   * void TextAnimator::modulateProps(const DomainMaps& maps, ModulatorBuffer& buf) const {
   *     // No selectors -> full coverage.
   *     const auto initial_coverage = fSelectors.empty() ? 1.f : 0.f;
   *
   *     // Coverage is scoped per animator.
   *     for (auto& mod : buf) {
   *         mod.coverage = initial_coverage;
   *     }
   *
   *     // Accumulate selector coverage.
   *     for (const auto& selector : fSelectors) {
   *         selector->modulateCoverage(maps, buf);
   *     }
   *
   *     // Modulate animated props.
   *     for (auto& mod : buf) {
   *         mod.props = this->modulateProps(mod.props, mod.coverage);
   *     }
   * }
   * ```
   */
  public override fun modulateProps(maps: DomainMaps, buf: ModulatorBuffer) {
    TODO("Implement modulateProps")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasBlur() const { return fHasBlur; }
   * ```
   */
  public override fun hasBlur(): Boolean {
    TODO("Implement hasBlur")
  }

  /**
   * C++ original:
   * ```cpp
   * bool requiresAnchorPoint()     const { return fRequiresAnchorPoint;     }
   * ```
   */
  public override fun requiresAnchorPoint(): Boolean {
    TODO("Implement requiresAnchorPoint")
  }

  /**
   * C++ original:
   * ```cpp
   * bool requiresLineAdjustments() const { return fRequiresLineAdjustments; }
   * ```
   */
  public override fun requiresLineAdjustments(): Boolean {
    TODO("Implement requiresLineAdjustments")
  }

  /**
   * C++ original:
   * ```cpp
   * ResolvedProps modulateProps(const ResolvedProps&, float amount) const
   * ```
   */
  public override fun modulateProps(param0: ResolvedProps, amount: Float): ResolvedProps {
    TODO("Implement modulateProps")
  }

  public data class AnimatedProps public constructor(
    public var position: Int,
    public var scale: Int,
    public var fillColor: Int,
    public var strokeColor: Int,
    public var rotation: Int,
    public var blur: Int,
    public var lineSpacing: Int,
    public var opacity: Int,
    public var fillOpacity: Int,
    public var strokeOpacity: Int,
    public var tracking: Int,
    public var strokeWidth: Int,
  )

  public data class ResolvedProps public constructor(
    public var position: Int,
    public var scale: Int,
    public var rotation: Int,
    public var opacity: Float,
    public var tracking: Float,
    public var strokeWidth: Float,
    public var fillColor: Int,
    public var strokeColor: Int,
    public var blur: Int,
    public var lineSpacing: Int,
  )

  public data class AnimatedPropsModulator public constructor(
    public var props: undefined.ResolvedProps,
    public var coverage: Float,
  )

  public data class DomainSpan public constructor(
    public var fOffset: Int,
    public var fCount: Int,
    public var fAdvance: Float,
    public var fAscent: Float,
  )

  public data class DomainMaps public constructor(
    public var fNonWhitespaceMap: undefined.DomainMaps,
    public var fWordsMap: undefined.DomainMaps,
    public var fLinesMap: undefined.DomainMaps,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<TextAnimator> TextAnimator::Make(const skjson::ObjectValue* janimator,
     *                                        const AnimationBuilder* abuilder,
     *                                        AnimatablePropertyContainer* acontainer) {
     *     if (!janimator) {
     *         return nullptr;
     *     }
     *
     *     const skjson::ObjectValue* jprops = (*janimator)["a"];
     *     if (!jprops) {
     *         return nullptr;
     *     }
     *
     *     std::vector<sk_sp<RangeSelector>> selectors;
     *
     *     // Depending on compat mode and whether more than one selector is present,
     *     // BM exports either an array or a single object.
     *     if (const skjson::ArrayValue* jselectors = (*janimator)["s"]) {
     *         selectors.reserve(jselectors->size());
     *         for (const skjson::ObjectValue* jselector : *jselectors) {
     *             if (!jselector) {
     *                 continue;
     *             }
     *             if (auto sel = RangeSelector::Make(*jselector, abuilder, acontainer)) {
     *                 selectors.push_back(std::move(sel));
     *             }
     *         }
     *     } else {
     *         if (auto sel = RangeSelector::Make((*janimator)["s"], abuilder, acontainer)) {
     *             selectors.reserve(1);
     *             selectors.push_back(std::move(sel));
     *         }
     *     }
     *
     *     return sk_sp<TextAnimator>(
     *                 new TextAnimator(std::move(selectors), *jprops, abuilder, acontainer));
     * }
     * ```
     */
    public override fun make(
      janimator: ObjectValue?,
      abuilder: AnimationBuilder?,
      acontainer: AnimatablePropertyContainer?,
    ): Int {
      TODO("Implement make")
    }
  }
}
