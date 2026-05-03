package org.skia.modules

import kotlin.Int
import kotlin.collections.List
import org.skia.foundation.SkNoncopyable
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class ShapeBuilder final : SkNoncopyable {
 * public:
 *     static sk_sp<sksg::Merge> MergeGeometry(std::vector<sk_sp<sksg::GeometryNode>>&&,
 *                                             sksg::Merge::Mode);
 *
 *     static sk_sp<sksg::GeometryNode> AttachPathGeometry(const skjson::ObjectValue&,
 *                                                         const AnimationBuilder*);
 *     static sk_sp<sksg::GeometryNode> AttachRRectGeometry(const skjson::ObjectValue&,
 *                                                          const AnimationBuilder*);
 *     static sk_sp<sksg::GeometryNode> AttachEllipseGeometry(const skjson::ObjectValue&,
 *                                                            const AnimationBuilder*);
 *     static sk_sp<sksg::GeometryNode> AttachPolystarGeometry(const skjson::ObjectValue&,
 *                                                             const AnimationBuilder*);
 *
 *     static sk_sp<sksg::PaintNode> AttachColorFill(const skjson::ObjectValue&,
 *                                                   const AnimationBuilder*);
 *     static sk_sp<sksg::PaintNode> AttachColorStroke(const skjson::ObjectValue&,
 *                                                     const AnimationBuilder*);
 *     static sk_sp<sksg::PaintNode> AttachGradientFill(const skjson::ObjectValue&,
 *                                                      const AnimationBuilder*);
 *     static sk_sp<sksg::PaintNode> AttachGradientStroke(const skjson::ObjectValue&,
 *                                                        const AnimationBuilder*);
 *
 *     static std::vector<sk_sp<sksg::GeometryNode>> AttachMergeGeometryEffect(
 *             const skjson::ObjectValue&, const AnimationBuilder*,
 *             std::vector<sk_sp<sksg::GeometryNode>>&&);
 *     static std::vector<sk_sp<sksg::GeometryNode>> AttachTrimGeometryEffect(
 *             const skjson::ObjectValue&,
 *             const AnimationBuilder*,
 *             std::vector<sk_sp<sksg::GeometryNode>>&&);
 *     static std::vector<sk_sp<sksg::GeometryNode>> AttachRoundGeometryEffect(
 *             const skjson::ObjectValue&, const AnimationBuilder*,
 *             std::vector<sk_sp<sksg::GeometryNode>>&&);
 *     static std::vector<sk_sp<sksg::GeometryNode>> AttachOffsetGeometryEffect(
 *             const skjson::ObjectValue&, const AnimationBuilder*,
 *             std::vector<sk_sp<sksg::GeometryNode>>&&);
 *     static std::vector<sk_sp<sksg::GeometryNode>> AttachPuckerBloatGeometryEffect(
 *             const skjson::ObjectValue&, const AnimationBuilder*,
 *             std::vector<sk_sp<sksg::GeometryNode>>&&);
 *     static std::vector<sk_sp<sksg::GeometryNode>> AdjustStrokeGeometry(
 *             const skjson::ObjectValue&, const AnimationBuilder*,
 *             std::vector<sk_sp<sksg::GeometryNode>>&&);
 *
 *     static std::vector<sk_sp<sksg::RenderNode>> AttachRepeaterDrawEffect(
 *             const skjson::ObjectValue&,
 *             const AnimationBuilder*,
 *             std::vector<sk_sp<sksg::RenderNode>>&&);
 *
 * private:
 *     static sk_sp<sksg::PaintNode> AttachFill(const skjson::ObjectValue&,
 *                                              const AnimationBuilder*,
 *                                              sk_sp<sksg::PaintNode>,
 *                                              sk_sp<AnimatablePropertyContainer> = nullptr);
 *     static sk_sp<sksg::PaintNode> AttachStroke(const skjson::ObjectValue&,
 *                                                const AnimationBuilder*,
 *                                                sk_sp<sksg::PaintNode>,
 *                                                sk_sp<AnimatablePropertyContainer> = nullptr);
 * }
 * ```
 */
public class ShapeBuilder : SkNoncopyable() {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<sksg::Merge> MergeGeometry(std::vector<sk_sp<sksg::GeometryNode>>&&,
     *                                             sksg::Merge::Mode)
     * ```
     */
    public fun mergeGeometry(param0: List<SkSp<GeometryNode>>, param1: Merge.Mode): Int {
      TODO("Implement mergeGeometry")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<sksg::GeometryNode> ShapeBuilder::AttachPathGeometry(const skjson::ObjectValue& jpath,
     *                                                            const AnimationBuilder* abuilder) {
     *     return abuilder->attachPath(jpath["ks"]);
     * }
     * ```
     */
    public fun attachPathGeometry(jpath: ObjectValue, abuilder: AnimationBuilder?): Int {
      TODO("Implement attachPathGeometry")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<sksg::GeometryNode> ShapeBuilder::AttachRRectGeometry(const skjson::ObjectValue& jrect,
     *                                                             const AnimationBuilder* abuilder) {
     *     return abuilder->attachDiscardableAdapter<RectangleGeometryAdapter>(jrect, abuilder);
     * }
     * ```
     */
    public fun attachRRectGeometry(jrect: ObjectValue, abuilder: AnimationBuilder?): Int {
      TODO("Implement attachRRectGeometry")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<sksg::GeometryNode> ShapeBuilder::AttachEllipseGeometry(const skjson::ObjectValue& jellipse,
     *                                                               const AnimationBuilder* abuilder) {
     *     return abuilder->attachDiscardableAdapter<EllipseGeometryAdapter>(jellipse, abuilder);
     * }
     * ```
     */
    public fun attachEllipseGeometry(jellipse: ObjectValue, abuilder: AnimationBuilder?): Int {
      TODO("Implement attachEllipseGeometry")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<sksg::GeometryNode> ShapeBuilder::AttachPolystarGeometry(const skjson::ObjectValue& jstar,
     *                                                                const AnimationBuilder* abuilder) {
     *     static constexpr PolystarGeometryAdapter::Type gTypes[] = {
     *         PolystarGeometryAdapter::Type::kStar, // "sy": 1
     *         PolystarGeometryAdapter::Type::kPoly, // "sy": 2
     *     };
     *
     *     const auto type = ParseDefault<size_t>(jstar["sy"], 0) - 1;
     *     if (type >= std::size(gTypes)) {
     *         abuilder->log(Logger::Level::kError, &jstar, "Unknown polystar type.");
     *         return nullptr;
     *     }
     *
     *     return abuilder->attachDiscardableAdapter<PolystarGeometryAdapter>
     *                 (jstar, abuilder, gTypes[type]);
     * }
     * ```
     */
    public fun attachPolystarGeometry(jstar: ObjectValue, abuilder: AnimationBuilder?): Int {
      TODO("Implement attachPolystarGeometry")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<sksg::PaintNode> ShapeBuilder::AttachColorFill(const skjson::ObjectValue& jpaint,
     *                                                      const AnimationBuilder* abuilder) {
     *     auto color_node  = sksg::Color::Make(SK_ColorBLACK);
     *     auto color_paint = AttachFill(jpaint, abuilder, color_node);
     *     abuilder->dispatchColorProperty(color_node);
     *     return color_paint;
     * }
     * ```
     */
    public fun attachColorFill(jpaint: ObjectValue, abuilder: AnimationBuilder?): Int {
      TODO("Implement attachColorFill")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<sksg::PaintNode> ShapeBuilder::AttachColorStroke(const skjson::ObjectValue& jpaint,
     *                                                        const AnimationBuilder* abuilder) {
     *     auto color_node  = sksg::Color::Make(SK_ColorBLACK);
     *     auto color_paint = AttachStroke(jpaint, abuilder, color_node);
     *     abuilder->dispatchColorProperty(color_node);
     *     return color_paint;
     * }
     * ```
     */
    public fun attachColorStroke(jpaint: ObjectValue, abuilder: AnimationBuilder?): Int {
      TODO("Implement attachColorStroke")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<sksg::PaintNode> ShapeBuilder::AttachGradientFill(const skjson::ObjectValue& jgrad,
     *                                                         const AnimationBuilder* abuilder) {
     *     auto adapter = GradientAdapter::Make(jgrad, *abuilder);
     *
     *     return adapter
     *             ? AttachFill(jgrad, abuilder, sksg::ShaderPaint::Make(adapter->node()), adapter)
     *             : nullptr;
     * }
     * ```
     */
    public fun attachGradientFill(jgrad: ObjectValue, abuilder: AnimationBuilder?): Int {
      TODO("Implement attachGradientFill")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<sksg::PaintNode> ShapeBuilder::AttachGradientStroke(const skjson::ObjectValue& jgrad,
     *                                                           const AnimationBuilder* abuilder) {
     *     auto adapter = GradientAdapter::Make(jgrad, *abuilder);
     *
     *     return adapter
     *             ? AttachStroke(jgrad, abuilder, sksg::ShaderPaint::Make(adapter->node()), adapter)
     *             : nullptr;
     * }
     * ```
     */
    public fun attachGradientStroke(jgrad: ObjectValue, abuilder: AnimationBuilder?): Int {
      TODO("Implement attachGradientStroke")
    }

    /**
     * C++ original:
     * ```cpp
     * std::vector<sk_sp<sksg::GeometryNode>> ShapeBuilder::AttachMergeGeometryEffect(
     *         const skjson::ObjectValue& jmerge, const AnimationBuilder*,
     *         std::vector<sk_sp<sksg::GeometryNode>>&& geos) {
     *     static constexpr sksg::Merge::Mode gModes[] = {
     *         sksg::Merge::Mode::kMerge,      // "mm": 1
     *         sksg::Merge::Mode::kUnion,      // "mm": 2
     *         sksg::Merge::Mode::kDifference, // "mm": 3
     *         sksg::Merge::Mode::kIntersect,  // "mm": 4
     *         sksg::Merge::Mode::kXOR      ,  // "mm": 5
     *     };
     *
     *     const auto mode = gModes[std::min<size_t>(ParseDefault<size_t>(jmerge["mm"], 1) - 1,
     *                                             std::size(gModes) - 1)];
     *
     *     std::vector<sk_sp<sksg::GeometryNode>> merged;
     *     merged.push_back(ShapeBuilder::MergeGeometry(std::move(geos), mode));
     *
     *     return merged;
     * }
     * ```
     */
    public fun attachMergeGeometryEffect(
      jmerge: ObjectValue,
      param1: AnimationBuilder?,
      geos: List<SkSp<GeometryNode>>,
    ): Int {
      TODO("Implement attachMergeGeometryEffect")
    }

    /**
     * C++ original:
     * ```cpp
     * std::vector<sk_sp<sksg::GeometryNode>> ShapeBuilder::AttachTrimGeometryEffect(
     *         const skjson::ObjectValue& jtrim,
     *         const AnimationBuilder* abuilder,
     *         std::vector<sk_sp<sksg::GeometryNode>>&& geos) {
     *
     *     enum class Mode {
     *         kParallel, // "m": 1 (Trim Multiple Shapes: Simultaneously)
     *         kSerial,   // "m": 2 (Trim Multiple Shapes: Individually)
     *     } gModes[] = { Mode::kParallel, Mode::kSerial};
     *
     *     const auto mode = gModes[std::min<size_t>(ParseDefault<size_t>(jtrim["m"], 1) - 1,
     *                                             std::size(gModes) - 1)];
     *
     *     std::vector<sk_sp<sksg::GeometryNode>> inputs;
     *     if (mode == Mode::kSerial) {
     *         inputs.push_back(ShapeBuilder::MergeGeometry(std::move(geos), sksg::Merge::Mode::kMerge));
     *     } else {
     *         inputs = std::move(geos);
     *     }
     *
     *     std::vector<sk_sp<sksg::GeometryNode>> trimmed;
     *     trimmed.reserve(inputs.size());
     *
     *     for (const auto& i : inputs) {
     *         trimmed.push_back(
     *             abuilder->attachDiscardableAdapter<TrimEffectAdapter>(jtrim, *abuilder, i));
     *     }
     *
     *     return trimmed;
     * }
     * ```
     */
    public fun attachTrimGeometryEffect(
      jtrim: ObjectValue,
      abuilder: AnimationBuilder?,
      geos: List<SkSp<GeometryNode>>,
    ): Int {
      TODO("Implement attachTrimGeometryEffect")
    }

    /**
     * C++ original:
     * ```cpp
     * std::vector<sk_sp<sksg::GeometryNode>> ShapeBuilder::AttachRoundGeometryEffect(
     *         const skjson::ObjectValue& jround, const AnimationBuilder* abuilder,
     *         std::vector<sk_sp<sksg::GeometryNode>>&& geos) {
     *     std::vector<sk_sp<sksg::GeometryNode>> rounded;
     *     rounded.reserve(geos.size());
     *
     *     for (auto& g : geos) {
     *         rounded.push_back(
     *             abuilder->attachDiscardableAdapter<RoundCornersAdapter>
     *                         (jround, *abuilder, std::move(g)));
     *     }
     *
     *     return rounded;
     * }
     * ```
     */
    public fun attachRoundGeometryEffect(
      jround: ObjectValue,
      abuilder: AnimationBuilder?,
      geos: List<SkSp<GeometryNode>>,
    ): Int {
      TODO("Implement attachRoundGeometryEffect")
    }

    /**
     * C++ original:
     * ```cpp
     * std::vector<sk_sp<sksg::GeometryNode>> ShapeBuilder::AttachOffsetGeometryEffect(
     *         const skjson::ObjectValue& jround, const AnimationBuilder* abuilder,
     *         std::vector<sk_sp<sksg::GeometryNode>>&& geos) {
     *     std::vector<sk_sp<sksg::GeometryNode>> offsetted;
     *     offsetted.reserve(geos.size());
     *
     *     for (auto& g : geos) {
     *         offsetted.push_back(abuilder->attachDiscardableAdapter<OffsetPathsAdapter>
     *                                         (jround, *abuilder, std::move(g)));
     *     }
     *
     *     return offsetted;
     * }
     * ```
     */
    public fun attachOffsetGeometryEffect(
      jround: ObjectValue,
      abuilder: AnimationBuilder?,
      geos: List<SkSp<GeometryNode>>,
    ): Int {
      TODO("Implement attachOffsetGeometryEffect")
    }

    /**
     * C++ original:
     * ```cpp
     * std::vector<sk_sp<sksg::GeometryNode>> ShapeBuilder::AttachPuckerBloatGeometryEffect(
     *         const skjson::ObjectValue& jround, const AnimationBuilder* abuilder,
     *         std::vector<sk_sp<sksg::GeometryNode>>&& geos) {
     *     std::vector<sk_sp<sksg::GeometryNode>> bloated;
     *     bloated.reserve(geos.size());
     *
     *     for (auto& g : geos) {
     *         bloated.push_back(abuilder->attachDiscardableAdapter<PuckerBloatAdapter>
     *                                         (jround, *abuilder, std::move(g)));
     *     }
     *
     *     return bloated;
     * }
     * ```
     */
    public fun attachPuckerBloatGeometryEffect(
      jround: ObjectValue,
      abuilder: AnimationBuilder?,
      geos: List<SkSp<GeometryNode>>,
    ): Int {
      TODO("Implement attachPuckerBloatGeometryEffect")
    }

    /**
     * C++ original:
     * ```cpp
     * std::vector<sk_sp<sksg::GeometryNode>> ShapeBuilder::AdjustStrokeGeometry(
     *         const skjson::ObjectValue& jstroke,
     *         const AnimationBuilder* abuilder,
     *         std::vector<sk_sp<sksg::GeometryNode>>&& geos) {
     *
     *     const skjson::ArrayValue* jdash = jstroke["d"];
     *     if (jdash && jdash->size() > 1) {
     *         for (size_t i = 0; i < geos.size(); ++i) {
     *             geos[i] = abuilder->attachDiscardableAdapter<DashAdapter>(
     *                           *jdash, *abuilder, std::move(geos[i]));
     *         }
     *     }
     *
     *     return std::move(geos);
     * }
     * ```
     */
    public fun adjustStrokeGeometry(
      jstroke: ObjectValue,
      abuilder: AnimationBuilder?,
      geos: List<SkSp<GeometryNode>>,
    ): Int {
      TODO("Implement adjustStrokeGeometry")
    }

    /**
     * C++ original:
     * ```cpp
     * std::vector<sk_sp<sksg::RenderNode>> ShapeBuilder::AttachRepeaterDrawEffect(
     *         const skjson::ObjectValue& jrepeater,
     *         const AnimationBuilder* abuilder,
     *         std::vector<sk_sp<sksg::RenderNode>>&& draws) {
     *     std::vector<sk_sp<sksg::RenderNode>> repeater_draws;
     *
     *     if (const skjson::ObjectValue* jtransform = jrepeater["tr"]) {
     *         // input draws are in top->bottom order - reverse for paint order
     *         std::reverse(draws.begin(), draws.end());
     *
     *         repeater_draws.reserve(1);
     *         repeater_draws.push_back(
     *                     abuilder->attachDiscardableAdapter<RepeaterAdapter>(jrepeater,
     *                                                                         *jtransform,
     *                                                                         *abuilder,
     *                                                                         std::move(draws)));
     *     } else {
     *         repeater_draws = std::move(draws);
     *     }
     *
     *     return repeater_draws;
     * }
     * ```
     */
    public fun attachRepeaterDrawEffect(
      jrepeater: ObjectValue,
      abuilder: AnimationBuilder?,
      draws: List<SkSp<RenderNode>>,
    ): Int {
      TODO("Implement attachRepeaterDrawEffect")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<sksg::PaintNode> ShapeBuilder::AttachFill(const skjson::ObjectValue& jpaint,
     *                                                 const AnimationBuilder* abuilder,
     *                                                 sk_sp<sksg::PaintNode> paint_node,
     *                                                 sk_sp<AnimatablePropertyContainer> gradient) {
     *     return abuilder->attachDiscardableAdapter<FillStrokeAdapter>
     *             (jpaint,
     *              *abuilder,
     *              std::move(paint_node),
     *              std::move(gradient),
     *              FillStrokeAdapter::Type::kFill);
     * }
     * ```
     */
    private fun attachFill(
      jpaint: ObjectValue,
      abuilder: AnimationBuilder?,
      paintNode: SkSp<PaintNode>,
      gradient: SkSp<AnimatablePropertyContainer> = TODO(),
    ): Int {
      TODO("Implement attachFill")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<sksg::PaintNode> ShapeBuilder::AttachStroke(const skjson::ObjectValue& jpaint,
     *                                                   const AnimationBuilder* abuilder,
     *                                                   sk_sp<sksg::PaintNode> paint_node,
     *                                                   sk_sp<AnimatablePropertyContainer> gradient) {
     *     return abuilder->attachDiscardableAdapter<FillStrokeAdapter>
     *             (jpaint,
     *              *abuilder,
     *              std::move(paint_node),
     *              std::move(gradient),
     *              FillStrokeAdapter::Type::kStroke);
     * }
     * ```
     */
    private fun attachStroke(
      jpaint: ObjectValue,
      abuilder: AnimationBuilder?,
      paintNode: SkSp<PaintNode>,
      gradient: SkSp<AnimatablePropertyContainer> = TODO(),
    ): Int {
      TODO("Implement attachStroke")
    }
  }
}
