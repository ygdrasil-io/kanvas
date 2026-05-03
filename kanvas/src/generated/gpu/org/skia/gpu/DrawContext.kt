package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkEnumBitMask
import org.skia.foundation.SkColorInfo
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkSp
import org.skia.foundation.SkSurfaceProps
import org.skia.math.SkIRect
import org.skia.math.SkISize
import undefined.SkColor4f

/**
 * C++ original:
 * ```cpp
 * class DrawContext final : public SkRefCnt {
 * public:
 *     static sk_sp<DrawContext> Make(const Caps* caps,
 *                                    sk_sp<TextureProxy> target,
 *                                    SkISize deviceSize,
 *                                    const SkColorInfo&,
 *                                    const SkSurfaceProps&);
 *
 *     ~DrawContext() override;
 *
 *     const SkImageInfo& imageInfo() const  { return fImageInfo;             }
 *     const SkColorInfo& colorInfo() const  { return fImageInfo.colorInfo(); }
 *     TextureProxy* target()                { return fTarget.get();          }
 *     const TextureProxy* target()    const { return fTarget.get();          }
 *     sk_sp<TextureProxy> refTarget() const { return fTarget;                }
 *
 *     // May be null if the target is not texturable.
 *     const TextureProxyView& readSurfaceView() const { return fReadView; }
 *
 *     const SkSurfaceProps& surfaceProps() const { return fSurfaceProps; }
 *
 *     int pendingRenderSteps() const { return fPendingDraws->renderStepCount(); }
 *
 *     bool modifiesTarget() const { return fPendingDraws->modifiesTarget(); }
 *
 *     bool readsTexture(const TextureProxy*) const;
 *
 *     void clear(const SkColor4f& clearColor);
 *     void discard();
 *
 *     void recordDraw(const Renderer* renderer,
 *                     const Transform& localToDevice,
 *                     const Geometry& geometry,
 *                     const Clip& clip,
 *                     DrawOrder ordering,
 *                     UniquePaintParamsID paintID,
 *                     SkEnumBitMask<DstUsage> dstUsage,
 *                     PipelineDataGatherer* gatherer,
 *                     const StrokeStyle* stroke);
 *
 *     bool recordUpload(Recorder* recorder,
 *                       sk_sp<TextureProxy> targetProxy,
 *                       const SkColorInfo& srcColorInfo,
 *                       const SkColorInfo& dstColorInfo,
 *                       const UploadSource& source,
 *                       const SkIRect& dstRect,
 *                       std::unique_ptr<ConditionalUploadContext>);
 *
 *     // Add a Task that will be executed *before* any of the pending draws and uploads are
 *     // executed as part of the next flush().
 *     void recordDependency(sk_sp<Task>);
 *
 *     // Returns the transient path atlas that uses compute to accumulate coverage masks for atlas
 *     // draws recorded to this SDC. The atlas gets created lazily upon request. Returns nullptr
 *     // if compute path generation is not supported.
 *     PathAtlas* getComputePathAtlas(Recorder*);
 *
 *     // Moves all accumulated pending recorded operations (draws and uploads), and any other
 *     // dependent tasks into the DrawTask currently being built.
 *     void flush(Recorder*);
 *
 *     // Returns the current DrawTask to the caller, so all pending draws and uploads (if flush()
 *     // was not immediately called prior to this) and subsequently recorded draws and uploads will
 *     // go into a new DrawTask.
 *     sk_sp<Task> snapDrawTask();
 *
 *     // Returns the dst read strategy to use when/if a paint requires a dst read
 *     DstReadStrategy dstReadStrategy() const { return fDstReadStrategy; }
 *
 * private:
 *     DrawContext(const Caps*, sk_sp<TextureProxy>, const SkImageInfo&, const SkSurfaceProps&);
 *
 *     void resetForClearOrDiscard();
 *
 *     sk_sp<TextureProxy> fTarget;
 *     TextureProxyView fReadView;
 *     SkImageInfo fImageInfo;
 *     const SkSurfaceProps fSurfaceProps;
 *
 *     // Does *not* reflect whether a dst read is needed by the DrawLists - simply specifies the
 *     // strategies to use should any encountered paint require it.
 *     const DstReadStrategy fDstReadStrategy;
 *     const bool fSupportsHardwareAdvancedBlend;
 *     const bool fAdvancedBlendsRequireBarrier;
 *
 *     // The in-progress DrawTask that will be snapped and returned when some external requirement
 *     // must depend on the contents of this DrawContext's target. As higher-level Skia operations
 *     // are recorded, it can be necessary to flush pending draws and uploads into the task list.
 *     // This provides a place to reset scratch textures or buffers as their previous state will have
 *     // been consumed by the flushed tasks rendering to this DrawContext's target.
 *     sk_sp<DrawTask> fCurrentDrawTask;
 *
 *     // Stores the most immediately recorded draws and uploads into the DrawContext's target. These
 *     // are collected outside of the DrawTask so that encoder switches can be minimized when
 *     // flushing.
 *     std::unique_ptr<DrawList> fPendingDraws;
 *     std::unique_ptr<UploadList> fPendingUploads;
 *
 *     // Accumulates atlas coverage masks generated by compute dispatches that are required by one or
 *     // more entries in `fPendingDraws`. When pending draws are snapped into a new DrawPass, a
 *     // compute dispatch group gets recorded which schedules the accumulated masks to get drawn into
 *     // an atlas texture. The accumulated masks are then cleared which frees up the atlas for
 *     // future draws.
 *     //
 *     // TODO: Currently every PathAtlas contains a single texture. If multiple snapped draw
 *     // passes resulted in multiple ComputePathAtlas dispatch groups, the later dispatches would
 *     // overwrite the atlas texture since all compute tasks are scheduled before render tasks. This
 *     // is currently not an issue since there is only one DrawPass per flush but we may want to
 *     // either support one atlas texture per DrawPass or record the dispatches once per
 *     // RenderPassTask rather than DrawPass.
 *     std::unique_ptr<ComputePathAtlas> fComputePathAtlas;
 * }
 * ```
 */
