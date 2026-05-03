package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import undefined.RenderStepID

/**
 * C++ original:
 * ```cpp
 * class TessellateWedgesRenderStep final : public RenderStep {
 * public:
 *     // 'vertexBuffer' and 'indexBuffer' must have been returned by CreateVertexTemplate(), but they
 *     // can be shared by all instances of TessellateWedgesRenderStep.
 *     TessellateWedgesRenderStep(Layout, RenderStepID, bool infinitySupport, DepthStencilSettings,
 *                                StaticBufferManager*);
 *
 *     ~TessellateWedgesRenderStep() override;
 *
 *     static std::pair<BindBufferInfo, BindBufferInfo> CreateVertexTemplate(StaticBufferManager*);
 *
 *     std::string vertexSkSL() const override;
 *     void writeVertices(DrawWriter*, const DrawParams&, uint32_t ssboIndex) const override;
 *     void writeUniformsAndTextures(const DrawParams&, PipelineDataGatherer*) const override;
 *
 * private:
 *     // Points to the static buffers holding the fixed indexed vertex template for drawing instances.
 *     BindBufferInfo fVertexBuffer;
 *     BindBufferInfo fIndexBuffer;
 *     bool fInfinitySupport;
 * }
 * ```
 */
public class TessellateWedgesRenderStep public constructor(
  param0: Layout,
  param1: RenderStepID,
  infinitySupport: Boolean,
  param3: DepthStencilSettings,
  param4: StaticBufferManager,
) : RenderStep() {
  /**
   * C++ original:
   * ```cpp
   * BindBufferInfo fVertexBuffer
   * ```
   */
  private var fVertexBuffer: Int = TODO("Initialize fVertexBuffer")

  /**
   * C++ original:
   * ```cpp
   * BindBufferInfo fIndexBuffer
   * ```
   */
  private var fIndexBuffer: Int = TODO("Initialize fIndexBuffer")

  /**
   * C++ original:
   * ```cpp
   * bool fInfinitySupport
   * ```
   */
  private var fInfinitySupport: Boolean = TODO("Initialize fInfinitySupport")

  /**
   * C++ original:
   * ```cpp
   * TessellateWedgesRenderStep(Layout, RenderStepID, bool infinitySupport, DepthStencilSettings,
   *                                StaticBufferManager*)
   * ```
   */
  public constructor(
    layout: Layout,
    renderStepID: RenderStepID,
    infinitySupport: Boolean,
    depthStencilSettings: DepthStencilSettings,
    bufferManager: StaticBufferManager?,
  ) : this(TODO(), TODO(), TODO(), TODO(), TODO(), TODO(), TODO(), TODO()) {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * std::string TessellateWedgesRenderStep::vertexSkSL() const {
   *     return SkSL::String::printf(
   *             "float2 localCoord;\n"
   *             "if (resolveLevel_and_idx.x < 0) {\n"
   *                 // A negative resolve level means this is the fan point.
   *                 "localCoord = fanPointAttrib;\n"
   *             "} else {\n"
   *                 // TODO: Approximate perspective scaling to match how PatchWriter is configured
   *                 // (or provide explicit tessellation level in instance data instead of
   *                 // replicating work)
   *                 "float2x2 vectorXform = float2x2(localToDevice[0].xy, localToDevice[1].xy);\n"
   *                 "localCoord = tessellate_filled_curve("
   *                     "vectorXform, resolveLevel_and_idx.x, resolveLevel_and_idx.y, p01, p23, %s);\n"
   *             "}\n"
   *             "float4 devPosition = localToDevice * float4(localCoord, 0.0, 1.0);\n"
   *             "devPosition.z = depth;\n"
   *             "stepLocalCoords = localCoord;\n",
   *             fInfinitySupport ? "curve_type_using_inf_support(p23)" : "curveType");
   * }
   * ```
   */
  public override fun vertexSkSL(): Int {
    TODO("Implement vertexSkSL")
  }

  /**
   * C++ original:
   * ```cpp
   * void TessellateWedgesRenderStep::writeVertices(DrawWriter* dw,
   *                                                const DrawParams& params,
   *                                                uint32_t ssboIndex) const {
   *     SkPath path = params.geometry().shape().asPath(); // TODO: Iterate the Shape directly
   *
   *     int patchReserveCount = FixedCountWedges::PreallocCount(path.countVerbs());
   *     Writer writer{fInfinitySupport ? kAttribs : kAttribsWithCurveType,
   *                   *dw,
   *                   fVertexBuffer,
   *                   fIndexBuffer,
   *                   patchReserveCount};
   *     writer.updatePaintDepthAttrib(params.order().depthAsFloat());
   *     writer.updateSsboIndexAttrib(ssboIndex);
   *
   *     // The vector xform approximates how the control points are transformed by the shader to
   *     // more accurately compute how many *parametric* segments are needed.
   *     // TODO: This doesn't account for perspective division yet, which will require updating the
   *     // approximate transform based on each verb's control points' bounding box.
   *     SkASSERT(params.transform().type() < Transform::Type::kPerspective);
   *     writer.setShaderTransform(wangs_formula::VectorXform{params.transform().matrix()},
   *                               params.transform().maxScaleFactor());
   *
   *     // TODO: Essentially the same as PathWedgeTessellator::write_patches but with a different
   *     // PatchWriter template.
   *     // For wedges, we iterate over each contour explicitly, using a fan point position that is in
   *     // the midpoint of the current contour.
   *     MidpointContourParser parser{path};
   *     while (parser.parseNextContour()) {
   *         writer.updateFanPointAttrib(parser.currentMidpoint());
   *         SkPoint lastPoint = {0, 0};
   *         SkPoint startPoint = {0, 0};
   *         for (auto [verb, pts, w] : parser.currentContour()) {
   *             switch (verb) {
   *                 case SkPathVerb::kMove:
   *                     startPoint = lastPoint = pts[0];
   *                     break;
   *                 case SkPathVerb::kLine:
   *                     // Unlike curve tessellation, wedges have to handle lines as part of the patch,
   *                     // effectively forming a single triangle with the fan point.
   *                     writer.writeLine(pts[0], pts[1]);
   *                     lastPoint = pts[1];
   *                     break;
   *                 case SkPathVerb::kQuad:
   *                     writer.writeQuadratic(pts);
   *                     lastPoint = pts[2];
   *                     break;
   *                 case SkPathVerb::kConic:
   *                     writer.writeConic(pts, *w);
   *                     lastPoint = pts[2];
   *                     break;
   *                 case SkPathVerb::kCubic:
   *                     writer.writeCubic(pts);
   *                     lastPoint = pts[3];
   *                     break;
   *                 default: break;
   *             }
   *         }
   *
   *         // Explicitly close the contour with another line segment, which also differs from curve
   *         // tessellation since that approach's triangle step automatically closes the contour.
   *         if (lastPoint != startPoint) {
   *             writer.writeLine(lastPoint, startPoint);
   *         }
   *     }
   * }
   * ```
   */
  public override fun writeVertices(
    dw: DrawWriter?,
    param1: DrawParams,
    ssboIndex: UInt,
  ) {
    TODO("Implement writeVertices")
  }

  /**
   * C++ original:
   * ```cpp
   * void TessellateWedgesRenderStep::writeUniformsAndTextures(const DrawParams& params,
   *                                                           PipelineDataGatherer* gatherer) const {
   *     SkDEBUGCODE(gatherer->checkRewind());
   *     SkDEBUGCODE(UniformExpectationsValidator uev(gatherer, this->uniforms());)
   *
   *     gatherer->write(params.transform().matrix());
   * }
   * ```
   */
  public override fun writeUniformsAndTextures(param0: DrawParams, gatherer: PipelineDataGatherer?) {
    TODO("Implement writeUniformsAndTextures")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static std::pair<BindBufferInfo, BindBufferInfo> CreateVertexTemplate(StaticBufferManager*)
     * ```
     */
    public fun createVertexTemplate(param0: StaticBufferManager?): Int {
      TODO("Implement createVertexTemplate")
    }
  }
}
