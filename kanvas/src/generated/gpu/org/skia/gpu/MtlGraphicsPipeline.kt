package org.skia.gpu

import MSLFunction
import MTLVertexStepFunction
import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.skia.core.SkEnumBitMask
import org.skia.foundation.SkSpan
import PipelineInfo as PipelineInfo_
import undefined.PipelineInfo as UndefinedPipelineInfo

/**
 * C++ original:
 * ```cpp
 * class MtlGraphicsPipeline final : public GraphicsPipeline {
 * public:
 *     inline static constexpr unsigned int kIntrinsicUniformBufferIndex = 0;
 *     inline static constexpr unsigned int kCombinedUniformIndex = 1; // Paint AND rendersteps!
 *     inline static constexpr unsigned int kStaticDataBufferIndex = 2;
 *     inline static constexpr unsigned int kAppendDataBufferIndex = 3;
 *     inline static constexpr unsigned int kGradientBufferIndex = 4;
 *
 *     static sk_sp<MtlGraphicsPipeline> Make(const MtlSharedContext*,
 *                                            const RuntimeEffectDictionary*,
 *                                            const UniqueKey&,
 *                                            const GraphicsPipelineDesc&,
 *                                            const RenderPassDesc&,
 *                                            SkEnumBitMask<PipelineCreationFlags>,
 *                                            uint32_t compilationID);
 *
 *     static sk_sp<MtlGraphicsPipeline> MakeLoadMSAAPipeline(const MtlSharedContext*,
 *                                                            const RenderPassDesc&);
 *
 *     ~MtlGraphicsPipeline() override {}
 *
 *     id<MTLRenderPipelineState> mtlPipelineState() const { return fPipelineState.get(); }
 *     id<MTLDepthStencilState> mtlDepthStencilState() const { return fDepthStencilState.get(); }
 *     uint32_t stencilReferenceValue() const { return fStencilReferenceValue; }
 *
 * private:
 *     MtlGraphicsPipeline(const skgpu::graphite::SharedContext* sharedContext,
 *                         const PipelineInfo& pipelineInfo,
 *                         std::string_view pipelineLabel,
 *                         sk_cfp<id<MTLRenderPipelineState>> pso,
 *                         sk_cfp<id<MTLDepthStencilState>> dss,
 *                         uint32_t refValue);
 *
 *     using MSLFunction = std::pair<id<MTLLibrary>, std::string>;
 *     static sk_sp<MtlGraphicsPipeline> Make(const MtlSharedContext*,
 *                                            const std::string& label,
 *                                            const PipelineInfo&,
 *                                            MSLFunction vertexMain,
 *                                            MTLVertexStepFunction appendStepFunc,
 *                                            SkSpan<const Attribute> staticAttrs,
 *                                            SkSpan<const Attribute> appendAttrs,
 *                                            MSLFunction fragmentMain,
 *                                            sk_cfp<id<MTLDepthStencilState>>,
 *                                            uint32_t stencilRefValue,
 *                                            const BlendInfo& blendInfo,
 *                                            const RenderPassDesc&);
 *
 *     void freeGpuData() override;
 *
 *     sk_cfp<id<MTLRenderPipelineState>> fPipelineState;
 *     sk_cfp<id<MTLDepthStencilState>> fDepthStencilState;
 *     uint32_t fStencilReferenceValue;
 * }
 * ```
 */
