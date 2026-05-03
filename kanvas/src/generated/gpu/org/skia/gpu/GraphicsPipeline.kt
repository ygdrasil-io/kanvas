package org.skia.gpu

import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.String
import kotlin.UShort

/**
 * C++ original:
 * ```cpp
 * class GraphicsPipeline : public Resource {
 * public:
 *     ~GraphicsPipeline() override;
 *
 *     const char* getResourceType() const override { return "Graphics Pipeline"; }
 *
 *     DstReadStrategy dstReadStrategy() const { return fPipelineInfo.fDstReadStrategy; }
 *
 *     int  numFragTexturesAndSamplers() const { return fPipelineInfo.fNumFragTexturesAndSamplers; }
 *     bool hasCombinedUniforms()        const { return fPipelineInfo.fHasCombinedUniforms;        }
 *     bool hasGradientBuffer()          const { return fPipelineInfo.fHasGradientBuffer;          }
 *
 *     struct PipelineInfo {
 *         PipelineInfo() = default;
 *
 *         // NOTE: Subclasses must manually fill in native shader code in GPU_TEST_UTILS builds.
 *         PipelineInfo(const ShaderInfo&, SkEnumBitMask<PipelineCreationFlags>,
 *                      uint32_t uniqueKeyHash, uint32_t compilationID);
 *
 *         DstReadStrategy fDstReadStrategy = DstReadStrategy::kNoneRequired;
 *         int  fNumFragTexturesAndSamplers = 0;
 *         bool fHasCombinedUniforms = false;
 *         bool fHasGradientBuffer = false;
 *
 *         // In test-enabled builds, we preserve the generated shader code to display in the viewer
 *         // slide UI. This is not quite enough information to fully recreate the pipeline, as the
 *         // RenderPassDesc used to make the pipeline is not preserved.
 * #if defined(GPU_TEST_UTILS)
 *         std::string fSkSLVertexShader;
 *         std::string fSkSLFragmentShader;
 *         std::string fNativeVertexShader;
 *         std::string fNativeFragmentShader;
 * #endif
 *         const uint32_t fUniqueKeyHash = 0;
 *         // The compilation ID is used to distinguish between different compilations/instantiations
 *         // of the same unique key. If, for example, two versions were created due to threading.
 *         const uint32_t fCompilationID = 0;
 *         const bool fFromPrecompile = false;
 *         bool fWasUsed = false;
 *         uint16_t fEpoch = 0;   // the last epoch in which this Pipeline was touched
 *     };
 *
 *     const PipelineInfo& getPipelineInfo() const { return fPipelineInfo; }
 *     bool fromPrecompile() const { return fPipelineInfo.fFromPrecompile; }
 *
 *     void markUsed() { fPipelineInfo.fWasUsed = true; }
 *     bool wasUsed() const { return fPipelineInfo.fWasUsed; }
 *
 *     void markEpoch(uint16_t epoch) { fPipelineInfo.fEpoch = epoch; }
 *     uint16_t epoch() const { return fPipelineInfo.fEpoch; }
 *
 *     // GraphicsPipeline compiles can take a while. If the underlying compilation is performed
 *     // asynchronously, we may create a GraphicsPipeline object that later "fails" and need to remove
 *     // it from the GlobalCache.
 *     virtual bool didAsyncCompilationFail() const { return false; }
 *
 * protected:
 *     // GraphicsPipeline labels are often provided to the description of what needs to be compiled,
 *     // so it is required before the actual pipeline has been successfully created. Instead of adding
 *     // it to PipelineInfo, just use Resource's label field.
 *     GraphicsPipeline(const SharedContext*, const PipelineInfo&, std::string_view label);
 *
 * private:
 *     PipelineInfo fPipelineInfo;
 * }
 * ```
 */
