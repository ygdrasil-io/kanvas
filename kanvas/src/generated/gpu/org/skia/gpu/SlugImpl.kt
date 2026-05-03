package org.skia.gpu

import gpu.SubRunContainerOwner
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.core.GlyphRunList
import org.skia.core.SkStrikeDeviceInfo
import org.skia.core.StrikeForGPUCacheInterface
import org.skia.foundation.SkPaint
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.utils.SkStrikeClient
import org.skia.utils.Slug

/**
 * C++ original:
 * ```cpp
 * class SlugImpl final : public Slug {
 * public:
 *     SlugImpl(SubRunAllocator&& alloc,
 *              gpu::SubRunContainerOwner subRuns,
 *              SkRect sourceBounds,
 *              SkPoint origin);
 *     ~SlugImpl() override = default;
 *
 *     static sk_sp<SlugImpl> Make(const SkMatrix& viewMatrix,
 *                                 const sktext::GlyphRunList& glyphRunList,
 *                                 const SkPaint& paint,
 *                                 SkStrikeDeviceInfo strikeDeviceInfo,
 *                                 sktext::StrikeForGPUCacheInterface* strikeCache);
 *     static sk_sp<Slug> MakeFromBuffer(SkReadBuffer& buffer,
 *                                       const SkStrikeClient* client);
 *     void doFlatten(SkWriteBuffer& buffer) const override;
 *
 *     SkRect sourceBounds() const override { return fSourceBounds; }
 *     SkRect sourceBoundsWithOrigin() const override { return fSourceBounds.makeOffset(fOrigin); }
 *
 *     const SkMatrix& initialPositionMatrix() const { return fSubRuns->initialPosition(); }
 *     SkPoint origin() const { return fOrigin; }
 *
 *     const gpu::SubRunContainerOwner& subRuns() const { return fSubRuns; }
 *
 *     // Change memory management to handle the data after Slug, but in the same allocation
 *     // of memory. Only allow placement new.
 *     void operator delete(void* p) { ::operator delete(p); }
 *     void* operator new(size_t) { SK_ABORT("All slugs are created by placement new."); }
 *     void* operator new(size_t, void* p) { return p; }
 *
 * private:
 *     // The allocator must come first because it needs to be destroyed last. Other fields of this
 *     // structure may have pointers into it.
 *     SubRunAllocator fAlloc;
 *     gpu::SubRunContainerOwner fSubRuns;
 *     const SkRect fSourceBounds;
 *     const SkPoint fOrigin;
 * }
 * ```
 */
