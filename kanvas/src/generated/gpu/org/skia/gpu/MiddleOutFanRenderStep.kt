package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * class MiddleOutFanRenderStep final : public RenderStep {
 * public:
 *     // TODO: If this takes DepthStencilSettings directly and a way to adjust the flags to specify
 *     // that it performs shading, this RenderStep definition can be shared between the stencil and
 *     // the convex rendering variants.
 *     MiddleOutFanRenderStep(Layout, bool evenOdd);
 *
 *     ~MiddleOutFanRenderStep() override;
 *
 *     std::string vertexSkSL() const override;
 *     void writeVertices(DrawWriter*, const DrawParams&, uint32_t ssboIndex) const override;
 *     void writeUniformsAndTextures(const DrawParams&, PipelineDataGatherer*) const override;
 * }
 * ```
 */
public class MiddleOutFanRenderStep public constructor(
  layout: Layout,
  evenOdd: Boolean,
) : RenderStep(TODO(), TODO(), TODO(), TODO(), TODO(), TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * std::string MiddleOutFanRenderStep::vertexSkSL() const {
   *     return
   *         "float4 devPosition = localToDevice * float4(position, 0.0, 1.0);\n"
   *         "devPosition.z = depth;\n"
   *         "stepLocalCoords = position;\n";
   * }
   * ```
   */
  public override fun vertexSkSL(): Int {
    TODO("Implement vertexSkSL")
  }

  /**
   * C++ original:
   * ```cpp
   * void MiddleOutFanRenderStep::writeVertices(DrawWriter* writer,
   *                                            const DrawParams& params,
   *                                            uint32_t ssboIndex) const {
   *     // TODO: Have Shape provide a path-like iterator so we don't actually have to convert non
   *     // paths to SkPath just to iterate their pts/verbs
   *     SkPath path = params.geometry().shape().asPath();
   *
   *     const int maxTrianglesInFans = std::max(path.countVerbs() - 2, 0);
   *
   *     float depth = params.order().depthAsFloat();
   *
   *     DrawWriter::Vertices verts{*writer};
   *     verts.reserve(maxTrianglesInFans * 3);
   *     for (tess::PathMiddleOutFanIter it(path); !it.done();) {
   *         for (auto [p0, p1, p2] : it.nextStack()) {
   *             verts.append(3) << p0 << depth << ssboIndex
   *                             << p1 << depth << ssboIndex
   *                             << p2 << depth << ssboIndex;
   *         }
   *     }
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
   * void MiddleOutFanRenderStep::writeUniformsAndTextures(const DrawParams& params,
   *                                                       PipelineDataGatherer* gatherer) const {
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
}
