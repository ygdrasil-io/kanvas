package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkData
import org.skia.foundation.SkSp
import undefined.Domain

/**
 * C++ original:
 * ```cpp
 * class UniqueKey : public ResourceKey {
 * public:
 *     typedef uint32_t Domain;
 *     /** Generate a Domain for unique keys. */
 *     static Domain GenerateDomain();
 *
 *     /** Creates an invalid unique key. It must be initialized using a Builder object before use. */
 *     UniqueKey() : fTag(nullptr) {}
 *
 *     UniqueKey(const UniqueKey& that) { *this = that; }
 *
 *     UniqueKey& operator=(const UniqueKey& that) {
 *         this->ResourceKey::operator=(that);
 *         this->setCustomData(sk_ref_sp(that.getCustomData()));
 *         fTag = that.fTag;
 *         return *this;
 *     }
 *
 *     bool operator==(const UniqueKey& that) const { return this->ResourceKey::operator==(that); }
 *     bool operator!=(const UniqueKey& that) const { return !(*this == that); }
 *
 *     void setCustomData(sk_sp<SkData> data) { fData = std::move(data); }
 *     SkData* getCustomData() const { return fData.get(); }
 *     sk_sp<SkData> refCustomData() const { return fData; }
 *
 *     const char* tag() const { return fTag; }
 *
 *     const uint32_t* data() const { return this->ResourceKey::data(); }
 *
 * #ifdef SK_DEBUG
 *     uint32_t domain() const { return this->ResourceKey::domain(); }
 *     size_t dataSize() const { return this->ResourceKey::dataSize(); }
 *
 *     void dump(const char* label) const {
 *         SkDebugf("%s tag: %s\n", label, fTag ? fTag : "None");
 *         this->ResourceKey::dump();
 *     }
 * #endif
 *
 *     class Builder : public ResourceKey::Builder {
 *     public:
 *         Builder(UniqueKey* key, Domain type, int data32Count, const char* tag = nullptr)
 *                 : ResourceKey::Builder(key, type, data32Count) {
 *             key->fTag = tag;
 *         }
 *
 *         /** Used to build a key that wraps another key and adds additional data. */
 *         Builder(UniqueKey* key, const UniqueKey& innerKey, Domain domain, int extraData32Cnt,
 *                 const char* tag = nullptr)
 *                 : ResourceKey::Builder(key,
 *                                        domain,
 *                                        Data32CntForInnerKey(innerKey) + extraData32Cnt) {
 *             SkASSERT(&innerKey != key);
 *             // add the inner key to the end of the key so that op[] can be indexed normally.
 *             uint32_t* innerKeyData = &this->operator[](extraData32Cnt);
 *             const uint32_t* srcData = innerKey.data();
 *             (*innerKeyData++) = innerKey.domain();
 *             memcpy(innerKeyData, srcData, innerKey.dataSize());
 *             key->fTag = tag;
 *         }
 *
 *     private:
 *         static int Data32CntForInnerKey(const UniqueKey& innerKey) {
 *             // key data + domain
 *             return SkToInt((innerKey.dataSize() >> 2) + 1);
 *         }
 *     };
 *
 * private:
 *     sk_sp<SkData> fData;
 *     const char* fTag;
 * }
 * ```
 */
public open class UniqueKey public constructor() : ResourceKey() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> fData
   * ```
   */
  private var fData: SkSp<SkData> = TODO("Initialize fData")

  /**
   * C++ original:
   * ```cpp
   * const char* fTag
   * ```
   */
  private val fTag: String? = TODO("Initialize fTag")

  /**
   * C++ original:
   * ```cpp
   * UniqueKey() : fTag(nullptr) {}
   * ```
   */
  public constructor(that: UniqueKey) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * UniqueKey& operator=(const UniqueKey& that) {
   *         this->ResourceKey::operator=(that);
   *         this->setCustomData(sk_ref_sp(that.getCustomData()));
   *         fTag = that.fTag;
   *         return *this;
   *     }
   * ```
   */
  public fun assign(that: UniqueKey) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator==(const UniqueKey& that) const { return this->ResourceKey::operator==(that); }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const UniqueKey& that) const { return !(*this == that); }
   * ```
   */
  public fun setCustomData(`data`: SkSp<SkData>) {
    TODO("Implement setCustomData")
  }

  /**
   * C++ original:
   * ```cpp
   * void setCustomData(sk_sp<SkData> data) { fData = std::move(data); }
   * ```
   */
  public fun getCustomData(): SkData {
    TODO("Implement getCustomData")
  }

  /**
   * C++ original:
   * ```cpp
   * SkData* getCustomData() const { return fData.get(); }
   * ```
   */
  public fun refCustomData(): SkSp<SkData> {
    TODO("Implement refCustomData")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> refCustomData() const { return fData; }
   * ```
   */
  public fun tag(): Char {
    TODO("Implement tag")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* tag() const { return fTag; }
   * ```
   */
  public override fun `data`(): Int {
    TODO("Implement data")
  }

  /**
   * C++ original:
   * ```cpp
   * const uint32_t* data() const { return this->ResourceKey::data(); }
   * ```
   */
  public override fun domain(): Int {
    TODO("Implement domain")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t domain() const { return this->ResourceKey::domain(); }
   * ```
   */
  public override fun dataSize(): Int {
    TODO("Implement dataSize")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t dataSize() const { return this->ResourceKey::dataSize(); }
   * ```
   */
  public fun dump(label: String?) {
    TODO("Implement dump")
  }

  public open class Builder public constructor(
    key: UniqueKey?,
    type: Domain,
    data32Count: Int,
    tag: String? = TODO(),
  ) : ResourceKey.Builder(TODO(), TODO(), TODO()) {
    public constructor(
      key: UniqueKey?,
      innerKey: UniqueKey,
      domain: Domain,
      extraData32Cnt: Int,
      tag: String? = TODO(),
    ) : this(TODO(), TODO(), TODO()) {
      TODO("Implement constructor")
    }

    public companion object {
      private fun data32CntForInnerKey(innerKey: UniqueKey): Int {
        TODO("Implement data32CntForInnerKey")
      }
    }
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * UniqueKey::Domain UniqueKey::GenerateDomain() {
     *     static std::atomic<int32_t> nextDomain{ResourceKey::kInvalidDomain + 1};
     *
     *     int32_t domain = nextDomain.fetch_add(1, std::memory_order_relaxed);
     *     if (domain > SkTo<int32_t>(UINT16_MAX)) {
     *         SK_ABORT("Too many skgpu::UniqueKey Domains");
     *     }
     *
     *     return static_cast<Domain>(domain);
     * }
     * ```
     */
    public fun generateDomain(): UniqueKeyDomain {
      TODO("Implement generateDomain")
    }
  }
}
