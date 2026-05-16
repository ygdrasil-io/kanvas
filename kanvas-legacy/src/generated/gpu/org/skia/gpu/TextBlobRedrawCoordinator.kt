package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import org.skia.core.GlyphRunList
import org.skia.core.SkCanvas
import org.skia.core.SkStrikeDeviceInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import undefined.AtlasDrawDelegate

/**
 * C++ original:
 * ```cpp
 * class TextBlobRedrawCoordinator {
 * public:
 *     TextBlobRedrawCoordinator(uint32_t messageBusID);
 *
 *     void drawGlyphRunList(SkCanvas* canvas,
 *                           const SkMatrix& viewMatrix,
 *                           const GlyphRunList& glyphRunList,
 *                           const SkPaint& paint,
 *                           SkStrikeDeviceInfo strikeDeviceInfo,
 *                           const AtlasDrawDelegate&);
 *
 *     void freeAll() SK_EXCLUDES(fSpinLock);
 *
 *     struct PurgeBlobMessage {
 *         PurgeBlobMessage(uint32_t blobID, uint32_t contextUniqueID)
 *                 : fBlobID(blobID), fContextID(contextUniqueID) {}
 *
 *         uint32_t fBlobID;
 *         uint32_t fContextID;
 *     };
 *
 *     void purgeStaleBlobs() SK_EXCLUDES(fSpinLock);
 *
 *     size_t usedBytes() const SK_EXCLUDES(fSpinLock);
 *
 *     bool isOverBudget() const SK_EXCLUDES(fSpinLock);
 *
 * private:
 *     friend class ::GrTextBlobTestingPeer;
 *     using TextBlobList = SkTInternalLList<TextBlob>;
 *
 *     struct BlobIDCacheEntry {
 *         BlobIDCacheEntry();
 *         explicit BlobIDCacheEntry(uint32_t id);
 *
 *         static uint32_t GetKey(const BlobIDCacheEntry& entry);
 *
 *         void addBlob(sk_sp<TextBlob> blob);
 *
 *         void removeBlob(TextBlob* blob);
 *
 *         sk_sp<TextBlob> find(const TextBlob::Key& key) const;
 *
 *         int findBlobIndex(const TextBlob::Key& key) const;
 *
 *         uint32_t fID;
 *         // Current clients don't generate multiple GrAtlasTextBlobs per SkTextBlob, so an array w/
 *         // linear search is acceptable.  If usage changes, we should re-evaluate this structure.
 *         skia_private::STArray<1, sk_sp<TextBlob>> fBlobs;
 *     };
 *
 *     sk_sp<TextBlob> findOrCreateBlob(const SkMatrix& positionMatrix,
 *                                      const GlyphRunList& glyphRunList,
 *                                      const SkPaint& paint,
 *                                      SkStrikeDeviceInfo strikeDeviceInfo);
 *
 *     // If not already in the cache, then add it else, return the text blob from the cache.
 *     sk_sp<TextBlob> addOrReturnExisting(
 *             const GlyphRunList& glyphRunList,
 *             sk_sp<TextBlob> blob) SK_EXCLUDES(fSpinLock);
 *
 *     sk_sp<TextBlob> find(const TextBlob::Key& key) SK_EXCLUDES(fSpinLock);
 *
 *     void remove(TextBlob* blob) SK_EXCLUDES(fSpinLock);
 *
 *     void internalPurgeStaleBlobs() SK_REQUIRES(fSpinLock);
 *
 *     sk_sp<TextBlob>
 *             internalAdd(sk_sp<TextBlob> blob) SK_REQUIRES(fSpinLock);
 *     void internalRemove(TextBlob* blob) SK_REQUIRES(fSpinLock);
 *
 *     void internalCheckPurge(TextBlob* blob = nullptr) SK_REQUIRES(fSpinLock);
 *
 *     static const int kDefaultBudget = 1 << 22;
 *
 *     mutable SkSpinlock fSpinLock;
 *     TextBlobList fBlobList SK_GUARDED_BY(fSpinLock);
 *     skia_private::THashMap<uint32_t, BlobIDCacheEntry> fBlobIDCache SK_GUARDED_BY(fSpinLock);
 *     size_t fSizeBudget SK_GUARDED_BY(fSpinLock);
 *     size_t fCurrentSize SK_GUARDED_BY(fSpinLock) {0};
 *
 *     // In practice 'messageBusID' is always the unique ID of the owning GrContext
 *     const uint32_t fMessageBusID;
 *     SkMessageBus<PurgeBlobMessage, uint32_t>::Inbox fPurgeBlobInbox SK_GUARDED_BY(fSpinLock);
 * }
 * ```
 */
