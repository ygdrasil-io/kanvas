package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.utils.SkStrikeClient
import undefined.AtlasDrawDelegate

/**
 * C++ original:
 * ```cpp
 * class SubRun {
 * public:
 *     virtual ~SubRun();
 *
 *     virtual void draw(SkCanvas*, SkPoint drawOrigin, const SkPaint&, sk_sp<SkRefCnt> subRunStorage,
 *                       const AtlasDrawDelegate&) const = 0;
 *
 *     void flatten(SkWriteBuffer& buffer) const;
 *     static SubRunOwner MakeFromBuffer(SkReadBuffer& buffer,
 *                                       sktext::gpu::SubRunAllocator* alloc,
 *                                       const SkStrikeClient* client);
 *
 *     // Size hint for unflattening this run. If this is accurate, it will help with the allocation
 *     // of the slug. If it's off then there may be more allocations needed to unflatten.
 *     virtual int unflattenSize() const = 0;
 *
 *     // Given an already cached subRun, can this subRun handle this combination paint, matrix, and
 *     // position.
 *     virtual bool canReuse(const SkPaint& paint, const SkMatrix& positionMatrix) const = 0;
 *
 *     // Return the underlying atlas SubRun if it exists. Otherwise, return nullptr.
 *     // * Don't use this API. It is only to support testing.
 *     virtual const AtlasSubRun* testingOnly_atlasSubRun() const = 0;
 *
 * protected:
 *     enum SubRunStreamTag : int;
 *     virtual SubRunStreamTag subRunStreamTag() const = 0;
 *     virtual void doFlatten(SkWriteBuffer& buffer) const = 0;
 *
 * private:
 *     friend class SubRunList;
 *     SubRunOwner fNext;
 * }
 * ```
 */
public abstract class SubRun {
  /**
   * C++ original:
   * ```cpp
   * SubRunOwner fNext
   * ```
   */
  private var fNext: Int = TODO("Initialize fNext")

  /**
   * C++ original:
   * ```cpp
   * virtual void draw(SkCanvas*, SkPoint drawOrigin, const SkPaint&, sk_sp<SkRefCnt> subRunStorage,
   *                       const AtlasDrawDelegate&) const = 0
   * ```
   */
  public abstract fun draw(
    param0: SkCanvas?,
    drawOrigin: SkPoint,
    param2: SkPaint,
    subRunStorage: SkSp<SkRefCnt>,
    param4: AtlasDrawDelegate,
  )

  /**
   * C++ original:
   * ```cpp
   * void SubRun::flatten(SkWriteBuffer& buffer) const {
   *     buffer.writeInt(this->subRunStreamTag());
   *     this->doFlatten(buffer);
   * }
   * ```
   */
  public fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual int unflattenSize() const = 0
   * ```
   */
  public abstract fun unflattenSize(): Int

  /**
   * C++ original:
   * ```cpp
   * virtual bool canReuse(const SkPaint& paint, const SkMatrix& positionMatrix) const = 0
   * ```
   */
  public abstract fun canReuse(paint: SkPaint, positionMatrix: SkMatrix): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual const AtlasSubRun* testingOnly_atlasSubRun() const = 0
   * ```
   */
  public abstract fun testingOnlyAtlasSubRun(): AtlasSubRun

  /**
   * C++ original:
   * ```cpp
   * virtual SubRunStreamTag subRunStreamTag() const = 0
   * ```
   */
  protected abstract fun subRunStreamTag(): SubRunStreamTag

  /**
   * C++ original:
   * ```cpp
   * virtual void doFlatten(SkWriteBuffer& buffer) const = 0
   * ```
   */
  protected abstract fun doFlatten(buffer: SkWriteBuffer)

  public enum class SubRunStreamTag

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * SubRunOwner SubRun::MakeFromBuffer(SkReadBuffer& buffer,
     *                                    SubRunAllocator* alloc,
     *                                    const SkStrikeClient* client) {
     *     using Maker = SubRunOwner (*)(SkReadBuffer&,
     *                                   SubRunAllocator*,
     *                                   const SkStrikeClient*);
     *
     *     static Maker makers[kSubRunStreamTagCount] = {
     *             nullptr,                                             // 0 index is bad.
     *             DirectMaskSubRun::MakeFromBuffer,
     * #if !defined(SK_DISABLE_SDF_TEXT)
     *             SDFTSubRun::MakeFromBuffer,
     * #endif
     *             TransformedMaskSubRun::MakeFromBuffer,
     *             PathSubRun::MakeFromBuffer,
     *             DrawableSubRun::MakeFromBuffer,
     *     };
     *     int subRunTypeInt = buffer.readInt();
     *     if (!buffer.validate(kBad < subRunTypeInt && subRunTypeInt < kSubRunStreamTagCount)) {
     *         return nullptr;
     *     }
     *     auto maker = makers[subRunTypeInt];
     *     if (!buffer.validate(maker != nullptr)) { return nullptr; }
     *     return maker(buffer, alloc, client);
     * }
     * ```
     */
    public fun makeFromBuffer(
      buffer: SkReadBuffer,
      alloc: SubRunAllocator?,
      client: SkStrikeClient?,
    ): Int {
      TODO("Implement makeFromBuffer")
    }
  }
}
