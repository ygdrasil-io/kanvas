package org.skia.modules

import kotlin.Float
import kotlin.ULong
import kotlin.collections.List
import org.skia.core.SkCanvas
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkV2
import undefined.RenderContext

/**
 * C++ original:
 * ```cpp
 * class RepeaterRenderNode final : public sksg::CustomRenderNode {
 * public:
 *     enum class CompositeMode { kBelow, kAbove };
 *
 *     RepeaterRenderNode(std::vector<sk_sp<RenderNode>>&& children, CompositeMode mode)
 *         : INHERITED(std::move(children))
 *         , fMode(mode) {}
 *
 *     SG_ATTRIBUTE(Count       , size_t, fCount       )
 *     SG_ATTRIBUTE(Offset      , float , fOffset      )
 *     SG_ATTRIBUTE(AnchorPoint , SkV2  , fAnchorPoint )
 *     SG_ATTRIBUTE(Position    , SkV2  , fPosition    )
 *     SG_ATTRIBUTE(Scale       , SkV2  , fScale       )
 *     SG_ATTRIBUTE(Rotation    , float , fRotation    )
 *     SG_ATTRIBUTE(StartOpacity, float , fStartOpacity)
 *     SG_ATTRIBUTE(EndOpacity  , float , fEndOpacity  )
 *
 * private:
 *     const RenderNode* onNodeAt(const SkPoint&) const override { return nullptr; } // no hit-testing
 *
 *     SkMatrix instanceTransform(size_t i) const {
 *         const auto t = fOffset + i;
 *
 *         // Position, scale & rotation are "scaled" by index/offset.
 *         return SkMatrix::Translate(t * fPosition.x + fAnchorPoint.x,
 *                                    t * fPosition.y + fAnchorPoint.y)
 *              * SkMatrix::RotateDeg(t * fRotation)
 *              * SkMatrix::Scale(std::pow(fScale.x, t),
 *                                std::pow(fScale.y, t))
 *              * SkMatrix::Translate(-fAnchorPoint.x,
 *                                    -fAnchorPoint.y);
 *     }
 *
 *     SkRect onRevalidate(sksg::InvalidationController* ic, const SkMatrix& ctm) override {
 *         fChildrenBounds = SkRect::MakeEmpty();
 *         for (const auto& child : this->children()) {
 *             fChildrenBounds.join(child->revalidate(ic, ctm));
 *         }
 *
 *         auto bounds = SkRect::MakeEmpty();
 *         for (size_t i = 0; i < fCount; ++i) {
 *             bounds.join(this->instanceTransform(i).mapRect(fChildrenBounds));
 *         }
 *
 *         return bounds;
 *     }
 *
 *     void onRender(SkCanvas* canvas, const RenderContext* ctx) const override {
 *         // To cover the full opacity range, the denominator below should be (fCount - 1).
 *         // Interstingly, that's not what AE does.  Off-by-one bug?
 *         const auto dOpacity = fCount > 1 ? (fEndOpacity - fStartOpacity) / fCount : 0.0f;
 *
 *         for (size_t i = 0; i < fCount; ++i) {
 *             const auto render_index = fMode == CompositeMode::kAbove ? i : fCount - i - 1;
 *             const auto opacity      = fStartOpacity + dOpacity * render_index;
 *
 *             if (opacity <= 0) {
 *                 continue;
 *             }
 *
 *             SkAutoCanvasRestore acr(canvas, true);
 *             canvas->concat(this->instanceTransform(render_index));
 *
 *             const auto& children = this->children();
 *             const auto local_ctx = ScopedRenderContext(canvas, ctx)
 *                                         .modulateOpacity(opacity)
 *                                         .setIsolation(fChildrenBounds,
 *                                                       canvas->getTotalMatrix(),
 *                                                       children.size() > 1);
 *             for (const auto& child : children) {
 *                 child->render(canvas, local_ctx);
 *             }
 *         }
 *     }
 *
 *     const CompositeMode           fMode;
 *
 *     SkRect fChildrenBounds = SkRect::MakeEmpty(); // cached
 *
 *     size_t fCount          = 0;
 *     float  fOffset         = 0,
 *            fRotation       = 0,
 *            fStartOpacity   = 1,
 *            fEndOpacity     = 1;
 *     SkV2   fAnchorPoint    = {0,0},
 *            fPosition       = {0,0},
 *            fScale          = {1,1};
 *
 *     using INHERITED = sksg::CustomRenderNode;
 * }
 * ```
 */
