package org.skia.gpu

import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * class BitmapTextRenderStep final : public RenderStep {
 * public:
 *     BitmapTextRenderStep(Layout, skgpu::MaskFormat variant);
 *
 *     ~BitmapTextRenderStep() override;
 *
 *     std::string vertexSkSL() const override;
 *     std::string texturesAndSamplersSkSL(const ResourceBindingRequirements&,
 *                                         int* nextBindingIndex) const override;
 *     // For a given BitmapTextRenderStep instance,
 *     // we will only use either fragmentColorSkSL() (for color text)
 *     // or fragmentCoverageSKSL() (for grayscale and LCD masks), never both.
 *     const char* fragmentColorSkSL() const override;
 *     const char* fragmentCoverageSkSL() const override;
 *     bool usesUniformsInFragmentSkSL() const override;
 *
 *     void writeVertices(DrawWriter*, const DrawParams&, uint32_t ssboIndex) const override;
 *     void writeUniformsAndTextures(const DrawParams&, PipelineDataGatherer*) const override;
 *
 * private:
 *     static SkEnumBitMask<Flags> Flags(skgpu::MaskFormat);
 * }
 * ```
 */
public class BitmapTextRenderStep public constructor(
  layout: Layout,
  variant: MaskFormat,
) : RenderStep(TODO(), TODO(), TODO(), TODO(), TODO(), TODO(), TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * std::string BitmapTextRenderStep::vertexSkSL() const {
   *     // Returns the body of a vertex function, which must define a float4 devPosition variable and
   *     // must write to an already-defined float2 stepLocalCoords variable.
   *     return "texIndex = half(indexAndFlags.x);"
   *            "maskFormat = half(indexAndFlags.y);"
   *            "float2 unormTexCoords;"
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
   * std::string BitmapTextRenderStep::texturesAndSamplersSkSL(
   *         const ResourceBindingRequirements& bindingReqs, int* nextBindingIndex) const {
   *     std::string result;
   *
   *     for (unsigned int i = 0; i < kNumTextAtlasTextures; ++i) {
   *         result += EmitSamplerLayout(bindingReqs, nextBindingIndex);
   *         SkSL::String::appendf(&result, " sampler2D text_atlas_%u;\n", i);
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
   * const char* BitmapTextRenderStep::fragmentColorSkSL() const {
   *     // The returned SkSL must write its color into a 'half4 primitiveColor' variable
   *     // (defined in the calling code).
   *     static_assert(kNumTextAtlasTextures == 4);
   *     return "primitiveColor = sample_indexed_atlas(textureCoords, "
   *                                                  "int(texIndex), "
   *                                                  "text_atlas_0, "
   *                                                  "text_atlas_1, "
   *                                                  "text_atlas_2, "
   *                                                  "text_atlas_3);";
   * }
   * ```
   */
  public override fun fragmentColorSkSL(): Char {
    TODO("Implement fragmentColorSkSL")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* BitmapTextRenderStep::fragmentCoverageSkSL() const {
   *     // The returned SkSL must write its coverage into a 'half4 outputCoverage' variable (defined in
   *     // the calling code) with the actual coverage splatted out into all four channels.
   *     static_assert(kNumTextAtlasTextures == 4);
   *     return "outputCoverage = bitmap_text_coverage_fn(sample_indexed_atlas(textureCoords, "
   *                                                                          "int(texIndex), "
   *                                                                          "text_atlas_0, "
   *                                                                          "text_atlas_1, "
   *                                                                          "text_atlas_2, "
   *                                                                          "text_atlas_3), "
   *                                                     "int(maskFormat));";
   * }
   * ```
   */
  public override fun fragmentCoverageSkSL(): Char {
    TODO("Implement fragmentCoverageSkSL")
  }

  /**
   * C++ original:
   * ```cpp
   * bool BitmapTextRenderStep::usesUniformsInFragmentSkSL() const { return false; }
   * ```
   */
  public override fun usesUniformsInFragmentSkSL(): Boolean {
    TODO("Implement usesUniformsInFragmentSkSL")
  }

  /**
   * C++ original:
   * ```cpp
   * void BitmapTextRenderStep::writeVertices(DrawWriter* dw,
   *                                          const DrawParams& params,
   *                                          uint32_t ssboIndex) const {
   *     const SubRunData& subRunData = params.geometry().subRunData();
   *
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
   * void BitmapTextRenderStep::writeUniformsAndTextures(const DrawParams& params,
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
   *     // write textures and samplers
   *     for (unsigned int i = 0; i < numProxies; ++i) {
   *         gatherer->add(proxies[i], {SkFilterMode::kNearest, SkTileMode::kClamp});
   *     }
   *     // If the atlas has less than 4 active proxies we still need to set up samplers for the shader.
   *     for (unsigned int i = numProxies; i < kNumTextAtlasTextures; ++i) {
   *         gatherer->add(proxies[0], {SkFilterMode::kNearest, SkTileMode::kClamp});
   *     }
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
     * SkEnumBitMask<RenderStep::Flags> BitmapTextRenderStep::Flags(skgpu::MaskFormat variant) {
     *     switch (variant) {
     *         case skgpu::MaskFormat::kA8:
     *             return Flags::kPerformsShading | Flags::kHasTextures | Flags::kEmitsCoverage;
     *         case skgpu::MaskFormat::kA565:
     *             return Flags::kPerformsShading | Flags::kHasTextures | Flags::kEmitsCoverage |
     *                    Flags::kLCDCoverage;
     *         case skgpu::MaskFormat::kARGB:
     *             return Flags::kPerformsShading | Flags::kHasTextures | Flags::kEmitsPrimitiveColor;
     *         default:
     *             SkUNREACHABLE;
     *     }
     * }
     * ```
     */
    private fun flags(variant: MaskFormat): Int {
      TODO("Implement flags")
    }
  }
}
