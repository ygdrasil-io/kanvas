package org.skia.gpu

import kotlin.Char
import kotlin.Int
import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * class AnalyticBlurRenderStep final : public RenderStep {
 * public:
 *     AnalyticBlurRenderStep(Layout);
 *     ~AnalyticBlurRenderStep() override = default;
 *
 *     std::string vertexSkSL() const override;
 *     std::string texturesAndSamplersSkSL(const ResourceBindingRequirements&,
 *                                         int* nextBindingIndex) const override;
 *     const char* fragmentCoverageSkSL() const override;
 *
 *     void writeVertices(DrawWriter*, const DrawParams&, uint32_t ssboIndex) const override;
 *     void writeUniformsAndTextures(const DrawParams&, PipelineDataGatherer*) const override;
 * }
 * ```
 */
public class AnalyticBlurRenderStep public constructor(
  layout: Layout,
) : RenderStep(TODO(), TODO(), TODO(), TODO(), TODO(), TODO(), TODO(), TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * std::string AnalyticBlurRenderStep::vertexSkSL() const {
   *     return
   *         "float4 devPosition = localToDevice * float4(position, depth, 1.0);\n"
   *         "stepLocalCoords = position;\n"
   *         "scaledShapeCoords = (deviceToScaledShape * devPosition.xy1).xy;\n";
   * }
   * ```
   */
  public override fun vertexSkSL(): Int {
    TODO("Implement vertexSkSL")
  }

  /**
   * C++ original:
   * ```cpp
   * std::string AnalyticBlurRenderStep::texturesAndSamplersSkSL(
   *         const ResourceBindingRequirements& bindingReqs, int* nextBindingIndex) const {
   *     return EmitSamplerLayout(bindingReqs, nextBindingIndex) + " sampler2D s;";
   * }
   * ```
   */
  public override fun texturesAndSamplersSkSL(bindingReqs: ResourceBindingRequirements, nextBindingIndex: Int?): Int {
    TODO("Implement texturesAndSamplersSkSL")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* AnalyticBlurRenderStep::fragmentCoverageSkSL() const {
   *     return "outputCoverage = blur_coverage_fn(scaledShapeCoords, "
   *                                              "shapeData, "
   *                                              "blurData, "
   *                                              "shapeType, "
   *                                              "s);";
   * }
   * ```
   */
  public override fun fragmentCoverageSkSL(): Char {
    TODO("Implement fragmentCoverageSkSL")
  }

  /**
   * C++ original:
   * ```cpp
   * void AnalyticBlurRenderStep::writeVertices(DrawWriter* writer,
   *                                            const DrawParams& params,
   *                                            uint32_t ssboIndex) const {
   *     const Rect& r = params.geometry().analyticBlurMask().drawBounds();
   *     DrawWriter::Vertices verts{*writer};
   *     verts.append(6) << skvx::float2(r.left(), r.top()) << ssboIndex
   *                     << skvx::float2(r.right(), r.top()) << ssboIndex
   *                     << skvx::float2(r.left(), r.bot()) << ssboIndex
   *                     << skvx::float2(r.right(), r.top()) << ssboIndex
   *                     << skvx::float2(r.right(), r.bot()) << ssboIndex
   *                     << skvx::float2(r.left(), r.bot()) << ssboIndex;
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
   * void AnalyticBlurRenderStep::writeUniformsAndTextures(const DrawParams& params,
   *                                                       PipelineDataGatherer* gatherer) const {
   *     SkDEBUGCODE(gatherer->checkRewind());
   *     SkDEBUGCODE(UniformExpectationsValidator uev(gatherer, this->uniforms());)
   *
   *     gatherer->write(params.transform().matrix());
   *
   *     const AnalyticBlurMask& blur = params.geometry().analyticBlurMask();
   *     gatherer->write(blur.deviceToScaledShape().asM33());
   *     gatherer->write(blur.shapeData().asSkRect());
   *     gatherer->writeHalf(blur.blurData());
   *     gatherer->write(static_cast<int>(blur.shapeType()));
   *     gatherer->write(params.order().depthAsFloat());
   *
   *     SkSamplingOptions samplingOptions = blur.shapeType() == AnalyticBlurMask::ShapeType::kRect
   *                                                 ? SkFilterMode::kLinear
   *                                                 : SkFilterMode::kNearest;
   *     gatherer->add(blur.refProxy(), {samplingOptions, SkTileMode::kClamp});
   * }
   * ```
   */
  public override fun writeUniformsAndTextures(param0: DrawParams, gatherer: PipelineDataGatherer?) {
    TODO("Implement writeUniformsAndTextures")
  }
}
