package org.skia.gpu

import kotlin.Char
import kotlin.Int
import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * class SDFTextLCDRenderStep final : public RenderStep {
 * public:
 *     SDFTextLCDRenderStep(Layout);
 *
 *     ~SDFTextLCDRenderStep() override;
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
public class SDFTextLCDRenderStep public constructor(
  layout: Layout,
) : RenderStep(TODO(), TODO(), TODO(), TODO(), TODO(), TODO(), TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * std::string SDFTextLCDRenderStep::vertexSkSL() const {
   *     // Returns the body of a vertex function, which must define a float4 devPosition variable and
   *     // must write to an already-defined float2 stepLocalCoords variable.
   *     return "texIndex = half(indexAndFlags.x);"
   *            "float4 devPosition = text_vertex_fn(float2(sk_VertexID >> 1, sk_VertexID & 1), "
   *                                                "subRunDeviceMatrix, "
   *                                                "deviceToLocal, "
   *                                                "atlasSizeInv, "
   *                                                "float2(size), "
   *                                                "float2(uvPos), "
   *                                                "xyPos, "
   *                                                "strikeToSourceScale, "
   *                                                "depth, "
   *                                                "textureCoords, "
   *                                                "unormTexCoords, "
   *                                                "stepLocalCoords);";
   * }
   * ```
   */
  public override fun vertexSkSL(): Int {
    TODO("Implement vertexSkSL")
  }

  /**
   * C++ original:
   * ```cpp
   * std::string SDFTextLCDRenderStep::texturesAndSamplersSkSL(
   *         const ResourceBindingRequirements& bindingReqs, int* nextBindingIndex) const {
   *     std::string result;
   *
   *     for (unsigned int i = 0; i < kNumSDFAtlasTextures; ++i) {
   *         result += EmitSamplerLayout(bindingReqs, nextBindingIndex);
   *         SkSL::String::appendf(&result, " sampler2D sdf_atlas_%u;\n", i);
   *     }
   *
   *     return result;
   * }
   * ```
   */
  public override fun texturesAndSamplersSkSL(bindingReqs: ResourceBindingRequirements, nextBindingIndex: Int?): Int {
    TODO("Implement texturesAndSamplersSkSL")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* SDFTextLCDRenderStep::fragmentCoverageSkSL() const {
   *     // The returned SkSL must write its coverage into a 'half4 outputCoverage' variable (defined in
   *     // the calling code) with the actual coverage splatted out into all four channels.
   *
   *     // TODO: To minimize the number of shaders generated this is the full affine shader.
   *     // For best performance it may be worth creating the uniform scale shader as well,
   *     // as that's the most common case.
   *     // TODO: Need to add 565 support.
   *     // TODO: Need aliased and possibly sRGB support.
   *     static_assert(kNumSDFAtlasTextures == 4);
   *     return "outputCoverage = sdf_text_lcd_coverage_fn(textureCoords, "
   *                                                      "pixelGeometryDelta, "
   *                                                      "gammaParams, "
   *                                                      "unormTexCoords, "
   *                                                      "texIndex, "
   *                                                      "sdf_atlas_0, "
   *                                                      "sdf_atlas_1, "
   *                                                      "sdf_atlas_2, "
   *                                                      "sdf_atlas_3);";
   * }
   * ```
   */
  public override fun fragmentCoverageSkSL(): Char {
    TODO("Implement fragmentCoverageSkSL")
  }

  /**
   * C++ original:
   * ```cpp
   * void SDFTextLCDRenderStep::writeVertices(DrawWriter* dw,
   *                                          const DrawParams& params,
   *                                          uint32_t ssboIndex) const {
   *     const SubRunData& subRunData = params.geometry().subRunData();
   *     subRunData.subRun()->vertexFiller().fillInstanceData(dw,
   *                                                          subRunData.startGlyphIndex(),
   *                                                          subRunData.glyphCount(),
   *                                                          subRunData.subRun()->instanceFlags(),
   *                                                          ssboIndex,
   *                                                          subRunData.subRun()->glyphs(),
   *                                                          params.order().depthAsFloat());
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
   * void SDFTextLCDRenderStep::writeUniformsAndTextures(const DrawParams& params,
   *                                                     PipelineDataGatherer* gatherer) const {
   *     SkDEBUGCODE(gatherer->checkRewind());
   *     SkDEBUGCODE(UniformExpectationsValidator uev(gatherer, this->uniforms());)
   *
   *     const SubRunData& subRunData = params.geometry().subRunData();
   *     unsigned int numProxies;
   *     Recorder* recorder = subRunData.recorder();
   *     const sk_sp<TextureProxy>* proxies =
   *             recorder->priv().atlasProvider()->textAtlasManager()->getProxies(
   *                     subRunData.subRun()->maskFormat(), &numProxies);
   *     SkASSERT(proxies && numProxies > 0);
   *
   *     // write uniforms
   *     gatherer->write(params.transform().matrix());  // subRunDeviceMatrix
   *     gatherer->write(subRunData.deviceToLocal());
   *     SkV2 atlasDimensionsInverse = {1.f/proxies[0]->dimensions().width(),
   *                                    1.f/proxies[0]->dimensions().height()};
   *     gatherer->write(atlasDimensionsInverse);
   *
   *     // compute and write pixelGeometry vector
   *     SkV2 pixelGeometryDelta = {0, 0};
   *     if (SkPixelGeometryIsH(subRunData.pixelGeometry())) {
   *         pixelGeometryDelta = {1.f/(3*proxies[0]->dimensions().width()), 0};
   *     } else if (SkPixelGeometryIsV(subRunData.pixelGeometry())) {
   *         pixelGeometryDelta = {0, 1.f/(3*proxies[0]->dimensions().height())};
   *     }
   *     if (SkPixelGeometryIsBGR(subRunData.pixelGeometry())) {
   *         pixelGeometryDelta = -pixelGeometryDelta;
   *     }
   *     gatherer->writeHalf(pixelGeometryDelta);
   *
   *     // compute and write gamma adjustment
   *     auto dfAdjustTable = sktext::gpu::DistanceFieldAdjustTable::Get();
   *     float redCorrection = dfAdjustTable->getAdjustment(SkColorGetR(subRunData.luminanceColor()),
   *                                                        subRunData.useGammaCorrectDistanceTable());
   *     float greenCorrection = dfAdjustTable->getAdjustment(SkColorGetG(subRunData.luminanceColor()),
   *                                                          subRunData.useGammaCorrectDistanceTable());
   *     float blueCorrection = dfAdjustTable->getAdjustment(SkColorGetB(subRunData.luminanceColor()),
   *                                                         subRunData.useGammaCorrectDistanceTable());
   *     SkV4 gammaParams = {redCorrection, greenCorrection, blueCorrection,
   *                         subRunData.useGammaCorrectDistanceTable() ? 1.f : 0.f};
   *     gatherer->writeHalf(gammaParams);
   *
   *     // write textures and samplers
   *     for (unsigned int i = 0; i < numProxies; ++i) {
   *         gatherer->add(proxies[i], {SkFilterMode::kLinear, SkTileMode::kClamp});
   *     }
   *     // If the atlas has less than 4 active proxies we still need to set up samplers for the shader.
   *     for (unsigned int i = numProxies; i < kNumSDFAtlasTextures; ++i) {
   *         gatherer->add(proxies[0], {SkFilterMode::kLinear, SkTileMode::kClamp});
   *     }
   * }
   * ```
   */
  public override fun writeUniformsAndTextures(param0: DrawParams, gatherer: PipelineDataGatherer?) {
    TODO("Implement writeUniformsAndTextures")
  }
}
