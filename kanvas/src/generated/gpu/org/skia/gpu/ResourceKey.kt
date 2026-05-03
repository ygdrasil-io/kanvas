package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * class ResourceKey {
 * public:
 *     uint32_t hash() const {
 *         this->validate();
 *         return fKey[kHash_MetaDataIdx];
 *     }
 *
 *     size_t size() const {
 *         this->validate();
 *         SkASSERT(this->isValid());
 *         return this->internalSize();
 *     }
 *
 *     /** Reset to an invalid key. */
 *     void reset() {
 *         fKey.reset(kMetaDataCnt);
 *         fKey[kHash_MetaDataIdx] = 0;
 *         fKey[kDomainAndSize_MetaDataIdx] = kInvalidDomain;
 *     }
 *
 *     bool isValid() const { return kInvalidDomain != this->domain(); }
 *
 *     /** Used to initialize a key. */
 *     class Builder {
 *     public:
 *         ~Builder() { this->finish(); }
 *
 *         void finish() {
 *             if (nullptr == fKey) {
 *                 return;
 *             }
 *             uint32_t* hash = &fKey->fKey[kHash_MetaDataIdx];
 *             *hash = ResourceKeyHash(hash + 1, fKey->internalSize() - sizeof(uint32_t));
 *             fKey->validate();
 *             fKey = nullptr;
 *         }
 *
 *         uint32_t& operator[](int dataIdx) {
 *             SkASSERT(fKey);
 *             SkDEBUGCODE(size_t dataCount = fKey->internalSize() / sizeof(uint32_t) - kMetaDataCnt;)
 *                     SkASSERT(SkToU32(dataIdx) < dataCount);
 *             return fKey->fKey[(int)kMetaDataCnt + dataIdx];
 *         }
 *
 *     protected:
 *         Builder(ResourceKey* key, uint32_t domain, int data32Count) : fKey(key) {
 *             size_t count = SkToSizeT(data32Count);
 *             SkASSERT(domain != kInvalidDomain);
 *             key->fKey.reset(kMetaDataCnt + count);
 *             size_t size = (count + kMetaDataCnt) * sizeof(uint32_t);
 *             SkASSERT(SkToU16(size) == size);
 *             SkASSERT(SkToU16(domain) == domain);
 *             key->fKey[kDomainAndSize_MetaDataIdx] = SkToU32(domain | (size << 16));
 *         }
 *
 *     private:
 *         ResourceKey* fKey;
 *     };
 *
 * protected:
 *     static const uint32_t kInvalidDomain = 0;
 *
 *     ResourceKey() { this->reset(); }
 *
 *     bool operator==(const ResourceKey& that) const {
 *         // Both keys should be sized to at least contain the meta data. The metadata contains each
 *         // key's length. So the second memcmp should only run if the keys have the same length.
 *         return 0 == memcmp(fKey.get(), that.fKey.get(), kMetaDataCnt*sizeof(uint32_t)) &&
 *                0 == memcmp(&fKey[kMetaDataCnt], &that.fKey[kMetaDataCnt], this->dataSize());
 *     }
 *
 *     ResourceKey& operator=(const ResourceKey& that) {
 *         if (this != &that) {
 *             if (!that.isValid()) {
 *                 this->reset();
 *             } else {
 *                 size_t bytes = that.size();
 *                 SkASSERT(SkIsAlign4(bytes));
 *                 fKey.reset(bytes / sizeof(uint32_t));
 *                 memcpy(fKey.get(), that.fKey.get(), bytes);
 *                 this->validate();
 *             }
 *         }
 *         return *this;
 *     }
 *
 *     uint32_t domain() const { return fKey[kDomainAndSize_MetaDataIdx] & 0xffff; }
 *
 *     /** size of the key data, excluding meta-data (hash, domain, etc).  */
 *     size_t dataSize() const { return this->size() - 4 * kMetaDataCnt; }
 *
 *     /** ptr to the key data, excluding meta-data (hash, domain, etc).  */
 *     const uint32_t* data() const {
 *         this->validate();
 *         return &fKey[kMetaDataCnt];
 *     }
 *
 * #ifdef SK_DEBUG
 *     void dump() const {
 *         if (!this->isValid()) {
 *             SkDebugf("Invalid Key\n");
 *         } else {
 *             SkDebugf("hash: %u ", this->hash());
 *             SkDebugf("domain: %u ", this->domain());
 *             SkDebugf("size: %zuB ", this->internalSize());
 *             size_t dataCount = this->internalSize() / sizeof(uint32_t) - kMetaDataCnt;
 *             for (size_t i = 0; i < dataCount; ++i) {
 *                 SkDebugf("%u ", fKey[SkTo<int>(kMetaDataCnt+i)]);
 *             }
 *             SkDebugf("\n");
 *         }
 *     }
 * #endif
 *
 * private:
 *     enum MetaDataIdx {
 *         kHash_MetaDataIdx,
 *         // The key domain and size are packed into a single uint32_t.
 *         kDomainAndSize_MetaDataIdx,
 *
 *         kLastMetaDataIdx = kDomainAndSize_MetaDataIdx
 *     };
 *     static const uint32_t kMetaDataCnt = kLastMetaDataIdx + 1;
 *
 *     size_t internalSize() const { return fKey[kDomainAndSize_MetaDataIdx] >> 16; }
 *
 *     void validate() const {
 *         SkASSERT(this->isValid());
 *         SkASSERT(fKey[kHash_MetaDataIdx] ==
 *                  ResourceKeyHash(&fKey[kHash_MetaDataIdx] + 1,
 *                                  this->internalSize() - sizeof(uint32_t)));
 *         SkASSERT(SkIsAlign4(this->internalSize()));
 *     }
 *
 *     friend class ::TestResource;  // For unit test to access kMetaDataCnt.
 *
 *     // For Ganesh, bmp textures require 5 uint32_t values. Graphite requires 6 (due to
 *     // storing mipmap status as part of the key).
 *     skia_private::AutoSTMalloc<kMetaDataCnt + 6, uint32_t> fKey;
 * }
 * ```
 */
public open class ResourceKey public constructor() {
  /**
   * C++ original:
   * ```cpp
   * static const uint32_t kInvalidDomain = 0
   * ```
   */
  private var fKey: Int = TODO("Initialize fKey")

  /**
   * C++ original:
   * ```cpp
   * uint32_t hash() const {
   *         this->validate();
   *         return fKey[kHash_MetaDataIdx];
   *     }
   * ```
   */
  public fun hash(): Int {
    TODO("Implement hash")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t size() const {
   *         this->validate();
   *         SkASSERT(this->isValid());
   *         return this->internalSize();
   *     }
   * ```
   */
  public fun size(): Int {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * void reset() {
   *         fKey.reset(kMetaDataCnt);
   *         fKey[kHash_MetaDataIdx] = 0;
   *         fKey[kDomainAndSize_MetaDataIdx] = kInvalidDomain;
   *     }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isValid() const { return kInvalidDomain != this->domain(); }
   * ```
   */
  public fun isValid(): Boolean {
    TODO("Implement isValid")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator==(const ResourceKey& that) const {
   *         // Both keys should be sized to at least contain the meta data. The metadata contains each
   *         // key's length. So the second memcmp should only run if the keys have the same length.
   *         return 0 == memcmp(fKey.get(), that.fKey.get(), kMetaDataCnt*sizeof(uint32_t)) &&
   *                0 == memcmp(&fKey[kMetaDataCnt], &that.fKey[kMetaDataCnt], this->dataSize());
   *     }
   * ```
   */
  protected override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * ResourceKey& operator=(const ResourceKey& that) {
   *         if (this != &that) {
   *             if (!that.isValid()) {
   *                 this->reset();
   *             } else {
   *                 size_t bytes = that.size();
   *                 SkASSERT(SkIsAlign4(bytes));
   *                 fKey.reset(bytes / sizeof(uint32_t));
   *                 memcpy(fKey.get(), that.fKey.get(), bytes);
   *                 this->validate();
   *             }
   *         }
   *         return *this;
   *     }
   * ```
   */
  protected fun assign(that: ResourceKey) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t domain() const { return fKey[kDomainAndSize_MetaDataIdx] & 0xffff; }
   * ```
   */
  protected fun domain(): Int {
    TODO("Implement domain")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t dataSize() const { return this->size() - 4 * kMetaDataCnt; }
   * ```
   */
  protected fun dataSize(): Int {
    TODO("Implement dataSize")
  }

  /**
   * C++ original:
   * ```cpp
   * const uint32_t* data() const {
   *         this->validate();
   *         return &fKey[kMetaDataCnt];
   *     }
   * ```
   */
  protected fun `data`(): Int {
    TODO("Implement data")
  }

  /**
   * C++ original:
   * ```cpp
   * void dump() const {
   *         if (!this->isValid()) {
   *             SkDebugf("Invalid Key\n");
   *         } else {
   *             SkDebugf("hash: %u ", this->hash());
   *             SkDebugf("domain: %u ", this->domain());
   *             SkDebugf("size: %zuB ", this->internalSize());
   *             size_t dataCount = this->internalSize() / sizeof(uint32_t) - kMetaDataCnt;
   *             for (size_t i = 0; i < dataCount; ++i) {
   *                 SkDebugf("%u ", fKey[SkTo<int>(kMetaDataCnt+i)]);
   *             }
   *             SkDebugf("\n");
   *         }
   *     }
   * ```
   */
  protected fun dump() {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t internalSize() const { return fKey[kDomainAndSize_MetaDataIdx] >> 16; }
   * ```
   */
  private fun internalSize(): Int {
    TODO("Implement internalSize")
  }

  /**
   * C++ original:
   * ```cpp
   * void validate() const {
   *         SkASSERT(this->isValid());
   *         SkASSERT(fKey[kHash_MetaDataIdx] ==
   *                  ResourceKeyHash(&fKey[kHash_MetaDataIdx] + 1,
   *                                  this->internalSize() - sizeof(uint32_t)));
   *         SkASSERT(SkIsAlign4(this->internalSize()));
   *     }
   * ```
   */
  private fun validate() {
    TODO("Implement validate")
  }

  public open class Builder public constructor(
    key: ResourceKey?,
    domain: UInt,
    data32Count: Int,
  ) {
    private var fKey: ResourceKey? = TODO("Initialize fKey")

    public fun finish() {
      TODO("Implement finish")
    }

    public operator fun `get`(dataIdx: Int): Int {
      TODO("Implement get")
    }
  }

  public enum class MetaDataIdx {
    kHash_MetaDataIdx,
    kDomainAndSize_MetaDataIdx,
    kLastMetaDataIdx,
  }

  public companion object {
    protected val kInvalidDomain: Int = TODO("Initialize kInvalidDomain")

    private val kMetaDataCnt: Int = TODO("Initialize kMetaDataCnt")
  }
}