public open class GraphicsPipeline public constructor(
  param0: SharedContext,
  param1: PipelineInfo,
  label: String,
) : Resource() {
  /**
   * C++ original:
   * ```cpp
   * PipelineInfo fPipelineInfo
   * ```
   */
  private var fPipelineInfo: PipelineInfo = TODO("Initialize fPipelineInfo")

  /**
   * C++ original:
   * ```cpp
   * GraphicsPipeline(const SharedContext*, const PipelineInfo&, std::string_view label)
   * ```
   */
  public constructor(
    sharedContext: SharedContext?,
    pipelineInfo: PipelineInfo,
    label: String,
  ) : this(TODO(), TODO(), TODO()) {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* getResourceType() const override { return "Graphics Pipeline"; }
   * ```
   */
  public override fun getResourceType(): Char {
    TODO("Implement getResourceType")
  }

  /**
   * C++ original:
   * ```cpp
   * DstReadStrategy dstReadStrategy() const { return fPipelineInfo.fDstReadStrategy; }
   * ```
   */
  public fun dstReadStrategy(): Int {
    TODO("Implement dstReadStrategy")
  }

  /**
   * C++ original:
   * ```cpp
   * int  numFragTexturesAndSamplers() const { return fPipelineInfo.fNumFragTexturesAndSamplers; }
   * ```
   */
  public fun numFragTexturesAndSamplers(): Int {
    TODO("Implement numFragTexturesAndSamplers")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasCombinedUniforms()        const { return fPipelineInfo.fHasCombinedUniforms;        }
   * ```
   */
  public fun hasCombinedUniforms(): Boolean {
    TODO("Implement hasCombinedUniforms")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasGradientBuffer()          const { return fPipelineInfo.fHasGradientBuffer;          }
   * ```
   */
  public fun hasGradientBuffer(): Boolean {
    TODO("Implement hasGradientBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * const PipelineInfo& getPipelineInfo() const { return fPipelineInfo; }
   * ```
   */
  public fun getPipelineInfo(): PipelineInfo {
    TODO("Implement getPipelineInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * bool fromPrecompile() const { return fPipelineInfo.fFromPrecompile; }
   * ```
   */
  public fun fromPrecompile(): Boolean {
    TODO("Implement fromPrecompile")
  }

  /**
   * C++ original:
   * ```cpp
   * void markUsed() { fPipelineInfo.fWasUsed = true; }
   * ```
   */
  public fun markUsed() {
    TODO("Implement markUsed")
  }

  /**
   * C++ original:
   * ```cpp
   * bool wasUsed() const { return fPipelineInfo.fWasUsed; }
   * ```
   */
  public fun wasUsed(): Boolean {
    TODO("Implement wasUsed")
  }

  /**
   * C++ original:
   * ```cpp
   * void markEpoch(uint16_t epoch) { fPipelineInfo.fEpoch = epoch; }
   * ```
   */
  public fun markEpoch(epoch: UShort) {
    TODO("Implement markEpoch")
  }

  /**
   * C++ original:
   * ```cpp
   * uint16_t epoch() const { return fPipelineInfo.fEpoch; }
   * ```
   */
  public fun epoch(): Int {
    TODO("Implement epoch")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool didAsyncCompilationFail() const { return false; }
   * ```
   */
  public open fun didAsyncCompilationFail(): Boolean {
    TODO("Implement didAsyncCompilationFail")
  }

  public data class PipelineInfo public constructor(
    public var fDstReadStrategy: Int,
    public var fNumFragTexturesAndSamplers: Int,
    public var fHasCombinedUniforms: Boolean,
    public var fHasGradientBuffer: Boolean,
    public var fSkSLVertexShader: Int,
    public var fSkSLFragmentShader: Int,
    public var fNativeVertexShader: Int,
    public var fNativeFragmentShader: Int,
    public val fUniqueKeyHash: Int,
    public val fCompilationID: Int,
    public val fFromPrecompile: Boolean,
    public var fWasUsed: Boolean,
    public var fEpoch: Int,
  )
}
