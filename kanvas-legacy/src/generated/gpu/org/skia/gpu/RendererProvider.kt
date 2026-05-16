package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.IntArray
import org.skia.core.SkVertices
import org.skia.math.SkPathFillType
import undefined.Args

/**
 * C++ original:
 * ```cpp
 * class RendererProvider {
 * public:
 *     ~RendererProvider();
 *
 *     static bool IsSupported(PathRendererStrategy, const Caps*);
 *
 *     // A given Caps may support more than one strategy, but only one will be used for all rendering.
 *     PathRendererStrategy pathRendererStrategy() const { return fStrategy; }
 *
 *     // TODO: Add configuration options to disable "optimization" renderers in favor of the more
 *     // general case, or renderers that won't be used by the application. When that's added, these
 *     // functions could return null.
 *
 *     // Path rendering for fills and strokes, used by the kTessellation[AndSmallAtlas] strategies.
 *     const Renderer* stencilTessellatedCurvesAndTris(SkPathFillType type) const {
 *         return &fStencilTessellatedCurves[(int) type];
 *     }
 *     const Renderer* stencilTessellatedWedges(SkPathFillType type) const {
 *         return &fStencilTessellatedWedges[(int) type];
 *     }
 *     const Renderer* convexTessellatedWedges() const { return &fConvexTessellatedWedges; }
 *     const Renderer* tessellatedStrokes() const { return &fTessellatedStrokes; }
 *
 *     // Coverage mask rendering. Used by the atlas path rendering strategies and rendering mask
 *     // filter results.
 *     const Renderer* coverageMask() const { return &fCoverageMask; }
 *
 *     // ** Specialized renderers that are used regardless of general path rendering strategy.
 *
 *     // Atlased text rendering
 *     const Renderer* bitmapText(bool useLCDText, skgpu::MaskFormat format) const {
 *         // We use 565 here to represent all LCD rendering, regardless of texture format
 *         if (useLCDText) {
 *             return &fBitmapText[(int)skgpu::MaskFormat::kA565];
 *         }
 *         SkASSERT(format != skgpu::MaskFormat::kA565);
 *         return &fBitmapText[(int)format];
 *     }
 *     const Renderer* sdfText(bool useLCDText) const { return &fSDFText[useLCDText]; }
 *
 *     // Mesh rendering
 *     const Renderer* vertices(SkVertices::VertexMode mode, bool hasColors, bool hasTexCoords) const {
 *         SkASSERT(mode != SkVertices::kTriangleFan_VertexMode); // Should be converted to kTriangles
 *         bool triStrip = mode == SkVertices::kTriangleStrip_VertexMode;
 *         return &fVertices[4*triStrip + 2*hasColors + hasTexCoords];
 *     }
 *
 *     // Filled and stroked [r]rects
 *     const Renderer* analyticRRect() const { return &fAnalyticRRect; }
 *
 *     // Per-edge AA quadrilaterals
 *     const Renderer* perEdgeAAQuad() const { return &fPerEdgeAAQuad; }
 *
 *     // Non-AA bounds filling (can handle inverse "fills" but will touch every pixel within the clip)
 *     const Renderer* nonAABounds() const { return &fNonAABoundsFill; }
 *
 *     // Circular arcs
 *     const Renderer* circularArc() const { return &fCircularArc; }
 *
 *     const Renderer* analyticBlur() const { return &fAnalyticBlur; }
 *
 *     // TODO: May need to add support for inverse filled strokes (need to check SVG spec if this is a
 *     // real thing).
 *
 *     // Iterate over all available Renderers to combine with specified paint combinations when
 *     // pre-compiling pipelines.
 *     SkSpan<const Renderer* const> renderers() const {
 *         return {fRenderers.data(), (size_t)fRenderers.size()};
 *     }
 *
 *     const RenderStep* lookup(RenderStep::RenderStepID renderStepID) const {
 *         return fRenderSteps[(int) renderStepID].get();
 *     }
 *
 * #ifdef SK_ENABLE_VELLO_SHADERS
 *     // Compute shader-based path renderer and compositor. Used with the kCompute related strategies
 *     // to coordinate the ComputeSteps that feed into the coverageMask() renderer.
 *     const VelloRenderer* velloRenderer() const { return fVelloRenderer.get(); }
 * #endif
 *
 * private:
 *     static constexpr int kPathTypeCount = 4;
 *     static constexpr int kVerticesCount = 8; // 2 modes * 2 color configs * 2 tex coord configs
 *
 *     friend class Context; // for ctor
 *
 *     RendererProvider(const Caps*, StaticBufferManager* bufferManager);
 *
 *     // Cannot be moved or copied
 *     RendererProvider(const RendererProvider&) = delete;
 *     RendererProvider(RendererProvider&&) = delete;
 *
 *     RenderStep* assumeOwnership(std::unique_ptr<RenderStep> renderStep) {
 *         int index = (int) renderStep->renderStepID();
 *         SkASSERT(!fRenderSteps[index]);
 *         fRenderSteps[index] = std::move(renderStep);
 *         return fRenderSteps[index].get();
 *     }
 *     template<typename... Args>
 *     void initRenderer(Renderer* member, Args... args) {
 *         *member = Renderer(args...);
 *         fRenderers.push_back(member);
 *     }
 *
 *     PathRendererStrategy fStrategy;
 *
 *     // Renderers are composed of 1+ steps, and some steps can be shared by multiple Renderers.
 *     // Renderers don't keep their RenderSteps alive so RendererProvider holds them here.
 *     std::unique_ptr<RenderStep> fRenderSteps[RenderStep::kNumRenderSteps];
 *
 *     // Use initRenderer() to set each member and register with `fRenderers`.
 *     Renderer fStencilTessellatedCurves[kPathTypeCount];
 *     Renderer fStencilTessellatedWedges[kPathTypeCount];
 *     Renderer fConvexTessellatedWedges;
 *     Renderer fTessellatedStrokes;
 *
 *     Renderer fCoverageMask;
 *
 *     Renderer fBitmapText[3];  // int variant
 *     Renderer fSDFText[2]; // bool isLCD
 *
 *     Renderer fAnalyticRRect;
 *     Renderer fPerEdgeAAQuad;
 *     Renderer fNonAABoundsFill;
 *     Renderer fCircularArc;
 *
 *     Renderer fAnalyticBlur;
 *
 *     Renderer fVertices[kVerticesCount];
 *
 *     // Aggregate of all enabled Renderers for convenient iteration when pre-compiling
 *     skia_private::TArray<const Renderer*> fRenderers;
 *
 * #ifdef SK_ENABLE_VELLO_SHADERS
 *     std::unique_ptr<VelloRenderer> fVelloRenderer;
 * #endif
 * }
 * ```
 */
