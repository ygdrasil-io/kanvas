package org.skia.core

import kotlin.Boolean
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPixmap
import Rec as Rec_
import undefined.Rec as UndefinedRec

/**
 * C++ original:
 * ```cpp
 * class SkBitmapCache {
 * public:
 *     /**
 *      *  Search based on the desc. If found, returns true and
 *      *  result will be set to the matching bitmap with its pixels already locked.
 *      */
 *     static bool Find(const SkBitmapCacheDesc&, SkBitmap* result);
 *
 *     class Rec;
 *     struct RecDeleter { void operator()(Rec* r) { PrivateDeleteRec(r); } };
 *     typedef std::unique_ptr<Rec, RecDeleter> RecPtr;
 *
 *     static RecPtr Alloc(const SkBitmapCacheDesc&, const SkImageInfo&, SkPixmap*);
 *     static void Add(RecPtr, SkBitmap*);
 *
 * private:
 *     static void PrivateDeleteRec(Rec*);
 * }
 * ```
 */
public open class SkBitmapCache {
  public open class RecDeleter {
    public operator fun invoke(r: UndefinedRec?) {
      TODO("Implement invoke")
    }
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * bool SkBitmapCache::Find(const SkBitmapCacheDesc& desc, SkBitmap* result) {
     *     desc.validate();
     *     return SkResourceCache::Find(BitmapKey(desc), SkBitmapCache::Rec::Finder, result);
     * }
     * ```
     */
    public fun find(desc: SkBitmapCacheDesc, result: SkBitmap?): Boolean {
      TODO("Implement find")
    }

    /**
     * C++ original:
     * ```cpp
     * SkBitmapCache::RecPtr SkBitmapCache::Alloc(const SkBitmapCacheDesc& desc, const SkImageInfo& info,
     *                                            SkPixmap* pmap) {
     *     // Ensure that the info matches the subset (i.e. the subset is the entire image)
     *     SkASSERT(info.width() == desc.fSubset.width());
     *     SkASSERT(info.height() == desc.fSubset.height());
     *
     *     const size_t rb = info.minRowBytes();
     *     size_t size = info.computeByteSize(rb);
     *     if (SkImageInfo::ByteSizeOverflowed(size)) {
     *         return nullptr;
     *     }
     *
     *     std::unique_ptr<SkDiscardableMemory> dm;
     *     void* block = nullptr;
     *
     *     auto factory = SkResourceCache::GetDiscardableFactory();
     *     if (factory) {
     *         dm.reset(factory(size));
     *     } else {
     *         block = sk_malloc_canfail(size);
     *     }
     *     if (!dm && !block) {
     *         return nullptr;
     *     }
     *     *pmap = SkPixmap(info, dm ? dm->data() : block, rb);
     *     return RecPtr(new Rec(desc, info, rb, std::move(dm), block));
     * }
     * ```
     */
    public fun alloc(
      desc: SkBitmapCacheDesc,
      info: SkImageInfo,
      pmap: SkPixmap?,
    ): SkBitmapCacheRecPtr {
      TODO("Implement alloc")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkBitmapCache::Add(RecPtr rec, SkBitmap* bitmap) {
     *     SkResourceCache::Add(rec.release(), bitmap);
     * }
     * ```
     */
    public fun add(rec: SkBitmapCacheRecPtr, bitmap: SkBitmap?) {
      TODO("Implement add")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkBitmapCache::PrivateDeleteRec(Rec* rec) { delete rec; }
     * ```
     */
    private fun privateDeleteRec(rec: Rec_?) {
      TODO("Implement privateDeleteRec")
    }
  }
}
