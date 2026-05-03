package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import org.skia.memory.SkArenaAlloc

/**
 * C++ original:
 * ```cpp
 * class PaintParamsKey {
 * public:
 *     // PaintParamsKey can only be created by using a PaintParamsKeyBuilder or by cloning the key
 *     // data from a Builder-owned key, but they can be passed around by value after that.
 *     constexpr PaintParamsKey(const PaintParamsKey&) = default;
 *
 *     constexpr PaintParamsKey(SkSpan<const uint32_t> span) : fData(span) {}
 *
 *     ~PaintParamsKey() = default;
 *     PaintParamsKey& operator=(const PaintParamsKey&) = default;
 *
 *     static constexpr PaintParamsKey Invalid() { return PaintParamsKey(SkSpan<const uint32_t>()); }
 *     bool isValid() const { return !fData.empty(); }
 *
 *     // Return a PaintParamsKey whose data is owned by the provided arena and is not attached to
 *     // a PaintParamsKeyBuilder. The caller must ensure that the SkArenaAlloc remains alive longer
 *     // than the returned key.
 *     PaintParamsKey clone(SkArenaAlloc*) const;
 *
 *     // Converts the key into a forest of ShaderNode trees. If the key is valid this will return at
 *     // least one root node. If the key contains unknown shader snippet IDs, returns an empty span.
 *     // All shader nodes, and the returned span's backing data, are owned by the provided arena.
 *     //
 *     // A valid key will produce either 2 or 3 root nodes. The first root node represents how the
 *     // source color is computed. The second node defines the final blender between the calculated
 *     // source color and the current pixel's dst color. If provided, the third node calculates an
 *     // additional analytic coverage value to combine with the geometry's coverage.
 *     //
 *     // Before returning the ShaderNode trees, this method decides which ShaderNode expressions to
 *     // lift to the vertex shader, depending on how many varyings are available.
 *     SkSpan<const ShaderNode*> getRootNodes(const Caps*,
 *                                            const ShaderCodeDictionary*,
 *                                            SkArenaAlloc*,
 *                                            int availableVaryings) const;
 *
 *     // Converts the key to a structured list of snippet information for debugging or labeling
 *     // purposes.
 *     SkString toString(const Caps*, const ShaderCodeDictionary*) const;
 *
 * #ifdef SK_DEBUG
 *     void dump(const Caps*, const ShaderCodeDictionary*, UniquePaintParamsID) const;
 * #endif
 *
 *     bool operator==(const PaintParamsKey& that) const {
 *         return fData.size() == that.fData.size() &&
 *                !memcmp(fData.data(), that.fData.data(), fData.size());
 *     }
 *     bool operator!=(const PaintParamsKey& that) const { return !(*this == that); }
 *
 *     struct Hash {
 *         uint32_t operator()(const PaintParamsKey& k) const {
 *             return SkChecksum::Hash32(k.fData.data(), k.fData.size_bytes());
 *         }
 *     };
 *
 *     SkSpan<const uint32_t> data() const { return fData; }
 *
 *     // Checks that a given key is viable for serialization and, also, that a deserialized
 *     // key is, at least, correctly formed. Other than that all the sizes make sense, this method
 *     // also checks that only Skia-internal shader code snippets appear in the key.
 *     [[nodiscard]] bool isSerializable(const ShaderCodeDictionary*) const;
 *
 * private:
 *     friend class PaintParamsKeyBuilder;   // for the parented-data ctor
 *
 *     // Returns null if the node or any of its children have an invalid snippet ID. Recursively
 *     // creates a node and all of its children, incrementing 'currentIndex' by the total number of
 *     // nodes created.
 *     ShaderNode* createNode(const ShaderCodeDictionary*,
 *                            int* currentIndex,
 *                            SkArenaAlloc* arena) const;
 *
 *     // The memory referenced in 'fData' is always owned by someone else. It either shares the span
 *     // from the Builder, or clone() puts the span in an arena.
 *     SkSpan<const uint32_t> fData;
 * }
 * ```
 */
