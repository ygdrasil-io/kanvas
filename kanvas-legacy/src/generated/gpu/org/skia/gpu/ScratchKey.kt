package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import undefined.ResourceType

/**
 * C++ original:
 * ```cpp
 * class ScratchKey : public ResourceKey {
 * public:
 *     /** Uniquely identifies the type of resource that is cached as scratch. */
 *     typedef uint32_t ResourceType;
 *
 *     /** Generate a unique ResourceType. */
 *     static ResourceType GenerateResourceType();
 *
 *     /** Creates an invalid scratch key. It must be initialized using a Builder object before use. */
 *     ScratchKey() {}
 *
 *     ScratchKey(const ScratchKey& that) { *this = that; }
 *
 *     ResourceType resourceType() const { return this->domain(); }
 *
 *     ScratchKey& operator=(const ScratchKey& that) {
 *         this->ResourceKey::operator=(that);
 *         return *this;
 *     }
 *
 *     bool operator==(const ScratchKey& that) const { return this->ResourceKey::operator==(that); }
 *     bool operator!=(const ScratchKey& that) const { return !(*this == that); }
 *
 *     class Builder : public ResourceKey::Builder {
 *     public:
 *         Builder(ScratchKey* key, ResourceType type, int data32Count)
 *                 : ResourceKey::Builder(key, type, data32Count) {}
 *     };
 * }
 * ```
 */
public open class ScratchKey public constructor() : ResourceKey() {
  /**
   * C++ original:
   * ```cpp
   * ScratchKey() {}
   * ```
   */
  public constructor(that: ScratchKey) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * ResourceType resourceType() const { return this->domain(); }
   * ```
   */
  public fun resourceType(): ScratchKeyResourceType {
    TODO("Implement resourceType")
  }

  /**
   * C++ original:
   * ```cpp
   * ScratchKey& operator=(const ScratchKey& that) {
   *         this->ResourceKey::operator=(that);
   *         return *this;
   *     }
   * ```
   */
  public fun assign(that: ScratchKey) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator==(const ScratchKey& that) const { return this->ResourceKey::operator==(that); }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  public open class Builder public constructor(
    key: ScratchKey?,
    type: ResourceType,
    data32Count: Int,
  ) : ResourceKey.Builder(TODO(), TODO(), TODO())

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * ScratchKey::ResourceType ScratchKey::GenerateResourceType() {
     *     static std::atomic<int32_t> nextType{ResourceKey::kInvalidDomain + 1};
     *
     *     int32_t type = nextType.fetch_add(1, std::memory_order_relaxed);
     *     if (type > SkTo<int32_t>(UINT16_MAX)) {
     *         SK_ABORT("Too many Resource Types");
     *     }
     *
     *     return static_cast<ResourceType>(type);
     * }
     * ```
     */
    public fun generateResourceType(): ScratchKeyResourceType {
      TODO("Implement generateResourceType")
    }
  }
}
