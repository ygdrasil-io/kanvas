package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.ULong
import org.skia.math.SkIPoint
import org.skia.math.SkIRect

/**
 * C++ original:
 * ```cpp
 * class ClipAtlasManager {
 * public:
 *     ClipAtlasManager(Recorder* recorder);
 *     ~ClipAtlasManager() = default;
 *
 *     sk_sp<TextureProxy> findOrCreateEntry(uint32_t stackRecordID,
 *                                           const ClipStack::ElementList*,
 *                                           SkIRect maskDeviceBounds,
 *                                           SkIPoint* outPos);
 *
 *     bool recordUploads(DrawContext* dc);
 *     void compact();
 *     void freeGpuResources();
 *
 *     void evictAtlases();
 *
 * private:
 *     // Wrapper class to manage DrawAtlas and associated caching operations
 *     class DrawAtlasMgr : public AtlasGenerationCounter, public PlotEvictionCallback {
 *     public:
 *         DrawAtlasMgr(size_t width, size_t height,
 *                      size_t plotWidth, size_t plotHeight,
 *                      DrawAtlas::UseStorageTextures useStorageTextures,
 *                      std::string_view label, const Caps*);
 *
 *         sk_sp<TextureProxy> findOrCreateEntry(Recorder* recorder,
 *                                               const skgpu::UniqueKey&,
 *                                               const ClipStack::ElementList*,
 *                                               SkIRect maskDeviceBounds,
 *                                               SkIRect keyBounds,
 *                                               SkIPoint* outPos);
 *         // Adds to DrawAtlas but not the cache
 *         sk_sp<TextureProxy> addToAtlas(Recorder* recorder,
 *                                        const ClipStack::ElementList*,
 *                                        SkIRect maskDeviceBounds,
 *                                        SkIPoint* outPos,
 *                                        AtlasLocator* locator);
 *         bool recordUploads(DrawContext*, Recorder*);
 *         void evict(PlotLocator) override;
 *         void compact(Recorder*);
 *         void freeGpuResources(Recorder*);
 *
 *         void evictAll();
 *
 *     private:
 *         std::unique_ptr<DrawAtlas> fDrawAtlas;
 *
 *         // Tracks whether a combined clip mask is already in the DrawAtlas and its location
 *         struct MaskHashEntry {
 *             SkIRect fBounds;
 *             AtlasLocator fLocator;
 *             MaskHashEntry* fNext = nullptr;
 *         };
 *         struct UniqueKeyHash {
 *             uint32_t operator()(const skgpu::UniqueKey& key) const { return key.hash(); }
 *         };
 *         using MaskCache = skia_private::THashMap<skgpu::UniqueKey, MaskHashEntry, UniqueKeyHash>;
 *         MaskCache fMaskCache;
 *         int fHashEntryCount = 0;
 *
 *         // List of stored keys per Plot, used to invalidate cache entries.
 *         // When a Plot is invalidated via evict(), we'll get its index and Page index from the
 *         // PlotLocator, index into the fKeyLists array to get the MaskKeyList for that Plot,
 *         // then iterate through the list and remove entries matching those keys from the MaskCache.
 *         struct MaskKeyEntry {
 *             skgpu::UniqueKey fKey;
 *             SkIRect fBounds;
 *             SK_DECLARE_INTERNAL_LLIST_INTERFACE(MaskKeyEntry);
 *         };
 *         using MaskKeyList = SkTInternalLList<MaskKeyEntry>;
 *         SkTDArray<MaskKeyList> fKeyLists;
 *         int fListEntryCount = 0;
 *     };
 *
 *     Recorder* fRecorder;
 *     // We have two atlas managers, one for clips that can be keyed via the path keys,
 *     // and a smaller one for those that can only be keyed by the SaveRecord ID. We keep
 *     // them separate because the SaveRecord keyed clips will be far more transient, i.e.,
 *     // once the SaveRecord is popped they'll never be used again.
 *     DrawAtlasMgr fPathKeyAtlasMgr;
 *     DrawAtlasMgr fSaveRecordKeyAtlasMgr;
 * }
 * ```
 */