public data class RendererProvider public constructor(
  /**
   * C++ original:
   * ```cpp
   * static constexpr int kPathTypeCount = 4
   * ```
   */
  private var fStrategy: PathRendererStrategy,
  /**
   * C++ original:
   * ```cpp
   * static constexpr int kVerticesCount = 8
   * ```
   */
  private var fRenderSteps: Int,
  /**
   * C++ original:
   * ```cpp
   * PathRendererStrategy fStrategy
   * ```
   */
  private var fStencilTessellatedCurves: IntArray,
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<RenderStep> fRenderSteps
   * ```
   */
  private var fStencilTessellatedWedges: IntArray,
  /**
   * C++ original:
   * ```cpp
   * Renderer fStencilTessellatedCurves[kPathTypeCount]
   * ```
   */
  private var fConvexTessellatedWedges: Int,
  /**
   * C++ original:
   * ```cpp
   * Renderer fStencilTessellatedWedges[kPathTypeCount]
   * ```
   */
  private var fTessellatedStrokes: Int,
  /**
   * C++ original:
   * ```cpp
   * Renderer fConvexTessellatedWedges
   * ```
   */
  private var fCoverageMask: Int,
  /**
   * C++ original:
   * ```cpp
   * Renderer fTessellatedStrokes
   * ```
   */
  private var fBitmapText: IntArray,
  /**
   * C++ original:
   * ```cpp
   * Renderer fCoverageMask
   * ```
   */
  private var fSDFText: IntArray,
  /**
   * C++ original:
   * ```cpp
   * Renderer fBitmapText[3]
   * ```
   */
  private var fAnalyticRRect: Int,
  /**
   * C++ original:
   * ```cpp
   * Renderer fSDFText[2]
   * ```
   */
  private var fPerEdgeAAQuad: Int,
  /**
   * C++ original:
   * ```cpp
   * Renderer fAnalyticRRect
   * ```
   */
  private var fNonAABoundsFill: Int,
  /**
   * C++ original:
   * ```cpp
   * Renderer fPerEdgeAAQuad
   * ```
   */
  private var fCircularArc: Int,
  /**
   * C++ original:
   * ```cpp
   * Renderer fNonAABoundsFill
   * ```
   */
  private var fAnalyticBlur: Int,
  /**
   * C++ original:
   * ```cpp
   * Renderer fCircularArc
   * ```
   */
  private var fVertices: IntArray,
  /**
   * C++ original:
   * ```cpp
   * Renderer fAnalyticBlur
   * ```
   */
  private var fRenderers: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * PathRendererStrategy pathRendererStrategy() const { return fStrategy; }
   * ```
   */
  public fun pathRendererStrategy(): PathRendererStrategy {
    TODO("Implement pathRendererStrategy")
  }

  /**
   * C++ original:
   * ```cpp
   * const Renderer* stencilTessellatedCurvesAndTris(SkPathFillType type) const {
   *         return &fStencilTessellatedCurves[(int) type];
   *     }
   * ```
   */
  public fun stencilTessellatedCurvesAndTris(type: SkPathFillType): Int {
    TODO("Implement stencilTessellatedCurvesAndTris")
  }

  /**
   * C++ original:
   * ```cpp
   * const Renderer* stencilTessellatedWedges(SkPathFillType type) const {
   *         return &fStencilTessellatedWedges[(int) type];
   *     }
   * ```
   */
  public fun stencilTessellatedWedges(type: SkPathFillType): Int {
    TODO("Implement stencilTessellatedWedges")
  }

  /**
   * C++ original:
   * ```cpp
   * const Renderer* convexTessellatedWedges() const { return &fConvexTessellatedWedges; }
   * ```
   */
  public fun convexTessellatedWedges(): Int {
    TODO("Implement convexTessellatedWedges")
  }

  /**
   * C++ original:
   * ```cpp
   * const Renderer* tessellatedStrokes() const { return &fTessellatedStrokes; }
   * ```
   */
  public fun tessellatedStrokes(): Int {
    TODO("Implement tessellatedStrokes")
  }

  /**
   * C++ original:
   * ```cpp
   * const Renderer* coverageMask() const { return &fCoverageMask; }
   * ```
   */
  public fun coverageMask(): Int {
    TODO("Implement coverageMask")
  }

  /**
   * C++ original:
   * ```cpp
   * const Renderer* bitmapText(bool useLCDText, skgpu::MaskFormat format) const {
   *         // We use 565 here to represent all LCD rendering, regardless of texture format
   *         if (useLCDText) {
   *             return &fBitmapText[(int)skgpu::MaskFormat::kA565];
   *         }
   *         SkASSERT(format != skgpu::MaskFormat::kA565);
   *         return &fBitmapText[(int)format];
   *     }
   * ```
   */
  public fun bitmapText(useLCDText: Boolean, format: MaskFormat): Int {
    TODO("Implement bitmapText")
  }

  /**
   * C++ original:
   * ```cpp
   * const Renderer* sdfText(bool useLCDText) const { return &fSDFText[useLCDText]; }
   * ```
   */
  public fun sdfText(useLCDText: Boolean): Int {
    TODO("Implement sdfText")
  }

  /**
   * C++ original:
   * ```cpp
   * const Renderer* vertices(SkVertices::VertexMode mode, bool hasColors, bool hasTexCoords) const {
   *         SkASSERT(mode != SkVertices::kTriangleFan_VertexMode); // Should be converted to kTriangles
   *         bool triStrip = mode == SkVertices::kTriangleStrip_VertexMode;
   *         return &fVertices[4*triStrip + 2*hasColors + hasTexCoords];
   *     }
   * ```
   */
  public fun vertices(
    mode: SkVertices.VertexMode,
    hasColors: Boolean,
    hasTexCoords: Boolean,
  ): Int {
    TODO("Implement vertices")
  }

  /**
   * C++ original:
   * ```cpp
   * const Renderer* analyticRRect() const { return &fAnalyticRRect; }
   * ```
   */
  public fun analyticRRect(): Int {
    TODO("Implement analyticRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * const Renderer* perEdgeAAQuad() const { return &fPerEdgeAAQuad; }
   * ```
   */
  public fun perEdgeAAQuad(): Int {
    TODO("Implement perEdgeAAQuad")
  }

  /**
   * C++ original:
   * ```cpp
   * const Renderer* nonAABounds() const { return &fNonAABoundsFill; }
   * ```
   */
  public fun nonAABounds(): Int {
    TODO("Implement nonAABounds")
  }

  /**
   * C++ original:
   * ```cpp
   * const Renderer* circularArc() const { return &fCircularArc; }
   * ```
   */
  public fun circularArc(): Int {
    TODO("Implement circularArc")
  }

  /**
   * C++ original:
   * ```cpp
   * const Renderer* analyticBlur() const { return &fAnalyticBlur; }
   * ```
   */
  public fun analyticBlur(): Int {
    TODO("Implement analyticBlur")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const Renderer* const> renderers() const {
   *         return {fRenderers.data(), (size_t)fRenderers.size()};
   *     }
   * ```
   */
  public fun renderers(): Int {
    TODO("Implement renderers")
  }

  /**
   * C++ original:
   * ```cpp
   * const RenderStep* lookup(RenderStep::RenderStepID renderStepID) const {
   *         return fRenderSteps[(int) renderStepID].get();
   *     }
   * ```
   */
  public fun lookup(renderStepID: RenderStep.RenderStepID): Int {
    TODO("Implement lookup")
  }

  /**
   * C++ original:
   * ```cpp
   * RenderStep* assumeOwnership(std::unique_ptr<RenderStep> renderStep) {
   *         int index = (int) renderStep->renderStepID();
   *         SkASSERT(!fRenderSteps[index]);
   *         fRenderSteps[index] = std::move(renderStep);
   *         return fRenderSteps[index].get();
   *     }
   * ```
   */
  private fun assumeOwnership(renderStep: RenderStep?): Int {
    TODO("Implement assumeOwnership")
  }

  /**
   * C++ original:
   * ```cpp
   *     template<typename... Args>
   *     void initRenderer(Renderer* member, Args... args) {
   *         *member = Renderer(args...);
   *         fRenderers.push_back(member);
   *     }
   * ```
   */
  private fun <Args> initRenderer(member: Renderer?, args: Args) {
    TODO("Implement initRenderer")
  }

  public companion object {
    private val kPathTypeCount: Int = TODO("Initialize kPathTypeCount")

    private val kVerticesCount: Int = TODO("Initialize kVerticesCount")

    /**
     * C++ original:
     * ```cpp
     * bool RendererProvider::IsSupported(PathRendererStrategy strategy, const Caps* caps) {
     *     switch (strategy) {
     *         case PathRendererStrategy::kTessellationAndSmallAtlas:
     *             if (caps->minPathSizeForMSAA() <= 0) {
     *                 return false; // Disabled explicitly
     *             }
     *             [[fallthrough]]; // Must support kTessellation too
     *         case PathRendererStrategy::kTessellation:
     *             // This strategy requires MSAA, which will use a supported MSAA count returned by
     *             // Caps::getDefaultMSAASampleCount(target). When avoidMSAA() returns false, this should
     *             // always be at least 4x on Graphite's supported devices.
     *             return !caps->avoidMSAA();
     *
     *         case PathRendererStrategy::kRasterAtlas:
     *             // The raster path atlas is currently always supported
     *             return true;
     *
     *         case PathRendererStrategy::kComputeAnalyticAA: [[fallthrough]];
     *         case PathRendererStrategy::kComputeMSAA16:
     *         case PathRendererStrategy::kComputeMSAA8:
     *             // The Vello compute strategies are supported if included in the build and has compute.
     * #if defined(SK_ENABLE_VELLO_SHADERS)
     *             return caps->computeSupport();
     * #else
     *             return false;
     * #endif
     *     }
     *
     *     SkUNREACHABLE;
     * }
     * ```
     */
    public fun isSupported(strategy: PathRendererStrategy, caps: Caps?): Boolean {
      TODO("Implement isSupported")
    }
  }
}
