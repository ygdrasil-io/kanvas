package org.skia.modules

import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit
import org.skia.core.SkCanvas
import org.skia.foundation.SkBlender
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkPaint
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class RenderNode : public Node {
 * protected:
 *     struct RenderContext;
 *
 * public:
 *     // Render the node and its descendants to the canvas.
 *     void render(SkCanvas*, const RenderContext* = nullptr) const;
 *
 *     // Perform a front-to-back hit-test, and return the RenderNode located at |point|.
 *     // Normally, hit-testing stops at leaf Draw nodes.
 *     const RenderNode* nodeAt(const SkPoint& point) const;
 *
 *     // Controls the visibility of the render node.  Invisible nodes are not rendered,
 *     // but they still participate in revalidation.
 *     bool isVisible() const;
 *     void setVisible(bool);
 *
 * protected:
 *     explicit RenderNode(uint32_t inval_traits = 0);
 *
 *     virtual void onRender(SkCanvas*, const RenderContext*) const = 0;
 *     virtual const RenderNode* onNodeAt(const SkPoint& p)   const = 0;
 *
 *     // Paint property overrides.
 *     // These are deferred until we can determine whether they can be applied to the individual
 *     // draw paints, or whether they require content isolation (applied to a layer).
 *     struct RenderContext {
 *         sk_sp<SkColorFilter> fColorFilter;
 *         sk_sp<SkShader>      fShader;
 *         sk_sp<SkShader>      fMaskShader;
 *         sk_sp<SkBlender>     fBlender;
 *         SkMatrix             fShaderCTM = SkMatrix::I(),
 *                              fMaskCTM   = SkMatrix::I();
 *         float                fOpacity   = 1;
 *
 *         // Returns true if the paint overrides require a layer when applied to non-atomic draws.
 *         bool requiresIsolation() const;
 *
 *         void modulatePaint(const SkMatrix& ctm, SkPaint*, bool is_layer_paint = false) const;
 *     };
 *
 *     class ScopedRenderContext final {
 *     public:
 *         ScopedRenderContext(SkCanvas*, const RenderContext*);
 *         ~ScopedRenderContext();
 *
 *         ScopedRenderContext(ScopedRenderContext&& that) { *this = std::move(that); }
 *
 *         ScopedRenderContext& operator=(ScopedRenderContext&& that) {
 *             fCanvas       = that.fCanvas;
 *             fCtx          = std::move(that.fCtx);
 *             fMaskShader   = std::move(that.fMaskShader);
 *             fRestoreCount = that.fRestoreCount;
 *
 *             // scope ownership is being transferred
 *             that.fRestoreCount = -1;
 *
 *             return *this;
 *         }
 *
 *         operator const RenderContext*  () const { return &fCtx; }
 *         const RenderContext* operator->() const { return &fCtx; }
 *
 *         // Add (cumulative) paint overrides to a render node sub-DAG.
 *         ScopedRenderContext&& modulateOpacity(float opacity);
 *         ScopedRenderContext&& modulateColorFilter(sk_sp<SkColorFilter>);
 *         ScopedRenderContext&& modulateShader(sk_sp<SkShader>, const SkMatrix& shader_ctm);
 *         ScopedRenderContext&& modulateMaskShader(sk_sp<SkShader>, const SkMatrix& ms_ctm);
 *         ScopedRenderContext&& modulateBlender(sk_sp<SkBlender>);
 *
 *         // Force content isolation for a node sub-DAG by applying the RenderContext
 *         // overrides via a layer.
 *         ScopedRenderContext&& setIsolation(const SkRect& bounds, const SkMatrix& ctm,
 *                                            bool do_isolate);
 *
 *         // Similarly, force content isolation by applying the RenderContext overrides and
 *         // an image filter via a single layer.
 *         ScopedRenderContext&& setFilterIsolation(const SkRect& bounds, const SkMatrix& ctm,
 *                                                  sk_sp<SkImageFilter>);
 *
 *     private:
 *         // stack-only
 *         void* operator new(size_t)        = delete;
 *         void* operator new(size_t, void*) = delete;
 *
 *         // Scopes cannot be copied.
 *         ScopedRenderContext(const ScopedRenderContext&)            = delete;
 *         ScopedRenderContext& operator=(const ScopedRenderContext&) = delete;
 *
 *         SkCanvas*       fCanvas;
 *         RenderContext   fCtx;
 *         sk_sp<SkShader> fMaskShader; // to be applied at isolation layer restore time
 *         int             fRestoreCount;
 *     };
 *
 * private:
 *     friend class ImageFilterEffect;
 *
 *     using INHERITED = Node;
 * }
 * ```
 */
public abstract class RenderNode public constructor(
  invalTraits: UInt = TODO(),
) : Node(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * void RenderNode::render(SkCanvas* canvas, const RenderContext* ctx) const {
   *     SkASSERT(!this->hasInval());
   *     if (this->isVisible() && !this->bounds().isEmpty()) {
   *         this->onRender(canvas, ctx);
   *     }
   *     SkASSERT(!this->hasInval());
   * }
   * ```
   */
  public fun render(canvas: SkCanvas?, ctx: RenderContext? = TODO()) {
    TODO("Implement render")
  }

  /**
   * C++ original:
   * ```cpp
   * const RenderNode* RenderNode::nodeAt(const SkPoint& p) const {
   *     return this->bounds().contains(p.x(), p.y()) ? this->onNodeAt(p) : nullptr;
   * }
   * ```
   */
  public fun nodeAt(point: SkPoint): RenderNode {
    TODO("Implement nodeAt")
  }

  /**
   * C++ original:
   * ```cpp
   * bool RenderNode::isVisible() const {
   *     return !(fNodeFlags & kInvisible_Flag);
   * }
   * ```
   */
  public fun isVisible(): Boolean {
    TODO("Implement isVisible")
  }

  /**
   * C++ original:
   * ```cpp
   * void RenderNode::setVisible(bool v) {
   *     if (v == this->isVisible()) {
   *         return;
   *     }
   *
   *     this->invalidate();
   *     fNodeFlags = v ? (fNodeFlags & ~kInvisible_Flag)
   *                    : (fNodeFlags | kInvisible_Flag);
   * }
   * ```
   */
  public fun setVisible(v: Boolean) {
    TODO("Implement setVisible")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void onRender(SkCanvas*, const RenderContext*) const = 0
   * ```
   */
  protected abstract fun onRender(param0: SkCanvas?, param1: RenderContext?)

  /**
   * C++ original:
   * ```cpp
   * virtual const RenderNode* onNodeAt(const SkPoint& p)   const = 0
   * ```
   */
  protected abstract fun onNodeAt(p: SkPoint): RenderNode

  public data class RenderContext public constructor(
    public var fColorFilter: Int,
    public var fShader: Int,
    public var fMaskShader: Int,
    public var fBlender: Int,
    public var fShaderCTM: Int,
    public var fMaskCTM: Int,
    public var fOpacity: Float,
  ) {
    public fun requiresIsolation(): Boolean {
      TODO("Implement requiresIsolation")
    }

    public fun modulatePaint(
      ctm: SkMatrix,
      paint: SkPaint?,
      isLayerPaint: Boolean = TODO(),
    ) {
      TODO("Implement modulatePaint")
    }
  }

  public data class ScopedRenderContext public constructor(
    private var fCanvas: SkCanvas?,
    private var fCtx: undefined.RenderContext,
    private var fMaskShader: Int,
    private var fRestoreCount: Int,
  ) {
    public fun assign(that: undefined.ScopedRenderContext) {
      TODO("Implement assign")
    }

    public fun `get`(): undefined.RenderContext {
      TODO("Implement get")
    }

    public fun modulateOpacity(opacity: Float): undefined.ScopedRenderContext {
      TODO("Implement modulateOpacity")
    }

    public fun modulateColorFilter(cf: SkSp<SkColorFilter>): undefined.ScopedRenderContext {
      TODO("Implement modulateColorFilter")
    }

    public fun modulateShader(sh: SkSp<SkShader>, shaderCtm: SkMatrix): undefined.ScopedRenderContext {
      TODO("Implement modulateShader")
    }

    public fun modulateMaskShader(ms: SkSp<SkShader>, msCtm: SkMatrix): undefined.ScopedRenderContext {
      TODO("Implement modulateMaskShader")
    }

    public fun modulateBlender(blender: SkSp<SkBlender>): undefined.ScopedRenderContext {
      TODO("Implement modulateBlender")
    }

    public fun setIsolation(
      bounds: SkRect,
      ctm: SkMatrix,
      doIsolate: Boolean,
    ): undefined.ScopedRenderContext {
      TODO("Implement setIsolation")
    }

    public fun setFilterIsolation(
      bounds: SkRect,
      ctm: SkMatrix,
      filter: SkSp<SkImageFilter>,
    ): undefined.ScopedRenderContext {
      TODO("Implement setFilterIsolation")
    }

    private fun toNew(param0: ULong) {
      TODO("Implement toNew")
    }

    private fun toNew(param0: ULong, param1: Unit?) {
      TODO("Implement toNew")
    }
  }
}

public typealias CustomRenderNodeINHERITED = RenderNode