public data class ClipAtlasManager public constructor(
  /**
   * C++ original:
   * ```cpp
   * Recorder* fRecorder
   * ```
   */
  private var fRecorder: Recorder?,
  /**
   * C++ original:
   * ```cpp
   * DrawAtlasMgr fPathKeyAtlasMgr
   * ```
   */
  private var fPathKeyAtlasMgr: DrawAtlasMgr,
  /**
   * C++ original:
   * ```cpp
   * DrawAtlasMgr fSaveRecordKeyAtlasMgr
   * ```
   */
  private var fSaveRecordKeyAtlasMgr: DrawAtlasMgr,
) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<TextureProxy> ClipAtlasManager::findOrCreateEntry(uint32_t stackRecordID,
   *                                                         const ClipStack::ElementList* elementList,
   *                                                         SkIRect maskDeviceBounds,
   *                                                         SkIPoint* outPos) {
   *     // For the ClipAtlas cache, we don't include the bounds in the key
   *     skgpu::UniqueKey maskKey;
   *     bool usesPathKey;
   *     // The keyBounds are the maskDeviceBounds relative to the full transformed mask. We use this
   *     // to ensure we capture the situation where the maskDeviceBounds are equal in two cases but
   *     // actually enclose different regions of the full mask due to a difference in integer
   *     // translation (which is not captured in the key) in the element transforms.
   *     SkIRect keyBounds;
   *     maskKey = GenerateClipMaskKey(stackRecordID, elementList, maskDeviceBounds,
   *                                   /*includeBounds=*/false, &keyBounds, &usesPathKey);
   *
   *     sk_sp<TextureProxy> atlasProxy;
   *     if (usesPathKey) {
   *         atlasProxy = fPathKeyAtlasMgr.findOrCreateEntry(fRecorder, maskKey, elementList,
   *                                                         maskDeviceBounds, keyBounds, outPos);
   *     } else {
   *         atlasProxy = fSaveRecordKeyAtlasMgr.findOrCreateEntry(fRecorder, maskKey, elementList,
   *                                                               maskDeviceBounds, keyBounds, outPos);
   *     }
   *     if (atlasProxy) {
   *         return atlasProxy;
   *     }
   *
   *     // We need to include the bounds in the key when using the ProxyCache
   *     maskKey = GenerateClipMaskKey(stackRecordID, elementList, maskDeviceBounds,
   *                                   /*includeBounds=*/true, &keyBounds, &usesPathKey);
   *
   *     const struct ClipDrawContext {
   *         const ClipStack::ElementList* fElementList;
   *         SkIRect fMaskDeviceBounds;
   *     } context = {elementList, maskDeviceBounds};
   *     sk_sp<TextureProxy> proxy = fRecorder->priv().proxyCache()->findOrCreateCachedProxy(
   *             fRecorder, maskKey, &context,
   *             [](const void* ctx) {
   *                 const ClipDrawContext* cdc = static_cast<const ClipDrawContext*>(ctx);
   *                 auto translate =
   *                         -cdc->fMaskDeviceBounds.topLeft() + SkIVector{kEntryPadding, kEntryPadding};
   *                 auto [bm, helper] =
   *                         RasterMaskHelper::Allocate(cdc->fMaskDeviceBounds.size(),
   *                                                    translate,
   *                                                    0,
   *                                                    initial_alpha_for_elements(*cdc->fElementList));
   *
   *                 render_elements(&helper, *cdc->fElementList);
   *                 bm.setImmutable();
   *                 return bm;
   *             });
   *     *outPos = { kEntryPadding, kEntryPadding };
   *
   *     return proxy;
   * }
   * ```
   */
  public fun findOrCreateEntry(
    stackRecordID: UInt,
    elementList: ClipStack.ElementList?,
    maskDeviceBounds: SkIRect,
    outPos: SkIPoint?,
  ): Int {
    TODO("Implement findOrCreateEntry")
  }

  /**
   * C++ original:
   * ```cpp
   * bool ClipAtlasManager::recordUploads(DrawContext* dc) {
   *     return fPathKeyAtlasMgr.recordUploads(dc, fRecorder) ||
   *            fSaveRecordKeyAtlasMgr.recordUploads(dc, fRecorder);
   * }
   * ```
   */
  public fun recordUploads(dc: DrawContext?): Boolean {
    TODO("Implement recordUploads")
  }

  /**
   * C++ original:
   * ```cpp
   * void ClipAtlasManager::compact() {
   *     fPathKeyAtlasMgr.compact(fRecorder);
   *     fSaveRecordKeyAtlasMgr.compact(fRecorder);
   * }
   * ```
   */
  public fun compact() {
    TODO("Implement compact")
  }

  /**
   * C++ original:
   * ```cpp
   * void ClipAtlasManager::freeGpuResources() {
   *     fPathKeyAtlasMgr.freeGpuResources(fRecorder);
   *     fSaveRecordKeyAtlasMgr.freeGpuResources(fRecorder);
   * }
   * ```
   */
  public fun freeGpuResources() {
    TODO("Implement freeGpuResources")
  }

  /**
   * C++ original:
   * ```cpp
   * void ClipAtlasManager::evictAtlases() {
   *     fPathKeyAtlasMgr.evictAll();
   *     fSaveRecordKeyAtlasMgr.evictAll();
   * }
   * ```
   */
  public fun evictAtlases() {
    TODO("Implement evictAtlases")
  }

  public open class DrawAtlasMgr public constructor(
    width: ULong,
    height: ULong,
    plotWidth: ULong,
    plotHeight: ULong,
    useStorageTextures: DrawAtlas.UseStorageTextures,
    label: String,
    param6: Caps,
  ) : AtlasGenerationCounter(),
      PlotEvictionCallback {
    private var fDrawAtlas: Int = TODO("Initialize fDrawAtlas")

    private var fMaskCache: Int = TODO("Initialize fMaskCache")

    private var fHashEntryCount: Int = TODO("Initialize fHashEntryCount")

    private var fKeyLists: Int = TODO("Initialize fKeyLists")

    private var fListEntryCount: Int = TODO("Initialize fListEntryCount")

    public constructor(
      width: ULong,
      height: ULong,
      plotWidth: ULong,
      plotHeight: ULong,
      useStorageTextures: DrawAtlas.UseStorageTextures,
      label: String,
      caps: Caps?,
    ) : this() {
      TODO("Implement constructor")
    }

    public fun findOrCreateEntry(
      recorder: Recorder?,
      maskKey: UniqueKey,
      elementList: ClipStack.ElementList?,
      maskDeviceBounds: SkIRect,
      keyBounds: SkIRect,
      outPos: SkIPoint?,
    ): Int {
      TODO("Implement findOrCreateEntry")
    }

    public fun addToAtlas(
      recorder: Recorder?,
      elementsForMask: ClipStack.ElementList?,
      maskDeviceBounds: SkIRect,
      outPos: SkIPoint?,
      locator: AtlasLocator?,
    ): Int {
      TODO("Implement addToAtlas")
    }

    public fun recordUploads(dc: DrawContext?, recorder: Recorder?): Boolean {
      TODO("Implement recordUploads")
    }

    public override fun evict(plotLocator: PlotLocator) {
      TODO("Implement evict")
    }

    public fun compact(recorder: Recorder?) {
      TODO("Implement compact")
    }

    public fun freeGpuResources(recorder: Recorder?) {
      TODO("Implement freeGpuResources")
    }

    public fun evictAll() {
      TODO("Implement evictAll")
    }

    public data class MaskHashEntry public constructor(
      public var fBounds: Int,
      public var fLocator: Int,
      public var fNext: MaskHashEntry?,
    )

    public open class UniqueKeyHash {
      public operator fun invoke(key: UniqueKey): Int {
        TODO("Implement invoke")
      }
    }

    public data class MaskKeyEntry public constructor(
      public var fKey: Int,
      public var fBounds: Int,
    )
  }
}
