package org.skia.tools

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.collections.Map
import org.skia.foundation.SkData
import org.skia.gpu.ganesh.GrBackendApi
import org.skia.gpu.ganesh.GrContextOptions
import undefined.Fn

/**
 * C++ original:
 * ```cpp
 * class MemoryCache : public GrContextOptions::PersistentCache {
 * public:
 *     MemoryCache() = default;
 *     MemoryCache(const MemoryCache&) = delete;
 *     MemoryCache& operator=(const MemoryCache&) = delete;
 *     void reset() {
 *         this->resetCacheStats();
 *         fMap.clear();
 *     }
 *
 *     sk_sp<SkData> load(const SkData& key) override;
 *     void store(const SkData& key, const SkData& data, const SkString& description) override;
 *     int numCacheMisses() const { return fCacheMissCnt; }
 *     int numCacheStores() const { return fCacheStoreCnt; }
 *     void resetCacheStats() {
 *         fCacheMissCnt = 0;
 *         fCacheStoreCnt = 0;
 *     }
 *
 *     void writeShadersToDisk(const char* path, GrBackendApi backend);
 *
 *     template <typename Fn>
 *     void foreach(Fn&& fn) {
 *         for (auto it = fMap.begin(); it != fMap.end(); ++it) {
 *             fn(it->first.fKey, it->second.fData, it->second.fDescription, it->second.fHitCount);
 *         }
 *     }
 *
 * private:
 *     struct Key {
 *         Key() = default;
 *         Key(const SkData& key) : fKey(SkData::MakeWithCopy(key.data(), key.size())) {}
 *         Key(const Key& that) = default;
 *         Key& operator=(const Key&) = default;
 *         bool operator==(const Key& that) const {
 *             return that.fKey->size() == fKey->size() &&
 *                    !memcmp(fKey->data(), that.fKey->data(), that.fKey->size());
 *         }
 *         sk_sp<const SkData> fKey;
 *     };
 *
 *     struct Value {
 *         Value() = default;
 *         Value(const SkData& data, const SkString& description)
 *             : fData(SkData::MakeWithCopy(data.data(), data.size()))
 *             , fDescription(description)
 *             , fHitCount(1) {}
 *         Value(const Value& that) = default;
 *         Value& operator=(const Value&) = default;
 *
 *         sk_sp<SkData> fData;
 *         SkString      fDescription;
 *         int           fHitCount;
 *     };
 *
 *     struct Hash {
 *         using argument_type = Key;
 *         using result_type = uint32_t;
 *         uint32_t operator()(const Key& key) const {
 *             return key.fKey ? SkChecksum::Hash32(key.fKey->data(), key.fKey->size()) : 0;
 *         }
 *     };
 *
 *     int fCacheMissCnt = 0;
 *     int fCacheStoreCnt = 0;
 *     std::unordered_map<Key, Value, Hash> fMap;
 * }
 * ```
 */
public open class MemoryCache public constructor() : GrContextOptions.PersistentCache() {
  /**
   * C++ original:
   * ```cpp
   * int fCacheMissCnt = 0
   * ```
   */
  private var fCacheMissCnt: Int = TODO("Initialize fCacheMissCnt")

  /**
   * C++ original:
   * ```cpp
   * int fCacheStoreCnt = 0
   * ```
   */
  private var fCacheStoreCnt: Int = TODO("Initialize fCacheStoreCnt")

  /**
   * C++ original:
   * ```cpp
   * std::unordered_map<Key, Value, Hash> fMap
   * ```
   */
  private var fMap: Map<org.skia.pdf.Key, org.skia.modules.Value> = TODO("Initialize fMap")

  /**
   * C++ original:
   * ```cpp
   * MemoryCache() = default
   * ```
   */
  public constructor(param0: MemoryCache) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * MemoryCache& operator=(const MemoryCache&) = delete
   * ```
   */
  public fun assign(param0: MemoryCache) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * void reset() {
   *         this->resetCacheStats();
   *         fMap.clear();
   *     }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> load(const SkData& key) override
   * ```
   */
  public override fun load(key: SkData): Int {
    TODO("Implement load")
  }

  /**
   * C++ original:
   * ```cpp
   * void store(const SkData& key, const SkData& data, const SkString& description) override
   * ```
   */
  public override fun store(
    key: SkData,
    `data`: SkData,
    description: String,
  ) {
    TODO("Implement store")
  }

  /**
   * C++ original:
   * ```cpp
   * int numCacheMisses() const { return fCacheMissCnt; }
   * ```
   */
  public fun numCacheMisses(): Int {
    TODO("Implement numCacheMisses")
  }

  /**
   * C++ original:
   * ```cpp
   * int numCacheStores() const { return fCacheStoreCnt; }
   * ```
   */
  public fun numCacheStores(): Int {
    TODO("Implement numCacheStores")
  }

  /**
   * C++ original:
   * ```cpp
   * void resetCacheStats() {
   *         fCacheMissCnt = 0;
   *         fCacheStoreCnt = 0;
   *     }
   * ```
   */
  public fun resetCacheStats() {
    TODO("Implement resetCacheStats")
  }

  /**
   * C++ original:
   * ```cpp
   * void writeShadersToDisk(const char* path, GrBackendApi backend)
   * ```
   */
  public fun writeShadersToDisk(path: String?, backend: GrBackendApi) {
    TODO("Implement writeShadersToDisk")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename Fn>
   *     void foreach(Fn&& fn) {
   *         for (auto it = fMap.begin(); it != fMap.end(); ++it) {
   *             fn(it->first.fKey, it->second.fData, it->second.fDescription, it->second.fHitCount);
   *         }
   *     }
   * ```
   */
  public fun <Fn> foreach(fn: Fn) {
    TODO("Implement foreach")
  }

  public data class Key public constructor(
    public var fKey: Int,
  ) {
    public fun assign(param0: org.skia.pdf.Key) {
      TODO("Implement assign")
    }

    public override operator fun equals(other: Any?): Boolean {
      TODO("Implement equals")
    }
  }

  public open class Value public constructor(
    public var fData: Int,
    public var fDescription: Int,
    public var fHitCount: Int,
  ) {
    public constructor() : this() {
      TODO("Implement constructor")
    }

    public constructor(`data`: SkData, description: String) : this() {
      TODO("Implement constructor")
    }

    public constructor(that: org.skia.modules.Value) : this() {
      TODO("Implement constructor")
    }

    public fun assign(param0: org.skia.modules.Value) {
      TODO("Implement assign")
    }
  }

  public open class Hash {
    public operator fun invoke(key: org.skia.pdf.Key): UInt {
      TODO("Implement invoke")
    }
  }
}
