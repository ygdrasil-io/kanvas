package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.ULong
import org.skia.core.Half2
import org.skia.core.SkStrokeRec
import org.skia.math.SkIRect
import org.skia.math.SkIVector

/**
 * C++ original:
 * ```cpp
 * class PathAtlas {
 * public:
 *     /**
 *      * The PathAtlas will use textures of the requested size or the system's maximum texture size,
 *      * whichever is smaller.
 *      */
 *     PathAtlas(Recorder* recorder, uint32_t requestedWidth, uint32_t requestedHeight);
 *     virtual ~PathAtlas();
 *
 *     using MaskAndOrigin = std::pair<CoverageMaskShape, SkIPoint>;
 *
 *     // Subclasses should ensure that the recorded masks have this much padding around each entry.
 *     // PathAtlas passes in un-padded sizes to onAddShape and assumes that padding has been included
 *     // in the outPos value.
 *     static constexpr int kEntryPadding = 1;
 *
 *     /**
 *      * Searches the atlas for a slot that can fit a coverage mask for a clipped shape with the given
 *      * bounds in device coordinates and submits the mask to be drawn into the found atlas region.
 *      * For atlases that cache coverage masks, will first search the cache before adding.
 *      *
 *      * Returns an empty result if a the shape cannot fit in the atlas. Otherwise, returns the
 *      * CoverageMaskShape (including the texture proxy) for sampling the eventually-rendered coverage
 *      * mask and the device-space origin the mask should be drawn at (e.g. its recorded draw should
 *      * be an integer translation matrix), and the Renderer that should be used to draw that shape.
 *      * The Renderer should have single-channel coverage, require AA bounds outsetting, and have a
 *      * single renderStep.
 *      *
 *      * The bounds of the atlas entry is laid out with a 1 pixel outset from the given dimensions.
 *      * The returned shape's UV origin accounts for the padding, and its mask size does not include
 *      * the padding. This allows the mask to be sampled safely with linear filtering without worrying
 *      * about HW filtering accessing pixels from other entries.
 *      *
 *      * `shape` will be drawn after applying the linear components (scale, rotation, skew) of the
 *      * provided `localToDevice` transform. This is done by  translating the shape by the inverse of
 *      * the rounded out `transformedShapeBounds` offset. For an unclipped shape this amounts to
 *      * translating it back to its origin while preserving any sub-pixel translation. For a clipped
 *      * shape, this ensures that the visible portions of the mask are centered in the atlas slot
 *      * while invisible portions that would lie outside the atlas slot get clipped out.
 *      *
 *      * `addShape()` schedules the shape to be drawn but when and how the rendering happens is
 *      * specified by the subclass implementation.
 *      *
 *      * The stroke-and-fill style is drawn as a single combined coverage mask containing the stroke
 *      * and the fill.
 *      */
 *     std::pair<const Renderer*, std::optional<MaskAndOrigin>> addShape(
 *             const Rect& transformedShapeBounds,
 *             const Shape& shape,
 *             const Transform& localToDevice,
 *             const SkStrokeRec& style);
 *
 *     /**
 *      * Returns true if a path coverage mask with the given device-space bounds is sufficiently
 *      * small to benefit from atlasing without causing too many atlas renders.
 *      *
 *      * `transformedShapeBounds` represents the device-space bounds of the coverage mask shape
 *      * unrestricted by clip and viewport bounds.
 *      *
 *      * `clipBounds` represents the conservative bounding box of the union of the clip stack that
 *      * should apply to the shape.
 *      */
 *     virtual bool isSuitableForAtlasing(const Rect& transformedShapeBounds,
 *                                        const Rect& clipBounds) const {
 *         return true;
 *     }
 *
 *     uint32_t width() const { return fWidth; }
 *     uint32_t height() const { return fHeight; }
 *
 * protected:
 *     // The 'transform' has been adjusted to draw the Shape into a logical image from (0,0) to
 *     // 'maskSize'. The actual rendering into the returned TextureProxy will need to be further
 *     // translated by the value written to 'outPos', which is the responsibility of subclasses.
 *     virtual sk_sp<TextureProxy> onAddShape(const Shape&,
 *                                            const Transform& localToDevice,
 *                                            const SkStrokeRec&,
 *                                            skvx::half2 maskOrigin,
 *                                            skvx::half2 maskSize,
 *                                            SkIVector transformedMaskOffset,
 *                                            skvx::half2* outPos) = 0;
 *
 *     // Wrapper class to manage DrawAtlas and associated caching operations
 *     class DrawAtlasMgr : public AtlasGenerationCounter, public PlotEvictionCallback {
 *     public:
 *         // Adds to the DrawAtlas and shape cache.
 *         // If successful, returns a ref for the caller to use.
 *         sk_sp<TextureProxy> findOrCreateEntry(Recorder* recorder,
 *                                               const Shape&,
 *                                               const Transform& localToDevice,
 *                                               const SkStrokeRec&,
 *                                               skvx::half2 maskOrigin,
 *                                               skvx::half2 maskSize,
 *                                               SkIVector transformedMaskOffset,
 *                                               skvx::half2* outPos);
 *         // Adds to DrawAtlas but not the cache.
 *         // If successful, returns a ref for the caller to use.
 *         sk_sp<TextureProxy> addToAtlas(Recorder* recorder,
 *                                        const Shape&,
 *                                        const Transform& localToDevice,
 *                                        const SkStrokeRec&,
 *                                        skvx::half2 maskSize,
 *                                        SkIVector transformedMaskOffset,
 *                                        skvx::half2* outPos,
 *                                        AtlasLocator* locator);
 *         bool recordUploads(DrawContext*, Recorder*);
 *         void evict(PlotLocator) override;
 *         void compact(Recorder*);
 *         void freeGpuResources(Recorder*);
 *
 *         void evictAll();
 *
 *     protected:
 *         DrawAtlasMgr(size_t width, size_t height,
 *                      size_t plotWidth, size_t plotHeight,
 *                      DrawAtlas::UseStorageTextures useStorageTextures,
 *                      std::string_view label, const Caps*);
 *
 *         bool virtual onAddToAtlas(const Shape&,
 *                                   const Transform& localToDevice,
 *                                   const SkStrokeRec&,
 *                                   SkIRect shapeBounds,
 *                                   SkIVector transformedMaskOffset,
 *                                   const AtlasLocator&) = 0;
 *
 *         std::unique_ptr<DrawAtlas> fDrawAtlas;
 *
 *     private:
 *         // Tracks whether a shape is already in the DrawAtlas, and its location in the atlas
 *         struct UniqueKeyHash {
 *             uint32_t operator()(const skgpu::UniqueKey& key) const { return key.hash(); }
 *         };
 *         using ShapeCache = skia_private::THashMap<skgpu::UniqueKey, AtlasLocator, UniqueKeyHash>;
 *         ShapeCache fShapeCache;
 *
 *         // List of stored keys per Plot, used to invalidate cache entries.
 *         // When a Plot is invalidated via evict(), we'll get its index and Page index from the
 *         // PlotLocator, index into the fKeyLists array to get the ShapeKeyList for that Plot,
 *         // then iterate through the list and remove entries matching those keys from the ShapeCache.
 *         struct ShapeKeyEntry {
 *             skgpu::UniqueKey fKey;
 *             SK_DECLARE_INTERNAL_LLIST_INTERFACE(ShapeKeyEntry);
 *         };
 *         using ShapeKeyList = SkTInternalLList<ShapeKeyEntry>;
 *         SkTDArray<ShapeKeyList> fKeyLists;
 *     };
 *
 *     // The Recorder that created and owns this Atlas.
 *     Recorder* fRecorder;
 *
 *     uint32_t fWidth = 0;
 *     uint32_t fHeight = 0;
 * }
 * ```
 */
public abstract class PathAtlas public constructor(
  recorder: Recorder?,
  requestedWidth: UInt,
  requestedHeight: UInt,
) {
  /**
   * C++ original:
   * ```cpp
   * static constexpr int kEntryPadding = 1
   * ```
   */
  private var fRecorder: Recorder? = TODO("Initialize fRecorder")

  /**
   * C++ original:
   * ```cpp
   * Recorder* fRecorder
   * ```
   */
  private var fWidth: Int = TODO("Initialize fWidth")

  /**
   * C++ original:
   * ```cpp
   * uint32_t fWidth
   * ```
   */
  private var fHeight: Int = TODO("Initialize fHeight")

  /**
   * C++ original:
   * ```cpp
   * virtual bool isSuitableForAtlasing(const Rect& transformedShapeBounds,
   *                                        const Rect& clipBounds) const {
   *         return true;
   *     }
   * ```
   */
  public open fun isSuitableForAtlasing(transformedShapeBounds: Rect, clipBounds: Rect): Boolean {
    TODO("Implement isSuitableForAtlasing")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t width() const { return fWidth; }
   * ```
   */
  public fun width(): Int {
    TODO("Implement width")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t height() const { return fHeight; }
   * ```
   */
  public fun height(): Int {
    TODO("Implement height")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<TextureProxy> onAddShape(const Shape&,
   *                                            const Transform& localToDevice,
   *                                            const SkStrokeRec&,
   *                                            skvx::half2 maskOrigin,
   *                                            skvx::half2 maskSize,
   *                                            SkIVector transformedMaskOffset,
   *                                            skvx::half2* outPos) = 0
   * ```
   */
  protected abstract fun onAddShape(
    param0: Shape,
    localToDevice: Transform,
    param2: SkStrokeRec,
    maskOrigin: Half2,
    maskSize: Half2,
    transformedMaskOffset: SkIVector,
    outPos: Half2?,
  ): Int

  public abstract class DrawAtlasMgr public constructor(
    width: ULong,
    height: ULong,
    plotWidth: ULong,
    plotHeight: ULong,
    useStorageTextures: DrawAtlas.UseStorageTextures,
    label: String,
    param6: Caps,
  ) : AtlasGenerationCounter(),
      PlotEvictionCallback {
    protected var fDrawAtlas: Int = TODO("Initialize fDrawAtlas")

    private var fShapeCache: Int = TODO("Initialize fShapeCache")

    private var fKeyLists: Int = TODO("Initialize fKeyLists")

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
      shape: Shape,
      localToDevice: Transform,
      strokeRec: SkStrokeRec,
      maskOrigin: Half2,
      maskSize: Half2,
      transformedMaskOffset: SkIVector,
      outPos: Half2?,
    ): Int {
      TODO("Implement findOrCreateEntry")
    }

    public fun addToAtlas(
      recorder: Recorder?,
      shape: Shape,
      localToDevice: Transform,
      strokeRec: SkStrokeRec,
      maskSize: Half2,
      transformedMaskOffset: SkIVector,
      outPos: Half2?,
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

    protected abstract fun onAddToAtlas(
      param0: Shape,
      localToDevice: Transform,
      param2: SkStrokeRec,
      shapeBounds: SkIRect,
      transformedMaskOffset: SkIVector,
      param5: AtlasLocator,
    ): Boolean

    public open class UniqueKeyHash {
      public operator fun invoke(key: UniqueKey): Int {
        TODO("Implement invoke")
      }
    }

    public data class ShapeKeyEntry public constructor(
      public var fKey: Int,
    )
  }

  public companion object {
    public val kEntryPadding: Int = TODO("Initialize kEntryPadding")
  }
}
