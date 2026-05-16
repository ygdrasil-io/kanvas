package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit
import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.AtomicRef
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkSp
import undefined.SkDiscardableMemory

/**
 * C++ original:
 * ```cpp
 * class SK_API SkPixelRef : public SkRefCnt {
 * public:
 *     SkPixelRef(int width, int height, void* addr, size_t rowBytes);
 *     ~SkPixelRef() override;
 *
 *     SkISize dimensions() const { return {fWidth, fHeight}; }
 *     int width() const { return fWidth; }
 *     int height() const { return fHeight; }
 *     void* pixels() const { return fPixels; }
 *     size_t rowBytes() const { return fRowBytes; }
 *
 *     /** Returns a non-zero, unique value corresponding to the pixels in this
 *         pixelref. Each time the pixels are changed (and notifyPixelsChanged is
 *         called), a different generation ID will be returned.
 *     */
 *     uint32_t getGenerationID() const;
 *
 *     /**
 *      *  Call this if you have changed the contents of the pixels. This will in-
 *      *  turn cause a different generation ID value to be returned from
 *      *  getGenerationID().
 *      */
 *     void notifyPixelsChanged();
 *
 *     /** Returns true if this pixelref is marked as immutable, meaning that the
 *         contents of its pixels will not change for the lifetime of the pixelref.
 *     */
 *     bool isImmutable() const { return fMutability != kMutable; }
 *
 *     /** Marks this pixelref is immutable, meaning that the contents of its
 *         pixels will not change for the lifetime of the pixelref. This state can
 *         be set on a pixelref, but it cannot be cleared once it is set.
 *     */
 *     void setImmutable();
 *
 *     // Register a listener that may be called the next time our generation ID changes.
 *     //
 *     // We'll only call the listener if we're confident that we are the only SkPixelRef with this
 *     // generation ID.  If our generation ID changes and we decide not to call the listener, we'll
 *     // never call it: you must add a new listener for each generation ID change.  We also won't call
 *     // the listener when we're certain no one knows what our generation ID is.
 *     //
 *     // This can be used to invalidate caches keyed by SkPixelRef generation ID.
 *     // Takes ownership of listener.  Threadsafe.
 *     void addGenIDChangeListener(sk_sp<SkIDChangeListener> listener);
 *
 *     // Call when this pixelref is part of the key to a resourcecache entry. This allows the cache
 *     // to know automatically those entries can be purged when this pixelref is changed or deleted.
 *     void notifyAddedToCache() {
 *         fAddedToCache.store(true);
 *     }
 *
 *     virtual SkDiscardableMemory* diagnostic_only_getDiscardable() const { return nullptr; }
 *
 * protected:
 *     void android_only_reset(int width, int height, size_t rowBytes);
 *
 * private:
 *     int                 fWidth;
 *     int                 fHeight;
 *     void*               fPixels;
 *     size_t              fRowBytes;
 *
 *     // Bottom bit indicates the Gen ID is unique.
 *     bool genIDIsUnique() const { return SkToBool(fTaggedGenID.load() & 1); }
 *     mutable std::atomic<uint32_t> fTaggedGenID;
 *
 *     SkIDChangeListener::List fGenIDChangeListeners;
 *
 *     // Set true by caches when they cache content that's derived from the current pixels.
 *     std::atomic<bool> fAddedToCache;
 *
 *     enum Mutability {
 *         kMutable,               // PixelRefs begin mutable.
 *         kTemporarilyImmutable,  // Considered immutable, but can revert to mutable.
 *         kImmutable,             // Once set to this state, it never leaves.
 *     } fMutability : 8;          // easily fits inside a byte
 *
 *     void needsNewGenID();
 *     void callGenIDChangeListeners();
 *
 *     void setTemporarilyImmutable();
 *     void restoreMutability();
 *     friend class SkSurface_Raster;  // For temporary immutable methods above.
 *
 *     void setImmutableWithID(uint32_t genID);
 *     friend void SkBitmapCache_setImmutableWithID(SkPixelRef*, uint32_t);
 *
 *     using INHERITED = SkRefCnt;
 * }
 * ```
 */
public open class SkPixelRef public constructor(
  width: Int,
  height: Int,
  addr: Unit?,
  rowBytes: ULong,
) : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * int                 fWidth
   * ```
   */
  private var fWidth: Int = TODO("Initialize fWidth")

  /**
   * C++ original:
   * ```cpp
   * int                 fHeight
   * ```
   */
  private var fHeight: Int = TODO("Initialize fHeight")

  /**
   * C++ original:
   * ```cpp
   * void*               fPixels
   * ```
   */
  private var fPixels: Unit? = TODO("Initialize fPixels")

  /**
   * C++ original:
   * ```cpp
   * size_t              fRowBytes
   * ```
   */
  private var fRowBytes: ULong = TODO("Initialize fRowBytes")

  /**
   * C++ original:
   * ```cpp
   * mutable std::atomic<uint32_t> fTaggedGenID
   * ```
   */
  private val fTaggedGenID: AtomicRef<UInt> = TODO("Initialize fTaggedGenID")

  /**
   * C++ original:
   * ```cpp
   * SkIDChangeListener::List fGenIDChangeListeners
   * ```
   */
  private var fGenIDChangeListeners: Int = TODO("Initialize fGenIDChangeListeners")

  /**
   * C++ original:
   * ```cpp
   * std::atomic<bool> fAddedToCache
   * ```
   */
  private val fAddedToCache: AtomicBoolean = TODO("Initialize fAddedToCache")

  /**
   * C++ original:
   * ```cpp
   * enum Mutability {
   *         kMutable,               // PixelRefs begin mutable.
   *         kTemporarilyImmutable,  // Considered immutable, but can revert to mutable.
   *         kImmutable,             // Once set to this state, it never leaves.
   *     } fMutability : 8
   * ```
   */
  private var fMutability: Mutability = TODO("Initialize fMutability")

  /**
   * C++ original:
   * ```cpp
   * SkISize dimensions() const { return {fWidth, fHeight}; }
   * ```
   */
  public fun dimensions(): Int {
    TODO("Implement dimensions")
  }

  /**
   * C++ original:
   * ```cpp
   * int width() const { return fWidth; }
   * ```
   */
  public fun width(): Int {
    TODO("Implement width")
  }

  /**
   * C++ original:
   * ```cpp
   * int height() const { return fHeight; }
   * ```
   */
  public fun height(): Int {
    TODO("Implement height")
  }

  /**
   * C++ original:
   * ```cpp
   * void* pixels() const { return fPixels; }
   * ```
   */
  public fun pixels() {
    TODO("Implement pixels")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t rowBytes() const { return fRowBytes; }
   * ```
   */
  public fun rowBytes(): ULong {
    TODO("Implement rowBytes")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t SkPixelRef::getGenerationID() const {
   *     uint32_t id = fTaggedGenID.load();
   *     if (0 == id) {
   *         uint32_t next = SkNextID::ImageID() | 1u;
   *         if (fTaggedGenID.compare_exchange_strong(id, next)) {
   *             id = next;  // There was no race or we won the race.  fTaggedGenID is next now.
   *         } else {
   *             // We lost a race to set fTaggedGenID. compare_exchange() filled id with the winner.
   *         }
   *         // We can't quite SkASSERT(this->genIDIsUnique()). It could be non-unique
   *         // if we got here via the else path (pretty unlikely, but possible).
   *     }
   *     return id & ~1u;  // Mask off bottom unique bit.
   * }
   * ```
   */
  public fun getGenerationID(): UInt {
    TODO("Implement getGenerationID")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPixelRef::notifyPixelsChanged() {
   * #ifdef SK_DEBUG
   *     if (this->isImmutable()) {
   *         SkDebugf("========== notifyPixelsChanged called on immutable pixelref");
   *     }
   * #endif
   *     this->callGenIDChangeListeners();
   *     this->needsNewGenID();
   * }
   * ```
   */
  public fun notifyPixelsChanged() {
    TODO("Implement notifyPixelsChanged")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isImmutable() const { return fMutability != kMutable; }
   * ```
   */
  public fun isImmutable(): Boolean {
    TODO("Implement isImmutable")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPixelRef::setImmutable() {
   *     fMutability = kImmutable;
   * }
   * ```
   */
  public fun setImmutable() {
    TODO("Implement setImmutable")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPixelRef::addGenIDChangeListener(sk_sp<SkIDChangeListener> listener) {
   *     if (!listener || !this->genIDIsUnique()) {
   *         // No point in tracking this if we're not going to call it.
   *         return;
   *     }
   *     SkASSERT(!listener->shouldDeregister());
   *     fGenIDChangeListeners.add(std::move(listener));
   * }
   * ```
   */
  public fun addGenIDChangeListener(listener: SkSp<SkIDChangeListener>) {
    TODO("Implement addGenIDChangeListener")
  }

  /**
   * C++ original:
   * ```cpp
   * void notifyAddedToCache() {
   *         fAddedToCache.store(true);
   *     }
   * ```
   */
  public fun notifyAddedToCache() {
    TODO("Implement notifyAddedToCache")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual SkDiscardableMemory* diagnostic_only_getDiscardable() const { return nullptr; }
   * ```
   */
  public open fun diagnosticOnlyGetDiscardable(): SkDiscardableMemory {
    TODO("Implement diagnosticOnlyGetDiscardable")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPixelRef::android_only_reset(int width, int height, size_t rowBytes) {
   *     fWidth = width;
   *     fHeight = height;
   *     fRowBytes = rowBytes;
   *     // note: we do not change fPixels
   *
   *     // conservative, since its possible the "new" settings are the same as the old.
   *     this->notifyPixelsChanged();
   * }
   * ```
   */
  protected fun androidOnlyReset(
    width: Int,
    height: Int,
    rowBytes: ULong,
  ) {
    TODO("Implement androidOnlyReset")
  }

  /**
   * C++ original:
   * ```cpp
   * bool genIDIsUnique() const { return SkToBool(fTaggedGenID.load() & 1); }
   * ```
   */
  private fun genIDIsUnique(): Boolean {
    TODO("Implement genIDIsUnique")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPixelRef::needsNewGenID() {
   *     fTaggedGenID.store(0);
   *     SkASSERT(!this->genIDIsUnique()); // This method isn't threadsafe, so the assert should be fine.
   * }
   * ```
   */
  private fun needsNewGenID() {
    TODO("Implement needsNewGenID")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPixelRef::callGenIDChangeListeners() {
   *     // We don't invalidate ourselves if we think another SkPixelRef is sharing our genID.
   *     if (this->genIDIsUnique()) {
   *         fGenIDChangeListeners.changed();
   *         if (fAddedToCache.exchange(false)) {
   *             SkNotifyBitmapGenIDIsStale(this->getGenerationID());
   *         }
   *     } else {
   *         // Listeners get at most one shot, so even though these weren't triggered or not, blow them
   *         // away.
   *         fGenIDChangeListeners.reset();
   *     }
   * }
   * ```
   */
  private fun callGenIDChangeListeners() {
    TODO("Implement callGenIDChangeListeners")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPixelRef::setTemporarilyImmutable() {
   *     SkASSERT(fMutability != kImmutable);
   *     fMutability = kTemporarilyImmutable;
   * }
   * ```
   */
  private fun setTemporarilyImmutable() {
    TODO("Implement setTemporarilyImmutable")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPixelRef::restoreMutability() {
   *     SkASSERT(fMutability != kImmutable);
   *     fMutability = kMutable;
   * }
   * ```
   */
  private fun restoreMutability() {
    TODO("Implement restoreMutability")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPixelRef::setImmutableWithID(uint32_t genID) {
   *     /*
   *      *  We are forcing the genID to match an external value. The caller must ensure that this
   *      *  value does not conflict with other content.
   *      *
   *      *  One use is to force this pixelref's id to match an SkImage's id
   *      */
   *     fMutability = kImmutable;
   *     fTaggedGenID.store(genID);
   * }
   * ```
   */
  private fun setImmutableWithID(genID: UInt) {
    TODO("Implement setImmutableWithID")
  }

  public enum class Mutability {
    kMutable,
    kTemporarilyImmutable,
    kImmutable,
  }
}