public class DrawContext public constructor(
  param0: Caps,
  param1: SkSp<TextureProxy>,
  param2: SkImageInfo,
  param3: SkSurfaceProps,
) : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<TextureProxy> fTarget
   * ```
   */
  private var fTarget: Int = TODO("Initialize fTarget")

  /**
   * C++ original:
   * ```cpp
   * TextureProxyView fReadView
   * ```
   */
  private var fReadView: Int = TODO("Initialize fReadView")

  /**
   * C++ original:
   * ```cpp
   * SkImageInfo fImageInfo
   * ```
   */
  private var fImageInfo: Int = TODO("Initialize fImageInfo")

  /**
   * C++ original:
   * ```cpp
   * const SkSurfaceProps fSurfaceProps
   * ```
   */
  private val fSurfaceProps: Int = TODO("Initialize fSurfaceProps")

  /**
   * C++ original:
   * ```cpp
   * const DstReadStrategy fDstReadStrategy
   * ```
   */
  private val fDstReadStrategy: Int = TODO("Initialize fDstReadStrategy")

  /**
   * C++ original:
   * ```cpp
   * const bool fSupportsHardwareAdvancedBlend
   * ```
   */
  private val fSupportsHardwareAdvancedBlend: Boolean =
      TODO("Initialize fSupportsHardwareAdvancedBlend")

  /**
   * C++ original:
   * ```cpp
   * const bool fAdvancedBlendsRequireBarrier
   * ```
   */
  private val fAdvancedBlendsRequireBarrier: Boolean =
      TODO("Initialize fAdvancedBlendsRequireBarrier")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<DrawTask> fCurrentDrawTask
   * ```
   */
  private var fCurrentDrawTask: Int = TODO("Initialize fCurrentDrawTask")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<DrawList> fPendingDraws
   * ```
   */
  private var fPendingDraws: Int = TODO("Initialize fPendingDraws")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<UploadList> fPendingUploads
   * ```
   */
  private var fPendingUploads: Int = TODO("Initialize fPendingUploads")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<ComputePathAtlas> fComputePathAtlas
   * ```
   */
  private var fComputePathAtlas: Int = TODO("Initialize fComputePathAtlas")

  /**
   * C++ original:
   * ```cpp
   * DrawContext(const Caps*, sk_sp<TextureProxy>, const SkImageInfo&, const SkSurfaceProps&)
   * ```
   */
  public constructor(
    caps: Caps?,
    target: SkSp<TextureProxy>,
    ii: SkImageInfo,
    props: SkSurfaceProps,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkImageInfo& imageInfo() const  { return fImageInfo;             }
   * ```
   */
  public fun imageInfo(): Int {
    TODO("Implement imageInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkColorInfo& colorInfo() const  { return fImageInfo.colorInfo(); }
   * ```
   */
  public fun colorInfo(): Int {
    TODO("Implement colorInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * TextureProxy* target()                { return fTarget.get();          }
   * ```
   */
  public fun target(): Int {
    TODO("Implement target")
  }

  /**
   * C++ original:
   * ```cpp
   * const TextureProxy* target()    const { return fTarget.get();          }
   * ```
   */
  public fun refTarget(): Int {
    TODO("Implement refTarget")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<TextureProxy> refTarget() const { return fTarget;                }
   * ```
   */
  public fun readSurfaceView(): Int {
    TODO("Implement readSurfaceView")
  }

  /**
   * C++ original:
   * ```cpp
   * const TextureProxyView& readSurfaceView() const { return fReadView; }
   * ```
   */
  public fun surfaceProps(): Int {
    TODO("Implement surfaceProps")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkSurfaceProps& surfaceProps() const { return fSurfaceProps; }
   * ```
   */
  public fun pendingRenderSteps(): Int {
    TODO("Implement pendingRenderSteps")
  }

  /**
   * C++ original:
   * ```cpp
   * int pendingRenderSteps() const { return fPendingDraws->renderStepCount(); }
   * ```
   */
  public fun modifiesTarget(): Boolean {
    TODO("Implement modifiesTarget")
  }

  /**
   * C++ original:
   * ```cpp
   * bool modifiesTarget() const { return fPendingDraws->modifiesTarget(); }
   * ```
   */
  public fun readsTexture(texture: TextureProxy?): Boolean {
    TODO("Implement readsTexture")
  }

  /**
   * C++ original:
   * ```cpp
   * bool DrawContext::readsTexture(const TextureProxy* texture) const {
   *     if (fPendingDraws->samplesTexture(texture)) {
   *         return true;
   *     }
   *
   *     // visitProxies() before calling prepareResources() can revisit tasks in the general case
   *     // (e.g. processing everything in the root task list). In this case, the only tasks being
   *     // visited are pending tasks so their graph complexity should be minimal.
   *     bool notFound = fCurrentDrawTask->visitProxies(
   *         [texture](const TextureProxy* other) {
   *             // Return true to continue visiting, i.e. when we haven't found `texture` yet.
   *             return texture != other;
   *         }, /*readsOnly=*/true);
   *
   *     return !notFound; // double negation means its found in a pending child task
   * }
   * ```
   */
  public fun clear(clearColor: SkColor4f) {
    TODO("Implement clear")
  }

  /**
   * C++ original:
   * ```cpp
   * void DrawContext::clear(const SkColor4f& clearColor) {
   *     this->resetForClearOrDiscard();
   *     fPendingDraws->reset(LoadOp::kClear, clearColor);
   * }
   * ```
   */
  public fun discard() {
    TODO("Implement discard")
  }

  /**
   * C++ original:
   * ```cpp
   * void DrawContext::discard() {
   *     this->resetForClearOrDiscard();
   *     fPendingDraws->reset(LoadOp::kDiscard);
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
    gatherer: PipelineDataGatherer?,
    stroke: StrokeStyle?,
  ) {
    TODO("Implement recordDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void DrawContext::recordDraw(const Renderer* renderer,
   *                              const Transform& localToDevice,
   *                              const Geometry& geometry,
   *                              const Clip& clip,
   *                              DrawOrder ordering,
   *                              UniquePaintParamsID paintID,
   *                              SkEnumBitMask<DstUsage> dstUsage,
   *                              PipelineDataGatherer* gatherer,
   *                              const StrokeStyle* stroke) {
   *     SkASSERTF(SkIRect::MakeSize(this->imageInfo().dimensions()).contains(clip.scissor()),
   *               "Image %dx%d, scissor %d,%d,%d,%d",
   *               this->imageInfo().width(), this->imageInfo().height(),
   *               clip.scissor().left(), clip.scissor().top(),
   *               clip.scissor().right(), clip.scissor().bottom());
   *
   *     // Determine whether a draw requies a barrier
   *     BarrierType barrierBeforeDraws = BarrierType::kNone;
   *     if (fDstReadStrategy == DstReadStrategy::kReadFromInput &&
   *         (dstUsage & DstUsage::kDstReadRequired)) {
   *         barrierBeforeDraws = BarrierType::kReadDstFromInput;
   *     }
   *     if ((dstUsage & DstUsage::kAdvancedBlend) &&
   *         fSupportsHardwareAdvancedBlend && fAdvancedBlendsRequireBarrier) {
   *         // A draw should only read from the dst OR use hardware for advanced blend modes.
   *         SkASSERT(!(dstUsage & DstUsage::kDstReadRequired));
   *         barrierBeforeDraws = BarrierType::kAdvancedNoncoherentBlend;
   *     }
   *
   *     fPendingDraws->recordDraw(renderer, localToDevice, geometry, clip, ordering, paintID, dstUsage,
   *                               barrierBeforeDraws, gatherer, stroke);
   * }
   * ```
   */
  public fun recordUpload(
    recorder: Recorder?,
    targetProxy: SkSp<TextureProxy>,
    srcColorInfo: SkColorInfo,
    dstColorInfo: SkColorInfo,
    source: UploadSource,
    dstRect: SkIRect,
    condContext: ConditionalUploadContext?,
  ): Boolean {
    TODO("Implement recordUpload")
  }

  /**
   * C++ original:
   * ```cpp
   * bool DrawContext::recordUpload(Recorder* recorder,
   *                                sk_sp<TextureProxy> targetProxy,
   *                                const SkColorInfo& srcColorInfo,
   *                                const SkColorInfo& dstColorInfo,
   *                                const UploadSource& source,
   *                                const SkIRect& dstRect,
   *                                std::unique_ptr<ConditionalUploadContext> condContext) {
   *     // Our caller should have clipped to the bounds of the surface already.
   *     SkASSERT(targetProxy->isFullyLazy() ||
   *              SkIRect::MakeSize(targetProxy->dimensions()).contains(dstRect));
   *     SkASSERT(source.isValid());
   *     return fPendingUploads->recordUpload(recorder,
   *                                          std::move(targetProxy),
   *                                          srcColorInfo,
   *                                          dstColorInfo,
   *                                          source,
   *                                          dstRect,
   *                                          std::move(condContext));
   * }
   * ```
   */
  public fun recordDependency(task: SkSp<Task>) {
    TODO("Implement recordDependency")
  }

  /**
   * C++ original:
   * ```cpp
   * void DrawContext::recordDependency(sk_sp<Task> task) {
   *     SkASSERT(task);
   *     // Adding `task` to the current DrawTask directly means that it will execute after any previous
   *     // dependent tasks and after any previous calls to flush(), but everything else that's being
   *     // collected on the DrawContext will execute after `task` once the next flush() is performed.
   *     fCurrentDrawTask->addTask(std::move(task));
   * }
   * ```
   */
  public fun getComputePathAtlas(recorder: Recorder?): PathAtlas {
    TODO("Implement getComputePathAtlas")
  }

  /**
   * C++ original:
   * ```cpp
   * PathAtlas* DrawContext::getComputePathAtlas(Recorder* recorder) {
   *     if (!fComputePathAtlas) {
   *         fComputePathAtlas = recorder->priv().atlasProvider()->createComputePathAtlas(recorder);
   *     }
   *     return fComputePathAtlas.get();
   * }
   * ```
   */
  public fun flush(recorder: Recorder?) {
    TODO("Implement flush")
  }

  /**
   * C++ original:
   * ```cpp
   * void DrawContext::flush(Recorder* recorder) {
   *     if (fPendingUploads->size() > 0) {
   *         TRACE_EVENT_INSTANT1("skia.gpu", TRACE_FUNC, TRACE_EVENT_SCOPE_THREAD,
   *                              "# uploads", fPendingUploads->size());
   *         fCurrentDrawTask->addTask(UploadTask::Make(fPendingUploads.get()));
   *         // The UploadTask steals the collected upload instances, automatically resetting this list
   *         SkASSERT(fPendingUploads->size() == 0);
   *     }
   *
   *     // Generate compute dispatches that render into the atlas texture used by pending draws.
   *     // TODO: Once compute atlas caching is implemented, DrawContext might not hold onto to this
   *     // at which point a recordDispatch() could be added and it stores a pending dispatches list that
   *     // much like how uploads are handled. In that case, Device would be responsible for triggering
   *     // the recording of dispatches, but that may happen naturally in AtlasProvider::recordUploads().
   *     if (fComputePathAtlas) {
   *         ComputeTask::DispatchGroupList dispatches;
   *         if (fComputePathAtlas->recordDispatches(recorder, &dispatches)) {
   *             // For now this check is valid as all coverage mask draws involve dispatches
   *             SkASSERT(fPendingDraws->hasCoverageMaskDraws());
   *
   *             fCurrentDrawTask->addTask(ComputeTask::Make(std::move(dispatches)));
   *         } // else no pending compute work needed to be recorded
   *
   *         fComputePathAtlas->reset();
   *     } // else platform doesn't support compute or atlas was never initialized.
   *
   *     if (!fPendingDraws->modifiesTarget()) {
   *         // Nothing will be rasterized to the target that warrants a RenderPassTask, but we preserve
   *         // any added uploads or compute tasks since those could also affect the target w/o
   *         // rasterizing anything directly.
   *         return;
   *     }
   *
   *     // Extract certain properties from DrawList relevant for DrawTask construction before
   *     // relinquishing the pending draw list to the DrawPass constructor.
   *     SkIRect dstReadPixelBounds = fPendingDraws->dstReadBounds().makeRoundOut().asSkIRect();
   *     const bool drawsRequireMSAA = fPendingDraws->drawsRequireMSAA();
   *     const SkEnumBitMask<DepthStencilFlags> dsFlags = fPendingDraws->depthStencilFlags();
   *     // Determine the optimal dst read strategy for the drawpass given pending draw characteristics
   *     const DstReadStrategy drawPassDstReadStrategy = fPendingDraws->drawsReadDst()
   *                                                             ? this->dstReadStrategy()
   *                                                             : DstReadStrategy::kNoneRequired;
   *
   *     // Convert the pending draws and load/store ops into a DrawPass that will be executed after
   *     // the collected uploads and compute dispatches.
   *     // TODO: At this point, there's only ever one DrawPass in a RenderPassTask to a target. When
   *     // subpasses are implemented, they will either be collected alongside fPendingDraws or added
   *     // to the RenderPassTask separately.
   *     std::unique_ptr<DrawPass> pass = fPendingDraws->snapDrawPass(recorder,
   *                                                                  fTarget,
   *                                                                  this->imageInfo(),
   *                                                                  drawPassDstReadStrategy);
   *     SkASSERT(!fPendingDraws->modifiesTarget()); // Should be drained into `pass`.
   *
   *     if (pass) {
   *         SkASSERT(fTarget.get() == pass->target());
   *
   *         // If any paint used within the DrawPass reads from the dst texture (indicated by nonempty
   *         // dstReadPixelBounds) and the dstReadStrategy is kTextureCopy, then add a CopyTask.
   *         sk_sp<TextureProxy> dstCopy;
   *         if (!dstReadPixelBounds.isEmpty() &&
   *             drawPassDstReadStrategy == DstReadStrategy::kTextureCopy) {
   *             TRACE_EVENT_INSTANT0("skia.gpu", "DrawPass requires dst copy",
   *                                  TRACE_EVENT_SCOPE_THREAD);
   *             sk_sp<Image> imageCopy = Image::Copy(
   *                     recorder,
   *                     this,
   *                     fReadView,
   *                     fImageInfo.colorInfo(),
   *                     dstReadPixelBounds,
   *                     Budgeted::kYes,
   *                     Mipmapped::kNo,
   *                     SkBackingFit::kApprox,
   *                     "DstCopy");
   *             if (!imageCopy) {
   *                 SKGPU_LOG_W("DrawContext::flush Image::Copy failed, draw pass dropped!");
   *                 return;
   *             }
   *             dstCopy = imageCopy->textureProxyView().refProxy();
   *             SkASSERT(dstCopy);
   *         }
   *
   *         const Caps* caps = recorder->priv().caps();
   *         auto [loadOp, storeOp] = pass->ops();
   *         auto writeSwizzle = caps->getWriteSwizzle(this->colorInfo().colorType(),
   *                                                   fTarget->textureInfo());
   *
   *         RenderPassDesc desc = RenderPassDesc::Make(caps, fTarget->textureInfo(), loadOp, storeOp,
   *                                                    dsFlags,
   *                                                    pass->clearColor(),
   *                                                    drawsRequireMSAA,
   *                                                    writeSwizzle,
   *                                                    drawPassDstReadStrategy);
   *
   *         RenderPassTask::DrawPassList passes;
   *         passes.emplace_back(std::move(pass));
   *         fCurrentDrawTask->addTask(RenderPassTask::Make(std::move(passes), desc, fTarget,
   *                                                        std::move(dstCopy), dstReadPixelBounds));
   *         if (fTarget->mipmapped() == Mipmapped::kYes) {
   *             if (!GenerateMipmaps(recorder, this, fTarget, fImageInfo.colorInfo())) {
   *                 SKGPU_LOG_W("DrawContext::flush GenerateMipmaps failed, draw pass dropped!");
   *                 return;
   *             }
   *         }
   *     }
   *     // else pass creation failed, DrawPass will have logged why. Don't discard the previously
   *     // accumulated tasks, however, since they may represent operations on an atlas that other
   *     // DrawContexts now implicitly depend on.
   * }
   * ```
   */
  public fun snapDrawTask(): Int {
    TODO("Implement snapDrawTask")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<Task> DrawContext::snapDrawTask() {
   *     if (!fCurrentDrawTask->hasTasks()) {
   *         return nullptr;
   *     }
   *
   *     sk_sp<Task> snappedTask = std::move(fCurrentDrawTask);
   *     fCurrentDrawTask = sk_make_sp<DrawTask>(fTarget);
   *     return snappedTask;
   * }
   * ```
   */
  public fun dstReadStrategy(): Int {
    TODO("Implement dstReadStrategy")
  }

  /**
   * C++ original:
   * ```cpp
   * DstReadStrategy dstReadStrategy() const { return fDstReadStrategy; }
   * ```
   */
  private fun resetForClearOrDiscard() {
    TODO("Implement resetForClearOrDiscard")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<DrawContext> DrawContext::Make(const Caps* caps,
     *                                      sk_sp<TextureProxy> target,
     *                                      SkISize deviceSize,
     *                                      const SkColorInfo& colorInfo,
     *                                      const SkSurfaceProps& props) {
     *     if (!target) {
     *         return nullptr;
     *     }
     *     // We don't render to unknown or unpremul alphatypes
     *     if (colorInfo.alphaType() == kUnknown_SkAlphaType ||
     *         colorInfo.alphaType() == kUnpremul_SkAlphaType) {
     *         return nullptr;
     *     }
     *     if (!caps->isRenderable(target->textureInfo())) {
     *         return nullptr;
     *     }
     *     if (!caps->areColorTypeAndTextureInfoCompatible(colorInfo.colorType(), target->textureInfo())) {
     *         return nullptr;
     *     }
     *
     *     // Accept an approximate-fit texture, but make sure it's at least as large as the device's
     *     // logical size.
     *     // TODO: validate that the alpha type is compatible with the target's info
     *     SkASSERT(target->isFullyLazy() || (target->dimensions().width() >= deviceSize.width() &&
     *                                        target->dimensions().height() >= deviceSize.height()));
     *     SkImageInfo imageInfo = SkImageInfo::Make(deviceSize, colorInfo);
     *     return sk_sp<DrawContext>(new DrawContext(caps, std::move(target), imageInfo, props));
     * }
     * ```
     */
    public fun make(
      caps: Caps?,
      target: SkSp<TextureProxy>,
      deviceSize: SkISize,
      colorInfo: SkColorInfo,
      props: SkSurfaceProps,
    ): Int {
      TODO("Implement make")
    }
  }
}
