package org.skia.gpu

import SK_DUMP_TASKS_CODE
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class RecorderPriv {
 * public:
 *     void add(sk_sp<Task>);
 *     // Flush *all* tracked devices created by this Recorder
 *     void flushTrackedDevices(SK_DUMP_TASKS_CODE(const char* flushSource));
 *     // Flush tracked devices that have pending reads from `dependency`.
 *     void flushTrackedDevices(const TextureProxy* dependency);
 *
 *     std::unique_ptr<KeyAndDataBuilder> popOrCreateKeyAndDataBuilder();
 *     void pushKeyAndDataBuilder(std::unique_ptr<KeyAndDataBuilder> keyDB);
 *
 *     const Caps* caps() const { return fRecorder->fSharedContext->caps(); }
 *
 *     ResourceProvider* resourceProvider() { return fRecorder->fResourceProvider; }
 *
 *     sk_sp<RuntimeEffectDictionary> runtimeEffectDictionary();
 *
 *     const ShaderCodeDictionary* shaderCodeDictionary() const {
 *         return fRecorder->fSharedContext->shaderCodeDictionary();
 *     }
 *     ShaderCodeDictionary* shaderCodeDictionary() {
 *         return fRecorder->fSharedContext->shaderCodeDictionary();
 *     }
 *
 *     const RendererProvider* rendererProvider() const {
 *         return fRecorder->fSharedContext->rendererProvider();
 *     }
 *
 *     Protected isProtected() const {
 *         return fRecorder->fSharedContext->isProtected();
 *     }
 *
 *     UploadList* rootUploadList() { return fRecorder->fRootUploads.get(); }
 *     DrawBufferManager* drawBufferManager() { return fRecorder->fDrawBufferManager.get(); }
 *     UploadBufferManager* uploadBufferManager() { return fRecorder->fUploadBufferManager.get(); }
 *     FloatStorageManager* floatStorageManager() { return fRecorder->fFloatStorageManager.get(); }
 *     sk_sp<FloatStorageManager> refFloatStorageManager() { return fRecorder->fFloatStorageManager; }
 *
 *     AtlasProvider* atlasProvider() { return fRecorder->fAtlasProvider.get(); }
 *     TokenTracker* tokenTracker() { return fRecorder->fTokenTracker.get(); }
 *     sktext::gpu::StrikeCache* strikeCache() { return fRecorder->fStrikeCache.get(); }
 *     sktext::gpu::TextBlobRedrawCoordinator* textBlobCache() {
 *         return fRecorder->fTextBlobCache.get();
 *     }
 *     ProxyCache* proxyCache() { return this->resourceProvider()->proxyCache(); }
 *
 *     // NOTE: Temporary access for DrawTask to manipulate pending read counts.
 *     void addPendingRead(const TextureProxy*);
 *
 *     static sk_sp<TextureProxy> CreateCachedProxy(Recorder*,
 *                                                  const SkBitmap&,
 *                                                  std::string_view label);
 *
 *     uint32_t uniqueID() const { return fRecorder->fUniqueID; }
 *
 * #if defined(SK_DEBUG)
 *     uint32_t nextRecordingID() const { return fRecorder->fNextRecordingID; }
 * #endif
 *
 *     size_t getResourceCacheLimit() const;
 *
 * #if defined(GPU_TEST_UTILS)
 *     bool deviceIsRegistered(Device*) const;
 *     ResourceCache* resourceCache() { return fRecorder->fResourceProvider->resourceCache(); }
 *     SharedContext* sharedContext() { return fRecorder->fSharedContext.get(); }
 *     // used by the Context that created this Recorder to set a back pointer
 *     void setContext(Context*);
 *     Context* context() { return fRecorder->fContext; }
 *     void issueFlushToken();
 * #endif
 *
 * private:
 *     explicit RecorderPriv(Recorder* recorder) : fRecorder(recorder) {}
 *     RecorderPriv& operator=(const RecorderPriv&) = delete;
 *
 *     // No taking addresses of this type.
 *     const RecorderPriv* operator&() const = delete;
 *     RecorderPriv* operator&() = delete;
 *
 *     Recorder* fRecorder;
 *
 *     friend class Recorder;  // to construct/copy this type.
 * }
 * ```
 */
public data class RecorderPriv public constructor(
  /**
   * C++ original:
   * ```cpp
   * Recorder* fRecorder
   * ```
   */
  private var fRecorder: Int?,
) {
  /**
   * C++ original:
   * ```cpp
   * void RecorderPriv::add(sk_sp<Task> task) {
   *     ASSERT_SINGLE_OWNER_PRIV
   *     // Associate each task with current flush count.
   *     SK_DUMP_TASKS_CODE(task->fFlushToken = fRecorder->fTokenTracker->nextFlushToken();)
   *     fRecorder->fRootTaskList->add(std::move(task));
   * }
   * ```
   */
  public fun add(task: SkSp<Task>) {
    TODO("Implement add")
  }

  /**
   * C++ original:
   * ```cpp
   * void flushTrackedDevices(SK_DUMP_TASKS_CODE(const char* flushSource))
   * ```
   */
  public fun flushTrackedDevices(param0: (Int) -> SK_DUMP_TASKS_CODE) {
    TODO("Implement flushTrackedDevices")
  }

  /**
   * C++ original:
   * ```cpp
   * void RecorderPriv::flushTrackedDevices(const TextureProxy* dependency) {
   *     // This version of flushTrackedDevices() must be re-entrant because it is entirely possible for
   *     // client-owned surfaces to read and write to each other, where this will be called with
   *     // different textures for `dependency`. The recursion stops once the encountered surfaces have
   *     // snapped remaining pending work from their DrawContext. But because we might recurse, we do
   *     // not perform any cleanup of the fTrackedDevices list. That is deferred until snap() time.
   *
   *     for (int i = 0; i < fRecorder->fTrackedDevices.size(); ++i) {
   *         // Entries may be set to null from a call to deregisterDevice(), which will be cleaned up
   *         // along with any immutable or uniquely held Devices once everything is snapped.
   *         Device* device = fRecorder->fTrackedDevices[i].get();
   *         if (device && device->hasPendingReads(dependency)) {
   *             device->flushPendingWork(/*drawContext=*/nullptr);
   *         }
   *     }
   *
   *     // TODO(michaelludwig): These flushes are currently only triggered for client-owned surfaces
   *     // drawn into other surfaces. This function could be used to flush a more targeted set of
   *     // devices when an atlas fills up; in that case we could increment the flush token as part of
   *     // that work. As-is, we don't increment the flush token because there could be a tracked atlas
   *     // that depended on the atlas's texture state that did *not* depend on `dependency` so it still
   *     // requires the atlas to be using the old flush token. The surfaces that were flushed here could
   *     // advance to a new token but the token tracking isn't that precise. This all may be moot
   *     // anyways if we can successfully switch to a rolling atlas page system.
   * }
   * ```
   */
  public fun flushTrackedDevices(dependency: TextureProxy?) {
    TODO("Implement flushTrackedDevices")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<KeyAndDataBuilder> RecorderPriv::popOrCreateKeyAndDataBuilder() {
   *     if (!fRecorder->fKeyAndDataBuilders.empty()) {
   *         std::unique_ptr<KeyAndDataBuilder> keyDB = std::move(fRecorder->fKeyAndDataBuilders.back());
   *         fRecorder->fKeyAndDataBuilders.pop_back();
   *         return keyDB;
   *     }
   *
   *     const bool useStorageBuffers = this->caps()->storageBufferSupport();
   *     const auto& bindingReq = this->caps()->resourceBindingRequirements();
   *     auto gathererLayout = useStorageBuffers ? bindingReq.fStorageBufferLayout
   *                                             : bindingReq.fUniformBufferLayout;
   *
   *     return std::make_unique<KeyAndDataBuilder>(
   *         PipelineDataGatherer(gathererLayout),
   *         PaintParamsKeyBuilder(this->shaderCodeDictionary()));
   *     }
   * ```
   */
  public fun popOrCreateKeyAndDataBuilder(): Int {
    TODO("Implement popOrCreateKeyAndDataBuilder")
  }

  /**
   * C++ original:
   * ```cpp
   * void pushKeyAndDataBuilder(std::unique_ptr<KeyAndDataBuilder> keyDB)
   * ```
   */
  public fun pushKeyAndDataBuilder(keyDB: KeyAndDataBuilder?) {
    TODO("Implement pushKeyAndDataBuilder")
  }

  /**
   * C++ original:
   * ```cpp
   * const Caps* caps() const { return fRecorder->fSharedContext->caps(); }
   * ```
   */
  public fun caps(): Caps {
    TODO("Implement caps")
  }

  /**
   * C++ original:
   * ```cpp
   * ResourceProvider* resourceProvider() { return fRecorder->fResourceProvider; }
   * ```
   */
  public fun resourceProvider(): RendererProvider {
    TODO("Implement resourceProvider")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<RuntimeEffectDictionary> RecorderPriv::runtimeEffectDictionary() {
   *     return fRecorder->fRuntimeEffectDict;
   * }
   * ```
   */
  public fun runtimeEffectDictionary(): Int {
    TODO("Implement runtimeEffectDictionary")
  }

  /**
   * C++ original:
   * ```cpp
   * const ShaderCodeDictionary* shaderCodeDictionary() const {
   *         return fRecorder->fSharedContext->shaderCodeDictionary();
   *     }
   * ```
   */
  public fun shaderCodeDictionary(): ShaderCodeDictionary {
    TODO("Implement shaderCodeDictionary")
  }

  /**
   * C++ original:
   * ```cpp
   * ShaderCodeDictionary* shaderCodeDictionary() {
   *         return fRecorder->fSharedContext->shaderCodeDictionary();
   *     }
   * ```
   */
  public fun rendererProvider(): RendererProvider {
    TODO("Implement rendererProvider")
  }

  /**
   * C++ original:
   * ```cpp
   * const RendererProvider* rendererProvider() const {
   *         return fRecorder->fSharedContext->rendererProvider();
   *     }
   * ```
   */
  public fun isProtected(): Protected {
    TODO("Implement isProtected")
  }

  /**
   * C++ original:
   * ```cpp
   * Protected isProtected() const {
   *         return fRecorder->fSharedContext->isProtected();
   *     }
   * ```
   */
  public fun rootUploadList(): UploadList {
    TODO("Implement rootUploadList")
  }

  /**
   * C++ original:
   * ```cpp
   * UploadList* rootUploadList() { return fRecorder->fRootUploads.get(); }
   * ```
   */
  public fun drawBufferManager(): DrawBufferManager {
    TODO("Implement drawBufferManager")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawBufferManager* drawBufferManager() { return fRecorder->fDrawBufferManager.get(); }
   * ```
   */
  public fun uploadBufferManager(): UploadBufferManager {
    TODO("Implement uploadBufferManager")
  }

  /**
   * C++ original:
   * ```cpp
   * UploadBufferManager* uploadBufferManager() { return fRecorder->fUploadBufferManager.get(); }
   * ```
   */
  public fun floatStorageManager(): Int {
    TODO("Implement floatStorageManager")
  }

  /**
   * C++ original:
   * ```cpp
   * FloatStorageManager* floatStorageManager() { return fRecorder->fFloatStorageManager.get(); }
   * ```
   */
  public fun refFloatStorageManager(): Int {
    TODO("Implement refFloatStorageManager")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<FloatStorageManager> refFloatStorageManager() { return fRecorder->fFloatStorageManager; }
   * ```
   */
  public fun atlasProvider(): AtlasProvider {
    TODO("Implement atlasProvider")
  }

  /**
   * C++ original:
   * ```cpp
   * AtlasProvider* atlasProvider() { return fRecorder->fAtlasProvider.get(); }
   * ```
   */
  public fun tokenTracker(): TokenTracker {
    TODO("Implement tokenTracker")
  }

  /**
   * C++ original:
   * ```cpp
   * TokenTracker* tokenTracker() { return fRecorder->fTokenTracker.get(); }
   * ```
   */
  public fun strikeCache(): StrikeCache {
    TODO("Implement strikeCache")
  }

  /**
   * C++ original:
   * ```cpp
   * sktext::gpu::StrikeCache* strikeCache() { return fRecorder->fStrikeCache.get(); }
   * ```
   */
  public fun textBlobCache(): TextBlobRedrawCoordinator {
    TODO("Implement textBlobCache")
  }

  /**
   * C++ original:
   * ```cpp
   * sktext::gpu::TextBlobRedrawCoordinator* textBlobCache() {
   *         return fRecorder->fTextBlobCache.get();
   *     }
   * ```
   */
  public fun proxyCache(): ProxyCache {
    TODO("Implement proxyCache")
  }

  /**
   * C++ original:
   * ```cpp
   * ProxyCache* proxyCache() { return this->resourceProvider()->proxyCache(); }
   * ```
   */
  public fun addPendingRead(proxy: TextureProxy?) {
    TODO("Implement addPendingRead")
  }

  /**
   * C++ original:
   * ```cpp
   * void RecorderPriv::addPendingRead(const TextureProxy* proxy) {
   *     ASSERT_SINGLE_OWNER_PRIV
   *     fRecorder->fProxyReadCounts->increment(proxy);
   * }
   * ```
   */
  public fun uniqueID(): Int {
    TODO("Implement uniqueID")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t uniqueID() const { return fRecorder->fUniqueID; }
   * ```
   */
  public fun getResourceCacheLimit(): Int {
    TODO("Implement getResourceCacheLimit")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t RecorderPriv::getResourceCacheLimit() const {
   *     return fRecorder->fResourceProvider->getResourceCacheLimit();
   * }
   * ```
   */
  public fun deviceIsRegistered(device: Device?): Boolean {
    TODO("Implement deviceIsRegistered")
  }

  /**
   * C++ original:
   * ```cpp
   * bool RecorderPriv::deviceIsRegistered(Device* device) const {
   *     ASSERT_SINGLE_OWNER_PRIV
   *     for (const sk_sp<Device>& currentDevice : fRecorder->fTrackedDevices) {
   *         if (device == currentDevice.get()) {
   *             return true;
   *         }
   *     }
   *     return false;
   * }
   * ```
   */
  public fun resourceCache(): ResourceCache {
    TODO("Implement resourceCache")
  }

  /**
   * C++ original:
   * ```cpp
   * ResourceCache* resourceCache() { return fRecorder->fResourceProvider->resourceCache(); }
   * ```
   */
  public fun sharedContext(): Int {
    TODO("Implement sharedContext")
  }

  /**
   * C++ original:
   * ```cpp
   * SharedContext* sharedContext() { return fRecorder->fSharedContext.get(); }
   * ```
   */
  public fun setContext(context: Context?) {
    TODO("Implement setContext")
  }

  /**
   * C++ original:
   * ```cpp
   * void RecorderPriv::setContext(Context* context) {
   *     fRecorder->fContext = context;
   * }
   * ```
   */
  public fun context(): Context {
    TODO("Implement context")
  }

  /**
   * C++ original:
   * ```cpp
   * Context* context() { return fRecorder->fContext; }
   * ```
   */
  public fun issueFlushToken() {
    TODO("Implement issueFlushToken")
  }

  /**
   * C++ original:
   * ```cpp
   * void RecorderPriv::issueFlushToken() {
   *     fRecorder->fTokenTracker->issueFlushToken();
   * }
   * ```
   */
  private fun assign(param0: RecorderPriv) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * RecorderPriv& operator=(const RecorderPriv&) = delete
   * ```
   */
  private fun addressOf(): RecorderPriv {
    TODO("Implement addressOf")
  }

  /**
   * C++ original:
   * ```cpp
   * const RecorderPriv* operator&() const = delete
   * ```
   */
  public fun flushTrackedDevices() {
    TODO("Implement flushTrackedDevices")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<TextureProxy> RecorderPriv::CreateCachedProxy(Recorder* recorder,
     *                                                     const SkBitmap& bitmap,
     *                                                     std::string_view label) {
     *     SkASSERT(!bitmap.isNull());
     *
     *     if (!recorder) {
     *         return nullptr;
     *     }
     *     return recorder->priv().proxyCache()->findOrCreateCachedProxy(recorder,
     *                                                                   bitmap,
     *                                                                   std::move(label));
     * }
     * ```
     */
    public fun createCachedProxy(
      recorder: Recorder?,
      bitmap: SkBitmap,
      label: String,
    ): Int {
      TODO("Implement createCachedProxy")
    }
  }
}
