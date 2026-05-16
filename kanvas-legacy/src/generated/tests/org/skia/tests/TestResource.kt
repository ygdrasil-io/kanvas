package org.skia.tests

import kotlin.Char
import kotlin.ULong
import org.skia.`external`.Resource
import org.skia.foundation.SkSp
import org.skia.gpu.GraphiteResourceKey
import org.skia.gpu.Ownership
import org.skia.gpu.ResourceCache
import org.skia.gpu.Shareable
import org.skia.gpu.SharedContext
import org.skia.gpu.ganesh.Budgeted

/**
 * C++ original:
 * ```cpp
 * class TestResource : public Resource {
 * public:
 *     static sk_sp<TestResource> Make(const SharedContext* sharedContext,
 *                                     ResourceCache* resourceCache,
 *                                     Ownership owned,
 *                                     Budgeted budgeted,
 *                                     Shareable shareable,
 *                                     size_t gpuMemorySize = 1) {
 *         auto resource = sk_sp<TestResource>(new TestResource(sharedContext,
 *                                                              owned,
 *                                                              gpuMemorySize));
 *         if (!resource) {
 *             return nullptr;
 *         }
 *
 *         GraphiteResourceKey key;
 *         CreateKey(&key);
 *
 *         resourceCache->insertResource(resource.get(), key, budgeted, shareable);
 *         return resource;
 *     }
 *
 *     const char* getResourceType() const override { return "Test Resource"; }
 *
 *     static void CreateKey(GraphiteResourceKey* key) {
 *         // All unit tests that currently use TestResource are able to work with a single Resource,
 *         // so the key doesn't require any real state.
 *         static const ResourceType kType = GraphiteResourceKey::GenerateResourceType();
 *         GraphiteResourceKey::Builder(key, kType, 0);
 *     }
 *
 * private:
 *     TestResource(const SharedContext* sharedContext,
 *                  Ownership owned,
 *                  size_t gpuMemorySize)
 *             : Resource(sharedContext, owned, gpuMemorySize) {}
 *
 *     void freeGpuData() override {}
 * }
 * ```
 */
public open class TestResource public constructor(
  sharedContext: SharedContext?,
  owned: Ownership,
  gpuMemorySize: ULong,
) : Resource(TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * const char* getResourceType() const override { return "Test Resource"; }
   * ```
   */
  public override fun getResourceType(): Char {
    TODO("Implement getResourceType")
  }

  /**
   * C++ original:
   * ```cpp
   * void freeGpuData() override {}
   * ```
   */
  public override fun freeGpuData() {
    TODO("Implement freeGpuData")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<TestResource> Make(const SharedContext* sharedContext,
     *                                     ResourceCache* resourceCache,
     *                                     Ownership owned,
     *                                     Budgeted budgeted,
     *                                     Shareable shareable,
     *                                     size_t gpuMemorySize = 1) {
     *         auto resource = sk_sp<TestResource>(new TestResource(sharedContext,
     *                                                              owned,
     *                                                              gpuMemorySize));
     *         if (!resource) {
     *             return nullptr;
     *         }
     *
     *         GraphiteResourceKey key;
     *         CreateKey(&key);
     *
     *         resourceCache->insertResource(resource.get(), key, budgeted, shareable);
     *         return resource;
     *     }
     * ```
     */
    public fun make(
      sharedContext: SharedContext?,
      resourceCache: ResourceCache?,
      owned: Ownership,
      budgeted: Budgeted,
      shareable: Shareable,
      gpuMemorySize: ULong = TODO(),
    ): SkSp<TestResource> {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * static void CreateKey(GraphiteResourceKey* key) {
     *         // All unit tests that currently use TestResource are able to work with a single Resource,
     *         // so the key doesn't require any real state.
     *         static const ResourceType kType = GraphiteResourceKey::GenerateResourceType();
     *         GraphiteResourceKey::Builder(key, kType, 0);
     *     }
     * ```
     */
    public fun createKey(key: GraphiteResourceKey?) {
      TODO("Implement createKey")
    }
  }
}
