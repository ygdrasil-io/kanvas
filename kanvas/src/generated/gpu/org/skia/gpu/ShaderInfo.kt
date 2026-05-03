package org.skia.gpu

import kotlin.Boolean
import kotlin.Char
import kotlin.CharArray
import kotlin.Int
import kotlin.String
import org.skia.core.TArray
import undefined.SharedGeneratorData

/**
 * C++ original:
 * ```cpp
 * class ShaderInfo {
 * public:
 *     // Accepts a real or, by default, an invalid/nullptr pointer to a container of SamplerDescs.
 *     // Backend implementations which may utilize static / immutable samplers should pass in a real
 *     // pointer to indicate that shader node data must be analyzed to determine whether
 *     // immutable samplers are used, and if so, ascertain SamplerDescs for them.
 *     // TODO(b/366220690): Actually perform this analysis.
 *     //
 *     // If provided a valid container ptr, this function will delegate the addition of SamplerDescs
 *     // for each sampler the nodes utilize (dynamic and immutable). This way, a SamplerDesc's index
 *     // within the container can inform its binding order. Each SamplerDesc will be either:
 *     // 1) a default-constructed SamplerDesc, indicating the use of a "regular" dynamic sampler which
 *     //    requires no special handling OR
 *     // 2) a real SamplerDesc describing an immutable sampler. Backend pipelines can then use the
 *     //    desc to obtain a real immutable sampler pointer (which typically must be included in
 *     //    pipeline layouts)
 *     static std::unique_ptr<ShaderInfo> Make(const Caps*,
 *                                             const ShaderCodeDictionary*,
 *                                             const RuntimeEffectDictionary*,
 *                                             const RenderPassDesc& rpDesc,
 *                                             const RenderStep*,
 *                                             UniquePaintParamsID,
 *                                             skia_private::TArray<SamplerDesc>* outDescs = nullptr);
 *
 *     const ShaderCodeDictionary* shaderCodeDictionary() const {
 *         return fShaderCodeDictionary;
 *     }
 *     const RuntimeEffectDictionary* runtimeEffectDictionary() const {
 *         return fRuntimeEffectDictionary;
 *     }
 *
 *     const char* uniformSsboIndex() const { return fUniformSsboIndex; }
 *
 *     DstReadStrategy dstReadStrategy() const { return fDstReadStrategy; }
 *     const skgpu::BlendInfo& blendInfo() const { return fBlendInfo; }
 *
 *     const std::string& vertexSkSL() const { return fVertexSkSL; }
 *     const std::string& fragmentSkSL() const { return fFragmentSkSL; }
 *
 *     const std::string& vsLabel() const { return fVSLabel; }
 *     const std::string& fsLabel() const { return fFSLabel; }
 *     // Matches ContextUtils::GetPipelineLabel() for the same args that were passed to Make()
 *     const std::string& pipelineLabel() const { return fPipelineLabel; }
 *
 *     int numFragmentTexturesAndSamplers() const { return fNumFragmentTexturesAndSamplers; }
 *     bool hasCombinedUniforms() const { return fHasCombinedUniforms; }
 *     bool hasGradientBuffer() const { return fHasGradientBuffer; }
 *
 *     // Name used in-shader for gradient buffer uniform.
 *     static constexpr char kGradientBufferName[] = "fsGradientBuffer";
 *
 * private:
 *     struct SharedGeneratorData;
 *
 *     ShaderInfo(const ShaderCodeDictionary*,
 *                const RuntimeEffectDictionary*,
 *                const char* uniformSsboIndex,
 *                DstReadStrategy);
 *
 *     // Determines fNumFragmentTexturesAndSamplers, fHasPaintUniforms, fHasGradientBuffer,
 *     // fHasSsboIndexVarying, and if a valid SamplerDesc ptr is passed in, any immutable
 *     // sampler SamplerDescs.
 *     void generateFragmentSkSL(const Caps*,
 *                               const ShaderCodeDictionary*,
 *                               const char* label,
 *                               const RenderStep*,
 *                               UniquePaintParamsID,
 *                               TextureFormat targetFormat,
 *                               skgpu::Swizzle writeSwizzle,
 *                               skia_private::TArray<SamplerDesc>* outDescs,
 *                               const SharedGeneratorData&);
 *
 *     void generateVertexSkSL(const Caps*,
 *                             const RenderStep*,
 *                             const SharedGeneratorData&);
 *
 *     const ShaderCodeDictionary* fShaderCodeDictionary;
 *     const RuntimeEffectDictionary* fRuntimeEffectDictionary;
 *     const char* fUniformSsboIndex;
 *
 *     // The blendInfo represents the actual GPU blend operations, which may or may not completely
 *     // implement the paint and coverage blending defined by the root nodes.
 *     skgpu::BlendInfo fBlendInfo;
 *     DstReadStrategy fDstReadStrategy = DstReadStrategy::kNoneRequired;
 *
 *     std::string fVertexSkSL;
 *     std::string fFragmentSkSL;
 *     std::string fVSLabel;
 *     std::string fFSLabel;
 *     std::string fPipelineLabel;
 *
 *     int fNumFragmentTexturesAndSamplers = 0;
 *     bool fHasCombinedUniforms = false;
 *     bool fHasGradientBuffer = false;
 * }
 * ```
 */
