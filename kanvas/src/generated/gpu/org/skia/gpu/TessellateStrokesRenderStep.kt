package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * class TessellateStrokesRenderStep final : public RenderStep {
 * public:
 *     // TODO: If this takes DepthStencilSettings directly and a way to adjust the flags to specify
 *     // that it performs shading, this RenderStep definition could be used to handle inverse-filled
 *     // stroke draws.
 *     explicit TessellateStrokesRenderStep(Layout, bool infinitySupport);
 *
 *     ~TessellateStrokesRenderStep() override;
 *
 *     std::string vertexSkSL() const override;
 *     void writeVertices(DrawWriter*, const DrawParams&, uint32_t ssboIndex) const override;
 *     void writeUniformsAndTextures(const DrawParams&, PipelineDataGatherer*) const override;
 *
 * private:
 *     bool fInfinitySupport;
 * }
 * ```
 */
public class TessellateStrokesRenderStep public constructor(
  layout: Layout,
  infinitySupport: Boolean,
) : RenderStep(TODO(), TODO(), TODO(), TODO(), TODO(), TODO(), TODO(), TODO()) {
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
   * std::string TessellateStrokesRenderStep::vertexSkSL() const {
   *     // TODO: Assumes vertex ID support for now, max edges must equal
   *     // skgpu::tess::FixedCountStrokes::kMaxEdges -> (2^14 - 1) -> 16383
   *     return SkSL::String::printf(
   *             "float edgeID = float(sk_VertexID >> 1);\n"
   *             "if ((sk_VertexID & 1) != 0) {"
   *                 "edgeID = -edgeID;"
   *             "}\n"
   *             "float2x2 affine = float2x2(affineMatrix.xy, affineMatrix.zw);\n"
   *             "float4 devAndLocalCoords = tessellate_stroked_curve("
   *                     "edgeID, 16383, affine, translate, maxScale, p01, p23, prevPoint,"
   *                     "stroke, %s);\n"
   *             "float4 devPosition = float4(devAndLocalCoords.xy, depth, 1.0);\n"
   *             "stepLocalCoords = devAndLocalCoords.zw;\n",
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
   * void TessellateStrokesRenderStep::writeVertices(DrawWriter* dw,
   *                                                 const DrawParams& params,
   *                                                 uint32_t ssboIndex) const {
   *     SkPath path = params.geometry().shape().asPath(); // TODO: Iterate the Shape directly
   *
   *     int patchReserveCount = FixedCountStrokes::PreallocCount(path.countVerbs());
   *     // Stroke tessellation does not use fixed indices or vertex data, and only needs the vertex ID
   *     static const BindBufferInfo kNullBinding = {};
   *     // TODO: All HW that Graphite will run on should support instancing ith sk_VertexID, but when
   *     // we support Vulkan+Swiftshader, we will need the vertex buffer ID fallback unless Swiftshader
   *     // has figured out how to support vertex IDs before then.
   *     Writer writer{fInfinitySupport ? kAttribs : kAttribsWithCurveType,
   *                   *dw,
   *                   kNullBinding,
   *                   kNullBinding,
   *                   patchReserveCount};
   *     writer.updatePaintDepthAttrib(params.order().depthAsFloat());
   *     writer.updateSsboIndexAttrib(ssboIndex);
   *
   *     // The vector xform approximates how the control points are transformed by the shader to
   *     // more accurately compute how many *parametric* segments are needed.
   *     // getMaxScale() returns -1 if it can't compute a scale factor (e.g. perspective), taking the
   *     // absolute value automatically converts that to an identity scale factor for our purposes.
   *     writer.setShaderTransform(wangs_formula::VectorXform{params.transform().matrix()},
   *                               params.transform().maxScaleFactor());
   *
   *     SkASSERT(params.isStroke());
   *     writer.updateStrokeParamsAttrib({params.strokeStyle().halfWidth(),
   *                                      params.strokeStyle().joinLimit()});
   *
   *     for (auto [verb, pts, w] : SkPathPriv::Iterate(path)) {
   *         switch (verb) {
   *             case SkPathVerb::kMove:
   *                 // This automatically joins the last contour with the first contour (deferred) if
   *                 // the contour is closed. If the contour is not closed, it automatically adds
   *                 // additional patches for the end cap of the last patch and the beginning cap of the
   *                 // deferred patch. This does nothing if this is the beginning of the first contour.
   *                 writer.writeDeferredStrokePatch(pts[0], params.strokeStyle().cap());
   *                 break;
   *
   *             case SkPathVerb::kClose:
   *                 // Draws a line back to the starting point of the contour and writes any deferred
   *                 // patch with a join (instead of caps). Or if the contour was empty, draws a cap.
   *                 // Since any deferred patch is consumed, the next moveTo's writeDeferredStrokePatch
   *                 // will do nothing but record the beginning of the new contour.
   *                 writer.closeDeferredStrokePatch(params.strokeStyle().cap());
   *                 break;
   *
   *             case SkPathVerb::kLine:
   *                 writer.writeLine(pts[0], pts[1]);
   *                 break;
   *
   *             case SkPathVerb::kQuad:
   *                 if (ConicHasCusp(pts)) {
   *                     // The cusp is always at the midtangent.
   *                     SkPoint cusp = SkEvalQuadAt(pts, SkFindQuadMidTangent(pts));
   *                     writer.writeCircle(cusp);
   *                     // A quad can only have a cusp if it's flat with a 180-degree turnaround.
   *                     writer.writeLine(pts[0], cusp);
   *                     writer.writeLine(cusp, pts[2]);
   *                 } else {
   *                     writer.writeQuadratic(pts);
   *                 }
   *                 break;
   *
   *             case SkPathVerb::kConic:
   *                 if (ConicHasCusp(pts)) {
   *                     // The cusp is always at the midtangent.
   *                     SkConic conic(pts, *w);
   *                     SkPoint cusp = conic.evalAt(conic.findMidTangent());
   *                     writer.writeCircle(cusp);
   *                     // A conic can only have a cusp if it's flat with a 180-degree turnaround.
   *                     writer.writeLine(pts[0], cusp);
   *                     writer.writeLine(cusp, pts[2]);
   *                 } else {
   *                     writer.writeConic(pts, *w);
   *                 }
   *                 break;
   *
   *             case SkPathVerb::kCubic: {
   *                 SkPoint chops[10];
   *                 float T[2];
   *                 bool areCusps;
   *                 int numChops = FindCubicConvex180Chops(pts, T, &areCusps);
   *                 if (numChops == 0) {
   *                     writer.writeCubic(pts);
   *                 } else if (numChops == 1) {
   *                     SkChopCubicAt(pts, chops, T[0]);
   *                     if (areCusps) {
   *                         writer.writeCircle(chops[3]);
   *                         // In a perfect world, these 3 points would be be equal after chopping
   *                         // on a cusp.
   *                         chops[2] = chops[4] = chops[3];
   *                     }
   *                     writer.writeCubic(chops);
   *                     writer.writeCubic(chops + 3);
   *                 } else {
   *                     SkASSERT(numChops == 2);
   *                     SkChopCubicAt(pts, chops, T[0], T[1]);
   *                     if (areCusps) {
   *                         writer.writeCircle(chops[3]);
   *                         writer.writeCircle(chops[6]);
   *                         // Two cusps are only possible if it's a flat line with two 180-degree
   *                         // turnarounds.
   *                         writer.writeLine(chops[0], chops[3]);
   *                         writer.writeLine(chops[3], chops[6]);
   *                         writer.writeLine(chops[6], chops[9]);
   *                     } else {
   *                         writer.writeCubic(chops);
   *                         writer.writeCubic(chops + 3);
   *                         writer.writeCubic(chops + 6);
   *                     }
   *                 }
   *                 break;
   *             }
   *         }
   *     }
   *
   *     // Finish the last contour (next moveTo point doesn't matter)
   *     writer.writeDeferredStrokePatch({0.f, 0.f}, params.strokeStyle().cap());
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
   * void TessellateStrokesRenderStep::writeUniformsAndTextures(const DrawParams& params,
   *                                                            PipelineDataGatherer* gatherer) const {
   *     SkDEBUGCODE(gatherer->checkRewind());
   *     // TODO: Implement perspective
   *     SkASSERT(params.transform().type() < Transform::Type::kPerspective);
   *
   *     SkDEBUGCODE(UniformExpectationsValidator uev(gatherer, this->uniforms());)
   *
   *     // affineMatrix = float4 (2x2 of transform), translate = float2, maxScale = float
   *     // Column-major 2x2 of the transform.
   *     SkV4 upper = {params.transform().matrix().rc(0, 0), params.transform().matrix().rc(1, 0),
   *                   params.transform().matrix().rc(0, 1), params.transform().matrix().rc(1, 1)};
   *     gatherer->write(upper);
   *
   *     gatherer->write(SkPoint{params.transform().matrix().rc(0, 3),
   *                             params.transform().matrix().rc(1, 3)});
   *
   *     gatherer->write(params.transform().maxScaleFactor());
   * }
   * ```
   */
  public override fun writeUniformsAndTextures(param0: DrawParams, gatherer: PipelineDataGatherer?) {
    TODO("Implement writeUniformsAndTextures")
  }
}
