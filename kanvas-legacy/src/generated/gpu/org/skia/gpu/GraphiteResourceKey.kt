package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import undefined.ResourceType

/**
 * C++ original:
 * ```cpp
 * class GraphiteResourceKey : public skgpu::ResourceKey {
 * public:
 *     /** Generate a unique ResourceType. */
 *     static ResourceType GenerateResourceType();
 *
 *     /** Creates an invalid key. It must be initialized using a Builder object before use. */
 *     GraphiteResourceKey() {}
 *
 *     GraphiteResourceKey(const GraphiteResourceKey& that) { *this = that; }
 *
 *     ResourceType resourceType() const { return this->domain(); }
 *
 *     GraphiteResourceKey& operator=(const GraphiteResourceKey& that) {
 *         this->ResourceKey::operator=(that);
 *         return *this;
 *     }
 *
 *     bool operator==(const GraphiteResourceKey& that) const {
 *         return this->ResourceKey::operator==(that);
 *     }
 *     bool operator!=(const GraphiteResourceKey& that) const {
 *         return !(*this == that);
 *     }
 *
 *     class Builder : public ResourceKey::Builder {
 *     public:
 *         Builder(GraphiteResourceKey* key, ResourceType type, int data32Count)
 *                 : ResourceKey::Builder(key, type, data32Count) {}
 *     };
 * }
 * ```
 */
public open class GraphiteResourceKey public constructor() : ResourceKey() {
  /**
   * C++ original:
   * ```cpp
   * GraphiteResourceKey() {}
   * ```
   */
  public constructor(that: GraphiteResourceKey) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * ResourceType resourceType() const { return this->domain(); }
   * ```
   */
  public fun resourceType(): Int {
    TODO("Implement resourceType")
  }

  /**
   * C++ original:
   * ```cpp
   * GraphiteResourceKey& operator=(const GraphiteResourceKey& that) {
   *         this->ResourceKey::operator=(that);
   *         return *this;
   *     }
   * ```
   */
  public fun assign(that: GraphiteResourceKey) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator==(const GraphiteResourceKey& that) const {
   *         return this->ResourceKey::operator==(that);
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  public open class Builder public constructor(
    key: GraphiteResourceKey?,
    type: ResourceType,
    data32Count: Int,
  ) : ResourceKey.Builder(TODO(), TODO(), TODO())

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * ResourceType GraphiteResourceKey::GenerateResourceType() {
     *     static std::atomic<int32_t> nextType{ResourceKey::kInvalidDomain + 1};
     *
     *     int32_t type = nextType.fetch_add(1, std::memory_order_relaxed);
     *     if (type > SkTo<int32_t>(UINT16_MAX)) {
     *         SK_ABORT("Too many Graphite Resource Types");
     *     }
     *
     *     return static_cast<ResourceType>(type);
     * }
     * ```
     */
    public fun generateResourceType(): Int {
      TODO("Implement generateResourceType")
    }
  }
}
