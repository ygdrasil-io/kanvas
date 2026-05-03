package org.skia.gpu

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit
import kotlin.time.Duration
import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.core.SkTraceMemoryDump
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPixmap
import org.skia.foundation.SkRecorder
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class SK_API Recorder final : public SkRecorder {
 * public:
 *     Recorder(const Recorder&) = delete;
 *     Recorder(Recorder&&) = delete;
 *     Recorder& operator=(const Recorder&) = delete;
 *     Recorder& operator=(Recorder&&) = delete;
 *
 *     ~Recorder() override;
 *
 *     BackendApi backend() const;
 *
 *     Type type() const override { return SkRecorder::Type::kGraphite; }
 *     skcpu::Recorder* cpuRecorder() override;
 *
 *     std::unique_ptr<Recording> snap();
 *
 *     ImageProvider* clientImageProvider() { return fClientImageProvider.get(); }
 *     const ImageProvider* clientImageProvider() const { return fClientImageProvider.get(); }
 *
 *     /**
 *      * Gets the maximum supported texture size.
 *      */
 *     int maxTextureSize() const;
 *
 *     /**
 *      * Creates a new backend gpu texture matching the dimensions and TextureInfo. If an invalid
 *      * TextureInfo or a TextureInfo Skia can't support is passed in, this will return an invalid
 *      * BackendTexture. Thus the client should check isValid on the returned BackendTexture to know
 *      * if it succeeded or not.
 *      *
 *      * If this does return a valid BackendTexture, the caller is required to use
 *      * Recorder::deleteBackendTexture or Context::deleteBackendTexture to delete the texture. It is
 *      * safe to use the Context that created this Recorder or any other Recorder created from the
 *      * same Context to call deleteBackendTexture.
 *      */
 *     BackendTexture createBackendTexture(SkISize dimensions, const TextureInfo&);
 *
 * #ifdef SK_BUILD_FOR_ANDROID
 *     BackendTexture createBackendTexture(AHardwareBuffer*,
 *                                         bool isRenderable,
 *                                         bool isProtectedContent,
 *                                         SkISize dimensions,
 *                                         bool fromAndroidWindow = false) const;
 * #endif
 *
 *     /**
 *      * If possible, updates a backend texture with the provided pixmap data. The client
 *      * should check the return value to see if the update was successful. The client is required
 *      * to insert a Recording into the Context and call `submit` to send the upload work to the gpu.
 *      * The backend texture must be compatible with the provided pixmap(s). Compatible, in this case,
 *      * means that the backend format is compatible with the base pixmap's colortype. The src data
 *      * can be deleted when this call returns. When the BackendTexture is safe to be destroyed by the
 *      * client, Skia will call the passed in GpuFinishedProc. The BackendTexture should not be
 *      * destroyed before that.
 *      * If the backend texture is mip mapped, the data for all the mipmap levels must be provided.
 *      * In the mipmapped case all the colortypes of the provided pixmaps must be the same.
 *      * Additionally, all the miplevels must be sized correctly (please see
 *      * SkMipmap::ComputeLevelSize and ComputeLevelCount).
 *      * Note: the pixmap's alphatypes and colorspaces are ignored.
 *      * For the Vulkan backend after a successful update the layout of the created VkImage will be:
 *      *      VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
 *      */
 *     bool updateBackendTexture(const BackendTexture&,
 *                               const SkPixmap srcData[],
 *                               int numLevels,
 *                               GpuFinishedProc = nullptr,
 *                               GpuFinishedContext = nullptr);
 *
 *     /**
 *      * If possible, updates a compressed backend texture filled with the provided raw data. The
 *      * client should check the return value to see if the update was successful. The client is
 *      * required to insert a Recording into the Context and call `submit` to send the upload work to
 *      * the gpu. When the BackendTexture is safe to be destroyed by the client, Skia will call the
 *      * passed in GpuFinishedProc. The BackendTexture should not be destroyed before that.
 *      * If the backend texture is mip mapped, the data for all the mipmap levels must be provided.
 *      * Additionally, all the miplevels must be sized correctly (please see
 *      * SkMipMap::ComputeLevelSize and ComputeLevelCount).
 *      * For the Vulkan backend after a successful update the layout of the created VkImage will be:
 *      *      VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
 *      */
 *     bool updateCompressedBackendTexture(const BackendTexture&,
 *                                         const void* data,
 *                                         size_t dataSize,
 *                                         GpuFinishedProc = nullptr,
 *                                         GpuFinishedContext = nullptr);
 *
 *     /**
 *      * Called to delete the passed in BackendTexture. This should only be called if the
 *      * BackendTexture was created by calling Recorder::createBackendTexture on a Recorder that is
 *      * associated with the same Context. If the BackendTexture is not valid or does not match the
 *      * BackendApi of the Recorder then nothing happens.
 *      *
 *      * Otherwise this will delete/release the backend object that is wrapped in the BackendTexture.
 *      * The BackendTexture will be reset to an invalid state and should not be used again.
 *      */
 *     void deleteBackendTexture(const BackendTexture&);
 *
 *     // Adds a proc that will be moved to the Recording upon snap, subsequently attached to the
 *     // CommandBuffer when the Recording is added, and called when that CommandBuffer is submitted
 *     // and finishes. If the Recorder or Recording is deleted before the proc is added to the
 *     // CommandBuffer, it will be called with result Failure.
 *     void addFinishInfo(const InsertFinishInfo&);
 *
 *     // Returns a canvas that will record to a proxy surface, which must be instantiated on replay.
 *     // This can only be called once per Recording; subsequent calls will return null until a
 *     // Recording is snapped. Additionally, the returned SkCanvas is only valid until the next
 *     // Recording snap, at which point it is deleted.
 *     SkCanvas* makeDeferredCanvas(const SkImageInfo&, const TextureInfo&);
 *
 *     /**
 *      * Frees GPU resources created and held by the Recorder. Can be called to reduce GPU memory
 *      * pressure. Any resources that are still in use (e.g. being used by work submitted to the GPU)
 *      * will not be deleted by this call. If the caller wants to make sure all resources are freed,
 *      * then they should first make sure to submit and wait on any outstanding work.
 *      */
 *     void freeGpuResources();
 *
 *     /**
 *      * Purge GPU resources on the Recorder that haven't been used in the past 'msNotUsed'
 *      * milliseconds or are otherwise marked for deletion, regardless of whether the context is under
 *      * budget.
 *      */
 *     void performDeferredCleanup(std::chrono::milliseconds msNotUsed);
 *
 *     /**
 *      * Returns the number of bytes of the Recorder's gpu memory cache budget that are currently in
 *      * use.
 *      */
 *     size_t currentBudgetedBytes() const;
 *
 *     /**
 *      * Returns the number of bytes of the Recorder's resource cache that are currently purgeable.
 *      */
 *     size_t currentPurgeableBytes() const;
 *
 *     /**
 *      * Returns the size of Recorder's gpu memory cache budget in bytes.
 *      */
 *     size_t maxBudgetedBytes() const;
 *
 *     /**
 *      * Sets the size of Recorders's gpu memory cache budget in bytes. If the new budget is lower
 *      * than the current budget, the cache will try to free resources to get under the new budget.
 *      */
 *     void setMaxBudgetedBytes(size_t bytes);
 *
 *     /**
 *      * Enumerates all cached GPU resources owned by the Recorder and dumps their memory to
 *      * traceMemoryDump.
 *      */
 *     void dumpMemoryStatistics(SkTraceMemoryDump* traceMemoryDump) const;
 *
 *     // Provides access to functions that aren't part of the public API.
 *     RecorderPriv priv();
 *     const RecorderPriv priv() const;  // NOLINT(readability-const-return-type)
 *
 * private:
 *     static constexpr int kMaxKeyAndDataBuilders = 2;
 *
 *     friend class Context; // For ctor
 *     friend class Device; // For registering and deregistering Devices;
 *     friend class RecorderPriv; // for ctor and hidden methods
 *
 *     // If Context is non-null, the Recorder will use the Context's resource provider
 *     // instead of creating its own.
 *     Recorder(sk_sp<SharedContext>, const RecorderOptions&, const Context*);
 *
 *     SingleOwner* singleOwner() const { return &fSingleOwner; }
 *
 *     // We keep track of all Devices that are connected to a Recorder. This allows the client to
 *     // safely delete an SkSurface or a Recorder in any order. If the client deletes the Recorder
 *     // we need to notify all Devices that the Recorder is no longer valid. If we delete the
 *     // SkSurface/Device first we will flush all the Device's into the Recorder before deregistering
 *     // it from the Recorder.
 *     //
 *     // We take a ref on the Device so that ~Device() does not have to deregister the recorder
 *     // (which can happen on any thread if the Device outlives the Surface via an Image view).
 *     // Recorder::flushTrackedDevices() cleans up uniquely held and immutable Devices on the recorder
 *     // thread so this extra ref is not significantly increasing the Device lifetime.
 *     //
 *     // Note: We could probably get by with only registering Devices directly connected to
 *     // SkSurfaces. All other one off Devices will be created in a controlled scope where the
 *     // Recorder should still be valid by the time they need to flush their work when the Device is
 *     // deleted. We would have to make sure we safely handle cases where a client calls saveLayer
 *     // then either deletes the SkSurface or Recorder before calling restore. For simplicity we just
 *     // register every device for now, but if we see extra overhead in pushing back the extra
 *     // pointers, we can look into only registering SkSurface Devices.
 *     void registerDevice(sk_sp<Device>);
 *     void deregisterDevice(const Device*);
 *
 *     SkCanvas* makeCaptureCanvas(SkCanvas*) override;
 *     void createCaptureBreakpoint(SkSurface*) override;
 *
 *     sk_sp<SharedContext> fSharedContext;
 *     ResourceProvider* fResourceProvider; // May point to the Context's resource provider
 *     std::unique_ptr<ResourceProvider> fOwnedResourceProvider; // May be null
 *
 *     sk_sp<RuntimeEffectDictionary> fRuntimeEffectDict;
 *
 *     // NOTE: These are stored by pointer to allow them to be forward declared.
 *     std::unique_ptr<TaskList> fRootTaskList;
 *     // Aggregated one-time uploads that preceed all tasks in the root task list.
 *     std::unique_ptr<UploadList> fRootUploads;
 *
 *     std::unique_ptr<DrawBufferManager> fDrawBufferManager;
 *     std::unique_ptr<UploadBufferManager> fUploadBufferManager;
 *     sk_sp<FloatStorageManager> fFloatStorageManager;
 *     std::unique_ptr<ProxyReadCountMap> fProxyReadCounts;
 *
 *     skia_private::STArray<kMaxKeyAndDataBuilders, std::unique_ptr<KeyAndDataBuilder>>
 *         fKeyAndDataBuilders;
 *
 *     // Iterating over tracked devices in flushTrackedDevices() needs to be re-entrant and support
 *     // additions to fTrackedDevices if registerDevice() is triggered by a temporary device during
 *     // flushing. Removals are handled by setting elements to null; final clean up is handled at the
 *     // end of the initial call to flushTrackedDevices().
 *     skia_private::TArray<sk_sp<Device>> fTrackedDevices;
 *     int fFlushingDevicesIndex = -1;
 *
 *     uint32_t fUniqueID;  // Needed for MessageBox handling for text
 *     uint32_t fNextRecordingID = 1;
 *     const bool fRequireOrderedRecordings;
 *
 *     std::unique_ptr<AtlasProvider> fAtlasProvider;
 *     std::unique_ptr<TokenTracker> fTokenTracker;
 *     std::unique_ptr<sktext::gpu::StrikeCache> fStrikeCache;
 *     std::unique_ptr<sktext::gpu::TextBlobRedrawCoordinator> fTextBlobCache;
 *     sk_sp<ImageProvider> fClientImageProvider;
 *
 *     // In debug builds we guard against improper thread handling
 *     // This guard is passed to the ResourceCache.
 *     // TODO: Should we also pass this to Device, DrawContext, and similar classes?
 *     mutable SingleOwner fSingleOwner;
 *
 *     sk_sp<Device> fTargetProxyDevice;
 *     std::unique_ptr<SkCanvas> fTargetProxyCanvas;
 *     std::unique_ptr<Recording::LazyProxyData> fTargetProxyData;
 *
 *     skia_private::TArray<sk_sp<RefCntedCallback>> fFinishedProcs;
 *
 *     // Tracks the flushing state to ensure recursive flushing does not occur.
 *     SkDEBUGCODE(bool fIsFlushingTrackedDevices = false;)
 *
 * #if defined(GPU_TEST_UTILS)
 *     // For testing use only -- the Context used to create this Recorder
 *     Context* fContext = nullptr;
 * #endif
 *
 * #if defined(SK_DUMP_TASKS)
 *     // Traverses and dumps the task list at Recorder::snap()
 *     void dumpTasks(TaskList*) const;
 *
 *     // Log of all callers of RecorderPriv::flushTrackedDevices
 *     SkTDArray<const char*> fFlushSources;
 * #endif
 * }
 * ```
 */
public class Recorder public constructor(
  param0: Recorder,
) : SkRecorder() {
  /**
   * C++ original:
   * ```cpp
   * static constexpr int kMaxKeyAndDataBuilders = 2
   * ```
   */
  private var skSp: Recorder = TODO("Initialize skSp")

  /**
   * C++ original:
   * ```cpp
   * Recorder(sk_sp<SharedContext>, const RecorderOptions&, const Context*)
   * ```
   */
  private var fSharedContext: Int = TODO("Initialize fSharedContext")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SharedContext> fSharedContext
   * ```
   */
  private var fResourceProvider: ResourceProvider? = TODO("Initialize fResourceProvider")

  /**
   * C++ original:
   * ```cpp
   * ResourceProvider* fResourceProvider
   * ```
   */
  private var fOwnedResourceProvider: Int = TODO("Initialize fOwnedResourceProvider")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<ResourceProvider> fOwnedResourceProvider
   * ```
   */
  private var fRuntimeEffectDict: Int = TODO("Initialize fRuntimeEffectDict")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<RuntimeEffectDictionary> fRuntimeEffectDict
   * ```
   */
  private var fRootTaskList: Int = TODO("Initialize fRootTaskList")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<TaskList> fRootTaskList
   * ```
   */
  private var fRootUploads: Int = TODO("Initialize fRootUploads")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<UploadList> fRootUploads
   * ```
   */
  private var fDrawBufferManager: Int = TODO("Initialize fDrawBufferManager")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<DrawBufferManager> fDrawBufferManager
   * ```
   */
  private var fUploadBufferManager: Int = TODO("Initialize fUploadBufferManager")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<UploadBufferManager> fUploadBufferManager
   * ```
   */
  private var fFloatStorageManager: Int = TODO("Initialize fFloatStorageManager")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<FloatStorageManager> fFloatStorageManager
   * ```
   */
  private var fProxyReadCounts: Int = TODO("Initialize fProxyReadCounts")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<ProxyReadCountMap> fProxyReadCounts
   * ```
   */
  private var fKeyAndDataBuilders: Int = TODO("Initialize fKeyAndDataBuilders")

  /**
   * C++ original:
   * ```cpp
   * skia_private::STArray<kMaxKeyAndDataBuilders, std::unique_ptr<KeyAndDataBuilder>>
   *         fKeyAndDataBuilders
   * ```
   */
  private var fFlushingDevicesIndex: Int = TODO("Initialize fFlushingDevicesIndex")

  /**
   * C++ original:
   * ```cpp
   * int fFlushingDevicesIndex = -1
   * ```
   */
  private var fUniqueID: UInt = TODO("Initialize fUniqueID")

  /**
   * C++ original:
   * ```cpp
   * uint32_t fUniqueID
   * ```
   */
  private var fNextRecordingID: UInt = TODO("Initialize fNextRecordingID")

  /**
   * C++ original:
   * ```cpp
   * uint32_t fNextRecordingID = 1
   * ```
   */
  private val fRequireOrderedRecordings: Boolean = TODO("Initialize fRequireOrderedRecordings")

  /**
   * C++ original:
   * ```cpp
   * const bool fRequireOrderedRecordings
   * ```
   */
  private var fAtlasProvider: Int = TODO("Initialize fAtlasProvider")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<AtlasProvider> fAtlasProvider
   * ```
   */
  private var fTokenTracker: Int = TODO("Initialize fTokenTracker")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<TokenTracker> fTokenTracker
   * ```
   */
  private var fStrikeCache: Int = TODO("Initialize fStrikeCache")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<sktext::gpu::StrikeCache> fStrikeCache
   * ```
   */
  private var fTextBlobCache: Int = TODO("Initialize fTextBlobCache")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<sktext::gpu::TextBlobRedrawCoordinator> fTextBlobCache
   * ```
   */
  private var fClientImageProvider: Int = TODO("Initialize fClientImageProvider")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<ImageProvider> fClientImageProvider
   * ```
   */
  private var fSingleOwner: Int = TODO("Initialize fSingleOwner")

  /**
   * C++ original:
   * ```cpp
   * mutable SingleOwner fSingleOwner
   * ```
   */
  private var fTargetProxyDevice: Int = TODO("Initialize fTargetProxyDevice")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<Device> fTargetProxyDevice
   * ```
   */
  private var fTargetProxyCanvas: Int = TODO("Initialize fTargetProxyCanvas")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkCanvas> fTargetProxyCanvas
   * ```
   */
  private var fTargetProxyData: Int = TODO("Initialize fTargetProxyData")

  /**
   * C++ original:
   * ```cpp
   * Recorder(const Recorder&) = delete
   * ```
   */
  public constructor(
    sharedContext: SkSp<SharedContext>,
    options: RecorderOptions,
    context: Context?,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * Recorder& operator=(const Recorder&) = delete
   * ```
   */
  public fun assign(param0: Recorder) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * Recorder& operator=(Recorder&&) = delete
   * ```
   */
  public fun backend(): BackendApi {
    TODO("Implement backend")
  }

  /**
   * C++ original:
   * ```cpp
   * BackendApi Recorder::backend() const { return fSharedContext->backend(); }
   * ```
   */
  public override fun type(): Int {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * Type type() const override { return SkRecorder::Type::kGraphite; }
   * ```
   */
  public override fun cpuRecorder(): Int {
    TODO("Implement cpuRecorder")
  }

  /**
   * C++ original:
   * ```cpp
   * skcpu::Recorder* Recorder::cpuRecorder() {
   *     return skcpu::Recorder::TODO();
   * }
   * ```
   */
  public fun snap(): Recorder? {
    TODO("Implement snap")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<Recording> Recorder::snap() {
   *     TRACE_EVENT0_ALWAYS("skia.gpu", TRACE_FUNC);
   *     ASSERT_SINGLE_OWNER
   *
   *     if (fTargetProxyData) {
   *         // Normally devices are marked immutable when their owning Surface goes away, but the
   *         // deferred canvas+device do not have a surface so mimic that operation. Do this before
   *         // flushing all other tracked devices to avoid a redundant flush.
   *         fTargetProxyDevice->setImmutable();
   *         fTargetProxyDevice.reset();
   *         fTargetProxyCanvas.reset();
   *     }
   *     // Collect all pending tasks on the deferred recording canvas and any other tracked device.
   *     this->priv().flushTrackedDevices(SK_DUMP_TASKS_CODE("Recorder::Snap"));
   *
   *     // The scratch resources only need to be tracked until prepareResources() is finished, so
   *     // Recorder doesn't hold a persistent manager and it can be deleted when snap() returns.
   *     ScratchResourceManager scratchManager{fResourceProvider, std::move(fProxyReadCounts)};
   *     std::unique_ptr<Recording> recording(new Recording(fNextRecordingID++,
   *                                                        fRequireOrderedRecordings ? fUniqueID
   *                                                                                  : SK_InvalidGenID,
   *                                                        std::move(fTargetProxyData),
   *                                                        std::move(fFinishedProcs)));
   *     // Allow the buffer managers to add any collected tasks for data transfer or initialization
   *     // before moving the root task list to the Recording.
   *     bool valid = fFloatStorageManager->finalize(fDrawBufferManager.get());
   *     valid &= fDrawBufferManager->transferToRecording(recording.get());
   *
   *     // We create the Recording's full task list even if the DrawBufferManager failed because it is
   *     // a convenient way to ensure everything else is unmapped and reset for the next Recording.
   *     fUploadBufferManager->transferToRecording(recording.get());
   *     // Add one task for all root uploads before the rest of the rendering tasks might depend on them
   *     if (fRootUploads->size() > 0) {
   *         sk_sp<Task> uploadTask = UploadTask::Make(fRootUploads.get());
   *
   *         // If we are dumping tasks, we want to be able to associate each task with the current flush
   *         // count, so each task gets a flushToken---just an int---to track this.
   *         SK_DUMP_TASKS_CODE(uploadTask->fFlushToken =
   *                 this->priv().tokenTracker()->currentFlushToken();)
   *
   *         recording->priv().taskList()->add(std::move(uploadTask));
   *         SkASSERT(fRootUploads->size() == 0); // Drained by the newly added task
   *     }
   *     recording->priv().taskList()->add(std::move(*fRootTaskList));
   *     SkASSERT(!fRootTaskList->hasTasks());
   *
   *     SK_DUMP_TASKS_CODE(this->dumpTasks(recording->priv().taskList()));
   *
   *     // In both the "task failed" case and the "everything is discarded" case, there's no work that
   *     // needs to be done in insertRecording(). However, we use nullptr as a failure signal, so
   *     // kDiscard will return a non-null Recording that has no tasks in it.
   *     valid &= recording->priv().prepareResources(fResourceProvider,
   *                                                 &scratchManager,
   *                                                 fRuntimeEffectDict);
   *     if (!valid) {
   *         recording = nullptr;
   *         fAtlasProvider->invalidateAtlases();
   *     }
   *
   *     // Process the return queue at least once to keep it from growing too large, as otherwise
   *     // it's only processed during an explicit cleanup or a cache miss.
   *     fResourceProvider->forceProcessReturnedResources();
   *
   *     // Remaining cleanup that must always happen regardless of success or failure
   *     fRuntimeEffectDict = sk_make_sp<RuntimeEffectDictionary>();
   *     fProxyReadCounts = std::make_unique<ProxyReadCountMap>();
   *     fFloatStorageManager = sk_make_sp<FloatStorageManager>();
   *     if (!fRequireOrderedRecordings) {
   *         fAtlasProvider->invalidateAtlases();
   *     }
   *
   *     // For each KeyAndDataBuilder owned by the Recorder, check if the high watermark of data usage
   *     // over the lifetime snap is less than half of allocated capacity. If so, shrink the capacity.
   *     for (const std::unique_ptr<KeyAndDataBuilder>& keyDB : fKeyAndDataBuilders) {
   *         SkASSERT(keyDB);
   *         keyDB->first.tryShrinkCapacity();
   *         keyDB->second.tryShrinkCapacity();
   *     }
   *     return recording;
   * }
   * ```
   */
  public fun clientImageProvider(): ImageProvider {
    TODO("Implement clientImageProvider")
  }

  /**
   * C++ original:
   * ```cpp
   * ImageProvider* clientImageProvider() { return fClientImageProvider.get(); }
   * ```
   */
  public fun maxTextureSize(): Int {
    TODO("Implement maxTextureSize")
  }

  /**
   * C++ original:
   * ```cpp
   * const ImageProvider* clientImageProvider() const { return fClientImageProvider.get(); }
   * ```
   */
  public fun createBackendTexture(dimensions: SkISize, info: TextureInfo): BackendTexture {
    TODO("Implement createBackendTexture")
  }

  /**
   * C++ original:
   * ```cpp
   * int Recorder::maxTextureSize() const {
   *     return this->priv().caps()->maxTextureSize();
   * }
   * ```
   */
  public fun updateBackendTexture(
    backendTex: BackendTexture,
    srcData: Array<SkPixmap>,
    numLevels: Int,
    finishedProc: GpuFinishedProc = TODO(),
    finishedContext: GpuFinishedContext = TODO(),
  ): Boolean {
    TODO("Implement updateBackendTexture")
  }

  /**
   * C++ original:
   * ```cpp
   * BackendTexture Recorder::createBackendTexture(SkISize dimensions, const TextureInfo& info) {
   *     ASSERT_SINGLE_OWNER
   *
   *     if (!info.isValid() || info.backend() != this->backend()) {
   *         return {};
   *     }
   *     return fResourceProvider->createBackendTexture(dimensions, info);
   * }
   * ```
   */
  public fun updateCompressedBackendTexture(
    backendTex: BackendTexture,
    `data`: Unit?,
    dataSize: ULong,
    finishedProc: GpuFinishedProc = TODO(),
    finishedContext: GpuFinishedContext = TODO(),
  ): Boolean {
    TODO("Implement updateCompressedBackendTexture")
  }

  /**
   * C++ original:
   * ```cpp
   * bool Recorder::updateBackendTexture(const BackendTexture& backendTex,
   *                                     const SkPixmap srcData[],
   *                                     int numLevels,
   *                                     GpuFinishedProc finishedProc,
   *                                     GpuFinishedContext finishedContext) {
   *     ASSERT_SINGLE_OWNER
   *
   *     auto releaseHelper = skgpu::RefCntedCallback::Make(finishedProc, finishedContext);
   *
   *     if (!backendTex.isValid() || backendTex.backend() != this->backend()) {
   *         return false;
   *     }
   *
   *     if (!srcData || numLevels <= 0) {
   *         return false;
   *     }
   *
   *     // If the texture has MIP levels then we require that the full set is overwritten.
   *     int numExpectedLevels = 1;
   *     if (backendTex.info().mipmapped() == Mipmapped::kYes) {
   *         numExpectedLevels = SkMipmap::ComputeLevelCount(backendTex.dimensions()) + 1;
   *     }
   *     if (numLevels != numExpectedLevels) {
   *         return false;
   *     }
   *
   *     SkColorType ct = srcData[0].colorType();
   *
   *     if (!this->priv().caps()->areColorTypeAndTextureInfoCompatible(ct, backendTex.info())) {
   *         return false;
   *     }
   *
   *     sk_sp<Texture> texture = this->priv().resourceProvider()->createWrappedTexture(backendTex, "");
   *     if (!texture) {
   *         return false;
   *     }
   *     texture->setReleaseCallback(std::move(releaseHelper));
   *
   *     std::vector<MipLevel> mipLevels;
   *     mipLevels.resize(numLevels);
   *
   *     for (int i = 0; i < numLevels; ++i) {
   *         SkASSERT(srcData[i].addr());
   *         SkASSERT(srcData[i].info().colorInfo() == srcData[0].info().colorInfo());
   *
   *         mipLevels[i].fPixels = srcData[i].addr();
   *         mipLevels[i].fRowBytes = srcData[i].rowBytes();
   *     }
   *
   *     sk_sp<TextureProxy> proxy = TextureProxy::Wrap(std::move(texture));
   *
   *     // Src and dst colorInfo are the same
   *     const SkColorInfo& colorInfo = srcData[0].info().colorInfo();
   *
   *     const SkIRect dimensions = SkIRect::MakeSize(backendTex.dimensions());
   *     UploadSource uploadSource = UploadSource::Make(
   *             this->priv().caps(), *proxy, colorInfo, colorInfo, mipLevels, dimensions);
   *     if (!uploadSource.isValid()) {
   *         SKGPU_LOG_E("Recorder::updateBackendTexture: Could not create UploadSource");
   *         return false;
   *     }
   *
   *     // Attempt to update the texture directly on the host if possible.
   *     if (uploadSource.canUploadOnHost()) {
   *         return proxy->texture()->uploadDataOnHost(uploadSource, dimensions);
   *     }
   *
   *     // Add UploadTask to Recorder
   *     UploadInstance upload = UploadInstance::Make(this,
   *                                                  std::move(proxy),
   *                                                  colorInfo,
   *                                                  colorInfo,
   *                                                  uploadSource,
   *                                                  dimensions,
   *                                                  std::make_unique<ImageUploadContext>());
   *     if (!upload.isValid()) {
   *         SKGPU_LOG_E("Recorder::updateBackendTexture: Could not create UploadInstance");
   *         return false;
   *     }
   *     sk_sp<Task> uploadTask = UploadTask::Make(std::move(upload));
   *
   *     // Need to flush any pending work in case it depends on this texture
   *     this->priv().flushTrackedDevices(
   *         SK_DUMP_TASKS_CODE("Recorder::updateBackendTexture: Update Backend Texture"));
   *
   *     this->priv().add(std::move(uploadTask));
   *
   *     return true;
   * }
   * ```
   */
  public fun deleteBackendTexture(texture: BackendTexture) {
    TODO("Implement deleteBackendTexture")
  }

  /**
   * C++ original:
   * ```cpp
   * bool Recorder::updateCompressedBackendTexture(const BackendTexture& backendTex,
   *                                               const void* data,
   *                                               size_t dataSize,
   *                                               GpuFinishedProc finishedProc,
   *                                               GpuFinishedContext finishedContext) {
   *     ASSERT_SINGLE_OWNER
   *
   *     auto releaseHelper = skgpu::RefCntedCallback::Make(finishedProc, finishedContext);
   *
   *     if (!backendTex.isValid() || backendTex.backend() != this->backend()) {
   *         return false;
   *     }
   *
   *     if (!data) {
   *         return false;
   *     }
   *
   *     sk_sp<Texture> texture = this->priv().resourceProvider()->createWrappedTexture(backendTex, "");
   *     if (!texture) {
   *         return false;
   *     }
   *     texture->setReleaseCallback(std::move(releaseHelper));
   *
   *     sk_sp<TextureProxy> proxy = TextureProxy::Wrap(std::move(texture));
   *
   *     UploadSource uploadSource =
   *             UploadSource::MakeCompressed(this->priv().caps(), *proxy, data, dataSize);
   *     if (!uploadSource.isValid()) {
   *         SKGPU_LOG_E("Recorder::updateBackendTexture: Could not create compressed UploadSource");
   *         return false;
   *     }
   *
   *     // Attempt to update the texture directly on the host if possible.
   *     if (uploadSource.canUploadOnHost()) {
   *         return proxy->texture()->uploadDataOnHost(uploadSource,
   *                                                   SkIRect::MakeSize(proxy->dimensions()));
   *     }
   *
   *     // Add UploadTask to Recorder
   *     UploadInstance upload = UploadInstance::MakeCompressed(this, std::move(proxy), uploadSource);
   *     if (!upload.isValid()) {
   *         SKGPU_LOG_E("Recorder::updateBackendTexture: Could not create compressed UploadInstance");
   *         return false;
   *     }
   *     sk_sp<Task> uploadTask = UploadTask::Make(std::move(upload));
   *
   *     // Need to flush any pending work in case it depends on this texture
   *     this->priv().flushTrackedDevices(SK_DUMP_TASKS_CODE(
   *             "Recorder::updateCompressedBackendTexture Update Compressed Backend Texture"));
   *
   *     this->priv().add(std::move(uploadTask));
   *
   *     return true;
   * }
   * ```
   */
  public fun addFinishInfo(info: InsertFinishInfo) {
    TODO("Implement addFinishInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * void Recorder::deleteBackendTexture(const BackendTexture& texture) {
   *     ASSERT_SINGLE_OWNER
   *
   *     if (!texture.isValid() || texture.backend() != this->backend()) {
   *         return;
   *     }
   *     fResourceProvider->deleteBackendTexture(texture);
   * }
   * ```
   */
  public fun makeDeferredCanvas(imageInfo: SkImageInfo, textureInfo: TextureInfo): SkCanvas {
    TODO("Implement makeDeferredCanvas")
  }

  /**
   * C++ original:
   * ```cpp
   * void Recorder::addFinishInfo(const InsertFinishInfo& info) {
   *     if (info.fFinishedProc) {
   *         sk_sp<RefCntedCallback> callback =
   *                 RefCntedCallback::Make(info.fFinishedProc, info.fFinishedContext);
   *         fFinishedProcs.push_back(std::move(callback));
   *     }
   * }
   * ```
   */
  public fun freeGpuResources() {
    TODO("Implement freeGpuResources")
  }

  /**
   * C++ original:
   * ```cpp
   * SkCanvas* Recorder::makeDeferredCanvas(const SkImageInfo& imageInfo,
   *                                        const TextureInfo& textureInfo) {
   *     if (fTargetProxyCanvas) {
   *         // Require snapping before requesting another canvas.
   *         SKGPU_LOG_W("Requested a new deferred canvas before snapping the previous one");
   *         return nullptr;
   *     }
   *
   *     fTargetProxyData = std::make_unique<Recording::LazyProxyData>(
   *             this->priv().caps(), imageInfo.dimensions(), textureInfo);
   *     // Use kLoad for the initial load op since the purpose of a deferred canvas is to draw on top
   *     // of an existing, late-bound texture.
   *     fTargetProxyDevice = Device::Make(this,
   *                                       fTargetProxyData->refLazyProxy(),
   *                                       imageInfo.dimensions(),
   *                                       imageInfo.colorInfo(),
   *                                       {},
   *                                       LoadOp::kLoad);
   *     fTargetProxyCanvas = std::make_unique<SkCanvas>(fTargetProxyDevice);
   *     return fTargetProxyCanvas.get();
   * }
   * ```
   */
  public fun performDeferredCleanup(msNotUsed: Duration) {
    TODO("Implement performDeferredCleanup")
  }

  /**
   * C++ original:
   * ```cpp
   * void Recorder::freeGpuResources() {
   *     ASSERT_SINGLE_OWNER
   *
   *     // We don't want to free the Uniform or the Draw/UploadBufferManagers sinceall their resources
   *     // need to be held on to until a Recording is snapped. And once snapped, all their held
   *     // resources are released. The StrikeCache and TextBlobCache don't hold onto any Gpu resources.
   *
   *     // Notify the atlas and resource provider to free any resources it can (does not include
   *     // resources that are locked due to pending work).
   *     fAtlasProvider->freeGpuResources();
   *
   *     fResourceProvider->freeGpuResources();
   *
   *     // This is technically not GPU memory, but there's no other place for the client to tell us to
   *     // clean this up, and without any cleanup it can grow unbounded.
   *     fStrikeCache->freeAll();
   * }
   * ```
   */
  public fun currentBudgetedBytes(): ULong {
    TODO("Implement currentBudgetedBytes")
  }

  /**
   * C++ original:
   * ```cpp
   * void Recorder::performDeferredCleanup(std::chrono::milliseconds msNotUsed) {
   *     ASSERT_SINGLE_OWNER
   *
   *     auto purgeTime = skgpu::StdSteadyClock::now() - msNotUsed;
   *     fResourceProvider->purgeResourcesNotUsedSince(purgeTime);
   * }
   * ```
   */
  public fun currentPurgeableBytes(): ULong {
    TODO("Implement currentPurgeableBytes")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t Recorder::currentBudgetedBytes() const {
   *     ASSERT_SINGLE_OWNER
   *     return fResourceProvider->getResourceCacheCurrentBudgetedBytes();
   * }
   * ```
   */
  public fun maxBudgetedBytes(): ULong {
    TODO("Implement maxBudgetedBytes")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t Recorder::currentPurgeableBytes() const {
   *     ASSERT_SINGLE_OWNER
   *     return fResourceProvider->getResourceCacheCurrentPurgeableBytes();
   * }
   * ```
   */
  public fun setMaxBudgetedBytes(bytes: ULong) {
    TODO("Implement setMaxBudgetedBytes")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t Recorder::maxBudgetedBytes() const {
   *     ASSERT_SINGLE_OWNER
   *     return fResourceProvider->getResourceCacheLimit();
   * }
   * ```
   */
  public fun dumpMemoryStatistics(traceMemoryDump: SkTraceMemoryDump?) {
    TODO("Implement dumpMemoryStatistics")
  }

  /**
   * C++ original:
   * ```cpp
   * void Recorder::setMaxBudgetedBytes(size_t bytes) {
   *     ASSERT_SINGLE_OWNER
   *     return fResourceProvider->setResourceCacheLimit(bytes);
   * }
   * ```
   */
  public fun priv(): RecorderPriv {
    TODO("Implement priv")
  }

  /**
   * C++ original:
   * ```cpp
   * void Recorder::dumpMemoryStatistics(SkTraceMemoryDump* traceMemoryDump) const {
   *     ASSERT_SINGLE_OWNER
   *     fResourceProvider->dumpMemoryStatistics(traceMemoryDump);
   *     // TODO: What is the graphite equivalent for the text blob cache and how do we print out its
   *     // used bytes here (see Ganesh implementation).
   * }
   * ```
   */
  private fun singleOwner(): Int {
    TODO("Implement singleOwner")
  }

  /**
   * C++ original:
   * ```cpp
   * RecorderPriv priv()
   * ```
   */
  private fun registerDevice(device: SkSp<Device>) {
    TODO("Implement registerDevice")
  }

  /**
   * C++ original:
   * ```cpp
   * const RecorderPriv priv() const
   * ```
   */
  private fun deregisterDevice(device: Device?) {
    TODO("Implement deregisterDevice")
  }

  /**
   * C++ original:
   * ```cpp
   * SingleOwner* singleOwner() const { return &fSingleOwner; }
   * ```
   */
  public override fun makeCaptureCanvas(canvas: SkCanvas?): SkCanvas {
    TODO("Implement makeCaptureCanvas")
  }

  /**
   * C++ original:
   * ```cpp
   * void Recorder::registerDevice(sk_sp<Device> device) {
   *     ASSERT_SINGLE_OWNER
   *
   *     SkASSERT(device);
   *
   *     // By taking a ref on tracked devices, the Recorder prevents the Device from being deleted on
   *     // another thread unless the Recorder has been destroyed or the device has abandoned its
   *     // recorder (e.g. was marked immutable).
   *     fTrackedDevices.emplace_back(std::move(device));
   * }
   * ```
   */
  public override fun createCaptureBreakpoint(surface: SkSurface?) {
    TODO("Implement createCaptureBreakpoint")
  }

  /**
   * C++ original:
   * ```cpp
   * void Recorder::deregisterDevice(const Device* device) {
   *     ASSERT_SINGLE_OWNER
   *     for (int i = 0; i < fTrackedDevices.size(); ++i) {
   *         if (fTrackedDevices[i].get() == device) {
   *             // Don't modify the list structure of fTrackedDevices within this loop
   *             fTrackedDevices[i] = nullptr;
   *             break;
   *         }
   *     }
   * }
   * ```
   */
  private fun skDEBUGCODE(param0: Boolean): Int {
    TODO("Implement skDEBUGCODE")
  }

  public companion object {
    private val kMaxKeyAndDataBuilders: Int = TODO("Initialize kMaxKeyAndDataBuilders")
  }
}
