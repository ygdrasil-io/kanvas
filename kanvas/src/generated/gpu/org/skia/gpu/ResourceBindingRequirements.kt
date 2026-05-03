package org.skia.gpu

import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct ResourceBindingRequirements {
 *     /* The API of the backend currently in use. */
 *     BackendApi fBackendApi = BackendApi::kUnsupported;
 *
 *     /* The required data layout rules for the contents of a uniform buffer. */
 *     Layout fUniformBufferLayout = Layout::kInvalid;
 *
 *     /* The required data layout rules for the contents of a storage buffer. */
 *     Layout fStorageBufferLayout = Layout::kInvalid;
 *
 *     /**
 *      * Whether combined texture-sampler types are supported. Backends that do not support combined
 *      * image samplers (i.e. sampler2D) require a texture and sampler object to be bound separately
 *      * and their binding indices explicitly specified in the shader text.
 *      */
 *     bool fSeparateTextureAndSamplerBinding = false;
 *
 *     /**
 *      * Whether intrinsic constant information is stored as push constants (rather than normal UBO).
 *      * Currently only relevant or possibly true for Dawn or Vulkan.
 *      */
 *     bool fUsePushConstantsForIntrinsicConstants  = false;
 *
 *     /**
 *      * Whether compute shader textures use separate index ranges from other resources (i.e. buffers)
 *      */
 *     bool fComputeUsesDistinctIdxRangesForTextures = false;
 *
 *     /**
 *      * Define set indices. We assume that even if textures and samplers must be bound separately,
 *      * they will still be contained within the same set/group.
 *      */
 *     static constexpr int kUnassigned  = -1;
 *     int fUniformsSetIdx               = kUnassigned;
 *     int fTextureSamplerSetIdx         = kUnassigned;
 *     int fInputAttachmentSetIdx        = kUnassigned;
 *     /* Define uniform buffer bindings */
 *     int fIntrinsicBufferBinding       = kUnassigned;
 *     int fCombinedUniformBufferBinding = kUnassigned;
 *     int fGradientBufferBinding        = kUnassigned;
 * }
 * ```
 */
public data class ResourceBindingRequirements public constructor(
  /**
   * C++ original:
   * ```cpp
   * BackendApi fBackendApi
   * ```
   */
  public var fBackendApi: Int,
  /**
   * C++ original:
   * ```cpp
   * Layout fUniformBufferLayout
   * ```
   */
  public var fUniformBufferLayout: Int,
  /**
   * C++ original:
   * ```cpp
   * Layout fStorageBufferLayout
   * ```
   */
  public var fStorageBufferLayout: Int,
  /**
   * C++ original:
   * ```cpp
   * bool fSeparateTextureAndSamplerBinding = false
   * ```
   */
  public var fSeparateTextureAndSamplerBinding: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fUsePushConstantsForIntrinsicConstants  = false
   * ```
   */
  public var fUsePushConstantsForIntrinsicConstants: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fComputeUsesDistinctIdxRangesForTextures = false
   * ```
   */
  public var fComputeUsesDistinctIdxRangesForTextures: Boolean,
  /**
   * C++ original:
   * ```cpp
   * static constexpr int kUnassigned  = -1
   * ```
   */
  public var fUniformsSetIdx: Int,
  /**
   * C++ original:
   * ```cpp
   * int fUniformsSetIdx               = kUnassigned
   * ```
   */
  public var fTextureSamplerSetIdx: Int,
  /**
   * C++ original:
   * ```cpp
   * int fTextureSamplerSetIdx         = kUnassigned
   * ```
   */
  public var fInputAttachmentSetIdx: Int,
  /**
   * C++ original:
   * ```cpp
   * int fInputAttachmentSetIdx        = kUnassigned
   * ```
   */
  public var fIntrinsicBufferBinding: Int,
  /**
   * C++ original:
   * ```cpp
   * int fIntrinsicBufferBinding       = kUnassigned
   * ```
   */
  public var fCombinedUniformBufferBinding: Int,
  /**
   * C++ original:
   * ```cpp
   * int fCombinedUniformBufferBinding = kUnassigned
   * ```
   */
  public var fGradientBufferBinding: Int,
) {
  public companion object {
    public val kUnassigned: Int = TODO("Initialize kUnassigned")
  }
}
