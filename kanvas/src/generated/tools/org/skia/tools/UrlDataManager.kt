package org.skia.tools

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.collections.List
import org.skia.foundation.SkData
import org.skia.foundation.SkImage
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class UrlDataManager {
 * public:
 *     explicit UrlDataManager(SkString rootUrl);
 *     ~UrlDataManager() { this->reset(); }
 *
 *     /*
 *      * Adds a data blob to the cache with a particular content type.  UrlDataManager will hash
 *      * the blob data to ensure uniqueness
 *      */
 *     SkString addData(SkData*, const char* contentType);
 *
 *     struct UrlData : public SkRefCnt {
 *         SkString fUrl;
 *         SkString fContentType;
 *         sk_sp<SkData> fData;
 *     };
 *
 *     /*
 *      * returns the UrlData object which should be hosted at 'url'
 *      */
 *     UrlData* getDataFromUrl(const SkString& url) {
 *         return fUrlLookup.find(url);
 *     }
 *     void reset();
 *
 *     // Methods used to identify images differently in wasm debugger for mskp animations.
 *     // serving is uncessary, as a collection of images with identifiers is already present, we
 *     // just want to use it when serializing commands.
 *
 *     /*
 *      * Construct an index from a list of images
 *      * (expected to be the list that was loaded from the mskp file)
 *      * Use only once.
 *      */
 *     void indexImages(const std::vector<sk_sp<SkImage>>&);
 *
 *     /*
 *      * Reports whether this UDM has an initialized image index (effevitely whether we're in wasm)
 *      */
 *     bool hasImageIndex() { return imageMap.size() > 0; }
 *
 *     /*
 *      * Return the file id (index of the image in the originally provided list) of an SkImage
 *      */
 *     int lookupImage(const SkImage*);
 *
 * private:
 *     struct LookupTrait {
 *         // We use the data as a hash, this is not really optimal but is fine until proven otherwise
 *         static const SkData& GetKey(const UrlData& data) {
 *             return *data.fData;
 *         }
 *
 *         static uint32_t Hash(const SkData& key) {
 *             return SkChecksum::Hash32(key.bytes(), key.size());
 *         }
 *     };
 *
 *     struct ReverseLookupTrait {
 *         static const SkString& GetKey(const UrlData& data) {
 *             return data.fUrl;
 *         }
 *
 *         static uint32_t Hash(const SkString& key) {
 *             return SkChecksum::Hash32(key.c_str(), strlen(key.c_str()));
 *         }
 *     };
 *
 *
 *     SkString fRootUrl;
 *     SkTDynamicHash<UrlData, SkData, LookupTrait> fCache;
 *     SkTDynamicHash<UrlData, SkString, ReverseLookupTrait> fUrlLookup;
 *     uint32_t fDataId;
 *     std::unordered_map<const SkImage*, int> imageMap;
 * }
 * ```
 */
public data class UrlDataManager public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkString fRootUrl
   * ```
   */
  private var fRootUrl: Int,
  /**
   * C++ original:
   * ```cpp
   * SkTDynamicHash<UrlData, SkData, LookupTrait> fCache
   * ```
   */
  private var fCache: Int,
  /**
   * C++ original:
   * ```cpp
   * SkTDynamicHash<UrlData, SkString, ReverseLookupTrait> fUrlLookup
   * ```
   */
  private var fUrlLookup: Int,
  /**
   * C++ original:
   * ```cpp
   * uint32_t fDataId
   * ```
   */
  private var fDataId: Int,
  /**
   * C++ original:
   * ```cpp
   * std::unordered_map<const SkImage*, int> imageMap
   * ```
   */
  private var imageMap: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * SkString UrlDataManager::addData(SkData* data, const char* contentType) {
   *     UrlData* urlData = fCache.find(*data);
   *     if (fCache.find(*data)) {
   *         SkASSERT(data->equals(urlData->fData.get()));
   *         return urlData->fUrl;
   *     }
   *
   *     urlData = new UrlData;
   *     urlData->fData.reset(SkRef(data));
   *     urlData->fContentType.set(contentType);
   *     urlData->fUrl.appendf("%s/%u", fRootUrl.c_str(), fDataId++);
   *
   *     fCache.add(urlData);
   *
   *     SkASSERT(!fUrlLookup.find(urlData->fUrl));
   *     fUrlLookup.add(urlData);
   *     return urlData->fUrl;
   * }
   * ```
   */
  public fun addData(`data`: SkData?, contentType: String?): Int {
    TODO("Implement addData")
  }

  /**
   * C++ original:
   * ```cpp
   * UrlData* getDataFromUrl(const SkString& url) {
   *         return fUrlLookup.find(url);
   *     }
   * ```
   */
  public fun getDataFromUrl(url: String): UrlData {
    TODO("Implement getDataFromUrl")
  }

  /**
   * C++ original:
   * ```cpp
   * void UrlDataManager::reset() {
   *     fCache.foreach([&](UrlData* urlData) {
   *         urlData->unref();
   *     });
   *     fCache.rewind();
   * }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void UrlDataManager::indexImages(const std::vector<sk_sp<SkImage>>& images) {
   *     SkASSERT(imageMap.empty());  // this method meant only for initialization once.
   *     for (size_t i = 0; i < images.size(); ++i) {
   *         imageMap.insert({images[i].get(), i});
   *     }
   * }
   * ```
   */
  public fun indexImages(images: List<SkSp<SkImage>>) {
    TODO("Implement indexImages")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasImageIndex() { return imageMap.size() > 0; }
   * ```
   */
  public fun hasImageIndex(): Boolean {
    TODO("Implement hasImageIndex")
  }

  /**
   * C++ original:
   * ```cpp
   * int UrlDataManager::lookupImage(const SkImage* im) {
   *     auto search = imageMap.find(im);
   *     if (search != imageMap.end()) {
   *         return search->second;
   *     } else {
   *         // -1 signals the pointer to this image wasn't in the original list.
   *         // Maybe it was synthesized after file load? If so, you shouldn't be looking it up here.
   *         return -1;
   *     }
   * }
   * ```
   */
  public fun lookupImage(im: SkImage?): Int {
    TODO("Implement lookupImage")
  }

  public open class UrlData public constructor(
    public var fUrl: Int,
    public var fContentType: Int,
    public var fData: Int,
  ) : SkRefCnt(TODO())

  public open class LookupTrait {
    public companion object {
      public fun getKey(`data`: UrlData): Int {
        TODO("Implement getKey")
      }

      public fun hash(key: SkData): Int {
        TODO("Implement hash")
      }
    }
  }

  public open class ReverseLookupTrait {
    public companion object {
      public fun getKey(`data`: UrlData): Int {
        TODO("Implement getKey")
      }

      public fun hash(key: String): Int {
        TODO("Implement hash")
      }
    }
  }
}