public class MtlGraphicsPipeline public constructor(
  sharedContext: SharedContext?,
  pipelineInfo: UndefinedPipelineInfo,
  pipelineLabel: String,
  refValue: UInt,
) : GraphicsPipeline() {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr unsigned int kIntrinsicUniformBufferIndex = 0
   * ```
   */
  private var fPipelineState: Int = TODO("Initialize fPipelineState")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr unsigned int kCombinedUniformIndex = 1
   * ```
   */
  private var fDepthStencilState: Int = TODO("Initialize fDepthStencilState")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr unsigned int kStaticDataBufferIndex = 2
   * ```
   */
  private var fStencilReferenceValue: Int = TODO("Initialize fStencilReferenceValue")

  /**
   * C++ original:
   * ```cpp
   * id<MTLRenderPipelineState> mtlPipelineState() const { return fPipelineState.get(); }
   * ```
   */
  public fun mtlPipelineState(): Int {
    TODO("Implement mtlPipelineState")
  }

  /**
   * C++ original:
   * ```cpp
   * id<MTLDepthStencilState> mtlDepthStencilState() const { return fDepthStencilState.get(); }
   * ```
   */
  public fun mtlDepthStencilState(): Int {
    TODO("Implement mtlDepthStencilState")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t stencilReferenceValue() const { return fStencilReferenceValue; }
   * ```
   */
  public fun stencilReferenceValue(): Int {
    TODO("Implement stencilReferenceValue")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlGraphicsPipeline::freeGpuData() {
   *     fPipelineState.reset();
   * }
   * ```
   */
  public override fun freeGpuData() {
    TODO("Implement freeGpuData")
  }

  public companion object {
    public val kIntrinsicUniformBufferIndex: UInt = TODO("Initialize kIntrinsicUniformBufferIndex")

    public val kCombinedUniformIndex: UInt = TODO("Initialize kCombinedUniformIndex")

    public val kStaticDataBufferIndex: UInt = TODO("Initialize kStaticDataBufferIndex")

    public val kAppendDataBufferIndex: UInt = TODO("Initialize kAppendDataBufferIndex")

    public val kGradientBufferIndex: UInt = TODO("Initialize kGradientBufferIndex")

    /**
     * C++ original:
     * ```cpp
     * sk_sp<MtlGraphicsPipeline> MtlGraphicsPipeline::Make(
     *         const MtlSharedContext* sharedContext,
     *         const RuntimeEffectDictionary* runtimeDict,
     *         const UniqueKey& pipelineKey,
     *         const GraphicsPipelineDesc& pipelineDesc,
     *         const RenderPassDesc& renderPassDesc,
     *         SkEnumBitMask<PipelineCreationFlags> pipelineCreationFlags,
     *         uint32_t compilationID) {
     *     SkSL::NativeShader vsMSL, fsMSL;
     *     SkSL::Program::Interface vsInterface, fsInterface;
     *
     *     SkSL::ProgramSettings settings;
     *     settings.fSharpenTextures = true;
     *     settings.fForceNoRTFlip = true;
     *
     *     SkSL::Compiler skslCompiler;
     *     ShaderErrorHandler* errorHandler = sharedContext->caps()->shaderErrorHandler();
     *
     *     const RenderStep* step = sharedContext->rendererProvider()->lookup(pipelineDesc.renderStepID());
     *
     *     UniquePaintParamsID paintID = pipelineDesc.paintParamsID();
     *
     *     std::unique_ptr<ShaderInfo> shaderInfo =
     *             ShaderInfo::Make(sharedContext->caps(),
     *                              sharedContext->shaderCodeDictionary(),
     *                              runtimeDict,
     *                              renderPassDesc,
     *                              step,
     *                              paintID);
     *
     *     const std::string& fsSkSL = shaderInfo->fragmentSkSL();
     *     const BlendInfo& blendInfo = shaderInfo->blendInfo();
     *     if (!SkSLToMSL(sharedContext->caps()->shaderCaps(),
     *                    fsSkSL,
     *                    SkSL::ProgramKind::kGraphiteFragment,
     *                    settings,
     *                    &fsMSL,
     *                    &fsInterface,
     *                    errorHandler)) {
     *         return nullptr;
     *     }
     *
     *     const std::string& vsSkSL = shaderInfo->vertexSkSL();
     *     if (!SkSLToMSL(sharedContext->caps()->shaderCaps(),
     *                    vsSkSL,
     *                    SkSL::ProgramKind::kGraphiteVertex,
     *                    settings,
     *                    &vsMSL,
     *                    &vsInterface,
     *                    errorHandler)) {
     *         return nullptr;
     *     }
     *
     *     auto vsLibrary = MtlCompileShaderLibrary(
     *             sharedContext, shaderInfo->vsLabel(), vsMSL.fText, errorHandler);
     *     auto fsLibrary = MtlCompileShaderLibrary(
     *             sharedContext, shaderInfo->fsLabel(), fsMSL.fText, errorHandler);
     *
     *     sk_cfp<id<MTLDepthStencilState>> dss =
     *             sharedContext->getCompatibleDepthStencilState(step->depthStencilSettings());
     *
     *     PipelineInfo pipelineInfo{ *shaderInfo, pipelineCreationFlags,
     *                                pipelineKey.hash(), compilationID };
     * #if defined(GPU_TEST_UTILS)
     *     pipelineInfo.fNativeVertexShader = std::move(vsMSL.fText);
     *     pipelineInfo.fNativeFragmentShader = std::move(fsMSL.fText);
     * #endif
     *
     *    return Make(sharedContext,
     *                shaderInfo->pipelineLabel(),
     *                pipelineInfo,
     *                {vsLibrary.get(), "vertexMain"},
     *                step->appendsVertices() ? MTLVertexStepFunctionPerVertex :
     *                                          MTLVertexStepFunctionPerInstance,
     *                step->staticAttributes(),
     *                step->appendAttributes(),
     *                {fsLibrary.get(), "fragmentMain"},
     *                std::move(dss),
     *                step->depthStencilSettings().fStencilReferenceValue,
     *                blendInfo,
     *                renderPassDesc);
     * }
     * ```
     */
    public fun make(
      sharedContext: MtlSharedContext?,
      runtimeDict: RuntimeEffectDictionary?,
      pipelineKey: UniqueKey,
      pipelineDesc: GraphicsPipelineDesc,
      renderPassDesc: RenderPassDesc,
      pipelineCreationFlags: SkEnumBitMask<PipelineCreationFlags>,
      compilationID: UInt,
    ): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<MtlGraphicsPipeline> MtlGraphicsPipeline::MakeLoadMSAAPipeline(
     *         const MtlSharedContext* sharedContext,
     *         const RenderPassDesc& renderPassDesc) {
     *     static const char* kLoadMSAAShaderText =
     *             "#include <metal_stdlib>\n"
     *             "#include <simd/simd.h>\n"
     *             "using namespace metal;"
     *
     *             "typedef struct {"
     *                 "float4 position [[position]];"
     *             "} VertexOutput;"
     *
     *             "vertex VertexOutput vertexMain(uint vertexID [[vertex_id]]) {"
     *                 "VertexOutput out;"
     *                 "float2 position = float2(float(vertexID >> 1), float(vertexID & 1));"
     *                 "out.position = float4(2.0 * position - 1.0, 0.0, 1.0);"
     *                 "return out;"
     *             "}"
     *
     *             "fragment float4 fragmentMain(VertexOutput in [[stage_in]],"
     *                                             "texture2d<half> colorMap [[texture(0)]]) {"
     *                 "uint2 coords = uint2(in.position.x, in.position.y);"
     *                 "half4 colorSample   = colorMap.read(coords);"
     *                 "return float4(colorSample);"
     *             "}";
     *
     *     auto mtlLibrary = MtlCompileShaderLibrary(sharedContext,
     *                                               "LoadMSAAFromResolve",
     *                                               kLoadMSAAShaderText,
     *                                               sharedContext->caps()->shaderErrorHandler());
     *     BlendInfo noBlend{}; // default is equivalent to kSrc blending
     *
     *     static constexpr DepthStencilSettings kIgnoreDSS;
     *     sk_cfp<id<MTLDepthStencilState>> ignoreDS =
     *             sharedContext->getCompatibleDepthStencilState(kIgnoreDSS);
     *
     *     std::string pipelineLabel = "LoadMSAAFromResolve + ";
     *     pipelineLabel += renderPassDesc.toString().c_str();
     *
     *     PipelineInfo pipelineInfo;
     *     pipelineInfo.fNumFragTexturesAndSamplers = 1;
     *     // This is an internal shader, leave off filling out the test-utils shader code
     *     return Make(sharedContext,
     *                 pipelineLabel,
     *                 pipelineInfo,
     *                 {mtlLibrary.get(), "vertexMain"},
     *                 /*appendStepFunc=*/{},
     *                 /*staticAttrs=*/{},
     *                 /*appendAttrs=*/{},
     *                 {mtlLibrary.get(), "fragmentMain"},
     *                 std::move(ignoreDS),
     *                 /*stencilRefValue=*/0,
     *                 noBlend,
     *                 renderPassDesc);
     * }
     * ```
     */
    public fun makeLoadMSAAPipeline(sharedContext: MtlSharedContext?, renderPassDesc: RenderPassDesc): Int {
      TODO("Implement makeLoadMSAAPipeline")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<MtlGraphicsPipeline> MtlGraphicsPipeline::Make(const MtlSharedContext* sharedContext,
     *                                                      const std::string& label,
     *                                                      const PipelineInfo& pipelineInfo,
     *                                                      MSLFunction vertexMain,
     *                                                      MTLVertexStepFunction appendStepFunc,
     *                                                      SkSpan<const Attribute> staticAttrs,
     *                                                      SkSpan<const Attribute> appendAttrs,
     *                                                      MSLFunction fragmentMain,
     *                                                      sk_cfp<id<MTLDepthStencilState>> dss,
     *                                                      uint32_t stencilRefValue,
     *                                                      const BlendInfo& blendInfo,
     *                                                      const RenderPassDesc& renderPassDesc) {
     *     id<MTLLibrary> vsLibrary = std::get<0>(vertexMain);
     *     id<MTLLibrary> fsLibrary = std::get<0>(fragmentMain);
     *     if (!vsLibrary || !fsLibrary) {
     *         return nullptr;
     *     }
     *
     *     sk_cfp<MTLRenderPipelineDescriptor*> psoDescriptor([[MTLRenderPipelineDescriptor alloc] init]);
     *
     *     NSString* labelName =  [NSString stringWithUTF8String: label.c_str()];
     *     NSString* vsFuncName = [NSString stringWithUTF8String: std::get<1>(vertexMain).c_str()];
     *     NSString* fsFuncName = [NSString stringWithUTF8String: std::get<1>(fragmentMain).c_str()];
     *
     *     (*psoDescriptor).label = labelName;
     *     (*psoDescriptor).vertexFunction = [vsLibrary newFunctionWithName: vsFuncName];
     *     (*psoDescriptor).fragmentFunction = [fsLibrary newFunctionWithName: fsFuncName];
     *
     *     // TODO: I *think* this gets cleaned up by the pipelineDescriptor?
     *     (*psoDescriptor).vertexDescriptor = create_vertex_descriptor(appendStepFunc,
     *                                                                  staticAttrs,
     *                                                                  appendAttrs);
     *
     *     TextureFormat colorFormat = renderPassDesc.fColorAttachment.fFormat;
     *     TextureFormat dsFormat = renderPassDesc.fDepthStencilAttachment.fFormat;
     *
     *     auto mtlColorAttachment =
     *             create_color_attachment(TextureFormatToMTLPixelFormat(colorFormat), blendInfo);
     *     (*psoDescriptor).colorAttachments[0] = mtlColorAttachment;
     *
     *     (*psoDescriptor).rasterSampleCount = (uint8_t) renderPassDesc.fColorAttachment.fSampleCount;
     *
     *     if (TextureFormatHasStencil(dsFormat)) {
     *         (*psoDescriptor).stencilAttachmentPixelFormat = TextureFormatToMTLPixelFormat(dsFormat);
     *     } else {
     *         (*psoDescriptor).stencilAttachmentPixelFormat = MTLPixelFormatInvalid;
     *     }
     *     if (TextureFormatHasDepth(dsFormat)) {
     *         (*psoDescriptor).depthAttachmentPixelFormat = TextureFormatToMTLPixelFormat(dsFormat);
     *     } else {
     *         (*psoDescriptor).depthAttachmentPixelFormat = MTLPixelFormatInvalid;
     *     }
     *
     *     NSError* error;
     *     sk_cfp<id<MTLRenderPipelineState>> pso(
     *             [sharedContext->device() newRenderPipelineStateWithDescriptor:psoDescriptor.get()
     *                                                                     error:&error]);
     *     if (!pso) {
     *         SKGPU_LOG_E("Render pipeline creation failure:\n%s", error.debugDescription.UTF8String);
     *         return nullptr;
     *     }
     *
     *     return sk_sp<MtlGraphicsPipeline>(new MtlGraphicsPipeline(sharedContext,
     *                                                               pipelineInfo,
     *                                                               label,
     *                                                               std::move(pso),
     *                                                               std::move(dss),
     *                                                               stencilRefValue));
     * }
     * ```
     */
    public fun make(
      sharedContext: MtlSharedContext?,
      label: String,
      pipelineInfo: PipelineInfo_,
      vertexMain: MSLFunction,
      appendStepFunc: MTLVertexStepFunction,
      staticAttrs: SkSpan<Attribute>,
      appendAttrs: SkSpan<Attribute>,
      fragmentMain: MSLFunction,
      stencilRefValue: UInt,
      blendInfo: BlendInfo,
      renderPassDesc: RenderPassDesc,
    ): Int {
      TODO("Implement make")
    }
  }
}
