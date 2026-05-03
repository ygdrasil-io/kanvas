package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import kotlin.ULong
import org.skia.core.SkEnumBitMask
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class DrawList {
 * public:
 *     // The maximum number of render steps that can be recorded into a DrawList before it must be
 *     // converted to a DrawPass. The true fundamental limit is imposed by the limits of the depth
 *     // attachment and precision of CompressedPaintersOrder and PaintDepth. These values can be
 *     // shared by multiple draw calls so it's more difficult to reason about how much room is left
 *     // in a DrawList. Limiting it to this keeps tracking simple and ensures that the sequences in
 *     // DrawOrder cannot overflow since they are always less than or equal to the number of draws.
 *     // TODO(b/322840221): The theoretic max for this value is 16-bit, but we see markedly better
 *     // performance with smaller values. This should be understood and fixed directly rather than as
 *     // a magic side-effect, but for now, let it go fast.
 *     static constexpr int kMaxRenderSteps = 4096;
 *     static_assert(kMaxRenderSteps <= std::numeric_limits<uint16_t>::max());
 *
 *     // Add a construtor to prevent default zero initialization of SkTBlockList members' storage.
 *     DrawList() {}
 *
 *     // DrawList requires that all Transforms be valid and asserts as much; invalid transforms should
 *     // be detected at the Device level or similar. The provided Renderer must be compatible with the
 *     // 'shape' and 'stroke' parameters. If the renderer uses coverage AA, 'ordering' must have a
 *     // compressed painters order that reflects that. If the renderer uses stencil, the 'ordering'
 *     // must have a valid stencil index as well.
 *     void recordDraw(const Renderer* renderer,
 *                     const Transform& localToDevice,
 *                     const Geometry& geometry,
 *                     const Clip& clip,
 *                     DrawOrder ordering,
 *                     UniquePaintParamsID paintID,
 *                     SkEnumBitMask<DstUsage> dstUsage,
 *                     BarrierType barrierBeforeDraws,
 *                     PipelineDataGatherer* gatherer,
 *                     const StrokeStyle* stroke);
 *
 *     std::unique_ptr<DrawPass> snapDrawPass(Recorder* recorder,
 *                                            sk_sp<TextureProxy> target,
 *                                            const SkImageInfo& targetInfo,
 *                                            const DstReadStrategy dstReadStrategy);
 *
 *     int renderStepCount() const { return fRenderStepCount; }
 *
 *     bool modifiesTarget() const {
 *         return this->renderStepCount() > 0 || fLoadOp == LoadOp::kClear;
 *     }
 *
 *     bool samplesTexture(const TextureProxy* texture) const {
 *         return fTextureDataCache.hasTexture(texture);
 *     }
 *
 *     // Discard all previously recorded draws and set to the requested load op (with optional clear
 *     // color).
 *     void reset(LoadOp op, SkColor4f clearColor = {0.f, 0.f, 0.f, 0.f});
 *
 *     // Bounds for a dst read required by this DrawList. These bounds are only valid if drawsReadDst
 *     // returns true.
 *     const Rect& dstReadBounds() const { return fDstReadBounds; }
 *     const Rect& passBounds() const { return fPassBounds; }
 *     bool drawsReadDst() const { return !fDstReadBounds.isEmptyNegativeOrNaN(); }
 *     bool drawsRequireMSAA() const { return fRequiresMSAA; }
 *     SkEnumBitMask<DepthStencilFlags> depthStencilFlags() const { return fDepthStencilFlags; }
 *
 *     SkDEBUGCODE(bool hasCoverageMaskDraws() const { return fCoverageMaskShapeDrawCount > 0; })
 *
 * private:
 *     friend class DrawPass;
 *
 *     struct Draw {
 *     public:
 *         Draw(const Renderer* renderer, const Transform& transform, const Geometry& geometry,
 *              const Clip& clip, DrawOrder order, BarrierType barrierBeforeDraws,
 *              const StrokeStyle* stroke)
 *                 : fRenderer(renderer)
 *                 , fDrawParams(transform, geometry, clip, order, stroke)
 *                 , fBarrierBeforeDraws(barrierBeforeDraws) {}
 *
 *         const Renderer* renderer()                             const { return fRenderer;           }
 *         const DrawParams& drawParams()                         const { return fDrawParams;         }
 *         const BarrierType& barrierBeforeDraws()                const { return fBarrierBeforeDraws; }
 *
 *     private:
 *         const Renderer* fRenderer; // Owned by SharedContext of Recorder that recorded the draw
 *         DrawParams fDrawParams; // The DrawParam's transform is owned by fTransforms of the DrawList
 *         BarrierType fBarrierBeforeDraws;
 *     };
 *
 *     template <uint64_t Bits, uint64_t Offset>
 *     struct Bitfield {
 *         static constexpr uint64_t kMask = ((uint64_t) 1 << Bits) - 1;
 *         static constexpr uint64_t kOffset = Offset;
 *         static constexpr uint64_t kBits = Bits;
 *
 *         static uint32_t get(uint64_t v) { return static_cast<uint32_t>((v >> kOffset) & kMask); }
 *         static uint64_t set(uint32_t v) { return (v & kMask) << kOffset; }
 *     };
 *
 *     /**
 *      * Each Draw in a DrawList might be processed by multiple RenderSteps (determined by the Draw's
 *      * Renderer), which can be sorted independently. Each (step, draw) pair produces its own
 *      * SortKey.
 *      *
 *      * The goal of sorting draws for the DrawPass is to minimize pipeline transitions and dynamic
 *      * binds within a pipeline, while still respecting the overall painter's order. This decreases
 *      * the number of low-level draw commands in a command buffer and increases the size of those,
 *      * allowing the GPU to operate more efficiently and have fewer bubbles within its own
 *      * instruction stream.
 *      *
 *      * The Draw's CompresssedPaintersOrder and DisjointStencilIndex represent the most significant
 *      * bits of the key, and are shared by all SortKeys produced by the same draw. Next, the pipeline
 *      * description is encoded in two steps:
 *      *  1. The index of the RenderStep packed in the high bits to ensure each step for a draw is
 *      *     ordered correctly.
 *      *  2. An index into a cache of pipeline descriptions is used to encode the identity of the
 *      *     pipeline (SortKeys that differ in the bits from #1 necessarily would have different
 *      *     descriptions, but then the specific ordering of the RenderSteps isn't enforced). Last,
 *      *     the SortKey encodes an index into the set of uniform bindings accumulated for a DrawPass.
 *      *     This allows the SortKey to cluster draw steps that have both a compatible pipeline and do
 *      *     not require rebinding uniform data or other state (e.g. scissor). Since the uniform data
 *      *     index and the pipeline description index are packed into indices and not actual pointers,
 *      *     a given SortKey is only valid for the a specific DrawList->DrawPass conversion.
 *      */
 *     class SortKey {
 *     public:
 *         SortKey(const DrawList::Draw* draw,
 *                 int renderStep,
 *                 GraphicsPipelineCache::Index pipelineIndex,
 *                 UniformDataCache::Index uniformIndex,
 *                 TextureDataCache::Index textureBindingIndex)
 *                 : fPipelineKey(
 *                           ColorDepthOrderField::set(draw->drawParams().order().paintOrder().bits())
 *                           | StencilIndexField::set(draw->drawParams().order().stencilIndex().bits())
 *                           | RenderStepField::set(static_cast<uint32_t>(renderStep))
 *                           | PipelineField::set(pipelineIndex))
 *                 , fUniformKey(UniformField::set(uniformIndex) |
 *                               TextureBindingsField::set(textureBindingIndex))
 *                 , fDraw(draw) {
 *             SkASSERT(pipelineIndex < GraphicsPipelineCache::kInvalidIndex);
 *             SkASSERT(renderStep <= draw->renderer()->numRenderSteps());
 *         }
 *
 *         bool operator<(const SortKey& k) const {
 *             return fPipelineKey < k.fPipelineKey ||
 *                 (fPipelineKey == k.fPipelineKey && fUniformKey < k.fUniformKey);
 *         }
 *
 *         const RenderStep& renderStep() const {
 *             return fDraw->renderer()->step(RenderStepField::get(fPipelineKey));
 *         }
 *
 *         const DrawList::Draw& draw() const { return *fDraw; }
 *
 *         GraphicsPipelineCache::Index pipelineIndex() const {
 *             return PipelineField::get(fPipelineKey);
 *         }
 *         UniformDataCache::Index uniformIndex() const {
 *             return UniformField::get(fUniformKey);
 *         }
 *         TextureDataCache::Index textureBindingIndex() const {
 *             return TextureBindingsField::get(fUniformKey);
 *         }
 *
 *     private:
 *         // Fields are ordered from most-significant to least when sorting by 128-bit value.
 *         // NOTE: We don't use C++ bit fields because field ordering is implementation defined and we
 *         // need to sort consistently.
 *         using ColorDepthOrderField = Bitfield<16, 48>; // sizeof(CompressedPaintersOrder)
 *         using StencilIndexField    = Bitfield<16, 32>; // sizeof(DisjointStencilIndex)
 *         using RenderStepField      = Bitfield<2,  30>; // bits >= log2(Renderer::kMaxRenderSteps)
 *         using PipelineField        = Bitfield<30, 0>;  // bits >= log2(max total steps in draw list)
 *         uint64_t fPipelineKey;
 *
 *         // The uniform/texture index fields need 1 extra bit to encode "no-data". Values that are
 *         // greater than or equal to 2^(bits-1) represent "no-data", while values between
 *         // [0, 2^(bits-1)-1] can access data arrays without extra logic.
 *         using UniformField         = Bitfield<34, 30>; // bits >= 1+log2(max total steps)
 *         using TextureBindingsField = Bitfield<30, 0>;  // bits >= 1+log2(max total steps)
 *         uint64_t fUniformKey;
 *
 *         // Backpointer to the draw that produced the sort key
 *         const DrawList::Draw* fDraw;
 *
 *         static_assert(ColorDepthOrderField::kBits >= sizeof(CompressedPaintersOrder));
 *         static_assert(StencilIndexField::kBits    >= sizeof(DisjointStencilIndex));
 *         static_assert(RenderStepField::kBits      >= SkNextLog2_portable(Renderer::kMaxRenderSteps));
 *         static_assert(PipelineField::kBits        >= SkNextLog2_portable(DrawList::kMaxRenderSteps));
 *         static_assert(UniformField::kBits         >= 1+SkNextLog2_portable(DrawList::kMaxRenderSteps));
 *         static_assert(TextureBindingsField::kBits >= 1+SkNextLog2_portable(DrawList::kMaxRenderSteps));
 *     };
 *
 *
 *     // The returned Transform reference remains valid for the lifetime of the DrawList.
 *     const Transform& deduplicateTransform(const Transform&);
 *
 *     SkTBlockList<Transform, 16> fTransforms{SkBlockAllocator::GrowthPolicy::kFibonacci};
 *     SkTBlockList<Draw, 16>      fDraws{SkBlockAllocator::GrowthPolicy::kFibonacci};
 *
 *     // Running total of RenderSteps for all draws, assuming nothing is culled
 *     int fRenderStepCount = 0;
 *
 * #if defined(SK_DEBUG)
 *     // The number of CoverageMask draws that have been recorded. Used in debugging.
 *     int fCoverageMaskShapeDrawCount = 0;
 * #endif
 *
 *     // Tracked for all paints that read from the dst. If it is later determined that the
 *     // DstReadStrategy is not kTextureCopy, this value can simply be ignored.
 *     Rect fDstReadBounds = Rect::InfiniteInverted();
 *     Rect fPassBounds = Rect::InfiniteInverted();
 *     // Other properties of draws contained within this DrawList
 *     bool fRequiresMSAA = false;
 *     SkEnumBitMask<DepthStencilFlags> fDepthStencilFlags = DepthStencilFlags::kNone;
 *
 *     std::vector<SortKey> fSortKeys;
 *
 *     UniformDataCache fUniformDataCache;
 *     TextureDataCache fTextureDataCache;
 *     GraphicsPipelineCache fPipelineCache;
 *
 *     LoadOp fLoadOp = LoadOp::kLoad;
 *     std::array<float, 4> fClearColor = {0.f, 0.f, 0.f, 0.f};
 * }
 * ```
 */
public data class DrawList public constructor(
  /**
   * C++ original:
   * ```cpp
   * static constexpr int kMaxRenderSteps = 4096
   * ```
   */
  private var fTransforms: Int,
  /**
   * C++ original:
   * ```cpp
   * SkTBlockList<Transform, 16> fTransforms
   * ```
   */
  private var fDraws: Int,
  /**
   * C++ original:
   * ```cpp
   * SkTBlockList<Draw, 16>      fDraws
   * ```
   */
  private var fRenderStepCount: Int,
  /**
   * C++ original:
   * ```cpp
   * int fRenderStepCount = 0
   * ```
   */
  private var fDstReadBounds: Int,
  /**
   * C++ original:
   * ```cpp
   * Rect fDstReadBounds
   * ```
   */
  private var fPassBounds: Int,
  /**
   * C++ original:
   * ```cpp
   * Rect fPassBounds
   * ```
   */
  private var fRequiresMSAA: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fRequiresMSAA = false
   * ```
   */
  private var fDepthStencilFlags: Int,
  /**
   * C++ original:
   * ```cpp
   * SkEnumBitMask<DepthStencilFlags> fDepthStencilFlags
   * ```
   */
  private var fSortKeys: Int,
  /**
   * C++ original:
   * ```cpp
   * std::vector<SortKey> fSortKeys
   * ```
   */
  private var fUniformDataCache: Int,
  /**
   * C++ original:
   * ```cpp
   * UniformDataCache fUniformDataCache
   * ```
   */
  private var fTextureDataCache: Int,
  /**
   * C++ original:
   * ```cpp
   * TextureDataCache fTextureDataCache
   * ```
   */
  private var fPipelineCache: Int,
  /**
   * C++ original:
   * ```cpp
   * GraphicsPipelineCache fPipelineCache
   * ```
   */
  private var fLoadOp: Int,
  /**
   * C++ original:
   * ```cpp
   * LoadOp fLoadOp
   * ```
   */
  private var fClearColor: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * void DrawList::recordDraw(const Renderer* renderer,
   *                           const Transform& localToDevice,
   *                           const Geometry& geometry,
   *                           const Clip& clip,
   *                           DrawOrder ordering,
   *                           UniquePaintParamsID paintID,
   *                           SkEnumBitMask<DstUsage> dstUsage,
   *                           BarrierType barrierBeforeDraws,
   *                           PipelineDataGatherer* gatherer,
   *                           const StrokeStyle* stroke) {
   *     SkASSERT(localToDevice.valid());
   *     SkASSERT(!geometry.isEmpty() && !clip.drawBounds().isEmptyNegativeOrNaN());
   *     SkASSERT(!(renderer->depthStencilFlags() & DepthStencilFlags::kStencil) ||
   *              ordering.stencilIndex() != DrawOrder::kUnassigned);
   *
   *     // TODO: Add validation that the renderer's expected shape type and stroke params match provided
   *
   *     const Draw& draw = fDraws.emplace_back(renderer,
   *                                            this->deduplicateTransform(localToDevice),
   *                                            geometry,
   *                                            clip,
   *                                            ordering,
   *                                            barrierBeforeDraws,
   *                                            stroke);
   *
   *     fRenderStepCount += renderer->numRenderSteps();
   *     // Create a sort key for every render step in this draw
   *     for (int stepIndex = 0; stepIndex < draw.renderer()->numRenderSteps(); ++stepIndex) {
   *         const RenderStep* const step = draw.renderer()->steps()[stepIndex];
   *         gatherer->markOffsetAndAlign(step->performsShading(), step->uniformAlignment());
   *
   *         GraphicsPipelineCache::Index pipelineIndex = fPipelineCache.insert(
   *                 { step->renderStepID(), step->performsShading() ?
   *                                         paintID : UniquePaintParamsID::Invalid()});
   *
   *         step->writeUniformsAndTextures(draw.drawParams(), gatherer);
   *
   *         auto [combinedUniforms, combinedTextures] =
   *                 gatherer->endCombinedData(step->performsShading());
   *
   *         UniformDataCache::Index uniformIndex = combinedUniforms ?
   *                 fUniformDataCache.insert(combinedUniforms) : UniformDataCache::kInvalidIndex;
   *         TextureDataCache::Index textureBindingIndex = combinedTextures ?
   *                 fTextureDataCache.insert(combinedTextures) : TextureDataCache::kInvalidIndex;
   *
   *         fSortKeys.push_back({&draw, stepIndex, pipelineIndex, uniformIndex, textureBindingIndex});
   *         gatherer->rewindForRenderStep();
   *     }
   *
   *     fPassBounds.join(clip.drawBounds());
   *     fRequiresMSAA |= renderer->requiresMSAA();
   *     fDepthStencilFlags |= renderer->depthStencilFlags();
   *     if (dstUsage & DstUsage::kDstReadRequired) {
   *         // For paints that read from the dst, update the bounds. It may later be determined that the
   *         // DstReadStrategy does not require them, but they are inexpensive to track.
   *         fDstReadBounds.join(clip.drawBounds());
   *     }
   *
   * #if defined(SK_DEBUG)
   *     if (geometry.isCoverageMaskShape()) {
   *         fCoverageMaskShapeDrawCount++;
   *     }
   * #endif
   * }
   * ```
   */
  public fun recordDraw(
    renderer: Renderer?,
    localToDevice: Transform,
    geometry: Geometry,
    clip: Clip,
    ordering: DrawOrder,
    paintID: UniquePaintParamsID,
    dstUsage: SkEnumBitMask<DstUsage>,
    barrierBeforeDraws: BarrierType,
    gatherer: PipelineDataGatherer?,
    stroke: StrokeStyle?,
  ) {
    TODO("Implement recordDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<DrawPass> DrawList::snapDrawPass(Recorder* recorder,
   *                                                  sk_sp<TextureProxy> target,
   *                                                  const SkImageInfo& targetInfo,
   *                                                  const DstReadStrategy dstReadStrategy) {
   *     // NOTE: This assert is here to ensure SortKey is as tightly packed as possible. Any change to
   *     // its size should be done with care and good reason. The performance of sorting the keys is
   *     // heavily tied to the total size.
   *     //
   *     // At 24 bytes (current), sorting is about 30% slower than if SortKey could be packed into just
   *     // 16 bytes. There are several ways this could be done if necessary:
   *     //  - Restricting the max draw count to 16k (14-bits) and only using a single index to refer to
   *     //    the uniform data => 8 bytes of key, 8 bytes of pointer.
   *     //  - Restrict the max draw count to 32k (15-bits), use a single uniform index, and steal the
   *     //    4 low bits from the Draw* pointer since it's 16 byte aligned.
   *     //  - Compact the Draw* to an index into the original collection, although that has extra
   *     //    indirection and does not work as well with SkTBlockList.
   *     // In pseudo tests, manipulating the pointer or having to mask out indices was about 15% slower
   *     // than an 8 byte key and unmodified pointer.
   *     static_assert(sizeof(SortKey) == SkAlignTo(16 + sizeof(void*), alignof(SortKey)));
   *
   *     // TODO: Explore sorting algorithms; in all likelihood this will be mostly sorted already, so
   *     // algorithms that approach O(n) in that condition may be favorable. Alternatively, could
   *     // explore radix sort that is always O(n). Brief testing suggested std::sort was faster than
   *     // std::stable_sort and SkTQSort on my [ml]'s Windows desktop. Also worth considering in-place
   *     // vs. algorithms that require an extra O(n) storage.
   *     // TODO: It's not strictly necessary, but would a stable sort be useful or just end up hiding
   *     // bugs in the DrawOrder determination code?
   *     std::sort(fSortKeys.begin(), fSortKeys.end());
   *
   *     TRACE_EVENT1("skia.gpu", TRACE_FUNC, "draw count", fDraws.count());
   *
   *     // The DrawList is converted directly into the DrawPass' data structures, but once the DrawPass
   *     // is returned from Make(), it is considered immutable.
   *     std::unique_ptr<DrawPass> drawPass(new DrawPass(target, {fLoadOp, StoreOp::kStore}, fClearColor,
   *                                                     recorder->priv().refFloatStorageManager()));
   *
   *     DrawBufferManager* bufferMgr = recorder->priv().drawBufferManager();
   *     DrawWriter drawWriter(&drawPass->fCommandList, bufferMgr);
   *     GraphicsPipelineCache::Index lastPipeline = GraphicsPipelineCache::kInvalidIndex;
   *     const SkIRect targetBounds = SkIRect::MakeSize(targetInfo.dimensions());
   *     SkIRect lastScissor = targetBounds;
   *
   *     SkASSERT(drawPass->fTarget->isFullyLazy() ||
   *              SkIRect::MakeSize(drawPass->fTarget->dimensions()).contains(lastScissor));
   *     drawPass->fCommandList.setScissor(lastScissor);
   *
   *     const Caps* caps = recorder->priv().caps();
   *     const bool useStorageBuffers = caps->storageBufferSupport();
   *     UniformTracker uniformTracker(useStorageBuffers);
   *
   *     // TODO(b/372953722): Remove this forced binding command behavior once dst copies are always
   *     // bound separately from the rest of the textures.
   *     const bool rebindTexturesOnPipelineChange = dstReadStrategy == DstReadStrategy::kTextureCopy;
   *     // Keep track of the prior draw's PaintOrder. If the current draw requires barriers and there
   *     // is no pipeline or state change, then we must compare the current and prior draw's PaintOrders
   *     // to determine if the draws overlap. If they do, we must inject a flush between them such that
   *     // the barrier addition and draw commands are ordered correctly.
   *     CompressedPaintersOrder priorDrawPaintOrder {};
   *
   *     // Accumulate rough pixel area touched by each pipeline as we iterate the SortKeys
   *     drawPass->fPipelineDrawAreas.push_back_n(fPipelineCache.count(), 0.f);
   *
   *     TextureTracker textureBindingTracker(&fTextureDataCache);
   *     for (const DrawList::SortKey& key : fSortKeys) {
   *         const DrawList::Draw& draw = key.draw();
   *         const RenderStep& renderStep = key.renderStep();
   *
   *         const bool pipelineChange = key.pipelineIndex() != lastPipeline;
   *         drawPass->fPipelineDrawAreas[key.pipelineIndex()] +=
   *                 draw.drawParams().drawBounds().area();
   *
   *         const bool uniformBindingChange = uniformTracker.writeUniforms(
   *                 fUniformDataCache, bufferMgr, key.uniformIndex());
   *
   *         // TODO(b/372953722): The Dawn and Vulkan CommandBuffer implementations currently append any
   *         // dst copy to the texture bind group/descriptor set automatically when processing a
   *         // BindTexturesAndSamplers call because they use a single group to contain all textures.
   *         // However, from the DrawPass POV, we can run into the scenario where two pipelines have the
   *         // same textures+samplers except one requires a dst-copy and the other does not. In this
   *         // case we wouldn't necessarily insert a new command when the pipeline changed and then
   *         // end up with layout validation errors.
   *         const bool textureBindingsChange = textureBindingTracker.setCurrentTextureBindings(
   *                 key.textureBindingIndex()) ||
   *                 (rebindTexturesOnPipelineChange && pipelineChange &&
   *                  key.textureBindingIndex() != TextureDataCache::kInvalidIndex);
   *
   *         std::optional<SkIRect> newScissor =
   *                 renderStep.getScissor(draw.drawParams(), lastScissor, targetBounds);
   *
   *         const bool stateChange = uniformBindingChange  || textureBindingsChange ||
   *                                  newScissor.has_value();
   *
   *         // Update DrawWriter *before* we actually change any state so that accumulated draws from
   *         // the previous state use the proper state.
   *         if (pipelineChange) {
   *             drawWriter.newPipelineState(renderStep.primitiveType(),
   *                                         renderStep.staticDataStride(),
   *                                         renderStep.appendDataStride(),
   *                                         renderStep.getRenderStateFlags(),
   *                                         draw.barrierBeforeDraws());
   *         } else if (stateChange) {
   *             drawWriter.newDynamicState();
   *         } else if (draw.barrierBeforeDraws() != BarrierType::kNone &&
   *                    priorDrawPaintOrder != draw.drawParams().order().paintOrder()) {
   *             // Even if there is no pipeline or state change, we must consider whether a
   *             // DrawPassCommand to add barriers must be inserted before any draw commands. If so,
   *             // then determine if the current and prior draws overlap (ie, their PaintOrders are
   *             // unequal). If so, perform a flush() to make sure the draw and add barrier commands are
   *             // appended to the command list in the proper order.
   *             drawWriter.flush();
   *         }
   *
   *         // Make state changes before accumulating new draw data
   *         if (pipelineChange) {
   *             drawPass->fCommandList.bindGraphicsPipeline(key.pipelineIndex());
   *             lastPipeline = key.pipelineIndex();
   *         }
   *         if (stateChange) {
   *             if (uniformBindingChange) {
   *                 uniformTracker.bindUniforms(UniformSlot::kCombinedUniforms, &drawPass->fCommandList);
   *             }
   *             if (textureBindingsChange) {
   *                 textureBindingTracker.bindTextures(&drawPass->fCommandList);
   *             }
   *             if (newScissor.has_value()) {
   *                 drawPass->fCommandList.setScissor(*newScissor);
   *                 lastScissor = *newScissor;
   *             }
   *         }
   *
   *         uint32_t uniformSsboIndex = useStorageBuffers ? uniformTracker.ssboIndex() : 0;
   *         renderStep.writeVertices(&drawWriter, draw.drawParams(), uniformSsboIndex);
   *
   *         if (bufferMgr->hasMappingFailed()) {
   *             SKGPU_LOG_W("Failed to write necessary vertex/instance data for DrawPass, dropping!");
   *             return nullptr;
   *         }
   *
   *         // Update priorDrawPaintOrder value before iterating to analyze the next draw.
   *         priorDrawPaintOrder = draw.drawParams().order().paintOrder();
   *     }
   *     // Finish recording draw calls for any collected data still pending at end of the loop
   *     drawWriter.flush();
   *
   *     drawPass->fBounds = fPassBounds.roundOut().asSkIRect();
   *     drawPass->fPipelineDescs   = fPipelineCache.detach();
   *     drawPass->fSampledTextures = fTextureDataCache.detachTextures();
   *
   *     TRACE_COUNTER1("skia.gpu", "# pipelines", drawPass->fPipelineDescs.size());
   *     TRACE_COUNTER1("skia.gpu", "# textures", drawPass->fSampledTextures.size());
   *     TRACE_COUNTER1("skia.gpu", "# commands", drawPass->fCommandList.count());
   *
   *     this->reset(LoadOp::kLoad);
   *
   *     return drawPass;
   * }
   * ```
   */
  public fun snapDrawPass(
    recorder: Recorder?,
    target: SkSp<TextureProxy>,
    targetInfo: SkImageInfo,
    dstReadStrategy: DstReadStrategy,
  ): Int {
    TODO("Implement snapDrawPass")
  }

  /**
   * C++ original:
   * ```cpp
   * int renderStepCount() const { return fRenderStepCount; }
   * ```
   */
  public fun renderStepCount(): Int {
    TODO("Implement renderStepCount")
  }

  /**
   * C++ original:
   * ```cpp
   * bool modifiesTarget() const {
   *         return this->renderStepCount() > 0 || fLoadOp == LoadOp::kClear;
   *     }
   * ```
   */
  public fun modifiesTarget(): Boolean {
    TODO("Implement modifiesTarget")
  }

  /**
   * C++ original:
   * ```cpp
   * bool samplesTexture(const TextureProxy* texture) const {
   *         return fTextureDataCache.hasTexture(texture);
   *     }
   * ```
   */
  public fun samplesTexture(texture: TextureProxy?): Boolean {
    TODO("Implement samplesTexture")
  }

  /**
   * C++ original:
   * ```cpp
   * void DrawList::reset(LoadOp loadOp, SkColor4f color) {
   *     fLoadOp = loadOp;
   *     fClearColor = color.premul().array();
   *
   *     fSortKeys.clear();
   *     fDraws.reset();
   *     fTransforms.reset();
   *
   *     // Accumulate renderer information for each draw added to this list
   *     fRenderStepCount = 0;
   *     fRequiresMSAA = false;
   *     fDepthStencilFlags = DepthStencilFlags::kNone;
   *     SkDEBUGCODE(fCoverageMaskShapeDrawCount = 0);
   *
   *     fDstReadBounds = Rect::InfiniteInverted();
   *     fPassBounds = Rect::InfiniteInverted();
   *
   *     fUniformDataCache.reset();
   *     fTextureDataCache.reset();
   *     fPipelineCache.reset();
   * }
   * ```
   */
  public fun reset(loadOp: Int, color: Int) {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * const Rect& dstReadBounds() const { return fDstReadBounds; }
   * ```
   */
  public fun dstReadBounds(): Int {
    TODO("Implement dstReadBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * const Rect& passBounds() const { return fPassBounds; }
   * ```
   */
  public fun passBounds(): Int {
    TODO("Implement passBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * bool drawsReadDst() const { return !fDstReadBounds.isEmptyNegativeOrNaN(); }
   * ```
   */
  public fun drawsReadDst(): Boolean {
    TODO("Implement drawsReadDst")
  }

  /**
   * C++ original:
   * ```cpp
   * bool drawsRequireMSAA() const { return fRequiresMSAA; }
   * ```
   */
  public fun drawsRequireMSAA(): Boolean {
    TODO("Implement drawsRequireMSAA")
  }

  /**
   * C++ original:
   * ```cpp
   * SkEnumBitMask<DepthStencilFlags> depthStencilFlags() const { return fDepthStencilFlags; }
   * ```
   */
  public fun depthStencilFlags(): Int {
    TODO("Implement depthStencilFlags")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDEBUGCODE(bool hasCoverageMaskDraws() const { return fCoverageMaskShapeDrawCount > 0; })
   * ```
   */
  public fun skDEBUGCODE(param0: () -> Boolean): Int {
    TODO("Implement skDEBUGCODE")
  }

  /**
   * C++ original:
   * ```cpp
   * const Transform& DrawList::deduplicateTransform(const Transform& localToDevice) {
   *     // TODO: This is a pretty simple deduplication strategy and doesn't take advantage of the stack
   *     // knowledge that Device has.
   *     if (fTransforms.empty() || fTransforms.back() != localToDevice) {
   *         fTransforms.push_back(localToDevice);
   *     }
   *     return fTransforms.back();
   * }
   * ```
   */
  private fun deduplicateTransform(localToDevice: Transform): Int {
    TODO("Implement deduplicateTransform")
  }

  public data class Draw public constructor(
    private val fRenderer: Renderer?,
    private var fDrawParams: Int,
    private var fBarrierBeforeDraws: Int,
  ) {
    public fun renderer(): Renderer {
      TODO("Implement renderer")
    }

    public fun drawParams(): Int {
      TODO("Implement drawParams")
    }

    public fun barrierBeforeDraws(): Int {
      TODO("Implement barrierBeforeDraws")
    }
  }

  public open class Bitfield {
    public companion object {
      private val kMask: Int = TODO("Initialize kMask")

      private val kOffset: Int = TODO("Initialize kOffset")

      private val kBits: Int = TODO("Initialize kBits")

      private fun `get`(v: ULong): Int {
        TODO("Implement get")
      }

      private fun `set`(v: UInt): Int {
        TODO("Implement set")
      }
    }
  }

  public data class SortKey public constructor(
    private var fPipelineKey: Int,
    private var fUniformKey: Int,
    private val fDraw: Draw?,
  ) {
    public operator fun compareTo(k: undefined.SortKey): Int {
      TODO("Implement compareTo")
    }

    public fun renderStep(): Int {
      TODO("Implement renderStep")
    }

    public fun draw(): Draw {
      TODO("Implement draw")
    }

    public fun pipelineIndex(): Int {
      TODO("Implement pipelineIndex")
    }

    public fun uniformIndex(): Int {
      TODO("Implement uniformIndex")
    }

    public fun textureBindingIndex(): Int {
      TODO("Implement textureBindingIndex")
    }
  }

  public companion object {
    public val kMaxRenderSteps: Int = TODO("Initialize kMaxRenderSteps")
  }
}