public data class ShaderInfo public constructor(
  /**
   * C++ original:
   * ```cpp
   * static constexpr char kGradientBufferName[] = "fsGradientBuffer"
   * ```
   */
  private val fShaderCodeDictionary: ShaderCodeDictionary?,
  /**
   * C++ original:
   * ```cpp
   * const ShaderCodeDictionary* fShaderCodeDictionary
   * ```
   */
  private val fRuntimeEffectDictionary: RuntimeEffectDictionary?,
  /**
   * C++ original:
   * ```cpp
   * const RuntimeEffectDictionary* fRuntimeEffectDictionary
   * ```
   */
  private val fUniformSsboIndex: String?,
  /**
   * C++ original:
   * ```cpp
   * const char* fUniformSsboIndex
   * ```
   */
  private var fBlendInfo: Int,
  /**
   * C++ original:
   * ```cpp
   * skgpu::BlendInfo fBlendInfo
   * ```
   */
  private var fDstReadStrategy: Int,
  /**
   * C++ original:
   * ```cpp
   * DstReadStrategy fDstReadStrategy
   * ```
   */
  private var fVertexSkSL: Int,
  /**
   * C++ original:
   * ```cpp
   * std::string fVertexSkSL
   * ```
   */
  private var fFragmentSkSL: Int,
  /**
   * C++ original:
   * ```cpp
   * std::string fFragmentSkSL
   * ```
   */
  private var fVSLabel: Int,
  /**
   * C++ original:
   * ```cpp
   * std::string fVSLabel
   * ```
   */
  private var fFSLabel: Int,
  /**
   * C++ original:
   * ```cpp
   * std::string fFSLabel
   * ```
   */
  private var fPipelineLabel: Int,
  /**
   * C++ original:
   * ```cpp
   * std::string fPipelineLabel
   * ```
   */
  private var fNumFragmentTexturesAndSamplers: Int,
  /**
   * C++ original:
   * ```cpp
   * int fNumFragmentTexturesAndSamplers = 0
   * ```
   */
  private var fHasCombinedUniforms: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fHasCombinedUniforms = false
   * ```
   */
  private var fHasGradientBuffer: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * const ShaderCodeDictionary* shaderCodeDictionary() const {
   *         return fShaderCodeDictionary;
   *     }
   * ```
   */
  public fun shaderCodeDictionary(): ShaderCodeDictionary {
    TODO("Implement shaderCodeDictionary")
  }

  /**
   * C++ original:
   * ```cpp
   * const RuntimeEffectDictionary* runtimeEffectDictionary() const {
   *         return fRuntimeEffectDictionary;
   *     }
   * ```
   */
  public fun runtimeEffectDictionary(): RuntimeEffectDictionary {
    TODO("Implement runtimeEffectDictionary")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* uniformSsboIndex() const { return fUniformSsboIndex; }
   * ```
   */
  public fun uniformSsboIndex(): Char {
    TODO("Implement uniformSsboIndex")
  }

  /**
   * C++ original:
   * ```cpp
   * DstReadStrategy dstReadStrategy() const { return fDstReadStrategy; }
   * ```
   */
  public fun dstReadStrategy(): Int {
    TODO("Implement dstReadStrategy")
  }

  /**
   * C++ original:
   * ```cpp
   * const skgpu::BlendInfo& blendInfo() const { return fBlendInfo; }
   * ```
   */
  public fun blendInfo(): Int {
    TODO("Implement blendInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * const std::string& vertexSkSL() const { return fVertexSkSL; }
   * ```
   */
  public fun vertexSkSL(): Int {
    TODO("Implement vertexSkSL")
  }

  /**
   * C++ original:
   * ```cpp
   * const std::string& fragmentSkSL() const { return fFragmentSkSL; }
   * ```
   */
  public fun fragmentSkSL(): Int {
    TODO("Implement fragmentSkSL")
  }

  /**
   * C++ original:
   * ```cpp
   * const std::string& vsLabel() const { return fVSLabel; }
   * ```
   */
  public fun vsLabel(): Int {
    TODO("Implement vsLabel")
  }

  /**
   * C++ original:
   * ```cpp
   * const std::string& fsLabel() const { return fFSLabel; }
   * ```
   */
  public fun fsLabel(): Int {
    TODO("Implement fsLabel")
  }

  /**
   * C++ original:
   * ```cpp
   * const std::string& pipelineLabel() const { return fPipelineLabel; }
   * ```
   */
  public fun pipelineLabel(): Int {
    TODO("Implement pipelineLabel")
  }

  /**
   * C++ original:
   * ```cpp
   * int numFragmentTexturesAndSamplers() const { return fNumFragmentTexturesAndSamplers; }
   * ```
   */
  public fun numFragmentTexturesAndSamplers(): Int {
    TODO("Implement numFragmentTexturesAndSamplers")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasCombinedUniforms() const { return fHasCombinedUniforms; }
   * ```
   */
  public fun hasCombinedUniforms(): Boolean {
    TODO("Implement hasCombinedUniforms")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasGradientBuffer() const { return fHasGradientBuffer; }
   * ```
   */
  public fun hasGradientBuffer(): Boolean {
    TODO("Implement hasGradientBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * void ShaderInfo::generateFragmentSkSL(const Caps* caps,
   *                                       const ShaderCodeDictionary* dict,
   *                                       const char* label,
   *                                       const RenderStep* step,
   *                                       UniquePaintParamsID paintID,
   *                                       TextureFormat targetFormat,
   *                                       Swizzle writeSwizzle,
   *                                       skia_private::TArray<SamplerDesc>* outDescs,
   *                                       const SharedGeneratorData& sharedData) {
   * #if defined(SK_DEBUG)
   *     // Validate the root node structure of the key.
   *     SkASSERT(sharedData.fRootNodes.size() == 2 || sharedData.fRootNodes.size() == 3);
   *     // First node produces the source color (all snippets return a half4), so we just require that
   *     // its signature takes no extra args or just local coords.
   *     const ShaderSnippet* srcSnippet = dict->getEntry(sharedData.fRootNodes[0]->codeSnippetId());
   *     SkASSERT(!srcSnippet->needsBlenderDstColor());
   *     // TODO(b/349997190): Once SkEmptyShader doesn't use the passthrough snippet, we can assert
   *     // that srcSnippet->needsPriorStageOutput() is false.
   *     SkASSERT(!srcSnippet->needsBlenderDstColor());
   *     // Second node is the final blender, so it must take both the src color and dst color, and not
   *     // any local coordinate.
   *     const ShaderSnippet* blendSnippet = dict->getEntry(sharedData.fRootNodes[1]->codeSnippetId());
   *     SkASSERT(blendSnippet->needsPriorStageOutput() && blendSnippet->needsBlenderDstColor());
   *     SkASSERT(!blendSnippet->needsLocalCoords());
   *     // Optional third node is the clip
   *     const ShaderSnippet* clipSnippet = sharedData.fRootNodes.size() > 2 ?
   *             dict->getEntry(sharedData.fRootNodes[2]->codeSnippetId()) : nullptr;
   *     SkASSERT(!clipSnippet ||
   *              (!clipSnippet->needsPriorStageOutput() && !clipSnippet->needsBlenderDstColor()));
   * #endif
   *
   *     // Check for unexpected corruption / illegal instructions occurring in the wild.
   *     SkASSERTF_RELEASE(sharedData.fRootNodes.size() == 2 || sharedData.fRootNodes.size() == 3,
   *                       "root node size = %zu, label = %s", sharedData.fRootNodes.size(), label);
   *
   *     // Extract the root nodes for clarity
   *     const ShaderNode* const srcColorRoot = sharedData.fRootNodes[0];
   *     const ShaderNode* const finalBlendRoot = sharedData.fRootNodes[1];
   *     const int32_t finalBlendRootSnippetId = finalBlendRoot->codeSnippetId();
   *     const ShaderNode* const clipRoot =
   *             sharedData.fRootNodes.size() > 2 ? sharedData.fRootNodes[2] : nullptr;
   *
   *     // Determine the algorithm for final blending: direct HW blending, coverage-modified HW
   *     // blending (w/ or w/o dual-source blending) or via dst-read requirement.
   *     Coverage finalCoverage = step->coverage();
   *     if (finalCoverage == Coverage::kNone && SkToBool(clipRoot)) {
   *         finalCoverage = Coverage::kSingleChannel;
   *     }
   *
   *     // Initialize the final blend mode to the final snippet's blend mode. It may be changed based
   *     // upon whether or not we can use hardware blending.
   *     std::optional<SkBlendMode> finalBlendMode;
   *     if (finalBlendRootSnippetId < kBuiltInCodeSnippetIDCount &&
   *         finalBlendRootSnippetId >= kFixedBlendIDOffset) {
   *         finalBlendMode = static_cast<SkBlendMode>(finalBlendRootSnippetId - kFixedBlendIDOffset);
   *     }
   *     if (finalBlendMode.has_value() &&
   *         CanUseHardwareBlending(caps, targetFormat, *finalBlendMode, finalCoverage)) {
   *         // If we can use hardware blending, update the dstReadStrategy to be kNoneRequired to ensure
   *         // that ShaderInfo properly informs PipelineInfo of the pipeline's dst read requirement.
   *         fDstReadStrategy = DstReadStrategy::kNoneRequired;
   *     } else {
   *         // If we cannot use hardware blending, then we must perform a dst read within the shader.
   *         // Therefore we should assert that a valid strategy to do so was passed in. Later operations
   *         // also expect the blend mode to be kSrc, so update that here.
   *         SkASSERT(fDstReadStrategy != DstReadStrategy::kNoneRequired);
   *         finalBlendMode = SkBlendMode::kSrc;
   *     }
   *
   *     auto allReqFlags = srcColorRoot->requiredFlags() | finalBlendRoot->requiredFlags();
   *     if (clipRoot) {
   *         allReqFlags |= clipRoot->requiredFlags();
   *     }
   *
   *     std::string fsPreamble;
   *     const ResourceBindingRequirements& bindingReqs = caps->resourceBindingRequirements();
   *     fsPreamble += emit_intrinsic_constants(bindingReqs);
   *     fsPreamble += emit_varyings(step, "in",
   *                                 sharedData.fLiftedExpr,
   *                                 sharedData.fHasSsboIndexVarying,
   *                                 sharedData.fNeedsLocalCoords);
   *
   *     if (fDstReadStrategy == DstReadStrategy::kReadFromInput) {
   *         // If this shader reads the dst texture as an input attachment, assert that a valid set
   *         // index has been assigned within ResourceBindingRequirements.
   *         SkASSERT(bindingReqs.fInputAttachmentSetIdx != ResourceBindingRequirements::kUnassigned);
   *         // TODO: The following SkSL depends upon the fact that Vulkan is currently the only backend
   *         // that utilizes DstReadStrategy::kReadFromInput. Update accordingly if other backends add
   *         // support for this DstReadStrategy.
   *         SkSL::String::appendf(
   *                 &fsPreamble,
   *                 "layout (vulkan, input_attachment_index=%d, set=%d, binding=%d) "
   *                 "subpassInput DstTextureInput;\n",
   *                 /*input attachment idx within set=*/0,
   *                 /*input attachment set idx=*/bindingReqs.fInputAttachmentSetIdx,
   *                 /*binding=*/0);
   *     }
   *
   *     bool useGradientBuffer = caps->gradientBufferSupport() &&
   *                               (allReqFlags & SnippetRequirementFlags::kGradientBuffer);
   *     if (useGradientBuffer) {
   *         SkSL::String::appendf(&fsPreamble,
   *                               "layout (set=%d, binding=%d) readonly buffer FSGradientBuffer {\n"
   *                               "    float %s[];\n"
   *                               "};\n",
   *                               bindingReqs.fUniformsSetIdx,
   *                               bindingReqs.fGradientBufferBinding,
   *                               ShaderInfo::kGradientBufferName);
   *         fHasGradientBuffer = true;
   *     }
   *
   *     const bool useDstSampler = fDstReadStrategy == DstReadStrategy::kTextureCopy ||
   *                                fDstReadStrategy == DstReadStrategy::kTextureSample;
   *     {
   *         int binding = 0;
   *         fsPreamble += emit_textures_and_samplers(bindingReqs, sharedData.fRootNodes, &binding,
   *                                                outDescs);
   *         int paintTextureCount = binding;
   *         if (step->hasTextures()) {
   *             fsPreamble += step->texturesAndSamplersSkSL(bindingReqs, &binding);
   *             if (outDescs) {
   *                 // Determine how many render step samplers were used by comparing the binding value
   *                 // against paintTextureCount, taking into account the binding requirements. We
   *                 // assume and do not anticipate the render steps to use immutable samplers.
   *                 int renderStepSamplerCount = bindingReqs.fSeparateTextureAndSamplerBinding
   *                                                      ? (binding - paintTextureCount) / 2
   *                                                      : binding - paintTextureCount;
   *                 // Add default SamplerDescs for all the dynamic samplers used by the render step so
   *                 // the size of outDescs will be equivalent to the total number of samplers.
   *                 outDescs->push_back_n(renderStepSamplerCount);
   *             }
   *         }
   *         if (useDstSampler) {
   *             fsPreamble += EmitSamplerLayout(bindingReqs, &binding);
   *             fsPreamble += " sampler2D dstSampler;";
   *             // Add default SamplerDesc for the intrinsic dstSampler to stay consistent with
   *             // `fNumFragmentTexturesAndSamplers`.
   *             if (outDescs) {
   *                 outDescs->push_back({});
   *             }
   *         }
   *
   *         // Record how many textures and samplers are used.
   *         fNumFragmentTexturesAndSamplers = binding;
   *     }
   *
   *     // Emit preamble declarations and helper functions required for snippets. In the default case
   *     // this adds functions that bind a node's specific mangled uniforms to the snippet's
   *     // implementation in the SkSL modules.
   *     emit_preambles(*this, sharedData.fRootNodes, /*treeLabel=*/"", &fsPreamble);
   *
   *     std::string mainBody = "void main() {";
   *
   *     if (sharedData.fHasSsboIndexVarying) {
   *         SkSL::String::appendf(&mainBody,
   *                               "%s = %s;\n",
   *                               this->uniformSsboIndex(),
   *                               RenderStep::ssboIndexVarying());
   *     }
   *
   *     if (step->emitsPrimitiveColor()) {
   *         mainBody += "half4 primitiveColor;";
   *         mainBody += step->fragmentColorSkSL();
   *     } else {
   *         SkASSERT(!(sharedData.fRootNodes[0]->requiredFlags() &
   *                  SnippetRequirementFlags::kPrimitiveColor));
   *     }
   *
   *     // Using kDefaultArgs as the initial value means it will refer to undefined variables, but the
   *     // root nodes should--at most--be depending on the coordinate when "needsLocalCoords" is true.
   *     // If the PaintParamsKey violates that structure, this will produce SkSL compile errors.
   *     ShaderSnippet::Args args = ShaderSnippet::kDefaultArgs;
   *     args.fFragCoord = "localCoordsVar";  // the varying added in emit_varyings()
   *     // TODO(b/349997190): The paint root node should not depend on any prior stage's output, but
   *     // it can happen with how SkEmptyShader is currently mapped to `sk_passthrough`. In this case
   *     // it requires that prior stage color to be transparent black. When SkEmptyShader can instead
   *     // cause the draw to be skipped, this can go away.
   *     args.fPriorStageOutput = "half4(0)";
   *
   *     // Calculate the src color and stash its output variable in `args`
   *     args.fPriorStageOutput = srcColorRoot->invokeAndAssign(*this, args, &mainBody);
   *
   *     // If not using hardware blending, we perform a dst read in the shader and must add SkSL
   *     // accordingly.
   *     if (fDstReadStrategy != DstReadStrategy::kNoneRequired) {
   *         // Get the current dst color into a local variable, it may be used later on for coverage
   *         // blending as well as the final blend.
   *         mainBody += "half4 dstColor;";
   *         if (useDstSampler) {
   *             // dstReadBounds is in frag coords and already includes the replay translation. The
   *             // reciprocol of the dstCopy dimensions are in ZW.
   *             mainBody += "dstColor = sample(dstSampler,"
   *                                           "dstReadBounds.zw*(sk_FragCoord.xy - dstReadBounds.xy));";
   *         } else if (fDstReadStrategy == DstReadStrategy::kReadFromInput) {
   *             // The dst texture should have been written to with the appropriate write swizzle, so we
   *             // do not need to worry about the read swizzle when accessing that value for blending.
   *             mainBody += "// Read color from input attachment\n";
   *             mainBody += "dstColor = subpassLoad(DstTextureInput);\n";
   *         } else {
   *             SkASSERT(fDstReadStrategy == DstReadStrategy::kFramebufferFetch);
   *             mainBody += "dstColor = sk_LastFragColor;";
   *         }
   *
   *         args.fBlenderDstColor = "dstColor";
   *         args.fPriorStageOutput = finalBlendRoot->invokeAndAssign(*this, args, &mainBody);
   *     }
   *
   *     if (writeSwizzle != Swizzle::RGBA()) {
   *         SkSL::String::appendf(&mainBody, "%s = %s.%s;", args.fPriorStageOutput.c_str(),
   *                                                         args.fPriorStageOutput.c_str(),
   *                                                         writeSwizzle.asString().c_str());
   *     }
   *
   *     if (finalCoverage == Coverage::kNone) {
   *         // Either direct HW blending or a dst-read w/o any extra coverage. In both cases we just
   *         // need to assign directly to sk_FragCoord and update the HW blend info to finalBlendMode.
   *         SkASSERT(finalBlendMode.has_value());
   *         fBlendInfo = gBlendTable[static_cast<int>(*finalBlendMode)];
   *         SkSL::String::appendf(&mainBody, "sk_FragColor = %s;", args.fPriorStageOutput.c_str());
   *     } else {
   *         // Accumulate the output coverage. This will either modify the src color and secondary
   *         // outputs for dual-source blending, or be combined directly with the in-shader blended
   *         // final color if a dst-readback was required.
   *
   *         if (sharedData.fUseUniformStorageBufferFS && sharedData.fHasStepUniforms) {
   *             mainBody +=
   *                     emit_uniforms_from_storage_buffer(this->uniformSsboIndex(), step->uniforms());
   *         }
   *
   *         mainBody += "half4 outputCoverage = half4(1);";
   *         if (step->coverage() != Coverage::kNone) {
   *             mainBody += step->fragmentCoverageSkSL();
   *         }
   *
   *         if (clipRoot) {
   *             // The clip block node is invoked with device coords, not local coords like the main
   *             // shading root node. However sk_FragCoord includes any replay translation and we
   *             // need to recover the original device coordinate.
   *             mainBody += "float2 devCoord = sk_FragCoord.xy - viewport.xy;";
   *             args.fFragCoord = "devCoord";
   *             std::string clipBlockOutput = clipRoot->invokeAndAssign(*this, args, &mainBody);
   *             SkSL::String::appendf(&mainBody, "outputCoverage *= %s.a;", clipBlockOutput.c_str());
   *         }
   *
   *         const char* outColor = args.fPriorStageOutput.c_str();
   *         if (fDstReadStrategy != DstReadStrategy::kNoneRequired) {
   *             // If this draw uses a non-coherent dst read, we want to keep the existing dst color (or
   *             // whatever has been previously drawn) when there's no coverage. This helps for batching
   *             // text draws that need to read from a dst copy for blends. However, this only helps the
   *             // case where the outer bounding boxes of each letter overlap and not two actual parts
   *             // of the text.
   *             if (useDstSampler) {
   *                 // We don't think any shaders actually output negative coverage, but just as a
   *                 // safety check for floating point precision errors, we compare with <= here. We
   *                 // just check the RGB values of the coverage, since the alpha may not have been set
   *                 // when using LCD. If we are using single-channel coverage, alpha will be equal to
   *                 // RGB anyway.
   *                 mainBody +=
   *                     "if (all(lessThanEqual(outputCoverage.rgb, half3(0)))) {"
   *                         "discard;"
   *                     "}";
   *             }
   *
   *             // Use kSrc HW BlendInfo and do the coverage blend with dst in the shader.
   *             SkASSERT(finalBlendMode.has_value() && finalBlendMode.value() == SkBlendMode::kSrc);
   *             fBlendInfo = gBlendTable[static_cast<int>(*finalBlendMode)];
   *             SkSL::String::appendf(
   *                     &mainBody,
   *                     "sk_FragColor = %s * outputCoverage + dstColor * (1.0 - outputCoverage);",
   *                     outColor);
   *             if (finalCoverage == Coverage::kLCD) {
   *                 SkSL::String::appendf(
   *                         &mainBody,
   *                         "half3 lerpRGB = mix(dstColor.aaa, %s.aaa, outputCoverage.rgb);"
   *                         "sk_FragColor.a = max(max(lerpRGB.r, lerpRGB.g), lerpRGB.b);",
   *                         outColor);
   *             }
   *         } else {
   *             // Adjust the shader output(s) to incorporate the coverage so that HW blending produces
   *             // the correct output.
   *             if (finalBlendMode > SkBlendMode::kLastCoeffMode) {
   *                 SkASSERT(finalCoverage == Coverage::kSingleChannel);
   *                 fBlendInfo = gBlendTable[static_cast<int>(*finalBlendMode)];
   *                 mainBody += emit_advanced_blend_color_output("sk_FragColor", outColor);
   *             } else {
   *                 // Porter-Duff blend modes can utilize BlendFormula.
   *                 // TODO: Determine whether draw is opaque and pass that to GetBlendFormula.
   *                 BlendFormula coverageBlendFormula =
   *                         finalCoverage == Coverage::kLCD
   *                                 ? skgpu::GetLCDBlendFormula(*finalBlendMode)
   *                                 : skgpu::GetBlendFormula(
   *                                         /*isOpaque=*/false, /*hasCoverage=*/true, *finalBlendMode);
   *                 fBlendInfo = {coverageBlendFormula.equation(),
   *                               coverageBlendFormula.srcCoeff(),
   *                               coverageBlendFormula.dstCoeff(),
   *                               SK_PMColor4fTRANSPARENT,
   *                               coverageBlendFormula.modifiesDst()};
   *
   *                 if (finalCoverage == Coverage::kLCD) {
   *                     mainBody += "outputCoverage.a = max(max(outputCoverage.r, "
   *                                                            "outputCoverage.g), "
   *                                                    "outputCoverage.b);";
   *                 }
   *
   *                 mainBody += emit_color_output(coverageBlendFormula.primaryOutput(),
   *                                               "sk_FragColor",
   *                                               outColor);
   *                 if (coverageBlendFormula.hasSecondaryOutput()) {
   *                     SkASSERT(caps->shaderCaps()->fDualSourceBlendingSupport);
   *                     mainBody += emit_color_output(coverageBlendFormula.secondaryOutput(),
   *                                                   "sk_SecondaryFragColor",
   *                                                   outColor);
   *                 }
   *             }
   *         }
   *     }
   *     mainBody += "}\n";
   *
   *     SkASSERT(fFragmentSkSL.empty());
   *     fFragmentSkSL.reserve(
   *             sharedData.fSharedPreamble.size() + fsPreamble.size() + mainBody.size() +2);
   *     fFragmentSkSL  = sharedData.fSharedPreamble;
   *     fFragmentSkSL += "\n";
   *     fFragmentSkSL += fsPreamble;
   *     fFragmentSkSL += "\n";
   *     fFragmentSkSL += mainBody;
   * }
   * ```
   */
  private fun generateFragmentSkSL(
    caps: Caps?,
    dict: ShaderCodeDictionary?,
    label: String?,
    step: RenderStep?,
    paintID: UniquePaintParamsID,
    targetFormat: TextureFormat,
    writeSwizzle: Swizzle,
    outDescs: TArray<SamplerDesc>?,
    sharedData: SharedGeneratorData,
  ) {
    TODO("Implement generateFragmentSkSL")
  }

  /**
   * C++ original:
   * ```cpp
   * void ShaderInfo::generateVertexSkSL(const Caps* caps,
   *                                     const RenderStep* step,
   *                                     const SharedGeneratorData& sharedData) {
   *     std::string vsPreamble;
   *     // Fixed program header (intrinsics are always declared as an uniform interface block)
   *     const ResourceBindingRequirements& bindingReqs = caps->resourceBindingRequirements();
   *     vsPreamble = emit_intrinsic_constants(bindingReqs);
   *     // Varyings needed by RenderStep and potentially lifted expressions
   *     vsPreamble += emit_varyings(step, "out",
   *                                 sharedData.fLiftedExpr,
   *                                 sharedData.fHasSsboIndexVarying,
   *                                 sharedData.fNeedsLocalCoords);
   *
   *     // Add vertex attributes
   *     if (step->numStaticAttributes() > 0 || step->numAppendAttributes() > 0) {
   *         int attr = 0;
   *         auto add_attrs = [&vsPreamble, &attr](SkSpan<const Attribute> attrs) {
   *             for (auto a : attrs) {
   *                 SkSL::String::appendf(&vsPreamble, "    layout(location=%d) in ", attr++);
   *                 vsPreamble.append(SkSLTypeString(a.gpuType()));
   *                 SkSL::String::appendf(&vsPreamble, " %s;\n", a.name());
   *             }
   *         };
   *         if (step->numStaticAttributes() > 0) {
   * #if defined(SK_DEBUG)
   *             vsPreamble.append("// static attrs\n");
   * #endif
   *             add_attrs(step->staticAttributes());
   *         }
   *         if (step->numAppendAttributes() > 0) {
   * #if defined(SK_DEBUG)
   *             vsPreamble.append("// append attrs\n");
   * #endif
   *             add_attrs(step->appendAttributes());
   *         }
   *     }
   *
   *     // Vertex shader function declaration
   *     std::string mainBody = "void main() {";
   *     // Create stepLocalCoords which render steps can write to.
   *     mainBody += "float2 stepLocalCoords = float2(0);";
   *
   *     // We define the SSBO index variable immediately if the VS is using storage buffers. This covers
   *     // both the "Step Uniforms" case and the "Lifted Uniforms Only" case.
   *     if (sharedData.fUseUniformStorageBufferVS) {
   *         SkSL::String::appendf(&mainBody, "uint %s = %s;\n", this->uniformSsboIndex(),
   *                                   RenderStep::ssboIndexAttribute());
   *         if (sharedData.fHasStepUniforms) {
   *             mainBody +=
   *                     emit_uniforms_from_storage_buffer(this->uniformSsboIndex(), step->uniforms());
   *         }
   *     }
   *
   *     // Inject RenderStep's main vertex logic
   *     mainBody += step->vertexSkSL();
   *
   *     // Calculate sk_Position
   *     mainBody +=
   *             "sk_Position = float4(viewport.zw*devPosition.xy - sign(viewport.zw)*devPosition.ww,"
   *             "devPosition.zw);";
   *
   *     // Assign local coords to varying if needed
   *     if (sharedData.fNeedsLocalCoords) {
   *         mainBody += "localCoordsVar = stepLocalCoords;";
   *     }
   *
   *     // Generate lifted expressions
   *     if (!sharedData.fLiftedExpr.empty()) {
   *         for (const LiftedExpression& expr : sharedData.fLiftedExpr) {
   *             const ShaderNode* node = expr.fNode;
   *             // Determine the SkSL type string if not emitting directly to a varying
   *             const char* typeStr = expr.fEmitVarying
   *                                           ? ""
   *                                           : SkSLTypeString(sksl_type_for_lifted_expression(
   *                                                     node->entry()->fLiftableExpressionType));
   *             const std::string varName = node->getExpressionVaryingName();
   *
   *             // Generate the expression code, potentially extracting uniforms from SSBO if needed
   *             std::string expression;
   *             expression = node->entry()->fLiftableExpressionGenerator(*this, node, expr.fArgs);
   *
   *             // Assign the expression result to the varying or a temporary variable
   *             SkSL::String::appendf(
   *                     &mainBody, "%s %s = %s;", typeStr, varName.c_str(), expression.c_str());
   *         }
   *     }
   *
   *     // Assign SSBO index to varying if needed
   *     if (sharedData.fHasSsboIndexVarying) {
   *         if (sharedData.fUseUniformStorageBufferVS) {
   *             // Use the local variable we already defined
   *             SkSL::String::appendf(&mainBody,
   *                                   "%s = %s;",
   *                                   RenderStep::ssboIndexVarying(),
   *                                   this->uniformSsboIndex());
   *         } else {
   *             // No local variable, read directly from attribute
   *             SkSL::String::appendf(&mainBody,
   *                                   "%s = %s;",
   *                                   RenderStep::ssboIndexVarying(),
   *                                   RenderStep::ssboIndexAttribute());
   *         }
   *     }
   *
   *     mainBody += "}"; // End main()
   *
   *     SkASSERT(fVertexSkSL.empty());
   *     fVertexSkSL.reserve(
   *             sharedData.fSharedPreamble.size() + vsPreamble.size() + mainBody.size() + 2);
   *     fVertexSkSL = sharedData.fSharedPreamble;
   *     fVertexSkSL += "\n";
   *     fVertexSkSL += vsPreamble;
   *     fVertexSkSL += "\n";
   *     fVertexSkSL += mainBody;
   * }
   * ```
   */
  private fun generateVertexSkSL(
    caps: Caps?,
    step: RenderStep?,
    sharedData: SharedGeneratorData,
  ) {
    TODO("Implement generateVertexSkSL")
  }

  public companion object {
    public val kGradientBufferName: CharArray = TODO("Initialize kGradientBufferName")

    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<ShaderInfo> ShaderInfo::Make(const Caps* caps,
     *                                              const ShaderCodeDictionary* dict,
     *                                              const RuntimeEffectDictionary* rteDict,
     *                                              const RenderPassDesc& rpDesc,
     *                                              const RenderStep* step,
     *                                              UniquePaintParamsID paintID,
     *                                              skia_private::TArray<SamplerDesc>* outDescs) {
     *     // Determine if an SSBO index is needed at all (by either stage)
     *     bool needsSsboIndex =
     *         caps->storageBufferSupport() && (step->performsShading() || step->numUniforms() > 0);
     *     const char* uniformSsboIndex = needsSsboIndex ? "uniformSsboIndex" : nullptr;
     *
     *     const bool hasFragShader = paintID.isValid() && step->performsShading();
     *
     *     // Create the final ShaderInfo object.
     *     auto result = std::unique_ptr<ShaderInfo>(new ShaderInfo(dict, rteDict, uniformSsboIndex,
     *                                               hasFragShader ? rpDesc.fDstReadStrategy
     *                                                             : DstReadStrategy::kNoneRequired));
     *
     *     // This arena holds all the ShaderNodes. It must live for the duration of 'Make' so the
     *     // rootNodes span is valid when passed to helpers.
     *     SkArenaAlloc shaderNodeAlloc{256};
     *     SharedGeneratorData sharedData(
     *             caps, dict, &shaderNodeAlloc, step, paintID, result->uniformSsboIndex());
     *     result->fHasCombinedUniforms = sharedData.fHasStepUniforms || sharedData.fHasPaintUniforms;
     *
     *     SkString paintLabel = dict->idToString(caps, paintID);
     *     if (hasFragShader) {
     *         result->generateFragmentSkSL(caps,
     *                                      dict,
     *                                      paintLabel.c_str(),
     *                                      step,
     *                                      paintID,
     *                                      rpDesc.fColorAttachment.fFormat,
     *                                      rpDesc.fWriteSwizzle,
     *                                      outDescs,
     *                                      sharedData);
     *     } else {
     *         result->fBlendInfo.fWritesColor = false;
     *     }
     *
     *     result->generateVertexSkSL(caps, step, sharedData);
     *     result->fVSLabel = step->name();
     *     if (sharedData.fNeedsLocalCoords) {
     *         result->fVSLabel += " (w/ local coords)";
     *     }
     *
     *     result->fFSLabel = step->name();
     *     result->fFSLabel += " + ";
     *     result->fFSLabel += paintLabel.c_str();
     *     if (rpDesc.fWriteSwizzle != Swizzle::RGBA() ||
     *         result->fDstReadStrategy != DstReadStrategy::kNoneRequired) {
     *         result->fFSLabel += "(";
     *         result->fFSLabel += rpDesc.fWriteSwizzle.asString().c_str();
     *         if (result->fDstReadStrategy != DstReadStrategy::kNoneRequired) {
     *             result->fFSLabel += ", ";
     *             result->fFSLabel += dst_read_strategy_to_str(result->fDstReadStrategy);
     *         }
     *         result->fFSLabel += ")";
     *     }
     *
     *     // KEEP IN SYNC with ContextUtils::GetPipelineLabel()
     *     result->fPipelineLabel = rpDesc.toPipelineLabel().c_str();
     *     result->fPipelineLabel += " + ";
     *     result->fPipelineLabel += step->name();
     *     result->fPipelineLabel += " + ";
     *     result->fPipelineLabel += paintLabel.c_str();
     *
     *     return result;
     * }
     * ```
     */
    public fun make(
      caps: Caps?,
      dict: ShaderCodeDictionary?,
      rteDict: RuntimeEffectDictionary?,
      rpDesc: RenderPassDesc,
      step: RenderStep?,
      paintID: UniquePaintParamsID,
      outDescs: TArray<SamplerDesc>? = TODO(),
    ): Int {
      TODO("Implement make")
    }
  }
}
