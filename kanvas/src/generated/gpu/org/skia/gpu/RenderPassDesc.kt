package org.skia.gpu

import kotlin.Any
import kotlin.Array
import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import org.skia.core.SkEnumBitMask

/**
 * C++ original:
 * ```cpp
 * struct RenderPassDesc {
 *     static RenderPassDesc Make(const Caps* caps,
 *                                const TextureInfo& targetInfo,
 *                                LoadOp loadOp,
 *                                StoreOp storeOp,
 *                                SkEnumBitMask<DepthStencilFlags> depthStencilFlags,
 *                                const std::array<float, 4>& clearColor,
 *                                bool requiresMSAA,
 *                                Swizzle writeSwizzle,
 *                                const DstReadStrategy);
 *
 *     bool operator==(const RenderPassDesc& other) const {
 *         return (fWriteSwizzle == other.fWriteSwizzle &&
 *                 fClearDepth == other.fClearDepth &&
 *                 fClearColor == other.fClearColor &&
 *                 fColorAttachment == other.fColorAttachment &&
 *                 fColorResolveAttachment == other.fColorResolveAttachment &&
 *                 fDepthStencilAttachment == other.fDepthStencilAttachment &&
 *                 fDstReadStrategy == other.fDstReadStrategy);
 *     }
 *
 *     bool operator!=(const RenderPassDesc& other) const {
 *         return !(*this == other);
 *     }
 *
 *     AttachmentDesc fColorAttachment;
 *     AttachmentDesc fColorResolveAttachment;
 *     AttachmentDesc fDepthStencilAttachment;
 *
 *     // The write swizzle is applied in shader, so affects SkSL code generation, but is determined by
 *     // the desired SkColorType semantics and target TextureFormat combination of the render pass.
 *     Swizzle fWriteSwizzle;
 *
 *     // The overall sample count of the render pass
 *     SampleCount fSampleCount;
 *
 *     // The remaining fields are set on renderpasses, but don't change the structure of the pass.
 *
 *     // Each renderpass determines what strategy to use for reading the dst texture. If no draws
 *     // within the renderpass require a dst read, this is set to be kNoneRequired. If any draw does
 *     // read from the dst, then each pipeline used by this RP independently determines if a dst read
 *     // is needed. When required, this strategy determines how to perform it.
 *     DstReadStrategy fDstReadStrategy;
 *
 *     std::array<float, 4> fClearColor;
 *     float fClearDepth = 0.f;
 *     uint32_t fClearStencil = 0;
 *
 *     SkString toString() const;
 *     // Only includes fixed state relevant to pipeline creation
 *     SkString toPipelineLabel() const;
 *
 *     // TODO:
 *     // * bounds (TBD whether exact bounds vs. granular)
 *     // * input attachments
 *     // * subpass makeup information
 * }
 * ```
 */