public data class PaintParamsKey public constructor(
  /**
   * C++ original:
   * ```cpp
   * constexpr PaintParamsKey(SkSpan<const uint32_t> span)
   * ```
   */
  private var fData: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * PaintParamsKey& operator=(const PaintParamsKey&) = default
   * ```
   */
  public fun assign(param0: PaintParamsKey) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isValid() const { return !fData.empty(); }
   * ```
   */
  public fun isValid(): Boolean {
    TODO("Implement isValid")
  }

  /**
   * C++ original:
   * ```cpp
   * PaintParamsKey PaintParamsKey::clone(SkArenaAlloc* arena) const {
   *     uint32_t* newData = arena->makeArrayDefault<uint32_t>(fData.size());
   *     memcpy(newData, fData.data(), fData.size_bytes());
   *     return PaintParamsKey({newData, fData.size()});
   * }
   * ```
   */
  public fun clone(arena: SkArenaAlloc?): PaintParamsKey {
    TODO("Implement clone")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const ShaderNode*> PaintParamsKey::getRootNodes(const Caps* caps,
   *                                                        const ShaderCodeDictionary* dict,
   *                                                        SkArenaAlloc* arena,
   *                                                        int availableVaryings) const {
   *     // TODO: Once the PaintParamsKey creation is organized to represent a single tree starting at
   *     // the final blend, there will only be a single root node and this can be simplified.
   *     // For now, we don't know how many roots there are, so collect them into a local array before
   *     // copying into the arena.
   *     const int keySize = SkTo<int>(fData.size());
   *
   *     // Normal PaintParams creation will have up to 7 roots for the different stages.
   *     STArray<7, ShaderNode*> roots;
   *     int currentIndex = 0;
   *     while (currentIndex < keySize) {
   *         ShaderNode* root = this->createNode(dict, &currentIndex, arena);
   *         if (!root) {
   *             return {}; // a bad key
   *         }
   *         roots.push_back(root);
   *     }
   *
   *     // See what expressions we can lift to the vertex shader.
   *     const bool hasClipNode = roots.size() > 2;
   *     SkSpan<ShaderNode*> liftableNodes(roots.data(), hasClipNode ? 2 : roots.size());
   *     lift_coord_expressions(liftableNodes, &availableVaryings);
   *     // Don't lift constant expressions if we're using regular UBOs, since lifting is likely only
   *     // beneficial if we're avoiding a storage buffer access.
   *     if (caps->storageBufferSupport()) {
   *         lift_color_expressions(liftableNodes, &availableVaryings);
   *     }
   *
   *     // Copy the accumulated roots into a span stored in the arena
   *     const ShaderNode** rootSpan = arena->makeArray<const ShaderNode*>(roots.size());
   *     memcpy(rootSpan, roots.data(), roots.size_bytes());
   *     return SkSpan(rootSpan, roots.size());
   * }
   * ```
   */
  public fun getRootNodes(
    caps: Caps?,
    dict: ShaderCodeDictionary?,
    arena: SkArenaAlloc?,
    availableVaryings: Int,
  ): Int {
    TODO("Implement getRootNodes")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString PaintParamsKey::toString(const Caps* caps,
   *                                   const ShaderCodeDictionary* dict) const {
   *     SkString str;
   *     const int keySize = SkTo<int>(fData.size());
   *     for (int currentIndex = 0; currentIndex < keySize;) {
   *         currentIndex = key_to_string(caps, &str, dict, fData, currentIndex, /*indent=*/-1);
   *     }
   *     return str.isEmpty() ? SkString("(empty)") : str;
   * }
   * ```
   */
  public override fun toString(caps: Caps?, dict: ShaderCodeDictionary?): Int {
    TODO("Implement toString")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator==(const PaintParamsKey& that) const {
   *         return fData.size() == that.fData.size() &&
   *                !memcmp(fData.data(), that.fData.data(), fData.size());
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const PaintParamsKey& that) const { return !(*this == that); }
   * ```
   */
  public fun `data`(): Int {
    TODO("Implement data")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const uint32_t> data() const { return fData; }
   * ```
   */
  public fun isSerializable(dict: ShaderCodeDictionary?): Boolean {
    TODO("Implement isSerializable")
  }

  /**
   * C++ original:
   * ```cpp
   * bool PaintParamsKey::isSerializable(const ShaderCodeDictionary* dict) const {
   *     const int keySize = SkTo<int>(fData.size());
   *
   *     int currentIndex = 0;
   *     while (currentIndex < keySize) {
   *         if (!is_block_valid(dict, fData, &currentIndex)) {
   *             return false;
   *         }
   *     }
   *
   *     return true;
   * }
   * ```
   */
  private fun createNode(
    dict: ShaderCodeDictionary?,
    currentIndex: Int?,
    arena: SkArenaAlloc?,
  ): ShaderNode {
    TODO("Implement createNode")
  }

  /**
   * C++ original:
   * ```cpp
   * ShaderNode* PaintParamsKey::createNode(const ShaderCodeDictionary* dict,
   *                                        int* currentIndex,
   *                                        SkArenaAlloc* arena) const {
   *     SkASSERT(*currentIndex < SkTo<int>(fData.size()));
   *     const int32_t index = (*currentIndex)++;
   *     const int32_t id = fData[index];
   *
   *     const ShaderSnippet* entry = dict->getEntry(id);
   *     if (!entry) {
   *         SKGPU_LOG_E("Unknown snippet ID in key: %d", id);
   *         return nullptr;
   *     }
   *
   *     SkSpan<const uint32_t> dataSpan = {};
   *     if (entry->storesSamplerDescData()) {
   *         // If a snippet stores data, then the subsequent paint key index signifies the length of
   *         // its data. Determine this data length and iterate currentIndex past it.
   *         const int storedDataLengthIdx = (*currentIndex)++;
   *         SkASSERT(storedDataLengthIdx < SkTo<int>(fData.size()));
   *         const int dataLength = fData[storedDataLengthIdx];
   *         SkASSERT(storedDataLengthIdx + dataLength < SkTo<int>(fData.size()));
   *
   *         // Gather the data contents (length can now be inferred by the consumers of the data) to
   *         // pass into ShaderNode creation. Iterate the paint key index past the data indices.
   *         dataSpan = fData.subspan(storedDataLengthIdx + 1, dataLength);
   *         *currentIndex += dataLength;
   *     }
   *
   *     ShaderNode** childArray = arena->makeArray<ShaderNode*>(entry->fNumChildren);
   *     for (int i = 0; i < entry->fNumChildren; ++i) {
   *         ShaderNode* child = this->createNode(dict, currentIndex, arena);
   *         if (!child) {
   *             return nullptr;
   *         }
   *         childArray[i] = child;
   *     }
   *
   *     return arena->make<ShaderNode>(entry,
   *                                    SkSpan(childArray, entry->fNumChildren),
   *                                    id,
   *                                    index,
   *                                    dataSpan);
   * }
   * ```
   */
  public fun dump(
    caps: Caps?,
    dict: ShaderCodeDictionary?,
    id: UniquePaintParamsID,
  ) {
    TODO("Implement dump")
  }

  public open class Hash {
    public operator fun invoke(k: PaintParamsKey): Int {
      TODO("Implement invoke")
    }
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static constexpr PaintParamsKey Invalid() { return PaintParamsKey(SkSpan<const uint32_t>()); }
     * ```
     */
    public fun invalid(): PaintParamsKey {
      TODO("Implement invalid")
    }
  }
}
