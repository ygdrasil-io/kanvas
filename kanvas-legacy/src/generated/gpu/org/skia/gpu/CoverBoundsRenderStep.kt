package org.skia.gpu

import kotlin.Int
import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * class CoverBoundsRenderStep final : public RenderStep {
 * public:
 *     CoverBoundsRenderStep(Layout, RenderStep::RenderStepID, DepthStencilSettings);
 *
 *     ~CoverBoundsRenderStep() override;
 *
 *     std::string vertexSkSL() const override;
 *     void writeVertices(DrawWriter*, const DrawParams&, uint32_t ssboIndex) const override;
 *     void writeUniformsAndTextures(const DrawParams&, PipelineDataGatherer*) const override;
 *
 * private:
 * }
 * ```
 */
public class CoverBoundsRenderStep public constructor(
  layout: Layout,
  renderStepID: RenderStep.RenderStepID,
  dsSettings: DepthStencilSettings,
) : RenderStep(TODO(), TODO(), TODO(), TODO(), TODO(), TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * std::string CoverBoundsRenderStep::vertexSkSL() const {
   *     // Returns the body of a vertex function, which must define a float4 devPosition variable and
   *     // must write to an already-defined float2 stepLocalCoords variable.
   *     return "float4 devPosition = cover_bounds_vertex_fn("
   *                                          "float2(sk_VertexID / 2, sk_VertexID % 2), "
   *                                          "bounds, depth, float3x3(mat0, mat1, mat2), "
   *                                          "stepLocalCoords);\n";
   * }
   * ```
   */
  public override fun vertexSkSL(): Int {
    TODO("Implement vertexSkSL")
  }

  /**
   * C++ original:
   * ```cpp
   * void CoverBoundsRenderStep::writeVertices(DrawWriter* writer,
   *                                           const DrawParams& params,
   *                                           uint32_t ssboIndex) const {
   *     // Each instance is 4 vertices, forming 2 triangles from a single triangle strip, so no indices
   *     // are needed. sk_VertexID is used to place vertex positions, so no vertex buffer is needed.
   *     DrawWriter::Instances instances{*writer, {}, {}, 4};
   *
   *     skvx::float4 bounds;
   *     if (params.geometry().isShape() && params.geometry().shape().inverted()) {
   *         // Normally all bounding boxes are sorted such that l<r and t<b. We upload an inverted
   *         // rectangle [r,b,l,t] when it's an inverse fill to encode that the bounds are already in
   *         // device space and then the VS will use the inverse of the transform to compute local
   *         // coordinates.
   *         bounds = skvx::shuffle</*R*/2, /*B*/3, /*L*/0, /*T*/1>(
   *                 skvx::cast<float>(skvx::int4::Load(&params.scissor())));
   *     } else {
   *         bounds = params.geometry().bounds().ltrb();
   *     }
   *
   *     // Since the local coords always have Z=0, we can discard the 3rd row and column of the matrix.
   *     const SkM44& m = params.transform().matrix();
   *     instances.append(1) << bounds << params.order().depthAsFloat() << ssboIndex
   *                         << m.rc(0,0) << m.rc(1,0) << m.rc(3,0)
   *                         << m.rc(0,1) << m.rc(1,1) << m.rc(3,1)
   *                         << m.rc(0,3) << m.rc(1,3) << m.rc(3,3);
   * }
   * ```
   */
  public override fun writeVertices(
    writer: DrawWriter?,
    param1: DrawParams,
    ssboIndex: UInt,
  ) {
    TODO("Implement writeVertices")
  }

  /**
   * C++ original:
   * ```cpp
   * void CoverBoundsRenderStep::writeUniformsAndTextures(const DrawParams&,
   *                                                      PipelineDataGatherer* gatherer) const {
   *     // All data is uploaded as instance attributes, so no uniforms are needed.
   *     SkDEBUGCODE(gatherer->checkRewind());
   * }
   * ```
   */
  public override fun writeUniformsAndTextures(param0: DrawParams, gatherer: PipelineDataGatherer?) {
    TODO("Implement writeUniformsAndTextures")
  }
}