public data class TextBlobRedrawCoordinator public constructor(
  /**
   * C++ original:
   * ```cpp
   * static const int kDefaultBudget = 1 << 22
   * ```
   */
  private var fSpinLock: Int,
  /**
   * C++ original:
   * ```cpp
   * mutable SkSpinlock fSpinLock
   * ```
   */
  private var fBlobList: Int,
  /**
   * C++ original:
   * ```cpp
   * TextBlobList fBlobList
   * ```
   */
  private var fBlobIDCache: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::THashMap<uint32_t, BlobIDCacheEntry> fBlobIDCache
   * ```
   */
  private var fSizeBudget: Int,
  /**
   * C++ original:
   * ```cpp
   * size_t fSizeBudget
   * ```
   */
  private var fCurrentSize: Int,
  /**
   * C++ original:
   * ```cpp
   * size_t fCurrentSize
   * ```
   */
  private val fMessageBusID: Int,
  /**
   * C++ original:
   * ```cpp
   * const uint32_t fMessageBusID
   * ```
   */
  private var fPurgeBlobInbox: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * void TextBlobRedrawCoordinator::drawGlyphRunList(SkCanvas* canvas,
   *                                                  const SkMatrix& viewMatrix,
   *                                                  const sktext::GlyphRunList& glyphRunList,
   *                                                  const SkPaint& paint,
   *                                                  SkStrikeDeviceInfo strikeDeviceInfo,
   *                                                  const AtlasDrawDelegate& atlasDelegate) {
   *     sk_sp<TextBlob> blob = this->findOrCreateBlob(viewMatrix, glyphRunList, paint,
   *                                                   strikeDeviceInfo);
   *
   *     blob->draw(canvas, glyphRunList.origin(), paint, atlasDelegate);
   * }
   * ```
   */
  public fun drawGlyphRunList(
    canvas: SkCanvas?,
    viewMatrix: SkMatrix,
    glyphRunList: GlyphRunList,
    paint: SkPaint,
    strikeDeviceInfo: SkStrikeDeviceInfo,
    atlasDelegate: AtlasDrawDelegate,
  ) {
    TODO("Implement drawGlyphRunList")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextBlobRedrawCoordinator::freeAll() {
   *     SkAutoSpinlock lock{fSpinLock};
   *     fBlobIDCache.reset();
   *     fBlobList.reset();
   *     fCurrentSize = 0;
   * }
   * ```
   */
  public fun freeAll() {
    TODO("Implement freeAll")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextBlobRedrawCoordinator::purgeStaleBlobs() {
   *     SkAutoSpinlock lock{fSpinLock};
   *     this->internalPurgeStaleBlobs();
   * }
   * ```
   */
  public fun purgeStaleBlobs() {
    TODO("Implement purgeStaleBlobs")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t TextBlobRedrawCoordinator::usedBytes() const {
   *     SkAutoSpinlock lock{fSpinLock};
   *     return fCurrentSize;
   * }
   * ```
   */
  public fun usedBytes(): Int {
    TODO("Implement usedBytes")
  }

  /**
   * C++ original:
   * ```cpp
   * bool TextBlobRedrawCoordinator::isOverBudget() const {
   *     SkAutoSpinlock lock{fSpinLock};
   *     return fCurrentSize > fSizeBudget;
   * }
   * ```
   */
  public fun isOverBudget(): Boolean {
    TODO("Implement isOverBudget")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<TextBlob> TextBlobRedrawCoordinator::findOrCreateBlob(const SkMatrix& viewMatrix,
   *                                                             const GlyphRunList& glyphRunList,
   *                                                             const SkPaint& paint,
   *                                                             SkStrikeDeviceInfo strikeDeviceInfo) {
   *     SkMatrix positionMatrix{viewMatrix};
   *     positionMatrix.preTranslate(glyphRunList.origin().x(), glyphRunList.origin().y());
   *
   *     auto [canCache, key] = TextBlob::Key::Make(
   *             glyphRunList, paint, positionMatrix, strikeDeviceInfo);
   *     sk_sp<TextBlob> blob;
   *     if (canCache) {
   *         blob = this->find(key);
   *     }
   *
   *     if (blob == nullptr || !blob->canReuse(paint, positionMatrix)) {
   *         if (blob != nullptr) {
   *             // We have to remake the blob because changes may invalidate our masks.
   *             this->remove(blob.get());
   *         }
   *
   *         blob = TextBlob::Make(
   *                 glyphRunList, paint, positionMatrix,
   *                 strikeDeviceInfo, SkStrikeCache::GlobalStrikeCache());
   *
   *         if (canCache) {
   *             blob->addKey(key);
   *             // The blob may already have been created on a different thread. Use the first one
   *             // that was there.
   *             blob = this->addOrReturnExisting(glyphRunList, blob);
   *         }
   *     }
   *
   *     return blob;
   * }
   * ```
   */
  private fun findOrCreateBlob(
    positionMatrix: SkMatrix,
    glyphRunList: GlyphRunList,
    paint: SkPaint,
    strikeDeviceInfo: SkStrikeDeviceInfo,
  ): Int {
    TODO("Implement findOrCreateBlob")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<TextBlob> TextBlobRedrawCoordinator::addOrReturnExisting(
   *         const GlyphRunList& glyphRunList, sk_sp<TextBlob> blob) {
   *     SkAutoSpinlock lock{fSpinLock};
   *     blob = this->internalAdd(std::move(blob));
   *     glyphRunList.temporaryShuntBlobNotifyAddedToCache(fMessageBusID, post_purge_blob_message);
   *     return blob;
   * }
   * ```
   */
  private fun addOrReturnExisting(glyphRunList: GlyphRunList, blob: SkSp<TextBlob>): Int {
    TODO("Implement addOrReturnExisting")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<TextBlob> TextBlobRedrawCoordinator::find(const TextBlob::Key& key) {
   *     SkAutoSpinlock lock{fSpinLock};
   *     const BlobIDCacheEntry* idEntry = fBlobIDCache.find(key.fUniqueID);
   *     if (idEntry == nullptr) {
   *         return nullptr;
   *     }
   *
   *     sk_sp<TextBlob> blob = idEntry->find(key);
   *     TextBlob* blobPtr = blob.get();
   *     if (blobPtr != nullptr && blobPtr != fBlobList.head()) {
   *         fBlobList.remove(blobPtr);
   *         fBlobList.addToHead(blobPtr);
   *     }
   *     return blob;
   * }
   * ```
   */
  private fun find(key: TextBlob.Key): Int {
    TODO("Implement find")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextBlobRedrawCoordinator::remove(TextBlob* blob) {
   *     SkAutoSpinlock lock{fSpinLock};
   *     this->internalRemove(blob);
   * }
   * ```
   */
  private fun remove(blob: TextBlob?) {
    TODO("Implement remove")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextBlobRedrawCoordinator::internalPurgeStaleBlobs() {
   *     TArray<PurgeBlobMessage> msgs;
   *     fPurgeBlobInbox.poll(&msgs);
   *
   *     for (const auto& msg : msgs) {
   *         auto* idEntry = fBlobIDCache.find(msg.fBlobID);
   *         if (!idEntry) {
   *             // no cache entries for id
   *             continue;
   *         }
   *
   *         // remove all blob entries from the LRU list
   *         for (const auto& blob : idEntry->fBlobs) {
   *             fCurrentSize -= blob->size();
   *             fBlobList.remove(blob.get());
   *         }
   *
   *         // drop the idEntry itself (unrefs all blobs)
   *         fBlobIDCache.remove(msg.fBlobID);
   *     }
   * }
   * ```
   */
  private fun internalPurgeStaleBlobs() {
    TODO("Implement internalPurgeStaleBlobs")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<TextBlob> TextBlobRedrawCoordinator::internalAdd(sk_sp<TextBlob> blob) {
   *     auto  id      = blob->key().fUniqueID;
   *     auto* idEntry = fBlobIDCache.find(id);
   *     if (!idEntry) {
   *         idEntry = fBlobIDCache.set(id, BlobIDCacheEntry(id));
   *     }
   *
   *     if (sk_sp<TextBlob> alreadyIn = idEntry->find(blob->key()); alreadyIn) {
   *         blob = std::move(alreadyIn);
   *     } else {
   *         fBlobList.addToHead(blob.get());
   *         fCurrentSize += blob->size();
   *         idEntry->addBlob(blob);
   *     }
   *
   *     this->internalCheckPurge(blob.get());
   *     return blob;
   * }
   * ```
   */
  private fun internalAdd(blob: SkSp<TextBlob>): Int {
    TODO("Implement internalAdd")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextBlobRedrawCoordinator::internalRemove(TextBlob* blob) {
   *     auto  id      = blob->key().fUniqueID;
   *     auto* idEntry = fBlobIDCache.find(id);
   *
   *     if (idEntry != nullptr) {
   *         sk_sp<TextBlob> stillExists = idEntry->find(blob->key());
   *         if (blob == stillExists.get())  {
   *             fCurrentSize -= blob->size();
   *             fBlobList.remove(blob);
   *             idEntry->removeBlob(blob);
   *             if (idEntry->fBlobs.empty()) {
   *                 fBlobIDCache.remove(id);
   *             }
   *         }
   *     }
   * }
   * ```
   */
  private fun internalRemove(blob: TextBlob?) {
    TODO("Implement internalRemove")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextBlobRedrawCoordinator::internalCheckPurge(TextBlob* blob) {
   *     // First, purge all stale blob IDs.
   *     this->internalPurgeStaleBlobs();
   *
   *     // If we are still over budget, then unref until we are below budget again
   *     if (fCurrentSize > fSizeBudget) {
   *         TextBlobList::Iter iter;
   *         iter.init(fBlobList, TextBlobList::Iter::kTail_IterStart);
   *         TextBlob* lruBlob = nullptr;
   *         while (fCurrentSize > fSizeBudget && (lruBlob = iter.get()) && lruBlob != blob) {
   *             // Backup the iterator before removing and unrefing the blob
   *             iter.prev();
   *
   *             this->internalRemove(lruBlob);
   *         }
   *
   *     #ifdef SPEW_BUDGET_MESSAGE
   *         if (fCurrentSize > fSizeBudget) {
   *             SkDebugf("Single textblob is larger than our whole budget");
   *         }
   *     #endif
   *     }
   * }
   * ```
   */
  private fun internalCheckPurge(blob: TextBlob? = TODO()) {
    TODO("Implement internalCheckPurge")
  }

  public data class PurgeBlobMessage public constructor(
    public var fBlobID: Int,
    public var fContextID: Int,
  )

  public data class BlobIDCacheEntry public constructor(
    public var fID: Int,
    public var fBlobs: Int,
  ) {
    public fun addBlob(blob: SkSp<TextBlob>) {
      TODO("Implement addBlob")
    }

    public fun removeBlob(blob: TextBlob?) {
      TODO("Implement removeBlob")
    }

    public fun find(key: TextBlob.Key): Int {
      TODO("Implement find")
    }

    public fun findBlobIndex(key: TextBlob.Key): Int {
      TODO("Implement findBlobIndex")
    }

    public companion object {
      public fun getKey(entry: BlobIDCacheEntry): Int {
        TODO("Implement getKey")
      }
    }
  }

  public companion object {
    private val kDefaultBudget: Int = TODO("Initialize kDefaultBudget")
  }
}
