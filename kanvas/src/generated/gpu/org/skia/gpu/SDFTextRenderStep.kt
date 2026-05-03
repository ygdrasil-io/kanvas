package org.skia.gpu

import kotlin.Char
import kotlin.Int
import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * class SDFTextRenderStep final : public RenderStep {
 * public:
 *     SDFTextRenderStep(Layout);
 *
 *     ~SDFTextRenderStep() override;
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
public class SDFTextRenderStep public constructor(
  layout: Layout,
) : RenderStep(TODO(), TODO(), TODO(), TODO(), TODO(), TODO(), TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * std::string SDFTextRenderStep::vertexSkSL() const {
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
   * std::string SDFTextRenderStep::texturesAndSamplersSkSL(
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
   * const char* SDFTextRenderStep::fragmentCoverageSkSL() const {
   *     // The returned SkSL must write its coverage into a 'half4 outputCoverage' variable (defined in
   *     // the calling code) with the actual coverage splatted out into all four channels.
   *
   *     // TODO: To minimize the number of shaders generated this is the full affine shader.
   *     // For best performance it may be worth creating the uniform scale shader as well,
   *     // as that's the most common case.
   *     // TODO: Need to add 565 support.
   *     // TODO: Need aliased and possibly sRGB support.
   *     static_assert(kNumSDFAtlasTextures == 4);
   *     return "outputCoverage = sdf_text_coverage_fn(sample_indexed_atlas(textureCoords, "
   *                                                                       "int(texIndex), "
   *                                                                       "sdf_atlas_0, "
   *                                                                       "sdf_atlas_1, "
   *                                                                       "sdf_atlas_2, "
   *                                                                       "sdf_atlas_3).r, "
   *                                                  "gammaParams, "
   *                                                  "unormTexCoords);";
   * }
   * ```
   */
  public override fun fragmentCoverageSkSL(): Char {
    TODO("Implement fragmentCoverageSkSL")
  }

  /**
   * C++ original:
   * ```cpp
   * void SDFTextRenderStep::writeVertices(DrawWriter* dw,
   *                                       const DrawParams& params,
   *                                       uint32_t ssboIndex) const {
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
   * void SDFTextRenderStep::writeUniformsAndTextures(const DrawParams& params,
   *                                                  PipelineDataGatherer* gatherer) const {
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
   *     float gammaAdjustment = 0;
   *     // TODO: generate LCD adjustment
   * #if defined(SK_GAMMA_APPLY_TO_A8)
   *     auto dfAdjustTable = sktext::gpu::DistanceFieldAdjustTable::Get();
   *     // TODO: don't do this for aliased text
   *     U8CPU lum = SkColorSpaceLuminance::computeLuminance(SK_GAMMA_EXPONENT,
   *                                                         subRunData.luminanceColor());
   *     gammaAdjustment = dfAdjustTable->getAdjustment(lum, subRunData.useGammaCorrectDistanceTable());
   * #endif
   *     SkV2 gammaParams = {gammaAdjustment, subRunData.useGammaCorrectDistanceTable() ? 1.f : 0.f};
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