public class SlugImpl public constructor(
  alloc: SubRunAllocator,
  subRuns: SubRunContainerOwner,
  sourceBounds: SkRect,
  origin: SkPoint,
) : Slug() {
  /**
   * C++ original:
   * ```cpp
   * SubRunAllocator fAlloc
   * ```
   */
  private var fAlloc: Int = TODO("Initialize fAlloc")

  /**
   * C++ original:
   * ```cpp
   * gpu::SubRunContainerOwner fSubRuns
   * ```
   */
  private var fSubRuns: Int = TODO("Initialize fSubRuns")

  /**
   * C++ original:
   * ```cpp
   * const SkRect fSourceBounds
   * ```
   */
  private val fSourceBounds: Int = TODO("Initialize fSourceBounds")

  /**
   * C++ original:
   * ```cpp
   * const SkPoint fOrigin
   * ```
   */
  private val fOrigin: SkPaint = TODO("Initialize fOrigin")

  /**
   * C++ original:
   * ```cpp
   * void SlugImpl::doFlatten(SkWriteBuffer& buffer) const {
   *     buffer.writeRect(fSourceBounds);
   *     buffer.writePoint(fOrigin);
   *     fSubRuns->flattenAllocSizeHint(buffer);
   *     fSubRuns->flattenRuns(buffer);
   * }
   * ```
   */
  public override fun doFlatten(buffer: SkWriteBuffer) {
    TODO("Implement doFlatten")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect sourceBounds() const override { return fSourceBounds; }
   * ```
   */
  public override fun sourceBounds(): Int {
    TODO("Implement sourceBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect sourceBoundsWithOrigin() const override { return fSourceBounds.makeOffset(fOrigin); }
   * ```
   */
  public override fun sourceBoundsWithOrigin(): Int {
    TODO("Implement sourceBoundsWithOrigin")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkMatrix& initialPositionMatrix() const { return fSubRuns->initialPosition(); }
   * ```
   */
  public fun initialPositionMatrix(): SkMatrix {
    TODO("Implement initialPositionMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPoint origin() const { return fOrigin; }
   * ```
   */
  public fun origin(): SkPaint {
    TODO("Implement origin")
  }

  /**
   * C++ original:
   * ```cpp
   * const gpu::SubRunContainerOwner& subRuns() const { return fSubRuns; }
   * ```
   */
  public fun subRuns(): Int {
    TODO("Implement subRuns")
  }

  /**
   * C++ original:
   * ```cpp
   * void operator delete(void* p) { ::operator delete(p); }
   * ```
   */
  public fun toDelete(p: Unit?) {
    TODO("Implement toDelete")
  }

  /**
   * C++ original:
   * ```cpp
   * void* operator new(size_t) { SK_ABORT("All slugs are created by placement new."); }
   * ```
   */
  public fun toNew(param0: ULong) {
    TODO("Implement toNew")
  }

  /**
   * C++ original:
   * ```cpp
   * void* operator new(size_t, void* p) { return p; }
   * ```
   */
  public fun toNew(param0: ULong, p: Unit?) {
    TODO("Implement toNew")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SlugImpl> SlugImpl::Make(const SkMatrix& viewMatrix,
     *                                const sktext::GlyphRunList& glyphRunList,
     *                                const SkPaint& paint,
     *                                SkStrikeDeviceInfo strikeDeviceInfo,
     *                                sktext::StrikeForGPUCacheInterface* strikeCache) {
     *     size_t subRunSizeHint = gpu::SubRunContainer::EstimateAllocSize(glyphRunList);
     *     auto [initializer, _, alloc] =
     *             SubRunAllocator::AllocateClassMemoryAndArena<SlugImpl>(subRunSizeHint);
     *
     *     const SkMatrix positionMatrix = position_matrix(viewMatrix, glyphRunList.origin());
     *
     *     auto subRuns = gpu::SubRunContainer::MakeInAlloc(glyphRunList,
     *                                                      positionMatrix,
     *                                                      paint,
     *                                                      strikeDeviceInfo,
     *                                                      strikeCache,
     *                                                      &alloc,
     *                                                      gpu::SubRunContainer::kAddSubRuns,
     *                                                      "Make Slug");
     *
     *     sk_sp<SlugImpl> slug = sk_sp<SlugImpl>(initializer.initialize(std::move(alloc),
     *                                                                   std::move(subRuns),
     *                                                                   glyphRunList.sourceBounds(),
     *                                                                   glyphRunList.origin()));
     *
     *     // There is nothing to draw here. This is particularly a problem with RSX form blobs where a
     *     // single space becomes a run with no glyphs.
     *     if (slug->fSubRuns->isEmpty()) { return nullptr; }
     *
     *     return slug;
     * }
     * ```
     */
    public fun make(
      viewMatrix: SkMatrix,
      glyphRunList: GlyphRunList,
      paint: SkPaint,
      strikeDeviceInfo: SkStrikeDeviceInfo,
      strikeCache: StrikeForGPUCacheInterface?,
    ): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<Slug> SlugImpl::MakeFromBuffer(SkReadBuffer& buffer, const SkStrikeClient* client) {
     *     SkRect sourceBounds = buffer.readRect();
     *     if (!buffer.validate(!sourceBounds.isEmpty())) {
     *         return nullptr;
     *     }
     *     SkPoint origin = buffer.readPoint();
     *     int allocSizeHint = gpu::SubRunContainer::AllocSizeHintFromBuffer(buffer);
     *
     *     auto [initializer, _, alloc] =
     *             SubRunAllocator::AllocateClassMemoryAndArena<SlugImpl>(allocSizeHint);
     *
     *     gpu::SubRunContainerOwner container =
     *             gpu::SubRunContainer::MakeFromBufferInAlloc(buffer, client, &alloc);
     *
     *     // Something went wrong while reading.
     *     if (!buffer.isValid()) {
     *         return nullptr;
     *     }
     *
     *     return sk_sp<SlugImpl>(
     *             initializer.initialize(std::move(alloc), std::move(container), sourceBounds, origin));
     * }
     * ```
     */
    public fun makeFromBuffer(buffer: SkReadBuffer, client: SkStrikeClient?): Int {
      TODO("Implement makeFromBuffer")
    }
  }
}
