package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import org.skia.core.Half2
import org.skia.core.SkStrokeRec
import org.skia.math.SkIRect
import org.skia.math.SkIVector

/**
 * C++ original:
 * ```cpp
 * class RasterPathAtlas : public PathAtlas {
 * public:
 *     explicit RasterPathAtlas(Recorder* recorder);
 *     ~RasterPathAtlas() override {}
 *     void recordUploads(DrawContext*);
 *
 *     void compact() {
 *         fCachedAtlasMgr.compact(fRecorder);
 *         fSmallPathAtlasMgr.compact(fRecorder);
 *         fUncachedAtlasMgr.compact(fRecorder);
 *     }
 *
 *     void freeGpuResources() {
 *         fCachedAtlasMgr.freeGpuResources(fRecorder);
 *         fSmallPathAtlasMgr.freeGpuResources(fRecorder);
 *         fUncachedAtlasMgr.freeGpuResources(fRecorder);
 *     }
 *
 *     void evictAtlases() {
 *         fCachedAtlasMgr.evictAll();
 *         fSmallPathAtlasMgr.evictAll();
 *         fUncachedAtlasMgr.evictAll();
 *     }
 *
 * protected:
 *     sk_sp<TextureProxy> onAddShape(const Shape&,
 *                                    const Transform& localToDevice,
 *                                    const SkStrokeRec&,
 *                                    skvx::half2 maskOrigin,
 *                                    skvx::half2 maskSize,
 *                                    SkIVector transformedMaskOffset,
 *                                    skvx::half2* outPos) override;
 * private:
 *     class RasterAtlasMgr : public PathAtlas::DrawAtlasMgr {
 *     public:
 *         RasterAtlasMgr(size_t width, size_t height,
 *                        size_t plotWidth, size_t plotHeight,
 *                        const Caps* caps)
 *             : PathAtlas::DrawAtlasMgr(width, height, plotWidth, plotHeight,
 *                                       DrawAtlas::UseStorageTextures::kNo,
 *                                       /*label=*/"RasterPathAtlas", caps) {}
 *
 *     protected:
 *         bool onAddToAtlas(const Shape&,
 *                           const Transform& localToDevice,
 *                           const SkStrokeRec&,
 *                           SkIRect shapeBounds,
 *                           SkIVector transformedMaskOffset,
 *                           const AtlasLocator&) override;
 *     };
 *
 *     RasterAtlasMgr fCachedAtlasMgr;
 *     RasterAtlasMgr fSmallPathAtlasMgr;
 *     RasterAtlasMgr fUncachedAtlasMgr;
 * }
 * ```
 */
public open class RasterPathAtlas public constructor(
  recorder: Recorder?,
) : PathAtlas() {
  /**
   * C++ original:
   * ```cpp
   * RasterAtlasMgr fCachedAtlasMgr
   * ```
   */
  protected var fCachedAtlasMgr: RasterAtlasMgr = TODO("Initialize fCachedAtlasMgr")

  /**
   * C++ original:
   * ```cpp
   * RasterAtlasMgr fSmallPathAtlasMgr
   * ```
   */
  protected var fSmallPathAtlasMgr: RasterAtlasMgr = TODO("Initialize fSmallPathAtlasMgr")

  /**
   * C++ original:
   * ```cpp
   * RasterAtlasMgr fUncachedAtlasMgr
   * ```
   */
  protected var fUncachedAtlasMgr: RasterAtlasMgr = TODO("Initialize fUncachedAtlasMgr")

  /**
   * C++ original:
   * ```cpp
   * void RasterPathAtlas::recordUploads(DrawContext* dc) {
   *     fCachedAtlasMgr.recordUploads(dc, fRecorder);
   *     fSmallPathAtlasMgr.recordUploads(dc, fRecorder);
   *     fUncachedAtlasMgr.recordUploads(dc, fRecorder);
   * }
   * ```
   */
  public fun recordUploads(dc: DrawContext?) {
    TODO("Implement recordUploads")
  }

  /**
   * C++ original:
   * ```cpp
   * void compact() {
   *         fCachedAtlasMgr.compact(fRecorder);
   *         fSmallPathAtlasMgr.compact(fRecorder);
   *         fUncachedAtlasMgr.compact(fRecorder);
   *     }
   * ```
   */
  public fun compact() {
    TODO("Implement compact")
  }

  /**
   * C++ original:
   * ```cpp
   * void freeGpuResources() {
   *         fCachedAtlasMgr.freeGpuResources(fRecorder);
   *         fSmallPathAtlasMgr.freeGpuResources(fRecorder);
   *         fUncachedAtlasMgr.freeGpuResources(fRecorder);
   *     }
   * ```
   */
  public fun freeGpuResources() {
    TODO("Implement freeGpuResources")
  }

  /**
   * C++ original:
   * ```cpp
   * void evictAtlases() {
   *         fCachedAtlasMgr.evictAll();
   *         fSmallPathAtlasMgr.evictAll();
   *         fUncachedAtlasMgr.evictAll();
   *     }
   * ```
   */
  public fun evictAtlases() {
    TODO("Implement evictAtlases")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<TextureProxy> RasterPathAtlas::onAddShape(const Shape& shape,
   *                                                 const Transform& localToDevice,
   *                                                 const SkStrokeRec& strokeRec,
   *                                                 skvx::half2 maskOrigin,
   *                                                 skvx::half2 maskSize,
   *                                                 SkIVector transformedMaskOffset,
   *                                                 skvx::half2* outPos) {
   *     sk_sp<TextureProxy> proxy;
   *
   *     if (!shape.isVolatilePath()) {
   *         constexpr int kMaxSmallPathSize = 162;
   *         // Try to locate or add to cached DrawAtlas
   *         if (maskSize.x() <= kMaxSmallPathSize && maskSize.y() <= kMaxSmallPathSize) {
   *             proxy = fSmallPathAtlasMgr.findOrCreateEntry(fRecorder,
   *                                                          shape,
   *                                                          localToDevice,
   *                                                          strokeRec,
   *                                                          maskOrigin,
   *                                                          maskSize,
   *                                                          transformedMaskOffset,
   *                                                          outPos);
   *         }
   *         if (!proxy) {
   *             proxy = fCachedAtlasMgr.findOrCreateEntry(fRecorder,
   *                                                       shape,
   *                                                       localToDevice,
   *                                                       strokeRec,
   *                                                       maskOrigin,
   *                                                       maskSize,
   *                                                       transformedMaskOffset,
   *                                                       outPos);
   *         }
   *     }
   *
   *     // Try to add to uncached DrawAtlas
   *     if (!proxy) {
   *         AtlasLocator loc;
   *         proxy = fUncachedAtlasMgr.addToAtlas(fRecorder,
   *                                              shape,
   *                                              localToDevice,
   *                                              strokeRec,
   *                                              maskSize,
   *                                              transformedMaskOffset,
   *                                              outPos,
   *                                              &loc);
   *     }
   *     if (proxy) {
   *         return proxy;
   *     }
   *
   *     // Failed to add to atlases, try to add to ProxyCache
   *     skgpu::UniqueKey maskKey = GeneratePathMaskKey(shape, localToDevice, strokeRec,
   *                                                    maskOrigin, maskSize);
   *     struct PathDrawContext {
   *         const Shape& fShape;
   *         const Transform& fLocalToDevice;
   *         const SkStrokeRec& fStrokeRec;
   *         SkIRect fShapeBounds;
   *         SkIVector fTransformedMaskOffset;
   *     } context = { shape, localToDevice, strokeRec,
   *                   SkIRect::MakeSize({maskSize.x(), maskSize.y()}).makeOffset(kEntryPadding,
   *                                                                              kEntryPadding),
   *                   transformedMaskOffset };
   *     sk_sp<TextureProxy> cachedProxy = fRecorder->priv().proxyCache()->findOrCreateCachedProxy(
   *             fRecorder, maskKey, &context,
   *             [](const void* ctx) {
   *                 const PathDrawContext* pdc = static_cast<const PathDrawContext*>(ctx);
   *                 auto [bm, helper] = RasterMaskHelper::Allocate(
   *                         pdc->fShapeBounds.size(),
   *                         -pdc->fTransformedMaskOffset,
   *                         kEntryPadding);
   *                 helper.drawShape(pdc->fShape, pdc->fLocalToDevice, pdc->fStrokeRec);
   *                 bm.setImmutable();
   *                 return bm;
   *             });
   *
   *     *outPos = { kEntryPadding, kEntryPadding };
   *     return cachedProxy;
   * }
   * ```
   */
  protected override fun onAddShape(
    shape: Shape,
    localToDevice: Transform,
    strokeRec: SkStrokeRec,
    maskOrigin: Half2,
    maskSize: Half2,
    transformedMaskOffset: SkIVector,
    outPos: Half2?,
  ): Int {
    TODO("Implement onAddShape")
  }

  public open class RasterAtlasMgr public constructor(
    width: ULong,
    height: ULong,
    plotWidth: ULong,
    plotHeight: ULong,
    caps: Caps?,
  ) : PathAtlas.DrawAtlasMgr(TODO(), TODO(), TODO(), TODO(), TODO(), TODO(), TODO()) {
    protected override fun onAddToAtlas(
      shape: Shape,
      localToDevice: Transform,
      strokeRec: SkStrokeRec,
      shapeBounds: SkIRect,
      transformedMaskOffset: SkIVector,
      locator: AtlasLocator,
    ): Boolean {
      TODO("Implement onAddToAtlas")
    }
  }
}
