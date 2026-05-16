package org.skia.utils

import kotlin.Int
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit
import org.skia.core.SkCanvas
import org.skia.core.SkTextBlob
import org.skia.foundation.SkDeserialProcs
import org.skia.foundation.SkPaint
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * class SK_API Slug : public SkRefCnt {
 * public:
 *     // Return nullptr if the blob would not draw. This is not because of clipping, but because of
 *     // some paint optimization. The Slug is captured as if drawn using drawTextBlob.
 *     static sk_sp<Slug> ConvertBlob(
 *             SkCanvas* canvas, const SkTextBlob& blob, SkPoint origin, const SkPaint& paint);
 *
 *     // Serialize the slug.
 *     sk_sp<SkData> serialize() const;
 *     size_t serialize(void* buffer, size_t size) const;
 *
 *     // Set the client parameter to the appropriate SkStrikeClient when typeface ID translation
 *     // is needed.
 *     static sk_sp<Slug> Deserialize(const void* data,
 *                                    size_t size,
 *                                    const SkStrikeClient* client = nullptr);
 *     static sk_sp<Slug> MakeFromBuffer(SkReadBuffer& buffer);
 *
 *     // Allows clients to deserialize SkPictures that contain slug data
 *     static void AddDeserialProcs(SkDeserialProcs* procs, const SkStrikeClient* client = nullptr);
 *
 *     // Draw the Slug obeying the canvas's mapping and clipping.
 *     void draw(SkCanvas* canvas, const SkPaint& paint) const;
 *
 *     virtual SkRect sourceBounds() const = 0;
 *     virtual SkRect sourceBoundsWithOrigin () const = 0;
 *
 *     virtual void doFlatten(SkWriteBuffer&) const = 0;
 *
 *     uint32_t uniqueID() const { return fUniqueID; }
 *
 * private:
 *     static uint32_t NextUniqueID();
 *     const uint32_t  fUniqueID{NextUniqueID()};
 * }
 * ```
 */
public abstract class Slug : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * const uint32_t  fUniqueID{NextUniqueID()}
   * ```
   */
  private val fUniqueID: UInt = TODO("Initialize fUniqueID")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> Slug::serialize() const {
   *     SkBinaryWriteBuffer buffer({});
   *     this->doFlatten(buffer);
   *     return buffer.snapshotAsData();
   * }
   * ```
   */
  public fun serialize(): Int {
    TODO("Implement serialize")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t Slug::serialize(void* buffer, size_t size) const {
   *     SkBinaryWriteBuffer writeBuffer{buffer, size, {}};
   *     this->doFlatten(writeBuffer);
   *
   *     // If we overflow the given buffer, then SkWriteBuffer allocates a new larger buffer. Check
   *     // to see if an additional buffer was allocated, if it wasn't then everything fit, else
   *     // return 0 signaling the buffer overflowed.
   *     // N.B. This is the idiom from SkTextBlob.
   *     return writeBuffer.usingInitialStorage() ? writeBuffer.bytesWritten() : 0u;
   * }
   * ```
   */
  public fun serialize(buffer: Unit?, size: ULong): ULong {
    TODO("Implement serialize")
  }

  /**
   * C++ original:
   * ```cpp
   * void Slug::draw(SkCanvas* canvas, const SkPaint& paint) const {
   *     canvas->drawSlug(this, paint);
   * }
   * ```
   */
  public fun draw(canvas: SkCanvas?, paint: SkPaint) {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual SkRect sourceBounds() const = 0
   * ```
   */
  public abstract fun sourceBounds(): Int

  /**
   * C++ original:
   * ```cpp
   * virtual SkRect sourceBoundsWithOrigin () const = 0
   * ```
   */
  public abstract fun sourceBoundsWithOrigin(): Int

  /**
   * C++ original:
   * ```cpp
   * virtual void doFlatten(SkWriteBuffer&) const = 0
   * ```
   */
  public abstract fun doFlatten(param0: SkWriteBuffer)

  /**
   * C++ original:
   * ```cpp
   * uint32_t uniqueID() const { return fUniqueID; }
   * ```
   */
  public fun uniqueID(): UInt {
    TODO("Implement uniqueID")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<Slug> Slug::ConvertBlob(
     *         SkCanvas* canvas, const SkTextBlob& blob, SkPoint origin, const SkPaint& paint) {
     *     return canvas->convertBlobToSlug(blob, origin, paint);
     * }
     * ```
     */
    public fun convertBlob(
      canvas: SkCanvas?,
      blob: SkTextBlob,
      origin: SkPoint,
      paint: SkPaint,
    ): Int {
      TODO("Implement convertBlob")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<Slug> Slug::Deserialize(const void* data, size_t size, const SkStrikeClient* client) {
     *     SkReadBuffer buffer{data, size};
     *     SkDeserialProcs procs;
     *     Slug::AddDeserialProcs(&procs, client);
     *     buffer.setDeserialProcs(procs);
     *     return MakeFromBuffer(buffer);
     * }
     * ```
     */
    public fun deserialize(
      `data`: Unit?,
      size: ULong,
      client: SkStrikeClient? = TODO(),
    ): Int {
      TODO("Implement deserialize")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<Slug> Slug::MakeFromBuffer(SkReadBuffer& buffer) {
     *     auto procs = buffer.getDeserialProcs();
     *     if (procs.fSlugProc) {
     *         return procs.fSlugProc(buffer, procs.fSlugCtx);
     *     }
     *     SkDEBUGFAIL("Should have set serial procs");
     *     return nullptr;
     * }
     * ```
     */
    public fun makeFromBuffer(buffer: SkReadBuffer): Int {
      TODO("Implement makeFromBuffer")
    }

    /**
     * C++ original:
     * ```cpp
     * void Slug::AddDeserialProcs(SkDeserialProcs* procs, const SkStrikeClient* client) {
     *     SkASSERT(procs);
     *     procs->fSlugCtx = const_cast<SkStrikeClient*>(client);
     *     procs->fSlugProc = [](SkReadBuffer& buffer, void* ctx) -> sk_sp<Slug> {
     *         auto client = static_cast<const SkStrikeClient*>(ctx);
     *         return SlugImpl::MakeFromBuffer(buffer, client);
     *     };
     * }
     * ```
     */
    public fun addDeserialProcs(procs: SkDeserialProcs?, client: SkStrikeClient? = TODO()) {
      TODO("Implement addDeserialProcs")
    }

    /**
     * C++ original:
     * ```cpp
     * uint32_t Slug::NextUniqueID() {
     *     static std::atomic<uint32_t> nextUnique = 1;
     *     return nextUnique++;
     * }
     * ```
     */
    private fun nextUniqueID(): UInt {
      TODO("Implement nextUniqueID")
    }
  }
}
