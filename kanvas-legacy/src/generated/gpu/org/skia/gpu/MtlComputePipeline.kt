package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class MtlComputePipeline final : public ComputePipeline {
 * public:
 *     static sk_sp<MtlComputePipeline> Make(const MtlSharedContext*, const ComputePipelineDesc&);
 *
 *     ~MtlComputePipeline() override = default;
 *
 *     id<MTLComputePipelineState> mtlPipelineState() const { return fPipelineState.get(); }
 *
 * private:
 *     MtlComputePipeline(const SharedContext* sharedContext, sk_cfp<id<MTLComputePipelineState>> pso)
 *             : ComputePipeline(sharedContext)
 *             , fPipelineState(std::move(pso)) {}
 *
 *     void freeGpuData() override;
 *
 *     sk_cfp<id<MTLComputePipelineState>> fPipelineState;
 * }
 * ```
 */
public class MtlComputePipeline public constructor(
  sharedContext: SharedContext?,
) : ComputePipeline(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_cfp<id<MTLComputePipelineState>> fPipelineState
   * ```
   */
  private var fPipelineState: Int = TODO("Initialize fPipelineState")

  /**
   * C++ original:
   * ```cpp
   * id<MTLComputePipelineState> mtlPipelineState() const { return fPipelineState.get(); }
   * ```
   */
  public fun mtlPipelineState(): Int {
    TODO("Implement mtlPipelineState")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlComputePipeline::freeGpuData() { fPipelineState.reset(); }
   * ```
   */
  public override fun freeGpuData() {
    TODO("Implement freeGpuData")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<MtlComputePipeline> MtlComputePipeline::Make(const MtlSharedContext* sharedContext,
     *                                                    const ComputePipelineDesc& pipelineDesc) {
     *     sk_cfp<id<MTLLibrary>> library;
     *     std::string entryPointName;
     *     ShaderErrorHandler* errorHandler = sharedContext->caps()->shaderErrorHandler();
     *     if (pipelineDesc.computeStep()->supportsNativeShader()) {
     *         auto nativeShader = pipelineDesc.computeStep()->nativeShaderSource(
     *                 ComputeStep::NativeShaderFormat::kMSL);
     *         library = MtlCompileShaderLibrary(sharedContext,
     *                                           pipelineDesc.computeStep()->name(),
     *                                           nativeShader.fSource,
     *                                           errorHandler);
     *         if (library == nil) {
     *             return nullptr;
     *         }
     *         entryPointName = std::move(nativeShader.fEntryPoint);
     *     } else {
     *         SkSL::NativeShader msl;
     *         SkSL::Program::Interface interface;
     *         SkSL::ProgramSettings settings;
     *
     *         SkSL::Compiler skslCompiler;
     *         std::string sksl = BuildComputeSkSL(sharedContext->caps(),
     *                                             pipelineDesc.computeStep(),
     *                                             BackendApi::kMetal);
     *         if (!SkSLToMSL(sharedContext->caps()->shaderCaps(),
     *                        sksl,
     *                        SkSL::ProgramKind::kCompute,
     *                        settings,
     *                        &msl,
     *                        &interface,
     *                        errorHandler)) {
     *             return nullptr;
     *         }
     *         library = MtlCompileShaderLibrary(
     *                 sharedContext, pipelineDesc.computeStep()->name(), msl.fText, errorHandler);
     *         if (library == nil) {
     *             return nullptr;
     *         }
     *         entryPointName = "computeMain";
     *     }
     *
     *     sk_cfp<MTLComputePipelineDescriptor*> psoDescriptor([MTLComputePipelineDescriptor new]);
     *
     *     (*psoDescriptor).label = @(pipelineDesc.computeStep()->name());
     *
     *     NSString* entryPoint = [NSString stringWithUTF8String:entryPointName.c_str()];
     *     (*psoDescriptor).computeFunction = [library.get() newFunctionWithName:entryPoint];
     *
     *     // TODO(b/240604614): Populate input data attribute and buffer layout descriptors using the
     *     // `stageInputDescriptor` property based on the contents of `pipelineDesc` (on iOS 10+ or
     *     // macOS 10.12+).
     *
     *     // TODO(b/240604614): Define input buffer mutability using the `buffers` property based on
     *     // the contents of `pipelineDesc` (on iOS 11+ or macOS 10.13+).
     *
     *     // TODO(b/240615224): Metal docs claim that setting the
     *     // `threadGroupSizeIsMultipleOfThreadExecutionWidth` to YES may improve performance, IF we can
     *     // guarantee that the thread group size used in a dispatch command is a multiple of
     *     // `threadExecutionWidth` property of the pipeline state object (otherwise this will cause UB).
     *
     *     NSError* error;
     *     sk_cfp<id<MTLComputePipelineState>> pso([sharedContext->device()
     *             newComputePipelineStateWithDescriptor:psoDescriptor.get()
     *                                           options:MTLPipelineOptionNone
     *                                        reflection:nil
     *                                             error:&error]);
     *     if (!pso) {
     *         SKGPU_LOG_E("Compute pipeline creation failure:\n%s", error.debugDescription.UTF8String);
     *         return nullptr;
     *     }
     *
     *     return sk_sp<MtlComputePipeline>(new MtlComputePipeline(sharedContext, std::move(pso)));
     * }
     * ```
     */
    public fun make(sharedContext: MtlSharedContext?, pipelineDesc: ComputePipelineDesc): Int {
      TODO("Implement make")
    }
  }
}