public class RepeaterRenderNode public constructor(
  children: List<SkSp<RenderNode>>,
  mode: CompositeMode,
) : CustomRenderNode(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * const CompositeMode           fMode
   * ```
   */
  private val fMode: CompositeMode = TODO("Initialize fMode")

  /**
   * C++ original:
   * ```cpp
   * SkRect fChildrenBounds = SkRect::MakeEmpty()
   * ```
   */
  private var fChildrenBounds: SkRect = TODO("Initialize fChildrenBounds")

  /**
   * C++ original:
   * ```cpp
   * size_t fCount          = 0
   * ```
   */
  private var fCount: ULong = TODO("Initialize fCount")

  /**
   * C++ original:
   * ```cpp
   * float  fOffset         = 0
   * ```
   */
  private var fOffset: Float = TODO("Initialize fOffset")

  /**
   * C++ original:
   * ```cpp
   * float  fOffset         = 0,
   *            fRotation       = 0
   * ```
   */
  private var fRotation: Float = TODO("Initialize fRotation")

  /**
   * C++ original:
   * ```cpp
   * float  fOffset         = 0,
   *            fRotation       = 0,
   *            fStartOpacity   = 1
   * ```
   */
  private var fStartOpacity: Float = TODO("Initialize fStartOpacity")

  /**
   * C++ original:
   * ```cpp
   * float  fOffset         = 0,
   *            fRotation       = 0,
   *            fStartOpacity   = 1,
   *            fEndOpacity     = 1
   * ```
   */
  private var fEndOpacity: Float = TODO("Initialize fEndOpacity")

  /**
   * C++ original:
   * ```cpp
   * SkV2   fAnchorPoint    = {0,0}
   * ```
   */
  private var fAnchorPoint: SkV2 = TODO("Initialize fAnchorPoint")

  /**
   * C++ original:
   * ```cpp
   * SkV2   fAnchorPoint    = {0,0},
   *            fPosition       = {0,0}
   * ```
   */
  private var fPosition: SkV2 = TODO("Initialize fPosition")

  /**
   * C++ original:
   * ```cpp
   * SkV2   fAnchorPoint    = {0,0},
   *            fPosition       = {0,0},
   *            fScale          = {1,1}
   * ```
   */
  private var fScale: SkV2 = TODO("Initialize fScale")

  /**
   * C++ original:
   * ```cpp
   * const RenderNode* onNodeAt(const SkPoint&) const override { return nullptr; }
   * ```
   */
  public override fun onNodeAt(param0: SkPoint): RenderNode {
    TODO("Implement onNodeAt")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix instanceTransform(size_t i) const {
   *         const auto t = fOffset + i;
   *
   *         // Position, scale & rotation are "scaled" by index/offset.
   *         return SkMatrix::Translate(t * fPosition.x + fAnchorPoint.x,
   *                                    t * fPosition.y + fAnchorPoint.y)
   *              * SkMatrix::RotateDeg(t * fRotation)
   *              * SkMatrix::Scale(std::pow(fScale.x, t),
   *                                std::pow(fScale.y, t))
   *              * SkMatrix::Translate(-fAnchorPoint.x,
   *                                    -fAnchorPoint.y);
   *     }
   * ```
   */
  private fun instanceTransform(i: ULong): SkMatrix {
    TODO("Implement instanceTransform")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect onRevalidate(sksg::InvalidationController* ic, const SkMatrix& ctm) override {
   *         fChildrenBounds = SkRect::MakeEmpty();
   *         for (const auto& child : this->children()) {
   *             fChildrenBounds.join(child->revalidate(ic, ctm));
   *         }
   *
   *         auto bounds = SkRect::MakeEmpty();
   *         for (size_t i = 0; i < fCount; ++i) {
   *             bounds.join(this->instanceTransform(i).mapRect(fChildrenBounds));
   *         }
   *
   *         return bounds;
   *     }
   * ```
   */
  public override fun onRevalidate(ic: InvalidationController?, ctm: SkMatrix): SkRect {
    TODO("Implement onRevalidate")
  }

  /**
   * C++ original:
   * ```cpp
   * void onRender(SkCanvas* canvas, const RenderContext* ctx) const override {
   *         // To cover the full opacity range, the denominator below should be (fCount - 1).
   *         // Interstingly, that's not what AE does.  Off-by-one bug?
   *         const auto dOpacity = fCount > 1 ? (fEndOpacity - fStartOpacity) / fCount : 0.0f;
   *
   *         for (size_t i = 0; i < fCount; ++i) {
   *             const auto render_index = fMode == CompositeMode::kAbove ? i : fCount - i - 1;
   *             const auto opacity      = fStartOpacity + dOpacity * render_index;
   *
   *             if (opacity <= 0) {
   *                 continue;
   *             }
   *
   *             SkAutoCanvasRestore acr(canvas, true);
   *             canvas->concat(this->instanceTransform(render_index));
   *
   *             const auto& children = this->children();
   *             const auto local_ctx = ScopedRenderContext(canvas, ctx)
   *                                         .modulateOpacity(opacity)
   *                                         .setIsolation(fChildrenBounds,
   *                                                       canvas->getTotalMatrix(),
   *                                                       children.size() > 1);
   *             for (const auto& child : children) {
   *                 child->render(canvas, local_ctx);
   *             }
   *         }
   *     }
   * ```
   */
  public override fun onRender(canvas: SkCanvas?, ctx: RenderContext?) {
    TODO("Implement onRender")
  }

  public enum class CompositeMode {
    kBelow,
    kAbove,
  }
}