public data class RenderPassDesc public constructor(
  /**
   * C++ original:
   * ```cpp
   * AttachmentDesc fColorAttachment
   * ```
   */
  public var fColorAttachment: AttachmentDesc,
  /**
   * C++ original:
   * ```cpp
   * AttachmentDesc fColorResolveAttachment
   * ```
   */
  public var fColorResolveAttachment: AttachmentDesc,
  /**
   * C++ original:
   * ```cpp
   * AttachmentDesc fDepthStencilAttachment
   * ```
   */
  public var fDepthStencilAttachment: AttachmentDesc,
  /**
   * C++ original:
   * ```cpp
   * Swizzle fWriteSwizzle
   * ```
   */
  public var fWriteSwizzle: Int,
  /**
   * C++ original:
   * ```cpp
   * SampleCount fSampleCount
   * ```
   */
  public var fSampleCount: Int,
  /**
   * C++ original:
   * ```cpp
   * DstReadStrategy fDstReadStrategy
   * ```
   */
  public var fDstReadStrategy: Int,
  /**
   * C++ original:
   * ```cpp
   * std::array<float, 4> fClearColor
   * ```
   */
  public var fClearColor: Int,
  /**
   * C++ original:
   * ```cpp
   * float fClearDepth = 0.f
   * ```
   */
  public var fClearDepth: Float,
  /**
   * C++ original:
   * ```cpp
   * uint32_t fClearStencil
   * ```
   */
  public var fClearStencil: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool operator==(const RenderPassDesc& other) const {
   *         return (fWriteSwizzle == other.fWriteSwizzle &&
   *                 fClearDepth == other.fClearDepth &&
   *                 fClearColor == other.fClearColor &&
   *                 fColorAttachment == other.fColorAttachment &&
   *                 fColorResolveAttachment == other.fColorResolveAttachment &&
   *                 fDepthStencilAttachment == other.fDepthStencilAttachment &&
   *                 fDstReadStrategy == other.fDstReadStrategy);
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const RenderPassDesc& other) const {
   *         return !(*this == other);
   *     }
   * ```
   */
  public override fun toString(): Int {
    TODO("Implement toString")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString RenderPassDesc::toString() const {
   *     return SkStringPrintf("RP(color: %s, resolve: %s, ds: %s, samples: %u, swizzle: %s, "
   *                           "clear: c(%f,%f,%f,%f), d(%f), s(0x%02x), dst read: %u)",
   *                           fColorAttachment.toString().c_str(),
   *                           fColorResolveAttachment.toString().c_str(),
   *                           fDepthStencilAttachment.toString().c_str(),
   *                           (unsigned)fSampleCount,
   *                           fWriteSwizzle.asString().c_str(),
   *                           fClearColor[0], fClearColor[1], fClearColor[2], fClearColor[3],
   *                           fClearDepth,
   *                           fClearStencil,
   *                           (unsigned)fDstReadStrategy);
   * }
   * ```
   */
  public fun toPipelineLabel(): Int {
    TODO("Implement toPipelineLabel")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * RenderPassDesc RenderPassDesc::Make(const Caps* caps,
     *                                     const TextureInfo& targetInfo,
     *                                     LoadOp loadOp,
     *                                     StoreOp storeOp,
     *                                     SkEnumBitMask<DepthStencilFlags> depthStencilFlags,
     *                                     const std::array<float, 4>& clearColor,
     *                                     bool requiresMSAA,
     *                                     Swizzle writeSwizzle,
     *                                     const DstReadStrategy dstReadStrategy) {
     *     // It doesn't make sense to have a storeOp for our main target not be store. Why are we doing
     *     // this DrawPass then
     *     SkASSERT(storeOp == StoreOp::kStore);
     *
     *     RenderPassDesc desc;
     *     desc.fClearColor = clearColor;
     *     // Depth and stencil is currently always cleared to 1.f or 0 if it's used. Depth is 1.0 and
     *     // counts down as painter's order increases due to HW preference for historic OpenGL defaults
     *     // of a fast hi-z clear value of 1.0 with depth test of lesser.
     *     desc.fClearDepth = 1.f;
     *     desc.fClearStencil = 0;
     *     desc.fWriteSwizzle = writeSwizzle;
     *     desc.fDstReadStrategy = dstReadStrategy;
     *
     *     TextureFormat colorFormat = TextureInfoPriv::ViewFormat(targetInfo);
     *     // The render pass's overall sample count will either be the target's sample count
     *     // (when single-sampling or already multisampled), or the default sample count (which will then
     *     // be either the implicit sample count for msaa-render-to-single-sample or the explicit sample
     *     // count of a separate color attachment).
     *     //
     *     // Higher-level logic should ensure the default MSAA sample count is supported if using either
     *     // msaa-render-to-single-sample or with separate attachments, and select non-MSAA techniques if
     *     // they weren't supported. getCompatibleMSAASampleCount() downgrades to single-sampled if we got
     *     // here and MSAA isn't supported.
     *     const bool msaaRenderToSingleSampledSupport =
     *             caps->msaaTextureRenderToSingleSampledSupport(targetInfo);
     *     desc.fSampleCount = requiresMSAA ? caps->getCompatibleMSAASampleCount(targetInfo)
     *                                      : targetInfo.sampleCount();
     *
     *     // We need to handle MSAA with an extra color attachment if:
     *     const bool needsMSAAColorAttachment =
     *             desc.fSampleCount > SampleCount::k1 &&         // using MSAA for the render pass,
     *             targetInfo.sampleCount() == SampleCount::k1 && // the target isn't already MSAA'ed,
     *             !msaaRenderToSingleSampledSupport;             // can't use an MSAA->single extension.
     *     if (needsMSAAColorAttachment) {
     *         // We set the color and resolve attachments up the same regardless of if the backend ends up
     *         // using msaaRenderToSingleSampledSupport() to skip explicitly creating the MSAA attachment.
     *         // The color attachment (and any depth/stencil attachment) will use `sampleCount` and the
     *         // resolve attachment will be single-sampled.
     *         desc.fColorAttachment = {colorFormat,
     *                                  loadOp != LoadOp::kClear ? LoadOp::kDiscard : LoadOp::kClear,
     *                                  StoreOp::kDiscard,
     *                                  desc.fSampleCount};
     *         desc.fColorResolveAttachment = {colorFormat,
     *                                         loadOp != LoadOp::kLoad ? LoadOp::kDiscard : LoadOp::kLoad,
     *                                         storeOp,
     *                                         SampleCount::k1};
     *     } else {
     *         // The target will be the color attachment and skip configuring the resolve attachment.
     *         SkASSERT(desc.fColorResolveAttachment.fFormat == TextureFormat::kUnsupported);
     *         desc.fColorAttachment = {colorFormat,
     *                                  loadOp,
     *                                  storeOp,
     *                                  targetInfo.sampleCount()};
     *     }
     *
     *     if (depthStencilFlags != DepthStencilFlags::kNone) {
     *         // To reduce pipeline compiles and attachment creations, if we need multisampling and need
     *         // a depth or stencil attachment, we always choose a depth-AND-stencil format.
     *         if (desc.fColorAttachment.fSampleCount > SampleCount::k1) {
     *             depthStencilFlags = DepthStencilFlags::kDepthStencil;
     *         }
     *         TextureFormat dsFormat = caps->getDepthStencilFormat(depthStencilFlags);
     *         SkASSERT(dsFormat != TextureFormat::kUnsupported);
     *         // Depth and stencil values are currently always cleared and don't need to persist.
     *         // The sample count should always match the color attachment.
     *         desc.fDepthStencilAttachment = {dsFormat,
     *                                         LoadOp::kClear,
     *                                         StoreOp::kDiscard,
     *                                         desc.fColorAttachment.fSampleCount};
     *     } else {
     *         SkASSERT(desc.fDepthStencilAttachment.fFormat == TextureFormat::kUnsupported);
     *     }
     *
     *     return desc;
     * }
     * ```
     */
    public fun make(
      caps: Caps?,
      targetInfo: TextureInfo,
      loadOp: LoadOp,
      storeOp: StoreOp,
      depthStencilFlags: SkEnumBitMask<DepthStencilFlags>,
      clearColor: Array<Float>,
      requiresMSAA: Boolean,
      writeSwizzle: Swizzle,
      dstReadStrategy: DstReadStrategy,
    ): RenderPassDesc {
      TODO("Implement make")
    }
  }
}
