package org.skia.gpu

import AsyncParams
import kotlin.Any
import kotlin.Boolean
import kotlin.Function
import kotlin.Int
import kotlin.UInt
import kotlin.ULong
import kotlin.time.Duration
import org.skia.core.SkSurface
import org.skia.core.SkTraceMemoryDump
import org.skia.foundation.SkColorInfo
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkImageReadPixelsCallback
import org.skia.foundation.SkImageReadPixelsContext
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.foundation.SkYUVColorSpace
import org.skia.math.SkIRect
import org.skia.math.SkISize
import undefined.ExtraArgs

/**
 * C++ original:
 * ```cpp
 * class SK_API Context final {
 * public:
 *     Context(const Context&) = delete;
 *     Context(Context&&) = delete;
 *     Context& operator=(const Context&) = delete;
 *     Context& operator=(Context&&) = delete;
 *
 *     ~Context();
 *
 *     BackendApi backend() const;
 *
 *     std::unique_ptr<Recorder> makeRecorder(const RecorderOptions& = {});
 *     std::unique_ptr<skcpu::Recorder> makeCPURecorder();
 *
 *     /** Creates a helper object that can be moved to a different thread and used
 *      *  for precompilation.
 *      */
 *     std::unique_ptr<PrecompileContext> makePrecompileContext();
 *
 *     InsertStatus insertRecording(const InsertRecordingInfo&);
 *     bool submit(SubmitInfo submitInfo = {});
 *
 *     /** Returns true if there is work that was submitted to the GPU that has not finished. */
 *     bool hasUnfinishedGpuWork() const;
 *
 *     /** Makes image pixel data available to caller, possibly asynchronously. It can also rescale
 *         the image pixels.
 *
 *         Data is read from the source sub-rectangle, is optionally converted to a linear gamma, is
 *         rescaled to the size indicated by 'dstImageInfo', is then converted to the color space,
 *         color type, and alpha type of 'dstImageInfo'. A 'srcRect' that is not contained by the
 *         bounds of the image causes failure.
 *
 *         When the pixel data is ready the caller's ReadPixelsCallback is called with a
 *         AsyncReadResult containing pixel data in the requested color type, alpha type, and color
 *         space. The AsyncReadResult will have count() == 1. Upon failure the callback is called with
 *         nullptr for AsyncReadResult. The callback can be triggered, for example, with a call to
 *         Context::submit(SyncToCpu::kYes).
 *
 *         The data is valid for the lifetime of AsyncReadResult with the exception that the data is
 *         immediately invalidated if the Graphite context is abandoned or destroyed.
 *
 *         @param src             Graphite-backed image or surface to read the data from.
 *         @param dstImageInfo    info of the requested pixels
 *         @param srcRect         subrectangle of image to read
 *         @param rescaleGamma    controls whether rescaling is done in the image's gamma or whether
 *                                the source data is transformed to a linear gamma before rescaling.
 *         @param rescaleMode     controls the technique (and cost) of the rescaling
 *         @param callback        function to call with result of the read
 *         @param context         passed to callback
 *     */
 *     void asyncRescaleAndReadPixels(const SkImage* src,
 *                                    const SkImageInfo& dstImageInfo,
 *                                    const SkIRect& srcRect,
 *                                    SkImage::RescaleGamma rescaleGamma,
 *                                    SkImage::RescaleMode rescaleMode,
 *                                    SkImage::ReadPixelsCallback callback,
 *                                    SkImage::ReadPixelsContext context);
 *     void asyncRescaleAndReadPixels(const SkSurface* src,
 *                                    const SkImageInfo& dstImageInfo,
 *                                    const SkIRect& srcRect,
 *                                    SkImage::RescaleGamma rescaleGamma,
 *                                    SkImage::RescaleMode rescaleMode,
 *                                    SkImage::ReadPixelsCallback callback,
 *                                    SkImage::ReadPixelsContext context);
 *
 *     /**
 *         Similar to asyncRescaleAndReadPixels but performs an additional conversion to YUV. The
 *         RGB->YUV conversion is controlled by 'yuvColorSpace'. The YUV data is returned as three
 *         planes ordered y, u, v. The u and v planes are half the width and height of the resized
 *         rectangle. The y, u, and v values are single bytes. Currently this fails if 'dstSize'
 *         width and height are not even. A 'srcRect' that is not contained by the bounds of the
 *         surface causes failure.
 *
 *         When the pixel data is ready the caller's ReadPixelsCallback is called with a
 *         AsyncReadResult containing the planar data. The AsyncReadResult will have count() == 3.
 *         Upon failure the callback is called with nullptr for AsyncReadResult. The callback can
 *         be triggered, for example, with a call to Context::submit(SyncToCpu::kYes).
 *
 *         The data is valid for the lifetime of AsyncReadResult with the exception that the data
 *         is immediately invalidated if the context is abandoned or destroyed.
 *
 *         @param src            Graphite-backed image or surface to read the data from.
 *         @param yuvColorSpace  The transformation from RGB to YUV. Applied to the resized image
 *                               after it is converted to dstColorSpace.
 *         @param dstColorSpace  The color space to convert the resized image to, after rescaling.
 *         @param srcRect        The portion of the surface to rescale and convert to YUV planes.
 *         @param dstSize        The size to rescale srcRect to
 *         @param rescaleGamma   controls whether rescaling is done in the surface's gamma or whether
 *                               the source data is transformed to a linear gamma before rescaling.
 *         @param rescaleMode    controls the sampling technique of the rescaling
 *         @param callback       function to call with the planar read result
 *         @param context        passed to callback
 *      */
 *     void asyncRescaleAndReadPixelsYUV420(const SkImage* src,
 *                                          SkYUVColorSpace yuvColorSpace,
 *                                          sk_sp<SkColorSpace> dstColorSpace,
 *                                          const SkIRect& srcRect,
 *                                          const SkISize& dstSize,
 *                                          SkImage::RescaleGamma rescaleGamma,
 *                                          SkImage::RescaleMode rescaleMode,
 *                                          SkImage::ReadPixelsCallback callback,
 *                                          SkImage::ReadPixelsContext context);
 *     void asyncRescaleAndReadPixelsYUV420(const SkSurface* src,
 *                                          SkYUVColorSpace yuvColorSpace,
 *                                          sk_sp<SkColorSpace> dstColorSpace,
 *                                          const SkIRect& srcRect,
 *                                          const SkISize& dstSize,
 *                                          SkImage::RescaleGamma rescaleGamma,
 *                                          SkImage::RescaleMode rescaleMode,
 *                                          SkImage::ReadPixelsCallback callback,
 *                                          SkImage::ReadPixelsContext context);
 *
 *     /**
 *      * Identical to asyncRescaleAndReadPixelsYUV420 but a fourth plane is returned in the
 *      * AsyncReadResult passed to 'callback'. The fourth plane contains the alpha chanel at the
 *      * same full resolution as the Y plane.
 *      */
 *     void asyncRescaleAndReadPixelsYUVA420(const SkImage* src,
 *                                           SkYUVColorSpace yuvColorSpace,
 *                                           sk_sp<SkColorSpace> dstColorSpace,
 *                                           const SkIRect& srcRect,
 *                                           const SkISize& dstSize,
 *                                           SkImage::RescaleGamma rescaleGamma,
 *                                           SkImage::RescaleMode rescaleMode,
 *                                           SkImage::ReadPixelsCallback callback,
 *                                           SkImage::ReadPixelsContext context);
 *     void asyncRescaleAndReadPixelsYUVA420(const SkSurface* src,
 *                                           SkYUVColorSpace yuvColorSpace,
 *                                           sk_sp<SkColorSpace> dstColorSpace,
 *                                           const SkIRect& srcRect,
 *                                           const SkISize& dstSize,
 *                                           SkImage::RescaleGamma rescaleGamma,
 *                                           SkImage::RescaleMode rescaleMode,
 *                                           SkImage::ReadPixelsCallback callback,
 *                                           SkImage::ReadPixelsContext context);
 *
 *     /**
 *      * Checks whether any asynchronous work is complete and if so calls related callbacks.
 *      */
 *     void checkAsyncWorkCompletion();
 *
 *     /**
 *      * Called to delete the passed in BackendTexture. This should only be called if the
 *      * BackendTexture was created by calling Recorder::createBackendTexture on a Recorder created
 *      * from this Context. If the BackendTexture is not valid or does not match the BackendApi of the
 *      * Context then nothing happens.
 *      *
 *      * Otherwise this will delete/release the backend object that is wrapped in the BackendTexture.
 *      * The BackendTexture will be reset to an invalid state and should not be used again.
 *      */
 *     void deleteBackendTexture(const BackendTexture&);
 *
 *     /**
 *      * Frees GPU resources created and held by the Context. Can be called to reduce GPU memory
 *      * pressure. Any resources that are still in use (e.g. being used by work submitted to the GPU)
 *      * will not be deleted by this call. If the caller wants to make sure all resources are freed,
 *      * then they should first make sure to submit and wait on any outstanding work.
 *      */
 *     void freeGpuResources();
 *
 *     /**
 *      * Purge GPU resources on the Context that haven't been used in the past 'msNotUsed'
 *      * milliseconds or are otherwise marked for deletion, regardless of whether the context is under
 *      * budget.
 *      */
 *     void performDeferredCleanup(std::chrono::milliseconds msNotUsed);
 *
 *     /**
 *      * Returns the number of bytes of the Context's gpu memory cache budget that are currently in
 *      * use.
 *      */
 *     size_t currentBudgetedBytes() const;
 *
 *     /**
 *      * Returns the number of bytes of the Context's resource cache that are currently purgeable.
 *      */
 *     size_t currentPurgeableBytes() const;
 *
 *     /**
 *      * Returns the size of Context's gpu memory cache budget in bytes.
 *      */
 *     size_t maxBudgetedBytes() const;
 *
 *     /**
 *      * Sets the size of Context's gpu memory cache budget in bytes. If the new budget is lower than
 *      * the current budget, the cache will try to free resources to get under the new budget.
 *      */
 *     void setMaxBudgetedBytes(size_t bytes);
 *
 *     /**
 *      * Enumerates all cached GPU resources owned by the Context and dumps their memory to
 *      * traceMemoryDump.
 *      */
 *     void dumpMemoryStatistics(SkTraceMemoryDump* traceMemoryDump) const;
 *
 *     /**
 *      * Returns true if the backend-specific context has gotten into an unrecoverarble, lost state
 *      * (e.g. if we've gotten a VK_ERROR_DEVICE_LOST in the Vulkan backend).
 *      */
 *     bool isDeviceLost() const;
 *
 *     /**
 *      * Returns the maximum texture dimension supported by the underlying backend.
 *      */
 *     int maxTextureSize() const;
 *
 *     /*
 *      * Does this context support protected content?
 *      */
 *     bool supportsProtectedContent() const;
 *
 *     /*
 *      * Gets the types of GPU stats supported by this Context.
 *      */
 *     GpuStatsFlags supportedGpuStats() const;
 *
 *     /**
 *      * If supported by the backend, stores the current pipeline cache data into the
 *      * PersistentPipelineStorage-derived object passed into Graphite via
 *      * ContextOptions::fPersistentPipelineStorage. The amount stored is limited to 'maxSize'.
 *      *
 *      * Skia attempts to only call store() on the PersistentPipelineStorage object when the data
 *      * is likely to be different from what was last sync'ed.
 *      */
 *     void syncPipelineData(size_t maxSize = SIZE_MAX);
 *
 *     /*
 *      * TODO (b/412351769): Do not use startCapture() or endCapture() as the feature is still under
 *      * development.
 *      *
 *      * Starts the SkCapture. Must have set ContextOptions::fEnableCapture to start.
 *      */
 *     void startCapture();
 *
 *     /*
 *      * Ends the SkCapture and returns the collected draws and surface creation.
 *      */
 *     sk_sp<SkCapture> endCapture();
 *
 *     // Provides access to functions that aren't part of the public API.
 *     ContextPriv priv();
 *     const ContextPriv priv() const;  // NOLINT(readability-const-return-type)
 *
 *     class ContextID {
 *     public:
 *         static Context::ContextID Next();
 *
 *         ContextID() : fID(SK_InvalidUniqueID) {}
 *
 *         bool operator==(const ContextID& that) const { return fID == that.fID; }
 *         bool operator!=(const ContextID& that) const { return !(*this == that); }
 *
 *         void makeInvalid() { fID = SK_InvalidUniqueID; }
 *         bool isValid() const { return fID != SK_InvalidUniqueID; }
 *
 *     private:
 *         constexpr ContextID(uint32_t id) : fID(id) {}
 *         uint32_t fID;
 *     };
 *
 *     ContextID contextID() const { return fContextID; }
 *
 * protected:
 *     Context(sk_sp<SharedContext>, std::unique_ptr<QueueManager>, const ContextOptions&);
 *
 * private:
 *     friend class ContextPriv;
 *     friend class ContextCtorAccessor;
 *
 *     struct PixelTransferResult {
 *         PixelTransferResult();
 *         PixelTransferResult(const PixelTransferResult&);
 *         PixelTransferResult(PixelTransferResult&&);
 *         PixelTransferResult& operator=(const PixelTransferResult&);
 *         ~PixelTransferResult();
 *
 *         using ConversionFn = void(void* dst, const void* mappedBuffer);
 *         // If null then the transfer could not be performed. Otherwise this buffer will contain
 *         // the pixel data when the transfer is complete.
 *         sk_sp<Buffer> fTransferBuffer;
 *         // Size of the read.
 *         SkISize fSize;
 *         // RowBytes for transfer buffer data
 *         size_t fRowBytes;
 *         // If this is null then the transfer buffer will contain the data in the requested
 *         // color type. Otherwise, when the transfer is done this must be called to convert
 *         // from the transfer buffer's color type to the requested color type.
 *         std::function<ConversionFn> fPixelConverter;
 *     };
 *
 *     SingleOwner* singleOwner() const { return &fSingleOwner; }
 *
 *     // Must be called in Make() to handle one-time GPU setup operations that can possibly fail and
 *     // require Context::Make() to return a nullptr.
 *     bool finishInitialization();
 *
 *     void checkForFinishedWork(SyncToCpu);
 *
 *     std::unique_ptr<Recorder> makeInternalRecorder() const;
 *
 *     template <typename SrcPixels> struct AsyncParams;
 *
 *     template <typename ReadFn, typename... ExtraArgs>
 *     void asyncRescaleAndReadImpl(ReadFn Context::* asyncRead,
 *                                  SkImage::RescaleGamma rescaleGamma,
 *                                  SkImage::RescaleMode rescaleMode,
 *                                  const AsyncParams<SkImage>&,
 *                                  ExtraArgs...);
 *
 *     // Recorder is optional and will be used if drawing operations are required. If no Recorder is
 *     // provided but drawing operations are needed, a new Recorder will be created automatically.
 *     void asyncReadPixels(std::unique_ptr<Recorder>, const AsyncParams<SkImage>&);
 *     void asyncReadPixelsYUV420(std::unique_ptr<Recorder>,
 *                                const AsyncParams<SkImage>&,
 *                                SkYUVColorSpace);
 *
 *     // Like asyncReadPixels() except it performs no fallbacks, and requires that the texture be
 *     // readable. However, the texture does not need to be sampleable.
 *     void asyncReadTexture(std::unique_ptr<Recorder>,
 *                           const AsyncParams<TextureProxy>&,
 *                           const SkColorInfo& srcColorInfo);
 *
 *     // Inserts a texture to buffer transfer task, used by asyncReadPixels methods. If the
 *     // Recorder is non-null, tasks will be added to the Recorder's list; otherwise the transfer
 *     // tasks will be added to the queue manager directly.
 *     PixelTransferResult transferPixels(Recorder*,
 *                                        const TextureProxy* srcProxy,
 *                                        const SkColorInfo& srcColorInfo,
 *                                        const SkColorInfo& dstColorInfo,
 *                                        const SkIRect& srcRect);
 *
 *     // If the recorder is non-null, it will be snapped and inserted with the assumption that the
 *     // copy tasks (and possibly preparatory draw tasks) have already been added to the Recording.
 *     void finalizeAsyncReadPixels(std::unique_ptr<Recorder>,
 *                                  SkSpan<PixelTransferResult>,
 *                                  SkImage::ReadPixelsCallback callback,
 *                                  SkImage::ReadPixelsContext callbackContext);
 *
 *     sk_sp<SharedContext> fSharedContext;
 *     std::unique_ptr<ResourceProvider> fResourceProvider;
 *     std::unique_ptr<QueueManager> fQueueManager;
 *     std::unique_ptr<ClientMappedBufferManager> fMappedBufferManager;
 *     std::unique_ptr<const skcpu::ContextImpl> fCPUContext;
 *
 *     PersistentPipelineStorage* fPersistentPipelineStorage;
 *
 *     // In debug builds we guard against improper thread handling. This guard is passed to the
 *     // ResourceCache for the Context.
 *     mutable SingleOwner fSingleOwner;
 *
 * #if defined(GPU_TEST_UTILS)
 *     void deregisterRecorder(const Recorder*) SK_EXCLUDES(fTestingLock);
 *
 *     // In test builds a Recorder may track the Context that was used to create it.
 *     bool fStoreContextRefInRecorder = false;
 *     // If this tracking is on, to allow the client to safely delete this Context or its Recorders
 *     // in any order we must also track the Recorders created here.
 *     SkMutex fTestingLock;
 *     std::vector<Recorder*> fTrackedRecorders SK_GUARDED_BY(fTestingLock);
 * #endif
 *
 *     // Needed for MessageBox handling
 *     const ContextID fContextID;
 * }
 * ```
 */
public class Context public constructor(
  param0: Context,
) {
  /**
   * C++ original:
   * ```cpp
   * Context(sk_sp<SharedContext>, std::unique_ptr<QueueManager>, const ContextOptions&)
   * ```
   */
  protected var skSp: Context = TODO("Initialize skSp")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SharedContext> fSharedContext
   * ```
   */
  private var fSharedContext: Int = TODO("Initialize fSharedContext")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<ResourceProvider> fResourceProvider
   * ```
   */
  private var fResourceProvider: Int = TODO("Initialize fResourceProvider")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<QueueManager> fQueueManager
   * ```
   */
  private var fQueueManager: Int = TODO("Initialize fQueueManager")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<ClientMappedBufferManager> fMappedBufferManager
   * ```
   */
  private var fMappedBufferManager: Int = TODO("Initialize fMappedBufferManager")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<const skcpu::ContextImpl> fCPUContext
   * ```
   */
  private var fCPUContext: Int = TODO("Initialize fCPUContext")

  /**
   * C++ original:
   * ```cpp
   * PersistentPipelineStorage* fPersistentPipelineStorage
   * ```
   */
  private var fPersistentPipelineStorage: PersistentPipelineStorage? =
      TODO("Initialize fPersistentPipelineStorage")

  /**
   * C++ original:
   * ```cpp
   * mutable SingleOwner fSingleOwner
   * ```
   */
  private var fSingleOwner: Int = TODO("Initialize fSingleOwner")

  /**
   * C++ original:
   * ```cpp
   * const ContextID fContextID
   * ```
   */
  private val fContextID: ContextID = TODO("Initialize fContextID")

  /**
   * C++ original:
   * ```cpp
   * Context(const Context&) = delete
   * ```
   */
  public constructor(
    sharedContext: SkSp<SharedContext>,
    queueManager: QueueManager?,
    options: ContextOptions,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * Context& operator=(const Context&) = delete
   * ```
   */
  public fun assign(param0: Context) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * Context& operator=(Context&&) = delete
   * ```
   */
  public fun backend(): BackendApi {
    TODO("Implement backend")
  }

  /**
   * C++ original:
   * ```cpp
   * BackendApi Context::backend() const { return fSharedContext->backend(); }
   * ```
   */
  public fun makeRecorder(options: Int): Recorder? {
    TODO("Implement makeRecorder")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<Recorder> Context::makeRecorder(const RecorderOptions& options) {
   *     ASSERT_SINGLE_OWNER
   *
   *     // This is a client-owned Recorder so pass a null context so it creates its own ResourceProvider
   *     auto recorder = std::unique_ptr<Recorder>(new Recorder(fSharedContext, options, nullptr));
   * #if defined(GPU_TEST_UTILS)
   *     if (fStoreContextRefInRecorder) {
   *         recorder->priv().setContext(this);
   *     }
   * #endif
   *     return recorder;
   * }
   * ```
   */
  public fun makeCPURecorder(): org.skia.core.Recorder? {
    TODO("Implement makeCPURecorder")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<skcpu::Recorder> Context::makeCPURecorder() {
   *     ASSERT_SINGLE_OWNER
   *
   *     return std::make_unique<skcpu::RecorderImpl>(fCPUContext.get());
   * }
   * ```
   */
  public fun makePrecompileContext(): PrecompileContext? {
    TODO("Implement makePrecompileContext")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<PrecompileContext> Context::makePrecompileContext() {
   *     ASSERT_SINGLE_OWNER
   *
   *     return std::unique_ptr<PrecompileContext>(new PrecompileContext(fSharedContext));
   * }
   * ```
   */
  public fun insertRecording(info: InsertRecordingInfo): Int {
    TODO("Implement insertRecording")
  }

  /**
   * C++ original:
   * ```cpp
   * InsertStatus Context::insertRecording(const InsertRecordingInfo& info) {
   *     ASSERT_SINGLE_OWNER
   *
   *     return fQueueManager->addRecording(info, this);
   * }
   * ```
   */
  public fun submit(submitInfo: Int): Boolean {
    TODO("Implement submit")
  }

  /**
   * C++ original:
   * ```cpp
   * bool Context::submit(SubmitInfo submitInfo) {
   *     ASSERT_SINGLE_OWNER
   *
   *     if (submitInfo.fSync == SyncToCpu::kYes && !fSharedContext->caps()->allowCpuSync()) {
   *         SKGPU_LOG_E("SyncToCpu::kYes not supported with ContextOptions::fNeverYieldToWebGPU. "
   *                     "The parameter is ignored and no synchronization will occur.");
   *         submitInfo.fSync = SyncToCpu::kNo;
   *     }
   *     bool success = fQueueManager->submitToGpu(submitInfo);
   *     this->checkForFinishedWork(submitInfo.fSync);
   *     return success;
   * }
   * ```
   */
  public fun hasUnfinishedGpuWork(): Boolean {
    TODO("Implement hasUnfinishedGpuWork")
  }

  /**
   * C++ original:
   * ```cpp
   * bool Context::hasUnfinishedGpuWork() const { return fQueueManager->hasUnfinishedGpuWork(); }
   * ```
   */
  public fun asyncRescaleAndReadPixels(
    src: SkImage?,
    dstImageInfo: SkImageInfo,
    srcRect: SkIRect,
    rescaleGamma: SkImage.RescaleGamma,
    rescaleMode: SkImage.RescaleMode,
    callback: SkImageReadPixelsCallback,
    context: SkImageReadPixelsContext,
  ) {
    TODO("Implement asyncRescaleAndReadPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * void asyncRescaleAndReadPixels(const SkImage* src,
   *                                    const SkImageInfo& dstImageInfo,
   *                                    const SkIRect& srcRect,
   *                                    SkImage::RescaleGamma rescaleGamma,
   *                                    SkImage::RescaleMode rescaleMode,
   *                                    SkImage::ReadPixelsCallback callback,
   *                                    SkImage::ReadPixelsContext context)
   * ```
   */
  public fun asyncRescaleAndReadPixels(
    src: SkSurface?,
    dstImageInfo: SkImageInfo,
    srcRect: SkIRect,
    rescaleGamma: SkImage.RescaleGamma,
    rescaleMode: SkImage.RescaleMode,
    callback: SkImageReadPixelsCallback,
    context: SkImageReadPixelsContext,
  ) {
    TODO("Implement asyncRescaleAndReadPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * void asyncRescaleAndReadPixels(const SkSurface* src,
   *                                    const SkImageInfo& dstImageInfo,
   *                                    const SkIRect& srcRect,
   *                                    SkImage::RescaleGamma rescaleGamma,
   *                                    SkImage::RescaleMode rescaleMode,
   *                                    SkImage::ReadPixelsCallback callback,
   *                                    SkImage::ReadPixelsContext context)
   * ```
   */
  public fun asyncRescaleAndReadPixelsYUV420(
    src: SkImage?,
    yuvColorSpace: SkYUVColorSpace,
    dstColorSpace: SkSp<SkColorSpace>,
    srcRect: SkIRect,
    dstSize: SkISize,
    rescaleGamma: SkImage.RescaleGamma,
    rescaleMode: SkImage.RescaleMode,
    callback: SkImageReadPixelsCallback,
    context: SkImageReadPixelsContext,
  ) {
    TODO("Implement asyncRescaleAndReadPixelsYUV420")
  }

  /**
   * C++ original:
   * ```cpp
   * void asyncRescaleAndReadPixelsYUV420(const SkImage* src,
   *                                          SkYUVColorSpace yuvColorSpace,
   *                                          sk_sp<SkColorSpace> dstColorSpace,
   *                                          const SkIRect& srcRect,
   *                                          const SkISize& dstSize,
   *                                          SkImage::RescaleGamma rescaleGamma,
   *                                          SkImage::RescaleMode rescaleMode,
   *                                          SkImage::ReadPixelsCallback callback,
   *                                          SkImage::ReadPixelsContext context)
   * ```
   */
  public fun asyncRescaleAndReadPixelsYUV420(
    src: SkSurface?,
    yuvColorSpace: SkYUVColorSpace,
    dstColorSpace: SkSp<SkColorSpace>,
    srcRect: SkIRect,
    dstSize: SkISize,
    rescaleGamma: SkImage.RescaleGamma,
    rescaleMode: SkImage.RescaleMode,
    callback: SkImageReadPixelsCallback,
    context: SkImageReadPixelsContext,
  ) {
    TODO("Implement asyncRescaleAndReadPixelsYUV420")
  }

  /**
   * C++ original:
   * ```cpp
   * void asyncRescaleAndReadPixelsYUV420(const SkSurface* src,
   *                                          SkYUVColorSpace yuvColorSpace,
   *                                          sk_sp<SkColorSpace> dstColorSpace,
   *                                          const SkIRect& srcRect,
   *                                          const SkISize& dstSize,
   *                                          SkImage::RescaleGamma rescaleGamma,
   *                                          SkImage::RescaleMode rescaleMode,
   *                                          SkImage::ReadPixelsCallback callback,
   *                                          SkImage::ReadPixelsContext context)
   * ```
   */
  public fun asyncRescaleAndReadPixelsYUVA420(
    src: SkImage?,
    yuvColorSpace: SkYUVColorSpace,
    dstColorSpace: SkSp<SkColorSpace>,
    srcRect: SkIRect,
    dstSize: SkISize,
    rescaleGamma: SkImage.RescaleGamma,
    rescaleMode: SkImage.RescaleMode,
    callback: SkImageReadPixelsCallback,
    context: SkImageReadPixelsContext,
  ) {
    TODO("Implement asyncRescaleAndReadPixelsYUVA420")
  }

  /**
   * C++ original:
   * ```cpp
   * void asyncRescaleAndReadPixelsYUVA420(const SkImage* src,
   *                                           SkYUVColorSpace yuvColorSpace,
   *                                           sk_sp<SkColorSpace> dstColorSpace,
   *                                           const SkIRect& srcRect,
   *                                           const SkISize& dstSize,
   *                                           SkImage::RescaleGamma rescaleGamma,
   *                                           SkImage::RescaleMode rescaleMode,
   *                                           SkImage::ReadPixelsCallback callback,
   *                                           SkImage::ReadPixelsContext context)
   * ```
   */
  public fun asyncRescaleAndReadPixelsYUVA420(
    src: SkSurface?,
    yuvColorSpace: SkYUVColorSpace,
    dstColorSpace: SkSp<SkColorSpace>,
    srcRect: SkIRect,
    dstSize: SkISize,
    rescaleGamma: SkImage.RescaleGamma,
    rescaleMode: SkImage.RescaleMode,
    callback: SkImageReadPixelsCallback,
    context: SkImageReadPixelsContext,
  ) {
    TODO("Implement asyncRescaleAndReadPixelsYUVA420")
  }

  /**
   * C++ original:
   * ```cpp
   * void asyncRescaleAndReadPixelsYUVA420(const SkSurface* src,
   *                                           SkYUVColorSpace yuvColorSpace,
   *                                           sk_sp<SkColorSpace> dstColorSpace,
   *                                           const SkIRect& srcRect,
   *                                           const SkISize& dstSize,
   *                                           SkImage::RescaleGamma rescaleGamma,
   *                                           SkImage::RescaleMode rescaleMode,
   *                                           SkImage::ReadPixelsCallback callback,
   *                                           SkImage::ReadPixelsContext context)
   * ```
   */
  public fun checkAsyncWorkCompletion() {
    TODO("Implement checkAsyncWorkCompletion")
  }

  /**
   * C++ original:
   * ```cpp
   * void Context::checkAsyncWorkCompletion() {
   *     this->checkForFinishedWork(SyncToCpu::kNo);
   * }
   * ```
   */
  public fun deleteBackendTexture(texture: BackendTexture) {
    TODO("Implement deleteBackendTexture")
  }

  /**
   * C++ original:
   * ```cpp
   * void Context::deleteBackendTexture(const BackendTexture& texture) {
   *     ASSERT_SINGLE_OWNER
   *
   *     if (!texture.isValid() || texture.backend() != this->backend()) {
   *         return;
   *     }
   *     fResourceProvider->deleteBackendTexture(texture);
   * }
   * ```
   */
  public fun freeGpuResources() {
    TODO("Implement freeGpuResources")
  }

  /**
   * C++ original:
   * ```cpp
   * void Context::freeGpuResources() {
   *     ASSERT_SINGLE_OWNER
   *
   *     this->checkAsyncWorkCompletion();
   *
   *     fResourceProvider->freeGpuResources();
   *     fSharedContext->freeGpuResources();
   * }
   * ```
   */
  public fun performDeferredCleanup(msNotUsed: Duration) {
    TODO("Implement performDeferredCleanup")
  }

  /**
   * C++ original:
   * ```cpp
   * void Context::performDeferredCleanup(std::chrono::milliseconds msNotUsed) {
   *     ASSERT_SINGLE_OWNER
   *
   *     this->checkAsyncWorkCompletion();
   *
   *     auto purgeTime = skgpu::StdSteadyClock::now() - msNotUsed;
   *     fResourceProvider->purgeResourcesNotUsedSince(purgeTime);
   *     fSharedContext->purgeResourcesNotUsedSince(purgeTime);
   * }
   * ```
   */
  public fun currentBudgetedBytes(): ULong {
    TODO("Implement currentBudgetedBytes")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t Context::currentBudgetedBytes() const {
   *     ASSERT_SINGLE_OWNER
   *     SkASSERT(fSharedContext->getResourceCacheCurrentBudgetedBytes() == 0);
   *     return fResourceProvider->getResourceCacheCurrentBudgetedBytes();
   * }
   * ```
   */
  public fun currentPurgeableBytes(): ULong {
    TODO("Implement currentPurgeableBytes")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t Context::currentPurgeableBytes() const {
   *     ASSERT_SINGLE_OWNER
   *     SkASSERT(fSharedContext->getResourceCacheCurrentPurgeableBytes() == 0);
   *     return fResourceProvider->getResourceCacheCurrentPurgeableBytes();
   * }
   * ```
   */
  public fun maxBudgetedBytes(): ULong {
    TODO("Implement maxBudgetedBytes")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t Context::maxBudgetedBytes() const {
   *     ASSERT_SINGLE_OWNER
   *     SkASSERT(fSharedContext->getResourceCacheLimit() == SharedContext::kThreadedSafeResourceBudget);
   *     return fResourceProvider->getResourceCacheLimit();
   * }
   * ```
   */
  public fun setMaxBudgetedBytes(bytes: ULong) {
    TODO("Implement setMaxBudgetedBytes")
  }

  /**
   * C++ original:
   * ```cpp
   * void Context::setMaxBudgetedBytes(size_t bytes) {
   *     ASSERT_SINGLE_OWNER
   *     return fResourceProvider->setResourceCacheLimit(bytes);
   * }
   * ```
   */
  public fun dumpMemoryStatistics(traceMemoryDump: SkTraceMemoryDump?) {
    TODO("Implement dumpMemoryStatistics")
  }

  /**
   * C++ original:
   * ```cpp
   * void Context::dumpMemoryStatistics(SkTraceMemoryDump* traceMemoryDump) const {
   *     ASSERT_SINGLE_OWNER
   *     fResourceProvider->dumpMemoryStatistics(traceMemoryDump);
   *     fSharedContext->dumpMemoryStatistics(traceMemoryDump);
   *     // TODO: What is the graphite equivalent for the text blob cache and how do we print out its
   *     // used bytes here (see Ganesh implementation).
   * }
   * ```
   */
  public fun isDeviceLost(): Boolean {
    TODO("Implement isDeviceLost")
  }

  /**
   * C++ original:
   * ```cpp
   * bool Context::isDeviceLost() const {
   *     return fSharedContext->isDeviceLost();
   * }
   * ```
   */
  public fun maxTextureSize(): Int {
    TODO("Implement maxTextureSize")
  }

  /**
   * C++ original:
   * ```cpp
   * int Context::maxTextureSize() const {
   *     return fSharedContext->caps()->maxTextureSize();
   * }
   * ```
   */
  public fun supportsProtectedContent(): Boolean {
    TODO("Implement supportsProtectedContent")
  }

  /**
   * C++ original:
   * ```cpp
   * bool Context::supportsProtectedContent() const {
   *     return fSharedContext->isProtected() == Protected::kYes;
   * }
   * ```
   */
  public fun supportedGpuStats(): GpuStatsFlags {
    TODO("Implement supportedGpuStats")
  }

  /**
   * C++ original:
   * ```cpp
   * GpuStatsFlags Context::supportedGpuStats() const {
   *     return fSharedContext->caps()->supportedGpuStats();
   * }
   * ```
   */
  public fun syncPipelineData(maxSize: ULong = TODO()) {
    TODO("Implement syncPipelineData")
  }

  /**
   * C++ original:
   * ```cpp
   * void Context::syncPipelineData(size_t maxSize) {
   *     ASSERT_SINGLE_OWNER
   *
   *     if (fPersistentPipelineStorage) {
   *         fSharedContext->syncPipelineData(fPersistentPipelineStorage, maxSize);
   *     }
   * }
   * ```
   */
  public fun startCapture() {
    TODO("Implement startCapture")
  }

  /**
   * C++ original:
   * ```cpp
   * void Context::startCapture() {
   *     if (fSharedContext->captureManager()) {
   *         fSharedContext->captureManager()->toggleCapture(true);
   *     }
   * }
   * ```
   */
  public fun endCapture(): Int {
    TODO("Implement endCapture")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkCapture> Context::endCapture() {
   *     if (fSharedContext->captureManager()) {
   *         fSharedContext->captureManager()->toggleCapture(false);
   *         return fSharedContext->captureManager()->getLastCapture();
   *     }
   *     return nullptr;
   * }
   * ```
   */
  public fun priv(): ContextPriv {
    TODO("Implement priv")
  }

  /**
   * C++ original:
   * ```cpp
   * ContextPriv priv()
   * ```
   */
  private fun contextID(): ContextID {
    TODO("Implement contextID")
  }

  /**
   * C++ original:
   * ```cpp
   * const ContextPriv priv() const
   * ```
   */
  private fun singleOwner(): Int {
    TODO("Implement singleOwner")
  }

  /**
   * C++ original:
   * ```cpp
   * ContextID contextID() const { return fContextID; }
   * ```
   */
  private fun finishInitialization(): Boolean {
    TODO("Implement finishInitialization")
  }

  /**
   * C++ original:
   * ```cpp
   * SingleOwner* singleOwner() const { return &fSingleOwner; }
   * ```
   */
  private fun checkForFinishedWork(syncToCpu: SyncToCpu) {
    TODO("Implement checkForFinishedWork")
  }

  /**
   * C++ original:
   * ```cpp
   * bool Context::finishInitialization() {
   *     SkASSERT(!fSharedContext->rendererProvider()); // Can only initialize once
   *
   *     if (!fSharedContext->globalCache()->initializeDynamicSamplers(fResourceProvider.get(),
   *                                                                   fSharedContext->caps())) {
   *         return false;
   *     }
   *
   *     StaticBufferManager bufferManager{fResourceProvider.get(), fSharedContext->caps()};
   *     std::unique_ptr<RendererProvider> renderers{
   *             new RendererProvider(fSharedContext->caps(), &bufferManager)};
   *
   *     auto result = bufferManager.finalize(this, fQueueManager.get(), fSharedContext->globalCache());
   *     if (result == StaticBufferManager::FinishResult::kFailure) {
   *         // If something went wrong filling out the static vertex buffers, any Renderer that would
   *         // use it will draw incorrectly, so it's better to fail the Context creation.
   *         return false;
   *     }
   *     if (result == StaticBufferManager::FinishResult::kSuccess &&
   *         !fQueueManager->submitToGpu(/*submitInfo=*/{})) {
   *         SKGPU_LOG_W("Failed to submit initial command buffer for Context creation.\n");
   *         return false;
   *     } // else result was kNoWork so skip submitting to the GPU
   *     fSharedContext->setRendererProvider(std::move(renderers));
   *     return true;
   * }
   * ```
   */
  private fun makeInternalRecorder(): Int {
    TODO("Implement makeInternalRecorder")
  }

  /**
   * C++ original:
   * ```cpp
   * void Context::checkForFinishedWork(SyncToCpu syncToCpu) {
   *     ASSERT_SINGLE_OWNER
   *
   *     fQueueManager->checkForFinishedWork(syncToCpu);
   *     fMappedBufferManager->process();
   *     // Process the return queue periodically to make sure it doesn't get too big
   *     fResourceProvider->forceProcessReturnedResources();
   *     fSharedContext->forceProcessReturnedResources();
   * }
   * ```
   */
  private fun <ReadFn, ExtraArgs> asyncRescaleAndReadImpl(
    asyncRead: Any?,
    rescaleGamma: SkImage.RescaleGamma,
    rescaleMode: SkImage.RescaleMode,
    param3: AsyncParams<SkImage>,
    extraParams: ExtraArgs,
  ) {
    TODO("Implement asyncRescaleAndReadImpl")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<Recorder> Context::makeInternalRecorder() const {
   *     ASSERT_SINGLE_OWNER
   *
   *     // Unlike makeRecorder(), this Recorder is meant to be short-lived and go away before a Context
   *     // public API function returns to the caller. As such it shares the Context's resource provider
   *     // (no separate budget) and does not get tracked. The internal drawing performed with an
   *     // internal recorder should not require a client image provider.
   *     //
   *     // Explicitly overrides fRequiresOrderedRecordings to false so that these Recorders do not
   *     // inherit any global policy from the ContextOptions. Since they will only produce one Recording
   *     // there's no need to require subsequent recordings be ordered.
   *     RecorderOptions options = {};
   *     options.fRequireOrderedRecordings = false;
   *     return std::unique_ptr<Recorder>(new Recorder(fSharedContext, options, this));
   * }
   * ```
   */
  private fun asyncReadPixels(param0: Recorder?, param1: AsyncParams<SkImage>) {
    TODO("Implement asyncReadPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * template <typename ReadFn, typename... ExtraArgs>
   * void Context::asyncRescaleAndReadImpl(ReadFn Context::* asyncRead,
   *                                       SkImage::RescaleGamma rescaleGamma,
   *                                       SkImage::RescaleMode rescaleMode,
   *                                       const AsyncParams<SkImage>& params,
   *                                       ExtraArgs... extraParams) {
   *     if (!params.validate()) {
   *         return params.fail();
   *     }
   *
   *     if (params.fSrcRect.size() == params.fDstImageInfo.dimensions()) {
   *         // No need to rescale so do a direct readback
   *         return (this->*asyncRead)(/*recorder=*/nullptr, params, extraParams...);
   *     }
   *
   *     // Make a recorder to collect the rescale drawing commands and the copy commands
   *     std::unique_ptr<Recorder> recorder = this->makeInternalRecorder();
   *     sk_sp<SkImage> scaledImage = RescaleImage(recorder.get(),
   *                                               params.fSrcImage,
   *                                               params.fSrcRect,
   *                                               params.fDstImageInfo,
   *                                               rescaleGamma,
   *                                               rescaleMode);
   *     if (!scaledImage) {
   *         SKGPU_LOG_W("AsyncRead failed because rescaling failed");
   *         return params.fail();
   *     }
   *     (this->*asyncRead)(std::move(recorder),
   *                        params.withNewSource(scaledImage.get(), params.fDstImageInfo.bounds()),
   *                        extraParams...);
   * }
   * ```
   */
  private fun asyncReadPixelsYUV420(
    param0: Recorder?,
    param1: AsyncParams<SkImage>,
    param2: SkYUVColorSpace,
  ) {
    TODO("Implement asyncReadPixelsYUV420")
  }

  /**
   * C++ original:
   * ```cpp
   * void asyncReadPixels(std::unique_ptr<Recorder>, const AsyncParams<SkImage>&)
   * ```
   */
  private fun asyncReadTexture(
    param0: Recorder?,
    param1: AsyncParams<TextureProxy>,
    srcColorInfo: SkColorInfo,
  ) {
    TODO("Implement asyncReadTexture")
  }

  /**
   * C++ original:
   * ```cpp
   * void asyncReadPixelsYUV420(std::unique_ptr<Recorder>,
   *                                const AsyncParams<SkImage>&,
   *                                SkYUVColorSpace)
   * ```
   */
  private fun transferPixels(
    recorder: Recorder?,
    srcProxy: TextureProxy?,
    srcColorInfo: SkColorInfo,
    dstColorInfo: SkColorInfo,
    srcRect: SkIRect,
  ): PixelTransferResult {
    TODO("Implement transferPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * void asyncReadTexture(std::unique_ptr<Recorder>,
   *                           const AsyncParams<TextureProxy>&,
   *                           const SkColorInfo& srcColorInfo)
   * ```
   */
  private fun finalizeAsyncReadPixels(
    param0: Recorder?,
    param1: SkSpan<undefined.PixelTransferResult>,
    callback: SkImageReadPixelsCallback,
    callbackContext: SkImageReadPixelsContext,
  ) {
    TODO("Implement finalizeAsyncReadPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * Context::PixelTransferResult Context::transferPixels(Recorder* recorder,
   *                                                      const TextureProxy* srcProxy,
   *                                                      const SkColorInfo& srcColorInfo,
   *                                                      const SkColorInfo& dstColorInfo,
   *                                                      const SkIRect& srcRect) {
   *     SkASSERT(SkIRect::MakeSize(srcProxy->dimensions()).contains(srcRect));
   *     SkASSERT(SkColorInfoIsValid(dstColorInfo));
   *
   *     const Caps* caps = fSharedContext->caps();
   *     if (!srcProxy || !caps->supportsReadPixels(srcProxy->textureInfo())) {
   *         return {};
   *     }
   *
   *     const SkColorType srcColorType = srcColorInfo.colorType();
   *     SkColorType supportedColorType;
   *     bool isRGB888Format;
   *     std::tie(supportedColorType, isRGB888Format) =
   *             caps->supportedReadPixelsColorType(srcColorType,
   *                                                srcProxy->textureInfo(),
   *                                                dstColorInfo.colorType());
   *     if (supportedColorType == kUnknown_SkColorType) {
   *         return {};
   *     }
   *
   *     // Fail if read color type does not have all of dstCT's color channels and those missing color
   *     // channels are in the src.
   *     uint32_t dstChannels = SkColorTypeChannelFlags(dstColorInfo.colorType());
   *     uint32_t legalReadChannels = SkColorTypeChannelFlags(supportedColorType);
   *     uint32_t srcChannels = SkColorTypeChannelFlags(srcColorType);
   *     if ((~legalReadChannels & dstChannels) & srcChannels) {
   *         return {};
   *     }
   *
   *     int bpp = isRGB888Format ? 3 : SkColorTypeBytesPerPixel(supportedColorType);
   *     size_t rowBytes = caps->getAlignedTextureDataRowBytes(bpp * srcRect.width());
   *     size_t size = SkAlignTo(rowBytes * srcRect.height(), caps->requiredTransferBufferAlignment());
   *     sk_sp<Buffer> buffer = fResourceProvider->findOrCreateNonShareableBuffer(
   *             size, BufferType::kXferGpuToCpu, AccessPattern::kHostVisible, "TransferToCpu");
   *     if (!buffer) {
   *         return {};
   *     }
   *
   *     // Set up copy task. Since we always use a new buffer the offset can be 0 and we don't need to
   *     // worry about aligning it to the required transfer buffer alignment.
   *     sk_sp<CopyTextureToBufferTask> copyTask = CopyTextureToBufferTask::Make(sk_ref_sp(srcProxy),
   *                                                                             srcRect,
   *                                                                             buffer,
   *                                                                             /*bufferOffset=*/0,
   *                                                                             rowBytes);
   *     const bool addTasksDirectly = !SkToBool(recorder);
   *     Protected contextIsProtected = fSharedContext->isProtected();
   *     if (!copyTask || (addTasksDirectly && !fQueueManager->addTask(copyTask.get(),
   *                                                                   this,
   *                                                                   contextIsProtected))) {
   *         return {};
   *     } else if (!addTasksDirectly) {
   *         // Add the task to the Recorder instead of the QueueManager if that's been required for
   *         // collecting tasks to prepare the copied textures.
   *         recorder->priv().add(std::move(copyTask));
   *     }
   *     sk_sp<SynchronizeToCpuTask> syncTask = SynchronizeToCpuTask::Make(buffer);
   *     if (!syncTask || (addTasksDirectly && !fQueueManager->addTask(syncTask.get(),
   *                                                                   this,
   *                                                                   contextIsProtected))) {
   *         return {};
   *     } else if (!addTasksDirectly) {
   *         recorder->priv().add(std::move(syncTask));
   *     }
   *
   *     PixelTransferResult result;
   *     result.fTransferBuffer = std::move(buffer);
   *     result.fSize = srcRect.size();
   *     // srcColorInfo describes the texture; readColorInfo describes the result of the copy-to-buffer,
   *     // which may be different; dstColorInfo is what we have to transform it into when invoking the
   *     // async callbacks.
   *     SkColorInfo readColorInfo = srcColorInfo.makeColorType(supportedColorType);
   *     if (readColorInfo != dstColorInfo || isRGB888Format) {
   *         SkISize dims = srcRect.size();
   *         SkImageInfo srcInfo = SkImageInfo::Make(dims, readColorInfo);
   *         SkImageInfo dstInfo = SkImageInfo::Make(dims, dstColorInfo);
   *         result.fRowBytes = dstInfo.minRowBytes();
   *         result.fPixelConverter = [dstInfo, srcInfo, rowBytes, isRGB888Format](
   *                 void* dst, const void* src) {
   *             SkAutoPixmapStorage temp;
   *             size_t srcRowBytes = rowBytes;
   *             if (isRGB888Format) {
   *                 temp.alloc(srcInfo);
   *                 size_t tRowBytes = temp.rowBytes();
   *                 auto* sRow = reinterpret_cast<const char*>(src);
   *                 auto* tRow = reinterpret_cast<char*>(temp.writable_addr());
   *                 for (int y = 0; y < srcInfo.height(); ++y, sRow += srcRowBytes, tRow += tRowBytes) {
   *                     for (int x = 0; x < srcInfo.width(); ++x) {
   *                         auto s = sRow + x*3;
   *                         auto t = tRow + x*sizeof(uint32_t);
   *                         memcpy(t, s, 3);
   *                         t[3] = static_cast<char>(0xFF);
   *                     }
   *                 }
   *                 src = temp.addr();
   *                 srcRowBytes = tRowBytes;
   *             }
   *             SkAssertResult(SkConvertPixels(dstInfo, dst, dstInfo.minRowBytes(),
   *                                            srcInfo, src, srcRowBytes));
   *         };
   *     } else {
   *         result.fRowBytes = rowBytes;
   *     }
   *
   *     return result;
   * }
   * ```
   */
  public fun deregisterRecorder(recorder: Recorder?) {
    TODO("Implement deregisterRecorder")
  }

  public data class ContextID public constructor(
    private var fID: UInt,
  ) {
    public override operator fun equals(other: Any?): Boolean {
      TODO("Implement equals")
    }

    public fun makeInvalid() {
      TODO("Implement makeInvalid")
    }

    public fun isValid(): Boolean {
      TODO("Implement isValid")
    }

    public companion object {
      public fun next(): ContextID {
        TODO("Implement next")
      }
    }
  }

  public data class PixelTransferResult public constructor(
    public var fTransferBuffer: Int,
    public var fSize: Int,
    public var fRowBytes: ULong,
    public var fPixelConverter: Function,
  ) {
    public fun assign(param0: undefined.PixelTransferResult) {
      TODO("Implement assign")
    }
  }
}
