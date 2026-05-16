package org.skia.gpu

import TextureCopier
import UniformCopier
import kotlin.Boolean
import kotlin.Int
import org.skia.`external`.Index
import org.skia.core.Entry

/**
 * C++ original:
 * ```cpp
 * class DenseBiMap {
 * public:
 *     using Index = uint32_t;
 *     // 1 << SkNextLog2_portable(DrawList::kMaxRenderSteps);
 *     static constexpr Index kInvalidIndex = 4096;
 *
 *     Index insert(K data) {
 *         Index* index = fDataToIndex.find(data);
 *         if (!index) {
 *             // First time we've seen this piece of data.
 *             SkASSERT(SkToU32(fIndexToData.size()) < kInvalidIndex);
 *             // Persist it in storage if S is not monostate.
 *             if constexpr (!std::is_same_v<S, std::monostate>) {
 *                 data = fStorage.persist(data);
 *             } else {
 *                 static_assert(std::is_trivially_copyable<K>::value);
 *             }
 *
 *             index = fDataToIndex.set(data, static_cast<Index>(fIndexToData.size()));
 *             fIndexToData.emplace_back(data); // constructs V in place from single K argument
 *         }
 *         return *index;
 *     }
 *
 *     bool contains(K data) const { return SkToBool(fDataToIndex.find(data)); }
 *
 *     const V& lookup(Index index) const { return fIndexToData[index]; }
 *
 *     V& lookup(Index index) { return fIndexToData[index]; }
 *
 *     skia_private::TArray<V>&& detach() { return std::move(fIndexToData); }
 *
 *     const skia_private::TArray<V>& get() const { return fIndexToData; }
 *
 *     void reset() {
 *         fIndexToData.clear();
 *         fDataToIndex.reset();
 *         if constexpr (!std::is_same_v<S, std::monostate>) {
 *             fStorage.~S();
 *             new (&fStorage) S();
 *         }
 *     }
 *
 *     int count() const { return fIndexToData.size(); }
 *
 *     template <typename SV = S>
 *     typename std::enable_if<!std::is_same_v<SV, std::monostate>, const SV&>::type
 *     storage() const { return fStorage; }
 *
 *     template <typename SV = S>
 *     typename std::enable_if<!std::is_same_v<SV, std::monostate>, SV&>::type
 *     storage() { return fStorage; }
 *
 * private:
 *     skia_private::THashMap<K, Index, H> fDataToIndex;
 *     skia_private::TArray<V> fIndexToData;
 *
 *     S fStorage;
 * }
 * ```
 */
public data class DenseBiMap<S> public constructor(
  /**
   * C++ original:
   * ```cpp
   * static constexpr Index kInvalidIndex = 4096
   * ```
   */
  private var fIndexToData: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<V> fIndexToData
   * ```
   */
  private var fStorage: S,
) {
  /**
   * C++ original:
   * ```cpp
   * Index insert(K data) {
   *         Index* index = fDataToIndex.find(data);
   *         if (!index) {
   *             // First time we've seen this piece of data.
   *             SkASSERT(SkToU32(fIndexToData.size()) < kInvalidIndex);
   *             // Persist it in storage if S is not monostate.
   *             if constexpr (!std::is_same_v<S, std::monostate>) {
   *                 data = fStorage.persist(data);
   *             } else {
   *                 static_assert(std::is_trivially_copyable<K>::value);
   *             }
   *
   *             index = fDataToIndex.set(data, static_cast<Index>(fIndexToData.size()));
   *             fIndexToData.emplace_back(data); // constructs V in place from single K argument
   *         }
   *         return *index;
   *     }
   * ```
   */
  public fun insert(`data`: K): Int {
    TODO("Implement insert")
  }

  /**
   * C++ original:
   * ```cpp
   * bool contains(K data) const { return SkToBool(fDataToIndex.find(data)); }
   * ```
   */
  public fun contains(`data`: K): Boolean {
    TODO("Implement contains")
  }

  /**
   * C++ original:
   * ```cpp
   * const V& lookup(Index index) const { return fIndexToData[index]; }
   * ```
   */
  public fun lookup(index: Index): V {
    TODO("Implement lookup")
  }

  /**
   * C++ original:
   * ```cpp
   * V& lookup(Index index) { return fIndexToData[index]; }
   * ```
   */
  public fun detach(): Int {
    TODO("Implement detach")
  }

  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<V>&& detach() { return std::move(fIndexToData); }
   * ```
   */
  public fun `get`(): Int {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   * const skia_private::TArray<V>& get() const { return fIndexToData; }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void reset() {
   *         fIndexToData.clear();
   *         fDataToIndex.reset();
   *         if constexpr (!std::is_same_v<S, std::monostate>) {
   *             fStorage.~S();
   *             new (&fStorage) S();
   *         }
   *     }
   * ```
   */
  public fun count(): Int {
    TODO("Implement count")
  }

  public companion object {
    public val kInvalidIndex: Int = TODO("Initialize kInvalidIndex")
  }
}

public typealias UniformDataCacheUniformDataMap = DenseBiMap<UniformDataBlock, Entry, UniformCopier, UniformDataBlock.Hash>

public typealias TextureDataCacheTextureDataMap = DenseBiMap<TextureDataBlock, TextureDataBlock, TextureCopier, TextureDataBlock.Hash>
