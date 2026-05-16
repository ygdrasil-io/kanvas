package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct DescriptorData {
 *     DescriptorData(DescriptorType type,
 *                    uint32_t count,
 *                    int bindingIdx,
 *                    SkEnumBitMask<PipelineStageFlags> stageFlags,
 *                    const Sampler* immutableSampler = nullptr)
 *             : fType (type)
 *             , fCount (count)
 *             , fBindingIndex (bindingIdx)
 *             , fPipelineStageFlags(stageFlags)
 *             , fImmutableSampler(immutableSampler) {}
 *
 *     DescriptorType fType;
 *     uint32_t fCount;
 *     int fBindingIndex;
 *     SkEnumBitMask<PipelineStageFlags> fPipelineStageFlags;
 *     const Sampler* fImmutableSampler;
 * }
 * ```
 */
public data class DescriptorData public constructor(
  /**
   * C++ original:
   * ```cpp
   * DescriptorType fType
   * ```
   */
  public var fType: DescriptorType,
  /**
   * C++ original:
   * ```cpp
   * uint32_t fCount
   * ```
   */
  public var fCount: Int,
  /**
   * C++ original:
   * ```cpp
   * int fBindingIndex
   * ```
   */
  public var fBindingIndex: Int,
  /**
   * C++ original:
   * ```cpp
   * SkEnumBitMask<PipelineStageFlags> fPipelineStageFlags
   * ```
   */
  public var fPipelineStageFlags: Int,
  /**
   * C++ original:
   * ```cpp
   * const Sampler* fImmutableSampler
   * ```
   */
  public val fImmutableSampler: Sampler?,
)
